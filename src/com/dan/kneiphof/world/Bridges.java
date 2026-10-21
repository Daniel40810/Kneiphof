package com.dan.kneiphof.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Die sieben Brücken von Königsberg um 1910, nach dem Plan an ihren Stellen über die Flussarme:
 * Krämer-, Schmiede-, Holz-, Grüne, Köttel-, Honig- und Hohe Brücke. Nach den Fotos um 1910 (Ordner
 * {@code templates/}) sind alle sieben eiserne Doppelklappbrücken auf gemauerten Strompfeilern: zwei Klappen
 * mit Gegengewicht im Klappenpfeiler schließen die Durchfahrt in der Mitte, die festen Felder liegen auf
 * Blechträgern. Die Klappen sind eigene Netze mit dem Drehpunkt im Ursprung, damit der Renderer sie bewegen
 * kann.
 * <p>Jede Brücke hat ihre Merkmale aus den Fotos (Maße und Ornament geschätzt, nicht gemessen):</p>
 * <ul>
 * <li>Krämerbrücke: Stabgeländer, Fahrdrahtjoche der Straßenbahn über den Klappenpfeilern, große
 *     Kandelaber mit zwei Lampenkränzen auf den Ufervorbauten, Klappen als tiefe Gitterträger.</li>
 * <li>Schmiedebrücke: schmal, Maßwerkgeländer, Ringgirlande unter der Fahrbahnkante, Laternen auf runden
 *     Steinsockeln an den Ufern.</li>
 * <li>Holzbrücke: wuchtige Pfeiler mit runden Köpfen, Ringgeländer, Kandelaber und Portalbogen über den
 *     Klappenpfeilern, Straßenbahn.</li>
 * <li>Grüne Brücke: spitze Eisbrecher, Ringgeländer, Kandelaber und Portalbogen mit Strahlenfächer und
 *     Wappen, Straßenbahn.</li>
 * <li>Köttelbrücke: runde Pfeilerköpfe, die als Türme über die Fahrbahn reichen und Laternensäulen tragen,
 *     Rautengeländer, steinerne Balustraden an den Auffahrten.</li>
 * <li>Honigbrücke: schlicht, Stabgeländer, Gaslaternen, ein Gleis.</li>
 * <li>Hohe Brücke: eiserne Bogenträger über den festen Feldern, Pylone auf den Klappenpfeilern,
 *     Straßenbahn.</li>
 * </ul>
 * <p>Die Auffahrten gehen ohne Stufe in die Straße über: Ihr Längsschnitt folgt einer Kosinuskurve von der
 * Fahrbahn bis zur Geländehöhe am Ende, beide Enden also ohne Knick. Vor jeder Auffahrt liegt ein gepflasterter
 * Vorplatz, der Auffahrt und Kaipflaster zu einem Umriss zusammenfasst.</p>
 */
public final class Bridges {
    /** Länge einer Klappe, Lücke dazwischen, Schwanzlänge hinter dem Drehpunkt. */
    public static final double LEAF = 7.0, GAP = 0.25, TAIL = 2.5;
    /** Größter Öffnungswinkel (Bogenmaß). */
    public static final double MAX_ANGLE = Math.toRadians(72);

    /** Geländerarten: Stäbe, Ringe, Rauten, Maßwerk. */
    static final int BARS = 0, RINGS = 1, LOZENGE = 2, TRACERY = 3;
    /** Lampen: Gaslaternen auf der Fahrbahn, Kandelaber auf den Klappenpfeilern, Kandelaber an den Ufern,
     *  Laternen auf Steinsockeln an den Ufern, Laternensäulen auf runden Pfeilertürmen. */
    static final int GAS = 0, CAND_PIER = 1, CAND_SHORE = 2, PEDESTAL = 3, COLUMNS = 4;

    /** Bauart einer Brücke nach den Fotos. */
    static final class Look {
        final int railing, lamps;
        /** Gleise quer zur Achse (leer: keine Straßenbahn). */
        final double[] rails;
        /** Runde Pfeilerköpfe statt spitzer Eisbrecher; Überstand der Klappenpfeiler über die Fahrbahnkante. */
        final boolean round;
        final double pierOut;
        boolean portal, gantry, arches, festoon, balustrade, pylons, lattice;
        Look(int railing, int lamps, double[] rails, boolean round, double pierOut) {
            this.railing = railing; this.lamps = lamps; this.rails = rails; this.round = round; this.pierOut = pierOut;
        }
    }

    public static final class Bridge {
        public final String name;
        final Look look;
        public double cx, cz, angle, c, s, sMin, sMax, width, deck;
        /** Länge der Auffahrt hinter dem Ufer e (0: −u, 1: +u) und Geländehöhe an ihrem Ende. */
        public final double[] ramp = new double[2], land = new double[2];
        /** Höhe am Ende der Auffahrt quer über die Fahrbahn, in END Stücken von −width/2 bis +width/2. */
        final double[][] landAcross = new double[2][END + 1];
        public final MeshBuilder leaf = new MeshBuilder();
        /** Drehpunkte der beiden Klappen (x, y, z) und Drehung um die Hochachse. */
        public final double[][] pivot = new double[2][3];
        public final double[] yaw = new double[2];
        Bridge(String name, Look look) { this.name = name; this.look = look; }
        public double wx(double u, double w) { return cx + u * c - w * s; }
        public double wz(double u, double w) { return cz + u * s + w * c; }

        /** Höhe der Fahrbahn an der Stelle u: auf der Brücke eben, auf den Auffahrten als Kosinuskurve zur Straße. */
        public double surface(double u) {
            if (u >= sMin && u <= sMax) return deck;
            int e = u < sMin ? 0 : 1;
            double f = Math.min(1, Math.abs(u - (e == 0 ? sMin : sMax)) / ramp[e]);
            return deck + (land[e] - deck) * (0.5 - 0.5 * Math.cos(Math.PI * f));
        }

        /**
         * Höhe des Pflasters der Auffahrt bei (u, w): die Kurve, aber nie unter dem gezeichneten Gelände. Das Netz
         * des Geländes ist grob und liegt zwischen seinen Punkten stellenweise höher als Site.height; ohne diese
         * Grenze schnitte es das Pflaster am Ende der Auffahrt zackig ab.
         */
        public double rampSurface(double u, double w) {
            if (u >= sMin && u <= sMax) return deck;
            int e = u < sMin ? 0 : 1;
            double f = Math.min(1, Math.abs(u - (e == 0 ? sMin : sMax)) / ramp[e]);
            // Endhöhe an dieser Stelle quer zur Fahrbahn: der Platz fällt dort oft schräg ab
            double k = Math.max(0, Math.min(END, (w / width + 0.5) * END));
            int i = Math.min(END - 1, (int) k);
            double end = landAcross[e][i] + (landAcross[e][i + 1] - landAcross[e][i]) * (k - i);
            double y = deck + (end - deck) * (0.5 - 0.5 * Math.cos(Math.PI * f));
            return Math.max(y, plazaHeight(wx(u, w), wz(u, w)) + LIP);
        }

        /** Liegt (x, z) auf einem der beiden Brückenvorplätze? */
        public boolean onPlaza(double x, double z) {
            double u = (x - cx) * c + (z - cz) * s, w = -(x - cx) * s + (z - cz) * c;
            for (int e = 0; e < 2; e++) {
                double sShore = e == 0 ? sMin : sMax, t = (e == 0 ? -1 : 1) * (u - sShore);
                if (t >= PLAZA_IN && t <= ramp[e] + PLAZA_OUT && Math.abs(w) <= plazaHalf(e, t) && plazaField(x, z) > 0.05) return true;
            }
            return false;
        }

        /** Halbe Breite des Vorplatzes am Ufer e im Abstand t vom Ufer: breit an der Mauer, schmal am Ende. */
        double plazaHalf(int e, double t) {
            double f = Math.max(0, Math.min(1, (t - PLAZA_IN) / (ramp[e] + PLAZA_OUT - PLAZA_IN)));
            return width / 2 + 5.0 - 3.5 * f;
        }
    }

