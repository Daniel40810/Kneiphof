package com.dan.kneiphof.world;

import com.dan.forest.Season;
import com.dan.forest.Species;
import com.dan.forest.TreeGenerator;
import com.dan.forest.TreeMesh;
import com.dan.forest.TreeModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bäume für Königsberg um 1910, gebaut mit dem Wald-Paket aus Geyser ({@code com.dan.forest}):
 * Linden am Dom und auf den Kais, Rosskastanien in den Gärten am Stadtrand, Silberweiden an den
 * Wiesenufern der Lomse, Eichen, Buchen und Birken im Umland.
 * <p>
 * Je Art drei Varianten in drei Stufen (fein mit Blättern, grobe Büschel, Silhouette); die Grafikkarte
 * zeichnet sie vielfach an den Pflanzorten (Instanzen). Die Farben folgen dem Tag im Jahr; im Winter
 * sind die Laubbäume kahl.
 */
public final class Trees {
    public static final int VARIANTS = 3, LODS = 3;
    /** Punkte je Ecke: x y z  nx ny nz  r g b  Blatt(0/1)  Höhe relativ 0..1 */
    public static final int STRIDE = 11;

    public static final String[] NAMES = {"Linde", "Rosskastanie", "Silberweide", "Eiche", "Buche", "Birke"};

    /** Ein Pflanzort. */
    public static final class Spot {
        public final int species, variant;
        public final float x, y, z, yaw, scale;
        Spot(int species, int variant, double x, double y, double z, double yaw, double scale) {
            this.species = species; this.variant = variant;
            this.x = (float) x; this.y = (float) y; this.z = (float) z; this.yaw = (float) yaw; this.scale = (float) scale;
        }
    }

    /** Ein fertiges Netz (Art, Variante, Stufe) mit Ecken im Baumraum. */
    public static final class Mesh {
        public final int species, variant, lod;
        public final float[] verts;
        public final int[] indices;
        public final float height, radius;
        Mesh(int species, int variant, int lod, float[] verts, int[] indices, float height, float radius) {
            this.species = species; this.variant = variant; this.lod = lod;
            this.verts = verts; this.indices = indices; this.height = height; this.radius = radius;
        }
    }

    public final List<Spot> spots = new ArrayList<>();
    /** Gebäude der Stadt; dort wachsen keine Bäume. */
    public Town town;
    private final Species[] species = new Species[NAMES.length];
    private final TreeModel[][] models = new TreeModel[NAMES.length][VARIANTS];

    public Trees() {
        species[0] = linden();
        species[1] = chestnut();
        species[2] = willow();
        species[3] = Species.oak();
        species[4] = Species.beech();
        species[5] = Species.birch();
        for (int s = 0; s < species.length; s++)
            for (int v = 0; v < VARIANTS; v++) models[s][v] = TreeGenerator.grow(species[s], 1000L * s + 17 * v + 3, 1f);
    }

    public Species species(int i) { return species[i]; }

    // ------------------------------------------------------------------ Arten

    /** Winterlinde (Tilia cordata): dichte, gewölbte Krone, herzförmige Blätter; im Herbst gelb. */
    static Species linden() {
        Species s = Species.beech();
        Species l = new Species("Linde", "Tilia cordata");
        copy(s, l);
        l.heightMin = 16; l.heightMax = 24; l.crown = Species.Crown.OVOID; l.crownBase = 0.28f;
        l.leaf = Species.Leaf.ROUND; l.leafSize = 0.075f; l.leafAspect = 0.9f; l.leafDensity = 16;
        l.bark = new float[]{0.11f, 0.10f, 0.085f};
        l.spring = new float[]{0.20f, 0.40f, 0.07f}; l.summer = new float[]{0.06f, 0.17f, 0.035f};
        l.autumn = new float[][]{{0.62f, 0.50f, 0.06f}, {0.55f, 0.42f, 0.05f}};
        l.leafOut = 125; l.colorStart = 270; l.colorFull = 292; l.bare = 312;
        return l;
    }

