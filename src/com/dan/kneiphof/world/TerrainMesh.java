package com.dan.kneiphof.world;

/**
 * Das Gelände als Gitter mit wachsender Maschenweite: in der Mitte gut 3 m, am Rand des feinen
 * Teils (3,2 km) gut 20 m. Daran schließt ein Ring in 24 Stufen bis 16 km an, damit kein Rand der Welt
 * zu sehen ist: im Westen liegt dort das Frische Haff, sonst Felder im Dunst. Der Ring teilt seine
 * innersten Punkte mit dem Rand des Gitters, es gibt keine Fugen.
 * <p>
 * Je Punkt: Lage, Normale, Bodenart. Reines Java ohne OpenGL, damit Prüfprogramme es auch ohne
 * Grafikkarte bauen können.
 */
public final class TerrainMesh {
    public static final int STRIDE = 10;     // x y z  nx ny nz  stadt wiese nass schlamm
    public static final int RING_LAYERS = 24;
    public final int n;
    public final float[] verts;
    public final int[] indices;

    public TerrainMesh(int n) {
        this.n = n;
        double[] gx = new double[n];
        for (int i = 0; i < n; i++) gx[i] = warp(i / (n - 1.0) * 2 - 1) * Site.EXTENT;

        // Umfang des Gitters im Kreis herum: Norden (j=0) von West nach Ost, Osten, Süden, Westen
        int per = 4 * (n - 1);
        double[] px = new double[per], pz = new double[per];
        int q = 0;
        for (int i = 0; i < n - 1; i++) { px[q] = gx[i]; pz[q++] = gx[0]; }
        for (int j = 0; j < n - 1; j++) { px[q] = gx[n - 1]; pz[q++] = gx[j]; }
        for (int i = n - 1; i > 0; i--) { px[q] = gx[i]; pz[q++] = gx[n - 1]; }
        for (int j = n - 1; j > 0; j--) { px[q] = gx[0]; pz[q++] = gx[j]; }

        int gridVerts = n * n, ringVerts = per * RING_LAYERS;
        double[] X = new double[gridVerts + ringVerts], Z = new double[gridVerts + ringVerts];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) { X[j * n + i] = gx[i]; Z[j * n + i] = gx[j]; }
        for (int k = 1; k <= RING_LAYERS; k++) {
            double s = Math.pow(Site.FAR / Site.EXTENT, k / (double) RING_LAYERS);
            for (int p = 0; p < per; p++) {
                int v = gridVerts + (k - 1) * per + p;
                X[v] = px[p] * s; Z[v] = pz[p] * s;
            }
        }
        int total = X.length;
        verts = new float[total * STRIDE];
        java.util.stream.IntStream.range(0, total).parallel().forEach(v -> {
            double x = X[v], z = Z[v];
            double h = Site.height(x, z);
            double e = Math.max(0.5, spacingAt(x, z, gx) * 0.5);
            double dhdx = (Site.height(x + e, z) - Site.height(x - e, z)) / (2 * e);
            double dhdz = (Site.height(x, z + e) - Site.height(x, z - e)) / (2 * e);
            double l = Math.sqrt(dhdx * dhdx + 1 + dhdz * dhdz);
            int o = v * STRIDE;
            verts[o] = (float) x; verts[o + 1] = (float) h; verts[o + 2] = (float) z;
            verts[o + 3] = (float) (-dhdx / l); verts[o + 4] = (float) (1 / l); verts[o + 5] = (float) (-dhdz / l);
            float[] cov = new float[4];
            Site.cover(x, z, cov);
            System.arraycopy(cov, 0, verts, o + 6, 4);
        });

        int[] idx = new int[(n - 1) * (n - 1) * 6 + per * RING_LAYERS * 6];
        int t = 0;
        for (int j = 0; j + 1 < n; j++)
            for (int i = 0; i + 1 < n; i++) {
                int a = j * n + i, b = a + 1, c = a + n, d = c + 1;
                idx[t++] = a; idx[t++] = c; idx[t++] = b;
                idx[t++] = b; idx[t++] = c; idx[t++] = d;
            }
        // Ring: Stufe 0 sind die Randpunkte des Gitters
        int[] edge = new int[per];
        q = 0;
        for (int i = 0; i < n - 1; i++) edge[q++] = i;
        for (int j = 0; j < n - 1; j++) edge[q++] = j * n + n - 1;
        for (int i = n - 1; i > 0; i--) edge[q++] = (n - 1) * n + i;
        for (int j = n - 1; j > 0; j--) edge[q++] = j * n;
        for (int k = 0; k < RING_LAYERS; k++)
            for (int p = 0; p < per; p++) {
                int p1 = (p + 1) % per;
                int a = k == 0 ? edge[p] : gridVerts + (k - 1) * per + p;
                int b = k == 0 ? edge[p1] : gridVerts + (k - 1) * per + p1;
                int c = gridVerts + k * per + p, d = gridVerts + k * per + p1;
                // Umlaufsinn so, dass die Fläche nach oben zeigt (Rückseiten werden weggelassen)
                idx[t++] = a; idx[t++] = b; idx[t++] = c;
                idx[t++] = b; idx[t++] = d; idx[t++] = c;
            }
        indices = idx;
    }

    private static double spacingAt(double x, double z, double[] gx) {
        double r = Math.max(Math.abs(x), Math.abs(z));
        if (r > Site.EXTENT) return r * 0.1;
        double t = r / Site.EXTENT;
        return Site.EXTENT * 2.0 / (gx.length - 1) * (0.35 + 3 * 0.65 * t * t);
    }

    /** −1..1 auf −1..1, in der Mitte flach (feine Maschen), außen steil. */
    static double warp(double t) {
        double a = 0.35;
        return t * (a + (1 - a) * t * t);
    }

    /** Maschenweite in der Mitte in Metern. */
    public double centerSpacing() { return warp(2.0 / (n - 1)) * Site.EXTENT; }

    /** Anzahl der Punkte. */
    public int vertexCount() { return verts.length / STRIDE; }
}