    private static final String[] NAMES = {"Krämerbrücke", "Schmiedebrücke", "Holzbrücke", "Grüne Brücke", "Köttelbrücke", "Honigbrücke", "Hohe Brücke"};
    /** Ungefähre Lage nach dem Plan (Weltmeter) und Fahrbahnbreite. */
    private static final double[][] SPOT = {{-60, -190, 10.0}, {92, -59, 7.5}, {297, -11, 10.0}, {-205, 76, 9.0}, {-54, 119, 8.0}, {225, 162, 6.5}, {378, 864, 10.0}};
    /** Zwei Straßenbahngleise in Meterspur, ein Gleis in der Mitte. */
    private static final double[] TWO_TRACKS = {-2.05, -1.05, 1.05, 2.05}, ONE_TRACK = {-0.5, 0.5}, NO_TRACK = {};
    /** Länge des Klappenpfeilers vom Drehpunkt landwärts. */
    private static final double PIER = 4.6;
    /** Die Auffahrt endet so viel über dem Vorplatz, damit sich die Flächen nicht durchdringen. */
    private static final double LIP = 0.02;
    /** Stücke quer über das Ende der Auffahrt. */
    private static final int END = 8;
    /** Vorplatz: beginnt so weit hinter der Uferlinie und reicht so weit über das Ende der Auffahrt hinaus. */
    private static final double PLAZA_IN = 0.7, PLAZA_OUT = 2.5;

    /**
     * Wie sehr ist das Ufer nahe (x, z) Kaimauer (1) oder Wiesenufer (0)? Nach den Uferzügen von {@link Banks},
     * denn nur dort steht wirklich eine Mauer, und stetig in der Lage statt Site.isQuay, das an der Grenze von
     * Punkt zu Punkt springt.
     */
    static double quayWeight(double x, double z) {
        return Banks.shared().quayShare(x, z, Math.max(0, Site.water(x, z)) + 6.0);
    }

    /**
     * Rand des Vorplatzes zum Wasser als stetiges Feld (positiv: Platz). Am Kai liegt der Rand an der Innenkante des
     * Decksteins, am Wiesenufer 4 m weiter landwärts; dazwischen gleitend, damit der Rand nirgends springt.
     */
    static double plazaField(double x, double z) {
        return Site.water(x, z) - Banks.COPING - (1 - quayWeight(x, z)) * 4.0;
    }

    /**
     * Höhe des Pflasters auf einem Brückenvorplatz, 3 cm über dem Kaipflaster und nie unter dem gezeichneten Gelände.
     * Am Kai folgt der Platz demselben Profil wie das Kaipflaster in {@link Banks}: linear von der Kaikante
     * (Landhöhe) bis zur Außenkante, wo es auf dem Gelände aufliegt. Am Wiesenufer liegt er auf dem Gelände;
     * dazwischen nach {@link #quayWeight} gemischt. Folgte der Platz am Kai einer anderen Kurve, stäche das
     * Kaipflaster in Zacken durch ihn; läge er am Wiesenufer auf Kaihöhe, schwebte er über der Böschung.
     */
    static double plazaHeight(double x, double z) {
        double wd = Site.water(x, z), far = Site.QUAY_SINK + TerrainMesh.cellDiagonal(x, z) + 0.6;
        double mesh = TerrainMesh.surfaceHeight(x, z) + 0.03, ground = Math.max(Site.height(x, z) + 0.03, mesh), y = ground;
        if (wd < far) {
            // Außenkante des Kaipflasters: vom Punkt in Richtung des wachsenden Uferabstands bis zum Abstand far
            double gx = Site.water(x + 0.5, z) - Site.water(x - 0.5, z), gz = Site.water(x, z + 0.5) - Site.water(x, z - 0.5);
            double gl = Math.max(1e-6, Math.hypot(gx, gz)), d = far - wd;
            double outer = Site.height(x + gx / gl * d, z + gz / gl * d) + 0.03, inner = Site.land(x, z) + 0.07;
            double t = Math.max(0, Math.min(1, (wd - Banks.COPING) / (far - Banks.COPING)));
            double kai = inner + (outer - inner) * t;
            y = ground + (kai - ground) * quayWeight(x, z);
        }
        return Math.max(y, mesh) + 0.03;
    }

    private static Look look(int i) {
        switch (i) {
            case 0: { Look l = new Look(BARS, CAND_SHORE, TWO_TRACKS, false, 2.6); l.gantry = true; l.lattice = true; return l; }
            case 1: { Look l = new Look(TRACERY, PEDESTAL, NO_TRACK, false, 2.2); l.festoon = true; return l; }
            case 2: { Look l = new Look(RINGS, CAND_PIER, TWO_TRACKS, true, 3.2); l.portal = true; return l; }
            case 3: { Look l = new Look(RINGS, CAND_PIER, TWO_TRACKS, false, 2.8); l.portal = true; return l; }
            case 4: { Look l = new Look(LOZENGE, COLUMNS, NO_TRACK, true, 1.0); l.balustrade = true; return l; }
            case 5: return new Look(BARS, GAS, ONE_TRACK, false, 1.8);
            default: { Look l = new Look(BARS, GAS, TWO_TRACKS, false, 2.4); l.arches = true; l.pylons = true; return l; }
        }
    }

    public static String[] names() { return NAMES.clone(); }

    public final List<Bridge> list = new ArrayList<>();
    public final MeshBuilder statics = new MeshBuilder();

    public Bridges() {
        for (int i = 0; i < NAMES.length; i++) {
            Bridge b = locate(NAMES[i], SPOT[i][0], SPOT[i][1], SPOT[i][2], look(i));
            list.add(b);
            build(b);
        }
    }

    /** Den Arm an der Stelle suchen, die Querrichtung nehmen, die Ufer links und rechts messen und die Auffahrten legen. */
    private static Bridge locate(String name, double gx, double gz, double width, Look look) {
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
        Bridge b = new Bridge(name, look);
        b.cx = bx; b.cz = bz;
        double nx = -dz, nz = dx;
        b.angle = Math.atan2(nz, nx);
        b.c = Math.cos(b.angle); b.s = Math.sin(b.angle);
        double s1 = 0, s2 = 0;
        while (Site.water(bx + nx * s1, bz + nz * s1) < 0 && s1 < 200) s1 += 0.25;
        while (Site.water(bx - nx * s2, bz - nz * s2) < 0 && s2 < 200) s2 += 0.25;
        b.sMin = -s2; b.sMax = s1;
        b.width = width;
        double hA = Site.height(b.wx(b.sMin - 8, 0), b.wz(b.sMin - 8, 0)), hB = Site.height(b.wx(b.sMax + 8, 0), b.wz(b.sMax + 8, 0));
        b.deck = Math.min(Math.max(Math.max(hA, hB) + 0.9, 2.9), Math.min(hA, hB) + 3.6);
        b.deck = Math.max(b.deck, 2.9);
        // Auffahrten: höchstens 5 % mittlere Steigung; das Ende liegt auf dem Gelände, das dort tatsächlich ist
        for (int e = 0; e < 2; e++) {
            double sShore = e == 0 ? b.sMin : b.sMax;
            int dir = e == 0 ? -1 : 1;
            double h = e == 0 ? hA : hB, lr = 10;
            for (int k = 0; k < 3; k++) {
                lr = Math.max(10, Math.min(40, Math.abs(b.deck - h) / 0.05));
                h = plazaHeight(b.wx(sShore + dir * lr, 0), b.wz(sShore + dir * lr, 0));
            }
            b.ramp[e] = lr;
            for (int k = 0; k <= END; k++) {
                double w = -width / 2 + width * k / END;
                b.landAcross[e][k] = plazaHeight(b.wx(sShore + dir * lr, w), b.wz(sShore + dir * lr, w)) + LIP;
            }
            b.land[e] = b.landAcross[e][END / 2];
        }
        return b;
    }

