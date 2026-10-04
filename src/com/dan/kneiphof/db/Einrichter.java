package com.dan.kneiphof.db;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Der Einrichter: prüft die Verbindung, nimmt den Bestand auf (db/bestand.txt), führt die Skripte db/01 bis 04 aus,
 * prüft danach jedes Objekt, die Geometrie, das Paket (Funktionsprobe mit Rücknahme) und die Grunddaten und meldet
 * "ERGEBNIS: alles in Ordnung" oder die Zahl der Probleme. Das Protokoll steht in db/einrichtung.txt. Er legt nur
 * Fehlendes an: Was es schon gibt, wird nicht verändert und nicht gelöscht, und die Grunddaten werden nur eingefügt,
 * nie überschrieben, damit Änderungen in der Datenbank bleiben.
 */
public final class Einrichter {
    private Einrichter() { }

    public interface Ausgabe { void zeile(String s); }

    public static final class Ergebnis {
        public final boolean ok;
        public final int probleme;
        public final String text;
        Ergebnis(int probleme, String text) { this.ok = probleme == 0; this.probleme = probleme; this.text = text; }
    }

    /** Fehlernummern, die nur sagen "gibt es schon": Name belegt, Spalten schon indiziert, Schlüssel und Fremdschlüssel da. */
    private static final Set<Integer> VORHANDEN = new HashSet<>(Arrays.asList(955, 1408, 1430, 2260, 2261, 2264, 2275));

    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    public static Ergebnis lauf(Connection c, File dbDir, Ausgabe aus) {
        final StringBuilder log = new StringBuilder();
        final Ausgabe out = s -> { log.append(s).append('\n'); if (aus != null) aus.zeile(s); };
        int probleme = 0;
        out.zeile("Einrichter Kneiphof · " + LocalDateTime.now().format(ZEIT));
        try {
            // ---- 1 Verbindung
            out.zeile("");
            out.zeile("1  Verbindung");
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT SYS_CONTEXT('USERENV','SESSION_USER'), SYS_CONTEXT('USERENV','CON_NAME'), SYS_CONTEXT('USERENV','SERVICE_NAME') FROM DUAL")) {
                rs.next();
                out.zeile("   Benutzer " + rs.getString(1) + ", Container " + rs.getString(2) + ", Dienst " + rs.getString(3));
            }
            String prod = c.getMetaData().getDatabaseProductVersion();
            out.zeile("   " + prod.split("\n")[0].trim());

            // ---- 2 Bestand
            out.zeile("");
            out.zeile("2  Bestand im Schema");
            Map<String, Integer> typen = new TreeMap<>();
            StringBuilder best = new StringBuilder("Bestand im Schema, aufgenommen " + LocalDateTime.now().format(ZEIT) + "\n\nTYP\tNAME\tSTATUS\n");
            int hei = 0, alle = 0;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT OBJECT_TYPE, OBJECT_NAME, STATUS FROM USER_OBJECTS ORDER BY OBJECT_TYPE, OBJECT_NAME")) {
                while (rs.next()) {
                    String t = rs.getString(1), n = rs.getString(2);
                    typen.merge(t, 1, Integer::sum);
                    best.append(t).append('\t').append(n).append('\t').append(rs.getString(3)).append('\n');
                    alle++;
                    if (n.startsWith("KNH_")) hei++;
                }
            }
            StringBuilder sum = new StringBuilder();
            for (String t : new String[]{"TABLE", "VIEW", "SEQUENCE", "TRIGGER", "PACKAGE", "INDEX"}) {
                Integer n = typen.get(t);
                if (n == null) continue;
                if (sum.length() > 0) sum.append(", ");
                sum.append(n).append(' ').append(t);
            }
            out.zeile("   " + alle + " Objekte (" + sum + ")");
            out.zeile(hei == 0 ? "   Das Präfix KNH_ ist frei." : "   " + hei + " Objekte mit dem Präfix KNH_ gibt es schon; sie bleiben, nur Fehlendes wird ergänzt.");
            schreibe(new File(dbDir, "bestand.txt"), best.toString(), out);

