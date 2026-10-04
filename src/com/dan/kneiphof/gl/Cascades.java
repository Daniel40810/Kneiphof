package com.dan.kneiphof.gl;

import com.dan.kneiphof.math.Mat4;

/**
 * Schattenkaskaden für die Sonne: drei Karten von je 2048 × 2048 Punkten, die nah an der Kamera fein
 * und weiter weg grob auflösen (bis 18, 90 und 520 m). Jede Kaskade umschließt ihr Stück des
 * Sichtkegels mit einer Kugel; die Kugel ändert beim Drehen der Kamera ihre Größe nicht, und die Mitte
 * rastet auf ganze Kartenpunkte ein. So flimmern die Schattenkanten nicht, wenn sich die Kamera bewegt.
 */
public final class Cascades {
    public static final int COUNT = 3, SIZE = 2048;
    /** Enden der Kaskaden in Metern Sichttiefe (vom Abstand der Kamera zum Drehpunkt abhängig). */
    public final float[] splits = new float[COUNT];
    public final float[][] lightVP = new float[COUNT][];
    /** Größe eines Kartenpunkts in Metern je Kaskade (für die Verschiebung gegen Schattenakne). */
    public final float[] texel = new float[COUNT];

    /**
     * eye: Augpunkt; fwd: Blickrichtung (normiert); fovy und aspect der Kamera; sun: Richtung zur Sonne;
     * zeroToOne: Tiefenbereich 0..1 (glClipControl) statt −1..1.
     */
    public void update(double[] eye, double[] fwd, double fovyDeg, double aspect, double[] sun, double camDist, boolean zeroToOne) {
        double base = Math.max(14, Math.min(60, camDist * 0.25));
        double[] ends = {base, base * 5, Math.max(base * 28, 400)};
        double tanY = Math.tan(Math.toRadians(fovyDeg) / 2), tanX = tanY * aspect;
        double prev = 0.3;
        // Lichtsystem: z zur Sonne
        double[] lz = norm(sun);
        double[] up = Math.abs(lz[1]) > 0.99 ? new double[]{0, 0, 1} : new double[]{0, 1, 0};
        double[] lx = norm(cross(up, lz)), ly = cross(lz, lx);
        for (int c = 0; c < COUNT; c++) {
            double n = prev, f = ends[c];
            splits[c] = (float) f;
            // Kugel um das Kegelstück: Mitte auf der Achse in Tiefe t, Nah- und Fernecken auf der Kugel
            double k = tanX * tanX + tanY * tanY;
            double t = Math.min(f, (f + n) * (1 + k) / 2);
            double radius = Math.sqrt((f - t) * (f - t) + f * f * k);
            radius = Math.ceil(radius * 16) / 16;
            double cx = eye[0] + fwd[0] * t, cy = eye[1] + fwd[1] * t, cz = eye[2] + fwd[2] * t;
            // Mitte im Lichtsystem auf ganze Kartenpunkte runden
            double unit = 2 * radius / SIZE;
            double px = dot(lx, cx, cy, cz), py = dot(ly, cx, cy, cz), pz = dot(lz, cx, cy, cz);
            px = Math.floor(px / unit) * unit;
            py = Math.floor(py / unit) * unit;
            texel[c] = (float) unit;
            // Blick des Lichts: vom Punkt 2500 m sonnenwärts auf die Mitte
            double back = 2500;
            double ox = lx[0] * px + ly[0] * py + lz[0] * (pz + back);
            double oy = lx[1] * px + ly[1] * py + lz[1] * (pz + back);
            double oz = lx[2] * px + ly[2] * py + lz[2] * (pz + back);
            float[] view = Mat4.identity();
            view[0] = (float) lx[0]; view[4] = (float) lx[1]; view[8] = (float) lx[2];
            view[1] = (float) ly[0]; view[5] = (float) ly[1]; view[9] = (float) ly[2];
            view[2] = (float) lz[0]; view[6] = (float) lz[1]; view[10] = (float) lz[2];
            view[12] = (float) -(lx[0] * ox + lx[1] * oy + lx[2] * oz);
            view[13] = (float) -(ly[0] * ox + ly[1] * oy + ly[2] * oz);
            view[14] = (float) -(lz[0] * ox + lz[1] * oy + lz[2] * oz);
            float[] proj = ortho(-radius, radius, -radius, radius, 1, back + radius + 200, zeroToOne);
            lightVP[c] = Mat4.mul(proj, view);
            prev = f * 0.92;
        }
    }

    static float[] ortho(double l, double r, double b, double t, double n, double f, boolean zeroToOne) {
        float[] m = Mat4.identity();
        m[0] = (float) (2 / (r - l));
        m[5] = (float) (2 / (t - b));
        m[12] = (float) (-(r + l) / (r - l));
        m[13] = (float) (-(t + b) / (t - b));
        if (zeroToOne) {
            m[10] = (float) (-1 / (f - n));
            m[14] = (float) (-n / (f - n));
        } else {
            m[10] = (float) (-2 / (f - n));
            m[14] = (float) (-(f + n) / (f - n));
        }
        return m;
    }

    static double[] norm(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double dot(double[] a, double x, double y, double z) { return a[0] * x + a[1] * y + a[2] * z; }
}
