package com.dan.kneiphof.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Die Stadt um 1910: Bürgerhäuser an einem Straßenraster, das der Richtung der Inseln folgt (21°), dazu die
 * großen Bauten (Dom, Rathaus, Gymnasium, Albertina, Börse). Häuser entstehen nur auf festem Boden mit
 * Abstand zum Wasser; nahe dem Dom mit Fenstern und Türen, weiter weg nur als Baukörper mit Dach.
 * <ul>
 * <li>Bürgerhäuser 2 bis 4 Geschosse, Backstein oder Putz in sechs Farben</li>
 * <li>Giebelhaus zur Straße (Treppengiebel bei jedem dritten) oder Traufhaus</li>
 * <li>Dächer: Biberschwanz rot und braun, Schiefer; Schornsteine</li>
 * </ul>
 */
public final class Town {
    public static final double ANGLE = Math.toRadians(21.0);
    /** Mitte der Bebauung und Halbmesser, in dem Häuser stehen. */
    public static final double CX = 60, CZ = 30, RADIUS = 980;
    /** Bis hierhin (vom Dom) bekommen Häuser Fenster und Türen. */
    public static final double DETAIL = 380;

    /** Grundfläche eines Gebäudes (gedrehtes Rechteck) für Abstandsprüfung und Baumpflanzung. */
    public static final class Foot {
        final double cx, cz, c, s, hu, hw;
        /** Höhe der Oberkante (Dachfirst) für den Kameraschutz; ohne Angabe wird das Gebäude nicht zum Hindernis. */
        double top = Double.NEGATIVE_INFINITY;
        Foot top(double t) { this.top = t; return this; }
        Foot(double cx, double cz, double angle, double hu, double hw) {
            this.cx = cx; this.cz = cz; this.c = Math.cos(angle); this.s = Math.sin(angle); this.hu = hu; this.hw = hw;
        }
        boolean covers(double x, double z, double margin) {
            double dx = x - cx, dz = z - cz;
            double u = dx * c + dz * s, w = -dx * s + dz * c;
            return Math.abs(u) <= hu + margin && Math.abs(w) <= hw + margin;
        }
    }

    private final List<Foot> feet = new ArrayList<>();
    private final HashMap<Long, List<Foot>> grid = new HashMap<>();
    private final List<Foot> reserved = new ArrayList<>();
    public int houses, detailed;

    /** Eine Kachel der Stadt: eigenes Netz mit Umgrenzung für die Sichtbereichsprüfung. */
    public static final class Tile {
        public final MeshBuilder mb = new MeshBuilder();
        public double minX, maxX, minZ, maxZ;
        public static final double MIN_Y = -3, MAX_Y = 60;
    }
    public static final double CELL = 200, MARGIN = 40;
    private final HashMap<Long, Tile> tileMap = new HashMap<>();
    private final List<Tile> tiles = new ArrayList<>();

    /** Mündungen aller Schornsteine (x, y, z) für den Rauch. */
    public final List<double[]> chimneys = new ArrayList<>();

    public Town() { }

    private static volatile Town current;

    /**
     * Oberkante des Gebäudes, in dessen Grundfläche (plus Rand) der Punkt liegt; sonst −∞. Die Kamera hebt sich darüber,
     * statt durch ein Dach zu fahren.
     */
    public static double solidTop(double x, double z, double margin) {
        Town t = current;
        if (t == null) return Double.NEGATIVE_INFINITY;
        List<Foot> l = t.grid.get(key((int) Math.floor(x / 16), (int) Math.floor(z / 16)));
        double top = Double.NEGATIVE_INFINITY;
        if (l != null) for (Foot f : l) if (f.top > top && f.covers(x, z, margin)) top = f.top;
        return top;
    }

    /** Die Auffahrten der Brücken freihalten: keine Häuser, keine Bäume. */
    public void reserveBridges(Bridges br) {
        for (Bridges.Bridge b : br.list)
            for (int e = 0; e < 2; e++) {
                double sShore = e == 0 ? b.sMin : b.sMax;
                double mid = sShore + (e == 0 ? -22 : 22);
                Foot f = new Foot(b.wx(mid, 0), b.wz(mid, 0), b.angle, 25, b.width / 2 + 5);
                reserved.add(f);
                register(f);
            }
    }

