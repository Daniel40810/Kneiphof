package com.dan.kneiphof.world;

/**
 * Der Königsberger Dom in Backsteingotik, wie er 1910 stand: Westturm, dreischiffiger Bau mit steilem
 * Ziegeldach, Strebepfeiler zwischen hohen Spitzbogenfenstern, Treppengiebel im Osten und die kleine
 * Grabkapelle Kants an der Nordseite. Länge 88,5 m, Turm 50,75 m. Er steht am Ostende der Insel, der
 * Turm nach Westen.
 * <p>Der Bezug ist u = Längsachse nach Osten ab der Westwand des Turmes, w quer (+w nach Süden).</p>
 */
public final class Dom {
    public static final double L = 88.5;
    public static final double CX = 150, CZ = 44, ANGLE = Math.toRadians(5.0);
    public static final double GROUND = 3.0;

    private Dom() { }

    public static Obj frame(MeshBuilder m) {
        double cx = CX - Math.cos(ANGLE) * L / 2, cz = CZ - Math.sin(ANGLE) * L / 2;
        return new Obj(m, cx, cz, ANGLE);
    }

    /** Sperrfläche des Doms (Ecken im Uhrzeigersinn), damit keine Häuser hineinragen. */
    public static double[][] precinct(double margin) {
        Obj o = frame(new MeshBuilder());
        double u0 = -10 - margin, u1 = L + 8 + margin, w0 = -22 - margin, w1 = 22 + margin;
        return new double[][]{{o.wx(u0, w0), o.wz(u0, w0)}, {o.wx(u1, w0), o.wz(u1, w0)}, {o.wx(u1, w1), o.wz(u1, w1)}, {o.wx(u0, w1), o.wz(u0, w1)}};
    }

