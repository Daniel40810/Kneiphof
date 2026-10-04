// particle.frag — weiche Rauchwolke: Form aus Rauschen, Beleuchtung von Sonne und Himmel,
// sanftes Ausblenden vor festen Flächen (Tiefe der Szene), Luftperspektive wie bei allem anderen.
#include "common.glsl"

in vec2 vQ;
in vec4 vA;
in vec3 vWorld;
out vec4 frag;

uniform sampler2D uSkyLut;
uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform vec3 uMoonDir;
uniform vec3 uMoonColor;
uniform sampler2D uSceneDepth;   // Tiefe der Szene (Kopie)
uniform mat4 uInvViewProj2;
uniform vec2 uScreen;
uniform float uZeroOne2;

void main() {
    float d = length(vQ);
    if (d > 1.0) discard;
    float age = vA.x;
    bool steam = vA.y > 0.5;
    vec2 q = vQ * 2.6 + vA.z * 3.1 + vec2(age * 0.8, -age * 0.5);
    float n = fbm(q, 4);
    float shape = smoothstep(1.0, 0.35, d + (n - 0.5) * 1.05);
    float fadeIn = smoothstep(0.0, 0.06, age);
    float fadeOut = 1.0 - smoothstep(0.55, 1.0, age);
    float a = shape * fadeIn * fadeOut * vA.w;

    // Abstand zur festen Fläche dahinter
    float zb = texelFetch(uSceneDepth, ivec2(gl_FragCoord.xy), 0).r;
    bool bg = uZeroOne2 > 0.5 ? zb < 1e-6 : zb > 0.999999;
    if (!bg) {
        float zn = uZeroOne2 > 0.5 ? zb : zb * 2.0 - 1.0;
        vec4 s = uInvViewProj2 * vec4(gl_FragCoord.xy / uScreen * 2.0 - 1.0, zn, 1.0);
        float sd = distance(s.xyz / s.w, uCamPos);
        float pd = distance(vWorld, uCamPos);
        a *= clamp((sd - pd) / 1.5 + 0.2, 0.0, 1.0);
    }
    if (a < 0.002) discard;

    vec3 dir = vWorld - uCamPos;
    float dist = length(dir);
    dir /= dist;
    vec3 Esky = skyIrradiance(uSkyLut, vec3(0.0, 1.0, 0.0), uSunDir);
    float sunUp = max(uSunDir.y, 0.0);
    // Vorwärtsstreuung: Rauch gegen die Sonne leuchtet an den Rändern
    float cs = max(dot(dir, uSunDir), 0.0);
    float fwd = 0.45 + 0.9 * pow(cs, 6.0) * (1.0 - 0.6 * (1.0 - d));
    vec3 albedo = steam ? vec3(0.92) : mix(vec3(0.05, 0.048, 0.045), vec3(0.20, 0.19, 0.18), clamp(age * 1.3, 0.0, 1.0));
    vec3 light = uSunColor * sunUp * fwd * (0.55 + 0.45 * n) + Esky * 0.9 + uMoonColor * max(uMoonDir.y, 0.0) * 0.5;
    vec3 col = albedo * light;
    col = aerial(col, dir, dist, uSkyLut);
    frag = vec4(col * a, a);
}
