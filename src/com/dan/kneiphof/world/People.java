package com.dan.kneiphof.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fußgänger auf den Brücken, in der Kleidung um 1910: Herren mit Zylinder, Hut oder Mütze, Frauen in langen
 * Röcken, Kinder. Jeder geht auf einer Spur über Rampe und Fahrbahn und kehrt am Ende um. Wird eine Brücke
 * geöffnet oder angefordert, bleiben alle vor den Klappen stehen; wer schon auf den Klappen steht, geht noch weiter.
 * <p>Bezugssystem eines Figurennetzes: x voraus, y oben, z rechts, Füße bei y = 0.</p>
 */
public final class People {
    public static final int VARIANTS = 6;

    public static final class Person {
        public final int bridge, variant;
        double u, w, speed, phase;
        int dir;
        public double x, y, z, hx = 1, hz = 0, walk;
        public boolean waiting;
        Person(int bridge, int variant) { this.bridge = bridge; this.variant = variant; }
    }

    public final List<Person> list = new ArrayList<>();
    private final Bridges br;
    private final double[] uMin, uMax;
    private static final double ZONE = Bridges.LEAF + Bridges.GAP / 2 + 3.3;

    public People(Bridges br) {
        this.br = br;
        uMin = new double[br.list.size()];
        uMax = new double[br.list.size()];
        Random rnd = new Random(1910);
        for (int i = 0; i < br.list.size(); i++) {
            Bridges.Bridge b = br.list.get(i);
            uMin[i] = b.sMin - ramp(b, 0);
            uMax[i] = b.sMax + ramp(b, 1);
            int n = 14;
            for (int k = 0; k < n; k++) {
                Person p = new Person(i, rnd.nextInt(VARIANTS));
                p.dir = rnd.nextBoolean() ? 1 : -1;
                double hw = b.width / 2;
                p.w = (rnd.nextDouble() * 2 - 1) * (hw - 1.0);
                p.u = uMin[i] + rnd.nextDouble() * (uMax[i] - uMin[i]);
                p.speed = (p.variant == 5 ? 0.9 : 1.15) + rnd.nextDouble() * 0.45;
                p.phase = rnd.nextDouble() * 10;
                list.add(p);
                place(p, 0);
            }
        }
    }

    /** Länge der Auffahrt hinter dem Ufer e (0 West/−u, 1 Ost/+u), wie im Bau der Brücke. */
    private static double ramp(Bridges.Bridge b, int e) {
        double sShore = e == 0 ? b.sMin : b.sMax;
        int dir = e == 0 ? -1 : 1;
        double hLand = Site.height(b.wx(sShore + dir * 6, 0), b.wz(sShore + dir * 6, 0));
        return Math.max(8, Math.min(40, Math.abs(b.deck - hLand) / 0.07));
    }

    private double height(Bridges.Bridge b, double u, int i) {
        if (u >= b.sMin && u <= b.sMax) return b.deck;
        int e = u < b.sMin ? 0 : 1;
        double sShore = e == 0 ? b.sMin : b.sMax;
        int dir = e == 0 ? -1 : 1;
        double hLand = Site.height(b.wx(sShore + dir * 6, 0), b.wz(sShore + dir * 6, 0));
        double lr = Math.abs((e == 0 ? uMin[i] : uMax[i]) - sShore);
        double f = Math.min(1, Math.abs(u - sShore) / Math.max(1, lr));
        return b.deck + (hLand - b.deck) * f;
    }

    private void place(Person p, double dt) {
        Bridges.Bridge b = br.list.get(p.bridge);
        p.x = b.wx(p.u, p.w);
        p.z = b.wz(p.u, p.w);
        p.y = height(b, p.u, p.bridge);
        p.hx = p.dir * b.c;
        p.hz = p.dir * b.s;
    }

    /** Weiterrechnen. open: Öffnung jeder Brücke 0..1; need: Schiff will durch. */
    public void update(double dt, float[] open, boolean[] need) {
        for (Person p : list) {
            Bridges.Bridge b = br.list.get(p.bridge);
            boolean closed = open[p.bridge] > 0.003 || (p.bridge < need.length && need[p.bridge]);
            double v = p.speed;
            p.waiting = false;
            double a = Math.abs(p.u);
            // zur Brückenmitte hin und kurz vor den Klappen: warten (Sperre)
            if (closed && p.dir * p.u < 0 && a <= ZONE + 0.5 && a > ZONE - 0.02) { v = 0; p.waiting = true; }
            p.u += p.dir * v * dt;
            if (p.u > uMax[p.bridge]) { p.u = uMax[p.bridge]; p.dir = -1; p.w = -p.w; }
            if (p.u < uMin[p.bridge]) { p.u = uMin[p.bridge]; p.dir = 1; p.w = -p.w; }
            if (v > 0) p.walk += dt * (1.9 + 0.5 * p.speed);
            place(p, dt);
        }
    }

    /** Bewegungsmatrix (Spalten): x voraus, y oben, z rechts; leichtes Wippen beim Gehen. */
    public float[] matrix(Person p) {
        double fx = p.hx, fz = p.hz;
        double bob = p.waiting ? 0 : 0.025 * Math.abs(Math.sin(p.walk * Math.PI));
        return new float[]{
                (float) fx, 0f, (float) fz, 0f,
                0f, 1f, 0f, 0f,
                (float) -fz, 0f, (float) fx, 0f,
                (float) p.x, (float) (p.y + bob), (float) p.z, 1f};
    }

    /** 0 oder 1: welches der beiden Gehbilder. */
    public static int frame(Person p) { return p.waiting ? 0 : ((int) Math.floor(p.walk) & 1); }

