package com.dan.kneiphof.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Silbermöwen, die in Schwärmen über Pregel und Dom kreisen. Jede Möwe fliegt auf einem Kreis um einen
 * Mittelpunkt, neigt sich in die Kurve, segelt meist und schlägt zwischendurch mit den Flügeln.
 * <p>Bezugssystem des Netzes: x voraus, y oben, z rechts; Flügelschlag in vier Bildern, dazu ein Segelbild.</p>
 */
public final class Gulls {
    public static final int FRAMES = 5;     // 0..3 Flügelschlag, 4 Segeln

    public static final class Gull {
        final double cx, cz, r, h, w, ph, bob;
        public double x, y, z, hx = 1, hz = 0, bank;
        public int frame = 4;
        Gull(double cx, double cz, double r, double h, double w, double ph, double bob) {
            this.cx = cx; this.cz = cz; this.r = r; this.h = h; this.w = w; this.ph = ph; this.bob = bob;
        }
    }

    public final List<Gull> list = new ArrayList<>();

    public Gulls() {
        Random rnd = new Random(1910);
        // Mittelpunkt, Radius, Höhe, Anzahl
        double[][] flocks = {
                {-60, -40, 70, 24, 5}, {150, 70, 55, 19, 4}, {70, -130, 95, 36, 5}, {320, 30, 120, 44, 3}, {-190, 60, 110, 30, 2}};
        for (double[] f : flocks) {
            for (int k = 0; k < (int) f[4]; k++) {
                double dir = rnd.nextBoolean() ? 1 : -1;
                double v = 8.5 + rnd.nextDouble() * 2.5;
                double r = f[2] * (0.75 + rnd.nextDouble() * 0.5);
                list.add(new Gull(f[0], f[1], r, f[3] + rnd.nextDouble() * 12, dir * v / r, rnd.nextDouble() * 6.283, 1 + rnd.nextDouble() * 2.5));
            }
        }
    }

    public void update(double t) {
        for (Gull g : list) {
            double a = g.w * t + g.ph;
            g.x = g.cx + g.r * Math.cos(a);
            g.z = g.cz + g.r * Math.sin(a);
            g.y = g.h + g.bob * Math.sin(0.35 * t + g.ph * 3);
            double sgn = Math.signum(g.w);
            double tx = -Math.sin(a) * sgn, tz = Math.cos(a) * sgn;
            g.hx = tx; g.hz = tz;
            double v = Math.abs(g.w) * g.r;
            // zum Kreismittelpunkt hin geneigt: Mittelpunkt liegt rechts von der Flugrichtung, wenn dot > 0
            double rx = -tz, rz = tx;
            double side = (g.cx - g.x) * rx + (g.cz - g.z) * rz;
            g.bank = Math.signum(side) * Math.atan(v * v / (g.r * 9.81));
            // Flügelschlag: alle ~11 s drei Sekunden lang mit 3 Hz, sonst Segeln
            double cyc = (t * 0.09 + g.ph * 0.37) % 1.0;
            g.frame = cyc < 0.28 ? (int) Math.floor(t * 3.0 + g.ph) & 3 : 4;
        }
    }

    public float[] matrix(Gull g) {
        double fx = g.hx, fz = g.hz;
        double rx = -fz, rz = fx;
        double c = Math.cos(g.bank), s = Math.sin(g.bank);
        return new float[]{
                (float) fx, 0f, (float) fz, 0f,
                (float) (rx * s), (float) c, (float) (rz * s), 0f,
                (float) (rx * c), (float) -s, (float) (rz * c), 0f,
                (float) g.x, (float) g.y, (float) g.z, 1f};
    }

    private static void wing(MeshBuilder m, double[] a, double[] b, double[] c, double[] d, int mat) {
        m.quad(a, b, c, d, mat);
        m.quad(d, c, b, a, mat);
    }

