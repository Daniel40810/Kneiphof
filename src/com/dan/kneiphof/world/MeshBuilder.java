package com.dan.kneiphof.world;

import java.util.Arrays;

/**
 * Baut Dreiecksnetze auf der CPU: Lage, Normale, Tangente, Flächenkoordinaten in Metern, Material und
 * eine grobe Verdeckung (AO) je Punkt. Die Flächenkoordinaten laufen in Metern entlang der Fläche
 * (u waagerecht, v senkrecht), damit Backsteinverband und Marmoradern in jedem Bauteil gleich groß
 * erscheinen, ohne Texturen.
 */
public final class MeshBuilder {
    /** x y z  nx ny nz  tx ty tz  u v  material  ao */
    public static final int STRIDE = 13;

    private float[] v = new float[STRIDE * 4096];
    private int[] idx = new int[8192];
    private int nv, ni;

    public int vertexCount() { return nv; }
    public int indexCount() { return ni; }
    public float[] vertices() { return Arrays.copyOf(v, nv * STRIDE); }
    public int[] indices() { return Arrays.copyOf(idx, ni); }

    public int vertex(double x, double y, double z, double nx, double ny, double nz, double tx, double ty, double tz,
                      double u, double vv, int mat, double ao) {
        if ((nv + 1) * STRIDE > v.length) v = Arrays.copyOf(v, v.length * 2);
        int o = nv * STRIDE;
        v[o] = (float) x; v[o + 1] = (float) y; v[o + 2] = (float) z;
        v[o + 3] = (float) nx; v[o + 4] = (float) ny; v[o + 5] = (float) nz;
        v[o + 6] = (float) tx; v[o + 7] = (float) ty; v[o + 8] = (float) tz;
        v[o + 9] = (float) u; v[o + 10] = (float) vv;
        v[o + 11] = mat; v[o + 12] = (float) ao;
        return nv++;
    }

    public void tri(int a, int b, int c) {
        if (ni + 3 > idx.length) idx = Arrays.copyOf(idx, idx.length * 2);
        idx[ni++] = a; idx[ni++] = b; idx[ni++] = c;
    }

    /**
     * Viereck p0 p1 p2 p3 gegen den Uhrzeigersinn von außen gesehen. Die Flächenkoordinaten beginnen
     * bei (u0, v0); u läuft entlang p0→p1, v entlang p0→p3, beide in Metern.
     * aoBottom/aoTop: Verdeckung an der Unter- und Oberkante (p0p1 bzw. p3p2).
     */
    public void quad(double[] p0, double[] p1, double[] p2, double[] p3, int mat, double u0, double v0, double aoBottom, double aoTop) {
        double ex = p1[0] - p0[0], ey = p1[1] - p0[1], ez = p1[2] - p0[2];
        double fx = p3[0] - p0[0], fy = p3[1] - p0[1], fz = p3[2] - p0[2];
        double nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= nl; ny /= nl; nz /= nl;
        double el = Math.sqrt(ex * ex + ey * ey + ez * ez), fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        double tx = ex / el, ty = ey / el, tz = ez / el;
        int a = vertex(p0[0], p0[1], p0[2], nx, ny, nz, tx, ty, tz, u0, v0, mat, aoBottom);
        int b = vertex(p1[0], p1[1], p1[2], nx, ny, nz, tx, ty, tz, u0 + el, v0, mat, aoBottom);
        int c = vertex(p2[0], p2[1], p2[2], nx, ny, nz, tx, ty, tz, u0 + el, v0 + fl, mat, aoTop);
        int d = vertex(p3[0], p3[1], p3[2], nx, ny, nz, tx, ty, tz, u0, v0 + fl, mat, aoTop);
        tri(a, b, c);
        tri(a, c, d);
    }

    public void quad(double[] p0, double[] p1, double[] p2, double[] p3, int mat) { quad(p0, p1, p2, p3, mat, 0, 0, 1, 1); }

