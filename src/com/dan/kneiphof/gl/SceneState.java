package com.dan.kneiphof.gl;

import com.dan.kneiphof.sky.Atmosphere;
import com.dan.kneiphof.sky.SunClock;

/**
 * Alles, was ein Bild von der Uhr und vom Wetter braucht, einmal je Bild auf der CPU gerechnet:
 * Sonnen- und Mondrichtung, Farbe von Sonnen- und Mondlicht, Dunst, Wind, Belichtung.
 * Getrennt vom Zeichnen, damit Prüfprogramme dieselben Werte ohne Grafikkarte bekommen.
 * <p>
 * Die Szene rechnet <b>vorbelichtet</b>: Sonnen- und Mondlicht und die Himmelstabelle werden schon mit
 * der Belichtung multipliziert. So liegen Tag (Sonne rund 20) und Mondnacht (rund 0,00003) im
 * Bildspeicher beide nahe 1, und auch Halbfließkomma reicht für die Nacht.
 */
public final class SceneState {
    public final SunClock clock = new SunClock();
    /** Dunst: Mie-Streukoeffizient am Boden in 1/m (klar 8e-6, diesig 4e-5). */
    public double haze = 1.6e-5;
    public double windSpeed = 3.0, windDirDeg = 250;   // Wind aus Westsüdwest (Herkunft)
    public double flowSpeed = 0.25;                     // Pregel fließt nach Westen zum Haff
    /** Flussnebel: 0 nach Jahreszeit, Tageszeit und Wind, 1 aus, 2 dünner Morgennebel, 3 dichter Nebel. */
    public int mistMode;
    /** Wetter: 0 klar, 1 Regen, 2 nach dem Regen (Regenbogen). */
    public int weatherMode;
    /** Sanft nachgeführte Größen 0..1: Regen, nasse Flächen, Regenbogen, Bedeckung. */
    public double rain, wet, rainbow, overcast;
    /** Nebeldichte 0..1 (Ergebnis aus Betriebsart, Jahreszeit, Sonnenhöhe, Wind). */
    public double mist;
    /** Belichtungskorrektur in Blendenstufen (vom Bedienfeld). */
    public double ev;

    /** Licht ohne Belichtung (für Anzeigen) und vorbelichtet (für die Shader). */
    public final double[] sunRaw = new double[3], moonRaw = new double[3];
    public final double[] sunColor = new double[3], moonColor = new double[3];
    public final double[] extinction = new double[3];
    public double exposure = 1, targetExposure = 1, stars, night;
    private boolean adapted;

    /**
     * Gemessene Helligkeit des letzten Bildes: geometrisches Mittel der vorbelichteten Leuchtdichte
     * (aus der Messung lum.frag). Daraus die Leuchtdichte der Szene und die Zielbelichtung:
     * KEY · L^−0,88 — das Auge passt sich nicht ganz an, eine Mondnacht bleibt dunkler als der Tag.
     */
    public void measured(double geoMeanPreExposed) {
        if (!(geoMeanPreExposed > 0) || Double.isInfinite(geoMeanPreExposed)) return;
        double scene = geoMeanPreExposed / exposure;
        metered = Math.max(1e-4, Math.min(1e6, KEY * Math.pow(scene, -0.88)));
        hasMeter = true;
    }

    public static final double KEY = 0.055;
    private double metered;
    private boolean hasMeter;

