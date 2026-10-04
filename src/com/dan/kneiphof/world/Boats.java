package com.dan.kneiphof.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Schiffe auf dem Pregel: Haffkähne mit braunem Segel und ein Schraubendampfer. Jedes Schiff folgt
 * einer Linie im Fluss; seine Lage liefert die Bewegungsmatrix für den Zeichner und das Kielwasser
 * für den Wasser-Shader. Die Schiffe fahren in Phase 6 noch auf dem freien Westarm; Phase 9 lässt sie
 * durch die Brücken laufen.
 * <p>Bezugssystem eines Schiffsnetzes: x voraus, y oben, z nach Steuerbord, Wasserlinie y = 0.</p>
 */
public final class Boats {

    public static final int MAX = 8;

    public static final class Boat {
        public final String name;
        public final MeshBuilder mesh;
        public final double length, beam, speed;
        final double[][] path;
        final double lane;
        final boolean forward;
        final double phase;
        public double x, z, hx = 1, hz = 0, y, roll, s;
        public double v;
        double vNow;
        /** Brücken auf dem Weg: Bogenlänge der Brückenmitte, Nummer der Brücke. */
        final List<double[]> stops = new ArrayList<>();
        double[] cum;

        Boat(String name, MeshBuilder mesh, double length, double beam, double speed, double[][] path, double lane, boolean forward,
             double s0, double phase) {
            this.name = name;
            this.mesh = mesh;
            this.length = length;
            this.beam = beam;
            this.speed = speed;
            this.path = path;
            this.lane = lane;
            this.forward = forward;
            this.phase = phase;
            this.s = s0;
            this.vNow = speed;
            cum = new double[path.length];
            for (int i = 1; i < path.length; i++) cum[i] = cum[i - 1] + Math.hypot(path[i][0] - path[i - 1][0], path[i][1] - path[i - 1][1]);
        }

        double total() { return cum[cum.length - 1]; }

        /** Punkt und Richtung bei Bogenlänge t (Fahrtrichtung vorwärts auf der Linie). */
        double[] at(double t) {
            t = Math.max(0, Math.min(total() - 1e-6, t));
            int i = 1;
            while (i < cum.length - 1 && cum[i] < t) i++;
            double seg = cum[i] - cum[i - 1];
            double f = seg > 0 ? (t - cum[i - 1]) / seg : 0;
            double dx = path[i][0] - path[i - 1][0], dz = path[i][1] - path[i - 1][1];
            double dl = Math.hypot(dx, dz);
            dx /= dl;
            dz /= dl;
            // Fahrbahn seitlich versetzt (positiv: rechts der Linienrichtung gesehen von oben nach −dz)
            double px = path[i - 1][0] + (path[i][0] - path[i - 1][0]) * f - dz * lane;
            double pz = path[i - 1][1] + (path[i][1] - path[i - 1][1]) * f + dx * lane;
            return new double[]{px, pz, dx, dz};
        }

        /** Fahrt regeln: vor geschlossenen Brücken anhalten, Öffnung anfordern, Abstand zum Vordermann halten. */
        void control(double dt, float[] open, boolean[] need, List<Boat> all) {
            if (dt <= 0) return;
            double dirS = forward ? 1 : -1, target = speed, half = length / 2;
            for (double[] st : stops) {
                double ahead = (st[0] - s) * dirS;
                int bi = (int) st[1];
                if (ahead > -(half + 12) && ahead < 130) {
                    need[bi] = true;
                    if (ahead > 0) {
                        double room = ahead - (half + 13);
                        if (open[bi] < 0.93) target = Math.min(target, Math.sqrt(2 * 0.45 * Math.max(0, room)));
                        else target = Math.min(target, 2.2);
                    } else target = Math.min(target, 2.2);
                }
            }
            for (Boat o : all) {
                if (o == this || o.path != path || o.forward != forward) continue;
                double gap = (o.s - s) * dirS;
                if (gap > 0 && gap < 160) {
                    double room = gap - (length + o.length) / 2 - 10;
                    target = Math.min(target, Math.min(o.vNow + 0.3, Math.sqrt(2 * 0.5 * Math.max(0, room))));
                }
            }
            double dv = target - vNow;
            vNow += Math.max(-0.9 * dt, Math.min(0.5 * dt, dv));
            if (vNow < 0) vNow = 0;
        }