            // ---- 3 Skripte
            out.zeile("");
            out.zeile("3  Skripte in " + dbDir.getPath());
            List<Skript> skripte;
            try {
                skripte = Skript.laden(dbDir);
            } catch (IOException e) {
                out.zeile("   FEHLER: " + e.getMessage());
                return fertig(1, log, dbDir, out);
            }
            Map<String, Integer> zeilen = new LinkedHashMap<>();
            Set<Skript.Objekt> erwartet = new LinkedHashSet<>();
            try (Statement st = c.createStatement()) {
                st.setEscapeProcessing(false);
                for (Skript s : skripte) {
                    int neu = 0, vorh = 0, fehler = 0;
                    long rows = 0;
                    List<String> meldungen = new ArrayList<>();
                    for (String sql : s.anweisungen) {
                        try {
                            st.clearWarnings();
                            st.execute(sql);
                            int n = st.getUpdateCount();
                            if (n > 0 && (sql.regionMatches(true, 0, "INSERT", 0, 6) || sql.regionMatches(true, 0, "MERGE", 0, 5))) rows += n;
                            neu++;
                            SQLWarning w = st.getWarnings();
                            if (w != null && !String.valueOf(w.getMessage()).contains("24344"))
                                meldungen.add("   Hinweis bei " + Skript.kurz(sql) + ": " + w.getMessage().trim());
                        } catch (SQLException e) {
                            if (VORHANDEN.contains(e.getErrorCode())) vorh++;
                            else {
                                fehler++;
                                meldungen.add("   FEHLER ORA-" + String.format("%05d", e.getErrorCode()) + " bei " + Skript.kurz(sql) + ": " + erste(e.getMessage()));
                            }
                        }
                    }
                    String z = s.name + ": " + s.anweisungen.size() + " Anweisungen, " + neu + " ausgeführt, " + vorh + " schon vorhanden, " + fehler + " Fehler";
                    if (rows > 0) z += ", " + rows + " Datenzeilen neu";
                    out.zeile("   " + z);
                    for (String m : meldungen) out.zeile(m);
                    probleme += fehler;
                    erwartet.addAll(s.erwartet());
                    for (Map.Entry<String, Integer> e : s.erwartetZeilen.entrySet()) zeilen.merge(e.getKey(), e.getValue(), Math::max);
                }
            }