    public static MeshBuilder build() {
        MeshBuilder m = new MeshBuilder();
        Obj o = frame(m);
        double g = GROUND;
        double eave = g + 16.5, ridge = g + 30.5;
        double bu0 = 10, bu1 = L, bw = 11;

        // Sockel aus Granit rund um Turm und Schiff
        o.box(-0.5, -bw - 0.5, 14.5, bw + 0.5, g - 0.6, g + 0.9, Material.GRANITE, 0.5);
        o.box(bu0, -bw - 0.5, bu1 + 0.5, bw + 0.5, g - 0.6, g + 0.9, Material.GRANITE, 0.5);

        // Schiff
        DomInterior.shell(o, g, bu0, bu1, eave);
        o.gableRoof(bu0, -bw, bu1, bw, eave, ridge, 0.7, 0.0, Material.ROOF, Material.BRICK);
        // Traufgesims
        o.box(bu0 - 0.1, bw, bu1 + 0.1, bw + 0.35, eave - 0.5, eave, Material.GRANITE, 1);
        o.box(bu0 - 0.1, -bw - 0.35, bu1 + 0.1, -bw, eave - 0.5, eave, Material.GRANITE, 1);

        // Ostgiebel mit Stufen und blinden Spitzbögen
        o.stepGable(bu1 + 0.05, 0, 1, bw, eave, ridge - eave, 7, 1.0, Material.BRICK);
        for (int i = -1; i <= 1; i++) {
            double[][] outer = Obj.pointedArch(i * 5.4, g + 3.5, 3.0, 6.0, 5);
            o.fan(bu1, 0, 1, outer, 0.04, Material.GRANITE, 0.9);
            o.fan(bu1, 0, 1, Obj.pointedArch(i * 5.4, g + 3.6, 2.6, 5.9, 5), 0.09,
                    ((i + 3) % 2 == 0) ? Material.GLASS_G_LIT : Material.GLASS_G, 1);
        }
        for (int i = -2; i <= 2; i += 4)
            o.fan(bu1, 0, 1, Obj.pointedArch(i * 2.25, eave + 1.2, 1.2, 3.2, 4), 0.05, Material.BRICK, 0.35);

        // Strebepfeiler und Fenster beider Langseiten
        for (int k = 0; k <= 10; k++) {
            double uk = 16 + 6.5 * k;
            for (int side = -1; side <= 1; side += 2) {
                double w0 = side > 0 ? bw : -bw - 1.6, w1 = side > 0 ? bw + 1.6 : -bw;
                o.box(uk - 0.8, w0, uk + 0.8, w1, g + 0.9, g + 11.0, Material.BRICK, 0.7);
                o.box(uk - 0.9, w0 - (side > 0 ? 0 : 0.1), uk + 0.9, w1 + (side > 0 ? 0.1 : 0), g + 11.0, g + 11.3, Material.GRANITE, 1);
                double a0 = side > 0 ? bw : -bw - 0.9, a1 = side > 0 ? bw + 0.9 : -bw;
                o.box(uk - 0.7, a0, uk + 0.7, a1, g + 11.3, g + 16.9, Material.BRICK, 0.9);
                o.box(uk - 0.8, a0 - (side > 0 ? 0 : 0.1), uk + 0.8, a1 + (side > 0 ? 0.1 : 0), g + 16.9, g + 17.1, Material.GRANITE, 1);
                // Fenster in der Joch daneben
                double uc = uk + 3.25;
                if (uc > bu1 - 2.5) continue;
                int face = side > 0 ? 0 : 2;
                double ww = side > 0 ? bw : -bw;
                if (side > 0 && k == 2) { portal(o, uc, ww, 0, g); continue; }
                window(o, uc, ww, face, g + 5.0, 2.8, 7.0, k * 2 + (side > 0 ? 0 : 1));
            }
        }

        tower(o, g);

        // Dachreiter
        o.box(48.0, -1.1, 50.2, 1.1, ridge, ridge + 3.5, Material.COPPER, 1);
        o.pyramid(47.8, -1.3, 50.4, 1.3, ridge + 3.5, ridge + 11.0, Material.COPPER);

        // Grabkapelle Kants (1880), Neugotik, an der Nordseite des Chores
        Obj k = o.at(66, -15.8, Math.PI / 2);
        k.box(-3.2, -3.2, 3.2, 3.2, g - 0.4, g + 0.8, Material.GRANITE, 0.5);
        k.wallsOnly(-3.0, -3.0, 3.0, 3.0, g + 0.8, g + 6.8, Material.BRICK, 0.7);
        k.gableRoof(-3.0, -3.0, 3.0, 3.0, g + 6.8, g + 11.0, 0.4, 0.3, Material.SLATE, Material.BRICK);
        for (int f = 0; f < 4; f += 1) {
            if (f == 1) continue;       // diese Seite steht am Schiff
            k.fan(f == 0 || f == 2 ? 0 : (f == 1 ? 3 : -3), f == 0 ? 3.0 : (f == 2 ? -3.0 : 0), f,
                    Obj.pointedArch(0, g + 2.0, 1.5, 2.2, 5), 0.04, Material.GRANITE, 1);
            k.fan(f == 0 || f == 2 ? 0 : (f == 1 ? 3 : -3), f == 0 ? 3.0 : (f == 2 ? -3.0 : 0), f,
                    Obj.pointedArch(0, g + 2.1, 1.2, 2.1, 5), 0.08, Material.GLASS_G, 1);
        }
        return m;
    }

    /** Hohes Spitzbogenfenster mit Steingewände. uw/ww: Wandpunkt in der Fenstermitte. */
    private static void window(Obj o, double uw, double ww, int face, double y0, double wd, double hs, int seed) {
        o.fan(uw, ww, face, Obj.pointedArch(0, y0 - 0.25, wd + 0.7, hs + 0.25, 6), 0.03, Material.GRANITE, 0.9);
        boolean lit = (seed * 2654435761L >>> 7) % 7 == 0;
        o.fan(uw, ww, face, Obj.pointedArch(0, y0, wd, hs, 6), 0.08, lit ? Material.GLASS_G_LIT : Material.GLASS_G, 1);
        // Sohlbank
        double[] n = o.normal(face);
        Obj sill = new Obj(o.m, o.wx(uw, ww) + n[0] * 0.2, o.wz(uw, ww) + n[1] * 0.2, Math.atan2(-n[0], n[1]));
        sill.box(-(wd + 1.0) / 2, -0.25, (wd + 1.0) / 2, 0.25, y0 - 0.45, y0 - 0.25, Material.GRANITE, 1);
    }