    private Tile tile(double x, double z) {
        int i = (int) Math.floor(x / CELL), j = (int) Math.floor(z / CELL);
        return tileMap.computeIfAbsent(key(i, j), k -> {
            Tile t = new Tile();
            t.minX = i * CELL - MARGIN; t.maxX = (i + 1) * CELL + MARGIN;
            t.minZ = j * CELL - MARGIN; t.maxZ = (j + 1) * CELL + MARGIN;
            tiles.add(t);
            return t;
        });
    }

    // ------------------------------------------------------------------------------------ Abfrage
    private static long key(int i, int j) { return ((long) i << 32) ^ (j & 0xffffffffL); }

    private void register(Foot f) {
        feet.add(f);
        double r = Math.hypot(f.hu, f.hw) + 4;
        for (int i = (int) Math.floor((f.cx - r) / 16); i <= (int) Math.floor((f.cx + r) / 16); i++)
            for (int j = (int) Math.floor((f.cz - r) / 16); j <= (int) Math.floor((f.cz + r) / 16); j++)
                grid.computeIfAbsent(key(i, j), k -> new ArrayList<>()).add(f);
    }

    /** Steht hier (mit Rand) ein Gebäude? */
    public boolean covers(double x, double z, double margin) {
        List<Foot> l = grid.get(key((int) Math.floor(x / 16), (int) Math.floor(z / 16)));
        if (l == null) return false;
        for (Foot f : l) if (f.covers(x, z, margin)) return true;
        return false;
    }

    private boolean blockedByReserved(double x, double z, double margin) {
        for (Foot f : reserved) if (f.covers(x, z, margin)) return true;
        return false;
    }

    /** Darf ein Haus über diesem Punkt stehen? */
    private boolean buildable(double x, double z) {
        if (blockedByReserved(x, z, 3.0)) return false;
        if (Site.water(x, z) < 9.0) return false;
        if (Site.onKneiphof(x, z)) return true;
        if (Math.hypot(x - CX, z - CZ) > RADIUS) return false;
        float[] c = new float[4];
        Site.cover(x, z, c);
        if (c[0] < 0.6f) return false;
        return Math.abs(Site.height(x, z) - Site.height(x + 6, z + 6)) < 0.9;
    }

    // ------------------------------------------------------------------------------------ Aufbau
    /** Alle Kacheln zu einem Netz (Prüfwerkzeuge). */
    public MeshBuilder build() {
        MeshBuilder all = new MeshBuilder();
        for (Tile t : buildTiles()) all.append(t.mb);
        return all;
    }

    public List<Tile> buildTiles() {
        MeshBuilder m = null;
        // Sperrflächen der großen Bauten
        reserved.add(new Foot(Dom.CX, Dom.CZ, Dom.ANGLE, (Dom.L + 18) / 2 + 6, 28));
        reserved.add(new Foot(-78, 2, ANGLE, 22, 12));
        reserved.add(new Foot(104, -12, ANGLE, 20, 10));
        reserved.add(new Foot(172, -9, ANGLE, 16, 10));
        reserved.add(new Foot(-150, 178, 0, 24, 16));
        reserved.add(new Foot(Testbed.CX, Testbed.CZ, 0, 40, 30));

        landmarks(m);
        streetBlocks(m);
        current = this;
        return tiles;
    }

    private void landmarks(MeshBuilder m) {
        rathaus();
        gymnasium();
        albertina();
        boerse();

        Tile dt = new Tile();
        dt.minX = Dom.CX - 80; dt.maxX = Dom.CX + 80; dt.minZ = Dom.CZ - 80; dt.maxZ = Dom.CZ + 80;
        dt.mb.append(Dom.build());
        tiles.add(dt);
        register(new Foot(Dom.CX, Dom.CZ, Dom.ANGLE, Dom.L / 2 + 2, 13).top(Dom.GROUND + 45.0));
    }

    /** fensterreihe an einer Wand (Mitte uw, ww; Fläche face 0 = +w) auf Geschoss f. */
    private static void fenster(Obj o, double uw, double ww, double sc, double y0, double h, double w, boolean lit) {
        double s = sc - uw;   // sc ist die Lage im Gebäudebezug, rect misst ab der Wandmitte
        o.rect(uw, ww, 0, s - w / 2 - 0.14, s + w / 2 + 0.14, y0 - 0.14, y0 + h + 0.12, 0.02, Material.PLASTER0 + 4, 0.9);
        o.rect(uw, ww, 0, s - w / 2, s + w / 2, y0, y0 + h, 0.05, lit ? Material.PANE_LIT : Material.PANE, 1);
    }

