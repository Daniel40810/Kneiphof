// terrain.frag — Boden: Pflaster der Stadt, Wiese, nasse Wiese der Lomse, Uferschlamm.
// Licht: Sonne mit Schattenkaskaden, Mond, Himmelslicht, Luftperspektive.
#include "common.glsl"
#include "materials.glsl"
#include "lighting.glsl"
#include "shadow.glsl"

in vec3 vPos;
in vec3 vNormal;
in vec4 vCover;
out vec4 frag;

uniform vec3 uCamPos;

vec3 grassAlbedo(vec2 p) {
    float n = fbm(p * 0.08, 4);
    float m = vnoise(p * 0.9);
    vec3 a = mix(vec3(0.060, 0.105, 0.030), vec3(0.115, 0.140, 0.045), n);
    return a * (0.85 + 0.3 * m);
}

vec3 wetAlbedo(vec2 p) {
    float n = fbm(p * 0.05 + 3.0, 4);
    vec3 a = mix(vec3(0.055, 0.085, 0.040), vec3(0.120, 0.115, 0.060), n);  // Riedgras, im Sommer gelblich
    return a * (0.8 + 0.35 * vnoise(p * 1.7));
}

// Platzhalter bis Phase 4: die Stadt von oben als Dachlandschaft. Straßenblöcke, darin schmale
// Grundstücke mit roten Ziegeldächern, braunen und grauen Dächern, hier und da ein Hof.
vec3 cityAlbedo(vec2 p) {
    float ang = 0.5 * (vnoise(p * 0.0012 + 5.0) - 0.5);
    mat2 R = mat2(cos(ang), -sin(ang), sin(ang), cos(ang));
    vec2 q = R * p;
    vec2 size = vec2(72.0, 46.0);
    vec2 blk = floor(q / size), f = q - blk * size;
    float street = 7.0;
    float dEdge = min(min(f.x, size.x - f.x), min(f.y, size.y - f.y));
    float fw = length(fwidth(q));
    float isStreet = 1.0 - smoothstep(street * 0.5 - fw, street * 0.5 + fw, dEdge);
    // Grundstücke entlang der langen Seite
    float bw = 9.0 + 5.0 * hash12(blk + 3.1);
    float side = step(size.y * 0.5, f.y);                      // vordere oder hintere Häuserzeile
    float pid = floor(f.x / bw);
    float h = hash12(blk * 7.0 + vec2(pid, side * 13.0));
    vec3 roof = h < 0.50 ? vec3(0.24, 0.115, 0.08) : h < 0.78 ? vec3(0.19, 0.12, 0.09) : h < 0.985 ? vec3(0.12, 0.115, 0.115) : vec3(0.10, 0.17, 0.14);
    // verwittert: Ziegel nachgedunkelt, Moos, Ruß
    roof = mix(roof, vec3(0.13, 0.12, 0.10), 0.35 * hash12(blk + vec2(pid * 3.0, side)));
    roof *= 0.85 + 0.25 * hash12(blk + vec2(pid * 5.0, side + 2.0));
    // Dachneigung: zwei Seiten je Zeile, leicht verschieden hell
    float depth = size.y * 0.5 - street * 0.5;
    float yIn = side > 0.5 ? (size.y - street * 0.5 - f.y) : (f.y - street * 0.5);
    float ridge = step(depth * 0.35, yIn);
    roof *= mix(0.88, 1.08, ridge);
    // Hof in der Blockmitte
    float yard = step(depth * 0.70, yIn) * step(0.35, hash12(blk + vec2(pid, 7.0 + side)));
    vec3 yardC = mix(vec3(0.16, 0.14, 0.11), vec3(0.08, 0.12, 0.05), hash12(blk + pid));
    vec3 a = mix(roof, yardC, yard);
    // Fugen zwischen Grundstücken
    float gap = 1.0 - (1.0 - smoothstep(0.0, 0.6 + fw, mod(f.x, bw))) * 0.5;
    a *= gap;
    float avgOK = smoothstep(14.0, 4.0, fw);                   // sehr fern: Mittelwert
    vec3 avg = vec3(0.21, 0.13, 0.10);
    a = mix(avg, a, avgOK);
    return mix(a, cobbleAlbedo(p, length(fwidth(p))), isStreet);
}

// Ab hier stehen Häuser auf dem Boden: nur Pflaster, Gassen und Höfe
vec3 townGround(vec2 p) {
    float fw = length(fwidth(p));
    vec3 c = cobbleAlbedo(p, fw);
    float dirt = smoothstep(0.55, 0.8, fbm(p * 0.09 + 5.0, 4));
    return mix(c, vec3(0.13, 0.115, 0.095) * (0.8 + 0.4 * vnoise(p * 0.7)), dirt * 0.65);
}

vec3 mudAlbedo(vec2 p) {
    return vec3(0.075, 0.065, 0.048) * (0.8 + 0.4 * vnoise(p * 0.6));
}

void main() {
    vec3 n = normalize(vNormal);
    vec2 p = vPos.xz;
    vec4 w = vCover / max(1e-4, dot(vCover, vec4(1.0)));
    // Nur rechnen, was gebraucht wird
    vec3 albedo = vec3(0.0);
    if (w.x > 0.01) {
        float inTown = 1.0 - smoothstep(860.0, 1010.0, length(p - vec2(60.0, 30.0)));
        vec3 far = inTown < 0.999 ? cityAlbedo(p) : vec3(0.0);
        vec3 near = inTown > 0.001 ? townGround(p) : vec3(0.0);
        albedo += w.x * mix(far, near, inTown);
    }
    if (w.y > 0.01) albedo += w.y * grassAlbedo(p);
    if (w.z > 0.01) albedo += w.z * wetAlbedo(p);
    if (w.w > 0.01) albedo = mix(albedo, mudAlbedo(p), clamp(vCover.w, 0.0, 1.0));
    // Unter Wasser: Flussgrund
    if (vPos.y < -0.2) albedo = vec3(0.05, 0.05, 0.035);

    Surf sf;
    sf.albedo = albedo;
    sf.rough = vPos.y < -0.2 ? 0.7 : 0.92;
    sf.metal = 0.0;
    sf.nT = vec3(0.0, 0.0, 1.0);
    float sv = sunShadow(vPos, n, uCamPos, gl_FragCoord.xy);
    vec3 V = normalize(uCamPos - vPos);
    vec3 c = shadeSurface(sf, n, V, 1.0, sv);

    vec3 v = vPos - uCamPos;
    float dist = length(v);
    c = aerial(c, v / dist, dist, uSkyLut);
    frag = vec4(c, 1.0);
}
