// materials.glsl — Oberflächen ohne Bilddateien. Jede Funktion liefert Grundfarbe, Rauheit, Metallanteil
// und eine Normale im Tangentenraum (x entlang u, y entlang v, z aus der Fläche heraus).
// Flächenkoordinaten in Metern. fw: Meter je Bildpunkt; feine Muster blenden in der Ferne zum Mittel aus.

struct Surf {
    vec3 albedo;
    float rough;
    float metal;
    vec3 nT;
};

const int M_BRICK = 1, M_GRANITE = 2, M_MARBLE = 3, M_COPPER = 4, M_WOOD = 5, M_IRON = 6, M_COBBLE = 7, M_BASIN = 8, M_PLASTER = 9, M_QUAY = 10, M_LAMP = 11,
          M_GLASS_G = 12, M_GLASS_G_LIT = 13, M_PANE = 14, M_PANE_LIT = 15, M_ROOF = 16, M_SLATE = 17, M_ROOF_BROWN = 18, M_FEATHER = 19, M_PLASTER0 = 20, M_IN_GLASS = 38, M_IN = 40;

// ---------------------------------------------------------------- Backstein
// Klosterformat 28 × 13,5 × 9 cm, Fuge 1,2 cm, gotischer Verband: in jeder Schicht wechseln Läufer und
// Binder, die Schichten versetzt. Ein Teil der Binder ist dunkel überbrannt (wie am Königsberger Dom).
const float B_L = 0.28, B_H = 0.09, B_W = 0.135, B_J = 0.012;

// Höhe der Wand an (u, v): 1 auf dem Stein, 0 in der Fuge; dazu Kennung des Steins
float brickHeight(vec2 uv, out vec3 id) {
    float course = B_H + B_J;
    float row = floor(uv.y / course);
    float fy = uv.y - row * course;
    float period = B_L + B_W + 2.0 * B_J;
    float x = uv.x + mod(row, 2.0) * (B_W + B_J) * 1.5 + row * 0.07;
    float k = floor(x / period);
    float fx = x - k * period;
    float header = step(B_L + B_J, fx);
    float start = header > 0.5 ? B_L + B_J : 0.0;
    float len = header > 0.5 ? B_W : B_L;
    float lx = fx - start;
    id = vec3(k * 2.0 + header, row, header);
    float ex = min(lx, len - lx), ey = min(fy, B_H - fy);
    float e = min(ex, ey);
    // Kante leicht gerundet, Fuge zurückliegend
    return smoothstep(-0.001, 0.004, e) * (lx < len ? 1.0 : 0.0) * (fy < B_H ? 1.0 : 0.0);
}

Surf brick(vec2 uv, float fw) {
    Surf s;
    if (fw > 0.03) {                       // Ferne: Fugen sind kleiner als ein Pixel → Mittelwert, ohne Steinmuster
        s.albedo = mix(vec3(0.33, 0.13, 0.075), vec3(0.50, 0.48, 0.43), 0.18) * (0.85 + 0.25 * vnoise(uv * 0.7));
        s.rough = 0.85; s.metal = 0.0; s.nT = vec3(0.0, 0.0, 1.0);
        return s;
    }
    vec3 id;
    float h = brickHeight(uv, id);
    float r = hash12(id.xy + 0.37);
    float r2 = hash12(id.xy * 1.7 + 4.1);
    vec3 red = mix(vec3(0.36, 0.115, 0.06), vec3(0.50, 0.20, 0.10), r);
    red = mix(red, vec3(0.30, 0.16, 0.10), step(0.82, r2));                  // gelblich-braune Steine
    vec3 dark = mix(vec3(0.10, 0.06, 0.05), vec3(0.17, 0.08, 0.06), r);    // überbrannte Binder
    float isDark = id.z * step(0.45, r2);
    vec3 stone = mix(red, dark, isDark);
    // Verwitterung: Flecken und feine Körnung
    stone *= 0.82 + 0.3 * vnoise(uv * 14.0 + id.xy) * (1.0 - smoothstep(0.004, 0.02, fw));
    stone *= 0.85 + 0.25 * fbm(uv * 0.7, 3);
    vec3 mortar = vec3(0.50, 0.48, 0.43) * (0.8 + 0.3 * vnoise(uv * 40.0));
    float detail = 1.0 - smoothstep(0.006, 0.03, fw);
    vec3 avg = mix(vec3(0.33, 0.13, 0.075), mortar, 0.18);
    s.albedo = mix(avg, mix(mortar, stone, h), detail);
    s.rough = mix(0.85, mix(0.95, isDark > 0.5 ? 0.45 : 0.8, h), detail);
    s.metal = 0.0;
    // Normale aus der Höhe (Fuge 1 cm tief)
    vec3 tmp;
    float e = 0.004;
    float hx = brickHeight(uv + vec2(e, 0.0), tmp), hy = brickHeight(uv + vec2(0.0, e), tmp);
    vec2 g = vec2(hx - h, hy - h) / e * 0.010 * detail;
    s.nT = normalize(vec3(-g, 1.0));
    return s;
}

