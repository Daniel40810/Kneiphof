package com.dan.kneiphof.tools;

import com.dan.kneiphof.camera.OrbitCamera;
import com.dan.kneiphof.gl.Cascades;
import com.dan.kneiphof.gl.SceneState;
import com.dan.kneiphof.world.MeshBuilder;
import com.dan.kneiphof.world.Testbed;
import com.dan.kneiphof.math.Mat4;
import com.dan.kneiphof.world.TerrainMesh;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * Prüfhilfe ohne Grafikkarte: schreibt das Gelände und die Werte für einige Bilder (Kamera, Sonne,
 * Belichtung) in Dateien. Ein kleines Python-Programm rechnet daraus mit denselben Shadern Bilder
 * unter Mesa. So lassen sich Shader und Szene prüfen, wo kein JOGL läuft.
 * <p>Aufruf: {@code FrameDump <ordner>}</p>
 */
public final class FrameDump {
    static double[] BR(int i) {
        com.dan.kneiphof.world.Bridges.Bridge b = BRIDGES.list.get(i);
        // Blick schräg über die Brücke: Ziel ist die Mitte, Kamera von der Seite
        return new double[]{b.cx, b.deck + 1.0, b.cz, (180.0 - Math.toDegrees(b.angle) + 720.0) % 360.0};
    }
    static final com.dan.kneiphof.world.Bridges BRIDGES = new com.dan.kneiphof.world.Bridges();
    static final com.dan.kneiphof.world.Boats BOATS = new com.dan.kneiphof.world.Boats();
    /** Schiff i: Lage x, z und eine Blickrichtung (Gier) von der Seite und leicht von hinten. */
    static double[] W(int i) {
        com.dan.kneiphof.world.Boats.Boat b = BOATS.list.get(i);
        double px = -b.hz, pz = b.hx;
        double ux = px * 0.45 - b.hx * 0.9, uz = pz * 0.45 - b.hz * 0.9;
        return new double[]{b.x, b.z, Math.toDegrees(Math.atan2(ux, uz))};
    }

    static double[] dw(double u, double w) {
        com.dan.kneiphof.world.Obj o = com.dan.kneiphof.world.Dom.frame(new com.dan.kneiphof.world.MeshBuilder());
        return new double[]{o.wx(u, w), o.wz(u, w)};
    }

