// object.frag — Bauteile mit gemalten Materialien, Sonnenschatten, Himmelslicht und Luftperspektive.
#include "common.glsl"
#include "materials.glsl"
#include "lighting.glsl"
#include "shadow.glsl"

in vec3 vPos;
in vec3 vNormal;
in vec3 vTangent;
in vec2 vUV;
flat in int vMat;
in float vAO;
out vec4 frag;

uniform vec3 uCamPos;
uniform vec3 uLampColor;
uniform float uExposure;      // aktuelle Belichtung (für Licht, das in Weltlicht gemessen ist)
uniform float uTimeLamp;     // Leuchten der Gaslaternen, vorbelichtet (0 am Tag)

void main() {
    vec3 N0 = normalize(vNormal);
    if (!gl_FrontFacing) N0 = -N0;
    vec3 t0 = vTangent - N0 * dot(vTangent, N0);
    if (dot(t0, t0) < 1e-8) t0 = abs(N0.y) < 0.9 ? cross(vec3(0, 1, 0), N0) : vec3(1, 0, 0);
    vec3 T = normalize(t0);
    vec3 B = cross(N0, T);
    float fw = length(fwidth(vPos));
    bool inside = vMat >= M_IN_GLASS && vMat < 100;
    int mid = vMat == M_IN_GLASS ? M_GLASS_G : (vMat >= M_IN ? vMat - M_IN : vMat);
    Surf s = material(mid, vUV, vPos, fw);
    vec3 N = normalize(T * s.nT.x + B * s.nT.y + N0 * s.nT.z);
    vec3 v = uCamPos - vPos;
    float dist = length(v);
    vec3 V = v / dist;
    float sv = sunShadow(vPos, N0, uCamPos, gl_FragCoord.xy);
    // Kontaktschatten am Fuß, dazu etwas Selbstverdeckung in den Fugen
    float ao = vAO * mix(1.0, 0.75, 1.0 - s.nT.z * s.nT.z);
    vec3 c;
    if (inside) {
        // Raumlicht: Sonne nur durch die Tür, sonst weiches Licht aus den Fenstern, nachts Kerzen und Gas
        vec3 f0 = mix(vec3(0.04), s.albedo, s.metal);
        c = directLight(s.albedo, f0, s.rough, s.metal, N, V, uSunDir, uSunColor * sv);
        c += directLight(s.albedo, f0, max(s.rough, 0.3), s.metal, N, V, uMoonDir, uMoonColor * 0.0);
        vec3 Esky = skyIrradiance(uSkyLut, vec3(0.0, 1.0, 0.0), uSunDir);
        float el = dot(Esky, vec3(0.3, 0.55, 0.15));
        vec3 E = mix(Esky, vec3(el) * vec3(1.0, 0.92, 0.80), 0.7) * (0.055 + 0.03 * N.y * N.y) + (uLampColor / 22.0) * vec3(1.0, 0.75, 0.45) * (0.02 * uExposure);
        c += (1.0 - s.metal) * s.albedo / PI * E * ao;
        if (vMat == M_IN_GLASS) {
            float gm = glassMask(M_GLASS_G, vUV);
            vec3 day = envSample(vec3(0.0, 1.0, 0.0), 0.7) * 0.9;
            c = mix(c, day * (0.55 + 0.45 * gm), 0.9);
        }
    } else {
        c = shadeSurface(s, N, V, ao, sv);
    }
    if (vMat == M_PANE_LIT) c += uLampColor * (0.030 * glassMask(vMat, vUV));
    if (vMat == M_GLASS_G_LIT) c += uLampColor * (0.022 * glassMask(vMat, vUV));
    if (vMat == M_LAMP) c += uLampColor * (0.85 + 0.15 * sin(uTimeLamp * 9.0 + vPos.x * 3.1));
    if (!inside) c = aerial(c, -V, dist, uSkyLut);
    frag = vec4(c, 1.0);
}
