// tree.frag — Rinde und Laub: Farbe aus dem Wald-Paket (mit Jahreszeit), Sonne mit Schatten, Himmel,
// durchscheinendes Laub im Gegenlicht, Luftperspektive.
#include "common.glsl"
#include "materials.glsl"
#include "lighting.glsl"
#include "shadow.glsl"

in vec3 vPos;
in vec3 vNormal;
in vec3 vColor;
in float vLeaf;
in float vHf;
out vec4 frag;

uniform vec3 uCamPos;

void main() {
    vec3 N = normalize(vNormal);
    if (!gl_FrontFacing) N = -N;
    vec3 v = uCamPos - vPos;
    float dist = length(v);
    vec3 V = v / dist;
    Surf s;
    s.albedo = vColor;
    if (vLeaf > 0.5) {
        // Blattwerk: Flecken aus Licht und Schatten, damit grobe Büschel nicht glatt wirken
        float n = vnoise(vPos.xz * 1.7 + vPos.y * 1.3) * 0.6 + vnoise(vPos.xz * 6.0 - vPos.y * 4.0) * 0.4;
        s.albedo *= 0.65 + 0.7 * n;
    }
    s.rough = vLeaf > 0.5 ? 0.8 : 0.9;
    s.metal = 0.0;
    s.nT = vec3(0.0, 0.0, 1.0);
    float sv = sunShadow(vPos, N, uCamPos, gl_FragCoord.xy);
    // Innen in der Krone dunkler (schon in der Farbe), zusätzlich unten am Stamm
    float ao = mix(0.55, 1.0, smoothstep(0.0, 0.5, vHf));
    vec3 c = shadeSurface(s, N, V, ao, sv);
    if (vLeaf > 0.5) {
        // Licht scheint durch das Blatt (Gegenlicht), etwas grüner
        float back = max(dot(-N, uSunDir), 0.0) * pow(max(dot(-V, uSunDir), 0.0), 2.0);
        c += uSunColor * sv * vColor * vec3(0.9, 1.2, 0.6) * back * 0.45;
    }
    c = aerial(c, -V, dist, uSkyLut);
    frag = vec4(c, 1.0);
}