    public static void main(String[] args) throws IOException {
        String dir = args.length > 0 ? args[0] : ".";
        long t0 = System.nanoTime();
        TerrainMesh tm = new TerrainMesh(640);
        System.out.printf(Locale.ROOT, "Gelände %d×%d, Mitte %.2f m, %.1f s%n", tm.n, tm.n, tm.centerSpacing(), (System.nanoTime() - t0) / 1e9);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(dir + "/terrain.bin")))) {
            ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(tm.verts.length).putInt(tm.indices.length);
            out.write(bb.array());
            ByteBuffer v = ByteBuffer.allocate(tm.verts.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (float f : tm.verts) v.putFloat(f);
            out.write(v.array());
            ByteBuffer ix = ByteBuffer.allocate(tm.indices.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int i : tm.indices) ix.putInt(i);
            out.write(ix.array());
        }
        MeshBuilder tb = Testbed.build();
        com.dan.kneiphof.world.Banks banks = new com.dan.kneiphof.world.Banks();
        tb.append(banks.build());
        com.dan.kneiphof.world.Bridges bridges = new com.dan.kneiphof.world.Bridges();
        tb.append(bridges.statics);
        double open = Double.parseDouble(System.getProperty("open", "0"));
        tb.append(bridges.bake(open));
        com.dan.kneiphof.world.Boats boats = BOATS;
        double bt = Double.parseDouble(System.getProperty("boattime", "0"));
        for (int i = 0; i < 4000 && bt > 0; i++) boats.update(bt / 4000, bt * i / 4000);
        boats.update(0, 0);
        tb.append(boats.bake());
        {
            com.dan.kneiphof.world.People ppl = new com.dan.kneiphof.world.People(bridges);
            float[] op = new float[7];
            java.util.Arrays.fill(op, (float) open);
            boolean[] nd = new boolean[10];
            for (int i = 0; i < 300; i++) ppl.update(0.1, op, nd);
            tb.append(ppl.bake());
            tb.append(new com.dan.kneiphof.world.Gulls().bake(40.0));
        }
        try (PrintWriter ww = new PrintWriter(dir + "/wakes.txt", "UTF-8")) {
            for (com.dan.kneiphof.world.Boats.Boat b : boats.list)
                ww.printf(Locale.ROOT, "%f %f %f %f %f %f %f %f%n", b.x, b.z, b.hx, b.hz, b.v, b.length, b.beam, b.name.startsWith("Dampfer") ? 1.0 : 0.55);
            for (com.dan.kneiphof.world.Boats.Boat b : boats.list)
                System.out.printf(Locale.ROOT, "%-10s bei (%.0f, %.0f) Richtung (%.2f, %.2f)%n", b.name, b.x, b.z, b.hx, b.hz);
        }
        for (com.dan.kneiphof.world.Bridges.Bridge br : bridges.list)
            System.out.printf(Locale.ROOT, "%-15s Mitte (%.0f, %.0f) Winkel %.0f° Ufer %.1f..%.1f Fahrbahn %.2f m%n", br.name, br.cx, br.cz, Math.toDegrees(br.angle), br.sMin, br.sMax, br.deck);
        com.dan.kneiphof.world.Town town = new com.dan.kneiphof.world.Town();
        town.reserveBridges(bridges);
        long t1 = System.nanoTime();
        if (System.getProperty("notown") == null) tb.append(town.build()); else town.build();
        System.out.printf(Locale.ROOT, "Stadt: %d Häuser (%d mit Fenstern), %.1f s%n", town.houses, town.detailed, (System.nanoTime() - t1) / 1e9);
        com.dan.kneiphof.world.Trees trees = new com.dan.kneiphof.world.Trees();
        trees.town = town;
        trees.plant(banks);
        // Bäume: Pflanzorte und Netze für jeden Tag, der in den Bildern vorkommt
        try (PrintWriter tw = new PrintWriter(dir + "/trees.txt", "UTF-8")) {
            for (com.dan.kneiphof.world.Trees.Spot s : trees.spots)
                tw.printf(Locale.ROOT, "%d %d %.3f %.3f %.3f %.4f %.4f%n", s.species, s.variant, s.x, s.y, s.z, s.yaw, s.scale);
        }
        for (int day : new int[]{172, 285}) {
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(dir + "/trees_" + day + ".bin")))) {
                for (com.dan.kneiphof.world.Trees.Mesh mm : trees.meshes(day, 0f)) {
                    ByteBuffer bb = ByteBuffer.allocate(20 + mm.verts.length * 4 + mm.indices.length * 4).order(ByteOrder.LITTLE_ENDIAN);
                    bb.putInt(mm.species).putInt(mm.variant).putInt(mm.lod).putInt(mm.verts.length).putInt(mm.indices.length);
                    for (float f : mm.verts) bb.putFloat(f);
                    for (int i : mm.indices) bb.putInt(i);
                    out.write(bb.array());
                }
            }
        }
        System.out.printf(Locale.ROOT, "Ufer %d Züge, Bäume %d%n", banks.lines.size(), trees.spots.size());
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(dir + "/testbed.bin")))) {
            float[] tv = tb.vertices();
            int[] ti = tb.indices();
            ByteBuffer bb = ByteBuffer.allocate(8 + tv.length * 4 + ti.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(tv.length).putInt(ti.length);
            for (float f : tv) bb.putFloat(f);
            for (int i : ti) bb.putInt(i);
            out.write(bb.array());
        }
        try (PrintWriter bw = new PrintWriter(dir + "/basin.txt", "UTF-8")) {
            bw.printf(Locale.ROOT, "%f %f %f %f%n", Testbed.BASIN_X, Testbed.BASIN_Z, Testbed.BASIN_HALF, Testbed.BASIN_WATER);
        }
        System.out.printf(Locale.ROOT, "Prüfstand %d Punkte, %d Dreiecke%n", tb.vertexCount(), tb.indexCount() / 3);
        double px = Testbed.CX, pz = Testbed.CZ, py = Testbed.GROUND + 2;
        // Bilder: Name, Tag, Stunde, Drehpunkt, Gier, Nick, Abstand
        SceneState sm = new SceneState();
        sm.clock.set(285, 18.3); sm.update(0);
        double MOONYAW = Math.toDegrees(Math.atan2(sm.clock.moonDir[0], -sm.clock.moonDir[2]));
        sm.clock.set(285, 21.5); sm.update(0);
        double MOONYAW2 = Math.toDegrees(Math.atan2(sm.clock.moonDir[0], -sm.clock.moonDir[2]));
        System.out.printf(Locale.ROOT, "Mond a %.1f° b %.1f° (%.0f %% / elev %.1f)%n", MOONYAW, MOONYAW2, sm.clock.moonLit * 100, sm.clock.moonElevationDeg);
        Object[][] shots = {
                {"uebersicht_mittag", 172, 12.0, -40.0, 4.0, 10.0, 320.0, 26.0, 1100.0},
                {"vormittag_nah", 172, 9.5, 0.0, 3.0, 0.0, 300.0, 12.0, 320.0},
                {"abend", 172, 21.0, 0.0, 3.0, 0.0, 100.0, 8.0, 700.0},
                {"oktober_morgen", 285, 8.0, 100.0, 3.0, -50.0, 200.0, 10.0, 600.0},
                {"nacht", 172, 23.8, 0.0, 3.0, 0.0, 300.0, 18.0, 900.0},
                {"von_oben", 172, 13.0, 300.0, 0.0, 0.0, 0.0, 85.0, 2600.0},
                {"daemmerung", 172, 21.6, 0.0, 3.0, 0.0, 120.0, 10.0, 800.0},
                {"haff_weit", 172, 16.0, -1500.0, 3.0, 100.0, 80.0, 14.0, 5000.0},
                {"pruefstand_vormittag", 172, 10.0, px, py + 2, pz, 150.0, 16.0, 38.0},
                {"pruefstand_abend", 172, 19.3, px, py + 2, pz, 160.0, 12.0, 34.0},
                {"pruefstand_oktober", 285, 14.5, px, py + 2, pz, 30.0, 22.0, 36.0},
                {"backstein_nah", 172, 15.0, px - 8.0, py + 2, pz - 3.0, 300.0, 8.0, 7.5},
                {"marmor_becken", 172, 11.0, px + 4.5, py, pz, 120.0, 28.0, 14.0},
                {"marmor_nah", 172, 16.5, px + 1.0, py - 1.0, pz - 6.0, 250.0, 12.0, 6.0},
                {"dom_linden", 172, 11.0, 170.0, 5.0, 0.0, 250.0, 20.0, 160.0},
                {"kupferhelm", 172, 18.0, px - 8.0, py + 10.5, pz - 3.0, 230.0, 2.0, 9.0},
                {"gegenlicht", 172, 19.6, px, py + 4, pz, 50.0, 4.0, 30.0},
                {"pruefstand_nacht", 172, 23.5, px, py + 2, pz, 150.0, 18.0, 40.0},
                {"kai_dom", 172, 17.5, 150.0, 3.0, 20.0, 160.0, 14.0, 150.0},
                {"kai_nah", 172, 10.5, -60.0, 1.5, -118.0, 160.0, 6.0, 26.0},
                {"lomse_weiden", 172, 18.0, 1700.0, 2.0, 0.0, 230.0, 14.0, 420.0},
                {"ufer_von_oben", 172, 14.0, 250.0, 0.0, 0.0, 10.0, 72.0, 1300.0},
                {"speicher_bollwerk", 285, 9.5, 600.0, 1.0, -190.0, 140.0, 9.0, 70.0},
                {"laternen_abend", 285, 18.6, 120.0, 2.0, 110.0, 300.0, 8.0, 120.0},
                {"dom_west", 172, 15.0, 150.0, 12.0, 44.0, 80.0, 9.0, 115.0},
                {"dom_sued", 172, 15.0, 125.0, 12.0, 44.0, 345.0, 16.0, 36.0},
                {"strasse", 172, 11.0, -40.0, 5.5, 10.0, 70.0, 4.0, 30.0},
                {"dom_abend", 172, 19.3, 150.0, 14.0, 44.0, 285.0, 8.0, 110.0},
                {"dom_nacht", 172, 23.2, 150.0, 14.0, 44.0, 285.0, 8.0, 110.0},
                {"quai_fenster", 285, 17.2, 10.0, 6.0, 120.0, 340.0, 6.0, 55.0},
                {"dom_flanke", 172, 12.0, 122.0, 12.0, 62.0, 262.0, 7.0, 42.0},
                {"dom_portal", 172, 17.0, 108.0, 8.0, 41.0, 85.0, 8.0, 32.0},
                {"dom_wand", 172, 13.0, 122.0, 8.0, 50.0, 355.0, 4.0, 9.0},
                {"dom_a", 172, 16.0, 150.0, 14.0, 44.0, 200.0, 12.0, 190.0},
                {"dom_b", 172, 16.0, 150.0, 14.0, 44.0, 290.0, 12.0, 190.0},
                {"dom_c", 172, 16.0, 150.0, 14.0, 44.0, 20.0, 12.0, 190.0},
                {"dom_d", 172, 16.0, 150.0, 14.0, 44.0, 110.0, 12.0, 190.0},
                {"innen_ost", 172, 12.0, dw(70, 0)[0], 5.7, dw(70, 0)[1], 265.0, 0.0, 55.0},
                {"innen_west", 172, 12.0, dw(30, 0)[0], 6.5, dw(30, 0)[1], 85.0, 0.0, 54.0},
                {"innen_schraeg", 172, 13.0, dw(60, 3)[0], 5.7, dw(60, 3)[1], 265.0, 6.0, 24.0},
                {"innen_tuer", 172, 13.0, dw(32.25, 10.6)[0], 5.7, dw(32.25, 10.6)[1], 175.0, 0.0, 14.0},
                {"aussen_tuer", 172, 15.0, dw(32.25, 11)[0], 6.5, dw(32.25, 11)[1], 355.0, 2.0, 16.0},
                {"innen_gewoelbe", 172, 12.0, dw(52, 0)[0], 12.0, dw(52, 0)[1], 265.0, -25.0, 15.0},
                {"innen_strahl_a", 172, 16.5, dw(70, 0)[0], 5.7, dw(70, 0)[1], 265.0, 0.0, 55.0},
                {"innen_strahl_b", 172, 8.5, dw(30, 0)[0], 6.5, dw(30, 0)[1], 85.0, 0.0, 54.0},
                {"innen_strahl_c", 285, 11.5, dw(60, 3)[0], 5.7, dw(60, 3)[1], 265.0, 6.0, 24.0},
                {"innen_nacht", 172, 23.0, dw(70, 0)[0], 5.7, dw(70, 0)[1], 265.0, 0.0, 55.0},
                {"innen_orgel", 172, 12.0, dw(18, 0)[0], 10.5, dw(18, 0)[1], 85.0, 8.0, 14.0},
                {"br_kraemer", 172, 14.0, BR(0)[0], BR(0)[1], BR(0)[2], BR(0)[3], 14.0, 70.0},
                {"br_schmiede", 172, 14.0, BR(1)[0], BR(1)[1], BR(1)[2], BR(1)[3], 14.0, 70.0},
                {"br_holz", 172, 15.0, BR(2)[0], BR(2)[1], BR(2)[2], BR(2)[3], 14.0, 70.0},
                {"br_gruene", 172, 15.0, BR(3)[0], BR(3)[1], BR(3)[2], BR(3)[3], 14.0, 70.0},
                {"br_koettel", 172, 15.0, BR(4)[0], BR(4)[1], BR(4)[2], BR(4)[3], 14.0, 70.0},
                {"br_honig", 172, 15.0, BR(5)[0], BR(5)[1], BR(5)[2], BR(5)[3], 14.0, 55.0},
                {"br_hohe", 172, 15.0, BR(6)[0], BR(6)[1], BR(6)[2], BR(6)[3], 14.0, 80.0},
                {"br_nah", 172, 14.0, BR(0)[0], BR(0)[1] , BR(0)[2], BR(0)[3] + 40.0, 6.0, 18.0},
                {"br_klappe", 172, 14.0, BR(1)[0], BR(1)[1] + 2.0, BR(1)[2], BR(1)[3] + 90.0, 8.0, 34.0},
                {"wasser_kahn", 172, 11.0, W(0)[0], 1.0, W(0)[1], W(0)[2], 4.5, 60.0},
                {"wasser_dampfer", 172, 16.0, W(2)[0], 1.0, W(2)[1], W(2)[2], 5.0, 90.0},
                {"wasser_ufer", 172, 15.0, BR(1)[0], 0.5, BR(1)[2], BR(1)[3] + 35.0, 3.5, 45.0},
                {"wasser_spiegel", 172, 10.0, 0.0, 0.5, -135.0, 250.0, 3.0, 90.0},
                {"wasser_np_a", 172, 15.0, -140.0, 0.5, -185.0, 80.0, 4.0, 110.0},
                {"wasser_np_b", 172, 15.0, -140.0, 0.5, -185.0, 285.0, 4.0, 110.0},
                {"wasser_ap_a", 172, 15.5, -20.0, 0.5, 158.0, 250.0, 4.0, 110.0},
                {"wasser_ap_b", 172, 9.0, 40.0, 0.5, 168.0, 100.0, 4.0, 110.0},
                {"wasser_ap_abend", 172, 20.5, -20.0, 0.5, 158.0, 250.0, 4.0, 110.0},
                {"nebel_morgen", 285, 6.9, 0.0, 0.5, -135.0, 250.0, 3.0, 90.0},
                {"nebel_dicht", 285, 8.5, 0.0, 0.5, -135.0, 250.0, 3.0, 90.0, 3},
                {"nebel_dom", 285, 8.0, 150.0, 20.0, 44.0, 200.0, 8.0, 380.0, 3},
                {"nebel_ueber", 285, 7.4, 60.0, 30.0, 0.0, 250.0, 12.0, 700.0, 2},
                {"wasser_abend", 172, 20.4, 0.0, 0.5, -135.0, 250.0, 3.0, 90.0},
                {"br_abend", 285, 18.3, BR(0)[0], BR(0)[1], BR(0)[2], BR(0)[3], 10.0, 60.0},
                {"rauch_stadt", 20, 9.0, 60.0, 15.0, 0.0, 215.0, 14.0, 260.0},
                {"mond_a", 285, 18.3, 0.0, 15.0, 0.0, MOONYAW, 10.0, 80.0},
                {"mond_b", 285, 21.5, 0.0, 15.0, 0.0, MOONYAW2, 10.0, 80.0},
                {"wetter_regen", 172, 14.0, 0.0, 3.0, 0.0, 300.0, 10.0, 420.0, 0, 1},
                {"wetter_bogen", 172, 18.6, 0.0, 3.0, 0.0, 275.0, 20.0, 500.0, 0, 2},
                {"wetter_nass_nah", 172, 17.0, BR(0)[0], BR(0)[1], BR(0)[2], BR(0)[3] + 20.0, 6.0, 22.0, 0, 2},
                {"moewen", 172, 15.0, -1.6, 31.5, -90.0, 130.0, -12.0, 4.5},
                {"moewen_nah", 172, 15.0, -1.6, 31.5, -90.0, 120.0, 10.0, 3.0},
                {"leute_kraemer", 172, 15.0, BR(0)[0], BR(0)[1] + 0.0, BR(0)[2], BR(0)[3] + 20.0, 6.0, 22.0},
                {"leute_schmiede", 172, 11.0, BR(1)[0], BR(1)[1], BR(1)[2], BR(1)[3], 8.0, 34.0},
                {"rauch_klar", 20, 11.0, 80.0, 12.0, 20.0, 200.0, 6.0, 180.0, 1},
                {"rauch_dom", 20, 8.6, 150.0, 20.0, 20.0, 150.0, 8.0, 350.0},
                {"rauch_dampfer", 172, 16.0, W(2)[0], 6.0, W(2)[1], W(2)[2], 8.0, 90.0},
                {"insel_oben", 172, 14.0, 20.0, 0.0, 20.0, 0.0, 80.0, 560.0},
                {"rathaus", 172, 12.0, -80.0, 8.0, 6.0, 339.0, 34.0, 85.0},
                {"rathaus_turm", 172, 14.0, -80.0, 22.0, 2.0, 300.0, 22.0, 80.0},
                {"gymnasium", 172, 12.0, 102.0, 8.0, -5.0, 339.0, 38.0, 80.0},
                {"gymnasium_schraeg", 172, 15.0, 102.0, 8.0, -5.0, 290.0, 40.0, 85.0},
                {"albertina", 172, 12.0, 170.0, 10.0, -3.0, 339.0, 38.0, 75.0},
                {"albertina_schraeg", 172, 16.0, 170.0, 10.0, -3.0, 70.0, 36.0, 75.0},
                {"boerse", 172, 12.0, -150.0, 10.0, 170.0, 180.0, 12.0, 85.0},
                {"boerse_schraeg", 172, 16.0, -150.0, 10.0, 170.0, 140.0, 16.0, 90.0},
                {"insel_schraeg", 172, 15.0, 30.0, 4.0, 20.0, 200.0, 24.0, 520.0},
        };
        {
            SceneState st0 = new SceneState();
            st0.clock.set(20, 9.0);
            st0.update(0);
            float[] pa = com.dan.kneiphof.gl.Particles.simulateForTest(town.chimneys, boats, new double[]{60, 20, 0}, st0.windVector(), 0.95, -1);
            double[] bp = W(2);
            float[] pb = com.dan.kneiphof.gl.Particles.simulateForTest(new java.util.ArrayList<>(), boats, new double[]{bp[0], 20, bp[1]}, st0.windVector(), 0.95, 2);
            float[] pd = new float[pa.length + pb.length];
            System.arraycopy(pa, 0, pd, 0, pa.length);
            System.arraycopy(pb, 0, pd, pa.length, pb.length);
            try (java.io.DataOutputStream o = new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(dir + "/particles.bin")))) {
                java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(4 + pd.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                bb.putInt(pd.length / 8);
                for (float f : pd) bb.putFloat(f);
                o.write(bb.array());
            }
            System.out.println("Rauchteilchen: " + pd.length / 8);
        }
        {
            com.dan.kneiphof.camera.Director dr = new com.dan.kneiphof.camera.Director();
            com.dan.kneiphof.camera.Director.Ctx cx = new com.dan.kneiphof.camera.Director.Ctx() {
                public int boatCount() { return BOATS.list.size(); }
                public double[] boatPose(int k) { com.dan.kneiphof.world.Boats.Boat b = BOATS.list.get(k); return new double[]{b.x, b.z, b.hx, b.hz}; }
                public boolean boatIsSteamer(int k) { return BOATS.list.get(k).name.startsWith("Dampfer"); }
                public int bridgeCount() { return BRIDGES.list.size(); }
                public double[] bridgePose(int k) { com.dan.kneiphof.world.Bridges.Bridge b = BRIDGES.list.get(k); return new double[]{b.cx, b.cz, b.angle, b.deck}; }
                public String bridgeName(int k) { return BRIDGES.list.get(k).name; }
            };
            java.util.List<Object[]> more = new java.util.ArrayList<>(java.util.Arrays.asList(shots));
            int[] pick = {0, 0, 1, 1, 2, 3, 3};
            double[] when = {0.2, 0.6, 0.12, 0.55, 0.5, 0.2, 0.9};
            for (int q = 0; q < pick.length; q++) {
                com.dan.kneiphof.camera.Director.Tour tr = dr.tour(pick[q]);
                double tt = tr.duration * when[q];
                double[] p = dr.poseAt(pick[q], tt, cx);
                System.out.printf(Locale.ROOT, "Fahrt %d t=%.0f: %.0f %.0f %.0f yaw %.0f pitch %.0f dist %.0f fov %.0f%n", pick[q], tt, p[0], p[1], p[2], p[3], p[4], p[5], p[6]);
                more.add(new Object[]{"tour" + pick[q] + "_" + q, tr.day, tr.hour, p[0], p[1], p[2], p[3], p[4], p[5]});
            }
            shots = more.toArray(new Object[0][]);
        }
        try (PrintWriter pw = new PrintWriter(dir + "/frames.txt", "UTF-8")) {
            for (Object[] s : shots) {
                SceneState st = new SceneState();
                st.clock.set((Integer) s[1], (Double) s[2]);
                if (s.length > 9) st.mistMode = (Integer) s[9];
                if (s.length > 10) st.weatherMode = (Integer) s[10];
                st.update(0);
                OrbitCamera cam = new OrbitCamera();
                cam.lookAt((Double) s[3], (Double) s[4], (Double) s[5], (Double) s[6], (Double) s[7], (Double) s[8]);
                cam.snap();
                float[] vp = Mat4.mul(cam.projection(16 / 9.0, false), cam.view());
                Cascades cs = new Cascades();
                double[] eye = {cam.ex, cam.ey, cam.ez};
                cs.update(eye, cam.forward(), cam.fovy, 16 / 9.0, st.clock.dir, cam.distance(), false);
                double[] w = st.windVector();
                if (((String) s[0]).startsWith("innen_strahl")) {
                    com.dan.kneiphof.gl.DomBeams db = new com.dan.kneiphof.gl.DomBeams();
                    db.rebuild(st.clock.dir);
                    java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(8 + db.verts.length * 4 + db.idx.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    bb.putInt(db.verts.length / 7).putInt(db.idx.length);
                    for (float f : db.verts) bb.putFloat(f);
                    for (int f : db.idx) bb.putInt(f);
                    java.nio.file.Files.write(java.nio.file.Paths.get(dir + "/beams_" + s[0] + ".bin"), bb.array());
                }
                StringBuilder sb = new StringBuilder();
                sb.append(s[0]);
                sb.append(" vp"); for (float f : vp) sb.append(' ').append(f);
                sb.append(" cam ").append(cam.ex).append(' ').append(cam.ey).append(' ').append(cam.ez);
                sb.append(" fwd"); for (double d : cam.forward()) sb.append(' ').append(d);
                sb.append(" lvp"); for (float[] m : cs.lightVP) for (float f : m) sb.append(' ').append(f);
                sb.append(" splits"); for (float f : cs.splits) sb.append(' ').append(f);
                sb.append(" texel"); for (float f : cs.texel) sb.append(' ').append(f);
                sb.append(" sun"); for (double d : st.clock.dir) sb.append(' ').append(d);
                sb.append(" suncol"); for (double d : st.sunRaw) sb.append(' ').append(d);
                sb.append(" moon"); for (double d : st.clock.moonDir) sb.append(' ').append(d);
                sb.append(" mooncol"); for (double d : st.moonRaw) sb.append(' ').append(d);
                sb.append(" stars ").append(st.stars / st.exposure).append(" night ").append(st.night);
                sb.append(" moonlit ").append(st.clock.moonLit);
                sb.append(" ext"); for (double d : st.extinction) sb.append(' ').append(d);
                sb.append(" exposure ").append(st.exposure);
                sb.append(" haze ").append(st.haze);
                sb.append(" mist ").append(st.mist);
                sb.append(" weather ").append(st.rain).append(' ').append(st.wet).append(' ').append(st.rainbow).append(' ').append(st.overcast);
                sb.append(" wind ").append(w[0]).append(' ').append(w[1]);
                sb.append(" flow ").append(-st.flowSpeed).append(' ').append(0.0);
                sb.append(" day ").append(st.clock.day());
                sb.append(" lamp ").append(com.dan.kneiphof.sky.Atmosphere.smooth(2, -3, st.clock.elevationDeg));
                sb.append(" sunel ").append(st.clock.elevationDeg).append(" sunaz ").append(st.clock.azimuthDeg);
                pw.println(sb);
                System.out.printf(Locale.ROOT, "%-18s Sonne %5.1f° / %5.1f°  Mond %5.1f° (%.0f %%)  Belichtung %.4f  Licht %.2f %.2f %.2f%n",
                        s[0], st.clock.elevationDeg, st.clock.azimuthDeg, st.clock.moonElevationDeg, st.clock.moonLit * 100,
                        st.exposure, st.sunRaw[0], st.sunRaw[1], st.sunRaw[2]);
            }
        }
    }
}
