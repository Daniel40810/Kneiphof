package com.dan.kneiphof.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Die sieben Brücken von Königsberg um 1910, nach dem Plan an ihren Stellen über die Flussarme:
 * Krämer-, Schmiede-, Holz-, Grüne, Köttel-, Honig- und Hohe Brücke. Jede ist eine Holzjochbrücke mit
 * Fahrbahn aus Bohlen, eisernem Geländer und Gaslaternen und einer Durchfahrt in der Mitte, die zwei
 * Klappen schließen (Doppelklappbrücke mit Gegengewicht unter der Fahrbahn). Die Klappen sind eigene
 * Netze mit dem Drehpunkt im Ursprung, damit der Renderer sie bewegen kann.
 */
public final class Bridges {
    /** Länge einer Klappe, Lücke dazwischen, Schwanzlänge hinter dem Drehpunkt. */
    public static final double LEAF = 7.0, GAP = 0.25, TAIL = 2.5;
    /** Größter Öffnungswinkel (Bogenmaß). */
    public static final double MAX_ANGLE = Math.toRadians(72);

    public static final class Bridge {
        public final String name;
        public double cx, cz, angle, c, s, sMin, sMax, width, deck;
        public final MeshBuilder leaf = new MeshBuilder();
        /** Drehpunkte der beiden Klappen (x, y, z) und Drehung um die Hochachse. */
        public final double[][] pivot = new double[2][3];
        public final double[] yaw = new double[2];
        Bridge(String name) { this.name = name; }
        public double wx(double u, double w) { return cx + u * c - w * s; }
        public double wz(double u, double w) { return cz + u * s + w * c; }
    }

    private static final String[] NAMES = {"Krämerbrücke", "Schmiedebrücke", "Holzbrücke", "Grüne Brücke", "Köttelbrücke", "Honigbrücke", "Hohe Brücke"};
    /** Ungefähre Lage nach dem Plan (Weltmeter) und Fahrbahnbreite. */
    private static final double[][] SPOT = {{-60, -190, 8.0}, {92, -59, 8.0}, {297, -11, 9.0}, {-205, 76, 8.0}, {-54, 119, 7.5}, {225, 162, 6.5}, {378, 864, 10.0}};

    public static String[] names() { return NAMES.clone(); }

    public final List<Bridge> list = new ArrayList<>();
    public final MeshBuilder statics = new MeshBuilder();

    public Bridges() {
        for (int i = 0; i < NAMES.length; i++) {
            Bridge b = locate(NAMES[i], SPOT[i][0], SPOT[i][1], SPOT[i][2]);
            list.add(b);
            build(b);
        }
    }

    /** Den Arm an der Stelle suchen, die Querrichtung nehmen und die Ufer links und rechts messen. */
    private static Bridge locate(String name, double gx, double gz, double width) {
        double bx = 0, bz = 0, bd = 1e18, dx = 1, dz = 0;
        for (Site.Arm arm : Site.ARMS) {
            double[][] p = arm.pts;
            for (int i = 0; i + 1 < p.length; i++)
                for (double t = 0; t <= 1; t += 0.01) {
                    double x = p[i][0] + t * (p[i + 1][0] - p[i][0]), z = p[i][1] + t * (p[i + 1][1] - p[i][1]);
                    double d = Math.hypot(x - gx, z - gz);
                    if (d < bd) { bd = d; bx = x; bz = z; dx = p[i + 1][0] - p[i][0]; dz = p[i + 1][1] - p[i][1]; }
                }
        }
        double l = Math.hypot(dx, dz);
        dx /= l; dz /= l;
        Bridge b = new Bridge(name);
        b.cx = bx; b.cz = bz;
        double nx = -dz, nz = dx;
        b.angle = Math.atan2(nz, nx);
        b.c = Math.cos(b.angle); b.s = Math.sin(b.angle);
        double s1 = 0, s2 = 0;
        while (Site.water(bx + nx * s1, bz + nz * s1) < 0 && s1 < 200) s1 += 0.25;
        while (Site.water(bx - nx * s2, bz - nz * s2) < 0 && s2 < 200) s2 += 0.25;
        b.sMin = -s2; b.sMax = s1;
        b.width = width;
        double hA = Site.height(b.wx(b.sMin - 6, 0), b.wz(b.sMin - 6, 0)), hB = Site.height(b.wx(b.sMax + 6, 0), b.wz(b.sMax + 6, 0));
        b.deck = Math.min(Math.max(Math.max(hA, hB) + 0.9, 2.9), Math.min(hA, hB) + 3.6);
        b.deck = Math.max(b.deck, 2.9);
        return b;
    }

