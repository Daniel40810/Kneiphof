package com.dan.kneiphof.camera;

import com.dan.kneiphof.world.DomInterior;

import java.util.ArrayList;
import java.util.List;

/**
 * Regie: fertige Kamerafahrten. Jede Fahrt liefert zu jedem Zeitpunkt eine Lage (Drehpunkt, Blickwinkel, Abstand,
 * Brennweite); die Kreiskamera folgt ihr weich. Zwischen festen Stellen läuft die Bahn als Catmull-Rom-Kurve,
 * damit kein Knick entsteht. Kinobalken und Blenden gibt {@link #bars} und {@link #fade} an das Bild weiter.
 */
public final class Director {
    /** Was die Regie von der Szene wissen muss. */
    public interface Ctx {
        int boatCount();
        /** x, z, Richtung x, Richtung z. */
        double[] boatPose(int i);
        boolean boatIsSteamer(int i);
        int bridgeCount();
        /** Mitte x, z, Winkel (Bogenmaß), Fahrbahnhöhe. */
        double[] bridgePose(int i);
        String bridgeName(int i);
    }

    /** Eine Stelle der Bahn: Zeit, Lage x y z, Gier, Nick (Grad), Abstand, Öffnungswinkel; Beschriftung. */
    static final class K {
        final double t; final double[] p; final String label;
        K(double t, String label, double x, double y, double z, double yaw, double pitch, double dist, double fov) {
            this.t = t; this.label = label; this.p = new double[]{x, y, z, yaw, pitch, dist, fov};
        }
    }

    public static final class Tour {
        public final String name, note;
        public final double duration;
        /** Beginn: Tag und Stunde (Tag < 0: Uhr nicht anfassen), Zeitraffer (< 0: nicht anfassen). */
        public final int day; public final double hour, lapse;
        interface Path { double[] at(double t, Ctx c); }
        final Path path;
        final java.util.function.Function<Double, String> caption;
        Tour(String name, String note, double duration, int day, double hour, double lapse, Path path, java.util.function.Function<Double, String> caption) {
            this.name = name; this.note = note; this.duration = duration; this.day = day; this.hour = hour; this.lapse = lapse;
            this.path = path; this.caption = caption;
        }
    }

    private final List<Tour> tours = new ArrayList<>();
    private int cur = -1;
    private double t;
    /** Höhe eines Kinobalkens als Bruchteil der Bildhöhe (0 aus) und Abdunkelung (1 hell … 0 schwarz). */
    public double bars, fade = 1;
    private double barsNow;
    private Ctx ctx;

