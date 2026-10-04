package com.dan.kneiphof.world;

/**
 * Baut gedrehte Bauteile auf einem {@link MeshBuilder}. Ein Bezugssystem hat einen Ursprung (cx, cz) und
 * einen Winkel; u läuft entlang der Gebäudeachse, w quer dazu (w = +z des Bezugssystems, die Seite
 * „Süden“ des Quaders), y ist die Höhe in Metern.
 */
public final class Obj {
    public final MeshBuilder m;
    public final double cx, cz, c, s, angle;

    public Obj(MeshBuilder m, double cx, double cz, double angle) {
        this.m = m;
        this.cx = cx;
        this.cz = cz;
        this.angle = angle;
        this.c = Math.cos(angle);
        this.s = Math.sin(angle);
    }

    public double[] p(double u, double y, double w) {
        return new double[]{cx + u * c - w * s, y, cz + u * s + w * c};
    }

    /** Weltlage eines Punktes (u, w). */
    public double wx(double u, double w) { return cx + u * c - w * s; }
    public double wz(double u, double w) { return cz + u * s + w * c; }

    /** Ausrichtung der Fläche mit der Normalen +w, +u, −w, −u als Weltvektor (x, z). */
    public double[] dir(double du, double dw) { return new double[]{du * c - dw * s, du * s + dw * c}; }

    public Obj turned(double extra) { return new Obj(m, cx, cz, angle + extra); }

    public Obj at(double u, double w, double extraAngle) { return new Obj(m, wx(u, w), wz(u, w), angle + extraAngle); }

    // ------------------------------------------------------------------------------------ Quader
    public void box(double u0, double w0, double u1, double w1, double y0, double y1, int mat, double aoFoot) {
        double[] a = p(u0, y0, w1), b = p(u1, y0, w1), c2 = p(u1, y0, w0), d = p(u0, y0, w0);
        double[] e = p(u0, y1, w1), f = p(u1, y1, w1), g = p(u1, y1, w0), h = p(u0, y1, w0);
        double w = u1 - u0, dp = w1 - w0;
        m.quad(a, b, f, e, mat, 0, y0, aoFoot, 1);
        m.quad(b, c2, g, f, mat, w, y0, aoFoot, 1);
        m.quad(c2, d, h, g, mat, w + dp, y0, aoFoot, 1);
        m.quad(d, a, e, h, mat, 2 * w + dp, y0, aoFoot, 1);
        m.quad(e, f, g, h, mat, u0, -w1, 1, 1);
    }

    /** Quader ohne Boden und ohne Deckel (für Wände, die oben von einem Dach geschlossen werden). */
    public void wallsOnly(double u0, double w0, double u1, double w1, double y0, double y1, int mat, double aoFoot) {
        double[] a = p(u0, y0, w1), b = p(u1, y0, w1), c2 = p(u1, y0, w0), d = p(u0, y0, w0);
        double[] e = p(u0, y1, w1), f = p(u1, y1, w1), g = p(u1, y1, w0), h = p(u0, y1, w0);
        double w = u1 - u0, dp = w1 - w0;
        m.quad(a, b, f, e, mat, 0, y0, aoFoot, 1);
        m.quad(b, c2, g, f, mat, w, y0, aoFoot, 1);
        m.quad(c2, d, h, g, mat, w + dp, y0, aoFoot, 1);
        m.quad(d, a, e, h, mat, 2 * w + dp, y0, aoFoot, 1);
    }