    /** Rathaus des Kneiphofs: langer Backsteinbau, Mittelrisalit mit Giebel, Dachgauben, Turm über der Mitte. */
    private void rathaus() {
        Obj r = new Obj(tile(-78, 2).mb, -78 - Math.sin(ANGLE) * 8, 2 + Math.cos(ANGLE) * 8, ANGLE);
        double[] h = house(r, 36, 16, 3, 4.2, Material.BRICK, Material.SLATE, false, false, true, 99, true);
        double g = h[0], yE = h[1], yR = h[2];
        // Mittelrisalit, 11 m breit, mit Sockel, Fenstern, Portal und Quergiebel
        r.box(-5.8, -1.0, 5.8, 1.6, g - 1.0, g + 0.9, Material.GRANITE, 0.6);
        r.box(-5.5, -1.0, 5.5, 1.5, g + 0.9, yE, Material.BRICK, 0.8);
        Obj rr = r.turned(Math.PI / 2);
        rr.gableRoof(-9, -5.8, 1.5, 5.8, yE, yE + 5.4, 0.2, 0.0, Material.SLATE, Material.BRICK);
        r.box(-5.7, -0.3, 5.7, 1.7, yE - 0.3, yE + 0.02, Material.PLASTER0 + 4, 1);
        double[] ys = {g + 1.6, g + 5.7, g + 9.9};
        for (int f = 0; f < 3; f++)
            for (int k = -1; k <= 1; k++) {
                double sc = k * 3.2;
                if (f == 0 && k == 0) {
                    r.rect(0, 1.5, 0, -1.1, 1.1, g + 0.9, g + 3.9, 0.02, Material.GRANITE, 0.9);
                    r.rect(0, 1.5, 0, -0.9, 0.9, g + 1.0, g + 3.7, 0.06, Material.WOOD, 0.9);
                    Obj st = new Obj(r.m, r.wx(0, 2.2), r.wz(0, 2.2), r.angle);
                    st.box(-1.8, -0.8, 1.8, 0.8, g - 0.2, g + 0.5, Material.GRANITE, 0.6);
                    continue;
                }
                fenster(r, 0, 1.5, sc, ys[f], f == 0 ? 1.7 : 1.55, 1.25, (f + k) % 2 == 0);
            }
        r.fan(0, 1.5, 0, new double[][]{{-0.6, yE + 0.7}, {0.6, yE + 0.7}, {0.6, yE + 2.0}, {0.0, yE + 3.0}, {-0.6, yE + 2.0}}, 0.05, Material.PANE, 1);
        // Dachgauben auf der Traufseite
        double[] gu = {-14, -8, 8, 14};
        for (double u : gu) {
            double rise = yR - yE, wy = -2.0;
            double yb = yE + rise * (-wy) / 8.0;
            r.box(u - 0.95, wy - 2.0, u + 0.95, wy, yb - 0.2, yb + 2.5, Material.BRICK, 1);
            r.pyramid(u - 1.25, wy - 2.3, u + 1.25, wy + 0.3, yb + 2.5, yb + 4.0, Material.SLATE);
            r.rect(u, wy, 0, -0.45, 0.45, yb + 0.5, yb + 2.0, 0.03, Material.PANE, 1);
        }
        // Turm über der Mitte
        Obj t = new Obj(tile(-78, 2).mb, r.wx(0, -8), r.wz(0, -8), ANGLE);
        t.box(-3.0, -3.0, 3.0, 3.0, g + 12.0, g + 30.0, Material.BRICK, 1);
        t.box(-3.3, -3.3, 3.3, 3.3, g + 30.0, g + 30.5, Material.GRANITE, 1);
        t.box(-2.4, -2.4, 2.4, 2.4, g + 30.5, g + 33.0, Material.PLASTER0 + 4, 1);   // Glockenstube
        t.pyramid(-3.1, -3.1, 3.1, 3.1, g + 33.0, g + 44.0, Material.COPPER);
        t.lathe(0, 0, new double[]{0.35, 0.35, 0.0}, new double[]{g + 44.0, g + 46.5, g + 47.5}, 6, Material.COPPER);
        for (int f = 0; f < 4; f++) {
            t.fan(f == 3 ? -3 : f == 1 ? 3 : 0, f == 0 ? 3 : f == 2 ? -3 : 0, f, Obj.pointedArch(0, g + 21.0, 1.4, 3.0, 4), 0.05, Material.GLASS_G, 1);
            t.fan(f == 3 ? -2.4 : f == 1 ? 2.4 : 0, f == 0 ? 2.4 : f == 2 ? -2.4 : 0, f, Obj.pointedArch(0, g + 30.8, 0.8, 1.8, 3), 0.05, Material.GLASS_G, 1);
        }
        register(new Foot(-78, 2, ANGLE, 18, 8).top(yR + 2));
        register(new Foot(t.cx, t.cz, ANGLE, 3.6, 3.6).top(g + 48));
    }