    public Director() {
        final double[] dom = DomInterior.toWorld(60, 0);
        // 1 Überflug
        List<K> k1 = new ArrayList<>();
        k1.add(new K(0, "Die Stadt aus der Höhe", -40, 4, 10, 320, 26, 1100, 50));
        k1.add(new K(12, "Der Pregel teilt sich um den Kneiphof", 60, 10, 0, 270, 18, 520, 46));
        k1.add(new K(26, "Der Dom", dom[0], 16, dom[1], 200, 12, 260, 42));
        k1.add(new K(40, "Der Dom von Osten", dom[0], 16, dom[1], 120, 10, 170, 38));
        k1.add(new K(55, "Tief über dem Wasser", 30, 5, 0, 30, 7, 300, 46));
        k1.add(new K(70, "Zurück über den Dächern", -40, 4, 10, 320, 26, 1100, 50));
        tours.add(keyed("Überflug über Königsberg", "Von den Dächern über den Dom zum Wasser und zurück, bei Morgensonne.", 70, 172, 6.8, 0, k1));
        // 2 Sieben Brücken
        tours.add(new Tour("Die sieben Brücken", "Jede der sieben Brücken, mit dem Blick durch die Durchfahrt, im Abendlicht.", 7 * 10.0, 172, 18.2, 0,
                (tt, c) -> bridgesPath(tt, c), tt -> {
                    if (ctx == null) return "";
                    int i = Math.min(ctx.bridgeCount() - 1, (int) (tt / 10.0));
                    return "Brücke " + (i + 1) + " von " + ctx.bridgeCount() + ": " + ctx.bridgeName(i);
                }));
        // 3 Dampferfahrt
        tours.add(new Tour("Mit dem Dampfer", "Die Kamera begleitet den Dampfer, kreist um ihn und lässt den Qualm vorbeiziehen.", 80, 172, 16.0, 0,
                (tt, c) -> steamerPath(tt, c), tt -> "Auf dem Pregel"));
        // 4 Dom im Abendlicht
        tours.add(new Tour("Der Dom im Abendlicht", "Ein langer Bogen um den Dom, während die Sonne tief steht.", 60, 172, 19.0, 0,
                (tt, c) -> {
                    double u = tt / 60.0, s = u * u * (3 - 2 * u);
                    return new double[]{dom[0], 17 - 3 * s, dom[1], 200 + 150 * s, 14 - 4 * s, 230 - 140 * s, 44 - 8 * s};
                }, tt -> tt < 30 ? "Der Dom des Kneiphofs" : "Backstein im Gegenlicht"));
        // 5 Tag und Nacht
        tours.add(new Tour("Ein Tag in 90 Sekunden", "Feste Kamera über der Stadt; die Uhr läuft von 3 Uhr morgens im Zeitraffer durch.", 90, 172, 3.0, 960,
                (tt, c) -> new double[]{-40, 4, 10, 330 + 25 * tt / 90.0, 24 - 4 * Math.sin(tt / 90.0 * Math.PI), 1000, 50}, tt -> "Sommertag 1910"));
    }

    // ------------------------------------------------------------------ Bahnen

    private Tour keyed(String name, String note, double dur, int day, double hour, double lapse, List<K> keys) {
        final double[][] pts = new double[keys.size()][];
        double prev = 0;
        for (int i = 0; i < pts.length; i++) {
            pts[i] = keys.get(i).p.clone();
            // Gier ohne Sprung fortsetzen
            if (i > 0) while (pts[i][3] - pts[i - 1][3] > 180) pts[i][3] -= 360;
            if (i > 0) while (pts[i][3] - pts[i - 1][3] < -180) pts[i][3] += 360;
        }
        final double[] times = new double[pts.length];
        for (int i = 0; i < pts.length; i++) times[i] = keys.get(i).t;
        return new Tour(name, note, dur, day, hour, lapse, (tt, c) -> spline(pts, times, tt), tt -> {
            String l = keys.get(0).label;
            for (K k : keys) if (k.t <= tt) l = k.label;
            return l;
        });
    }

    static double[] spline(double[][] p, double[] times, double t) {
        int n = p.length;
        int i = 0;
        while (i < n - 2 && times[i + 1] <= t) i++;
        double u = (t - times[i]) / Math.max(1e-6, times[i + 1] - times[i]);
        u = Math.max(0, Math.min(1, u));
        double[] a = p[Math.max(0, i - 1)], b = p[i], c = p[i + 1], d = p[Math.min(n - 1, i + 2)];
        double[] r = new double[7];
        double u2 = u * u, u3 = u2 * u;
        for (int k = 0; k < 7; k++)
            r[k] = 0.5 * ((2 * b[k]) + (-a[k] + c[k]) * u + (2 * a[k] - 5 * b[k] + 4 * c[k] - d[k]) * u2 + (-a[k] + 3 * b[k] - 3 * c[k] + d[k]) * u3);
        return r;
    }

    private static final double PER = 10.0;

