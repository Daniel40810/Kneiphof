// common.glsl — Grundbausteine für alle Shader der Kneiphof-Szene.
// Koordinaten: x Osten, y oben, z Süden, Meter; Wasserspiegel des Pregel bei y = 0.

const float PI = 3.14159265359;

// ------------------------------------------------------------ Zufall und Rauschen
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

// Wertrauschen 0..1
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i), b = hash12(i + vec2(1, 0)), c = hash12(i + vec2(0, 1)), d = hash12(i + vec2(1, 1));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p, int oct) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < oct; i++) { s += a * vnoise(p); p = p * 2.03 + vec2(17.1, 9.7); a *= 0.5; }
    return s;
}

// ------------------------------------------------------------ Himmelstabelle
// Die Himmelstabelle (sky LUT) speichert die Strahldichte des Himmels über dem Horizont:
// u = Richtung (0 Norden, im Uhrzeigersinn), v = Wurzel der Höhe (feiner am Horizont).
vec2 skyUV(vec3 d) {
    float az = atan(d.x, -d.z);
    float el = asin(clamp(d.y, 0.0, 1.0));
    return vec2(fract(az / (2.0 * PI)), sqrt(el / (0.5 * PI)));
}

vec3 skyDir(vec2 uv) {
    float az = uv.x * 2.0 * PI;
    float el = uv.y * uv.y * 0.5 * PI;
    return vec3(cos(el) * sin(az), sin(el), -cos(el) * cos(az));
}

// Mittleres Himmelslicht auf eine Fläche mit Normale n (grob: Zenit und Horizont gemischt).
vec3 skyIrradiance(sampler2D lut, vec3 n, vec3 sunDir) {
    vec3 zen = textureLod(lut, vec2(0.0, 0.98), 2.0).rgb;
    vec2 hz = vec2(fract(atan(n.x, -n.z) / (2.0 * PI)), 0.25);
    vec3 side = textureLod(lut, hz, 3.0).rgb;
    vec3 sideSun = textureLod(lut, vec2(fract(atan(sunDir.x, -sunDir.z) / (2.0 * PI)), 0.25), 3.0).rgb;
    vec3 hor = mix(side, sideSun, 0.35);
    float up = 0.5 + 0.5 * n.y;
    // Licht vom Boden zurückgeworfen (Albedo rund 0,15)
    vec3 bounce = 0.15 * zen * (1.0 - up);
    return PI * (mix(hor, zen, up) * up * 0.9 + bounce);
}

// ------------------------------------------------------------ Luftperspektive und Nebel
// Durchlässigkeit und eingestreutes Licht auf dem Weg dist (m) in Richtung dir.
uniform vec3 uExtinction;      // Auslöschung am Boden je Meter (Rayleigh + Dunst), RGB
uniform vec4 uWeather;         // Regen, Nässe, Regenbogen, Bedeckung (alle 0..1)
uniform vec4 uMist;            // Nebeldichte (0..1), Schichthöhe in m, Höhe des Auges (m), unbenutzt

// Optische Tiefe von exp(-|y|/H) entlang eines Strahls y = y0 + dy·t, t = 0..len (teilt am Wasserspiegel y = 0:
// die Spiegelung läuft mit einer Kamera unter dem Wasser und braucht dieselbe Dichte wie über ihm).
float mistOneSide(float y0, float dy, float len, float H) {
    // y0, y0+dy·len beide >= 0
    float k = dy / H;
    float e0 = exp(-y0 / H);
    return abs(k * len) > 1e-3 ? e0 * (1.0 - exp(-k * len)) / k : e0 * len;
}
float mistDepth(float y0, float dy, float len, float H) {
    float y1 = y0 + dy * len;
    if (y0 >= 0.0 && y1 >= 0.0) return mistOneSide(y0, dy, len, H);
    if (y0 <= 0.0 && y1 <= 0.0) return mistOneSide(-y0, -dy, len, H);
    float t = -y0 / dy;                       // Schnitt mit y = 0
    if (y0 > 0.0) return mistOneSide(y0, dy, t, H) + mistOneSide(0.0, -dy, len - t, H);
    return mistOneSide(-y0, -dy, t, H) + mistOneSide(0.0, dy, len - t, H);
}

vec3 aerial(vec3 color, vec3 dir, float dist, sampler2D lut) {
    // Am Ende des Umlands (16 km) ganz im Dunst, damit kein Rand zu sehen ist
    vec3 T = exp(-uExtinction * dist) * (1.0 - smoothstep(7000.0, 15500.0, dist));
    vec2 uv = skyUV(normalize(vec3(dir.x, max(dir.y, 0.0) + 0.02, dir.z)));
    vec3 inscat = textureLod(lut, uv, 1.0).rgb;
    vec3 res = color * T + inscat * (1.0 - T);
    if (uMist.x > 0.002) {
        // Flussnebel: Dichte nimmt mit der Höhe über dem Wasser ab (Schichthöhe uMist.y), Farbe wie der Dunst
        // in dieser Richtung, etwas weißer
        float od = uMist.x * 0.014 * mistDepth(uMist.z, dir.y, dist, uMist.y);
        float Tm = exp(-min(od, 24.0));
        vec3 mc = mix(inscat, vec3(dot(inscat, vec3(0.3333))), 0.30) * 1.05;
        res = res * Tm + mc * (1.0 - Tm);
    }
    return res;
}

// ------------------------------------------------------------ Farben
vec3 srgbToLinear(vec3 c) { return pow(c, vec3(2.2)); }
