package com.dan.kneiphof.db;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ein SQL-Skript aus {@code db/NN_name.sql}. Jede Anweisung endet mit einer Zeile "/"; Kommentarzeilen (--) vor
 * einer Anweisung, SET, PROMPT und REM werden übergangen. Aus den Anweisungen liest der Einrichter, welche Objekte
 * danach da sein müssen (Tabellen, Folgen, Indizes, Trigger, Pakete) und, aus Zeilen "-- ERWARTET TABELLE=n", wie
 * viele Zeilen die Grunddaten mindestens bringen.
 */
public final class Skript {
    public final String name;
    public final List<String> anweisungen;
    public final Map<String, Integer> erwartetZeilen;

    /** Ein erwartetes Objekt: Typ wie in USER_OBJECTS und Name. */
    public static final class Objekt {
        public final String typ, name;
        Objekt(String typ, String name) { this.typ = typ; this.name = name; }
        @Override public boolean equals(Object o) { return o instanceof Objekt && ((Objekt) o).typ.equals(typ) && ((Objekt) o).name.equals(name); }
        @Override public int hashCode() { return typ.hashCode() * 31 + name.hashCode(); }
        @Override public String toString() { return typ + " " + name; }
    }

    private Skript(String name, List<String> a, Map<String, Integer> z) { this.name = name; this.anweisungen = a; this.erwartetZeilen = z; }

    /** Alle Skripte NN_*.sql des Ordners, nach Namen sortiert. */
    public static List<Skript> laden(File dir) throws IOException {
        File[] fs = dir.listFiles((d, n) -> n.matches("\\d\\d_.*\\.sql"));
        if (fs == null || fs.length == 0) throw new IOException("keine Skripte NN_*.sql in " + dir.getAbsolutePath());
        Arrays.sort(fs);
        List<Skript> l = new ArrayList<>();
        for (File f : fs) l.add(aus(f.getName(), new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
        return l;
    }

    private static final Pattern ERWARTET = Pattern.compile("^--\\s*ERWARTET\\s+(\\w+)\\s*=\\s*(\\d+)\\s*$");

    public static Skript aus(String name, String text) {
        List<String> out = new ArrayList<>();
        Map<String, Integer> erw = new LinkedHashMap<>();
        StringBuilder cur = new StringBuilder();
        for (String raw : text.replace("\r", "").split("\n", -1)) {
            String line = raw;
            String t = line.trim();
            if (t.equals("/")) { fertig(cur, out); continue; }
            Matcher m = ERWARTET.matcher(t);
            if (m.matches()) { erw.put(m.group(1).toUpperCase(), Integer.parseInt(m.group(2))); continue; }
            if (cur.length() == 0) {
                String u = t.toUpperCase();
                if (t.isEmpty() || t.startsWith("--") || u.startsWith("SET ") || u.startsWith("PROMPT") || u.startsWith("REM ") || u.equals("REM")) continue;
            }
            cur.append(line).append('\n');
        }
        fertig(cur, out);
        return new Skript(name, out, erw);
    }

    private static void fertig(StringBuilder cur, List<String> out) {
        String s = cur.toString().trim();
        cur.setLength(0);
        if (s.isEmpty()) return;
        String u = s.toUpperCase().replaceAll("\\s+", " ");
        boolean plsql = u.startsWith("BEGIN") || u.startsWith("DECLARE") || u.matches("^CREATE (OR REPLACE )?(TRIGGER|PACKAGE|FUNCTION|PROCEDURE|TYPE).*");
        if (!plsql && s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();
        out.add(s);
    }

    private static final Pattern TABLE = Pattern.compile("^CREATE TABLE (\\w+)"), SEQ = Pattern.compile("^CREATE SEQUENCE (\\w+)"),
            INDEX = Pattern.compile("^CREATE (?:UNIQUE )?INDEX (\\w+)"), TRG = Pattern.compile("^CREATE OR REPLACE TRIGGER (\\w+)"),
            PKG = Pattern.compile("^CREATE OR REPLACE PACKAGE (\\w+)"), BODY = Pattern.compile("^CREATE OR REPLACE PACKAGE BODY (\\w+)"),
            CONS = Pattern.compile("CONSTRAINT (\\w+) (?:PRIMARY KEY|UNIQUE)");

    /** Die Objekte, die dieses Skript anlegt (Constraint-Indizes für PRIMARY KEY und UNIQUE eingeschlossen). */
    public Set<Objekt> erwartet() {
        Set<Objekt> r = new LinkedHashSet<>();
        for (String a : anweisungen) {
            String u = a.toUpperCase().replaceAll("\\s+", " ");
            Matcher m;
            if ((m = TABLE.matcher(u)).find()) {
                r.add(new Objekt("TABLE", m.group(1)));
                Matcher c = CONS.matcher(u);
                while (c.find()) r.add(new Objekt("INDEX", c.group(1)));
            } else if ((m = SEQ.matcher(u)).find()) r.add(new Objekt("SEQUENCE", m.group(1)));
            else if ((m = INDEX.matcher(u)).find()) r.add(new Objekt("INDEX", m.group(1)));
            else if ((m = TRG.matcher(u)).find()) r.add(new Objekt("TRIGGER", m.group(1)));
            else if ((m = BODY.matcher(u)).find()) r.add(new Objekt("PACKAGE BODY", m.group(1)));
            else if ((m = PKG.matcher(u)).find()) r.add(new Objekt("PACKAGE", m.group(1)));
        }
        return r;
    }

    /** Kurzbeschreibung einer Anweisung für die Anzeige: die ersten Wörter ohne Umbrüche. */
    public static String kurz(String a) {
        String s = a.replaceAll("\\s+", " ");
        int p = s.indexOf(" (");
        if (p > 0 && p < 90) s = s.substring(0, p);
        return s.length() > 90 ? s.substring(0, 87) + " …" : s;
    }
}