    private double[] bridgesPath(double tt, Ctx c) {
        int n = Math.max(1, c.bridgeCount());
        int i = Math.min(n - 1, (int) (tt / PER));
        double u = (tt - i * PER) / PER;
        double[] b = c.bridgePose(i);
        double base = (180.0 - Math.toDegrees(b[2]) + 720.0) % 360.0;
        // vor dem Wechsel zur nächsten Brücke: weiche Überblendung der Lage
        double[] cur = bridgeShot(b, base, u);
        if (u > 0.82 && i + 1 < n) {
            double[] nb = c.bridgePose(i + 1);
            double[] nxt = bridgeShot(nb, (180.0 - Math.toDegrees(nb[2]) + 720.0) % 360.0, 0.0);
            double w = (u - 0.82) / 0.18;
            w = w * w * (3 - 2 * w);
            double dyaw = ((nxt[3] - cur[3]) % 360 + 540) % 360 - 180;
            double[] r = new double[7];
            for (int k = 0; k < 7; k++) r[k] = cur[k] + (nxt[k] - cur[k]) * w;
            r[3] = cur[3] + dyaw * w;
            // über der Zwischenstrecke etwas höher, damit man keine Häuser schneidet
            r[1] += 50 * Math.sin(Math.PI * w);
            r[5] += 90 * Math.sin(Math.PI * w);
            return r;
        }
        return cur;
    }

    private static double[] bridgeShot(double[] b, double base, double u) {
        double s = u * u * (3 - 2 * u);
        return new double[]{b[0], b[3] + 1.5, b[1], base - 30 + 70 * s, 14 - 6 * s, 85 - 40 * s, 46 - 8 * s};
    }

    private double[] steamerPath(double tt, Ctx c) {
        int idx = -1;
        for (int i = 0; i < c.boatCount(); i++) if (c.boatIsSteamer(i)) { idx = i; break; }
        if (idx < 0) return new double[]{-40, 4, 10, 320, 26, 900, 50};
        double[] p = c.boatPose(idx);
        double behind = Math.toDegrees(Math.atan2(-p[2], -p[3]));
        double sweep = 85 * Math.sin(tt * 2 * Math.PI / 80.0);
        double dist = 62 + 28 * Math.sin(tt * 2 * Math.PI / 40.0 + 1);
        return new double[]{p[0], 4.5, p[1], behind + sweep, 9 + 5 * Math.sin(tt * 2 * Math.PI / 55.0), dist, 44};
    }

    // ------------------------------------------------------------------ Steuerung

    public String[] names() {
        String[] n = new String[tours.size()];
        for (int i = 0; i < n.length; i++) n[i] = tours.get(i).name;
        return n;
    }
    /** Lage einer Fahrt zur Zeit tt (Prüfwerkzeuge). */
    public double[] poseAt(int i, double tt, Ctx c) { ctx = c; return tours.get(i).path.at(tt, c); }
    public Tour tour(int i) { return tours.get(i); }
    public boolean active() { return cur >= 0; }
    public Tour current() { return cur < 0 ? null : tours.get(cur); }
    public double time() { return t; }
    public String caption() { return cur < 0 ? "" : tours.get(cur).caption.apply(t); }

    /** Fahrt i beginnen (der Aufrufer stellt Uhr und Zeitraffer, siehe {@link Tour}). */
    public void start(int i, Ctx c) {
        ctx = c;
        cur = i;
        t = 0;
        fade = 0;
    }

    public void stop() {
        cur = -1;
    }

    /** Weiterrechnen; setzt Ziel der Kreiskamera. Gibt true zurück, solange eine Fahrt läuft. */
    public boolean update(double dt, OrbitCamera cam) {
        // Balken laufen sanft ein und aus, auch nach dem Ende
        double want = cur >= 0 ? 1 : 0;
        barsNow += (want - barsNow) * Math.min(1, dt * 2.2);
        bars = barsNow * 0.115;
        if (cur < 0) { fade += (1 - fade) * Math.min(1, dt * 4); return false; }
        Tour tr = tours.get(cur);
        t += dt;
        double[] p = tr.path.at(Math.min(t, tr.duration), ctx);
        cam.autoOrbit = false;
        cam.lookAt(p[0], p[1], p[2], p[3], p[4], p[5]);
        cam.fovy = p[6];
        // Blende: am Anfang aus Schwarz, am Ende nach Schwarz
        double in = Math.min(1, t / 2.5), out = Math.min(1, (tr.duration - t) / 2.5);
        fade = Math.max(0, Math.min(in, out));
        if (in < 1 && t < 0.05) cam.snap();
        if (t >= tr.duration) { cur = -1; }
        return true;
    }
}
