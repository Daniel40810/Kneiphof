package com.dan.kneiphof.world;

/**
 * Der Schauplatz in Metern: Ursprung in der Mitte des Kneiphofs, x nach Osten, y nach oben, z nach
 * Süden, Wasserspiegel des Pregel bei y = 0.
 * <p>
 * Phase 1 legt nur den Grundriss an: zwei Pregelarme um die Inseln Kneiphof und Lomse, der kurze
 * Durchstich zwischen beiden Inseln (über ihn führt die Honigbrücke) und der vereinte Pregel nach
 * Westen zum Haff. Die Uferlinien sind nach Stadtplänen um 1910 grob abgelesen und werden in Phase 3
 * genauer gezogen. Das Gelände steigt nach Norden zum Schlossberg und im Süden zum Haberberg an.
 */
public final class Site {

    /** Ein Flussarm als Linienzug: je Punkt x, z, halbe Breite. */
    public static final class Arm {
        public final String name;
        public final double[][] pts;
        Arm(String name, double[][] pts) { this.name = name; this.pts = pts; }
    }

    /**
     * Die Arme nach dem Stadtplan von 1904 (Brockhaus, 1:17600) abgelesen. Der Pregel kommt von Westen
     * und teilt sich an der Westspitze des Kneiphofs. Der Neue Pregel läuft nördlich der Insel (unter
     * Krämer-, Schmiede- und Holzbrücke) und danach nach Südosten. Der Alte Pregel läuft südlich der
     * Insel (Grüne und Köttelbrücke), und östlich vom Dom verbindet ein schmaler Kanal (Honigbrücke)
     * beide Arme. Der Alte Pregel biegt dort nach Süden und umfließt die Lomse, bis beide Arme sich
     * weit im Osten wieder vereinen.
     */
    public static final Arm NEUER_PREGEL = new Arm("Neuer Pregel", new double[][]{
            {-330, -10, 44}, {-250, -30, 32}, {-228, -110, 28}, {-178, -192, 26}, {-100, -182, 26}, {0, -135, 26},
            {100, -84, 26}, {200, -42, 27}, {300, -10, 29}, {378, 4, 31}, {650, 70, 31}, {918, 135, 32}, {1250, 200, 34},
            {1593, 270, 36}, {2000, 310, 38}, {2400, 330, 38}, {3200, 300, 38}, {3800, 180, 38}, {4400, -20, 38}});
    public static final Arm ALTER_PREGEL = new Arm("Alter Pregel", new double[][]{
            {-330, 10, 44}, {-250, 30, 32}, {-228, 100, 28}, {-175, 138, 26}, {-92, 128, 26}, {0, 150, 26}, {100, 174, 26},
            {200, 205, 26}, {228, 260, 18}, {248, 420, 17}, {270, 594, 18}, {300, 760, 20}, {378, 864, 22}, {600, 905, 26},
            {1000, 900, 28}, {1600, 860, 30}, {2200, 760, 32}, {2800, 600, 34}, {3400, 380, 36}, {3900, 200, 36}, {4400, 30, 36}});
    /** Kanal zwischen Kneiphof und Lomse; über ihn führt die Honigbrücke. */
    public static final Arm DURCHSTICH = new Arm("Durchstich", new double[][]{
            {222, -28, 17}, {224, 90, 16}, {226, 210, 17}});
    public static final Arm PREGEL_WEST = new Arm("Pregel", new double[][]{
            {-330, 0, 50}, {-800, 22, 52}, {-1600, 72, 58}, {-2400, 60, 60}, {-3400, 30, 62}, {-4800, 180, 70},
            {-6200, 420, 90}, {-7200, 520, 160}});
    /** Der Pregel oberhalb der Stadt, wo beide Arme noch ein Fluss sind. */
    public static final Arm PREGEL_OST = new Arm("Pregel", new double[][]{
            {4400, 0, 45}, {5600, -160, 45}, {7000, 120, 45}, {8600, -60, 45}, {10500, 200, 45}, {14500, 0, 45}});