    /** Kneiphöfsches Gymnasium: Putzbau mit zwei Schaugiebeln an den Enden und einem Dachreiter. */
    private void gymnasium() {
        Obj gym = new Obj(tile(104, -12).mb, 104 - Math.sin(ANGLE) * 6, -12 + Math.cos(ANGLE) * 6, ANGLE);
        double[] h = house(gym, 34, 13, 3, 4.0, Material.PLASTER0 + 1, Material.ROOF_BROWN, false, false, true, 11, true);
        double g = h[0], yE = h[1], yR = h[2];
        for (int e = -1; e <= 1; e += 2) {
            double u = e * 12.2;
            gym.box(u - 3.4, -0.9, u + 3.4, 1.1, g - 0.6, yE + 0.1, Material.BRICK, 0.8);
            gym.stepGable(u, 1.1, 0, 3.4, yE + 0.1, 5.2, 6, 0.7, Material.BRICK);
            for (int f = 0; f < 3; f++) {
                double y0 = f == 0 ? g + 1.6 : f == 1 ? g + 5.4 : g + 9.4;
                for (int k = -1; k <= 1; k += 2) fenster(gym, u, 1.1, u + k * 1.4, y0, f == 0 ? 1.7 : 1.55, 1.2, f == 1 && e > 0);
            }
            gym.fan(u, 1.1, 0, new double[][]{{-0.5, yE + 1.2}, {0.5, yE + 1.2}, {0.5, yE + 2.4}, {0, yE + 3.2}, {-0.5, yE + 2.4}}, 0.05, Material.PANE, 1);
        }
        // Dachreiter auf dem First
        double wm = -6.5;
        gym.box(-1.1, wm - 1.1, 1.1, wm + 1.1, yR - 0.3, yR + 2.6, Material.PLASTER0 + 4, 1);
        gym.pyramid(-1.4, wm - 1.4, 1.4, wm + 1.4, yR + 2.6, yR + 7.2, Material.COPPER);
        for (int f = 0; f < 2; f++)
            gym.fan(0, f == 0 ? wm + 1.1 : wm - 1.1, f == 0 ? 0 : 2, new double[][]{{-0.4, yR + 0.2}, {0.4, yR + 0.2}, {0.4, yR + 1.9}, {-0.4, yR + 1.9}}, 0.04, Material.PANE, 1);
        register(new Foot(104, -12, ANGLE, 17, 7).top(yR + 2));
    }

