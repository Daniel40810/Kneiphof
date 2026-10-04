package com.dan.kneiphof.gl;

import com.dan.kneiphof.world.Boats;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Rauch aus den Schornsteinen der Stadt, Qualm der Dampfer und Dampf der Dampfpfeife.
 * Gerechnet wird auf der CPU (höchstens {@value #MAX} Teilchen), gezeichnet als weiche Flächen
 * mit einem Aufruf (Instanzen). Die Teilchen werden von hinten nach vorn sortiert.
 */
public final class Particles {
    static final int MAX = 6000;
    private static final int FLOATS = 8;           // x y z Größe | Alter 0..1, Art, Same, Deckkraft
    private final float[] px = new float[MAX], py = new float[MAX], pz = new float[MAX];
    private final float[] vx = new float[MAX], vy = new float[MAX], vz = new float[MAX];
    private final float[] age = new float[MAX], life = new float[MAX], size0 = new float[MAX], size1 = new float[MAX];
    private final float[] alpha = new float[MAX], seed = new float[MAX];
    private final byte[] kind = new byte[MAX];
    private int n;
    private final Random rnd = new Random(7);
    private double nearTimer;
    private int[] near = new int[0];
    private int nearN;
    private final double[] whistleLeft = new double[8];
    private final double[] whistleAuto = new double[8];
    private final long[] order = new long[MAX];
    private final FloatBuffer buf = Buffers.newDirectFloatBuffer(MAX * FLOATS);
    private int vao, vbo;
    int count() { return n; }

    /** Für den Prüfstand: 20 s Rauch rechnen (Schiffe laufen davor rückwärts und enden in ihrer Lage), Teilchen als x y z Größe | Alter Art Same Deckkraft. */
    public static float[] simulateForTest(List<double[]> chimneys, Boats boats, double[] cam, double[] wind, double cold, int whistleBoat) {
        Particles p = new Particles();
        for (int k = 0; k < 200; k++) boats.update(-0.1, 0);
        for (int k = 0; k < 200; k++) {
            boats.update(0.1, 0);
            if (k == 172 && whistleBoat >= 0) p.whistle(whistleBoat);
            p.update(0.1, cam, wind, cold, chimneys, boats.list, true);
        }
        float[] out = new float[p.n * FLOATS];
        for (int i = 0; i < p.n; i++) {
            float a = p.age[i];
            float s = p.size0[i] + (p.size1[i] - p.size0[i]) * (float) Math.sqrt(a);
            float[] r = {p.px[i], p.py[i], p.pz[i], s, a, p.kind[i], p.seed[i], p.alpha[i]};
            System.arraycopy(r, 0, out, i * FLOATS, FLOATS);
        }
        return out;
    }

    void init(GL4 gl) {
        int[] a = new int[1];
        gl.glGenVertexArrays(1, a, 0);
        vao = a[0];
        gl.glGenBuffers(1, a, 0);
        vbo = a[0];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) MAX * FLOATS * 4, null, GL.GL_DYNAMIC_DRAW);
        for (int i = 0; i < 2; i++) {
            gl.glEnableVertexAttribArray(i);
            gl.glVertexAttribPointer(i, 4, GL.GL_FLOAT, false, FLOATS * 4, i * 16L);
            gl.glVertexAttribDivisor(i, 1);
        }
        gl.glBindVertexArray(0);
        for (int i = 0; i < whistleAuto.length; i++) whistleAuto[i] = 40 + rnd.nextDouble() * 90;
    }

    void dispose(GL4 gl) {
        if (vao != 0) {
            gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
            gl.glDeleteBuffers(1, new int[]{vbo}, 0);
        }
        vao = vbo = 0;
    }

    /** Dampfpfeife des Schiffs i (Taste H, sonst von selbst alle ein bis zwei Minuten). */
    void whistle(int boat) {
        if (boat >= 0 && boat < whistleLeft.length) whistleLeft[boat] = 2.4;
    }

    private void spawn(double x, double y, double z, double ux, double uy, double uz, double life, double s0, double s1, double a, int k) {
        if (n >= MAX) return;
        int i = n++;
        px[i] = (float) x; py[i] = (float) y; pz[i] = (float) z;
        vx[i] = (float) ux; vy[i] = (float) uy; vz[i] = (float) uz;
        age[i] = 0; this.life[i] = (float) life; size0[i] = (float) s0; size1[i] = (float) s1;
        alpha[i] = (float) a; kind[i] = (byte) k; seed[i] = rnd.nextFloat() * 100f;
    }

    /**
     * @param cold Heizbedarf 0..1 (Jahreszeit)
     * @param wind Windvektor in m/s (x Osten, z Süden)
     */
    public void update(double dt, double[] cam, double[] wind, double cold, List<double[]> chimneys, List<Boats.Boat> boats, boolean ships) {
        // vorhandene Teilchen bewegen
        for (int i = 0; i < n; ) {
            age[i] += (float) (dt / life[i]);
            double dx = px[i] - cam[0], dz = pz[i] - cam[2];
            if (age[i] >= 1f || dx * dx + dz * dz > 1.2e6) {
                int l = --n;
                px[i] = px[l]; py[i] = py[l]; pz[i] = pz[l]; vx[i] = vx[l]; vy[i] = vy[l]; vz[i] = vz[l];
                age[i] = age[l]; life[i] = life[l]; size0[i] = size0[l]; size1[i] = size1[l]; alpha[i] = alpha[l]; kind[i] = kind[l]; seed[i] = seed[l];
                continue;
            }
            float k = (float) Math.min(1.0, dt * (kind[i] == 1 ? 0.9 : 0.45));
            vx[i] += ((float) wind[0] * 0.85f - vx[i]) * k;
            vz[i] += ((float) wind[1] * 0.85f - vz[i]) * k;
            if (kind[i] == 1) vy[i] += (0.9f - vy[i]) * (float) Math.min(1.0, dt * 0.7);
            else vy[i] += (0.25f - vy[i]) * (float) Math.min(1.0, dt * 0.35);
            px[i] += vx[i] * (float) dt; py[i] += vy[i] * (float) dt; pz[i] += vz[i] * (float) dt;
            i++;
        }
        // Schornsteine: nur in der Nähe der Kamera
        nearTimer -= dt;
        if (nearTimer <= 0 && chimneys != null) {
            nearTimer = 1.0;
            if (near.length < chimneys.size()) near = new int[chimneys.size()];
            nearN = 0;
            double frac = 0.10 + 0.70 * cold;
            for (int i = 0; i < chimneys.size(); i++) {
                double[] c = chimneys.get(i);
                double dx = c[0] - cam[0], dz = c[2] - cam[2];
                if (dx * dx + dz * dz > 380 * 380) continue;
                int h = i * 0x9E3779B1;
                h ^= h >>> 15;
                if (((h & 0xFFFF) / 65536.0) < frac) near[nearN++] = i;
            }
        }
        if (chimneys != null) {
            double rate = 0.55 + 0.9 * cold;
            for (int q = 0; q < nearN; q++) {
                if (rnd.nextDouble() >= rate * dt) continue;
                double[] c = chimneys.get(near[q]);
                double s = 0.5 + 0.5 * rnd.nextDouble();
                spawn(c[0] + (rnd.nextDouble() - 0.5) * 0.3, c[1], c[2] + (rnd.nextDouble() - 0.5) * 0.3,
                        wind[0] * 0.3, 0.8 + rnd.nextDouble() * 0.6, wind[1] * 0.3, 11 + rnd.nextDouble() * 7, 0.45 * s, 3.8 + 3.0 * s, 0.17 + 0.10 * cold, 0);
            }
        }
        // Dampfer
        if (ships && boats != null) {
            for (int b = 0; b < boats.size() && b < whistleLeft.length; b++) {
                Boats.Boat bt = boats.get(b);
                if (!bt.name.startsWith("Dampfer")) continue;
                float[] m = bt.matrix();
                double lx = -bt.length * 0.05, ly = 7.25, lz = 0;
                double fx = m[0] * lx + m[4] * ly + m[8] * lz + m[12];
                double fy = m[1] * lx + m[5] * ly + m[9] * lz + m[13];
                double fz = m[2] * lx + m[6] * ly + m[10] * lz + m[14];
                double ddx = fx - cam[0], ddz = fz - cam[2];
                if (ddx * ddx + ddz * ddz > 1500 * 1500) continue;
                double rate = 16;
                int cnt = (int) (rate * dt + rnd.nextDouble());
                for (int q = 0; q < cnt; q++)
                    spawn(fx + (rnd.nextDouble() - 0.5) * 0.4, fy, fz + (rnd.nextDouble() - 0.5) * 0.4,
                            bt.hx * bt.v * 0.6 + wind[0] * 0.3 + (rnd.nextDouble() - 0.5) * 0.4, 1.7 + rnd.nextDouble() * 0.6,
                            bt.hz * bt.v * 0.6 + wind[1] * 0.3 + (rnd.nextDouble() - 0.5) * 0.4, 9 + rnd.nextDouble() * 5, 0.8, 5.0, 0.42, 0);
                whistleAuto[b] -= dt;
                if (whistleAuto[b] <= 0) { whistleLeft[b] = 2.2; whistleAuto[b] = 60 + rnd.nextDouble() * 110; }
                if (whistleLeft[b] > 0) {
                    whistleLeft[b] -= dt;
                    int w = (int) (45 * dt + rnd.nextDouble());
                    // Pfeife vor dem Schornstein, etwas niedriger
                    double px0 = m[0] * (lx + 1.2) + m[4] * 6.4 + m[12];
                    double py0 = m[1] * (lx + 1.2) + m[5] * 6.4 + m[13];
                    double pz0 = m[2] * (lx + 1.2) + m[6] * 6.4 + m[14];
                    for (int q = 0; q < w; q++)
                        spawn(px0, py0, pz0, bt.hx * bt.v * 0.5 + (rnd.nextDouble() - 0.5) * 1.5, 4.5 + rnd.nextDouble() * 2.0,
                                bt.hz * bt.v * 0.5 + (rnd.nextDouble() - 0.5) * 1.5, 2.0 + rnd.nextDouble() * 1.6, 0.3, 2.8, 0.6, 1);
                }
            }
        }
    }

    /** Sortiert und lädt die Teilchen; zeichnet sie mit dem aktiven Programm. */
    void draw(GL4 gl, double[] cam) {
        if (n == 0) return;
        for (int i = 0; i < n; i++) {
            double dx = px[i] - cam[0], dy = py[i] - cam[1], dz = pz[i] - cam[2];
            float d = (float) (dx * dx + dy * dy + dz * dz);
            order[i] = ((long) Float.floatToIntBits(d) << 32) | i;
        }
        Arrays.sort(order, 0, n);
        buf.clear();
        for (int j = n - 1; j >= 0; j--) {            // weit zuerst
            int i = (int) (order[j] & 0xFFFFFFFFL);
            float a = age[i];
            float s = size0[i] + (size1[i] - size0[i]) * (float) Math.sqrt(a);
            buf.put(px[i]).put(py[i]).put(pz[i]).put(s);
            buf.put(a).put(kind[i]).put(seed[i]).put(alpha[i]);
        }
        buf.flip();
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, (long) n * FLOATS * 4, buf);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLE_STRIP, 0, 4, n);
    }
}
