// shafts.frag — Lichtstrahlen: Streuung im Dunst und Nebel entlang jedes Sehstrahls, mit Sichtbarkeit der
// Sonne aus den Schattenkarten (ein Vergleich je Schritt). Halbe Auflösung, 28 Schritte mit Versatz je Bildpunkt.
// Ergebnis wird additiv auf das Bild gelegt.
#include "common.glsl"
#include "shadow.glsl"

in vec2 vUV;
out vec4 frag;

uniform sampler2D uDepth;
uniform mat4 uInvViewProj;
uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform float uHaze;          // Mie-Streukoeffizient am Boden (1/m)
uniform vec2 uFull;           // Größe des vollen Bildes

float visAt(vec3 p, float d) {
    int c = d < uSplits.x ? 0 : (d < uSplits.y ? 1 : 2);
    if (d >= uSplits.z) return 1.0;
    vec4 lp = uLightVP[c] * vec4(p, 1.0);
    vec3 q = lp.xyz / lp.w;
    vec2 uv = q.xy * 0.5 + 0.5;
    float z = uZeroOne == 1 ? q.z : q.z * 0.5 + 0.5;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0))) || z > 1.0) return 1.0;
    return texture(uShadow, vec4(uv, float(c), z - 0.0006 * float(c + 1)));
}

float hg(float cs, float g) {
    return (1.0 - g * g) / (4.0 * PI * pow(1.0 + g * g - 2.0 * g * cs, 1.5));
}

void main() {
    ivec2 ip = ivec2(vUV * uFull);
    float zb = texelFetch(uDepth, ip, 0).r;
    bool bg = uZeroOne == 1 ? zb < 1e-6 : zb > 0.999999;
    float zn = uZeroOne == 1 ? max(zb, 1e-7) : zb * 2.0 - 1.0;
    vec4 a = uInvViewProj * vec4(vUV * 2.0 - 1.0, bg ? (uZeroOne == 1 ? 1e-6 : 0.9999) : zn, 1.0);
    vec3 target = a.xyz / a.w;
    vec3 dir = normalize(target - uCamPos);
    float maxLen = min(uSplits.z, 700.0);
    float len = bg ? maxLen : min(distance(target, uCamPos), maxLen);
    const int N = 28;
    float ds = len / float(N);
    float jit = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float cs = dot(dir, uSunDir);
    float phase = 0.7 * hg(cs, 0.62) + 0.3 / (4.0 * PI);
    float acc = 0.0, T = 1.0;
    for (int i = 0; i < N; i++) {
        float t = (float(i) + jit) * ds;
        vec3 p = uCamPos + dir * t;
        float sigma = uHaze * exp(-max(p.y, 0.0) / 1200.0) + uMist.x * 0.014 * exp(-abs(p.y) / uMist.y);
        float vis = visAt(p, t * dot(dir, uCamFwd));
        acc += vis * sigma * T * ds;
        T *= exp(-sigma * ds);
    }
    vec3 L = uSunColor * (phase * acc);
    frag = vec4(min(L, vec3(30.0)), 1.0);
}