    /** Alle Personen an ihrer Stelle in ein festes Netz (Prüfwerkzeuge ohne Grafikkarte). */
    public MeshBuilder bake() {
        MeshBuilder out = new MeshBuilder();
        MeshBuilder[] fig = new MeshBuilder[VARIANTS * 2];
        for (int i = 0; i < fig.length; i++) fig[i] = figure(i / 2, i & 1);
        for (Person p : list) {
            float[] m = matrix(p);
            MeshBuilder f = fig[p.variant * 2 + frame(p)];
            float[] v = f.vertices();
            int[] idx = f.indices();
            int base = out.vertexCount();
            int n = v.length / MeshBuilder.STRIDE;
            for (int k = 0; k < n; k++) {
                int o = k * MeshBuilder.STRIDE;
                double[] a = xf(m, v[o], v[o + 1], v[o + 2], 1), nn = xf(m, v[o + 3], v[o + 4], v[o + 5], 0), t = xf(m, v[o + 6], v[o + 7], v[o + 8], 0);
                out.vertex(a[0], a[1], a[2], nn[0], nn[1], nn[2], t[0], t[1], t[2], v[o + 9], v[o + 10], (int) v[o + 11], v[o + 12]);
            }
            for (int k = 0; k < idx.length; k += 3) out.tri(base + idx[k], base + idx[k + 1], base + idx[k + 2]);
        }
        return out;
    }

    private static double[] xf(float[] m, double x, double y, double z, double w) {
        return new double[]{m[0] * x + m[4] * y + m[8] * z + m[12] * w, m[1] * x + m[5] * y + m[9] * z + m[13] * w, m[2] * x + m[6] * y + m[10] * z + m[14] * w};
    }

    // ------------------------------------------------------------------ Figuren

    private static final int SKIN = Material.PLASTER0, DARK = Material.PLASTER0 + 5, OLIVE = Material.PLASTER0 + 2,
            BROWN = Material.PLASTER0 + 3, GREY = Material.PLASTER0 + 4, SAND = Material.PLASTER0 + 1;

    /** Figur der Art variant im Gehbild frame (0, 1). */
    public static MeshBuilder figure(int variant, int frame) {
        MeshBuilder m = new MeshBuilder();
        double sc = variant == 5 ? 0.66 : 1.0;
        double sw = (frame == 0 ? 1 : -1) * 0.16 * sc;      // Schrittweite der Beine
        boolean woman = variant == 3 || variant == 4;
        int coat = variant == 0 ? DARK : variant == 1 ? OLIVE : variant == 2 ? GREY : variant == 3 ? BROWN : variant == 4 ? SAND : BROWN;
        int trousers = variant == 1 ? DARK : variant == 5 ? OLIVE : DARK;
        // Beine oder Rock
        if (woman) {
            m.cylinder(0, 0.08 * sc, 0, 0.27 * sc, 0.86 * sc, 10, coat, 0.6);
        } else {
            m.box(sw - 0.09 * sc, 0.0, -0.17 * sc, sw + 0.09 * sc, 0.88 * sc, -0.02 * sc, trousers, 0.7);
            m.box(-sw - 0.09 * sc, 0.0, 0.02 * sc, -sw + 0.09 * sc, 0.88 * sc, 0.17 * sc, trousers, 0.7);
        }
        // Rumpf, Arme, Kopf
        m.box(-0.12 * sc, 0.86 * sc, -0.22 * sc, 0.12 * sc, 1.46 * sc, 0.22 * sc, coat, 0.9);
        double as = -sw * 0.8;
        m.box(as - 0.06 * sc, 0.80 * sc, -0.30 * sc, as + 0.06 * sc, 1.42 * sc, -0.22 * sc, coat, 0.9);
        m.box(-as - 0.06 * sc, 0.80 * sc, 0.22 * sc, -as + 0.06 * sc, 1.42 * sc, 0.30 * sc, coat, 0.9);
        m.box(-0.07 * sc, 1.46 * sc, -0.07 * sc, 0.07 * sc, 1.52 * sc, 0.07 * sc, SKIN, 1);
        m.box(-0.10 * sc, 1.52 * sc, -0.09 * sc, 0.10 * sc, 1.72 * sc, 0.09 * sc, SKIN, 1);
        // Kopfbedeckung
        switch (variant) {
            case 0: m.cylinder(0, 1.70 * sc, 0, 0.115 * sc, 0.26 * sc, 10, Material.IRON, 1); m.cylinder(0, 1.70 * sc, 0, 0.19 * sc, 0.02 * sc, 10, Material.IRON, 1); break;
            case 1: m.box(-0.12 * sc, 1.70 * sc, -0.11 * sc, 0.16 * sc, 1.77 * sc, 0.11 * sc, DARK, 1); break;
            case 2: m.cylinder(0, 1.70 * sc, 0, 0.12 * sc, 0.10 * sc, 10, DARK, 1); m.cylinder(0, 1.70 * sc, 0, 0.18 * sc, 0.015 * sc, 10, DARK, 1); break;
            case 3: m.cylinder(0, 1.70 * sc, 0, 0.17 * sc, 0.06 * sc, 10, DARK, 1); break;
            case 4: m.box(-0.11 * sc, 1.69 * sc, -0.11 * sc, 0.11 * sc, 1.80 * sc, 0.11 * sc, BROWN, 1); break;
            default: m.box(-0.11 * sc, 1.70 * sc, -0.10 * sc, 0.12 * sc, 1.75 * sc, 0.10 * sc, GREY, 1);
        }
        return m;
    }
}