    /** Rosskastanie (Aesculus hippocastanum): breite, runde Krone, große Blätter; früh im Laub, im Herbst braun. */
    static Species chestnut() {
        Species s = Species.oak();
        Species k = new Species("Rosskastanie", "Aesculus hippocastanum");
        copy(s, k);
        k.heightMin = 15; k.heightMax = 22; k.crown = Species.Crown.ROUND; k.trunkEnd = 0.7f;
        k.leaf = Species.Leaf.OVAL; k.leafSize = 0.16f; k.leafAspect = 0.45f; k.leafDensity = 11; k.cluster = 0.9f;
        k.bark = new float[]{0.10f, 0.08f, 0.065f};
        k.spring = new float[]{0.16f, 0.36f, 0.05f}; k.summer = new float[]{0.05f, 0.15f, 0.03f};
        k.autumn = new float[][]{{0.40f, 0.20f, 0.04f}, {0.50f, 0.32f, 0.06f}};
        k.leafOut = 110; k.colorStart = 255; k.colorFull = 280; k.bare = 300;
        return k;
    }

    /** Silberweide (Salix alba): locker, überhängend, Blätter unten silbrig; am Wasser. */
    static Species willow() {
        Species s = Species.birch();
        Species w = new Species("Silberweide", "Salix alba");
        copy(s, w);
        w.heightMin = 12; w.heightMax = 20; w.crown = Species.Crown.ROUND; w.trunkRatio = 0.03f; w.trunkEnd = 0.45f;
        w.trunkWobble = 0.12f; w.tropism = new float[]{0, 0.02f, -0.18f, -0.45f};
        w.leaf = Species.Leaf.SMALL; w.leafSize = 0.07f; w.leafAspect = 0.25f;
        w.bark = new float[]{0.14f, 0.12f, 0.10f}; w.barkMarks = 0.1f;
        w.spring = new float[]{0.24f, 0.40f, 0.12f}; w.summer = new float[]{0.14f, 0.22f, 0.11f};
        w.autumn = new float[][]{{0.48f, 0.45f, 0.10f}};
        w.leafOut = 105; w.colorStart = 285; w.colorFull = 305; w.bare = 325;
        return w;
    }