    /** Albertina (Universität): neugotischer Backsteinbau mit zwei Eckturmen und Giebelportal. */
    private void albertina() {
        Obj alb = new Obj(tile(172, -9).mb, 172 - Math.sin(ANGLE) * 5, -9 + Math.cos(ANGLE) * 5, ANGLE);
        double[] h = house(alb, 26, 12, 3, 4.0, Material.BRICK, Material.ROOF, false, false, true, 17, true);
        double g = h[0], yE = h[1], yR = h[2];
        for (int e = -1; e <= 1; e += 2) {
            double u0 = e > 0 ? 8.4 : -13.2, u1 = u0 + 4.8, uc = (u0 + u1) / 2;
            alb.box(u0 - 0.15, -3.6, u1 + 0.15, 2.2, g - 0.8, g + 0.9, Material.GRANITE, 0.6);
            alb.box(u0, -3.4, u1, 2.0, g + 0.9, yE + 5.0, Material.BRICK, 0.8);
            alb.box(u0 - 0.2, -3.6, u1 + 0.2, 2.2, yE + 5.0, yE + 5.5, Material.GRANITE, 1);
            alb.pyramid(u0 - 0.3, -3.7, u1 + 0.3, 2.3, yE + 5.5, yE + 15.0, Material.SLATE);
            for (int f = 0; f < 3; f++)
                alb.fan(uc, 2.0, 0, Obj.pointedArch(0, f == 0 ? g + 1.6 : f == 1 ? g + 5.6 : g + 9.8, 0.62, f == 0 ? 2.1 : 1.9, 3), 0.05, f == 1 ? Material.GLASS_G_LIT : Material.GLASS_G, 1);
            alb.fan(uc, 2.0, 0, Obj.pointedArch(0, yE + 1.3, 0.7, 2.4, 3), 0.05, Material.GLASS_G, 1);
        }
        // Giebelportal in der Mitte
        alb.box(-2.6, -0.9, 2.6, 1.1, g - 0.5, yE + 0.5, Material.BRICK, 0.8);
        Obj rr = alb.turned(Math.PI / 2);
        rr.gableRoof(-8, -2.6, 1.1, 2.6, yE + 0.5, yE + 5.8, 0.15, 0.0, Material.ROOF, Material.BRICK);
        alb.fan(0, 1.1, 0, Obj.pointedArch(0, g + 0.5, 1.5, 4.2, 4), 0.06, Material.GRANITE, 0.9);
        alb.fan(0, 1.1, 0, Obj.pointedArch(0, g + 0.5, 1.15, 3.7, 4), 0.1, Material.WOOD, 0.9);
        alb.fan(0, 1.1, 0, Obj.pointedArch(0, yE - 3.2, 0.6, 2.2, 3), 0.05, Material.GLASS_G_LIT, 1);
        register(new Foot(172, -9, ANGLE, 15, 7).top(yR + 2));
        register(new Foot(alb.wx(-10.8, -0.7), alb.wz(-10.8, -0.7), ANGLE, 2.6, 3.0).top(yE + 17));
        register(new Foot(alb.wx(10.8, -0.7), alb.wz(10.8, -0.7), ANGLE, 2.6, 3.0).top(yE + 17));
    }

    /** Börse am Alten Pregel: Putzbau mit Säulenhalle, Kuppel über der Mitte und Kupferdach. */
    private void boerse() {
        Obj bo = new Obj(tile(-150, 168).mb, -150, 168, Math.PI);
        double[] h = house(bo, 46, 20, 3, 5.0, Material.PLASTER0 + 4, Material.COPPER, false, false, true, 5, true);
        double g = h[0], yE = h[1], yR = h[2];
        // Säulenhalle vor der Mitte: Stufen, sechs Säulen über zwei Geschosse, Gebälk, Dreiecksgiebel
        bo.box(-10, 0.0, 10, 4.6, g - 0.8, g + 0.9, Material.GRANITE, 0.6);
        double top = g + 11.4;
        for (int k = 0; k < 6; k++) bo.column(-7.5 + k * 3.0, 3.0, g + 0.9, top, 0.55, Material.PLASTER0 + 4);
        bo.box(-9.4, 0.0, 9.4, 3.8, top, top + 1.1, Material.PLASTER0 + 4, 1);
        bo.tri(bo.p(-9.4, top + 1.1, 3.8), bo.p(9.4, top + 1.1, 3.8), bo.p(0, top + 3.6, 3.8), Material.PLASTER0 + 4, 1, 1);
        bo.tri(bo.p(9.4, top + 1.1, 0.0), bo.p(-9.4, top + 1.1, 0.0), bo.p(0, top + 3.6, 0.0), Material.PLASTER0 + 4, 1, 1);
        // Kuppel
        double b = yR - 0.4, cw = -10.0;
        bo.lathe(0, cw, new double[]{6.3, 6.3, 6.0}, new double[]{b, b + 4.2, b + 4.5}, 16, Material.PLASTER0 + 4);
        for (int k = 0; k < 8; k++) {
            double a = -2 * Math.PI * (k + 0.5) / 8;
            Obj wn = bo.at(6.32 * Math.cos(a), cw + 6.32 * Math.sin(a), 0);
            Obj f = new Obj(bo.m, wn.cx, wn.cz, bo.angle + a - Math.PI / 2);
            f.rect(0, 0, 0, -0.6, 0.6, b + 1.2, b + 3.2, 0.0, Material.PANE_LIT, 1);
        }
        bo.dome(0, cw, b + 4.5, 6.0, 6.5, Material.COPPER);
        bo.lathe(0, cw, new double[]{1.0, 1.0, 0.7, 0.0}, new double[]{b + 11.0, b + 13.0, b + 13.3, b + 15.8}, 8, Material.COPPER);
        register(new Foot(-150, 178, 0, 23, 10).top(yR + 2));
        register(new Foot(bo.wx(0, cw), bo.wz(0, cw), 0, 6.5, 6.5).top(b + 17));
    }