    /**
     * Quader. aoFoot: Verdeckung am Fuß der Seitenflächen (Kontaktschatten zum Boden), 1 = keine.
     * Die Seiten bekommen fortlaufende u-Koordinaten rund um den Quader, damit der Verband um die
     * Ecke läuft.
     */
    public void box(double x0, double y0, double z0, double x1, double y1, double z1, int mat, double aoFoot) {
        double[] a = {x0, y0, z1}, b = {x1, y0, z1}, c = {x1, y0, z0}, d = {x0, y0, z0};
        double[] e = {x0, y1, z1}, f = {x1, y1, z1}, g = {x1, y1, z0}, h = {x0, y1, z0};
        double w = x1 - x0, dpt = z1 - z0;
        quad(a, b, f, e, mat, 0, y0, aoFoot, 1);               // Süden (+z)
        quad(b, c, g, f, mat, w, y0, aoFoot, 1);               // Osten
        quad(c, d, h, g, mat, w + dpt, y0, aoFoot, 1);         // Norden
        quad(d, a, e, h, mat, 2 * w + dpt, y0, aoFoot, 1);     // Westen
        quad(e, f, g, h, mat, x0, -z1, 1, 1);                   // oben
        quad(d, c, b, a, mat, x0, z0, 1, 1);                    // unten
    }

    /** Pyramide (Helm) über dem Rechteck, Spitze in Höhe top. */
    public void pyramid(double x0, double y0, double z0, double x1, double z1, double top, int mat) {
        double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        double[][] base = {{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}};
        double[] apex = {cx, top, cz};
        for (int i = 0; i < 4; i++) {
            double[] p = base[i], q = base[(i + 1) % 4];
            double ex = q[0] - p[0], ey = 0, ez = q[2] - p[2];
            double fx = apex[0] - p[0], fy = apex[1] - p[1], fz = apex[2] - p[2];
            double nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            double el = Math.hypot(ex, ez);
            double slant = Math.sqrt(Math.pow(apex[0] - (p[0] + q[0]) / 2, 2) + Math.pow(apex[1] - p[1], 2) + Math.pow(apex[2] - (p[2] + q[2]) / 2, 2));
            int a = vertex(p[0], p[1], p[2], nx / nl, ny / nl, nz / nl, ex / el, 0, ez / el, 0, 0, mat, 1);
            int b = vertex(q[0], q[1], q[2], nx / nl, ny / nl, nz / nl, ex / el, 0, ez / el, el, 0, mat, 1);
            int c = vertex(apex[0], apex[1], apex[2], nx / nl, ny / nl, nz / nl, ex / el, 0, ez / el, el / 2, slant, mat, 1);
            tri(a, b, c);
        }
    }

    /** Zylinder (Säule) mit seg Teilen, glatt schattiert, mit Deckel. */
    public void cylinder(double cx, double y0, double cz, double r, double h, int seg, int mat, double aoFoot) {
        int base = nv;
        double circ = 2 * Math.PI * r;
        for (int i = 0; i <= seg; i++) {
            double a = 2 * Math.PI * i / seg;
            double nx = Math.sin(a), nz = Math.cos(a);
            double tx = nz, tz = -nx;
            vertex(cx + nx * r, y0, cz + nz * r, nx, 0, nz, tx, 0, tz, circ * i / seg, y0, mat, aoFoot);
            vertex(cx + nx * r, y0 + h, cz + nz * r, nx, 0, nz, tx, 0, tz, circ * i / seg, y0 + h, mat, 1);
        }
        for (int i = 0; i < seg; i++) {
            int a = base + 2 * i, b = a + 2, c = a + 3, d = a + 1;
            tri(a, b, c);
            tri(a, c, d);
        }
        int center = vertex(cx, y0 + h, cz, 0, 1, 0, 1, 0, 0, cx, -cz, mat, 1);
        int first = nv;
        for (int i = 0; i <= seg; i++) {
            double a = 2 * Math.PI * i / seg;
            vertex(cx + Math.sin(a) * r, y0 + h, cz + Math.cos(a) * r, 0, 1, 0, 1, 0, 0, cx + Math.sin(a) * r, -(cz + Math.cos(a) * r), mat, 1);
        }
        for (int i = 0; i < seg; i++) tri(center, first + i, first + i + 1);
    }

    /** Hängt ein anderes Netz an. */
    public void append(MeshBuilder o) {
        int off = nv;
        for (int i = 0; i < o.nv; i++) {
            int s = i * STRIDE;
            vertex(o.v[s], o.v[s + 1], o.v[s + 2], o.v[s + 3], o.v[s + 4], o.v[s + 5], o.v[s + 6], o.v[s + 7], o.v[s + 8],
                    o.v[s + 9], o.v[s + 10], (int) o.v[s + 11], o.v[s + 12]);
        }
        for (int i = 0; i < o.ni; i += 3) tri(o.idx[i] + off, o.idx[i + 1] + off, o.idx[i + 2] + off);
    }
}
