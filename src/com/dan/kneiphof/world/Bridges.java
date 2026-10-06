package com.dan.kneiphof.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Die sieben Brücken von Königsberg um 1910, nach dem Plan an ihren Stellen über die Flussarme:
 * Krämer-, Schmiede-, Holz-, Grüne, Köttel-, Honig- und Hohe Brücke. Jede ist eine Holzjochbrücke mit
 * Fahrbahn aus Bohlen, eisernem Geländer und Gaslaternen und einer Durchfahrt in der Mitte, die zwei
 * Klappen schließen (Doppelklappbrücke mit Gegengewicht unter der Fahrbahn). Die Klappen sind eigene
 * Netze mit dem Drehpunkt im Ursprung, damit der Renderer sie bewegen kann.
 * <p>Die Grüne Brücke ist nach Fotos um 1910 anders gebaut: eine eiserne Klappbrücke auf gemauerten
 * Strompfeilern mit Eisbrechern, Pflaster und Straßenbahngleisen auf der Fahrbahn, einem Ziergeländer mit
 * Ringen, und auf den beiden Klappenpfeilern je zwei Kandelaber auf Steinsockeln, über die Fahrbahn
 * verbunden durch einen schmiedeeisernen Portalbogen mit Strahlenfächer und Wappen.</p>
 */
public final class Bridges {
    /** Länge einer Klappe, Lücke dazwischen, Schwanzlänge hinter dem Drehpunkt. */
    public static final double LEAF = 7.0, GAP = 0.25, TAIL = 2.5;
    /** Größter Öffnungswinkel (Bogenmaß). */
    public static final double MAX_ANGLE = Math.toRadians(72);

    public static final class Bridge {
        public final String name;
        /** Eiserne Klappbrücke auf Steinpfeilern (Grüne Brücke) statt Holzjochbrücke. */
        public final boolean iron;
        public double cx, cz, angle, c, s, sMin, sMax, width, deck;
        public final MeshBuilder leaf = new MeshBuilder();
        /** Drehpunkte der beiden Klappen (x, y, z) und Drehung um die Hochachse. */
        public final double[][] pivot = new double[2][3];
        public final double[] yaw = new double[2];
        Bridge(String name, boolean iron) { this.name = name; this.iron = iron; }
        public double wx(double u, double w) { return cx + u * c - w * s; }
        public double wz(double u, double w) { return cz + u * s + w * c; }
    }

    private static final String[] NAMES = {"Krämerbrücke", "Schmiedebrücke", "Holzbrücke", "Grüne Brücke", "Köttelbrücke", "Honigbrücke", "Hohe Brücke"};
    /** Ungefähre Lage nach dem Plan (Weltmeter), Fahrbahnbreite, Bauart (0 Holzjoche, 1 Eisen auf Steinpfeilern). */
    private static final double[][] SPOT = {{-60, -190, 8.0, 0}, {92, -59, 8.0, 0}, {297, -11, 9.0, 0}, {-205, 76, 9.0, 1}, {-54, 119, 7.5, 0}, {225, 162, 6.5, 0}, {378, 864, 10.0, 0}};
    /** Eiserne Brücke: Länge des Klappenpfeilers vom Drehpunkt landwärts, Überstand der Pfeiler über die Fahrbahnkante (stromauf und stromab). */
    private static final double PIER = 4.6, PIER_OUT = 2.8;

    public static String[] names() { return NAMES.clone(); }

    public final List<Bridge> list = new ArrayList<>();
    public final MeshBuilder statics = new MeshBuilder();

    public Bridges() {
        for (int i = 0; i < NAMES.length; i++) {
            Bridge b = locate(NAMES[i], SPOT[i][0], SPOT[i][1], SPOT[i][2], SPOT[i][3] > 0.5);
            list.add(b);
            build(b);
        }
    }

