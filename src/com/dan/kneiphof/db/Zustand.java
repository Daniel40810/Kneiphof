package com.dan.kneiphof.db;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Zustand des Programms zum Wiederherstellen: Zeit, Wetter, Schalter, Bild und Kamera.
 * Die Felder sind genau die Spalten von KNH_STATE (Reihenfolge wie in {@code db/01_tables.sql}); als Datei
 * ({@code ~/.kneiphof/zustand.properties}) dient derselbe Satz als Rückfall ohne Datenbank.
 */
public final class Zustand {
    public enum Typ { ZAHL, FLAG, TEXT }

    /** Ein Feld: Name (= Spaltenname), Typ, Vorgabe und Grenzen (Zahlen). */
    public static final class Feld {
        public final String name;
        public final Typ typ;
        public final Object vorgabe;
        public final double min, max;
        Feld(String name, Typ typ, Object vorgabe, double min, double max) { this.name = name; this.typ = typ; this.vorgabe = vorgabe; this.min = min; this.max = max; }
    }

    private static Feld z(String n, double v, double min, double max) { return new Feld(n, Typ.ZAHL, v, min, max); }
    private static Feld j(String n, boolean v) { return new Feld(n, Typ.FLAG, v, 0, 1); }
    private static Feld t(String n, String v) { return new Feld(n, Typ.TEXT, v, 0, 0); }

    /** Reihenfolge und Vorgaben wie die Spalten von KNH_STATE ab TAG (siehe db/01_tables.sql). */
    public static final Feld[] SPEC = {
            z("TAG", 172, 1, 366), z("STUNDE", 10.5, 0, 24), z("ZEITRAFFER", 0, 0, 4), z("DUNST", 30, 0, 100), z("WIND", 30, 0, 120),
            z("NEBEL", 0, 0, 3), z("WETTER", 0, 0, 2), z("BELICHTUNG", 0, -20, 20), z("AUFLOESUNG", 100, 50, 100), j("AUFLOESUNG_AUTO", false),
            j("SCHATTEN", true), j("BLOOM", true), j("FXAA", true), j("SPIEGELUNG", true), j("STRAHLEN", true), j("FUSSGAENGER", true),
            j("MOEWEN", true), j("RAUCH", true), j("BAEUME", true), j("HAEUSER", true), j("SCHIFFE", true), j("PRUEFSTAND", false),
            j("BETRIEB", false), z("DREHPUNKT_X", -40, -1000000, 1000000), z("DREHPUNKT_Y", 4, -10000, 100000),
            z("DREHPUNKT_Z", 10, -1000000, 1000000), z("GIER_GRAD", 215, -10000, 10000), z("NICK_GRAD", 24, -90, 90), z("ABSTAND_M", 900, 0.1, 100000),
    };

    private static final Map<String, Feld> BY_NAME = new LinkedHashMap<>();
    static { for (Feld f : SPEC) BY_NAME.put(f.name, f); }

    /** Kopf der Zeile (kein Teil von SPEC). */
    public String name = "AUTO", art = "AUTO";
    public long gespeichertMs;
    public long sessionId;
    /** Ob die Kamera (Drehpunkt, Gier, Nick, Abstand) gesetzt wurde; sonst bleibt der Blickpunkt. */
    public boolean kameraGesetzt;

    private final Map<String, Object> werte = new LinkedHashMap<>();

    public Zustand() { for (Feld f : SPEC) werte.put(f.name, f.vorgabe); }

    public static Zustand vorgabe() { return new Zustand(); }

    public double zahl(String n) { return ((Number) werte.get(check(n))).doubleValue(); }
    public int ganz(String n) { return (int) Math.round(zahl(n)); }
    public boolean flag(String n) { return (Boolean) werte.get(check(n)); }
    public String text(String n) { return (String) werte.get(check(n)); }

