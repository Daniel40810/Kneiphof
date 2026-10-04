package com.dan.kneiphof.gl;

import com.dan.kneiphof.world.Dom;
import com.dan.kneiphof.world.DomInterior;
import com.dan.kneiphof.world.Obj;

import java.util.ArrayList;
import java.util.List;

/**
 * Sonnenstrahlen durch die Fenster des Doms: Jedes Fenster, durch das die Sonne gerade hereinscheint, wirft ein
 * Prisma (Umriss des Spitzbogens, in Sonnenrichtung gezogen). Die Prismen werden additiv gezeichnet und vor festen
 * Flächen weich ausgeblendet, sodass sie am Boden und an den Wänden enden.
 */
public final class DomBeams {
    static final double LENGTH = 30.0;
    /** Eckpunkte: Lage (3), Normale (3), Weg 0..1 (1). */
    public float[] verts = new float[0];
    public int[] idx = new int[0];
    private double lx = 9, ly = 9, lz = 9;

    /** Neu bauen, wenn sich die Sonne merklich bewegt hat; liefert true, wenn das Netz neu ist. */
    public boolean rebuild(double[] sun) {
        if (Math.abs(sun[0] - lx) + Math.abs(sun[1] - ly) + Math.abs(sun[2] - lz) < 0.0015) return false;
        lx = sun[0]; ly = sun[1]; lz = sun[2];
        List<float[]> v = new ArrayList<>();
        List<Integer> ix = new ArrayList<>();
        double[] o0 = DomInterior.toWorld(0, 0), eu = DomInterior.toWorld(1, 0), ew = DomInterior.toWorld(0, 1);
        double ux = eu[0] - o0[0], uz = eu[1] - o0[1], wx = ew[0] - o0[0], wz = ew[1] - o0[1];
        double G = DomInterior.G, WI = DomInterior.WI;
        if (sun[1] > 0.05) {
            double[] d = {-sun[0], -sun[1], -sun[2]};
            double du = d[0] * ux + d[2] * uz, dw = d[0] * wx + d[2] * wz;      // Strahlrichtung im Bezugssystem
            for (int k = 0; k <= 10; k++) {
                double uc = 16 + 6.5 * k + 3.25;
                if (uc > Dom.L - 2.5 - 0.4) continue;
                for (int side = -1; side <= 1; side += 2) {
                    if (side > 0 && k == 2) continue;
                    // von der Wand ins Schiff: nach −w bei side > 0, nach +w bei side < 0
                    if (dw * -side < 0.12) continue;
                    prism(v, ix, Obj.pointedArch(0, G + 5.0, 2.8, 7.0, 6), uc, side * WI, 1, 0, d);
                }
            }
            for (int i = -1; i <= 1; i++)
                if (-du > 0.12) prism(v, ix, Obj.pointedArch(0, G + 3.6, 2.6, 5.9, 6), DomInterior.U1, i * 5.4, 0, 1, d);
        }
        verts = new float[v.size() * 7];
        for (int i = 0; i < v.size(); i++) System.arraycopy(v.get(i), 0, verts, i * 7, 7);
        idx = new int[ix.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = ix.get(i);
        return true;
    }

    /** lateral: Achse 1 = längs (u) bei Seitenfenstern, Achse 0 = quer (w) bei den Chorfenstern. */
    private static void prism(List<float[]> v, List<Integer> ix, double[][] outline, double u, double w, int alongU, int unused, double[] d) {
        int n = outline.length;
        int base = v.size();
        double[] p0 = DomInterior.toWorld(u, w);
        double[] eu = DomInterior.toWorld(1, 0), ew = DomInterior.toWorld(0, 1), o0 = DomInterior.toWorld(0, 0);
        double ax = alongU == 1 ? eu[0] - o0[0] : ew[0] - o0[0], az = alongU == 1 ? eu[1] - o0[1] : ew[1] - o0[1];
        // Wandebene ein wenig nach innen, damit der Strahl nicht in der Mauer beginnt
        double inX = alongU == 1 ? -(ew[0] - o0[0]) * Math.signum(w) : (eu[0] - o0[0]) * -1;
        double inZ = alongU == 1 ? -(ew[1] - o0[1]) * Math.signum(w) : (eu[1] - o0[1]) * -1;
        double[][] near = new double[n][3], far = new double[n][3];
        for (int i = 0; i < n; i++) {
            double lat = outline[i][0], y = outline[i][1];
            double x = p0[0] + ax * lat + inX * 0.25, z = p0[1] + az * lat + inZ * 0.25;
            near[i] = new double[]{x, y, z};
            far[i] = new double[]{x + d[0] * LENGTH, y + d[1] * LENGTH, z + d[2] * LENGTH};
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            double ex = near[j][0] - near[i][0], ey = near[j][1] - near[i][1], ez = near[j][2] - near[i][2];
            // Normale der Seitenfläche: Kante × Strahl
            double nx = ey * d[2] - ez * d[1], ny = ez * d[0] - ex * d[2], nz = ex * d[1] - ey * d[0];
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1e-9) continue;
            nx /= nl; ny /= nl; nz /= nl;
            int b = v.size();
            v.add(new float[]{(float) near[i][0], (float) near[i][1], (float) near[i][2], (float) nx, (float) ny, (float) nz, 0f});
            v.add(new float[]{(float) near[j][0], (float) near[j][1], (float) near[j][2], (float) nx, (float) ny, (float) nz, 0f});
            v.add(new float[]{(float) far[j][0], (float) far[j][1], (float) far[j][2], (float) nx, (float) ny, (float) nz, 1f});
            v.add(new float[]{(float) far[i][0], (float) far[i][1], (float) far[i][2], (float) nx, (float) ny, (float) nz, 1f});
            ix.add(b); ix.add(b + 1); ix.add(b + 2); ix.add(b); ix.add(b + 2); ix.add(b + 3);
        }
    }
}
