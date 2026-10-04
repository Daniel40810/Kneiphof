package com.dan.kneiphof.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Innere des Doms: dreischiffige Halle mit Rundpfeilern, drei Spitztonnen als Gewölbe mit Gurtbögen,
 * Fenster als Tageslichtflächen, Chor mit Altar, Gestühl, Kanzel, Orgelempore und Epitaphien. Das Südportal
 * ist eine echte Öffnung in der Wand, durch die man hineingeht.
 * <p>Alle Maße im Bezugssystem des Doms (u nach Osten ab der Turmwestwand, w nach Süden).
 * Innenflächen tragen Materialnummern ab {@link #IN}, die der Shader ohne Himmelslicht und mit
 * Raumlicht beleuchtet.</p>
 */
public final class DomInterior {
    /** Versatz der Innenmaterialien: IN + Material.BRICK usw. */
    public static final int IN = 40;
    /** Fensterglas von innen, leuchtet bei Tag. */
    public static final int IN_GLASS = 38;

    public static final double G = Dom.GROUND;
    /** Fußboden, Innenwände (halbe Breite), Westwand, Ostwand, Kämpferhöhe der Gewölbe. */
    public static final double FLOOR = G + 0.93, WI = 10.6, U0 = 14.05, U1 = Dom.L - 0.4, SPRING = G + 14.0;
    /** Pfeiler an w = ±PILLAR_W, Halbmesser. */
    public static final double PILLAR_W = 3.55, PILLAR_R = 0.62;
    /** Mitte des Südportals. */
    public static final double DOOR_U = 16 + 6.5 * 2 + 3.25, DOOR_W = 3.2, DOOR_H = 3.0;
    static final double OUTER_W = 11.0;

    private DomInterior() { }

    /** Pfeilermitten (u, w) für die Kollision. */
    public static double[][] pillars() {
        List<double[]> l = new ArrayList<>();
        for (int k = 0; k <= 10; k++)
            for (int s = -1; s <= 1; s += 2) l.add(new double[]{16 + 6.5 * k, s * PILLAR_W});
        return l.toArray(new double[0][]);
    }

    // ------------------------------------------------------------------------------------ Grundformen

    private static double[] up(double[] a, double[] b, double[] c) {
        double ex = b[0] - a[0], ey = b[1] - a[1], ez = b[2] - a[2], fx = c[0] - a[0], fy = c[1] - a[1], fz = c[2] - a[2];
        return new double[]{ey * fz - ez * fy, ez * fx - ex * fz, ex * fy - ey * fx};
    }

    /** Konvexes Vieleck mit gewünschter Normale n (Welt); Umlauf wird bei Bedarf gedreht. */
    private static void poly(MeshBuilder m, List<double[]> pts, double[] n, int mat, boolean aoByHeight) {
        double nl = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        double[] nn = {n[0] / nl, n[1] / nl, n[2] / nl};
        double[] g = up(pts.get(0), pts.get(1), pts.get(2));
        if (g[0] * nn[0] + g[1] * nn[1] + g[2] * nn[2] < 0) java.util.Collections.reverse(pts);
        // Tangente: up × n bei Wänden, sonst x
        double[] t;
        boolean wall = Math.abs(nn[1]) < 0.5;
        if (wall) {
            t = new double[]{nn[2], 0, -nn[0]};
            double tl = Math.hypot(t[0], t[2]);
            t[0] /= tl; t[2] /= tl;
        } else {
            t = new double[]{1, 0, 0};
        }
        int[] id = new int[pts.size()];
        for (int i = 0; i < id.length; i++) {
            double[] p = pts.get(i);
            double u = wall ? p[0] * t[0] + p[2] * t[2] : p[0];
            double v = wall ? p[1] : p[2];
            double ao = aoByHeight ? Math.min(1.0, 0.55 + 0.45 * (p[1] - FLOOR) / 5.0) : 1.0;
            id[i] = m.vertex(p[0], p[1], p[2], nn[0], nn[1], nn[2], t[0], t[1], t[2], u, v, mat, ao);
        }
        for (int i = 1; i + 1 < id.length; i++) m.tri(id[0], id[i], id[i + 1]);
    }

    private static List<double[]> list(double[]... p) {
        List<double[]> l = new ArrayList<>();
        for (double[] a : p) l.add(a);
        return l;
    }

    /** Senkrechte Fläche in der Ebene w = const; Punkte als (u, y). n: Richtung der Normalen in w (+1 oder −1). */
    private static void wallW(Obj o, double w, int sign, int mat, boolean ao, double[]... uy) {
        List<double[]> l = new ArrayList<>();
        for (double[] q : uy) l.add(o.p(q[0], q[1], w));
        double[] n = {o.dir(0, sign)[0], 0, o.dir(0, sign)[1]};
        poly(o.m, l, n, mat, ao);
    }

    /** Senkrechte Fläche in der Ebene u = const; Punkte als (w, y). */
    private static void wallU(Obj o, double u, int sign, int mat, boolean ao, double[]... wy) {
        List<double[]> l = new ArrayList<>();
        for (double[] q : wy) l.add(o.p(u, q[1], q[0]));
        double[] n = {o.dir(sign, 0)[0], 0, o.dir(sign, 0)[1]};
        poly(o.m, l, n, mat, ao);
    }

    // ------------------------------------------------------------------------------------ Bogenform
    /** Halbe Bogenlinie von der Kämpferhöhe y0 (Breite wd, Pfosten hs hoch) bis zur Spitze: (x, y), x ab Mitte, rechts. */
    private static double[][] halfArch(double wd, double y0, double hs, int steps) {
        double[][] r = new double[steps + 2][];
        r[0] = new double[]{wd / 2, y0};
        r[1] = new double[]{wd / 2, y0 + hs};
        for (int i = 1; i <= steps; i++) {
            double a = Math.toRadians(60.0 * i / steps);
            r[i + 1] = new double[]{-wd / 2 + wd * Math.cos(a), y0 + hs + wd * Math.sin(a)};
        }
        return r;
    }

    /** Wand in der Ebene w mit spitzbogiger Öffnung bei uc; Fläche u0..u1, y0..y1, Öffnung unten bei y0. */
    private static void wallWithArch(Obj o, double w, int sign, int mat, boolean ao, double u0, double u1, double y0, double y1,
                                     double uc, double wd, double hs) {
        double side = 3.0;
        if (uc - side > u0) wallW(o, w, sign, mat, ao, new double[]{u0, y0}, new double[]{uc - side, y0}, new double[]{uc - side, y1}, new double[]{u0, y1});
        if (uc + side < u1) wallW(o, w, sign, mat, ao, new double[]{uc + side, y0}, new double[]{u1, y0}, new double[]{u1, y1}, new double[]{uc + side, y1});
        double[][] h = halfArch(wd, y0, hs, 6);
        for (int j = 0; j + 1 < h.length; j++) {
            double[] a = h[j], b = h[j + 1];
            wallW(o, w, sign, mat, ao, new double[]{uc + a[0], a[1]}, new double[]{uc + side, a[1]}, new double[]{uc + side, b[1]}, new double[]{uc + b[0], b[1]});
            wallW(o, w, sign, mat, ao, new double[]{uc - a[0], a[1]}, new double[]{uc - side, a[1]}, new double[]{uc - side, b[1]}, new double[]{uc - b[0], b[1]});
        }
        double yA = h[h.length - 1][1];
        wallW(o, w, sign, mat, ao, new double[]{uc - side, yA}, new double[]{uc + side, yA}, new double[]{uc + side, y1}, new double[]{uc - side, y1});
    }

    /** Laibung der Türöffnung zwischen Innen- und Außenwand. */
    private static void reveal(Obj o, double wIn, double wOut, double y0, double uc, double wd, double hs) {
        double[][] h = halfArch(wd, y0, hs, 6);
        for (int j = 0; j + 1 < h.length; j++) {
            for (int s = -1; s <= 1; s += 2) {
                double[] a = h[j], b = h[j + 1];
                double ua = uc + s * a[0], ub = uc + s * b[0];
                List<double[]> l = list(o.p(ua, a[1], wIn), o.p(ua, a[1], wOut), o.p(ub, b[1], wOut), o.p(ub, b[1], wIn));
                // Normale zur Öffnungsmitte hin
                double du = -(ua + ub) / 2 + uc + 0, dy = (y0 + hs / 2) - (a[1] + b[1]) / 2;
                double[] d = o.dir(du, 0);
                double[] n = {d[0], dy, d[1]};
                // Steinschicht (Sturz) nach unten, Seiten zur Mitte
                if (Math.abs(n[0]) + Math.abs(n[2]) + Math.abs(n[1]) < 1e-6) continue;
                poly(o.m, l, n, Material.GRANITE, false);
            }
        }
        // Schwelle
        poly(o.m, list(o.p(uc - wd / 2, y0 + 0.02, wIn), o.p(uc + wd / 2, y0 + 0.02, wIn), o.p(uc + wd / 2, y0 + 0.02, wOut), o.p(uc - wd / 2, y0 + 0.02, wOut)),
                new double[]{0, 1, 0}, Material.GRANITE, false);
    }

    /** Steinrahmen um das Portal als Ring zwischen äußerem und innerem Bogen (nur Außenseite). */
    private static void archRing(Obj o, double w, double y0, double uc, double wdIn, double hsIn, double wdOut, double hsOut, double off) {
        double[][] a = halfArch(wdIn, y0, hsIn, 6), b = halfArch(wdOut, y0, hsOut, 6);
        double[] n = {o.dir(0, 1)[0], 0, o.dir(0, 1)[1]};
        for (int j = 0; j + 1 < a.length; j++)
            for (int s = -1; s <= 1; s += 2) {
                List<double[]> l = list(o.p(uc + s * a[j][0], a[j][1], w + off), o.p(uc + s * b[j][0], b[j][1], w + off),
                        o.p(uc + s * b[j + 1][0], b[j + 1][1], w + off), o.p(uc + s * a[j + 1][0], a[j + 1][1], w + off));
                poly(o.m, l, n, Material.GRANITE, false);
            }
    }

    // ------------------------------------------------------------------------------------ Schale
    /** Außenwände des Schiffs (Süden mit Portalöffnung) und die Innenhaut samt Böden, Gewölben und Ausstattung. */
    static void shell(Obj o, double g, double bu0, double bu1, double eave) {
        MeshBuilder m = o.m;
        double bw = OUTER_W;
        // Außenhaut: Norden, Osten, Westen wie ein Quader ohne Süden; Süden mit Öffnung
        double y0 = g + 0.9;
        double[] c2 = o.p(bu1, y0, -bw), d = o.p(bu0, y0, -bw), h = o.p(bu0, eave, -bw), gg = o.p(bu1, eave, -bw);
        double[] b = o.p(bu1, y0, bw), f = o.p(bu1, eave, bw), a = o.p(bu0, y0, bw), e = o.p(bu0, eave, bw);
        double w = bu1 - bu0, dp = 2 * bw;
        m.quad(b, c2, gg, f, Material.BRICK, w, y0, 0.7, 1);
        m.quad(c2, d, h, gg, Material.BRICK, w + dp, y0, 0.7, 1);
        m.quad(d, a, e, h, Material.BRICK, 2 * w + dp, y0, 0.7, 1);
        wallWithArch(o, bw, 1, Material.BRICK, false, bu0, bu1, y0, eave, DOOR_U, DOOR_W, DOOR_H);
        reveal(o, WI, bw, y0, DOOR_U, DOOR_W, DOOR_H);
        archRing(o, bw, y0, DOOR_U, DOOR_W, DOOR_H, 4.8, 3.6, 0.03);

        // Innenhaut
        int brick = IN + Material.BRICK, granite = IN + Material.GRANITE, plaster = IN + Material.PLASTER0 + 4;
        wallW(o, -WI, 1, brick, true, new double[]{U0, FLOOR}, new double[]{U1, FLOOR}, new double[]{U1, SPRING + 0.5}, new double[]{U0, SPRING + 0.5});
        wallWithArch(o, WI, -1, brick, true, U0, U1, FLOOR, SPRING + 0.5, DOOR_U, DOOR_W, DOOR_H);
        // Stirnwände: Rechteck bis zur Kämpferhöhe, darüber je Schiff ein Spitzbogenfeld
        double[] sc = {-(WI + PILLAR_W) / 2, 0, (WI + PILLAR_W) / 2};
        double[] wdv = {WI - PILLAR_W, 2 * PILLAR_W, WI - PILLAR_W};
        for (int e2 = 0; e2 < 2; e2++) {
            double u = e2 == 0 ? U0 : U1;
            int sg = e2 == 0 ? 1 : -1;
            wallU(o, u, sg, brick, true, new double[]{-WI, FLOOR}, new double[]{WI, FLOOR}, new double[]{WI, SPRING}, new double[]{-WI, SPRING});
            for (int i = 0; i < 3; i++) {
                double[][] pr = halfArch(wdv[i], SPRING, 0, 8);
                List<double[]> l = new ArrayList<>();
                for (int j = pr.length - 1; j >= 1; j--) l.add(o.p(u, pr[j][1], sc[i] - pr[j][0]));
                l.add(o.p(u, SPRING, sc[i] - pr[0][0]));
                l.add(o.p(u, SPRING, sc[i] + pr[0][0]));
                for (int j = 1; j < pr.length; j++) l.add(o.p(u, pr[j][1], sc[i] + pr[j][0]));
                // doppelte Punkte (Spitze) vermeiden
                List<double[]> clean = new ArrayList<>();
                for (double[] p : l) if (clean.isEmpty() || Math.abs(clean.get(clean.size() - 1)[0] - p[0]) + Math.abs(clean.get(clean.size() - 1)[1] - p[1]) + Math.abs(clean.get(clean.size() - 1)[2] - p[2]) > 1e-6) clean.add(p);
                poly(m, clean, new double[]{o.dir(sg, 0)[0], 0, o.dir(sg, 0)[1]}, brick, true);
            }
        }
        // Boden
        poly(m, list(o.p(U0, FLOOR, -WI), o.p(U1, FLOOR, -WI), o.p(U1, FLOOR, WI), o.p(U0, FLOOR, WI)), new double[]{0, 1, 0}, granite, false);
        // Gewölbe: drei Spitztonnen, dazu Gurtbögen über den Pfeilern
        for (int i = 0; i < 3; i++) vault(o, sc[i], wdv[i], plaster);
        for (int k = 0; k <= 10; k++) rib(o, 16 + 6.5 * k, sc, wdv, granite);
        rib(o, U0 + 0.3, sc, wdv, granite);
        rib(o, U1 - 0.3, sc, wdv, granite);

        // Pfeiler und Wandvorlagen
        for (int k = 0; k <= 10; k++) {
            double uk = 16 + 6.5 * k;
            for (int s = -1; s <= 1; s += 2) {
                double px = o.wx(uk, s * PILLAR_W), pz = o.wz(uk, s * PILLAR_W);
                m.cylinder(px, FLOOR + 0.8, pz, PILLAR_R, SPRING - 0.6 - (FLOOR + 0.8), 18, brick, 0.8);
                Obj pc = new Obj(m, px, pz, o.angle);
                pc.box(-0.9, -0.9, 0.9, 0.9, FLOOR, FLOOR + 0.8, granite, 0.8);
                pc.box(-0.78, -0.78, 0.78, 0.78, SPRING - 0.6, SPRING, granite, 1);
                // Wandvorlage mit Gurtbogenauflager
                o.box(uk - 0.45, s > 0 ? WI - 0.4 : -WI, uk + 0.45, s > 0 ? WI : -WI + 0.4, FLOOR, SPRING, brick, 0.8);
            }
        }
        windows(o);
        furniture(o);
    }

    private static void vault(Obj o, double sc, double wd, int mat) {
        int n = 10;
        double[][] pts = new double[2 * n + 1][];
        double[][] nor = new double[2 * n + 1][];
        for (int i = 0; i <= n; i++) {
            double a = Math.toRadians(60.0 * i / n);
            pts[i] = new double[]{sc + wd / 2 - wd * Math.cos(a), SPRING + wd * Math.sin(a)};
            nor[i] = new double[]{Math.cos(a), -Math.sin(a)};
            if (i < n) {
                pts[2 * n - i] = new double[]{sc - wd / 2 + wd * Math.cos(a), SPRING + wd * Math.sin(a)};
                nor[2 * n - i] = new double[]{-Math.cos(a), -Math.sin(a)};
            }
        }
        MeshBuilder m = o.m;
        double[] t = o.dir(1, 0);
        int[][] id = new int[2][pts.length];
        double[] us = {U0, U1};
        for (int e = 0; e < 2; e++)
            for (int i = 0; i < pts.length; i++) {
                double[] p = o.p(us[e], pts[i][1], pts[i][0]);
                double[] nv = o.dir(0, nor[i][0]);
                // Normale (Welt): quer = w-Anteil
                id[e][i] = m.vertex(p[0], p[1], p[2], nv[0], nor[i][1], nv[1], t[0], 0, t[1], us[e], pts[i][0] + pts[i][1], mat, 0.85);
            }
        // Umlauf so wählen, dass die Fläche nach unten zeigt
        double[] g = up(o.p(U0, pts[0][1], pts[0][0]), o.p(U1, pts[0][1], pts[0][0]), o.p(U0, pts[1][1], pts[1][0]));
        boolean flip = g[1] > 0;
        for (int i = 0; i + 1 < pts.length; i++) {
            if (!flip) { m.tri(id[0][i], id[1][i], id[1][i + 1]); m.tri(id[0][i], id[1][i + 1], id[0][i + 1]); }
            else { m.tri(id[0][i], id[1][i + 1], id[1][i]); m.tri(id[0][i], id[0][i + 1], id[1][i + 1]); }
        }
    }

    /** Gurtbogen: schmaler Steg unter dem Gewölbe. */
    private static void rib(Obj o, double u, double[] sc, double[] wd, int mat) {
        MeshBuilder m = o.m;
        double hw = 0.28;
        for (int s = 0; s < 3; s++) {
            int n = 10;
            double[][] pts = new double[2 * n + 1][];
            double[][] nor = new double[2 * n + 1][];
            for (int i = 0; i <= n; i++) {
                double a = Math.toRadians(60.0 * i / n);
                double r = wd[s] - 0.14;
                pts[i] = new double[]{sc[s] + wd[s] / 2 - r * Math.cos(a) - 0.0, SPRING - 0.0 + r * Math.sin(a) - 0.0};
                nor[i] = new double[]{Math.cos(a), -Math.sin(a)};
                if (i < n) {
                    pts[2 * n - i] = new double[]{sc[s] - wd[s] / 2 + r * Math.cos(a), SPRING + r * Math.sin(a)};
                    nor[2 * n - i] = new double[]{-Math.cos(a), -Math.sin(a)};
                }
            }
            // die Rippe sitzt um 0,12 unter der Gewölbefläche, Auflager mit gleichem Spitzenabstand
            for (int i = 0; i + 1 < pts.length; i++) {
                double[] p0 = o.p(u - hw, pts[i][1] - 0.0, pts[i][0]), p1 = o.p(u + hw, pts[i][1], pts[i][0]);
                double[] p2 = o.p(u + hw, pts[i + 1][1], pts[i + 1][0]), p3 = o.p(u - hw, pts[i + 1][1], pts[i + 1][0]);
                double[] nv = o.dir(0, (nor[i][0] + nor[i + 1][0]) / 2);
                poly(m, list(p0, p1, p2, p3), new double[]{nv[0], (nor[i][1] + nor[i + 1][1]) / 2, nv[1]}, mat, false);
            }
        }
    }

    // ------------------------------------------------------------------------------------ Fenster
    private static void windows(Obj o) {
        // Südseite: Fenster in den Jochen, Nordseite ebenso; Ostwand: drei Chorfenster
        for (int k = 0; k <= 10; k++) {
            double uc = 16 + 6.5 * k + 3.25;
            if (uc > Dom.L - 2.5 - 0.4) continue;
            for (int side = -1; side <= 1; side += 2) {
                if (side > 0 && k == 2) continue;
                double ww = side * (WI - 0.0);
                int face = side > 0 ? 2 : 0;
                o.fan(uc, ww, face, Obj.pointedArch(0, G + 4.75, 3.5, 7.25, 6), 0.03, IN + Material.GRANITE, 0.9);
                o.fan(uc, ww, face, Obj.pointedArch(0, G + 5.0, 2.8, 7.0, 6), 0.06, IN_GLASS, 1);
            }
        }
        for (int i = -1; i <= 1; i++) {
            o.fan(U1, i * 5.4, 3, Obj.pointedArch(0, G + 3.4, 3.1, 6.1, 6), 0.03, IN + Material.GRANITE, 0.9);
            o.fan(U1, i * 5.4, 3, Obj.pointedArch(0, G + 3.6, 2.6, 5.9, 6), 0.06, IN_GLASS, 1);
        }
        // Westfenster über der Empore
        o.fan(U0, 0, 1, Obj.pointedArch(0, G + 9.0, 3.0, 2.2, 6), 0.05, IN_GLASS, 1);
        // Epitaphien an den Wandvorlagen
        for (int k = 1; k <= 10; k++) {
            double uk = 16 + 6.5 * k;
            for (int side = -1; side <= 1; side += 2) {
                if (side > 0 && k <= 3 && k >= 2) continue;
                double ww = side * (WI - 0.4);
                int face = side > 0 ? 2 : 0;
                double y0 = FLOOR + 2.6 + ((k * 7 + (side > 0 ? 3 : 0)) % 3) * 0.5;
                o.rect(uk, ww, face, -0.34, 0.34, y0, y0 + 1.8, 0.03, IN + Material.GRANITE, 1);
                o.rect(uk, ww, face, -0.26, 0.26, y0 + 0.12, y0 + 1.68, 0.06, IN + Material.MARBLE, 1);
            }
        }
    }

    // ------------------------------------------------------------------------------------ Ausstattung
    private static void furniture(Obj o) {
        MeshBuilder m = o.m;
        int wood = IN + Material.WOOD, granite = IN + Material.GRANITE, marble = IN + Material.MARBLE, iron = IN + Material.IRON;
        // Gestühl im Mittelschiff, zwei Blöcke mit Mittelgang
        for (int r = 0; r < 20; r++) {
            double u = 24 + 2.3 * r;
            for (int s = -1; s <= 1; s += 2) {
                double w0 = s > 0 ? 0.9 : -2.8, w1 = s > 0 ? 2.8 : -0.9;
                o.box(u, w0, u + 0.5, w1, FLOOR, FLOOR + 0.45, wood, 0.7);
                o.box(u + 0.44, w0, u + 0.52, w1, FLOOR + 0.45, FLOOR + 1.05, wood, 0.9);
            }
        }
        // Chor: Stufen und Altar
        o.box(76.0, -6.0, U1 - 0.2, 6.0, FLOOR, FLOOR + 0.4, granite, 0.8);
        o.box(80.0, -4.5, U1 - 0.2, 4.5, FLOOR + 0.4, FLOOR + 0.8, granite, 0.8);
        o.box(84.2, -1.7, 85.6, 1.7, FLOOR + 0.8, FLOOR + 1.8, marble, 0.8);
        o.box(84.0, -1.9, 85.8, 1.9, FLOOR + 1.8, FLOOR + 1.95, marble, 1);
        // Kanzel am nördlichen Pfeiler
        double kx = o.wx(48.5, -1.7), kz = o.wz(48.5, -1.7);
        m.cylinder(kx, FLOOR, kz, 0.28, 1.6, 12, wood, 0.8);
        m.cylinder(kx, FLOOR + 1.6, kz, 0.95, 1.0, 12, wood, 1);
        m.cylinder(kx, FLOOR + 2.6, kz, 1.1, 0.12, 12, granite, 1);
        // Orgelempore im Westen
        o.box(U0, -8.6, U0 + 5.2, 8.6, FLOOR + 7.6, FLOOR + 8.1, wood, 1);
        o.box(U0 + 5.0, -8.6, U0 + 5.15, 8.6, FLOOR + 8.1, FLOOR + 9.1, wood, 1);
        for (int s = -1; s <= 1; s += 2) for (int j = 0; j < 2; j++) {
            double px = o.wx(U0 + 4.3, s * (3.5 + 4.2 * j)), pz = o.wz(U0 + 4.3, s * (3.5 + 4.2 * j));
            m.cylinder(px, FLOOR, pz, 0.32, 7.6, 12, IN + Material.BRICK, 0.8);
        }
        o.box(U0 + 0.1, -5.6, U0 + 2.3, 5.6, FLOOR + 8.1, FLOOR + 10.5, wood, 1);
        for (int i = -10; i <= 10; i++) {
            double h = 4.0 + 3.3 * Math.abs(Math.cos(i * 0.62)) + (i % 3 == 0 ? 0.8 : 0.0);
            double px = o.wx(U0 + 1.3, i * 0.52), pz = o.wz(U0 + 1.3, i * 0.52);
            m.cylinder(px, FLOOR + 10.5, pz, 0.14, h, 8, iron, 1);
        }
        // Leuchter im Mittelschiff
        for (int j = 0; j < 7; j++) {
            double u = 22 + 10 * j;
            double lx = o.wx(u, 0), lz = o.wz(u, 0);
            double top = SPRING + 3.55 * 1.7;
            m.cylinder(lx, SPRING + 2.0, lz, 0.025, top - 2.0 - SPRING, 5, iron, 1);
            m.cylinder(lx, SPRING + 1.6, lz, 0.75, 0.07, 14, iron, 1);
            for (int c = 0; c < 8; c++) {
                double a = c * Math.PI / 4;
                m.cylinder(lx + Math.cos(a) * 0.7, SPRING + 1.67, lz + Math.sin(a) * 0.7, 0.04, 0.22, 6, Material.LAMP, 1);
            }
        }
    }

    // ------------------------------------------------------------------------------------ Rundgang
    private static final double FC = Math.cos(Dom.ANGLE), FS = Math.sin(Dom.ANGLE);
    private static final double FX = Dom.CX - FC * Dom.L / 2, FZ = Dom.CZ - FS * Dom.L / 2;

    /** Längs- und Querlage (u, w) im Bezugssystem des Doms. */
    public static double[] toFrame(double x, double z) {
        double dx = x - FX, dz = z - FZ;
        return new double[]{dx * FC + dz * FS, -dx * FS + dz * FC};
    }

    /** Weltlage zu (u, w). */
    public static double[] toWorld(double u, double w) {
        return new double[]{FX + u * FC - w * FS, FZ + u * FS + w * FC};
    }

    /** Darf eine Person (Körperkreis ~0,35 m) hier stehen? Im Schiff, im Portalgang davor. */
    public static boolean walkable(double x, double z) {
        double[] f = toFrame(x, z);
        double u = f[0], w = f[1];
        boolean door = Math.abs(u - DOOR_U) < 1.3 && w > WI - 1.0 && w < OUTER_W + 5.0;
        if (door) return true;
        if (u < U0 + 0.45 || u > U1 - 0.45 || Math.abs(w) > WI - 0.45) return false;
        for (double[] p : pillars()) if (Math.hypot(u - p[0], w - p[1]) < PILLAR_R + 0.4) return false;
        // Gestühl (ganze Blöcke), Wandvorlagen, Kanzel, Altar, Empore
        if (u > 23.8 && u < 68.6 && Math.abs(w) > 0.85 && Math.abs(w) < 2.85) return false;
        for (int k = 0; k <= 10; k++) if (Math.abs(u - (16 + 6.5 * k)) < 0.45 + 0.35 && Math.abs(w) > WI - 0.4 - 0.35) return false;
        if (Math.hypot(u - 48.5, w + 1.7) < 1.35) return false;
        if (u > 83.5 && u < 86.4 && Math.abs(w) < 2.3) return false;
        return true;
    }

    /** Höhe des Fußbodens an dieser Stelle (Chorstufen; im Portalgang der Sockel, draußen das Gelände). */
    public static double floorAt(double x, double z) {
        double[] f = toFrame(x, z);
        double u = f[0], w = f[1];
        if (w > OUTER_W + 0.3) return Math.max(Site.height(x, z), G);
        if (w > OUTER_W - 0.2 && Math.abs(u - DOOR_U) < 1.5) return FLOOR - 0.1;
        double y = FLOOR;
        if (u > 76.0 && Math.abs(w) < 6.0) y += 0.4;
        if (u > 80.0 && Math.abs(w) < 4.5) y += 0.4;
        return y;
    }

    /** Ob der Punkt im Portalgang jenseits der Außenwand liegt (Rundgang endet). */
    public static boolean outside(double x, double z) {
        return toFrame(x, z)[1] > OUTER_W + 2.8;
    }
}