    private static double jit(int i, int salt) {
        long h = i * 0x9E3779B97F4A7C15L + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32;
        return ((h & 0xffff) / 32767.5) - 1;
    }

    private void streetBlocks(MeshBuilder m) {
        final int N = 27;
        double[] ul = new double[2 * N + 2], vl = new double[2 * N + 2];
        for (int i = 0; i < ul.length; i++) ul[i] = (i - N) * 54.0 + jit(i, 1) * 7;
        for (int j = 0; j < vl.length; j++) vl[j] = (j - N) * 44.0 + jit(j, 2) * 5;
        Obj grid = new Obj(new MeshBuilder(), 0, 0, ANGLE);
        double sw = 3.4;
        for (int i = 0; i + 1 < ul.length; i++)
            for (int j = 0; j + 1 < vl.length; j++) {
                double u0 = ul[i] + sw, u1 = ul[i + 1] - sw, v0 = vl[j] + sw, v1 = vl[j + 1] - sw;
                double mx = grid.wx((u0 + u1) / 2, (v0 + v1) / 2), mz = grid.wz((u0 + u1) / 2, (v0 + v1) / 2);
                if (Math.hypot(mx - CX, mz - CZ) > RADIUS + 60) continue;
                SplittableRandom rnd = new SplittableRandom(i * 7919L + j * 104729L + 17);
                double depth = Math.min(12.5 + rnd.nextDouble() * 1.5, (v1 - v0) / 2 - 1.2);
                if (depth < 6) continue;
                // Südzeile (Front nach +v), Nordzeile (Front nach −v), dann West und Ost dazwischen
                row(m, grid, rnd, u0, u1, v1, depth, ANGLE, 0);
                row(m, grid, rnd, u0, u1, v0, depth, ANGLE + Math.PI, 1);
                if (v1 - v0 - 2 * depth > 8) {
                    double a = v0 + depth, b = v1 - depth;
                    sideRow(m, grid, rnd, u0, a, b, depth, ANGLE + Math.PI / 2, 2);   // West: Front nach −u
                    sideRow(m, grid, rnd, u1, a, b, depth, ANGLE - Math.PI / 2, 3);   // Ost: Front nach +u
                }
            }
    }

    /** Eine Häuserzeile an der Südseite (front=+v) oder Nordseite (front=−v) des Blocks. */
    private void row(MeshBuilder m, Obj grid, SplittableRandom rnd, double u0, double u1, double vFront, double depth, double angle, int salt) {
        double u = u0;
        boolean north = salt == 1;
        while (u < u1 - 6) {
            double bw = 7.0 + rnd.nextDouble() * 5.5;
            if (u + bw > u1) bw = u1 - u;
            if (bw < 6) break;
            double uc = u + bw / 2;
            // Bezugssystem des Hauses: Frontmitte, w nach außen. Nordzeile: u-Achse läuft entgegen.
            double fx = grid.wx(uc, vFront), fz = grid.wz(uc, vFront);
            place(m, rnd, fx, fz, angle, bw, depth - rnd.nextDouble() * 1.0);
            u += bw + (rnd.nextDouble() < 0.1 ? 3.0 : 0.0);
        }
    }