    // ------------------------------------------------------------------------------------ Bau
    private void build(Bridge b) {
        MeshBuilder m = statics;
        Look lk = b.look;
        Obj o = new Obj(m, b.cx, b.cz, b.angle);
        double W = b.width, hw = W / 2, y = b.deck;
        double pv = LEAF + GAP / 2;           // Drehpunkt der Klappen: ±pv
        for (int e = 0; e < 2; e++) approach(b, o, e, hw, y);
        spans(b, o, hw, y, pv);
        // Lager an den Drehpunkten
        for (int e = -1; e <= 1; e += 2)
            for (int sd = -1; sd <= 1; sd += 2) {
                o.box(e * pv - 0.5, sd * (hw + 0.1) - 0.3, e * pv + 0.5, sd * (hw + 0.1) + 0.3, y - 1.2, y - 0.15, Material.IRON, 0.9);
                o.box(e * pv - 0.35, sd * (hw + 0.1) - 0.45, e * pv + 0.35, sd * (hw + 0.1) + 0.45, y - 0.9, y - 0.5, Material.IRON, 1);
            }
        lamps(b, o, hw, y, pv);
        double up = pv + PIER / 2;
        if (lk.portal) {
            // Kandelaber auf den Klappenpfeilern und darüber je ein Portalbogen
            double R = hw + 1.6;
            for (int e = -1; e <= 1; e += 2) portal(o.at(e * up, 0, Math.PI / 2), R, y);
        }
        if (lk.gantry) for (int e = -1; e <= 1; e += 2) gantry(o, e * up, hw, y);
        if (lk.pylons)
            for (int e = -1; e <= 1; e += 2)
                for (int sd = -1; sd <= 1; sd += 2) pylon(o, e * up, sd * (hw + 1.3), y);
        dolphins(b, o, hw, pv);
        buildLeaf(b);
        // Drehpunkte für den Renderer
        for (int e = 0; e < 2; e++) {
            double u = e == 0 ? -pv : pv;
            b.pivot[e][0] = o.wx(u, 0); b.pivot[e][1] = y; b.pivot[e][2] = o.wz(u, 0);
            b.yaw[e] = b.angle + (e == 0 ? 0 : Math.PI);
        }
    }

    /**
     * Auffahrt am Ufer e: Pflaster auf einem Damm zwischen Stützmauern, der Höhe nach als Kosinuskurve von der
     * Fahrbahn bis zum Gelände am Ende. Dazu Widerlager im Wasser, Geländer so weit der Damm hoch genug ist,
     * und das Wärterhaus.
     */
    private void approach(Bridge b, Obj o, int e, double hw, double y) {
        MeshBuilder m = o.m;
        Look lk = b.look;
        double sShore = e == 0 ? b.sMin : b.sMax, lr = b.ramp[e];
        int dir = e == 0 ? -1 : 1;                                   // vom Wasser weg
        double yb = y;
        for (double h : b.landAcross[e]) yb = Math.min(yb, h);
        yb -= 1.6;
        int n = Math.max(8, (int) Math.ceil(lr / 1.5));
        double[] dW = o.dir(0, 1);
        double railEnd = sShore;                                     // Geländer bis hier (Damm noch über 0,35 m)
        for (int k = 0; k < n; k++)
            if (b.surface(sShore + dir * lr * k / n) - b.land[e] > 0.35) railEnd = sShore + dir * lr * (k + 1) / n;
        // Pflaster in Feldern von höchstens 1,5 m längs und quer, damit es dem Gelände am Ende folgen kann
        int cols = Math.max(2, (int) Math.ceil(2 * hw / 1.5));
        for (int k = 0; k < n; k++) {
            double ua = sShore + dir * lr * k / n, ub = sShore + dir * lr * (k + 1) / n;
            for (int q = 0; q < cols; q++) {
                double w0 = -hw + 2 * hw * q / cols, w1 = -hw + 2 * hw * (q + 1) / cols;
                // Liegt die Auffahrt hier schon auf dem Vorplatz, trägt der Platz das Pflaster (keine Doppelfläche)
                if (onPlazaLevel(b, ua, w0) && onPlazaLevel(b, ub, w0) && onPlazaLevel(b, ua, w1) && onPlazaLevel(b, ub, w1)) continue;
                Banks.facing(m, o.p(ua, b.rampSurface(ua, w0), w0), o.p(ub, b.rampSurface(ub, w0), w0), o.p(ub, b.rampSurface(ub, w1), w1),
                        o.p(ua, b.rampSurface(ua, w1), w1), 0, 0, Material.COBBLE, ua * dir, w0, 1, 1, true);
            }
            for (double w : lk.rails) slat(o, ua, b.rampSurface(ua, w) + 0.005, ub, b.rampSurface(ub, w) + 0.005, w, 0.035);
            boolean walled = dir * (ua - railEnd) < -1e-9;
            for (int sd = -1; sd <= 1; sd += 2) {
                double ya = b.rampSurface(ua, sd * hw), yc = b.rampSurface(ub, sd * hw);
                if (walled)
                    Banks.facing(m, o.p(ua, yb, sd * hw), o.p(ub, yb, sd * hw), o.p(ub, yc, sd * hw), o.p(ua, ya, sd * hw), sd * dW[0], sd * dW[1], Material.QUAY, ua * dir, yb, 0.8, 1);
                else slope(b, o, ua, ub, sd, hw);
            }
        }
        // Deckplatte auf den Stützmauern, so weit das Geländer reicht: Granit, folgt dem Damm
        int nc = Math.max(1, (int) Math.ceil(Math.abs(railEnd - sShore) / 1.5));
        for (int sd = -1; sd <= 1; sd += 2)
            for (int k = 0; k < nc && railEnd != sShore; k++) {
                double ua = sShore + (railEnd - sShore) * k / nc, ub = sShore + (railEnd - sShore) * (k + 1) / nc;
                slatM(o, ua, b.surface(ua) - 0.06, ub, b.surface(ub) - 0.06, sd * (hw + 0.02), 0.09, Material.GRANITE);
            }
        plaza(b, o, e);
        // Widerlager im Wasser: Granit vom Grund bis unter die Fahrbahn
        double wa = dir < 0 ? sShore - 0.2 : sShore - 0.9, wb = dir < 0 ? sShore + 0.9 : sShore + 0.2;
        o.box(wa, -hw - 0.5, wb, hw + 0.5, -2.8, y - 0.36, Material.QUAY, 0.8);
        // Geländer auf der Auffahrt, abgeschlossen von einem Steinpfosten
        double r0 = Math.min(sShore, railEnd), r1 = Math.max(sShore, railEnd);
        for (int sd = -1; sd <= 1; sd += 2) {
            double w = sd * (hw - 0.08);
            if (lk.balustrade) balustrade(b, o, r0, r1, w);
            else rampRail(b, o, r0, r1, w);
            double ye = b.surface(railEnd);
            o.box(railEnd - 0.25, w - 0.25, railEnd + 0.25, w + 0.25, ye - 0.05, ye + 1.25, Material.GRANITE, 0.8);
            o.pyramid(railEnd - 0.3, w - 0.3, railEnd + 0.3, w + 0.3, ye + 1.25, ye + 1.45, Material.GRANITE);
        }
        // Ufervorbauten beidseits der Fahrbahn: Granit mit Kandelaber oder Laterne
        if (lk.lamps == CAND_SHORE || lk.lamps == PEDESTAL) {
            for (int sd = -1; sd <= 1; sd += 2) {
                double u0 = dir < 0 ? sShore - 2.6 : sShore - 0.6, u1 = dir < 0 ? sShore + 0.6 : sShore + 2.6;
                double w0 = sd < 0 ? -hw - 2.4 : hw, w1 = sd < 0 ? -hw : hw + 2.4;
                o.box(u0, w0, u1, w1, -2.8, y - 0.3, Material.QUAY, 0.8);
                o.box(u0 - 0.12, w0 - 0.12 * (sd < 0 ? 1 : 0), u1 + 0.12, w1 + 0.12 * (sd > 0 ? 1 : 0), y - 0.3, y, Material.GRANITE, 1);
                double uc = (u0 + u1) / 2, wc = sd * (hw + 1.2);
                if (lk.lamps == CAND_SHORE) candelabrum(o, uc, wc, y - 0.02, true);
                else pedestalLamp(o, uc, wc, y);
            }
        }
        // Wärterhäuschen auf der Seite des Ufers mit dem ersten Wasserlauf
        if (e == 0) keeperHouse(b, o, sShore - lr - 3.0, hw + 3.2);
    }