    private static void portal(Obj o, double uw, double ww, int face, double g) {
        Obj st = new Obj(o.m, o.wx(uw, ww) + o.normal(face)[0] * 1.2, o.wz(uw, ww) + o.normal(face)[1] * 1.2,
                Math.atan2(-o.normal(face)[0], o.normal(face)[1]));
        st.box(-2.6, -1.4, 2.6, 1.4, g - 0.2, g + 0.25, Material.GRANITE, 0.6);
        st.box(-2.3, -0.7, 2.3, 0.7, g + 0.25, g + 0.5, Material.GRANITE, 0.8);
    }

    private static void tower(Obj o, double g) {
        double h = 39.0, w = 7.2, tu0 = 0, tu1 = 14.0;
        o.box(tu0 - 0.5, -w - 0.5, tu1 + 0.5, w + 0.5, g - 0.6, g + 1.2, Material.GRANITE, 0.5);
        o.wallsOnly(tu0, -w, tu1, w, g + 1.2, g + h, Material.BRICK, 0.7);
        // Gesimsbänder
        for (double y : new double[]{g + 14.0, g + 25.5, g + h - 1.2}) {
            o.box(tu0 - 0.3, -w - 0.3, tu1 + 0.3, w + 0.3, y, y + 0.45, Material.GRANITE, 1);
        }
        // Westportal und Fenster darüber
        o.fan(0, 0, 3, Obj.pointedArch(0, g + 0.9, 6.2, 5.0, 6), 0.03, Material.GRANITE, 0.9);
        o.fan(0, 0, 3, Obj.pointedArch(0, g + 0.9, 4.4, 4.6, 6), 0.08, Material.WOOD, 0.9);
        o.fan(0, 0, 3, Obj.pointedArch(0, g + 16.0, 3.5, 5.5, 6), 0.03, Material.GRANITE, 0.9);
        o.fan(0, 0, 3, Obj.pointedArch(0, g + 16.2, 2.8, 5.3, 6), 0.08, Material.GLASS_G, 1);
        // blinde Bögen im Mittelgeschoss, Schallöffnungen im Glockengeschoss
        for (int f = 0; f < 4; f++) {
            if (f == 1) continue;
            double uw = f == 3 ? 0 : f == 1 ? tu1 : 7.0;
            double ww = f == 0 ? w : f == 2 ? -w : 0;
            for (int i = -1; i <= 1; i += 2) {
                o.fan(uw, ww, f, Obj.pointedArch(i * 2.3, g + 27.0, 1.9, 5.4, 5), 0.04, Material.GRANITE, 0.9);
                o.fan(uw, ww, f, Obj.pointedArch(i * 2.3, g + 27.2, 1.5, 5.2, 5), 0.08, Material.GLASS_G, 0.6);
            }
            if (f != 3) {
                for (int i = -1; i <= 1; i += 2)
                    o.fan(uw, ww, f, Obj.pointedArch(i * 2.0, g + 17.0, 1.6, 5.0, 5), 0.04, Material.BRICK, 0.4);
            }
        }
        // Turmhelm aus Kupfer mit vier Eckerkern
        o.pyramid(tu0 - 0.5, -w - 0.5, tu1 + 0.5, w + 0.5, g + h, g + h + 9.0, Material.COPPER);
        o.box(6.0, -1.0, 8.0, 1.0, g + h + 9.0, g + h + 10.6, Material.COPPER, 1);
        o.pyramid(5.9, -1.1, 8.1, 1.1, g + h + 10.6, g + 50.75, Material.COPPER);
        for (int a = 0; a < 4; a++) {
            double cu = (a & 1) == 0 ? tu0 + 0.9 : tu1 - 0.9, cw = a < 2 ? -w + 0.9 : w - 0.9;
            o.box(cu - 0.9, cw - 0.9, cu + 0.9, cw + 0.9, g + h - 2.0, g + h + 3.2, Material.BRICK, 1);
            o.pyramid(cu - 1.0, cw - 1.0, cu + 1.0, cw + 1.0, g + h + 3.2, g + h + 7.4, Material.COPPER);
        }
    }
}