    /** Umriss des Kneiphofs entlang der Mittellinien der Arme (Wasser wird davon abgezogen). */
    public static final double[][] KNEIPHOF = {
            {-250, -30}, {-228, -110}, {-178, -192}, {-100, -182}, {0, -135}, {100, -84}, {200, -42}, {222, -28},
            {224, 90}, {226, 210}, {200, 205}, {100, 174}, {0, 150}, {-92, 128}, {-175, 138}, {-228, 100}, {-250, 30}};
    /** Umriss der Lomse entlang der Mittellinien: Kanal, Alter Pregel, Neuer Pregel. */
    public static final double[][] LOMSE = buildLomse();

    private static double[][] buildLomse() {
        java.util.List<double[]> l = new java.util.ArrayList<>();
        l.add(new double[]{222, -28});
        l.add(new double[]{224, 90});
        double[][] a = ALTER_PREGEL.pts;
        for (int i = 8; i < a.length; i++) l.add(new double[]{a[i][0], a[i][1]});
        double[][] n = NEUER_PREGEL.pts;
        for (int i = n.length - 1; i >= 8; i--) l.add(new double[]{n[i][0], n[i][1]});
        return l.toArray(new double[0][]);
    }

    public static final Arm[] ARMS = {NEUER_PREGEL, ALTER_PREGEL, DURCHSTICH, PREGEL_WEST, PREGEL_OST};

    /** Ausdehnung des feinen Geländes (halbe Kantenlänge) in Metern. */
    public static final double EXTENT = 3200;
    /** Bis hierhin reicht das grobe Umland, dahinter nur noch Himmel und Dunst. */
    public static final double FAR = 16000;

    private Site() { }

    /** Abstand zum nächsten Ufer: negativ im Wasser, positiv an Land. */
    public static double water(double x, double z) {
        double best = 1e9;
        for (Arm a : ARMS) {
            double[][] p = a.pts;
            for (int i = 0; i + 1 < p.length; i++) {
                double ax = p[i][0], az = p[i][1], bx = p[i + 1][0], bz = p[i + 1][1];
                double vx = bx - ax, vz = bz - az, wx = x - ax, wz = z - az;
                double t = Math.max(0, Math.min(1, (wx * vx + wz * vz) / (vx * vx + vz * vz)));
                double dx = wx - t * vx, dz = wz - t * vz;
                double hw = p[i][2] + t * (p[i + 1][2] - p[i][2]);
                double d = Math.sqrt(dx * dx + dz * dz) - hw;
                if (d < best) best = d;
            }
        }
        return best;
    }