    public Zustand setze(String n, double v) {
        Feld f = BY_NAME.get(check(n));
        if (f.typ != Typ.ZAHL) throw new IllegalArgumentException(n + " ist keine Zahl");
        werte.put(n, Double.isFinite(v) ? Math.max(f.min, Math.min(f.max, v)) : f.vorgabe);
        return this;
    }

    public Zustand setze(String n, boolean v) {
        if (BY_NAME.get(check(n)).typ != Typ.FLAG) throw new IllegalArgumentException(n + " ist kein Schalter");
        werte.put(n, v);
        return this;
    }

    public Zustand setze(String n, String v) {
        if (BY_NAME.get(check(n)).typ != Typ.TEXT) throw new IllegalArgumentException(n + " ist kein Text");
        werte.put(n, v == null ? BY_NAME.get(n).vorgabe : v);
        return this;
    }

    private static String check(String n) {
        if (!BY_NAME.containsKey(n)) throw new IllegalArgumentException("unbekanntes Feld " + n);
        return n;
    }

    public Zustand kopie() {
        Zustand k = new Zustand();
        k.name = name; k.art = art; k.gespeichertMs = gespeichertMs; k.sessionId = sessionId; k.kameraGesetzt = kameraGesetzt;
        k.werte.putAll(werte);
        return k;
    }

    // ------------------------------------------------------------ Datei (Rückfall ohne Datenbank)

    public Properties alsProperties() {
        Properties p = new Properties();
        p.setProperty("name", name);
        p.setProperty("art", art);
        p.setProperty("gespeichert", Long.toString(gespeichertMs));
        p.setProperty("kamera", Boolean.toString(kameraGesetzt));
        for (Feld f : SPEC) {
            Object v = werte.get(f.name);
            p.setProperty(f.name, v instanceof Double ? Double.toString((Double) v) : String.valueOf(v));
        }
        return p;
    }

    /** Liest einen Zustand; unlesbare oder fehlende Werte fallen auf die Vorgabe zurück. */
    public static Zustand ausProperties(Properties p) {
        Zustand z = new Zustand();
        z.name = p.getProperty("name", "AUTO");
        z.art = p.getProperty("art", "AUTO");
        try { z.gespeichertMs = Long.parseLong(p.getProperty("gespeichert", "0")); } catch (NumberFormatException e) { z.gespeichertMs = 0; }
        z.kameraGesetzt = Boolean.parseBoolean(p.getProperty("kamera", "false"));
        for (Feld f : SPEC) {
            String s = p.getProperty(f.name);
            if (s == null) continue;
            try {
                switch (f.typ) {
                    case ZAHL: z.setze(f.name, Double.parseDouble(s)); break;
                    case FLAG: z.setze(f.name, Boolean.parseBoolean(s)); break;
                    default: z.setze(f.name, s);
                }
            } catch (NumberFormatException e) { /* Vorgabe bleibt */ }
        }
        return z;
    }

    public static void speichern(File datei, Zustand z) throws IOException {
        File dir = datei.getAbsoluteFile().getParentFile();
        if (dir != null) Files.createDirectories(dir.toPath());
        File tmp = new File(datei.getPath() + ".tmp");
        try (OutputStream o = Files.newOutputStream(tmp.toPath())) { z.alsProperties().store(o, "Kneiphof: Zustand beim Beenden"); }
        Files.move(tmp.toPath(), datei.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Der Zustand aus der Datei, oder null, wenn es keine gibt oder sie unlesbar ist. */
    public static Zustand laden(File datei) {
        if (!datei.isFile()) return null;
        Properties p = new Properties();
        try (InputStream i = Files.newInputStream(datei.toPath())) { p.load(i); } catch (IOException e) { return null; }
        return ausProperties(p);
    }

    @Override public String toString() {
        return String.format("Zustand[%s %s Tag %d %.1f h Abstand %.0f m]", art, name, ganz("TAG"), zahl("STUNDE"), zahl("ABSTAND_M"));
    }
}