    // ------------------------------------------------------------------------------------ Bau
    private void build(Bridge b) {
        MeshBuilder m = statics;
        Obj o = new Obj(m, b.cx, b.cz, b.angle);
        double W = b.width, hw = W / 2, y = b.deck;
        double pv = LEAF + GAP / 2;           // Drehpunkt der Klappen: ±pv
        // Auffahrten und Widerlager an den Ufern
        for (int e = 0; e < 2; e++) {
            double sShore = e == 0 ? b.sMin : b.sMax;
            int dir = e == 0 ? -1 : 1;                                   // vom Wasser weg
            double hLand = Site.height(b.wx(sShore + dir * 6, 0), b.wz(sShore + dir * 6, 0));
            double lr = Math.max(8, Math.min(40, Math.abs(y - hLand) / 0.07));
            double u0 = dir < 0 ? sShore - lr : sShore, u1 = dir < 0 ? sShore : sShore + lr;
            double yA = dir < 0 ? hLand : y, yB = dir < 0 ? y : hLand;   // Höhe bei u0 und u1
            double yb = Math.min(hLand, y) - 1.4;
            // Bohlenbelag
            double[] a = o.p(u0, yA, hw), bb = o.p(u1, yB, hw), c2 = o.p(u1, yB, -hw), d = o.p(u0, yA, -hw);
            m.quad(a, bb, c2, d, Material.WOOD, u0, -hw, 1, 1);
            // Stützmauern beidseits
            for (int sd = -1; sd <= 1; sd += 2) {
                double w = sd * hw;
                double[] p0 = o.p(u0, yb, w), p1 = o.p(u1, yb, w), p2 = o.p(u1, yB, w), p3 = o.p(u0, yA, w);
                if (sd > 0) m.quad(p0, p1, p2, p3, Material.QUAY, u0, yb, 0.8, 1);
                else m.quad(p1, p0, p3, p2, Material.QUAY, u0, yb, 0.8, 1);
            }
            // Stirnseite am Landende
            double ue = dir < 0 ? u0 : u1, ye = dir < 0 ? yA : yB;
            {
                double[] p0 = o.p(ue, yb, -hw), p1 = o.p(ue, yb, hw), p2 = o.p(ue, ye, hw), p3 = o.p(ue, ye, -hw);
                if (dir < 0) m.quad(p0, p1, p2, p3, Material.QUAY, 0, yb, 0.8, 1);
                else m.quad(p1, p0, p3, p2, Material.QUAY, 0, yb, 0.8, 1);
            }
            // Widerlager im Wasser: Granit vom Grund bis unter die Fahrbahn
            double wa = dir < 0 ? sShore - 0.2 : sShore - 0.9, wb = dir < 0 ? sShore + 0.9 : sShore + 0.2;
            o.box(wa, -hw - 0.5, wb, hw + 0.5, -2.8, y - 0.36, Material.QUAY, 0.8);
            // Geländer auf der Auffahrt
            railing(o, u0, yA, u1, yB, hw - 0.08, 1.6, false);
            railing(o, u0, yA, u1, yB, -hw + 0.08, 1.6, false);
            // Wärterhäuschen auf der Seite des Ufers mit dem ersten Wasserlauf
            if (e == 0) keeperHouse(b, o, sShore - lr - 3.0, hw + 3.2);
        }
        // feste Felder von den Widerlagern bis zu den Drehpunkten, auf Jochen
        for (int e = 0; e < 2; e++) {
            int sg = e == 0 ? -1 : 1;
            double uShore = e == 0 ? b.sMin : b.sMax;
            double uEnd = sg * (pv + 3.0);                // letztes Joch hinter dem Schwanz der Klappe
            double span = Math.abs(uEnd - uShore);
            int n = (int) Math.ceil(span / 6.5);
            double step = (uEnd - uShore) / n;
            // Joche
            for (int k = 1; k <= n; k++) joch(o, uShore + k * step, hw, y);
            // Fahrbahn über die ganze Länge zwischen Ufer und Drehpunkt
            double fa = Math.min(uShore, sg * pv), fb = Math.max(uShore, sg * pv);
            deck(o, fa, fb, hw, y);
            // Geländer des festen Feldes
            railing(o, fa, y, fb, y, hw - 0.08, 1.5, true);
            railing(o, fa, y, fb, y, -hw + 0.08, 1.5, true);
        }
        // Lager an den Drehpunkten, Gaslaternen
        for (int e = -1; e <= 1; e += 2) {
            for (int sd = -1; sd <= 1; sd += 2) {
                o.box(e * pv - 0.5, sd * (hw + 0.1) - 0.3, e * pv + 0.5, sd * (hw + 0.1) + 0.3, y - 1.2, y - 0.15, Material.IRON, 0.9);
                o.box(e * pv - 0.35, sd * (hw + 0.1) - 0.45, e * pv + 0.35, sd * (hw + 0.1) + 0.45, y - 0.9, y - 0.5, Material.IRON, 1);
            }
            Banks.lantern(m, o.wx(e * (pv + 4.0), hw + 0.35), y, o.wz(e * (pv + 4.0), hw + 0.35));
            Banks.lantern(m, o.wx(e * (b.sMax - 2.0) , -hw - 0.35), y, o.wz(e * (b.sMax - 2.0), -hw - 0.35));
        }
        buildLeaf(b);
        // Drehpunkte für den Renderer
        for (int e = 0; e < 2; e++) {
            double u = e == 0 ? -pv : pv;
            b.pivot[e][0] = o.wx(u, 0); b.pivot[e][1] = y; b.pivot[e][2] = o.wz(u, 0);
            b.yaw[e] = b.angle + (e == 0 ? 0 : Math.PI);
        }
    }

