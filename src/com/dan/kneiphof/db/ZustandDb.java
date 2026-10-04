package com.dan.kneiphof.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Zustände in KNH_STATE schreiben und lesen; die Spalten sind die Felder von {@link Zustand#SPEC}. */
public final class ZustandDb {
    private ZustandDb() { }

    /** Eine Zeile der Liste gemerkter Zustände. */
    public static final class Eintrag {
        public final long id;
        public final String name;
        public final long ms;
        Eintrag(long id, String name, long ms) { this.id = id; this.name = name; this.ms = ms; }
        @Override public String toString() { return name; }
    }

    private static String spalten() {
        StringBuilder b = new StringBuilder();
        for (Zustand.Feld f : Zustand.SPEC) b.append(", ").append(f.name);
        return b.toString();
    }

    /** Fügt den Zustand ein und liefert seine STATE_ID. */
    public static long speichern(Connection c, Zustand z, long sessionId) throws SQLException {
        StringBuilder cols = new StringBuilder("NAME, ART, SESSION_ID"), ph = new StringBuilder("?, ?, ?");
        for (Zustand.Feld f : Zustand.SPEC) { cols.append(", ").append(f.name); ph.append(", ?"); }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO KNH_STATE (" + cols + ") VALUES (" + ph + ")", new String[]{"STATE_ID"})) {
            int i = 1;
            ps.setString(i++, z.name.length() > 60 ? z.name.substring(0, 60) : z.name);
            ps.setString(i++, z.art);
            if (sessionId > 0) ps.setLong(i++, sessionId); else ps.setNull(i++, Types.NUMERIC);
            for (Zustand.Feld f : Zustand.SPEC) {
                switch (f.typ) {
                    case ZAHL: ps.setDouble(i++, z.zahl(f.name)); break;
                    case FLAG: ps.setString(i++, z.flag(f.name) ? "J" : "N"); break;
                    default: ps.setString(i++, z.text(f.name));
                }
            }
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { k.next(); return k.getLong(1); }
        }
    }

    public static Zustand laden(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT NAME, ART, GESPEICHERT, SESSION_ID" + spalten() + " FROM KNH_STATE WHERE STATE_ID = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Zustand z = new Zustand();
                z.name = rs.getString(1);
                z.art = rs.getString(2);
                Timestamp t = rs.getTimestamp(3);
                z.gespeichertMs = t == null ? 0 : t.getTime();
                z.sessionId = rs.getLong(4);
                int i = 5;
                for (Zustand.Feld f : Zustand.SPEC) {
                    switch (f.typ) {
                        case ZAHL: z.setze(f.name, rs.getDouble(i)); break;
                        case FLAG: z.setze(f.name, "J".equals(rs.getString(i))); break;
                        default: z.setze(f.name, rs.getString(i));
                    }
                    i++;
                }
                z.kameraGesetzt = true;
                return z;
            }
        }
    }

    /** STATE_ID des jüngsten Zustands der Art, oder 0. */
    public static long juengster(Connection c, String art) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT MAX(STATE_ID) FROM KNH_STATE WHERE ART = ?")) {
            ps.setString(1, art);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    /** Die gemerkten (MANUELL) Zustände, neueste zuerst. */
    public static List<Eintrag> gemerkte(Connection c) throws SQLException {
        List<Eintrag> l = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT STATE_ID, NAME, GESPEICHERT FROM KNH_STATE WHERE ART = 'MANUELL' ORDER BY STATE_ID DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next() && l.size() < 50) { Timestamp t = rs.getTimestamp(3); l.add(new Eintrag(rs.getLong(1), rs.getString(2), t == null ? 0 : t.getTime())); }
        }
        return l;
    }

    /** Löscht einen gemerkten Zustand (nur Art MANUELL). */
    public static void loeschen(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM KNH_STATE WHERE STATE_ID = ? AND ART = 'MANUELL'")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }
}