    /** Punkt im Vieleck (gerade Strahlenregel). */
    public static boolean inside(double[][] poly, double x, double z) {
        boolean in = false;
        for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
            double xi = poly[i][0], zi = poly[i][1], xj = poly[j][0], zj = poly[j][1];
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) in = !in;
        }
        return in;
    }

    /** Liegt (x, z) auf dem Kneiphof? */
    public static boolean onKneiphof(double x, double z) {
        return x > -260 && x < 240 && z > -220 && z < 215 && inside(KNEIPHOF, x, z) && water(x, z) > 0;
    }

    /** Liegt (x, z) auf der Lomse? */
    public static boolean onLomse(double x, double z) {
        return x > 215 && z > -50 && z < 960 && inside(LOMSE, x, z) && water(x, z) > 0;
    }

    /**
     * Steht hier eine Kaimauer? In der Stadt sind alle Ufer gemauert: rund um den Kneiphof, am Westteil
     * der Lomse mit den Speichern und an beiden Ufern, soweit die Stadt reicht. Weiter draußen laufen
     * die Ufer als Wiese flach ins Wasser.
     */
    public static boolean isQuay(double x, double z) {
        if (onKneiphof(x, z)) return true;
        if (onLomse(x, z)) return x < 950;
        if (Math.abs(x) > 1700) return false;
        float[] c = new float[4];
        cover(x, z, c);
        return c[0] > 0.45f;
    }

    /** Geländehöhe über dem Wasserspiegel. */
    public static double height(double x, double z) {
        double w = Math.min(water(x, z), haff(x, z));
        boolean quay = w > -8 && w < 8 && isQuay(x, z);
        if (w < 0) {
            // Flussbett: an der Kaimauer sofort 2 m tief, am flachen Ufer langsam; in der Mitte bis 6 m
            return quay ? -2.0 - Math.min(4.0, -w * 0.2) : -0.3 - Math.min(5.7, -w * 0.18);
        }
        double land = base(x, z);
        if (quay) {
            // Hinter der Kaimauer liegt der Boden unter dem Kaipflaster; erst ab 4 m frei
            return -2.0 + (land + 2.0) * smooth(0.4, 4.2, w);
        }
        // Wiesenufer: auf den ersten 10 m flach ansteigend, am Wasser etwas tiefer
        return -0.3 + (land + 0.3) * smooth(0, 10, w) * (0.75 + 0.25 * smooth(10, 30, w));
    }

    /** Landhöhe ohne Ufer (Höhe der Kaikante, des Pflasters, der Wiese). */
    public static double land(double x, double z) { return base(x, z); }

    /** Das Frische Haff im Westen: Abstand zur Uferlinie, negativ im Haff. */
    public static double haff(double x, double z) {
        double shore = -7000 - 600 * Math.sin(z * 0.00035) - 250 * noise(z * 0.002, 3.5);
        return x - shore;
    }

    /** Landhöhe ohne Ufer. */
    static double base(double x, double z) {
        // Der Kneiphof ist eben aufgeschüttet: 3 m über dem Pregel, ohne Hügel
        if (onKneiphof(x, z)) return 3.0;
        double h = 2.6;
        if (onLomse(x, z)) h = 1.9 + 0.4 * noise(x * 0.004, z * 0.004);
        // Schlossberg nördlich des Neuen Pregel
        h += 17.0 * Math.exp(-sq((x - 120) / 420) - sq((z + 560) / 260));
        // Altstadt und Tragheim: sanfter Anstieg nach Norden
        h += 9.0 * smooth(-260, -1500, z) * (1 - 0.3 * smooth(800, 2200, Math.abs(x)));
        // Haberberg im Süden
        h += 13.0 * Math.exp(-sq((x - 420) / 460) - sq((z - 1400) / 420));
        // Felder in der Ferne: leicht gewellt
        double far = smooth(1400, 2600, Math.hypot(x, z));
        h += far * (3.0 * noise(x * 0.0012, z * 0.0012) + 1.2 * noise(x * 0.005 + 7, z * 0.005 + 3));
        return h;
    }

    /**
     * Bodenart als Gewichte: [0] Stadt (Pflaster, Dächer), [1] Wiese, [2] nasse Wiese, [3] Uferschlamm.
     */
    public static void cover(double x, double z, float[] out) {
        double w = water(x, z);
        // Stadtrand nicht als Kreis: ausgefranst, nach Norden (Altstadt, Tragheim) weiter als nach Süden
        double r = Math.hypot(x - 80, (z + 60) * (z < 0 ? 1.05 : 1.35)) + 260 * noise(x * 0.0025 + 11, z * 0.0025 - 4);
        double city;
        if (onKneiphof(x, z)) city = 1;
        else if (onLomse(x, z)) city = 0.85 * smooth(900, 500, x);
        else city = smooth(1500, 1050, r);
        double wet = onLomse(x, z) ? (1 - city) : 0.25 * (1 - city) * smooth(400, 0, Math.abs(z) - 300) * smooth(-500, -1200, x);
        double mud = smooth(6, 0.5, w) * (1 - city * 0.8);
        double grass = Math.max(0, 1 - city - wet);
        out[0] = (float) city;
        out[1] = (float) grass;
        out[2] = (float) wet;
        out[3] = (float) mud;
    }

    static double sq(double v) { return v * v; }

    public static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Wertrauschen −1..1, glatt. */
    static double noise(double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        double ux = fx * fx * (3 - 2 * fx), uz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz), b = hash(ix + 1, iz), c = hash(ix, iz + 1), d = hash(ix + 1, iz + 1);
        return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uz;
    }

    static double hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xffff) / 32767.5 - 1;
    }
}