    public void update(double dt) {
        double[] t = Atmosphere.sunTransmittance(clock.elevationDeg, haze);
        double[] tm = Atmosphere.sunTransmittance(clock.moonElevationDeg, haze);
        double moonPower = Atmosphere.SUN_POWER * 2.5e-6 * clock.moonLit;
        for (int k = 0; k < 3; k++) {
            sunRaw[k] = t[k] * Atmosphere.SUN_POWER;
            moonRaw[k] = tm[k] * moonPower;
            extinction[k] = Atmosphere.BETA_R[k] + haze * 1.11;
        }
        // Wetter
        double[] tg = weatherMode == 1 ? new double[]{1, 1, 1, 0} : weatherMode == 2 ? new double[]{0, 0, 1, 1} : new double[]{0, 0, 0, 0};
        double ovT = weatherMode == 1 ? 1 : weatherMode == 2 ? 0.35 : 0;
        if (dt <= 0) { rain = tg[0]; wet = tg[1]; rainbow = tg[3]; overcast = ovT; }
        else {
            rain += Math.max(-dt * 0.25, Math.min(dt * 0.25, tg[0] - rain));
            wet += Math.max(-dt * 0.02, Math.min(dt * 0.1, tg[1] - wet));
            rainbow += Math.max(-dt * 0.2, Math.min(dt * 0.2, tg[3] - rainbow));
            overcast += Math.max(-dt * 0.15, Math.min(dt * 0.15, ovT - overcast));
        }
        double sunDim = 1 - 0.85 * overcast * (weatherMode == 2 ? 0 : 1) - 0.0;
        sunDim = Math.min(sunDim, 1 - 0.85 * rain);
        for (int k = 0; k < 3; k++) { sunRaw[k] *= sunDim; moonRaw[k] *= (1 - 0.5 * rain); }
        mist = Math.min(1, mistNow() + 0.4 * rain + 0.12 * wet);
        double estimate = targetExposure();
        // Belichtungsmessung am Bild; die Schätzung gilt nur bis zum ersten gemessenen Bild
        double base = hasMeter ? metered : estimate;
        targetExposure = base * Math.pow(2, ev);
        if (!adapted || dt <= 0) { exposure = targetExposure; adapted = true; }
        else exposure = Math.exp(Math.log(exposure) + (Math.log(targetExposure) - Math.log(exposure)) * (1 - Math.exp(-dt * 1.2)));
        for (int k = 0; k < 3; k++) { sunColor[k] = sunRaw[k] * exposure; moonColor[k] = moonRaw[k] * exposure; }
        // Sterne: Leuchtdichte fest, sichtbar erst, wenn die Dämmerung vorbei ist
        double dark = Atmosphere.smooth(-4, -14, clock.elevationDeg);
        stars = 2.0e-7 * exposure * dark;
        night = Atmosphere.smooth(-3, -12, clock.elevationDeg) * (1 - 0.4 * clock.moonLit * Atmosphere.smooth(0, 20, clock.moonElevationDeg));
    }

    /**
     * Belichtung aus der geschätzten Beleuchtungsstärke L (Sonne direkt, Himmel, Mond, Nachthimmel):
     * L^−0,96. Das Auge passt sich nicht ganz an; eine Mondnacht wird so etwa ein Viertel so hell
     * wie der Tag, nicht gleich hell.
     */
    double targetExposure() {
        double el = clock.elevationDeg;
        double lumSun = 0.2126 * sunRaw[0] + 0.7152 * sunRaw[1] + 0.0722 * sunRaw[2];
        double direct = lumSun * Math.max(0, Math.sin(Math.toRadians(el)));
        // Himmel: am Tag gut ein Zehntel der Sonne; in der Dämmerung fällt er über 10 Größenordnungen
        double sky = 2.2 * Math.pow(10, skyLog(el));
        double lumMoon = 0.2126 * moonRaw[0] + 0.7152 * moonRaw[1] + 0.0722 * moonRaw[2];
        double moon = lumMoon * Math.max(0.15, Math.sin(Math.toRadians(Math.max(0, clock.moonElevationDeg))));
        double L = direct + sky + moon + 4e-6;
        return 0.85 * Math.pow(L, -0.96);
    }

    /** Zehnerlogarithmus der Himmelshelligkeit relativ zum Tag, nach Messungen der Dämmerung. */
    static double skyLog(double el) {
        double[] e = {-90, -18, -12, -6, 0, 6, 90};
        double[] v = {-7.0, -6.3, -4.6, -2.6, -0.8, 0.0, 0.0};
        if (el <= e[0]) return v[0];
        for (int i = 1; i < e.length; i++)
            if (el <= e[i]) return v[i - 1] + (v[i] - v[i - 1]) * (el - e[i - 1]) / (e[i] - e[i - 1]);
        return 0;
    }

    /**
     * Nebel über dem Pregel: im Herbst am dichtesten (Ende Oktober), im Frühling am dünnsten; er liegt
     * in der Nacht und am frühen Morgen und löst sich auf, wenn die Sonne über etwa 25° steigt; Wind ab
     * 6 m/s weht ihn weg.
     */
    double mistNow() {
        switch (mistMode) {
            case 1: return 0;
            case 2: return 0.35;
            case 3: return 1.0;
            default: break;
        }
        double d = clock.day();
        double season = 0.5 + 0.5 * Math.cos(2 * Math.PI * (d - 295) / 365.0);
        double sun = Atmosphere.smooth(26, 3, clock.elevationDeg);
        double calm = Atmosphere.smooth(6.5, 2.5, windSpeed);
        return Math.min(1, 0.95 * season * sun * calm);
    }

    /** Wind als Vektor, in den der Wind weht (x Osten, z Süden), mal Stärke. */
    public double[] windVector() {
        double a = Math.toRadians(windDirDeg + 180);
        return new double[]{Math.sin(a) * windSpeed, -Math.cos(a) * windSpeed};
    }
}
