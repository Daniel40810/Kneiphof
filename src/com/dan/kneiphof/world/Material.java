package com.dan.kneiphof.world;

/**
 * Nummern der Materialien. Die Shader in {@code materials.glsl} kennen dieselben Nummern und malen
 * die Oberfläche daraus, ohne Bilddateien: Verband, Adern, Maserung und Patina entstehen im Shader.
 */
public final class Material {
    public static final int BRICK = 1;        // Backstein, gotischer Verband, Kalkmörtel
    public static final int GRANITE = 2;      // Feldstein-Granit, grau-rötlich
    public static final int MARBLE = 3;       // weißer Marmor mit grauen Adern, poliert
    public static final int COPPER = 4;       // Kupfer mit grüner Patina
    public static final int WOOD = 5;         // Eiche, gebeizt und verwittert
    public static final int IRON = 6;         // Gusseisen, gestrichen
    public static final int COBBLE = 7;       // Feldsteinpflaster
    public static final int BASIN_FLOOR = 8;  // dunkler Stein unter Wasser
    public static final int PLASTER = 9;      // Putz
    public static final int QUAY = 10;        // Kaimauer: Granitquader, unten nass und veralgt
    public static final int LAMP = 11;        // Laternenglas, leuchtet nachts
    public static final int GLASS_G = 12;     // gotisches Bleiglas, dunkel
    public static final int GLASS_G_LIT = 13; // gotisches Glas, von innen erleuchtet
    public static final int PANE = 14;        // Sprossenfenster, dunkel
    public static final int PANE_LIT = 15;    // Sprossenfenster, Licht an
    public static final int ROOF = 16;        // Biberschwanz-Ziegel, rot
    public static final int SLATE = 17;       // Schiefer
    public static final int ROOF_BROWN = 18;  // Ziegel, nachgedunkelt
    public static final int FEATHER = 19;     // Möwengefieder, weiß
    public static final int PLASTER0 = 20;    // Putzfarben 20..25

    /** Innenräume: IN + Material, im Shader ohne Himmelslicht mit Raumlicht; IN_GLASS ist Tagesglas von innen. */
    public static final int IN = 40, IN_GLASS = 38;

    private Material() { }
}