    static void copy(Species a, Species b) {
        for (java.lang.reflect.Field f : Species.class.getFields()) {
            if (java.lang.reflect.Modifier.isFinal(f.getModifiers()) || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            try {
                Object v = f.get(a);
                if (v instanceof float[]) v = ((float[]) v).clone();
                else if (v instanceof float[][]) {
                    float[][] o = (float[][]) v, c = new float[o.length][];
                    for (int i = 0; i < o.length; i++) c[i] = o[i].clone();
                    v = c;
                }
                f.set(b, v);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    // ------------------------------------------------------------------ Netze

    /** Alle Netze für den Tag im Jahr (Laubfarbe, Austrieb, Laubfall); Schnee 0..1. */
    public List<Mesh> meshes(int day, float snow) {
        List<Mesh> out = new ArrayList<>();
        for (int s = 0; s < species.length; s++)
            for (int v = 0; v < VARIANTS; v++)
                for (int lod = 0; lod < LODS; lod++)
                    out.add(mesh(s, v, lod, day, snow));
        return out;
    }

    Mesh mesh(int s, int v, int lod, int day, float snow) {
        TreeModel model = models[s][v];
        // Stufe 0 = fein (Blätter der Stufe 1 an Ästen der Stufe 1), 1 = grobe Büschel, 2 = Silhouette
        TreeMesh tm = lod == 0 ? TreeMesh.build(model, 1, 1) : lod == 1 ? TreeMesh.build(model, 2) : TreeMesh.silhouette(model, 6, 7);
        Season se = Season.of(species[s], day, snow);
        float[] col = new float[tm.nv * 3];
        se.colorize(tm, col);
        float H = model.height;
        float crownMid = H * (species[s].crownBase + (1 - species[s].crownBase) * 0.5f);
        // Laub: gefallene oder noch nicht ausgetriebene Blätter auf ihren Stiel zusammenziehen
        float[] scale = new float[tm.leafIds.length];
        for (int li = 0; li < scale.length; li++) scale[li] = se.leafScale(tm, li, 0);
        float clump = species[s].deciduous ? Math.min(1, se.foliage * (1 - se.drop) * 1.4f) : 1;
        float[] vt = new float[tm.nv * STRIDE];
        for (int i = 0; i < tm.nv; i++) {
            float x = tm.pos[3 * i], y = tm.pos[3 * i + 1], z = tm.pos[3 * i + 2];
            byte part = tm.part[i];
            boolean leaf = part == TreeMesh.LEAF || part == TreeMesh.NEEDLES || part == TreeMesh.CLUMP;
            int li = tm.leaf[i];
            if (li >= 0) {
                TreeModel.LeafSpot l = model.leaves.get(tm.leafIds[li]);
                float sc = scale[li];
                x = l.x + (x - l.x) * sc; y = l.y + (y - l.y) * sc; z = l.z + (z - l.z) * sc;
            } else if (part == TreeMesh.CLUMP && clump < 1) {
                // Büschel und Silhouette schrumpfen im Herbst zur Stammachse hin, im Winter bleibt nur der Stamm
                float k = Math.max(0.0f, clump);
                x *= k; z *= k; y = y * (0.4f + 0.6f * k);
            }
            int o = i * STRIDE;
            vt[o] = x; vt[o + 1] = y; vt[o + 2] = z;
            float nx = tm.nrm[3 * i], ny = tm.nrm[3 * i + 1], nz = tm.nrm[3 * i + 2];
            if (leaf) {
                // Laub wie eine Hülle beleuchten: Normale zur Mitte der Krone hin gebogen (weich statt facettiert)
                float cy = y - crownMid, rl = (float) Math.sqrt(x * x + cy * cy * 1.6f + z * z);
                if (rl > 1e-4f) {
                    float k = part == TreeMesh.CLUMP ? 0.85f : 0.55f;
                    nx = nx * (1 - k) + x / rl * k; ny = ny * (1 - k) + cy * 1.26f / rl * k; nz = nz * (1 - k) + z / rl * k;
                    float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    nx /= nl; ny /= nl; nz /= nl;
                }
            }
            vt[o + 3] = nx; vt[o + 4] = ny; vt[o + 5] = nz;
            vt[o + 6] = col[3 * i]; vt[o + 7] = col[3 * i + 1]; vt[o + 8] = col[3 * i + 2];
            vt[o + 9] = leaf ? 1 : 0;
            vt[o + 10] = Math.max(0, Math.min(1, y / H));
        }
        int[] idx = java.util.Arrays.copyOf(tm.tri, tm.nt * 3);
        return new Mesh(s, v, lod, vt, idx, H, model.crownRadius);
    }

    // ------------------------------------------------------------------ Pflanzen

    /** Verteilt die Bäume; braucht die Uferlinien (für Kaibäume und Weiden). */
    public void plant(Banks banks) {
        spots.clear();
        Random rnd = new Random(1910);
        // Linden um den Domplatz (Dom: Ostteil des Kneiphofs, etwa x 131..219, z −16..16)
        for (double x = 110; x <= 196; x += 12) {
            double axis = 44 + 0.0875 * (x - 150);
            addIf(0, x + rnd.nextGaussian() * 1.2, axis - 26 + rnd.nextGaussian(), rnd, true);
            addIf(0, x + rnd.nextGaussian() * 1.2, axis + 27 + rnd.nextGaussian(), rnd, true);
        }
        // Kaibäume: Linden in lockeren Reihen auf ausgewählten Kais
        for (Banks.Line L : banks.lines) {
            if (!L.quay) continue;
            double next = 6 + rnd.nextDouble() * 6;
            for (int k = 0; k < L.size(); k++) {
                if (L.s[k] < next) continue;
                next = L.s[k] + 13 + rnd.nextDouble() * 3;
                double x = L.x[k], z = L.z[k];
                boolean row = (Site.onKneiphof(x, z) && x > 20) || (z < -150 && x > 250 && x < 950) || (z > 140 && x > 280 && x < 700);
                if (!row) continue;
                addIf(0, x + L.nx[k] * 4.0, z + L.nz[k] * 4.0, rnd, true);
            }
        }
        // Weiden an den Wiesenufern der Lomse und flussauf
        for (Banks.Line L : banks.lines) {
            if (L.quay) continue;
            double next = rnd.nextDouble() * 20;
            for (int k = 0; k < L.size(); k++) {
                if (L.s[k] < next) continue;
                next = L.s[k] + 9 + rnd.nextDouble() * 24;
                double d = 3 + rnd.nextDouble() * 6;
                addIf(2, L.x[k] + L.nx[k] * d, L.z[k] + L.nz[k] * d, rnd, false);
            }
        }
        // Gärten am Stadtrand: Kastanien, Linden, Birken in Gruppen
        for (int i = 0; i < 2600; i++) {
            double x = (rnd.nextDouble() * 2 - 1) * 2400, z = (rnd.nextDouble() * 2 - 1) * 1800;
            float[] c = new float[4];
            Site.cover(x, z, c);
            double garden = c[0] > 0.05 && c[0] < 0.55 ? 1 : 0;
            if (garden == 0 || Site.noise(x * 0.01, z * 0.01) < 0.15) continue;
            int sp = rnd.nextDouble() < 0.45 ? 1 : rnd.nextDouble() < 0.6 ? 0 : 5;
            addIf(sp, x, z, rnd, false);
        }
        // Umland: Baumreihen an Feldwegen, Feldgehölze, Weiden auf der Lomse
        for (int i = 0; i < 30000; i++) {
            double x = (rnd.nextDouble() * 2 - 1) * 9000, z = (rnd.nextDouble() * 2 - 1) * 9000;
            if (Math.hypot(x - 80, z) < 1300) continue;
            boolean lomse = Site.onLomse(x, z) && x > 950;
            double grove = Site.noise(x * 0.004 + 31, z * 0.004 - 17);
            double row = Math.abs(Site.noise(x * 0.0015 + 5, z * 0.0015 + 9));
            boolean ok = lomse ? rnd.nextDouble() < 0.25 : (grove > 0.55 || row < 0.02);
            if (!ok) continue;
            int sp = lomse ? (rnd.nextDouble() < 0.7 ? 2 : 3) : (rnd.nextDouble() < 0.4 ? 3 : rnd.nextDouble() < 0.6 ? 4 : 5);
            addIf(sp, x, z, rnd, false);
        }
    }

    private void addIf(int sp, double x, double z, Random rnd, boolean city) {
        double w = Math.min(Site.water(x, z), Site.haff(x, z));
        if (w < (city ? 3.5 : 2.5)) return;
        if (Math.abs(x) > Site.FAR * 0.85 || Math.abs(z) > Site.FAR * 0.85) return;
        if (town != null && town.covers(x, z, 2.5)) return;
        // Prüfstand frei halten
        if (Math.abs(x - Testbed.CX) < 38 && Math.abs(z - Testbed.CZ) < 28) return;
        // Keine Bäume im Häusermeer (außer ausdrücklich gesetzten am Dom und auf Kais)
        if (!city) {
            float[] c = new float[4];
            Site.cover(x, z, c);
            if (c[0] > 0.6f) return;
        }
        for (Spot s : spots)
            if (Math.abs(s.x - x) < 5 && Math.abs(s.z - z) < 5) return;
        double y = Site.height(x, z);
        double scale = (sp == 2 ? 0.75 : 0.8) + rnd.nextDouble() * 0.3;
        spots.add(new Spot(sp, rnd.nextInt(VARIANTS), x, y - 0.05, z, rnd.nextDouble() * Math.PI * 2, scale));
    }
}
