package com.dan.kneiphof.sky;

/**
 * Datum und Uhrzeit auf dem Kneiphof (Dom: 54,7065° N, 20,5115° O) im Jahr 1910 und daraus der Stand
 * von Sonne und Mond im Koordinatensystem der Szene (x Osten, y oben, z Süden).
 * <p>
 * Die Uhren in Königsberg gingen 1910 nach Mitteleuropäischer Zeit (UTC+1, im Deutschen Reich seit
 * 1893); Sommerzeit gab es erst 1916. Der Ort liegt 20,5° östlich, die Sonne steht deshalb schon gegen
 * 11:40 Uhr MEZ im Süden. Sonnenstand nach den Formeln der NOAA, Mond aus seiner Phase vom Neumond am
 * 6. Januar 2000; übernommen aus Geyser und auf Königsberg umgestellt.
 */
public final class SunClock {
    public static final double LAT = 54.7065, LON = 20.5115;
    public static final int YEAR = 1910;
    /** MEZ, ganzjährig. */
    public static final int UTC_OFFSET = 1;
    public static final double SYNODIC = 29.530588853;
    private static final String[] MONTHS = {"Januar", "Februar", "März", "April", "Mai", "Juni", "Juli",
            "August", "September", "Oktober", "November", "Dezember"};
    private static final int[] MDAYS = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    private int day = 172;        // 21. Juni
    private double hour = 10.5;

    public double elevationDeg, azimuthDeg;
    public final double[] dir = new double[3];
    public final double[] moonDir = new double[3];
    public double moonPhase, moonLit, moonElevationDeg;
    public double solarHour;

    public SunClock() { update(); }

    public int day() { return day; }
    public double hour() { return hour; }

    public void set(int day, double hour) {
        this.day = Math.max(1, Math.min(365, day));
        this.hour = ((hour % 24) + 24) % 24;
        update();
    }

    public void advance(double hours) {
        double h = hour + hours;
        while (h >= 24) { h -= 24; day = day % 365 + 1; }
        while (h < 0) { h += 24; day = day == 1 ? 365 : day - 1; }
        hour = h;
        update();
    }

    private void update() {
        double utc = hour - UTC_OFFSET;
        double gamma = 2 * Math.PI / 365 * (day - 1 + (utc - 12) / 24);
        double eqt = 229.18 * (0.000075 + 0.001868 * Math.cos(gamma) - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2 * gamma) - 0.040849 * Math.sin(2 * gamma));
        double decl = 0.006918 - 0.399912 * Math.cos(gamma) + 0.070257 * Math.sin(gamma) - 0.006758 * Math.cos(2 * gamma)
                + 0.000907 * Math.sin(2 * gamma) - 0.002697 * Math.cos(3 * gamma) + 0.00148 * Math.sin(3 * gamma);
        double tst = hour * 60 + eqt + 4 * LON - 60 * UTC_OFFSET;
        solarHour = ((tst / 60) % 24 + 24) % 24;
        double H = Math.toRadians(tst / 4 - 180);
        elevationDeg = toDir(decl, H, dir);
        azimuthDeg = (Math.toDegrees(Math.atan2(dir[0], -dir[2])) + 360) % 360;
        double jd2000 = daysSince2000(day, utc);
        double age = jd2000 - 5.2597;
        moonPhase = ((age / SYNODIC) % 1 + 1) % 1;
        moonLit = 0.5 * (1 - Math.cos(2 * Math.PI * moonPhase));
        double eps = Math.toRadians(23.44);
        double lamS = Math.toRadians(280.46 + 0.9856474 * jd2000) + Math.toRadians(1.915) * Math.sin(Math.toRadians(357.528 + 0.9856003 * jd2000));
        double lamM = lamS + 2 * Math.PI * moonPhase;
        double betaM = Math.toRadians(5.1) * Math.sin(Math.toRadians(93.27 + 13.22935 * jd2000));
        double declM = Math.asin(Math.sin(betaM) * Math.cos(eps) + Math.cos(betaM) * Math.sin(eps) * Math.sin(lamM));
        double raS = Math.atan2(Math.cos(eps) * Math.sin(lamS), Math.cos(lamS));
        double raM = Math.atan2(Math.sin(lamM) * Math.cos(eps) - Math.tan(betaM) * Math.sin(eps), Math.cos(lamM));
        moonElevationDeg = toDir(declM, H + raS - raM, moonDir);
    }

    /** Tage seit dem 1. Januar 2000, 12 Uhr Weltzeit (für 1910 negativ). */
    static double daysSince2000(int day, double utc) {
        int days = 0;
        for (int k = YEAR; k < 2000; k++) days -= (k % 4 == 0 && (k % 100 != 0 || k % 400 == 0)) ? 366 : 365;
        return days + (day - 1) + (utc - 12) / 24.0;
    }

    static double toDir(double decl, double H, double[] out) {
        double lat = Math.toRadians(LAT);
        double sinEl = Math.sin(lat) * Math.sin(decl) + Math.cos(lat) * Math.cos(decl) * Math.cos(H);
        double el = Math.asin(Math.max(-1, Math.min(1, sinEl)));
        double az = Math.atan2(Math.sin(H), Math.cos(H) * Math.sin(lat) - Math.tan(decl) * Math.cos(lat)) + Math.PI;
        out[0] = Math.cos(el) * Math.sin(az);
        out[1] = Math.sin(el);
        out[2] = -Math.cos(el) * Math.cos(az);
        return Math.toDegrees(el);
    }

    public static String dateLabel(int day) {
        int m = 0, d = day;
        while (m < 11 && d > MDAYS[m]) { d -= MDAYS[m]; m++; }
        return d + ". " + MONTHS[m] + " " + YEAR;
    }

    public static String timeLabel(double h) {
        int hh = (int) Math.floor(h), mm = (int) Math.floor((h - hh) * 60);
        return String.format("%02d:%02d", hh, mm);
    }
}
