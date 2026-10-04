package com.dan.kneiphof.world;

/**
 * Der Prüfstand für Licht und Material, auf der Lomse (die Stadt nimmt den Kneiphof ein). Er bleibt als Testbild erhalten und lässt sich ausblenden.
 * <ul>
 * <li>Pflasterplatz 70 × 50 m</li>
 * <li>Backsteinpfeiler 12 m mit Granitsockel, Rücksprung und Kupferhelm: Verband, Fugen, Schatten</li>
 * <li>Marmorblock auf Granit: Glanz und Spiegelung des Himmels</li>
 * <li>Marmorsäule mit Platte: glatte Rundung, Schatten über gekrümmte Fläche</li>
 * <li>Becken mit Marmorrand und Wasser: Wasser auf einer anderen Höhe als der Pregel</li>
 * <li>Eichenbalken auf Eisenstützen: Holz und Eisen für die Brücken</li>
 * </ul>
 */
public final class Testbed {
    /** Mitte des Prüfstands. */
    public static final double CX = 1200, CZ = 520;
    public static final double GROUND = 3.0;
    /** Becken: Mitte und halbe Innenweite, Wasserspiegel. */
    public static final double BASIN_X = CX + 8, BASIN_Z = CZ + 4, BASIN_HALF = 2.6, BASIN_WATER = GROUND + 0.62;

    private Testbed() { }

    public static MeshBuilder build() {
        MeshBuilder m = new MeshBuilder();
        double g = GROUND;
        // Pflaster: ein Platz, Oberkante 5 cm über dem Gelände
        m.box(CX - 35, g - 0.5, CZ - 25, CX + 35, g + 0.05, CZ + 25, Material.COBBLE, 1);
        double y = g + 0.05;

        // Backsteinpfeiler
        double px = CX - 8, pz = CZ - 3;
        m.box(px - 1.4, y, pz - 1.4, px + 1.4, y + 0.7, pz + 1.4, Material.GRANITE, 0.55);
        m.box(px - 1.2, y + 0.7, pz - 1.2, px + 1.2, y + 6.5, pz + 1.2, Material.BRICK, 0.7);
        m.box(px - 1.3, y + 6.5, pz - 1.3, px + 1.3, y + 6.8, pz + 1.3, Material.GRANITE, 1);   // Gesims
        m.box(px - 1.0, y + 6.8, pz - 1.0, px + 1.0, y + 11.0, pz + 1.0, Material.BRICK, 0.85);
        m.box(px - 1.1, y + 11.0, pz - 1.1, px + 1.1, y + 11.25, pz + 1.1, Material.GRANITE, 1);
        m.pyramid(px - 1.15, y + 11.25, pz - 1.15, px + 1.15, pz + 1.15, y + 14.5, Material.COPPER);

        // Marmorblock auf Granitplatte
        double mx = CX + 1, mz = CZ - 6;
        m.box(mx - 2.0, y, mz - 1.2, mx + 2.0, y + 0.35, mz + 1.2, Material.GRANITE, 0.6);
        m.box(mx - 1.6, y + 0.35, mz - 0.8, mx + 1.6, y + 1.6, mz + 0.8, Material.MARBLE, 0.75);

        // Marmorsäule
        double sx = CX - 2, sz = CZ + 5;
        m.box(sx - 0.65, y, sz - 0.65, sx + 0.65, y + 0.4, sz + 0.65, Material.GRANITE, 0.6);
        m.cylinder(sx, y + 0.4, sz, 0.42, 5.2, 40, Material.MARBLE, 0.8);
        m.box(sx - 0.6, y + 5.6, sz - 0.6, sx + 0.6, y + 5.85, sz + 0.6, Material.MARBLE, 1);

        // Becken: Rand aus Marmor, Boden dunkler Stein
        double bx = BASIN_X, bz = BASIN_Z, r = BASIN_HALF, w = 0.35, hgt = 0.75;
        m.box(bx - r - w, y, bz - r - w, bx + r + w, y + hgt, bz - r, Material.MARBLE, 0.7);
        m.box(bx - r - w, y, bz + r, bx + r + w, y + hgt, bz + r + w, Material.MARBLE, 0.7);
        m.box(bx - r - w, y, bz - r, bx - r, y + hgt, bz + r, Material.MARBLE, 0.7);
        m.box(bx + r, y, bz - r, bx + r + w, y + hgt, bz + r, Material.MARBLE, 0.7);
        m.box(bx - r, y, bz - r, bx + r, y + 0.08, bz + r, Material.BASIN_FLOOR, 1);

        // Eichenbalken auf zwei Eisenstützen
        double wx = CX + 6, wz = CZ - 9;
        m.box(wx - 3.2, y, wz - 0.12, wx - 2.95, y + 1.1, wz + 0.12, Material.IRON, 0.7);
        m.box(wx + 2.95, y, wz - 0.12, wx + 3.2, y + 1.1, wz + 0.12, Material.IRON, 0.7);
        m.box(wx - 3.6, y + 1.1, wz - 0.22, wx + 3.6, y + 1.42, wz + 0.22, Material.WOOD, 1);
        return m;
    }
}
