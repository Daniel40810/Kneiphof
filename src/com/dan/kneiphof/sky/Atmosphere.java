package com.dan.kneiphof.sky;

/**
 * Die Lufthülle als Zahlen: Rayleigh-Streuung (Blau), Mie-Streuung (Dunst) und Ozon. Dieselben Werte
 * stehen im Shader {@code atmosphere.glsl}; hier wird nur gerechnet, was je Bild einmal reicht:
 * die Farbe des Sonnenlichts am Boden und eine Belichtung, die dem Auge folgt.
 */
public final class Atmosphere {
    public static final double R_GROUND = 6360e3, R_TOP = 6420e3;
    public static final double[] BETA_R = {5.802e-6, 13.558e-6, 33.1e-6};
    public static final double[] BETA_OZONE = {0.650e-6, 1.881e-6, 0.085e-6};
    public static final double H_R = 8000, H_M = 1200;
    /** Sonnenstärke in der Einheit, mit der die Szene rechnet. */
    public static final double SUN_POWER = 22.0;

    private Atmosphere() { }

    /**
     * Durchlässigkeit vom Boden zur Sonne für die Höhe elevDeg und Dunst haze (Mie-Koeffizient in 1/m).
     * Unter dem Horizont geht das Licht durch die Erde: 0.
     */
    public static double[] sunTransmittance(double elevDeg, double haze) {
        double[] t = new double[3];
        double el = Math.toRadians(elevDeg);
        double dy = Math.sin(el), dx = Math.cos(el);
        double oy = R_GROUND + 2;
        // Schnitt mit der Erde: unter dem Horizont kein direktes Licht (mit weichem Übergang)
        double b = oy * dy, c = oy * oy - R_TOP * R_TOP;
        double len = -b + Math.sqrt(b * b - c);
        int n = 48;
        double odR = 0, odM = 0, odO = 0;
        for (int i = 0; i < n; i++) {
            double s = (i + 0.5) / n * len;
            double px = dx * s, py = oy + dy * s;
            double h = Math.sqrt(px * px + py * py) - R_GROUND;
            if (h < 0) { odR += 1e9; break; }
            double ds = len / n;
            odR += Math.exp(-h / H_R) * ds;
            odM += Math.exp(-h / H_M) * ds;
            odO += Math.max(0, 1 - Math.abs(h - 25000) / 15000) * ds;
        }
        for (int k = 0; k < 3; k++)
            t[k] = Math.exp(-(BETA_R[k] * odR + haze * 1.11 * odM + BETA_OZONE[k] * odO));
        double fade = smooth(-2.0, 0.8, elevDeg);
        for (int k = 0; k < 3; k++) t[k] *= fade;
        return t;
    }

    public static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