    private void sideRow(MeshBuilder m, Obj grid, SplittableRandom rnd, double uFront, double v0, double v1, double depth, double angle, int salt) {
        double v = v0;
        while (v < v1 - 6) {
            double bw = 7.0 + rnd.nextDouble() * 4.5;
            if (v + bw > v1) bw = v1 - v;
            if (bw < 6) break;
            double vc = v + bw / 2;
            double fx = grid.wx(uFront, vc), fz = grid.wz(uFront, vc);
            place(m, rnd, fx, fz, angle, bw, depth - rnd.nextDouble() * 1.0);
            v += bw;
        }
    }

    private void place(MeshBuilder m, SplittableRandom rnd, double fx, double fz, double angle, double bw, double d) {
        Obj o = new Obj(tile(fx, fz).mb, fx, fz, angle);
        // Prüfpunkte der Grundfläche
        double[][] pts = {{-bw / 2, 0}, {bw / 2, 0}, {-bw / 2, -d}, {bw / 2, -d}, {0, -d / 2}, {0, -d}};
        for (double[] p : pts) if (!buildable(o.wx(p[0], p[1]), o.wz(p[0], p[1]))) return;
        double cdist = Math.hypot(o.wx(0, -d / 2) - Dom.CX, o.wz(0, -d / 2) - Dom.CZ);
        boolean detail = cdist < DETAIL;
        boolean island = Site.onKneiphof(o.cx, o.cz);
        int floors = island ? 3 + (rnd.nextDouble() < 0.4 ? 1 : 0) : 2 + (rnd.nextDouble() < 0.6 ? 1 : 0);
        if (rnd.nextDouble() < 0.08) floors++;
        double r = rnd.nextDouble();
        int wall = r < 0.40 ? Material.BRICK : Material.PLASTER0 + rnd.nextInt(6);
        double rr = rnd.nextDouble();
        int roof = rr < 0.46 ? Material.ROOF : rr < 0.76 ? Material.ROOF_BROWN : Material.SLATE;
        boolean gable = rnd.nextDouble() < 0.62;
        boolean stepped = gable && rnd.nextDouble() < 0.34;
        double[] hh = house(o, bw, d, floors, 3.2 + rnd.nextDouble() * 0.5, wall, roof, gable, stepped, detail, rnd.nextInt(1 << 20), false);
        register(new Foot(o.wx(0, -d / 2), o.wz(0, -d / 2), angle, bw / 2, d / 2).top(hh[2] + 2.0));
        houses++;
        if (detail) detailed++;
    }