    /** Möwe im Flügelbild frame (0..3 Schlag, 4 Segeln). */
    public static MeshBuilder figure(int frame) {
        MeshBuilder m = new MeshBuilder();
        int F = Material.FEATHER, DK = Material.PLASTER0 + 5, GR = Material.PLASTER0 + 4, BEAK = Material.PLASTER0 + 1;
        // Rumpf (Keil), Hals, Kopf, Schnabel, Schwanz
        m.box(-0.20, -0.07, -0.07, 0.17, 0.07, 0.07, F, 1);
        m.box(0.17, 0.0, -0.045, 0.26, 0.10, 0.045, F, 1);
        m.box(0.26, 0.04, -0.045, 0.34, 0.12, 0.045, F, 1);
        m.box(0.34, 0.06, -0.015, 0.40, 0.085, 0.015, BEAK, 1);
        m.box(-0.32, -0.03, -0.05, -0.20, 0.03, 0.05, F, 1);
        double[] ang = {0.70, 0.18, -0.50, 0.18, 0.12};
        double a = ang[frame];
        double ca = Math.cos(a), sa = Math.sin(a);
        for (int side = -1; side <= 1; side += 2) {
            // Innenflügel bis 0,34 m, Handschwinge bis 0,62 m mit dunkler Spitze
            double span1 = 0.34, span2 = 0.30;
            double a2 = a * (frame == 4 ? 0.4 : 1.6) - (frame == 4 ? 0.05 : 0.0);
            double c2 = Math.cos(a2), s2 = Math.sin(a2);
            double[] r0 = {0.08, 0.0, side * 0.07}, r1 = {-0.14, 0.0, side * 0.07};
            double[] m0 = {0.02, span1 * sa, side * (0.07 + span1 * ca)}, m1 = {-0.18, span1 * sa, side * (0.07 + span1 * ca)};
            double yz = m0[1], zz = Math.abs(m0[2]);
            double[] t0 = {-0.07, yz + span2 * s2, side * (zz + span2 * c2)}, t1 = {-0.20, yz + span2 * s2, side * (zz + span2 * c2)};
            if (side > 0) {
                wing(m, r1, r0, m0, m1, GR);
                wing(m, m1, m0, t0, t1, DK);
            } else {
                wing(m, r0, r1, m1, m0, GR);
                wing(m, m0, m1, t1, t0, DK);
            }
        }
        return m;
    }

    /** Alle Möwen zum Zeitpunkt t in ein festes Netz (Prüfwerkzeuge ohne Grafikkarte). */
    public MeshBuilder bake(double t) {
        update(t);
        MeshBuilder out = new MeshBuilder();
        MeshBuilder[] fig = new MeshBuilder[FRAMES];
        for (int i = 0; i < FRAMES; i++) fig[i] = figure(i);
        for (Gull g : list) {
            float[] m = matrix(g);
            float[] v = fig[g.frame].vertices();
            int[] idx = fig[g.frame].indices();
            int base = out.vertexCount();
            for (int k = 0; k < v.length / MeshBuilder.STRIDE; k++) {
                int o = k * MeshBuilder.STRIDE;
                double[] a = xf(m, v[o], v[o + 1], v[o + 2], 1), n = xf(m, v[o + 3], v[o + 4], v[o + 5], 0), tg = xf(m, v[o + 6], v[o + 7], v[o + 8], 0);
                out.vertex(a[0], a[1], a[2], n[0], n[1], n[2], tg[0], tg[1], tg[2], v[o + 9], v[o + 10], (int) v[o + 11], v[o + 12]);
            }
            for (int k = 0; k < idx.length; k += 3) out.tri(base + idx[k], base + idx[k + 1], base + idx[k + 2]);
        }
        return out;
    }

    private static double[] xf(float[] m, double x, double y, double z, double w) {
        return new double[]{m[0] * x + m[4] * y + m[8] * z + m[12] * w, m[1] * x + m[5] * y + m[9] * z + m[13] * w, m[2] * x + m[6] * y + m[10] * z + m[14] * w};
    }
}
