package com.dan.kneiphof.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Ufer des Pregel in der Stadt: Kaimauern aus Granitquadern mit Deckstein, dahinter ein gepflasterter
 * Kai, darauf Poller und Gaslaternen; an den Speichern der Lomse und am Hundegatt hölzerne Dalben vor
 * der Mauer (Bollwerk).
 * <p>
 * Die Uferlinie wird nicht von Hand gezeichnet, sondern aus der Wasserkarte von {@link Site} gewonnen
 * (Höhenlinie 0 auf einem Raster von 1,5 m, „marching squares“), zu Linienzügen verkettet und geglättet.
 * So passen Mauer und Gelände immer zueinander, auch wenn sich die Arme später noch ändern.
 */
public final class Banks {
    /** Ein Uferzug: Punkte x, z und die Normale zum Land. */
    public static final class Line {
        public final double[] x, z, nx, nz, s;
        public final boolean quay;
        Line(double[] x, double[] z, double[] nx, double[] nz, boolean quay) {
            this.x = x; this.z = z; this.nx = nx; this.nz = nz; this.quay = quay;
            s = new double[x.length];
            for (int i = 1; i < x.length; i++) s[i] = s[i - 1] + Math.hypot(x[i] - x[i - 1], z[i] - z[i - 1]);
        }
        public int size() { return x.length; }
        public double length() { return s[s.length - 1]; }
    }

    public static final double X0 = -1750, X1 = 1750, Z0 = -950, Z1 = 950, CELL = 1.5;
    /** Unterkante der Mauer (unter dem Flussbett an der Mauer). */
    static final double WALL_FOOT = -2.6;

    public final List<Line> lines = new ArrayList<>();
    /** Punkte für Bäume, Laternen usw. auf dem Kai: x, z, Richtung zum Land nx, nz. */
    public final List<double[]> lanterns = new ArrayList<>();

    public Banks() {
        trace();
    }

    // ------------------------------------------------------------------ Uferlinie