    /** Den Arm an der Stelle suchen, die Querrichtung nehmen und die Ufer links und rechts messen. */
    private static Bridge locate(String name, double gx, double gz, double width, boolean iron) {
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
        Bridge b = new Bridge(name, iron);
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
            // Bohlenbelag, an der eisernen Brücke Pflaster mit Gleisen
            double[] a = o.p(u0, yA, hw), bb = o.p(u1, yB, hw), c2 = o.p(u1, yB, -hw), d = o.p(u0, yA, -hw);
            m.quad(a, bb, c2, d, b.iron ? Material.COBBLE : Material.WOOD, u0, -hw, 1, 1);
            if (b.iron) for (double w : RAILS) slat(o, u0, yA + 0.005, u1, yB + 0.005, w, 0.035);
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
            rail(b, o, u0, yA, u1, yB, hw - 0.08, 1.6);
            rail(b, o, u0, yA, u1, yB, -hw + 0.08, 1.6);
            // Wärterhäuschen auf der Seite des Ufers mit dem ersten Wasserlauf
            if (e == 0) keeperHouse(b, o, sShore - lr - 3.0, hw + 3.2);
        }
        // feste Felder von den Widerlagern bis zu den Drehpunkten, auf Jochen oder Steinpfeilern
        if (b.iron) ironSpans(b, o, hw, y, pv);
        else for (int e = 0; e < 2; e++) {
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
            if (!b.iron) Banks.lantern(m, o.wx(e * (pv + 4.0), hw + 0.35), y, o.wz(e * (pv + 4.0), hw + 0.35));
            Banks.lantern(m, o.wx(e * (b.sMax - 2.0) , -hw - 0.35), y, o.wz(e * (b.sMax - 2.0), -hw - 0.35));
        }
        if (b.iron) {
            // Kandelaber auf den Klappenpfeilern und darüber je ein Portalbogen
            double up = pv + PIER / 2, R = hw + 1.6;
            for (int e = -1; e <= 1; e += 2) {
                for (int sd = -1; sd <= 1; sd += 2) candelabrum(o, e * up, sd * R, y - 0.02);
                portal(o.at(e * up, 0, Math.PI / 2), R, y);
            }
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

    // ------------------------------------------------------------------------------------ Grüne Brücke
    /** Schienen der zwei Straßenbahngleise (Meterspur) quer zur Fahrbahnachse. */
    private static final double[] RAILS = {-2.05, -1.05, 1.05, 2.05};

    /** Geländer passend zur Bauart. */
    private static void rail(Bridge b, Obj o, double ua, double ya, double ub, double yb, double w, double dist) {
        if (b.iron) ornateRailing(o, ua, ya, ub, yb, w, dist + 0.2);
        else railing(o, ua, ya, ub, yb, w, dist, false);
    }

    /**
     * Feste Felder der eisernen Brücke: der Klappenpfeiler mit der Kammer für den Klappenschwanz, dazwischen
     * schmale Strompfeiler, darauf eiserne Blechträger mit Pflaster und Gleisen.
     */
    private void ironSpans(Bridge b, Obj o, double hw, double y, double pv) {
        double half = hw + PIER_OUT;
        for (int e = 0; e < 2; e++) {
            int sg = e == 0 ? -1 : 1;
            double uShore = e == 0 ? b.sMin : b.sMax;
            // Klappenpfeiler von der Durchfahrt bis PIER hinter den Drehpunkt
            double lo = Math.min(sg * pv, sg * (pv + PIER)), hi = Math.max(sg * pv, sg * (pv + PIER));
            pier(o, lo, hi, half, 2.4, -3.0, y - 0.32, Material.QUAY, 0.7);
            pier(o, lo - 0.15, hi + 0.15, half + 0.15, 2.55, y - 0.32, y - 0.02, Material.GRANITE, 1);
            // Strompfeiler zwischen Ufer und Klappenpfeiler, Felder bis 13 m
            double uEnd = sg * (pv + PIER);
            double span = Math.abs(uEnd - uShore);
            int n = Math.max(1, (int) Math.ceil(span / 13.0));
            double step = (uEnd - uShore) / n;
            for (int k = 1; k < n; k++) {
                double u = uShore + k * step;
                pier(o, u - 0.8, u + 0.8, hw + 0.6, 1.4, -3.0, y - 1.6, Material.QUAY, 0.7);
                pier(o, u - 0.95, u + 0.95, hw + 0.75, 1.55, y - 1.6, y - 1.35, Material.GRANITE, 1);
            }
            double fa = Math.min(uShore, sg * pv), fb = Math.max(uShore, sg * pv);
            ironDeck(o, fa, fb, hw, y);
            ornateRailing(o, fa, y, fb, y, hw - 0.08, 1.8);
            ornateRailing(o, fa, y, fb, y, -hw + 0.08, 1.8);
        }
    }

    /** Eiserne Fahrbahn: Längsträger, Blechträger als Stirn an beiden Seiten, Pflaster, Radabweiser, Gleise. */
    private static void ironDeck(Obj o, double a, double b, double hw, double y) {
        for (int i = 0; i < 4; i++) {
            double w = -hw + 0.6 + i * (2 * hw - 1.2) / 3;
            o.box(a, w - 0.12, b, w + 0.12, y - 1.35, y - 0.3, Material.IRON, 1);
        }
        o.box(a, hw - 0.02, b, hw + 0.16, y - 1.4, y + 0.06, Material.IRON, 1);
        o.box(a, -hw - 0.16, b, -hw + 0.02, y - 1.4, y + 0.06, Material.IRON, 1);
        for (double u = a + 1.2; u < b - 0.3; u += 2.4) {                       // Steifen außen am Blechträger
            o.box(u - 0.05, hw + 0.16, u + 0.05, hw + 0.24, y - 1.35, y, Material.IRON, 1);
            o.box(u - 0.05, -hw - 0.24, u + 0.05, -hw - 0.16, y - 1.35, y, Material.IRON, 1);
        }
        o.box(a, -hw, b, hw, y - 0.3, y, Material.COBBLE, 1);
        o.box(a, hw - 0.4, b, hw - 0.12, y, y + 0.17, Material.GRANITE, 1);
        o.box(a, -hw + 0.12, b, -hw + 0.4, y, y + 0.17, Material.GRANITE, 1);
        for (double w : RAILS) o.box(a, w - 0.035, b, w + 0.035, y, y + 0.03, Material.IRON, 1);
    }

    /**
     * Gemauerter Pfeiler: im Grundriss ein Rechteck u0..u1 mal ±half, stromauf und stromab mit einem spitzen
     * Eisbrecher der Länge nose, von y0 bis y1, oben geschlossen.
     */
    private static void pier(Obj o, double u0, double u1, double half, double nose, double y0, double y1, int mat, double aoFoot) {
        double um = (u0 + u1) / 2;
        double[][] ring = {{u0, -half}, {um, -half - nose}, {u1, -half}, {u1, half}, {um, half + nose}, {u0, half}};
        double run = 0;
        for (int i = 0; i < ring.length; i++) {
            double[] a = ring[i], c = ring[(i + 1) % ring.length];
            double[] out = o.dir((a[0] + c[0]) / 2 - um, (a[1] + c[1]) / 2);
            Banks.facing(o.m, o.p(a[0], y0, a[1]), o.p(c[0], y0, c[1]), o.p(c[0], y1, c[1]), o.p(a[0], y1, a[1]), out[0], out[1], mat, run, y0, aoFoot, 1);
            run += Math.hypot(c[0] - a[0], c[1] - a[1]);
        }
        double[] mid = o.p(um, y1, 0);
        for (int i = 0; i < ring.length; i++) {
            double[] a = ring[i], c = ring[(i + 1) % ring.length];
            up(o, mid, o.p(a[0], y1, a[1]), o.p(c[0], y1, c[1]), mat);
        }
    }

    /** Dreieck mit der Normalen nach oben, gleich in welcher Umlaufrichtung es kommt. */
    private static void up(Obj o, double[] p0, double[] p1, double[] p2, int mat) {
        double ex = p1[0] - p0[0], ez = p1[2] - p0[2], fx = p2[0] - p0[0], fz = p2[2] - p0[2];
        if (ez * fx - ex * fz >= 0) o.tri(p0, p1, p2, mat, 1, 1);
        else o.tri(p0, p2, p1, mat, 1, 1);
    }

    /**
     * Ziergeländer aus Gusseisen: kräftige Pfosten mit Knauf, Handlauf und Fußstab, in jedem Feld ein Ring mit
     * Speichen, oben und unten an die Stäbe angebunden.
     */
    private static void ornateRailing(Obj o, double ua, double ya, double ub, double yb, double w, double dist) {
        double len = ub - ua;
        int n = Math.max(1, (int) Math.round(len / dist));
        for (int i = 0; i <= n; i++) {
            double u = ua + len * i / n, yy = ya + (yb - ya) * i / n;
            o.box(u - 0.06, w - 0.06, u + 0.06, w + 0.06, yy, yy + 1.15, Material.IRON, 1);
            o.box(u - 0.09, w - 0.09, u + 0.09, w + 0.09, yy + 1.15, yy + 1.27, Material.IRON, 1);
        }
        for (int i = 0; i < n; i++) {
            double u0 = ua + len * i / n, u1 = ua + len * (i + 1) / n;
            double y0 = ya + (yb - ya) * i / n, y1 = ya + (yb - ya) * (i + 1) / n;
            slat(o, u0, y0 + 1.08, u1, y1 + 1.08, w, 0.05);
            slat(o, u0, y0 + 0.15, u1, y1 + 0.15, w, 0.035);
            double uc = (u0 + u1) / 2, yc = (y0 + y1) / 2 + 0.62;
            double r = Math.min(0.38, (u1 - u0) / 2 - 0.12);
            int seg = 12;
            for (int k = 0; k < seg; k++) {
                double a0 = 2 * Math.PI * k / seg, a1 = 2 * Math.PI * (k + 1) / seg;
                slat(o, uc + r * Math.cos(a0), yc + r * Math.sin(a0), uc + r * Math.cos(a1), yc + r * Math.sin(a1), w, 0.022);
            }
            for (int k = 0; k < 4; k++) {
                double a0 = Math.PI / 4 + Math.PI / 2 * k;
                slat(o, uc + 0.08 * Math.cos(a0), yc + 0.08 * Math.sin(a0), uc + r * Math.cos(a0), yc + r * Math.sin(a0), w, 0.016);
            }
            slat(o, uc, yc + r, uc, (y0 + y1) / 2 + 1.06, w, 0.02);
            slat(o, uc, yc - r, uc, (y0 + y1) / 2 + 0.17, w, 0.02);
        }
    }

    /**
     * Kandelaber auf einem runden Steinsockel bei (u, w), Fuß in Höhe y: Sockel mit Gesims, eiserner Fuß,
     * schlanker Mast, vier Arme mit Laternen und eine Laterne auf der Spitze.
     */
    private static void candelabrum(Obj o, double u, double w, double y) {
        o.lathe(u, w, new double[]{1.15, 1.15, 1.0, 0.82, 0.82, 0.98, 0.98, 0},
                new double[]{y, y + 0.35, y + 0.5, y + 0.62, y + 2.25, y + 2.38, y + 2.62, y + 2.62}, 14, Material.GRANITE);
        o.lathe(u, w, new double[]{0.56, 0.5, 0.34, 0.24, 0.2, 0.17, 0.14, 0.14, 0.22, 0.12, 0.12, 0},
                new double[]{y + 2.62, y + 2.95, y + 3.4, y + 3.9, y + 5.4, y + 6.6, y + 7.8, y + 8.0, y + 8.15, y + 8.3, y + 9.3, y + 9.32}, 10, Material.IRON);
        double ya = y + 8.0;
        for (int k = 0; k < 4; k++) {
            double du = k == 0 ? 1 : k == 1 ? -1 : 0, dw = k == 2 ? 1 : k == 3 ? -1 : 0;
            double lu = u + du * 0.95, lw = w + dw * 0.95;
            o.box(Math.min(u, lu) - 0.04, Math.min(w, lw) - 0.04, Math.max(u, lu) + 0.04, Math.max(w, lw) + 0.04, ya - 0.05, ya + 0.04, Material.IRON, 1);
            lamp(o, lu, lw, ya + 0.04, 0.17);
        }
        lamp(o, u, w, y + 9.32, 0.2);
        o.lathe(u, w, new double[]{0.03, 0.03, 0}, new double[]{y + 9.95, y + 10.4, y + 10.45}, 6, Material.IRON);
    }

    /** Laterne: Glaskörper mit Halbmesser r, eiserne Haube. */
    private static void lamp(Obj o, double u, double w, double y, double r) {
        o.box(u - r * 0.6, w - r * 0.6, u + r * 0.6, w + r * 0.6, y, y + 0.06, Material.IRON, 1);
        o.lathe(u, w, new double[]{r * 0.6, r, r * 0.85, 0}, new double[]{y + 0.06, y + 0.3, y + 0.52, y + 0.52}, 8, Material.LAMP);
        o.pyramid(u - r * 1.1, w - r * 1.1, u + r * 1.1, w + r * 1.1, y + 0.52, y + 0.66, Material.IRON);
    }

    /**
     * Schmiedeeiserner Portalbogen im Bezugssystem q: u läuft quer über die Fahrbahn, die Kandelaber stehen bei
     * u = ±R. Zwei Korbbögen mit Streben, darüber ein Strahlenfächer im Halbkreis und ein Wappenschild.
     */
    private static void portal(Obj q, double R, double y) {
        double yS = y + 6.3, yC = y + 7.7, gap = 0.42;
        int seg = 20;
        double[] us = new double[seg + 1], ys = new double[seg + 1];
        for (int k = 0; k <= seg; k++) {
            double t = Math.PI * k / seg;
            us[k] = -R * Math.cos(t);
            ys[k] = yS + (yC - yS) * Math.sin(t);
        }
        for (int k = 0; k < seg; k++) {
            slat(q, us[k], ys[k], us[k + 1], ys[k + 1], 0, 0.045);
            slat(q, us[k], ys[k] + gap, us[k + 1], ys[k + 1] + gap, 0, 0.04);
            if (k % 2 == 1) {                                                   // Zickzackstreben
                slat(q, us[k - 1], ys[k - 1], us[k], ys[k] + gap, 0, 0.018);
                slat(q, us[k], ys[k] + gap, us[k + 1], ys[k + 1], 0, 0.018);
            }
        }
        // Strahlenfächer über dem Scheitel
        double fc = yC + gap, fr = 1.7;
        int rays = 13, arc = 16;
        for (int k = 0; k < arc; k++) {
            double a0 = Math.PI * k / arc, a1 = Math.PI * (k + 1) / arc;
            slat(q, fr * Math.cos(a0), fc + fr * Math.sin(a0), fr * Math.cos(a1), fc + fr * Math.sin(a1), 0, 0.035);
            slat(q, 0.7 * Math.cos(a0), fc + 0.7 * Math.sin(a0), 0.7 * Math.cos(a1), fc + 0.7 * Math.sin(a1), 0, 0.025);
        }
        for (int k = 0; k <= rays; k++) {
            double a = Math.PI * k / rays, r1 = k % 2 == 0 ? fr + 0.35 : fr;
            slat(q, 0.25 * Math.cos(a), fc + 0.25 * Math.sin(a), r1 * Math.cos(a), fc + r1 * Math.sin(a), 0, 0.02);
        }
        // Voluten seitlich des Fächers
        for (int sd = -1; sd <= 1; sd += 2) {
            double cu = sd * (fr + 0.55), cy = fc + 0.35;
            for (int k = 0; k < 10; k++) {
                double a0 = 2 * Math.PI * k / 10, a1 = 2 * Math.PI * (k + 1) / 10;
                double r0 = 0.42 - 0.025 * k, r1 = 0.42 - 0.025 * (k + 1);
                slat(q, cu + sd * r0 * Math.cos(a0), cy + r0 * Math.sin(a0), cu + sd * r1 * Math.cos(a1), cy + r1 * Math.sin(a1), 0, 0.02);
            }
        }
        // Wappenschild mit Krone und Spitze
        q.box(-0.36, -0.05, 0.36, 0.05, fc + fr + 0.05, fc + fr + 0.75, Material.IRON, 1);
        q.lathe(0, 0, new double[]{0.24, 0.28, 0.2, 0}, new double[]{fc + fr + 0.75, fc + fr + 0.88, fc + fr + 1.02, fc + fr + 1.04}, 8, Material.COPPER);
        q.lathe(0, 0, new double[]{0.025, 0.025, 0}, new double[]{fc + fr + 1.04, fc + fr + 1.6, fc + fr + 1.65}, 6, Material.IRON);
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
        if (b.iron) for (double w : RAILS) o.box(0.03, w - 0.035, LEAF, w + 0.035, 0.0, 0.03, Material.IRON, 1);
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
        rail(b, o, 0.05, 0.0, LEAF, 0.0, hw - 0.08, 1.4);
        rail(b, o, 0.05, 0.0, LEAF, 0.0, -hw + 0.08, 1.4);
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