    /** Holzjoch: fünf Pfähle, Holm, Strebe; reicht vom Grund bis unter die Längsträger. */
    private void joch(Obj o, double u, double hw, double y) {
        MeshBuilder m = o.m;
        double cap = y - 0.78;
        int n = 5;
        for (int i = 0; i < n; i++) {
            double w = -hw + 0.45 + i * (2 * hw - 0.9) / (n - 1);
            m.cylinder(o.wx(u, w), -4.0, o.wz(u, w), 0.25, cap - -4.0, 10, Material.WOOD, 0.9);
        }
        o.box(u - 0.28, -hw - 0.1, u + 0.28, hw + 0.1, cap, cap + 0.34, Material.WOOD, 1);
        // Eisbrecher stromauf und stromab: schräge Pfähle
        for (int sd = -1; sd <= 1; sd += 2) {
            o.box(u - 0.2, sd * (hw + 0.55) - 0.2, u + 0.2, sd * (hw + 0.55) + 0.2, -3.0, y - 0.8, Material.WOOD, 0.9);
        }
    }

    /** Fahrbahn: Längsträger und Bohlenbelag zwischen den Längen a und b (Achsmaß). */
    private void deck(Obj o, double a, double b, double hw, double y) {
        for (int i = 0; i < 4; i++) {
            double w = -hw + 0.6 + i * (2 * hw - 1.2) / 3;
            o.box(a, w - 0.16, b, w + 0.16, y - 0.82, y - 0.34, Material.WOOD, 1);
        }
        o.box(a, -hw, b, hw, y - 0.34, y, Material.WOOD, 1);
        // Radabweiser
        o.box(a, hw - 0.4, b, hw - 0.12, y, y + 0.17, Material.GRANITE, 1);
        o.box(a, -hw + 0.12, b, -hw + 0.4, y, y + 0.17, Material.GRANITE, 1);
    }

    /** Eisengeländer: Pfosten alle dist Meter, Handlauf und Mittelstab. Höhe folgt der Fahrbahn (ya bei ua, yb bei ub). */
    private static void railing(Obj o, double ua, double ya, double ub, double yb, double w, double dist, boolean flat) {
        double len = ub - ua;
        int n = Math.max(1, (int) Math.round(len / dist));
        for (int i = 0; i <= n; i++) {
            double u = ua + len * i / n, yy = ya + (yb - ya) * i / n;
            o.box(u - 0.04, w - 0.04, u + 0.04, w + 0.04, yy, yy + 1.1, Material.IRON, 1);
        }
        for (int i = 0; i < n; i++) {
            double u0 = ua + len * i / n, u1 = ua + len * (i + 1) / n;
            double y0 = ya + (yb - ya) * i / n, y1 = ya + (yb - ya) * (i + 1) / n;
            slat(o, u0, y0 + 1.05, u1, y1 + 1.05, w, 0.05);
            slat(o, u0, y0 + 0.55, u1, y1 + 0.55, w, 0.03);
        }
    }