        void update(double dt, double time) {
            double total = total();
            s += (forward ? 1 : -1) * vNow * dt;
            if (s > total - 20) s = 20;
            if (s < 20) s = total - 20;
            double[] p = at(s), q = at(s + (forward ? 6 : -6));
            double hxn = q[0] - p[0], hzn = q[1] - p[1];
            double hl = Math.hypot(hxn, hzn);
            if (hl > 1e-6) { hx = hxn / hl; hz = hzn / hl; }
            x = p[0];
            z = p[1];
            v = vNow;
            y = 0.05 * Math.sin(time * 0.9 + phase) + 0.02 * Math.sin(time * 2.3 + phase * 2);
            roll = 0.012 * Math.sin(time * 0.7 + phase);
        }

        /** Bewegungsmatrix (Spalten): x voraus, y oben, z nach Steuerbord, leichte Rollbewegung. */
        public float[] matrix() {
            double fx = hx, fz = hz;
            double rx = -fz, rz = fx;
            double cr = Math.cos(roll), sr = Math.sin(roll);
            // y-Achse ein wenig zur Seite geneigt
            double ux = rx * sr, uy = cr, uz = rz * sr;
            return new float[]{
                    (float) fx, 0f, (float) fz, 0f,
                    (float) ux, (float) uy, (float) uz, 0f,
                    (float) (rx * cr), (float) (-sr), (float) (rz * cr), 0f,
                    (float) x, (float) y, (float) z, 1f};
        }
    }

    public final List<Boat> list = new ArrayList<>();

    public Boats() {
        double[][] west = Site.PREGEL_WEST.pts;
        // nur die ersten rund 2,5 km: von der Stadt nach Westen zum Haff
        double[][] route = new double[][]{west[0], west[1], west[2], west[3]};
        // Zwei Kähne fahren mit dem Strom nach Westen, der Dampfer kommt auf der anderen Fahrbahn zurück
        list.add(new Boat("Haffkahn", kahn(22, 4.8, 1), 22, 4.8, 2.6, route, 16, true, 260, 0.3));
        list.add(new Boat("Haffkahn", kahn(18, 4.2, 2), 18, 4.2, 2.1, route, 14, true, 1250, 2.1));
        list.add(new Boat("Dampfer", dampfer(32, 5.6), 32, 5.6, 4.6, route, -18, false, 1750, 4.0));
        list.add(new Boat("Dampfer", dampfer(26, 4.8), 26, 4.8, 3.9, route, -16, false, 900, 5.5));
        // Durch die Brücken: Neuer Pregel nach Osten, Alter Pregel nach Westen, genau in der Mitte der Durchfahrt
        double[][] neu = Site.NEUER_PREGEL.pts, alt = Site.ALTER_PREGEL.pts;
        list.add(new Boat("Haffkahn", kahn(20, 4.5, 1), 20, 4.5, 2.4, neu, 0, true, 60, 1.1));
        list.add(new Boat("Dampfer", dampfer(28, 5.0), 28, 5.0, 4.2, neu, 0, true, 330, 3.3));
        list.add(new Boat("Haffkahn", kahn(19, 4.3, 2), 19, 4.3, 2.2, alt, 0, false, 480, 0.8));
        list.add(new Boat("Dampfer", dampfer(30, 5.2), 30, 5.2, 4.0, alt, 0, false, 1800, 2.6));
        for (Boat b : list) b.update(0, 0);
    }

    private static final float[] ALL_OPEN = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    /** Welche Brücken ein Schiff gerade geöffnet haben will. */
    public final boolean[] need = new boolean[10];

    public void update(double dt, double time) { update(dt, time, ALL_OPEN); }

    /** open: Öffnung jeder Brücke 0..1. */
    public void update(double dt, double time, float[] open) {
        java.util.Arrays.fill(need, false);
        for (Boat b : list) b.control(dt, open, need, list);
        for (Boat b : list) b.update(dt, time);
    }