// ---------------------------------------------------------------- Marmor
Surf marble(vec3 p, float fw) {
    Surf s;
    vec2 q = p.xz * 0.5 + vec2(p.y * 0.35, -p.y * 0.2);
    float warp = fbm(q * 1.3, 4);
    float t = fbm(q * 0.8 + warp * 1.7 + vec2(p.y * 0.4), 5);
    float vein = 1.0 - smoothstep(0.0, 0.045, abs(sin((t * 7.0 + p.x * 0.35 + p.y * 0.6) * 3.14159)));
    float fine = 1.0 - smoothstep(0.0, 0.02, abs(sin((fbm(q * 3.0 + 9.0, 3) * 9.0) * 3.14159)));
    vec3 base = mix(vec3(0.80, 0.79, 0.76), vec3(0.86, 0.85, 0.82), vnoise(q * 4.0));
    vec3 c = mix(base, vec3(0.44, 0.45, 0.47), vein * 0.75);
    c = mix(c, vec3(0.60, 0.61, 0.62), fine * 0.35 * (1.0 - smoothstep(0.004, 0.02, fw)));
    s.albedo = c;
    s.rough = 0.11 + 0.05 * vein;
    s.metal = 0.0;
    s.nT = vec3(0.0, 0.0, 1.0);
    return s;
}

// ---------------------------------------------------------------- Granit (Sockel, Gesimse)
Surf granite(vec3 p, float fw) {
    Surf s;
    float detail = 1.0 - smoothstep(0.003, 0.02, fw);
    float a = vnoise(p.xz * 90.0 + p.y * 37.0), b = vnoise(p.zy * 140.0 + p.x * 51.0 + 3.0);
    vec3 c = vec3(0.36, 0.34, 0.33);
    c = mix(c, vec3(0.52, 0.40, 0.36), smoothstep(0.55, 0.75, a) * detail);  // Feldspat rötlich
    c = mix(c, vec3(0.08, 0.08, 0.09), smoothstep(0.72, 0.9, b) * detail);   // Glimmer schwarz
    c *= 0.85 + 0.2 * fbm(p.xz * 0.6 + p.y, 3);
    s.albedo = c;
    s.rough = 0.55;
    s.metal = 0.0;
    s.nT = vec3(0.0, 0.0, 1.0);
    return s;
}

// ---------------------------------------------------------------- Kupfer mit Patina
Surf copper(vec2 uv, float fw) {
    Surf s;
    float streak = vnoise(vec2(uv.x * 9.0, uv.y * 0.6));
    float blotch = fbm(uv * 2.5, 4);
    vec3 patina = mix(vec3(0.16, 0.38, 0.30), vec3(0.32, 0.55, 0.46), blotch);
    vec3 dark = vec3(0.12, 0.16, 0.12);
    s.albedo = mix(patina, dark, smoothstep(0.55, 0.85, streak) * 0.6);
    // Stehfalze der Kupferbahnen alle 60 cm
    float seam = 1.0 - smoothstep(0.0, 0.01, abs(fract(uv.x / 0.6) - 0.5) - 0.48);
    s.albedo *= 1.0 - 0.25 * seam * (1.0 - smoothstep(0.005, 0.03, fw));
    s.rough = 0.62;
    s.metal = 0.0;
    s.nT = normalize(vec3(seam * 0.15 * sign(fract(uv.x / 0.6) - 0.5), 0.0, 1.0));
    return s;
}