    /**
     * Brückenvorplatz am Ufer e: ein Trapez aus Pflaster vor der Auffahrt, an der Kaimauer breit und am Ende der
     * Auffahrt schmal, in Feldern von etwa 1 m auf das Gelände gelegt. Er fasst Auffahrt und Kaipflaster zu einem
     * Umriss zusammen; über dem Wasser bleibt er aus.
     */
    private void plaza(Bridge b, Obj o, int e) {
        double sShore = e == 0 ? b.sMin : b.sMax, t0 = 0.2, len = b.ramp[e] + PLAZA_OUT - t0;
        int dir = e == 0 ? -1 : 1;
        int nu = (int) Math.ceil(len), nw = (int) Math.ceil(2 * b.plazaHalf(e, t0));
        double[][][] p = new double[nu + 1][nw + 1][];
        for (int i = 0; i <= nu; i++) {
            double t = t0 + len * i / nu, u = sShore + dir * t, half = b.plazaHalf(e, t);
            for (int j = 0; j <= nw; j++) {
                double w = -half + 2 * half * j / nw, x = o.wx(u, w), z = o.wz(u, w);
                p[i][j] = new double[]{x, 0, z, plazaField(x, z)};
            }
        }
        for (int i = 0; i < nu; i++)
            for (int j = 0; j < nw; j++) plazaCell(o, new double[][]{p[i][j], p[i + 1][j], p[i + 1][j + 1], p[i][j + 1]});
    }

    /**
     * Ein Feld des Vorplatzes, abgeschnitten am Rand zum Wasser ({@link #plazaField} = 0; am Kai die Innenkante des
     * Decksteins, dessen höhere Innenseite den Rand verdeckt): Die Schnittpunkte liegen linear interpoliert auf den
     * Feldkanten, so folgt der Rand glatt dem Ufer statt in Stufen von Feld zu Feld. Jede Ecke trägt
     * (x, –, z, Feldwert).
     */
    private static void plazaCell(Obj o, double[][] q) {
        List<double[]> poly = new ArrayList<>();
        for (int k = 0; k < q.length; k++) {
            double[] a = q[k], c = q[(k + 1) % q.length];
            if (a[3] >= 0) poly.add(a);
            if ((a[3] >= 0) != (c[3] >= 0)) {
                double f = a[3] / (a[3] - c[3]);
                poly.add(new double[]{a[0] + (c[0] - a[0]) * f, 0, a[2] + (c[2] - a[2]) * f, 0});
            }
        }
        if (poly.size() < 3) return;
        double[][] v = new double[poly.size()][];
        for (int k = 0; k < v.length; k++) {
            double[] s = poly.get(k);
            v[k] = new double[]{s[0], plazaHeight(s[0], s[2]), s[2]};
        }
        for (int k = 1; k + 1 < v.length; k++) up(o, v[0], v[k], v[k + 1], Material.COBBLE);
    }

    /**
     * Böschung neben der Auffahrt zwischen ua und ub auf der Seite sd, wo kein Geländer mehr steht: Statt einer
     * niedrigen Stützmauer, die als Naht aus dem Platz ragte, läuft das Pflaster seitlich schräg (1:4) auf die Höhe
     * des Vorplatzes aus.
     */
    private static void slope(Bridge b, Obj o, double ua, double ub, int sd, double hw) {
        double wi = sd * hw;
        double da = b.rampSurface(ua, wi) - plazaHeight(o.wx(ua, wi), o.wz(ua, wi)) - LIP;
        double db = b.rampSurface(ub, wi) - plazaHeight(o.wx(ub, wi), o.wz(ub, wi)) - LIP;
        if (Math.max(da, db) < 0.005) return;
        double wo = sd * (hw + Math.max(0.3, 4 * Math.max(da, db)));
        double[] pa = o.p(ua, b.rampSurface(ua, wi), wi), pb = o.p(ub, b.rampSurface(ub, wi), wi);
        double[] qa = o.p(ua, plazaHeight(o.wx(ua, wo), o.wz(ua, wo)) + LIP, wo), qb = o.p(ub, plazaHeight(o.wx(ub, wo), o.wz(ub, wo)) + LIP, wo);
        Banks.facing(o.m, pa, pb, qb, qa, 0, 0, Material.COBBLE, ua, wi, 1, 1, true);
    }

    /** Liegt die Auffahrt bei (u, w) auf Höhe des Vorplatzes, der dort auch liegt? */
    private static boolean onPlazaLevel(Bridge b, double u, double w) {
        double x = b.wx(u, w), z = b.wz(u, w);
        return b.onPlaza(x, z) && b.rampSurface(u, w) <= plazaHeight(x, z) + LIP + 1e-6;
    }

