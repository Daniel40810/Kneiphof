// beam.frag — Sonnenstrahl im staubigen Raum: weicher Rand, Staubkörnung, Abklingen mit dem Weg,
// weiches Ausblenden vor festen Flächen. Wird additiv aufs Bild gelegt.
#include "common.glsl"

in vec3 vWorld;
in vec3 vN;
in float vT;
out vec4 frag;

uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform float uTime;
uniform sampler2D uSceneDepth;
uniform mat4 uInvViewProj2;
uniform vec2 uScreen;
uniform float uZeroOne2;
uniform float uStrength;

void main() {
    vec3 v = vWorld - uCamPos;
    float dist = length(v);
    v /= dist;
    float edge = abs(dot(normalize(vN), v));
    float a = 0.02 + 0.98 * edge * edge;
    a *= (1.0 - smoothstep(0.0, 1.0, vT)) * smoothstep(0.0, 0.04, vT);
    // Staub: langsam treibende Körnung im Raum
    a *= smoothstep(2.0, 9.0, dist);
    float dust = 0.65 + 0.7 * fbm(vWorld.xz * 0.35 + vWorld.y * 0.21 + vec2(uTime * 0.03, -uTime * 0.02), 3);
    a *= dust;
    float zb = texelFetch(uSceneDepth, ivec2(gl_FragCoord.xy), 0).r;
    bool bg = uZeroOne2 > 0.5 ? zb < 1e-6 : zb > 0.999999;
    if (!bg) {
        float zn = uZeroOne2 > 0.5 ? zb : zb * 2.0 - 1.0;
        vec4 s = uInvViewProj2 * vec4(gl_FragCoord.xy / uScreen * 2.0 - 1.0, zn, 1.0);
        float sd = distance(s.xyz / s.w, uCamPos);
        a *= clamp((sd - dist) / 2.5, 0.0, 1.0);
    }
    float cs = dot(v, uSunDir);
    float phase = 0.45 + 0.9 * pow(max(cs, 0.0), 3.0);
    frag = vec4(uSunColor * (a * phase * uStrength), 1.0);
}
