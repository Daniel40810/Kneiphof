// skylut.frag — rechnet die Himmelstabelle: Einfachstreuung in einer kugelförmigen Lufthülle
// (Rayleigh, Mie, Ozon), für Sonne und Mond. Läuft auf 256 × 128 Punkten, nicht je Bildpunkt.
#include "common.glsl"

in vec2 vUV;
out vec4 frag;

uniform vec3 uSunDir;
uniform vec3 uMoonDir;
uniform float uOvercast;     // Wolkendecke 0..1: der Himmel wird grau und dunkler
uniform float uMoonLit;      // beleuchteter Anteil der Mondscheibe 0..1
uniform float uSunPower;
uniform float uMie;          // Dunst: Mie-Streukoeffizient am Boden (1/m)
uniform float uMieG;         // Vorwärtsstreuung des Dunstes
uniform float uScale;        // Belichtung: alle Werte werden vorbelichtet gespeichert

const float RG = 6360e3, RT = 6420e3;
const vec3 BETA_R = vec3(5.802e-6, 13.558e-6, 33.1e-6);
const vec3 BETA_O = vec3(0.650e-6, 1.881e-6, 0.085e-6);
const float HR = 8000.0, HM = 1200.0;

float raySphere(vec3 o, vec3 d, float r) {
    float b = dot(o, d), c = dot(o, o) - r * r;
    float h = b * b - c;
    if (h < 0.0) return -1.0;
    return -b + sqrt(h);
}

vec3 densities(float h) {
    return vec3(exp(-h / HR), exp(-h / HM), max(0.0, 1.0 - abs(h - 25000.0) / 15000.0));
}

vec3 extinction(vec3 od) {
    return exp(-(BETA_R * od.x + uMie * 1.11 * od.y + BETA_O * od.z));
}

// Optische Tiefe vom Punkt p zur Lichtquelle; -1 wenn die Erde im Weg ist.
vec3 lightDepth(vec3 p, vec3 l) {
    float b = dot(p, l), c = dot(p, p) - RG * RG;
    if (b < 0.0 && b * b - c > 0.0) return vec3(-1.0);
    float len = raySphere(p, l, RT);
    vec3 od = vec3(0.0);
    const int N = 6;
    float ds = len / float(N);
    for (int i = 0; i < N; i++) {
        vec3 q = p + l * (float(i) + 0.5) * ds;
        od += densities(length(q) - RG) * ds;
    }
    return od;
}

vec3 scatter(vec3 dir, vec3 l, float power) {
    vec3 o = vec3(0.0, RG + 30.0, 0.0);
    float len = raySphere(o, dir, RT);
    // Schritte quadratisch verteilt: dicht bei der Kamera, wo der Dunst am Horizont das Licht macht
    const int N = 28;
    vec3 od = vec3(0.0), sumR = vec3(0.0), sumM = vec3(0.0);
    float t0 = 0.0;
    for (int i = 0; i < N; i++) {
        float t1 = len * pow(float(i + 1) / float(N), 2.0);
        float ds = t1 - t0;
        vec3 p = o + dir * (0.5 * (t0 + t1));
        t0 = t1;
        vec3 dens = densities(length(p) - RG) * ds;
        vec3 odHalf = od + 0.5 * dens;
        od += dens;
        vec3 odl = lightDepth(p, l);
        if (odl.x < 0.0) continue;
        vec3 T = extinction(odHalf + odl);
        sumR += dens.x * T;
        sumM += dens.y * T;
    }
    float mu = dot(dir, l);
    float pr = 3.0 / (16.0 * PI) * (1.0 + mu * mu);
    float g = uMieG;
    float pm = 3.0 / (8.0 * PI) * ((1.0 - g * g) * (1.0 + mu * mu)) / ((2.0 + g * g) * pow(1.0 + g * g - 2.0 * g * mu, 1.5));
    return power * (sumR * BETA_R * pr + sumM * uMie * pm);
}

void main() {
    vec3 dir = skyDir(vUV);
    dir.y = max(dir.y, 0.002);
    dir = normalize(dir);
    vec3 c = scatter(dir, uSunDir, uSunPower);
    // Mondlicht: Sonnenlicht, das der Mond zurückwirft (Albedo 0,12, Raumwinkel 6,4e-5 sr)
    c += scatter(dir, uMoonDir, uSunPower * 2.6e-6 * uMoonLit);
    // Nachthimmel ohne Mond: Leuchten der hohen Atmosphäre und Licht der Sterne, zum Horizont heller
    c += vec3(3.0e-8, 4.0e-8, 6.5e-8) * (1.0 + 1.5 * (1.0 - dir.y));
    float gl = dot(c, vec3(0.3, 0.55, 0.15));
    c = mix(c, vec3(gl) * vec3(0.95, 0.98, 1.02) * 0.55, uOvercast * 0.85);
    frag = vec4(c * uScale, 1.0);
}