    /** Brücken suchen, die auf dem Weg eines Schiffs liegen (Mitte der Fahrbahn höchstens 8 m neben der Linie). */
    public void attach(Bridges br) {
        for (Boat b : list) {
            b.stops.clear();
            double[][] p = b.path;
            for (int k = 0; k < br.list.size(); k++) {
                Bridges.Bridge bg = br.list.get(k);
                double best = 1e9, bs = 0, cum = 0;
                for (int i = 1; i < p.length; i++) {
                    double sl = Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
                    for (double t = 0; t <= 1; t += 0.005) {
                        double x = p[i - 1][0] + t * (p[i][0] - p[i - 1][0]), z = p[i - 1][1] + t * (p[i][1] - p[i - 1][1]);
                        double d = Math.hypot(x - bg.cx, z - bg.cz);
                        if (d < best) { best = d; bs = cum + t * sl; }
                    }
                    cum += sl;
                }
                if (best < 8) b.stops.add(new double[]{bs, k});
            }
        }
    }

    /** Alle Schiffe an ihrer aktuellen Stelle in ein festes Netz (für den Prüfstand ohne Grafikkarte). */
    public MeshBuilder bake() {
        MeshBuilder out = new MeshBuilder();
        for (Boat b : list) {
            float[] m = b.matrix();
            float[] v = b.mesh.vertices();
            int[] idx = b.mesh.indices();
            int base = out.vertexCount();
            int n = v.length / MeshBuilder.STRIDE;
            for (int k = 0; k < n; k++) {
                int o = k * MeshBuilder.STRIDE;
                double[] p = xf(m, v[o], v[o + 1], v[o + 2], 1), nn = xf(m, v[o + 3], v[o + 4], v[o + 5], 0), t = xf(m, v[o + 6], v[o + 7], v[o + 8], 0);
                out.vertex(p[0], p[1], p[2], nn[0], nn[1], nn[2], t[0], t[1], t[2], v[o + 9], v[o + 10], (int) v[o + 11], v[o + 12]);
            }
            for (int k = 0; k < idx.length; k += 3) out.tri(base + idx[k], base + idx[k + 1], base + idx[k + 2]);
        }
        return out;
    }

    private static double[] xf(float[] m, double x, double y, double z, double w) {
        return new double[]{m[0] * x + m[4] * y + m[8] * z + m[12] * w, m[1] * x + m[5] * y + m[9] * z + m[13] * w, m[2] * x + m[6] * y + m[10] * z + m[14] * w};
    }

    // -------------------------------------------------------------------------------- Netze

