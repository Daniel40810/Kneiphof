package com.dan.kneiphof.gl;

import com.dan.kneiphof.world.MeshBuilder;
import com.dan.kneiphof.world.Town;
import com.jogamp.opengl.GL4;

import java.util.List;

/**
 * Die Stadt in Kacheln: jede Kachel hat ein eigenes Netz und eine Umgrenzung. Gezeichnet werden nur die
 * Kacheln, die ein Sichtkegel (vier Seitenebenen aus der Matrix) trifft, für die Kamera wie für jede
 * Schattenkaskade.
 */
final class TownRenderer {
    private static final class T {
        final Gpu.Mesh mesh = new Gpu.Mesh();
        double minX, maxX, minZ, maxZ;
    }

    private T[] tiles = new T[0];
    public int drawn, total;
    public long triangles;

    void upload(GL4 gl, List<Town.Tile> src) {
        dispose(gl);
        tiles = new T[src.size()];
        for (int i = 0; i < tiles.length; i++) {
            Town.Tile s = src.get(i);
            MeshBuilder mb = s.mb;
            T t = new T();
            t.mesh.upload(gl, mb.vertices(), mb.indices(), new int[]{3, 3, 3, 2, 1, 1});
            t.minX = s.minX; t.maxX = s.maxX; t.minZ = s.minZ; t.maxZ = s.maxZ;
            tiles[i] = t;
            triangles += mb.indexCount() / 3;
        }
        total = tiles.length;
    }

    /** Seitenebenen (links, rechts, unten, oben) aus einer spaltenweisen Matrix, normiert nicht nötig. */
    static float[][] planes(float[] m) {
        float[][] p = new float[4][4];
        for (int k = 0; k < 4; k++) {
            int row = k >> 1;          // 0: x, 1: y
            float sign = (k & 1) == 0 ? 1f : -1f;
            for (int j = 0; j < 4; j++) p[k][j] = m[j * 4 + 3] + sign * m[j * 4 + row];
        }
        return p;
    }

    private static boolean visible(float[][] pl, T t) {
        for (float[] p : pl) {
            // Eckpunkt der Box, der am weitesten auf der positiven Seite liegt
            double x = p[0] >= 0 ? t.maxX : t.minX;
            double y = p[1] >= 0 ? Town.Tile.MAX_Y : Town.Tile.MIN_Y;
            double z = p[2] >= 0 ? t.maxZ : t.minZ;
            if (p[0] * x + p[1] * y + p[2] * z + p[3] < 0) return false;
        }
        return true;
    }

    /** Sichtbare Kacheln zeichnen; vp: Matrix, deren Sichtkegel gilt. */
    void draw(GL4 gl, float[] vp) { draw(gl, vp, 0, 0, Double.MAX_VALUE); }

    /** Wie {@link #draw(GL4, float[])}, aber nur Kacheln, deren Grundfläche höchstens maxDist von (cx, cz) liegt. */
    void draw(GL4 gl, float[] vp, double cx, double cz, double maxDist) {
        float[][] pl = planes(vp);
        int n = 0;
        double m2 = maxDist * maxDist;
        for (T t : tiles) {
            if (maxDist < 1e9) {
                double dx = Math.max(Math.max(t.minX - cx, cx - t.maxX), 0), dz = Math.max(Math.max(t.minZ - cz, cz - t.maxZ), 0);
                if (dx * dx + dz * dz > m2) continue;
            }
            if (!visible(pl, t)) continue;
            t.mesh.draw(gl);
            n++;
        }
        drawn = n;
    }

    void dispose(GL4 gl) {
        for (T t : tiles) t.mesh.dispose(gl);
        tiles = new T[0];
        triangles = 0;
    }
}