            // ---- 4 Prüfung
            out.zeile("");
            out.zeile("4  Prüfung");
            probleme += pruefeObjekte(c, erwartet, out);
            probleme += funktionsprobe(c, out);
            probleme += pruefeZeilen(c, zeilen, out);
        } catch (SQLException e) {
            out.zeile("   FEHLER: " + erste(e.getMessage()));
            probleme++;
        }
        return fertig(probleme, log, dbDir, out);
    }

    private static Ergebnis fertig(int probleme, StringBuilder log, File dbDir, Ausgabe out) {
        out.zeile("");
        out.zeile(probleme == 0 ? "ERGEBNIS: alles in Ordnung" : "ERGEBNIS: " + probleme + (probleme == 1 ? " Problem" : " Probleme") + ", siehe oben");
        schreibe(new File(dbDir, "einrichtung.txt"), log.toString(), null);
        return new Ergebnis(probleme, log.toString());
    }

    private static void schreibe(File f, String text, Ausgabe out) {
        try {
            if (f.getParentFile() != null) Files.createDirectories(f.getParentFile().toPath());
            Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
            if (out != null) out.zeile("   Bestand geschrieben nach " + f.getPath());
        } catch (IOException e) {
            if (out != null) out.zeile("   Hinweis: " + f.getPath() + " nicht schreibbar (" + e.getMessage() + ")");
        }
    }

    private static String erste(String m) {
        if (m == null) return "";
        int i = m.indexOf('\n');
        return (i > 0 ? m.substring(0, i) : m).trim();
    }

    /** Jedes erwartete Objekt muss da und gültig sein; Übersetzungsfehler aus USER_ERRORS werden gezeigt. */
    private static int pruefeObjekte(Connection c, Set<Skript.Objekt> erwartet, Ausgabe out) throws SQLException {
        Map<String, String> status = new java.util.HashMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT OBJECT_TYPE, OBJECT_NAME, STATUS FROM USER_OBJECTS WHERE OBJECT_NAME LIKE 'KNH\\_%' ESCAPE '\\'")) {
            while (rs.next()) status.put(rs.getString(1) + " " + rs.getString(2), rs.getString(3));
        }
        int probleme = 0;
        Map<String, int[]> je = new TreeMap<>();
        for (Skript.Objekt o : erwartet) {
            int[] n = je.computeIfAbsent(o.typ, k -> new int[2]);
            String s = status.get(o.typ + " " + o.name);
            if (s == null) { out.zeile("   FEHLT: " + o.typ + " " + o.name); probleme++; }
            else if (!s.equals("VALID")) { out.zeile("   UNGÜLTIG: " + o.typ + " " + o.name); probleme++; }
            else n[0]++;
        }
        StringBuilder sum = new StringBuilder();
        for (Map.Entry<String, int[]> e : je.entrySet()) {
            if (sum.length() > 0) sum.append(", ");
            sum.append(e.getValue()[0]).append(' ').append(e.getKey());
        }
        out.zeile("   Objekte vorhanden und gültig: " + sum);
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT NAME, TYPE, LINE, TEXT FROM USER_ERRORS WHERE NAME LIKE 'KNH\\_%' ESCAPE '\\' ORDER BY NAME, TYPE, SEQUENCE")) {
            int k = 0;
            while (rs.next() && k++ < 12) out.zeile("   Übersetzungsfehler " + rs.getString(1) + " (" + rs.getString(2) + ") Zeile " + rs.getInt(3) + ": " + erste(rs.getString(4)));
        }
        return probleme;
    }

    /** Sitzung anlegen, protokollieren, beenden und einen Zustand schreiben, dann alles zurücknehmen. */
    private static int funktionsprobe(Connection c, Ausgabe out) {
        boolean auto = true;
        try {
            auto = c.getAutoCommit();
            c.setAutoCommit(false);
            String v;
            try (CallableStatement cs = c.prepareCall("{? = call KNH_API.version}")) {
                cs.registerOutParameter(1, Types.VARCHAR);
                cs.execute();
                v = cs.getString(1);
            }
            long sid;
            try (CallableStatement cs = c.prepareCall("{? = call KNH_API.session_start(?,?,?,?,?,?,?,?,?,?)}")) {
                cs.registerOutParameter(1, Types.NUMERIC);
                cs.setString(2, "Probe"); cs.setString(3, "Einrichter"); cs.setString(4, "0"); cs.setString(5, "Probe"); cs.setString(6, "Probe");
                cs.setInt(7, 1); cs.setInt(8, 1); cs.setInt(9, 1); cs.setInt(10, 1); cs.setString(11, "N");
                cs.execute();
                sid = cs.getLong(1);
            }
            try (CallableStatement cs = c.prepareCall("{call KNH_API.log_event(?,?,?,?,?,?,?)}")) {
                cs.setLong(1, sid); cs.setDouble(2, 0); cs.setString(3, "EINRICHTER"); cs.setString(4, "Funktionsprobe"); cs.setString(5, "ok");
                cs.setInt(6, 172); cs.setDouble(7, 16);
                cs.execute();
            }
            try (CallableStatement cs = c.prepareCall("{call KNH_API.session_end(?,?,?,?)}")) {
                cs.setLong(1, sid); cs.setDouble(2, 30); cs.setDouble(3, 25); cs.setInt(4, 100);
                cs.execute();
            }
            long sti;
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO KNH_STATE (NAME, ART, SESSION_ID) VALUES ('PROBE', 'MANUELL', ?)", new String[]{"STATE_ID"})) {
                ps.setLong(1, sid);
                ps.executeUpdate();
                try (ResultSet k = ps.getGeneratedKeys()) { k.next(); sti = k.getLong(1); }
            }
            long latest;
            try (CallableStatement cs = c.prepareCall("{? = call KNH_API.state_latest('MANUELL')}")) {
                cs.registerOutParameter(1, Types.NUMERIC);
                cs.execute();
                latest = cs.getLong(1);
            }
            int ev, dauer;
            try (PreparedStatement ps = c.prepareStatement("SELECT (SELECT COUNT(*) FROM KNH_EVENT WHERE SESSION_ID = ?), (SELECT NVL(DAUER_S, -1) FROM KNH_SESSION WHERE SESSION_ID = ?) FROM DUAL")) {
                ps.setLong(1, sid); ps.setLong(2, sid);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); ev = rs.getInt(1); dauer = rs.getInt(2); }
            }
            boolean gut = ev == 1 && dauer >= 0 && latest == sti;
            out.zeile("   Funktionsprobe (" + v + "): Sitzung, Protokoll, Beenden, Zustand " + (gut ? "ok, zurückgenommen" : "FEHLER: Ereignisse " + ev + ", Dauer " + dauer + ", Zustand " + (latest == sti)));
            return gut ? 0 : 1;
        } catch (SQLException e) {
            out.zeile("   Funktionsprobe: FEHLER " + erste(e.getMessage()));
            return 1;
        } finally {
            try { c.rollback(); c.setAutoCommit(auto); } catch (SQLException e) { /* ignorieren */ }
        }
    }

    private static int pruefeZeilen(Connection c, Map<String, Integer> erwartet, Ausgabe out) throws SQLException {
        int probleme = 0;
        StringBuilder sum = new StringBuilder();
        for (Map.Entry<String, Integer> e : erwartet.entrySet()) {
            if (!e.getKey().matches("[A-Z0-9_]+")) continue;
            int n;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + e.getKey())) { rs.next(); n = rs.getInt(1); }
            if (sum.length() > 0) sum.append(", ");
            sum.append(e.getKey().replace("KNH_", "")).append(' ').append(n);
            if (n < e.getValue()) { out.zeile("   ZU WENIG ZEILEN: " + e.getKey() + " hat " + n + ", erwartet mindestens " + e.getValue()); probleme++; }
        }
        out.zeile("   Grunddaten (Zeilen): " + sum);
        return probleme;
    }

    /** Auf der Konsole: java -cp "...;lib/*" com.dan.kneiphof.db.Einrichter  (Passwort aus db.properties, Umgebung oder Eingabe). */
    public static void main(String[] args) throws Exception {
        Db.Konfig k = Db.konfig();
        if (!k.hatPasswort()) {
            java.io.Console con = System.console();
            if (con == null) { System.err.println("Kein Passwort: in db/db.properties eintragen oder KNEIPHOF_DB_PASSWORD setzen."); System.exit(2); }
            k = k.mitPasswort(con.readPassword("Passwort für " + k.user + ": "));
        }
        try (Connection c = Db.oeffnen(k)) {
            Ergebnis e = lauf(c, Db.ordner(), System.out::println);
            System.exit(e.ok ? 0 : 1);
        }
    }
}