    private static double lerp(double[] xs, double[] vs, double x) {
        if (x <= xs[0]) return vs[0];
        for (int i = 1; i < xs.length; i++) {
            if (x <= xs[i]) {
                double f = (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
                return vs[i - 1] + (vs[i] - vs[i - 1]) * f;
            }
        }
        return vs[vs.length - 1];
    }

    /**
     * Rumpf aus Spanten: Halbbreite, Sprung (Höhe der Bordkante) und Tiefgang je Station. Die Seiten
     * laufen am Bug zusammen, das Heck ist gerundet und gerade abgeschnitten.
     */
    private static void hull(MeshBuilder m, double len, double beam, double[] fx, double[] hw, double[] sheer, double keel, int sideMat, int deckMat) {
        int n = fx.length;
        double[] xs = new double[n];
        for (int i = 0; i < n; i++) xs[i] = (fx[i] - 0.5) * len;
        for (int i = 0; i + 1 < n; i++) {
            double x0 = xs[i], x1 = xs[i + 1];
            double w0 = hw[i] * beam / 2, w1 = hw[i + 1] * beam / 2;
            double s0 = sheer[i], s1 = sheer[i + 1];
            // Steuerbord (+z): von außen gesehen gegen den Uhrzeigersinn
            m.quad(new double[]{x0, keel, w0}, new double[]{x1, keel, w1}, new double[]{x1, s1, w1}, new double[]{x0, s0, w0}, sideMat, 0, 0, 0.7, 1);
            // Backbord (−z)
            m.quad(new double[]{x1, keel, -w1}, new double[]{x0, keel, -w0}, new double[]{x0, s0, -w0}, new double[]{x1, s1, -w1}, sideMat, 0, 0, 0.7, 1);
            // Deck
            double d0 = s0 - 0.08, d1 = s1 - 0.08;
            m.quad(new double[]{x0, d0, w0}, new double[]{x1, d1, w1}, new double[]{x1, d1, -w1}, new double[]{x0, d0, -w0}, deckMat, x0, 0, 1, 1);
        }
        // Heckspiegel
        double w0 = hw[0] * beam / 2;
        m.quad(new double[]{xs[0], keel, -w0}, new double[]{xs[0], keel, w0}, new double[]{xs[0], sheer[0], w0}, new double[]{xs[0], sheer[0], -w0}, sideMat, 0, 0, 0.7, 1);
        // Schanzkleid: schmale Leiste auf der Bordkante
        for (int i = 0; i + 1 < n; i++) {
            double x0 = xs[i], x1 = xs[i + 1];
            double a = hw[i] * beam / 2, b = hw[i + 1] * beam / 2;
            for (int side = -1; side <= 1; side += 2) {
                double i0 = a - 0.10, i1 = b - 0.10;
                if (i0 <= 0.05 || i1 <= 0.0) continue;
                double top0 = sheer[i] + 0.22, top1 = sheer[i + 1] + 0.22;
                if (side > 0) {
                    m.quad(new double[]{x0, sheer[i] - 0.08, a}, new double[]{x1, sheer[i + 1] - 0.08, b}, new double[]{x1, top1, b}, new double[]{x0, top0, a}, sideMat, 0, 0, 1, 1);
                    m.quad(new double[]{x0, top0, a}, new double[]{x1, top1, b}, new double[]{x1, top1, i1}, new double[]{x0, top0, i0}, sideMat, 0, 0, 1, 1);
                } else {
                    m.quad(new double[]{x1, sheer[i + 1] - 0.08, -b}, new double[]{x0, sheer[i] - 0.08, -a}, new double[]{x0, top0, -a}, new double[]{x1, top1, -b}, sideMat, 0, 0, 1, 1);
                    m.quad(new double[]{x1, top1, -i1}, new double[]{x0, top0, -i0}, new double[]{x0, top0, -a}, new double[]{x1, top1, -b}, sideMat, 0, 0, 1, 1);
                }
            }
        }
    }

    static MeshBuilder kahn(double len, double beam, int variant) {
        MeshBuilder m = new MeshBuilder();
        double[] fx = {0, 0.03, 0.2, 0.4, 0.6, 0.8, 0.92, 0.98, 1.0};
        double[] hw = {0.72, 0.95, 1.0, 1.0, 1.0, 0.98, 0.78, 0.38, 0.0};
        double[] sheer = {0.85, 0.72, 0.55, 0.46, 0.46, 0.55, 0.78, 1.15, 1.5};
        hull(m, len, beam, fx, hw, sheer, -0.9, Material.WOOD, Material.WOOD);
        Obj o = new Obj(m, 0, 0, 0);
        // Ladeluke
        o.box(-len * 0.12, -beam * 0.30, len * 0.18, beam * 0.30, 0.38, 0.95, Material.WOOD, 0.8);
        o.box(-len * 0.12 - 0.05, -beam * 0.30 - 0.05, len * 0.18 + 0.05, beam * 0.30 + 0.05, 0.93, 1.0, Material.WOOD, 1);
        // Achterhaus mit Ruderstand
        double ax0 = -len * 0.5 + 0.8, ax1 = ax0 + len * 0.16;
        o.box(ax0, -beam * 0.32, ax1, beam * 0.32, 0.4, 2.1, Material.PLASTER0 + 2, 0.7);
        o.box(ax0 - 0.15, -beam * 0.36, ax1 + 0.15, beam * 0.36, 2.1, 2.3, Material.ROOF_BROWN, 1);
        o.box(ax0 + 0.3, -beam * 0.331, ax0 + 1.0, -beam * 0.30, 1.1, 1.8, Material.PANE, 1);
        o.box(ax0 + 0.3, beam * 0.30, ax0 + 1.0, beam * 0.331, 1.1, 1.8, Material.PANE, 1);
        // Mast mit Baum und Segel, Segel hell-braun (Gaffelsegel)
        double mx = len * 0.20;
        m.cylinder(mx, 0.4, 0, 0.17, len * 0.62, 10, Material.WOOD, 0.9);
        double top = 0.4 + len * 0.62;
        o.box(mx - len * 0.34, -0.06, mx - 0.1, 0.06, 2.0, 2.15, Material.WOOD, 1);      // Baum
        o.box(mx - len * 0.30, -0.025, mx - 0.12, 0.025, 2.15, 2.15 + len * 0.40, Material.PLASTER0 + 1, 1);   // Segel
        o.box(mx - len * 0.22, -0.05, mx - 0.1, 0.05, 2.15 + len * 0.40, 2.25 + len * 0.40, Material.WOOD, 1);   // Gaffel
        // Ruderpinne
        o.box(-len * 0.5 - 0.5, -0.05, -len * 0.5 + 0.1, 0.05, 0.85, 0.95, Material.WOOD, 1);
        return m;
    }

    static MeshBuilder dampfer(double len, double beam) {
        MeshBuilder m = new MeshBuilder();
        double[] fx = {0, 0.04, 0.2, 0.45, 0.7, 0.88, 0.96, 1.0};
        double[] hw = {0.62, 0.9, 1.0, 1.0, 0.96, 0.7, 0.30, 0.0};
        double[] sheer = {1.5, 1.35, 1.25, 1.2, 1.3, 1.6, 2.0, 2.3};
        hull(m, len, beam, fx, hw, sheer, -1.3, Material.IRON, Material.WOOD);
        Obj o = new Obj(m, 0, 0, 0);
        // roter Wasserpass (nicht zu sehen), weiße Deckshäuser
        double b = beam;
        o.box(-len * 0.18, -b * 0.34, len * 0.20, b * 0.34, 1.2, 3.6, Material.PLASTER0, 0.7);
        o.box(-len * 0.20, -b * 0.37, len * 0.22, b * 0.37, 3.6, 3.8, Material.WOOD, 1);
        // Fensterband
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 0; i < 6; i++) {
                double u0 = -len * 0.16 + i * len * 0.06;
                o.box(u0, s > 0 ? b * 0.34 : -b * 0.34 - 0.03, u0 + len * 0.04, s > 0 ? b * 0.34 + 0.03 : -b * 0.34, 2.3, 3.2, Material.PANE_LIT, 1);
            }
        }
        // Brücke (Steuerhaus) vorn
        double bx = len * 0.20;
        o.box(bx, -b * 0.28, bx + len * 0.10, b * 0.28, 3.8, 5.3, Material.PLASTER0, 0.8);
        o.box(bx - 0.1, -b * 0.31, bx + len * 0.10 + 0.1, b * 0.31, 5.3, 5.5, Material.WOOD, 1);
        o.box(bx + len * 0.10 - 0.02, -b * 0.25, bx + len * 0.10 + 0.04, b * 0.25, 4.2, 5.0, Material.PANE_LIT, 1);
        // Schornstein, schwarz mit rotem Band, hinter dem Deckshaus
        double cx = -len * 0.05;
        m.cylinder(cx, 3.8, 0, 0.62, 3.2, 14, Material.IRON, 0.9);
        m.cylinder(cx, 5.4, 0, 0.66, 0.5, 14, Material.BRICK, 1);
        m.cylinder(cx, 7.0, 0, 0.68, 0.25, 14, Material.IRON, 1);
        // Masten
        m.cylinder(len * 0.36, 1.8, 0, 0.12, 9.0, 8, Material.WOOD, 0.9);
        m.cylinder(-len * 0.34, 1.2, 0, 0.12, 6.5, 8, Material.WOOD, 0.9);
        // Reling vorn
        o.box(len * 0.30, -b * 0.30, len * 0.45, -b * 0.30 + 0.05, 1.7, 2.6, Material.IRON, 1);
        o.box(len * 0.30, b * 0.30 - 0.05, len * 0.45, b * 0.30, 1.7, 2.6, Material.IRON, 1);
        // Rettungsboote am Deckshaus
        o.box(-len * 0.12, -b * 0.46, -len * 0.02, -b * 0.37, 3.7, 4.4, Material.WOOD, 0.8);
        o.box(-len * 0.12, b * 0.37, -len * 0.02, b * 0.46, 3.7, 4.4, Material.WOOD, 0.8);
        return m;
    }
}