    /** Schräger Stab zwischen (u0, y0) und (u1, y1) in der Ebene w, Dicke 2·t. */
    private static void slat(Obj o, double u0, double y0, double u1, double y1, double w, double t) {
        MeshBuilder m = o.m;
        double[][] q = {{u0, y0 - t}, {u1, y1 - t}, {u1, y1 + t}, {u0, y0 + t}};
        double[] p00 = o.p(q[0][0], q[0][1], w - t), p01 = o.p(q[0][0], q[0][1], w + t);
        double[] p10 = o.p(q[1][0], q[1][1], w - t), p11 = o.p(q[1][0], q[1][1], w + t);
        double[] p20 = o.p(q[2][0], q[2][1], w - t), p21 = o.p(q[2][0], q[2][1], w + t);
        double[] p30 = o.p(q[3][0], q[3][1], w - t), p31 = o.p(q[3][0], q[3][1], w + t);
        m.quad(p01, p11, p21, p31, Material.IRON, 0, 0, 1, 1);          // +w
        m.quad(p10, p00, p30, p20, Material.IRON, 0, 0, 1, 1);          // −w
        m.quad(p30, p31, p21, p20, Material.IRON, 0, 0, 1, 1);          // oben
        m.quad(p00, p10, p11, p01, Material.IRON, 0, 0, 1, 1);          // unten
    }

    /** Wärterhaus: kleines Backsteinhaus mit Walmdach neben der Auffahrt. */
    private void keeperHouse(Bridge b, Obj o, double u, double w) {
        double g = Site.height(o.wx(u, w), o.wz(u, w));
        if (Site.water(o.wx(u, w), o.wz(u, w)) < 2.5) return;
        Obj h = new Obj(o.m, o.wx(u, w), o.wz(u, w), b.angle);
        h.box(-1.9, -1.6, 1.9, 1.6, g - 0.4, g + 0.5, Material.GRANITE, 0.6);
        h.wallsOnly(-1.8, -1.5, 1.8, 1.5, g + 0.5, g + 3.0, Material.BRICK, 0.7);
        h.hipRoof(-1.8, -1.5, 1.8, 1.5, g + 3.0, g + 4.7, 0.4, Material.SLATE);
        h.rect(0, 1.5, 0, -0.5, 0.5, g + 0.5, g + 2.4, 0.02, Material.WOOD, 0.9);
        h.rect(-1.0, 1.5, 0, -0.35, 0.35, g + 1.3, g + 2.3, 0.03, Material.PANE_LIT, 1);
        h.rect(1.0, 1.5, 0, -0.35, 0.35, g + 1.3, g + 2.3, 0.03, Material.PANE, 1);
        h.box(0.5, -0.5, 1.0, 0.0, g + 3.0, g + 5.3, Material.BRICK, 1);
    }