    /** Dreieck mit Normale aus der Umlaufrichtung (gegen den Uhrzeigersinn von außen). */
    public void tri(double[] p0, double[] p1, double[] p2, int mat, double ao0, double ao2) {
        double ex = p1[0] - p0[0], ey = p1[1] - p0[1], ez = p1[2] - p0[2];
        double fx = p2[0] - p0[0], fy = p2[1] - p0[1], fz = p2[2] - p0[2];
        double nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-9) return;
        nx /= nl; ny /= nl; nz /= nl;
        double el = Math.sqrt(ex * ex + ey * ey + ez * ez);
        double tx = ex / el, ty = ey / el, tz = ez / el;
        // zweite Achse in der Ebene: senkrecht zur Kante, nach oben (bei waagerechten Flächen: Richtung p2)
        double bx = ny * tz - nz * ty, by = nz * tx - nx * tz, bz = nx * ty - ny * tx;
        double dotUp = by;
        if (Math.abs(dotUp) < 1e-6) { bx = fx; by = fy; bz = fz; }
        else if (dotUp < 0) { bx = -bx; by = -by; bz = -bz; }
        double bl = Math.sqrt(bx * bx + by * by + bz * bz);
        bx /= bl; by /= bl; bz /= bl;
        int a = m.vertex(p0[0], p0[1], p0[2], nx, ny, nz, tx, ty, tz, 0, 0, mat, ao0);
        int b = m.vertex(p1[0], p1[1], p1[2], nx, ny, nz, tx, ty, tz, el, 0, mat, ao0);
        double u2 = fx * tx + fy * ty + fz * tz, v2 = fx * bx + fy * by + fz * bz;
        int c2 = m.vertex(p2[0], p2[1], p2[2], nx, ny, nz, tx, ty, tz, u2, v2, mat, ao2);
        m.tri(a, b, c2);
    }

    // ------------------------------------------------------------------------------------ Dächer
    /**
     * Satteldach mit dem First entlang u über dem Rechteck (u0..u1, w0..w1), Traufe in yE, First in yR.
     * overhang: Dachüberstand an der Traufe, verge: an den Giebelseiten. Giebeldreiecke in gableMat
     * (gableMat 0: keine).
     */
    public void gableRoof(double u0, double w0, double u1, double w1, double yE, double yR, double overhang, double verge,
                          int roofMat, int gableMat) {
        double wm = (w0 + w1) / 2, hd = (w1 - w0) / 2;
        double slope = (yR - yE) / hd;
        double yo = yE - overhang * slope;
        double ua = u0 - verge, ub = u1 + verge;
        double[] a0 = p(ua, yo, w1 + overhang), a1 = p(ub, yo, w1 + overhang), a2 = p(ub, yR, wm), a3 = p(ua, yR, wm);
        m.quad(a0, a1, a2, a3, roofMat, 0, 0, 1, 1);
        double[] b0 = p(ub, yo, w0 - overhang), b1 = p(ua, yo, w0 - overhang), b2 = p(ua, yR, wm), b3 = p(ub, yR, wm);
        m.quad(b0, b1, b2, b3, roofMat, 0, 0, 1, 1);
        if (gableMat != 0) {
            tri(p(u1, yE, w1), p(u1, yE, w0), p(u1, yR, wm), gableMat, 0.9, 1);
            tri(p(u0, yE, w0), p(u0, yE, w1), p(u0, yR, wm), gableMat, 0.9, 1);
        }
        // Firstziegel
        box(ua, wm - 0.12, ub, wm + 0.12, yR - 0.02, yR + 0.14, roofMat == Material.SLATE ? Material.SLATE : Material.ROOF, 1);
    }

    /** Pyramide über dem Rechteck. */
    public void pyramid(double u0, double w0, double u1, double w1, double y0, double top, int mat) {
        double[][] base = {p(u0, y0, w1), p(u1, y0, w1), p(u1, y0, w0), p(u0, y0, w0)};
        double[] apex = p((u0 + u1) / 2, top, (w0 + w1) / 2);
        for (int i = 0; i < 4; i++) tri(base[i], base[(i + 1) % 4], apex, mat, 1, 1);
    }

    /** Walmdach über dem Rechteck: vier Flächen bis zu einem First entlang u der Länge ridge. */
    public void hipRoof(double u0, double w0, double u1, double w1, double yE, double yR, double overhang, int mat) {
        double wm = (w0 + w1) / 2, um = (u0 + u1) / 2, hd = (w1 - w0) / 2;
        double slope = (yR - yE) / hd;
        double yo = yE - overhang * slope;
        double r = Math.max(0, (u1 - u0) / 2 - hd);
        double[] s0 = p(u0 - overhang, yo, w1 + overhang), s1 = p(u1 + overhang, yo, w1 + overhang);
        double[] s2 = p(um + r, yR, wm), s3 = p(um - r, yR, wm);
        m.quad(s0, s1, s2, s3, mat, 0, 0, 1, 1);
        double[] n0 = p(u1 + overhang, yo, w0 - overhang), n1 = p(u0 - overhang, yo, w0 - overhang);
        m.quad(n0, n1, s3, s2, mat, 0, 0, 1, 1);
        tri(p(u1 + overhang, yo, w1 + overhang), p(u1 + overhang, yo, w0 - overhang), s2, mat, 1, 1);
        tri(p(u0 - overhang, yo, w0 - overhang), p(u0 - overhang, yo, w1 + overhang), s3, mat, 1, 1);
    }

    // ------------------------------------------------------------------------------------ Flächen auf Wänden
    /**
     * Ebenes Vieleck (konvex) auf einer senkrechten Wand: Punkte als (s, y) mit s entlang der Wand
     * (von links nach rechts, von außen gesehen) ab der Wandmitte (uw, ww); n: Normale der Wand als
     * Richtung 0..3 (0: +w, 1: +u, 2: −w, 3: −u); off: Abstand vor der Wand.
     */
    public void fan(double uw, double ww, int face, double[][] pts, double off, int mat, double ao) {
        double[] dn = normal(face);
        double[] dr = {-dn[1], dn[0]};      // rechts, von außen gesehen: up × n
        // up × n = (nz, 0, −nx) → in (x, z): (n.z, −n.x)
        dr = new double[]{dn[1], -dn[0]};
        double ox = wx(uw, ww), oz = wz(uw, ww);
        int n = pts.length;
        int[] id = new int[n];
        for (int i = 0; i < n; i++) {
            double x = ox + dr[0] * pts[i][0] + dn[0] * off, z = oz + dr[1] * pts[i][0] + dn[1] * off;
            id[i] = m.vertex(x, pts[i][1], z, dn[0], 0, dn[1], dr[0], 0, dr[1], pts[i][0], pts[i][1], mat, ao);
        }
        for (int i = 1; i + 1 < n; i++) m.tri(id[0], id[i], id[i + 1]);
    }

    /** Weltnormale (x, z) der Seite face (0: +w, 1: +u, 2: −w, 3: −u). */
    public double[] normal(int face) {
        switch (face & 3) {
            case 0: return new double[]{-s, c};
            case 1: return new double[]{c, s};
            case 2: return new double[]{s, -c};
            default: return new double[]{-c, -s};
        }
    }

    /** Rechteck auf einer Wand. */
    public void rect(double uw, double ww, int face, double s0, double s1, double y0, double y1, double off, int mat, double ao) {
        fan(uw, ww, face, new double[][]{{s0, y0}, {s1, y0}, {s1, y1}, {s0, y1}}, off, mat, ao);
    }

    /** Spitzbogen-Umriss (gleichseitig) mit Breite wd, Kämpferhöhe hs ab y0; Mitte bei s = sc. */
    public static double[][] pointedArch(double sc, double y0, double wd, double hs, int arcSteps) {
        java.util.List<double[]> l = new java.util.ArrayList<>();
        l.add(new double[]{sc - wd / 2, y0});
        l.add(new double[]{sc + wd / 2, y0});
        l.add(new double[]{sc + wd / 2, y0 + hs});
        for (int i = 1; i <= arcSteps; i++) {
            double a = Math.toRadians(60.0 * i / arcSteps);
            l.add(new double[]{sc - wd / 2 + wd * Math.cos(a), y0 + hs + wd * Math.sin(a)});
        }
        for (int i = arcSteps - 1; i >= 1; i--) {
            double a = Math.toRadians(60.0 * i / arcSteps);
            l.add(new double[]{sc + wd / 2 - wd * Math.cos(a), y0 + hs + wd * Math.sin(a)});
        }
        l.add(new double[]{sc - wd / 2, y0 + hs});
        return l.toArray(new double[0][]);
    }

    /** Treppengiebel: Stufen aus Quadern vor einer Giebelwand an Seite face (nur +u/−u/+w/−w der Hauptachse). */
    public void stepGable(double uw, double ww, int face, double halfWidth, double yBase, double height, int steps,
                          double thick, int mat) {
        double[] dn = normal(face);
        double[] dr = {dn[1], -dn[0]};
        double sh = height / steps;
        for (int k = 0; k < steps; k++) {
            double y0 = yBase + k * sh;
            double hw = halfWidth * (1.0 - (double) k / steps) + 0.12;
            // Mittelpunkt der Stufe: hinter der Wandfläche um thick/2
            double cxk = wx(uw, ww) - dn[0] * thick / 2, czk = wz(uw, ww) - dn[1] * thick / 2;
            double ex = dr[0] * hw, ez = dr[1] * hw, tx = dn[0] * thick / 2, tz = dn[1] * thick / 2;
            // Quader aus vier Ecken: wir bauen ihn über ein drehbares Teil mit Winkel der Wandrichtung
            Obj o = new Obj(m, cxk, czk, Math.atan2(dr[1], dr[0]));
            o.box(-hw, -thick / 2, hw, thick / 2, y0, y0 + sh, mat, k == 0 ? 0.8 : 1);
        }
    }

    /**
     * Drehkörper um die senkrechte Achse bei (u, w): Ringe von unten nach oben mit Halbmesser r[k] in Höhe y[k]
     * (Halbmesser 0 am Scheitel schließt die Kuppel); n Seiten. Für Säulen, Trommeln, Kuppeln und Türmchen.
     */
    public void lathe(double u, double w, double[] r, double[] y, int n, int mat) {
        for (int k = 0; k + 1 < r.length; k++) {
            for (int i = 0; i < n; i++) {
                double a0 = -2 * Math.PI * i / n, a1 = -2 * Math.PI * (i + 1) / n;
                double[] p00 = p(u + r[k] * Math.cos(a0), y[k], w + r[k] * Math.sin(a0));
                double[] p01 = p(u + r[k] * Math.cos(a1), y[k], w + r[k] * Math.sin(a1));
                double[] p11 = p(u + r[k + 1] * Math.cos(a1), y[k + 1], w + r[k + 1] * Math.sin(a1));
                double[] p10 = p(u + r[k + 1] * Math.cos(a0), y[k + 1], w + r[k + 1] * Math.sin(a0));
                if (r[k + 1] < 1e-6) tri(p00, p01, p10, mat, 1, 1);
                else if (r[k] < 1e-6) tri(p00, p11, p10, mat, 1, 1);
                else { tri(p00, p01, p11, mat, 1, 1); tri(p00, p11, p10, mat, 1, 1); }
            }
        }
    }

    /** Säule (Zylinder) mit Fuß und Kapitell. */
    public void column(double u, double w, double y0, double y1, double rad, int mat) {
        lathe(u, w, new double[]{rad * 1.35, rad * 1.35, rad, rad, rad * 1.3, rad * 1.3}, new double[]{y0, y0 + 0.25, y0 + 0.3, y1 - 0.3, y1 - 0.25, y1}, 8, mat);
    }

    /** Halbkuppel: Halbmesser rad, Höhe h, Ellipsenprofil. */
    public void dome(double u, double w, double y0, double rad, double h, int mat) {
        int rings = 7;
        double[] r = new double[rings + 1], y = new double[rings + 1];
        for (int k = 0; k <= rings; k++) {
            double t = Math.PI / 2 * k / rings;
            r[k] = rad * Math.cos(t);
            y[k] = y0 + h * Math.sin(t);
        }
        r[rings] = 0;
        lathe(u, w, r, y, 16, mat);
    }
}