    /**
     * Ein Haus mit der Frontmitte im Ursprung von o, Front nach +w, Tiefe d nach −w.
     * big: großer Bau mit gleichmäßiger Fensterreihe und flacherem Dach.
     */
    private double[] house(Obj o, double bw, double d, int floors, double floorH, int wall, int roof, boolean gable, boolean stepped,
                       boolean detail, int seed, boolean big) {
        SplittableRandom rnd = new SplittableRandom(seed * 31L + 7);
        double g = Math.min(Math.min(Site.height(o.wx(-bw / 2, 0), o.wz(-bw / 2, 0)), Site.height(o.wx(bw / 2, 0), o.wz(bw / 2, 0))),
                Math.min(Site.height(o.wx(-bw / 2, -d), o.wz(-bw / 2, -d)), Site.height(o.wx(bw / 2, -d), o.wz(bw / 2, -d))));
        g = Math.max(g, -0.2);
        double groundH = big ? floorH + 0.6 : floorH + 0.5;
        double yE = g + groundH + (floors - 1) * floorH;
        // Grundmauer und Wände
        o.box(-bw / 2 - 0.08, -d - 0.08, bw / 2 + 0.08, 0.08, g - 1.2, g + 0.7, Material.GRANITE, 0.55);
        o.wallsOnly(-bw / 2, -d, bw / 2, 0, g + 0.7, yE, wall, 0.75);
        double half = gable ? bw / 2 : d / 2;
        double rise = Math.min(half * (big ? 0.85 : 1.15), big ? 6.0 : 9.0);
        double yR = yE + rise;
        if (gable) {
            Obj rr = o.turned(Math.PI / 2);
            rr.gableRoof(-d, -bw / 2, 0, bw / 2, yE, yR, 0.45, stepped ? 0.0 : 0.25, roof, stepped ? 0 : wall);
            if (stepped) {
                int steps = Math.max(3, (int) Math.round(rise / 0.95));
                o.stepGable(0, 0.04, 0, bw / 2, yE, rise, steps, 0.6, wall);
            }
        } else {
            o.gableRoof(-bw / 2, -d, bw / 2, 0, yE, yR, 0.5, 0.3, roof, wall);
        }
        // Schornstein
        if (rnd.nextDouble() < 0.75 || big) {
            double cu = gable ? (rnd.nextDouble() - 0.5) * bw * 0.4 : (rnd.nextDouble() - 0.5) * bw * 0.6;
            double cw = gable ? -d * (0.35 + 0.3 * rnd.nextDouble()) : -d / 2;
            double base = gable ? yE + rise * Math.max(0.0, 1.0 - Math.abs(cu) / (bw / 2)) - 1.0 : yR - 1.2;
            o.box(cu - 0.45, cw - 0.45, cu + 0.45, cw + 0.45, base, Math.max(yR, base) + 1.6, Material.BRICK, 1);
            chimneys.add(new double[]{o.wx(cu, cw), Math.max(yR, base) + 1.7, o.wz(cu, cw)});
        }
        if (!detail) return new double[]{g, yE, yR};

        // Fassade: Fensterreihen, Tür, Gesimse
        int nb = Math.max(2, (int) Math.round(bw / (big ? 3.4 : 3.0)));
        double step = bw / nb;
        int door = big ? nb / 2 : rnd.nextInt(nb);
        boolean cornice = big || rnd.nextDouble() < 0.5;
        for (int f = 0; f < floors; f++) {
            double y0 = g + (f == 0 ? 0.7 : groundH + (f - 1) * floorH) + (f == 0 ? 0.9 : 0.9);
            double wh = f == 0 ? 1.7 : 1.55, ww = big ? 1.25 : 1.05;
            for (int k = 0; k < nb; k++) {
                double sc = -bw / 2 + (k + 0.5) * step;
                if (f == 0 && k == door) {
                    o.rect(sc, 0, 0, -0.95, 0.95, g + 0.7, g + 3.35, 0.02, Material.GRANITE, 0.9);
                    o.rect(sc, 0, 0, -0.78, 0.78, g + 0.9, g + 3.2, 0.06, Material.WOOD, 0.9);
                    // zwei Stufen
                    Obj st = new Obj(o.m, o.wx(sc, 0.5), o.wz(sc, 0.5), o.angle);
                    st.box(-1.2, -0.5, 1.2, 0.5, g - 0.1, g + 0.35, Material.GRANITE, 0.6);
                    continue;
                }
                boolean lit = rnd.nextDouble() < 0.38;
                o.rect(sc, 0, 0, -ww / 2 - 0.14, ww / 2 + 0.14, y0 - 0.14, y0 + wh + 0.12, 0.02, Material.PLASTER0 + 4, 0.9);
                o.rect(sc, 0, 0, -ww / 2, ww / 2, y0, y0 + wh, 0.05, lit ? Material.PANE_LIT : Material.PANE, 1);
                if (f == 0 || f == floors - 1)
                    o.box(sc - ww / 2 - 0.2, 0, sc + ww / 2 + 0.2, 0.14, y0 - 0.2, y0 - 0.1, Material.GRANITE, 1);
            }
            if (f < floors - 1 && cornice)
                o.box(-bw / 2 - 0.05, -0.18, bw / 2 + 0.05, 0.12, g + groundH + f * floorH - 0.12, g + groundH + f * floorH + 0.1, Material.PLASTER0 + 4, 1);
        }
        if (cornice) o.box(-bw / 2 - 0.12, -0.35, bw / 2 + 0.12, 0.2, yE - 0.28, yE + 0.02, Material.PLASTER0 + 4, 1);
        // Speicherluke / Dachfenster im Giebel
        if (gable && rise > 3.0) {
            boolean lit = rnd.nextDouble() < 0.3;
            double ya = yE + 1.0;
            if (!stepped) o.rect(0, 0, 0, -0.45, 0.45, ya, ya + 1.1, 0.03, lit ? Material.PANE_LIT : Material.PANE, 1);
            else o.rect(0, 0.04, 0, -0.45, 0.45, ya, ya + 1.1, 0.07, lit ? Material.PANE_LIT : Material.PANE, 1);
        }
        return new double[]{g, yE, yR};
    }
}