    // ------------------------------------------------------------------------------------ Klappe
    /**
     * Eine Klappe im Bezugssystem des Drehpunkts: x läuft zur Mitte der Brücke, y nach oben, z quer. Die Fahrbahn
     * liegt mit der Oberkante auf y = 0; hinter dem Drehpunkt der Schwanz mit dem Gegengewicht.
     */
    private void buildLeaf(Bridge b) {
        Obj o = new Obj(b.leaf, 0, 0, 0);
        double hw = b.width / 2;
        // Bohlenbelag und Längsträger, bis in den Schwanz
        o.box(0.03, -hw, LEAF, hw, -0.34, 0.0, Material.WOOD, 1);
        o.box(-TAIL, -hw, 0.03, hw, -0.34, -0.0, Material.WOOD, 1);
        for (int i = 0; i < 4; i++) {
            double w = -hw + 0.6 + i * (2 * hw - 1.2) / 3;
            o.box(-TAIL, w - 0.16, LEAF, w + 0.16, -0.82, -0.34, Material.WOOD, 1);
        }
        // Hauptträger aus Eisen und Querverbände
        for (int sd = -1; sd <= 1; sd += 2) {
            o.box(-TAIL, sd * (hw - 0.55) - 0.1, LEAF, sd * (hw - 0.55) + 0.1, -1.4, -0.34, Material.IRON, 1);
            o.box(-0.15, sd * (hw + 0.02) - 0.14, 0.15, sd * (hw + 0.02) + 0.14, -0.5, 0.0, Material.IRON, 1);   // Zapfen
        }
        for (double x = -TAIL + 0.2; x < LEAF; x += 1.6)
            o.box(x - 0.05, -hw + 0.55, x + 0.05, hw - 0.55, -1.2, -0.9, Material.IRON, 1);
        // Gegengewicht unter dem Schwanz
        o.box(-TAIL + 0.1, -hw + 0.9, -0.7, hw - 0.9, -2.0, -0.82, Material.GRANITE, 0.8);
        o.box(-TAIL + 0.1, -hw + 0.9, -0.7, hw - 0.9, -1.35, -1.25, Material.IRON, 1);
        // Radabweiser und Geländer
        o.box(0.03, hw - 0.4, LEAF, hw - 0.12, 0.0, 0.17, Material.GRANITE, 1);
        o.box(0.03, -hw + 0.12, LEAF, -hw + 0.4, 0.0, 0.17, Material.GRANITE, 1);
        railing(o, 0.05, 0.0, LEAF, 0.0, hw - 0.08, 1.4, true);
        railing(o, 0.05, 0.0, LEAF, 0.0, -hw + 0.08, 1.4, true);
        // Klappenende: Schlagbalken
        o.box(LEAF - 0.1, -hw, LEAF, hw, -0.5, 0.0, Material.IRON, 1);
    }

    /** Modellmatrix (spaltenweise) einer Klappe bei Öffnung angle (Bogenmaß, 0 geschlossen). */
    public float[] leafMatrix(Bridge b, int leaf, double angle) {
        double yaw = b.yaw[leaf];
        double c = Math.cos(yaw), s = Math.sin(yaw), ca = Math.cos(angle), sa = Math.sin(angle);
        // Spalten: x' = ca·ex + sa·ey, y' = −sa·ex + ca·ey, z' = ez; ex = (c,0,s), ey = (0,1,0), ez = (−s,0,c)
        return new float[]{
                (float) (ca * c), (float) sa, (float) (ca * s), 0,
                (float) (-sa * c), (float) ca, (float) (-sa * s), 0,
                (float) -s, 0, (float) c, 0,
                (float) b.pivot[leaf][0], (float) b.pivot[leaf][1], (float) b.pivot[leaf][2], 1};
    }

    /** Alle Klappen mit der Öffnung open (0..1) fest ins Netz gerechnet (für Prüfbilder). */
    public MeshBuilder bake(double open) {
        MeshBuilder out = new MeshBuilder();
        for (Bridge b : list) {
            for (int e = 0; e < 2; e++) {
                float[] m = leafMatrix(b, e, open * MAX_ANGLE);
                float[] v = b.leaf.vertices();
                int[] idx = b.leaf.indices();
                int base = out.vertexCount();
                int n = v.length / MeshBuilder.STRIDE;
                for (int k = 0; k < n; k++) {
                    int o = k * MeshBuilder.STRIDE;
                    double[] p = xf(m, v[o], v[o + 1], v[o + 2], 1), nn = xf(m, v[o + 3], v[o + 4], v[o + 5], 0), t = xf(m, v[o + 6], v[o + 7], v[o + 8], 0);
                    out.vertex(p[0], p[1], p[2], nn[0], nn[1], nn[2], t[0], t[1], t[2], v[o + 9], v[o + 10], (int) v[o + 11], v[o + 12]);
                }
                for (int k = 0; k < idx.length; k += 3) out.tri(base + idx[k], base + idx[k + 1], base + idx[k + 2]);
            }
        }
        return out;
    }

    private static double[] xf(float[] m, double x, double y, double z, double w) {
        return new double[]{m[0] * x + m[4] * y + m[8] * z + m[12] * w, m[1] * x + m[5] * y + m[9] * z + m[13] * w, m[2] * x + m[6] * y + m[10] * z + m[14] * w};
    }
}