// ---------------------------------------------------------------- Eiche
Surf wood(vec2 uv, vec3 p, float fw) {
    Surf s;
    float grain = fbm(vec2(uv.x * 0.6, uv.y * 25.0 + p.z * 25.0), 4);
    float rings = fract(grain * 9.0);
    vec3 c = mix(vec3(0.20, 0.13, 0.075), vec3(0.32, 0.22, 0.13), smoothstep(0.2, 0.8, rings));
    c *= 0.8 + 0.3 * vnoise(uv * vec2(0.5, 3.0));
    s.albedo = mix(vec3(0.25, 0.17, 0.10), c, 1.0 - smoothstep(0.004, 0.02, fw));
    s.rough = 0.72;
    s.metal = 0.0;
    s.nT = vec3(0.0, 0.0, 1.0);
    return s;
}

// ---------------------------------------------------------------- Gusseisen, dunkelgrün gestrichen
Surf iron(vec2 uv, float fw) {
    Surf s;
    float rust = smoothstep(0.62, 0.8, fbm(uv * 6.0, 4));
    s.albedo = mix(vec3(0.035, 0.050, 0.042), vec3(0.20, 0.09, 0.04), rust);
    s.rough = mix(0.38, 0.8, rust);
    s.metal = 0.0;
    s.nT = vec3(0.0, 0.0, 1.0);
    return s;
}

// ---------------------------------------------------------------- Feldsteinpflaster
vec3 cobbleAlbedo(vec2 p, float fw) {
    vec2 q = p * 4.0;
    vec2 cell = floor(q), f = fract(q);
    float stone = hash12(cell);
    float joint = smoothstep(0.0, 0.12, min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y)));
    vec3 a = mix(vec3(0.17, 0.16, 0.15), vec3(0.29, 0.27, 0.24), stone);
    a *= mix(0.55, 1.0, joint);
    float detail = smoothstep(0.7, 0.2, fw * 4.0);
    return mix(vec3(0.20, 0.19, 0.175), a, detail) * (0.85 + 0.3 * fbm(p * 0.03, 3));
}

Surf cobble(vec2 p, float fw) {
    Surf s;
    s.albedo = cobbleAlbedo(p, fw);
    vec2 f = fract(p * 4.0) - 0.5;
    float detail = smoothstep(0.7, 0.2, fw * 4.0);
    s.nT = normalize(vec3(-f * 0.5 * detail, 1.0));
    s.rough = 0.8;
    s.metal = 0.0;
    return s;
}

// ---------------------------------------------------------------- Kaimauer
// Granitquader 1,1 × 0,55 m im Läuferverband, Fugen; nahe dem Wasser nass, darunter Algen, Kalkfahnen.
Surf quay(vec2 uv, vec3 p, float fw) {
    Surf s;
    float row = floor(uv.y / 0.55);
    float x = uv.x + mod(row, 2.0) * 0.55;
    vec2 cell = vec2(floor(x / 1.1), row);
    vec2 f = vec2(fract(x / 1.1) * 1.1, fract(uv.y / 0.55) * 0.55);
    float joint = smoothstep(0.0, 0.02, min(min(f.x, 1.1 - f.x), min(f.y, 0.55 - f.y)));
    float detail = 1.0 - smoothstep(0.01, 0.06, fw);
    float r = hash12(cell);
    vec3 c = mix(vec3(0.30, 0.29, 0.28), vec3(0.42, 0.38, 0.35), r);
    c *= 0.85 + 0.25 * vnoise(p.xz * 3.0 + p.y * 5.0) * detail;
    c = mix(vec3(0.33, 0.31, 0.30), c * mix(0.6, 1.0, joint), detail);
    // Wasser: nass bis 40 cm über dem Spiegel, Algen bis 15 cm, darüber Kalkfahnen und Rost von den Pollern
    float wet = 1.0 - smoothstep(0.05, 0.45 + 0.15 * vnoise(vec2(uv.x * 0.7, 0.0)), p.y);
    float algae = 1.0 - smoothstep(-0.05, 0.18, p.y);
    c = mix(c, c * 0.45, wet);
    c = mix(c, vec3(0.06, 0.09, 0.04), algae * 0.85);
    float streak = smoothstep(0.75, 0.95, vnoise(vec2(uv.x * 2.3, 1.7))) * smoothstep(-0.2, 1.5, p.y);
    c = mix(c, c * vec3(0.92, 0.95, 0.98) * 1.25, streak * 0.4);
    s.albedo = c;
    s.rough = mix(0.75, 0.35, wet);
    s.metal = 0.0;
    vec2 g = vec2(f.x < 0.03 ? 1.0 : (f.x > 1.07 ? -1.0 : 0.0), f.y < 0.03 ? 1.0 : (f.y > 0.52 ? -1.0 : 0.0));
    s.nT = normalize(vec3(-g * 0.3 * detail, 1.0));
    return s;
}