    private void trace() {
        int nx = (int) Math.round((X1 - X0) / CELL) + 1, nz = (int) Math.round((Z1 - Z0) / CELL) + 1;
        double[] d = new double[nx * nz];
        java.util.stream.IntStream.range(0, nz).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) d[j * nx + i] = Site.water(X0 + i * CELL, Z0 + j * CELL);
        });
        // Kanten: Schlüssel = (i, j, 0 waagerecht | 1 senkrecht)
        Map<Long, List<long[]>> adj = new HashMap<>();
        Map<Long, double[]> pt = new HashMap<>();
        List<long[]> segs = new ArrayList<>();
        for (int j = 0; j + 1 < nz; j++)
            for (int i = 0; i + 1 < nx; i++) {
                double a = d[j * nx + i], b = d[j * nx + i + 1], c = d[(j + 1) * nx + i + 1], e = d[(j + 1) * nx + i];
                int code = (a < 0 ? 1 : 0) | (b < 0 ? 2 : 0) | (c < 0 ? 4 : 0) | (e < 0 ? 8 : 0);
                if (code == 0 || code == 15) continue;
                // Kanten der Zelle: 0 unten (a-b), 1 rechts (b-c), 2 oben (e-c), 3 links (a-e)
                long[] ek = {key(i, j, 0), key(i + 1, j, 1), key(i, j + 1, 0), key(i, j, 1)};
                double[][] ep = {
                        lerp(X0 + i * CELL, Z0 + j * CELL, X0 + (i + 1) * CELL, Z0 + j * CELL, a, b),
                        lerp(X0 + (i + 1) * CELL, Z0 + j * CELL, X0 + (i + 1) * CELL, Z0 + (j + 1) * CELL, b, c),
                        lerp(X0 + i * CELL, Z0 + (j + 1) * CELL, X0 + (i + 1) * CELL, Z0 + (j + 1) * CELL, e, c),
                        lerp(X0 + i * CELL, Z0 + j * CELL, X0 + i * CELL, Z0 + (j + 1) * CELL, a, e)};
                int[][] pairs = cases(code, (a + b + c + e) / 4 < 0);
                for (int[] p : pairs) {
                    long k0 = ek[p[0]], k1 = ek[p[1]];
                    pt.put(k0, ep[p[0]]);
                    pt.put(k1, ep[p[1]]);
                    long[] sg = {k0, k1};
                    segs.add(sg);
                    adj.computeIfAbsent(k0, q -> new ArrayList<>()).add(sg);
                    adj.computeIfAbsent(k1, q -> new ArrayList<>()).add(sg);
                }
            }
        // Verketten
        java.util.Set<long[]> used = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (long[] start : segs) {
            if (used.contains(start)) continue;
            java.util.ArrayDeque<Long> chain = new java.util.ArrayDeque<>();
            used.add(start);
            chain.add(start[0]);
            chain.add(start[1]);
            extend(chain, adj, used, true);
            extend(chain, adj, used, false);
            if (chain.size() < 8) continue;
            List<double[]> pts = new ArrayList<>();
            for (long k : chain) pts.add(pt.get(k));
            addLines(smooth(pts));
        }
    }

    private static void extend(java.util.ArrayDeque<Long> chain, Map<Long, List<long[]>> adj, java.util.Set<long[]> used, boolean atEnd) {
        while (true) {
            long end = atEnd ? chain.peekLast() : chain.peekFirst();
            long[] next = null;
            for (long[] s : adj.getOrDefault(end, List.of())) if (!used.contains(s)) { next = s; break; }
            if (next == null) return;
            used.add(next);
            long other = next[0] == end ? next[1] : next[0];
            if (atEnd) chain.addLast(other); else chain.addFirst(other);
        }
    }

    /** Teilt einen Uferzug nach Kaimauer und Wiesenufer und berechnet die Normalen. */
    private void addLines(List<double[]> pts) {
        int n = pts.size();
        boolean[] q = new boolean[n];
        for (int i = 0; i < n; i++) q[i] = Site.isQuay(pts.get(i)[0], pts.get(i)[1]);
        int a = 0;
        while (a < n) {
            int b = a;
            while (b + 1 < n && q[b + 1] == q[a]) b++;
            if (b - a >= 3) {
                int m = b - a + 1;
                double[] x = new double[m], z = new double[m], nx = new double[m], nz = new double[m];
                for (int k = 0; k < m; k++) {
                    double[] p = pts.get(a + k);
                    x[k] = p[0]; z[k] = p[1];
                    double e = 0.6;
                    double gx = Site.water(p[0] + e, p[1]) - Site.water(p[0] - e, p[1]);
                    double gz = Site.water(p[0], p[1] + e) - Site.water(p[0], p[1] - e);
                    double l = Math.hypot(gx, gz);
                    nx[k] = l > 1e-9 ? gx / l : 0;
                    nz[k] = l > 1e-9 ? gz / l : 1;
                }
                lines.add(new Line(x, z, nx, nz, q[a]));
            }
            a = b + 1;
        }
    }

    /** Glätten (zweimal gleitendes Mittel) und auf 2 m Abstand neu verteilen. */
    private static List<double[]> smooth(List<double[]> p) {
        for (int pass = 0; pass < 3; pass++) {
            List<double[]> o = new ArrayList<>(p.size());
            for (int i = 0; i < p.size(); i++) {
                if (i == 0 || i == p.size() - 1) { o.add(p.get(i)); continue; }
                double[] a = p.get(i - 1), b = p.get(i), c = p.get(i + 1);
                o.add(new double[]{(a[0] + 2 * b[0] + c[0]) / 4, (a[1] + 2 * b[1] + c[1]) / 4});
            }
            p = o;
        }
        List<double[]> r = new ArrayList<>();
        r.add(p.get(0));
        double acc = 0;
        for (int i = 1; i < p.size(); i++) {
            double[] a = p.get(i - 1), b = p.get(i);
            double seg = Math.hypot(b[0] - a[0], b[1] - a[1]);
            acc += seg;
            if (acc >= 2.0) { r.add(b); acc = 0; }
        }
        if (r.get(r.size() - 1) != p.get(p.size() - 1)) r.add(p.get(p.size() - 1));
        return r;
    }

    // ------------------------------------------------------------------ Netz

    /** Baut Kaimauern, Decksteine, Kaipflaster, Poller, Laternen und Dalben. */
    public MeshBuilder build() {
        MeshBuilder m = new MeshBuilder();
        lanterns.clear();
        for (Line L : lines) {
            if (!L.quay) continue;
            int n = L.size();
            double nextBollard = 9, nextLamp = 18, nextPile = 3;
            boolean bollwerk = false;
            for (int k = 0; k + 1 < n; k++) {
                double x0 = L.x[k], z0 = L.z[k], x1 = L.x[k + 1], z1 = L.z[k + 1];
                double nx0 = L.nx[k], nz0 = L.nz[k], nx1 = L.nx[k + 1], nz1 = L.nz[k + 1];
                double h0 = quayTop(x0, z0, nx0, nz0), h1 = quayTop(x1, z1, nx1, nz1);
                double s0 = L.s[k], s1 = L.s[k + 1];
                // Mauerfläche zum Wasser
                double[] fa = {x0, WALL_FOOT, z0}, fb = {x1, WALL_FOOT, z1}, ta = {x0, h0, z0}, tb = {x1, h1, z1};
                facing(m, fa, fb, tb, ta, -(nx0 + nx1) / 2, -(nz0 + nz1) / 2, Material.QUAY, s0, WALL_FOOT, 1, 1);
                // Deckstein: 12 cm Überstand, 14 cm hoch, 60 cm tief
                double c = 0.14, o = 0.12, dep = 0.62;
                double[] ca = {x0 - nx0 * o, h0, z0 - nz0 * o}, cb = {x1 - nx1 * o, h1, z1 - nz1 * o};
                double[] cat = {ca[0], h0 + c, ca[2]}, cbt = {cb[0], h1 + c, cb[2]};
                double[] ia = {x0 + nx0 * dep, h0 + c, z0 + nz0 * dep}, ib = {x1 + nx1 * dep, h1 + c, z1 + nz1 * dep};
                double[] ia0 = {ia[0], h0, ia[2]}, ib0 = {ib[0], h1, ib[2]};
                facing(m, ca, cb, cbt, cat, -nx0, -nz0, Material.GRANITE, s0, h0, 1, 1);
                facing(m, cat, cbt, ib, ia, 0, 0, Material.GRANITE, s0, 0, 1, 1, true);
                facing(m, ca, cb, new double[]{x1, h1, z1}, new double[]{x0, h0, z0}, 0, 0, Material.GRANITE, s0, 0, 1, 1, false);
                facing(m, ib0, ia0, ia, ib, nx0, nz0, Material.GRANITE, s0, h0, 1, 1);
                // Kaipflaster bis 5 m hinter der Mauer
                double far = 5.2;
                double[] pa = {x0 + nx0 * dep, h0 + 0.02, z0 + nz0 * dep}, pb = {x1 + nx1 * dep, h1 + 0.02, z1 + nz1 * dep};
                double[] qa = {x0 + nx0 * far, Math.max(h0 + 0.02, Site.height(x0 + nx0 * far, z0 + nz0 * far) + 0.03), z0 + nz0 * far};
                double[] qb = {x1 + nx1 * far, Math.max(h1 + 0.02, Site.height(x1 + nx1 * far, z1 + nz1 * far) + 0.03), z1 + nz1 * far};
                facing(m, pa, pb, qb, qa, 0, 0, Material.COBBLE, 0, 0, 0.9, 1, true);

                // Ausstattung
                double mx = (x0 + x1) / 2, mz = (z0 + z1) / 2, mnx = (nx0 + nx1) / 2, mnz = (nz0 + nz1) / 2;
                double mh = (h0 + h1) / 2;
                bollwerk = (Site.onLomse(mx, mz) && mx < 950) || mx < -270;
                if (s1 >= nextBollard) {
                    nextBollard += 24;
                    double bx = mx + mnx * 0.95, bz = mz + mnz * 0.95;
                    m.cylinder(bx, mh + 0.02, bz, 0.17, 0.55, 12, Material.IRON, 0.7);
                    m.cylinder(bx, mh + 0.57, bz, 0.23, 0.07, 12, Material.IRON, 1);
                }
                if (s1 >= nextLamp) {
                    nextLamp += 36;
                    double lx = mx + mnx * 2.6, lz = mz + mnz * 2.6;
                    lantern(m, lx, mh + 0.02, lz);
                    lanterns.add(new double[]{lx, mh + 4.2, lz, mnx, mnz});
                }
                if (bollwerk && s1 >= nextPile) {
                    nextPile += 7.5;
                    double dx = mx - mnx * 0.45, dz = mz - mnz * 0.45;
                    m.cylinder(dx, WALL_FOOT, dz, 0.17, mh + 0.45 - WALL_FOOT, 10, Material.WOOD, 1);
                }
            }
        }
        return m;
    }

    /** Gaslaterne: Sockel, Mast, Laternenkopf mit Glas (leuchtet nachts), kleine Haube. */
    static void lantern(MeshBuilder m, double x, double y, double z) {
        m.cylinder(x, y, z, 0.16, 0.5, 10, Material.IRON, 0.7);
        m.cylinder(x, y + 0.5, z, 0.065, 3.3, 8, Material.IRON, 1);
        m.box(x - 0.22, y + 3.8, z - 0.22, x + 0.22, y + 3.85, z + 0.22, Material.IRON, 1);
        m.box(x - 0.19, y + 3.85, z - 0.19, x + 0.19, y + 4.35, z + 0.19, Material.LAMP, 1);
        m.pyramid(x - 0.26, y + 4.35, z - 0.26, x + 0.26, z + 0.26, y + 4.6, Material.IRON);
    }

    /** Höhe der Kaikante: Landhöhe ein Stück hinter der Mauer. */
    static double quayTop(double x, double z, double nx, double nz) {
        return Site.land(x + nx * 6, z + nz * 6) + 0.05;
    }

    static void facing(MeshBuilder m, double[] p0, double[] p1, double[] p2, double[] p3, double wantX, double wantZ,
                       int mat, double u0, double v0, double aoB, double aoT) {
        facingImpl(m, p0, p1, p2, p3, wantX, 0, wantZ, mat, u0, v0, aoB, aoT);
    }

    /** Wie {@link #facing}, aber für waagerechte Flächen: up = true nach oben, false nach unten. */
    static void facing(MeshBuilder m, double[] p0, double[] p1, double[] p2, double[] p3, double wx, double wz,
                       int mat, double u0, double v0, double aoB, double aoT, boolean up) {
        facingImpl(m, p0, p1, p2, p3, 0, up ? 1 : -1, 0, mat, u0, v0, aoB, aoT);
    }

    private static void facingImpl(MeshBuilder m, double[] p0, double[] p1, double[] p2, double[] p3, double wx, double wy, double wz,
                                   int mat, double u0, double v0, double aoB, double aoT) {
        double ex = p1[0] - p0[0], ey = p1[1] - p0[1], ez = p1[2] - p0[2];
        double fx = p3[0] - p0[0], fy = p3[1] - p0[1], fz = p3[2] - p0[2];
        double nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
        if (nx * wx + ny * wy + nz * wz >= 0) m.quad(p0, p1, p2, p3, mat, u0, v0, aoB, aoT);
        else m.quad(p1, p0, p3, p2, mat, u0, v0, aoB, aoT);
    }

    // ------------------------------------------------------------------ Hilfen

    private static long key(int i, int j, int dir) { return ((long) i << 32) | ((long) j << 1) | dir; }

    private static double[] lerp(double x0, double z0, double x1, double z1, double a, double b) {
        double t = a / (a - b);
        if (!Double.isFinite(t)) t = 0.5;
        t = Math.max(0, Math.min(1, t));
        return new double[]{x0 + (x1 - x0) * t, z0 + (z1 - z0) * t};
    }

    /** Kantenpaare je Fall (marching squares), Sattel nach dem Mittelwert aufgelöst. */
    private static int[][] cases(int code, boolean centerWet) {
        switch (code) {
            case 1: case 14: return new int[][]{{3, 0}};
            case 2: case 13: return new int[][]{{0, 1}};
            case 3: case 12: return new int[][]{{3, 1}};
            case 4: case 11: return new int[][]{{1, 2}};
            case 6: case 9: return new int[][]{{0, 2}};
            case 7: case 8: return new int[][]{{3, 2}};
            case 5: return centerWet ? new int[][]{{3, 2}, {0, 1}} : new int[][]{{3, 0}, {1, 2}};
            case 10: return centerWet ? new int[][]{{3, 0}, {1, 2}} : new int[][]{{3, 2}, {0, 1}};
            default: return new int[0][];
        }
    }
}