    /** Geländer auf der Auffahrt in der Art der Brücke, Feld für Feld der Kurve folgend. */
    private static void rampRail(Bridge b, Obj o, double r0, double r1, double w) {
        int n = Math.max(1, (int) Math.ceil((r1 - r0) / 1.8));
        for (int k = 0; k < n; k++) {
            double ua = r0 + (r1 - r0) * k / n, ub = r0 + (r1 - r0) * (k + 1) / n;
            rail(b, o, ua, b.surface(ua), ub, b.surface(ub), w, ub - ua);
        }
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

    // ------------------------------------------------------------------------------------ feste Felder
    /**
     * Feste Felder: der Klappenpfeiler mit der Kammer für den Klappenschwanz, dazwischen schmale Strompfeiler,
     * darauf eiserne Blechträger mit Pflaster (an der Hohen Brücke ein Bogenträger über das ganze Feld).
     */
    private void spans(Bridge b, Obj o, double hw, double y, double pv) {
        Look lk = b.look;
        double half = hw + lk.pierOut, nose = lk.round ? half - hw + 0.9 : 2.4;
        for (int e = 0; e < 2; e++) {
            int sg = e == 0 ? -1 : 1;
            double uShore = e == 0 ? b.sMin : b.sMax;
            // Klappenpfeiler von der Durchfahrt bis PIER hinter den Drehpunkt
            double lo = Math.min(sg * pv, sg * (pv + PIER)), hi = Math.max(sg * pv, sg * (pv + PIER));
            pier(o, lo, hi, half, nose, -3.0, y - 0.32, Material.QUAY, 0.7, lk.round);
            pier(o, lo - 0.15, hi + 0.15, half + 0.15, nose + 0.15, y - 0.32, y - 0.02, Material.GRANITE, 1, lk.round);
            if (lk.lamps == COLUMNS) for (int sd = -1; sd <= 1; sd += 2) tower(o, (lo + hi) / 2, sd * (hw + 2.6), y);
            // Strompfeiler zwischen Ufer und Klappenpfeiler, Felder bis 13 m (Bogenträger: ein Feld)
            double uEnd = sg * (pv + PIER);
            double span = Math.abs(uEnd - uShore);
            int n = lk.arches ? 1 : Math.max(1, (int) Math.ceil(span / 13.0));
            double step = (uEnd - uShore) / n;
            for (int k = 1; k < n; k++) {
                double u = uShore + k * step;
                pier(o, u - 0.8, u + 0.8, hw + 0.6, 1.4, -3.0, y - 1.6, Material.QUAY, 0.7, lk.round);
                pier(o, u - 0.95, u + 0.95, hw + 0.75, 1.55, y - 1.6, y - 1.35, Material.GRANITE, 1, lk.round);
            }
            double fa = Math.min(uShore, sg * pv), fb = Math.max(uShore, sg * pv);
            ironDeck(b, o, fa, fb, hw, y);
            rail(b, o, fa, y, fb, y, hw - 0.08, 1.8);
            rail(b, o, fa, y, fb, y, -hw + 0.08, 1.8);
            if (lk.festoon) for (int sd = -1; sd <= 1; sd += 2) festoon(o, fa, fb, sd * (hw + 0.26), y);
            if (lk.arches) {
                double aa = Math.min(uShore, uEnd), ab = Math.max(uShore, uEnd);
                for (int sd = -1; sd <= 1; sd += 2) bowstring(o, aa + 0.4, ab - 0.4, sd * (hw + 0.3), y, Math.min(5.0, (ab - aa) * 0.24));
            }
        }
    }

    /** Eiserne Fahrbahn: Längsträger, Blechträger als Stirn an beiden Seiten, Pflaster, Radabweiser, Gleise. */
    private static void ironDeck(Bridge b, Obj o, double a, double bb, double hw, double y) {
        for (int i = 0; i < 4; i++) {
            double w = -hw + 0.6 + i * (2 * hw - 1.2) / 3;
            o.box(a, w - 0.12, bb, w + 0.12, y - 1.35, y - 0.3, Material.IRON, 1);
        }
        o.box(a, hw - 0.02, bb, hw + 0.16, y - 1.4, y + 0.06, Material.IRON, 1);
        o.box(a, -hw - 0.16, bb, -hw + 0.02, y - 1.4, y + 0.06, Material.IRON, 1);
        for (double u = a + 1.2; u < bb - 0.3; u += 2.4) {                      // Steifen außen am Blechträger
            o.box(u - 0.05, hw + 0.16, u + 0.05, hw + 0.24, y - 1.35, y, Material.IRON, 1);
            o.box(u - 0.05, -hw - 0.24, u + 0.05, -hw - 0.16, y - 1.35, y, Material.IRON, 1);
        }
        o.box(a, -hw, bb, hw, y - 0.3, y, Material.COBBLE, 1);
        o.box(a, hw - 0.4, bb, hw - 0.12, y, y + 0.17, Material.GRANITE, 1);
        o.box(a, -hw + 0.12, bb, -hw + 0.4, y, y + 0.17, Material.GRANITE, 1);
        for (double w : b.look.rails) o.box(a, w - 0.035, bb, w + 0.035, y, y + 0.03, Material.IRON, 1);
    }

    /**
     * Gemauerter Pfeiler: im Grundriss ein Rechteck u0..u1 mal ±half, stromauf und stromab mit einem spitzen
     * Eisbrecher oder einem runden Kopf der Länge nose, von y0 bis y1, oben geschlossen.
     */
    private static void pier(Obj o, double u0, double u1, double half, double nose, double y0, double y1, int mat, double aoFoot, boolean round) {
        double um = (u0 + u1) / 2, ru = (u1 - u0) / 2;
        List<double[]> pts = new ArrayList<>();
        for (int sd = -1; sd <= 1; sd += 2) {
            // Kopf auf der Seite sd: von u0 nach u1 (sd < 0) bzw. zurück
            int seg = round ? 10 : 2;
            for (int k = 0; k <= seg; k++) {
                double t = Math.PI * k / seg;
                double u = sd < 0 ? um - ru * Math.cos(t) : um + ru * Math.cos(t);
                double w = sd * (half + (round ? nose * Math.sin(t) : (k == 1 ? nose : 0)));
                pts.add(new double[]{u, w});
            }
        }
        double[][] ring = pts.toArray(new double[0][]);
        double run = 0;
        for (int i = 0; i < ring.length; i++) {
            double[] a = ring[i], c = ring[(i + 1) % ring.length];
            double mu = (a[0] + c[0]) / 2, mw = (a[1] + c[1]) / 2;
            // nach außen: an den Längsseiten quer, an den Köpfen vom Kopfmittelpunkt (um, ±half) weg
            double[] out = o.dir(mu - um, mw - Math.max(-half, Math.min(half, mw)));
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

    // ------------------------------------------------------------------------------------ Geländer
    /** Geländer passend zur Bauart. */
    private static void rail(Bridge b, Obj o, double ua, double ya, double ub, double yb, double w, double dist) {
        switch (b.look.railing) {
            case RINGS: ornateRailing(o, ua, ya, ub, yb, w, dist + 0.2); break;
            case LOZENGE: lozengeRailing(o, ua, ya, ub, yb, w, dist + 0.2); break;
            case TRACERY: traceryRailing(o, ua, ya, ub, yb, w, dist + 0.2); break;
            default: barRailing(o, ua, ya, ub, yb, w, dist + 0.2);
        }
    }

    /** Pfosten mit Knauf und die beiden Längsstäbe oben und unten, gemeinsam für alle eisernen Geländer. */
    private static int frame(Obj o, double ua, double ya, double ub, double yb, double w, double dist) {
        double len = ub - ua;
        int n = Math.max(1, (int) Math.round(len / dist));
        for (int i = 0; i <= n; i++) {
            double u = ua + len * i / n, yy = ya + (yb - ya) * i / n;
            o.box(u - 0.06, w - 0.06, u + 0.06, w + 0.06, yy, yy + 1.15, Material.IRON, 1);
            o.box(u - 0.09, w - 0.09, u + 0.09, w + 0.09, yy + 1.15, yy + 1.27, Material.IRON, 1);
        }
        slat(o, ua, ya + 1.08, ub, yb + 1.08, w, 0.05);
        slat(o, ua, ya + 0.15, ub, yb + 0.15, w, 0.035);
        return n;
    }

    /** Stabgeländer: dichte senkrechte Stäbe zwischen Hand- und Fußstab (Krämer-, Honig-, Hohe Brücke). */
    private static void barRailing(Obj o, double ua, double ya, double ub, double yb, double w, double dist) {
        frame(o, ua, ya, ub, yb, w, dist);
        double len = ub - ua;
        int bars = Math.max(1, (int) Math.round(Math.abs(len) / 0.16));
        for (int i = 1; i < bars; i++) {
            double u = ua + len * i / bars, yy = ya + (yb - ya) * i / bars;
            o.box(u - 0.012, w - 0.012, u + 0.012, w + 0.012, yy + 0.15, yy + 1.08, Material.IRON, 1);
        }
        slat(o, ua, ya + 0.95, ub, yb + 0.95, w, 0.02);
    }

    /** Rautengeländer: zwei Scharen schräger Stäbe unter 45°, die sich zu Rauten kreuzen (Köttelbrücke). */
    private static void lozengeRailing(Obj o, double ua, double ya, double ub, double yb, double w, double dist) {
        frame(o, ua, ya, ub, yb, w, dist);
        double len = ub - ua, H = 0.93, lo = Math.min(ua, ub), hi = Math.max(ua, ub), step = 0.46;
        for (int sd = -1; sd <= 1; sd += 2)
            for (double s0 = lo - H; s0 < hi; s0 += step) {
                // Stab von (s0, unten) nach (s0 + H, oben), bei sd < 0 gespiegelt; auf [lo, hi] beschnitten
                double t0 = Math.max(0, sd > 0 ? lo - s0 : s0 + H - hi), t1 = Math.min(H, sd > 0 ? hi - s0 : s0 + H - lo);
                if (t1 - t0 < 0.02) continue;
                double uA = sd > 0 ? s0 + t0 : s0 + H - t0, uB = sd > 0 ? s0 + t1 : s0 + H - t1;
                double bA = ya + (yb - ya) * (uA - ua) / len, bB = ya + (yb - ya) * (uB - ua) / len;
                slat(o, uA, bA + 0.15 + t0, uB, bB + 0.15 + t1, w, 0.016);
            }
    }

    /**
     * Maßwerkgeländer: schlanke Stäbe, oben paarweise in kleinen Bögen verbunden, darunter ein Band aus Ringen
     * (Schmiedebrücke).
     */
    private static void traceryRailing(Obj o, double ua, double ya, double ub, double yb, double w, double dist) {
        frame(o, ua, ya, ub, yb, w, dist);
        double len = ub - ua;
        int k = Math.max(2, (int) Math.round(Math.abs(len) / 0.24));
        for (int i = 0; i < k; i++) {
            double u0 = ua + len * i / k, u1 = ua + len * (i + 1) / k, um = (u0 + u1) / 2;
            double y0 = ya + (yb - ya) * i / k, y1 = ya + (yb - ya) * (i + 1) / k, ym = (y0 + y1) / 2;
            if (i > 0) o.box(u0 - 0.011, w - 0.011, u0 + 0.011, w + 0.011, y0 + 0.15, y0 + 0.92, Material.IRON, 1);
            // Bogen von Stab zu Stab
            double r = (u1 - u0) / 2;
            for (int a = 0; a < 4; a++) {
                double t0 = Math.PI * a / 4, t1 = Math.PI * (a + 1) / 4;
                slat(o, um - r * Math.cos(t0), ym + 0.92 + r * 0.9 * Math.sin(t0), um - r * Math.cos(t1), ym + 0.92 + r * 0.9 * Math.sin(t1), w, 0.012);
            }
            // Ring im Fußband
            double rr = Math.min(0.1, r * 0.85), yc = ym + 0.33;
            for (int a = 0; a < 6; a++) {
                double t0 = 2 * Math.PI * a / 6, t1 = 2 * Math.PI * (a + 1) / 6;
                slat(o, um + rr * Math.cos(t0), yc + rr * Math.sin(t0), um + rr * Math.cos(t1), yc + rr * Math.sin(t1), w, 0.012);
            }
        }
        slat(o, ua, ya + 0.48, ub, yb + 0.48, w, 0.018);
        slat(o, ua, ya + 0.2, ub, yb + 0.2, w, 0.018);
    }

    /**
     * Ziergeländer aus Gusseisen: kräftige Pfosten mit Knauf, Handlauf und Fußstab, in jedem Feld ein Ring mit
     * Speichen, oben und unten an die Stäbe angebunden (Grüne Brücke, Holzbrücke).
     */
    private static void ornateRailing(Obj o, double ua, double ya, double ub, double yb, double w, double dist) {
        int n = frame(o, ua, ya, ub, yb, w, dist);
        double len = ub - ua;
        for (int i = 0; i < n; i++) {
            double u0 = ua + len * i / n, u1 = ua + len * (i + 1) / n;
            double y0 = ya + (yb - ya) * i / n, y1 = ya + (yb - ya) * (i + 1) / n;
            double uc = (u0 + u1) / 2, yc = (y0 + y1) / 2 + 0.62;
            double r = Math.min(0.38, Math.abs(u1 - u0) / 2 - 0.12);
            if (r < 0.08) continue;
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
     * Steinerne Balustrade auf der Auffahrt (Köttelbrücke): Sockel, gedrechselte Docken, Deckplatte; folgt der
     * Kurve der Auffahrt.
     */
    private static void balustrade(Bridge b, Obj o, double r0, double r1, double w) {
        int n = Math.max(1, (int) Math.ceil((r1 - r0) / 1.5));
        for (int k = 0; k < n; k++) {
            double ua = r0 + (r1 - r0) * k / n, ub = r0 + (r1 - r0) * (k + 1) / n;
            double ya = b.surface(ua), yb = b.surface(ub);
            slatM(o, ua, ya + 0.1, ub, yb + 0.1, w, 0.12, Material.GRANITE);
            slatM(o, ua, ya + 0.98, ub, yb + 0.98, w, 0.13, Material.GRANITE);
            int d = Math.max(2, (int) Math.round((ub - ua) / 0.3));
            for (int i = 0; i < d; i++) {
                double u = ua + (ub - ua) * (i + 0.5) / d, yy = ya + (yb - ya) * (i + 0.5) / d;
                o.lathe(u, w, new double[]{0.07, 0.07, 0.1, 0.06, 0.045, 0.07, 0.07}, new double[]{yy + 0.2, yy + 0.27, yy + 0.42, yy + 0.62, yy + 0.74, yy + 0.8, yy + 0.86}, 6, Material.GRANITE);
            }
        }
    }

    /** Ringgirlande außen an der Fahrbahnkante (Schmiedebrücke): Bögen hängen zwischen kleinen Ringen. */
    private static void festoon(Obj o, double ua, double ub, double w, double y) {
        double len = ub - ua;
        int n = Math.max(1, (int) Math.round(Math.abs(len) / 0.9));
        double yt = y - 0.12;
        for (int i = 0; i <= n; i++) {
            double u = ua + len * i / n;
            for (int a = 0; a < 6; a++) {
                double t0 = 2 * Math.PI * a / 6, t1 = 2 * Math.PI * (a + 1) / 6;
                slat(o, u + 0.1 * Math.cos(t0), yt - 0.1 + 0.1 * Math.sin(t0), u + 0.1 * Math.cos(t1), yt - 0.1 + 0.1 * Math.sin(t1), w, 0.014);
            }
            if (i == n) break;
            double u1 = ua + len * (i + 1) / n, um = (u + u1) / 2, r = (u1 - u) / 2 - 0.1;
            for (int a = 0; a < 6; a++) {
                double t0 = Math.PI * a / 6, t1 = Math.PI * (a + 1) / 6;
                slat(o, um - r * Math.cos(t0), yt - 0.1 - 0.32 * Math.sin(t0), um - r * Math.cos(t1), yt - 0.1 - 0.32 * Math.sin(t1), w, 0.016);
            }
        }
    }

    /** Stab zwischen (u0, y0) und (u1, y1) in der Ebene w, Dicke 2·t quer zum Stab und quer zur Ebene. */
    private static void slat(Obj o, double u0, double y0, double u1, double y1, double w, double t) {
        slatM(o, u0, y0, u1, y1, w, t, Material.IRON);
    }

    private static void slatM(Obj o, double u0, double y0, double u1, double y1, double w, double t, int mat) {
        MeshBuilder m = o.m;
        double du = u1 - u0, dy = y1 - y0, l = Math.hypot(du, dy);
        if (l < 1e-6) return;
        double nu = -dy / l * t, ny = du / l * t;                     // quer zum Stab in der Ebene
        double[][] q = {{u0 - nu, y0 - ny}, {u1 - nu, y1 - ny}, {u1 + nu, y1 + ny}, {u0 + nu, y0 + ny}};
        double[] p00 = o.p(q[0][0], q[0][1], w - t), p01 = o.p(q[0][0], q[0][1], w + t);
        double[] p10 = o.p(q[1][0], q[1][1], w - t), p11 = o.p(q[1][0], q[1][1], w + t);
        double[] p20 = o.p(q[2][0], q[2][1], w - t), p21 = o.p(q[2][0], q[2][1], w + t);
        double[] p30 = o.p(q[3][0], q[3][1], w - t), p31 = o.p(q[3][0], q[3][1], w + t);
        // Umlaufsinn hängt von der Richtung des Stabes ab: richtig herum über die gewünschte Normale
        double[] dW = o.dir(0, 1);
        double[] sideUp = {-(dy / l) * o.c, du / l, -(dy / l) * o.s};
        Banks.facing(m, p01, p11, p21, p31, dW[0], dW[1], mat, 0, 0, 1, 1);                    // +w
        Banks.facing(m, p10, p00, p30, p20, -dW[0], -dW[1], mat, 0, 0, 1, 1);                  // −w
        quadToward(m, p30, p31, p21, p20, sideUp, mat);                                        // Oberseite
        quadToward(m, p00, p10, p11, p01, new double[]{-sideUp[0], -sideUp[1], -sideUp[2]}, mat);
    }

    /** Viereck, gewendet so, dass seine Normale zur Richtung want zeigt. */
    private static void quadToward(MeshBuilder m, double[] p0, double[] p1, double[] p2, double[] p3, double[] want, int mat) {
        double ex = p1[0] - p0[0], ey = p1[1] - p0[1], ez = p1[2] - p0[2];
        double fx = p3[0] - p0[0], fy = p3[1] - p0[1], fz = p3[2] - p0[2];
        double nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
        if (nx * want[0] + ny * want[1] + nz * want[2] >= 0) m.quad(p0, p1, p2, p3, mat, 0, 0, 1, 1);
        else m.quad(p1, p0, p3, p2, mat, 0, 0, 1, 1);
    }

    // ------------------------------------------------------------------------------------ Lampen und Aufbauten
    /** Lampen in der Art der Brücke. */
    private void lamps(Bridge b, Obj o, double hw, double y, double pv) {
        MeshBuilder m = o.m;
        double up = pv + PIER / 2;
        switch (b.look.lamps) {
            case CAND_PIER:
                for (int e = -1; e <= 1; e += 2)
                    for (int sd = -1; sd <= 1; sd += 2) candelabrum(o, e * up, sd * (hw + 1.6), y - 0.02, false);
                break;
            case GAS:
                for (int e = 0; e < 2; e++) {
                    int sg = e == 0 ? -1 : 1;
                    double uShore = e == 0 ? b.sMin : b.sMax;
                    double ua = sg * (pv + PIER + 1.0), ub = uShore - sg * 2.0;
                    Banks.lantern(m, o.wx(ua, hw - 0.26), y + 0.17, o.wz(ua, hw - 0.26));
                    if (Math.abs(ub) > Math.abs(ua) + 6) Banks.lantern(m, o.wx(ub, -hw + 0.26), y + 0.17, o.wz(ub, -hw + 0.26));
                }
                break;
            default:
                break;
        }
    }

    /**
     * Kandelaber auf einem runden Steinsockel bei (u, w), Fuß in Höhe y: Sockel mit Gesims, eiserner Fuß,
     * schlanker Mast, vier Arme mit Laternen und eine Laterne auf der Spitze. Der große (Krämerbrücke) hat einen
     * zweiten, kleineren Lampenkranz darüber.
     */
    private static void candelabrum(Obj o, double u, double w, double y, boolean big) {
        o.lathe(u, w, new double[]{1.15, 1.15, 1.0, 0.82, 0.82, 0.98, 0.98, 0},
                new double[]{y, y + 0.35, y + 0.5, y + 0.62, y + 2.25, y + 2.38, y + 2.62, y + 2.62}, 14, Material.GRANITE);
        double top = big ? 1.4 : 0;
        o.lathe(u, w, new double[]{0.56, 0.5, 0.34, 0.24, 0.2, 0.17, 0.14, 0.14, 0.22, 0.12, 0.12, 0},
                new double[]{y + 2.62, y + 2.95, y + 3.4, y + 3.9, y + 5.4, y + 6.6, y + 7.8, y + 8.0, y + 8.15, y + 8.3, y + 9.3 + top, y + 9.32 + top}, 10, Material.IRON);
        double ya = y + 8.0;
        for (int k = 0; k < 4; k++) {
            double du = k == 0 ? 1 : k == 1 ? -1 : 0, dw = k == 2 ? 1 : k == 3 ? -1 : 0;
            double lu = u + du * 0.95, lw = w + dw * 0.95;
            o.box(Math.min(u, lu) - 0.04, Math.min(w, lw) - 0.04, Math.max(u, lu) + 0.04, Math.max(w, lw) + 0.04, ya - 0.05, ya + 0.04, Material.IRON, 1);
            lamp(o, lu, lw, ya + 0.04, 0.17);
            if (big) {
                // zweiter Kranz, um 45° gedreht und enger
                double a = Math.PI / 4 + Math.PI / 2 * k, su = u + Math.cos(a) * 0.62, sw = w + Math.sin(a) * 0.62, yb = ya + 1.25;
                slat(o, u, yb - 0.25, su, yb, w, 0.03);
                o.box(Math.min(u, su) - 0.03, Math.min(w, sw) - 0.03, Math.max(u, su) + 0.03, Math.max(w, sw) + 0.03, yb - 0.04, yb + 0.03, Material.IRON, 1);
                lamp(o, su, sw, yb + 0.03, 0.13);
            }
        }
        lamp(o, u, w, y + 9.32 + top, 0.2);
        o.lathe(u, w, new double[]{0.03, 0.03, 0}, new double[]{y + 9.95 + top, y + 10.4 + top, y + 10.45 + top}, 6, Material.IRON);
    }

    /** Laterne auf rundem Steinsockel (Schmiedebrücke): bauchiger Sockel, eiserner Mast mit Ring, eine Laterne. */
    private static void pedestalLamp(Obj o, double u, double w, double y) {
        o.lathe(u, w, new double[]{0.95, 0.95, 0.8, 0.62, 0.7, 0.85, 0.85, 0},
                new double[]{y, y + 0.25, y + 0.4, y + 0.8, y + 1.15, y + 1.3, y + 1.45, y + 1.45}, 14, Material.GRANITE);
        o.lathe(u, w, new double[]{0.28, 0.22, 0.12, 0.09, 0.09, 0.16, 0.08, 0.08, 0},
                new double[]{y + 1.45, y + 1.75, y + 2.2, y + 3.0, y + 4.4, y + 4.5, y + 4.6, y + 4.95, y + 4.97}, 8, Material.IRON);
        lamp(o, u, w, y + 4.97, 0.24);
    }

    /**
     * Runder Pfeilerkopf der Köttelbrücke bei (u, w): ein Zylinder aus Quadern vom Grund bis wenig über die
     * Fahrbahn, mit Gesims und flacher Kappe, darauf eine steinerne Laternensäule.
     */
    private static void tower(Obj o, double u, double w, double y) {
        double r = 1.9;
        o.lathe(u, w, new double[]{r + 0.25, r + 0.15, r, r, r + 0.18, r + 0.18, r * 0.75, 0},
                new double[]{-3.0, -0.6, -0.3, y + 0.15, y + 0.3, y + 0.55, y + 0.85, y + 0.9}, 20, Material.QUAY);
        o.column(u, w, y + 0.85, y + 3.6, 0.32, Material.GRANITE);
        lamp(o, u, w, y + 3.6, 0.26);
    }

    /** Laterne: Glaskörper mit Halbmesser r, eiserne Haube. */
    private static void lamp(Obj o, double u, double w, double y, double r) {
        o.box(u - r * 0.6, w - r * 0.6, u + r * 0.6, w + r * 0.6, y, y + 0.06, Material.IRON, 1);
        o.lathe(u, w, new double[]{r * 0.6, r, r * 0.85, 0}, new double[]{y + 0.06, y + 0.3, y + 0.52, y + 0.52}, 8, Material.LAMP);
        o.pyramid(u - r * 1.1, w - r * 1.1, u + r * 1.1, w + r * 1.1, y + 0.52, y + 0.66, Material.IRON);
    }

    /**
     * Fahrdrahtjoch der Straßenbahn über dem Klappenpfeiler (Krämerbrücke): zwei Gittermaste außen am Geländer,
     * ein Querriegel, Eckbleche und die Hänger für den Fahrdraht über den Gleisen.
     */
    private static void gantry(Obj o, double u, double hw, double y) {
        double R = hw + 0.45, top = y + 7.0;
        for (int sd = -1; sd <= 1; sd += 2) {
            double w = sd * R;
            o.box(u - 0.14, w - 0.14, u + 0.14, w + 0.14, y, y + 0.5, Material.IRON, 0.9);
            o.box(u - 0.08, w - 0.08, u + 0.08, w + 0.08, y + 0.5, top + 0.3, Material.IRON, 1);
            o.lathe(u, w, new double[]{0.1, 0.1, 0}, new double[]{top + 0.3, top + 0.4, top + 0.55}, 6, Material.IRON);
        }
        Obj q = o.at(u, 0, Math.PI / 2);
        slat(q, -R, top, R, top, 0, 0.07);
        slat(q, -R, top - 0.45, R, top - 0.45, 0, 0.04);
        for (int k = 0; k < 8; k++) {                                     // Gitter im Riegel
            double a = -R + 2 * R * k / 8, b2 = -R + 2 * R * (k + 1) / 8;
            slat(q, a, k % 2 == 0 ? top - 0.45 : top, b2, k % 2 == 0 ? top : top - 0.45, 0, 0.018);
        }
        for (int sd = -1; sd <= 1; sd += 2)                               // Kopfbänder als Viertelbogen in den Ecken
            for (int k = 0; k < 4; k++) {
                double t0 = Math.PI / 2 * k / 4, t1 = Math.PI / 2 * (k + 1) / 4;
                slat(q, sd * (R - 1.0 + Math.cos(t0)), top - 1.45 + Math.sin(t0), sd * (R - 1.0 + Math.cos(t1)), top - 1.45 + Math.sin(t1), 0, 0.025);
            }
        for (int k = 0; k < 2; k++) {                                     // Hänger über den Gleismitten
            double w = k == 0 ? -1.55 : 1.55;
            slat(q, w, top - 0.45, w, top - 1.2, 0, 0.015);
            q.box(w - 0.25, -0.02, w + 0.25, 0.02, top - 1.24, top - 1.2, Material.IRON, 1);
        }
    }

    /** Steinpylon auf dem Klappenpfeiler (Hohe Brücke): Quaderschaft mit Gesims, Pyramidendach, Laterne. */
    private static void pylon(Obj o, double u, double w, double y) {
        o.box(u - 0.8, w - 0.8, u + 0.8, w + 0.8, y - 0.02, y + 2.6, Material.QUAY, 0.8);
        o.box(u - 0.92, w - 0.92, u + 0.92, w + 0.92, y + 2.6, y + 2.85, Material.GRANITE, 1);
        o.pyramid(u - 0.92, w - 0.92, u + 0.92, w + 0.92, y + 2.85, y + 3.7, Material.COPPER);
        o.lathe(u, w, new double[]{0.06, 0.06, 0}, new double[]{y + 3.65, y + 4.1, y + 4.15}, 6, Material.IRON);
    }

    /**
     * Eiserner Bogenträger (Hohe Brücke) in der Ebene w über das feste Feld ua..ub: Untergurt auf der
     * Fahrbahnkante, Obergurt als flacher Bogen mit Stich rise, Pfosten und Streben dazwischen.
     */
    private static void bowstring(Obj o, double ua, double ub, double w, double y, double rise) {
        int n = Math.max(6, (int) Math.round((ub - ua) / 1.6));
        if (n % 2 == 1) n++;
        double y0 = y + 0.06;
        double[] us = new double[n + 1], ys = new double[n + 1];
        for (int k = 0; k <= n; k++) {
            double t = (double) k / n;
            us[k] = ua + (ub - ua) * t;
            ys[k] = y0 + 0.3 + rise * 4 * t * (1 - t);
        }
        for (int k = 0; k < n; k++) {
            slat(o, us[k], ys[k], us[k + 1], ys[k + 1], w, 0.13);                                      // Obergurt
            slat(o, us[k], y0 + 0.12, us[k + 1], y0 + 0.12, w, 0.1);                                   // Untergurt
            slat(o, us[k], k % 2 == 0 ? y0 + 0.12 : ys[k], us[k + 1], k % 2 == 0 ? ys[k + 1] : y0 + 0.12, w, 0.035);
        }
        for (int k = 1; k < n; k++) o.box(us[k] - 0.05, w - 0.06, us[k] + 0.05, w + 0.06, y0, ys[k], Material.IRON, 1);
        // Auflager auf den Endpfosten
        for (double u : new double[]{ua, ub}) o.box(u - 0.25, w - 0.2, u + 0.25, w + 0.2, y0 - 0.1, y0 + 0.5, Material.IRON, 1);
    }

    /** Dalben: Pfahlbündel aus fünf schrägen Pfählen mit Eisenband, stromauf und stromab der Durchfahrt. */
    private void dolphins(Bridge b, Obj o, double hw, double pv) {
        MeshBuilder m = o.m;
        for (int e = -1; e <= 1; e += 2)
            for (int sd = -1; sd <= 1; sd += 2) {
                double u = e * (pv + 0.6), w = sd * (hw + 11.0);
                double x = o.wx(u, w), z = o.wz(u, w);
                if (Site.water(x, z) > -3) continue;
                for (int k = 0; k < 5; k++) {
                    double a = 2 * Math.PI * k / 5, r = k == 4 ? 0 : 0.42;
                    double px = x + Math.cos(a) * r, pz = z + Math.sin(a) * r;
                    m.cylinder(px, -3.5, pz, 0.17, 5.2 - (k % 2) * 0.35, 8, Material.WOOD, 0.9);
                }
                m.cylinder(x, 0.95, z, 0.66, 0.12, 10, Material.IRON, 1);
            }
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
        Look lk = b.look;
        double hw = b.width / 2;
        // Bohlenbelag und Längsträger, bis in den Schwanz
        o.box(0.03, -hw, LEAF, hw, -0.34, 0.0, Material.WOOD, 1);
        for (double w : lk.rails) o.box(0.03, w - 0.035, LEAF, w + 0.035, 0.0, 0.03, Material.IRON, 1);
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
        // Stirnträger außen: Blech, an der Krämerbrücke ein Gitterträger, der zur Spitze flacher wird
        for (int sd = -1; sd <= 1; sd += 2) {
            double w = sd * (hw + 0.08);
            if (lk.lattice) {
                int n = 8;
                double d0 = 1.9, d1 = 0.8;
                for (int k = 0; k < n; k++) {
                    double x0 = -TAIL + (LEAF + TAIL) * k / n, x1 = -TAIL + (LEAF + TAIL) * (k + 1) / n;
                    double b0 = -d0 + (d0 - d1) * k / n, b1 = -d0 + (d0 - d1) * (k + 1) / n;
                    slat(o, x0, b0, x1, b1, w, 0.08);
                    slat(o, x0, b0, x1, -0.05, w, 0.03);
                    slat(o, x0, -0.05, x1, b1, w, 0.03);
                    o.box(x1 - 0.05, w - 0.06, x1 + 0.05, w + 0.06, b1, 0.0, Material.IRON, 1);
                }
                o.box(-TAIL, w - 0.08, LEAF, w + 0.08, -0.2, 0.06, Material.IRON, 1);
            } else o.box(-TAIL, w - 0.08, LEAF, w + 0.08, -1.3, 0.06, Material.IRON, 1);
        }
        // Gegengewicht unter dem Schwanz
        o.box(-TAIL + 0.1, -hw + 0.9, -0.7, hw - 0.9, -2.0, -0.82, Material.GRANITE, 0.8);
        o.box(-TAIL + 0.1, -hw + 0.9, -0.7, hw - 0.9, -1.35, -1.25, Material.IRON, 1);
        // Radabweiser und Geländer
        o.box(0.03, hw - 0.4, LEAF, hw - 0.12, 0.0, 0.17, Material.GRANITE, 1);
        o.box(0.03, -hw + 0.12, LEAF, -hw + 0.4, 0.0, 0.17, Material.GRANITE, 1);
        rail(b, o, 0.05, 0.0, LEAF, 0.0, hw - 0.08, 1.4);
        rail(b, o, 0.05, 0.0, LEAF, 0.0, -hw + 0.08, 1.4);
        if (lk.festoon) for (int sd = -1; sd <= 1; sd += 2) festoon(o, 0.05, LEAF, sd * (hw + 0.18), 0.0);
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