// ---------------------------------------------------------------- Putz in sechs Farben
const vec3 PLASTER_TINT[6] = vec3[6](vec3(0.56, 0.50, 0.38), vec3(0.52, 0.40, 0.22), vec3(0.38, 0.41, 0.34),
                                      vec3(0.52, 0.33, 0.26), vec3(0.62, 0.60, 0.54), vec3(0.34, 0.38, 0.43));

Surf plasterTint(int k, vec2 uv, vec3 p, float fw) {
    Surf s;
    vec3 c = PLASTER_TINT[clamp(k, 0, 5)];
    float stain = fbm(vec2(p.x + p.z, p.y * 0.45) * 0.45, 4);
    c *= 0.74 + 0.4 * stain;
    float soot = 1.0 - smoothstep(3.0, 5.5, p.y);                 // Spritzwasser und Schmutz am Fuß
    c *= 1.0 - 0.3 * soot * (0.5 + 0.5 * vnoise(vec2(p.x + p.z, 0.0) * 1.3));
    float grain = vnoise(uv * 38.0) * (1.0 - smoothstep(0.01, 0.05, fw));
    c *= 0.9 + 0.2 * grain;
    s.albedo = c;
    s.rough = 0.92;
    s.metal = 0.0;
    s.nT = vec3(0.0, 0.0, 1.0);
    return s;
}

// ---------------------------------------------------------------- Fensterglas
// 1 auf dem Glas, 0 auf Blei oder Sprosse. Gotisch: Rautenbleiung, Pfosten alle 1,4 m; Haus: Mittelpfosten.
float glassMask(int id, vec2 uv) {
    if (id == M_GLASS_G || id == M_GLASS_G_LIT) {
        float a = abs(fract((uv.x + uv.y) / 0.34 + 0.5) - 0.5) * 0.34;
        float b = abs(fract((uv.x - uv.y) / 0.34 + 0.5) - 0.5) * 0.34;
        float post = abs(fract(uv.x / 1.4 + 0.5) - 0.5) * 1.4;
        return smoothstep(0.006, 0.014, min(a, b)) * smoothstep(0.03, 0.045, post);
    }
    float ax = abs(uv.x);
    return smoothstep(0.018, 0.03, ax);
}

Surf glass(int id, vec2 uv, vec3 p, float fw) {
    Surf s;
    float m = glassMask(id, uv);
    vec2 cell = floor(uv * 3.0);
    float h = hash12(cell);
    vec3 pane = mix(vec3(0.014, 0.020, 0.030), vec3(0.030, 0.034, 0.040), h);
    bool gothic = id == M_GLASS_G || id == M_GLASS_G_LIT;
    if (gothic) pane = mix(pane, vec3(0.05, 0.03, 0.025), step(0.85, h));
    vec3 frame = gothic ? vec3(0.035, 0.035, 0.04) : vec3(0.55, 0.53, 0.48);
    s.albedo = mix(frame, pane, m);
    s.rough = mix(0.5, 0.07, m);
    s.metal = 0.0;
    s.nT = vec3(0.0, 0.0, 1.0);
    return s;
}

// ---------------------------------------------------------------- Dachziegel und Schiefer
// uv.x längs der Traufe, uv.y die Dachschräge hinauf, beides in Metern.
Surf roofTile(int id, vec2 uv, float fw) {
    Surf s;
    bool slate = id == M_SLATE;
    if (fw > 0.10) {                       // Ferne: Mittelfarbe
        vec3 b = slate ? vec3(0.078, 0.082, 0.092) : id == M_ROOF_BROWN ? vec3(0.20, 0.10, 0.07) : vec3(0.27, 0.105, 0.06);
        s.albedo = b * 0.78 * (0.72 + 0.4 * vnoise(uv * 0.45));
        s.rough = 0.82; s.metal = 0.0; s.nT = vec3(0.0, 0.0, 1.0);
        return s;
    }
    float rowH = slate ? 0.24 : 0.165, colW = slate ? 0.20 : 0.15;
    float row = floor(uv.y / rowH);
    float x = uv.x + mod(row, 2.0) * colW * 0.5;
    float col = floor(x / colW);
    vec2 f = vec2(fract(x / colW), fract(uv.y / rowH));
    float r = hash12(vec2(col, row) + 3.7);
    vec3 base;
    if (slate) base = mix(vec3(0.050, 0.055, 0.065), vec3(0.105, 0.110, 0.120), r);
    else if (id == M_ROOF_BROWN) base = mix(vec3(0.150, 0.075, 0.055), vec3(0.250, 0.125, 0.085), r);
    else base = mix(vec3(0.200, 0.075, 0.045), vec3(0.340, 0.135, 0.075), r);
    base *= 0.72 + 0.4 * fbm(uv * 0.45, 3);                              // Ruß, Moos, Bleiche
    float lap = smoothstep(0.0, 0.22, f.y);                              // Überlappung: unten hell, oben im Schatten
    float gap = smoothstep(0.0, 0.07, min(f.x, 1.0 - f.x));
    float detail = 1.0 - smoothstep(0.02, 0.10, fw);
    vec3 avg = base * 0.78;
    s.albedo = mix(avg, base * (0.5 + 0.5 * lap) * (0.7 + 0.3 * gap), detail);
    s.rough = 0.82;
    s.metal = 0.0;
    s.nT = normalize(vec3(0.0, (0.5 - f.y) * 0.5 * detail, 1.0));
    return s;
}

Surf material(int id, vec2 uv, vec3 p, float fw) {
    if (id == M_BRICK) return brick(uv, fw);
    if (id == M_MARBLE) return marble(p, fw);
    if (id == M_GRANITE) return granite(p, fw);
    if (id == M_COPPER) return copper(uv, fw);
    if (id == M_WOOD) return wood(uv, p, fw);
    if (id == M_IRON) return iron(uv, fw);
    if (id == M_COBBLE) return cobble(p.xz, fw);
    if (id == M_QUAY) return quay(uv, p, fw);
    if (id >= M_GLASS_G && id <= M_PANE_LIT) return glass(id, uv, p, fw);
    if (id >= M_ROOF && id <= M_ROOF_BROWN) return roofTile(id, uv, fw);
    if (id >= M_PLASTER0 && id < M_PLASTER0 + 6) return plasterTint(id - M_PLASTER0, uv, p, fw);
    Surf s;
    s.nT = vec3(0.0, 0.0, 1.0);
    s.metal = 0.0;
    if (id == M_FEATHER) { s.albedo = vec3(0.80, 0.80, 0.78) * (0.92 + 0.12 * vnoise(uv * 40.0)); s.rough = 0.8; return s; }
    if (id == M_LAMP) { s.albedo = vec3(0.10, 0.09, 0.07); s.rough = 0.1; return s; }
    if (id == M_BASIN) { s.albedo = vec3(0.30, 0.32, 0.31) * (0.85 + 0.3 * vnoise(uv * 3.0)); s.rough = 0.6; return s; }
    s.albedo = vec3(0.55, 0.50, 0.42) * (0.9 + 0.15 * fbm(uv * 2.0, 3));
    s.rough = 0.9;
    return s;
}
