// sky.frag — der Himmel hinter allem: Strahldichte aus der Himmelstabelle, Sonnen- und Mondscheibe.
#include "common.glsl"

in vec2 vUV;
out vec4 frag;

uniform sampler2D uSkyLut;
uniform mat4 uInvViewProj;
uniform vec3 uCamPos;
uniform float uTime;
uniform vec3 uSunDir;
uniform vec3 uSunColor;      // Sonnenlicht am Boden (schon durch die Luft gedämpft, mit Stärke)
uniform vec3 uMoonDir;
uniform vec3 uMoonColor;     // Mondlicht am Boden, vorbelichtet
uniform float uMoonLit;      // beleuchteter Anteil 0..1 (nur für die Helligkeit der Scheibe)
uniform float uMirror;       // 1: Spiegelbild — Sonne und Mond zeichnet der Glanz des Wassers
uniform float uStars;        // Helligkeit der Sterne, vorbelichtet

void main() {
    vec4 a = uInvViewProj * vec4(vUV * 2.0 - 1.0, 0.5, 1.0);
    vec3 dir = normalize(a.xyz / a.w - uCamPos);

    vec3 d = dir;
    float below = clamp(-d.y * 8.0, 0.0, 1.0);
    vec3 c = texture(uSkyLut, skyUV(d)).rgb;
    // Unter dem Horizont (nur am Rand der Welt sichtbar): Dunst in Bodennähe
    c = mix(c, c * 0.85, below);

    // Flussnebel vor dem Himmel: je flacher der Blick, desto länger der Weg durch die Schicht
    if (uMist.x > 0.002 && d.y > -0.02) {
        float od = uMist.x * 0.014 * mistDepth(uMist.z, max(d.y, 0.002), 20000.0, uMist.y);
        vec3 hz = textureLod(uSkyLut, skyUV(normalize(vec3(d.x, 0.02, d.z))), 1.0).rgb;
        hz = mix(hz, vec3(dot(hz, vec3(0.3333))), 0.30) * 1.05;
        c = mix(hz, c, exp(-min(od, 24.0)));
    }

    // Regenbogen: um den Gegenpunkt der Sonne, innen violett, außen rot; darüber ein schwacher zweiter Bogen
    if (uWeather.z > 0.01 && uSunDir.y > 0.03 && uSunDir.y < 0.8) {
        float th = degrees(acos(clamp(dot(d, -uSunDir), -1.0, 1.0)));
        float t1 = (th - 40.0) / 2.6, t2 = 1.0 - (th - 50.5) / 3.2;
        float w1 = smoothstep(0.0, 0.12, t1) * smoothstep(1.0, 0.88, t1) * step(0.0, t1) * step(t1, 1.0);
        float w2 = smoothstep(0.0, 0.12, t2) * smoothstep(1.0, 0.88, t2) * step(0.0, t2) * step(t2, 1.0) * 0.28;
        float tt = w1 > 0.0 ? clamp(t1, 0.0, 1.0) : clamp(t2, 0.0, 1.0);
        float hue = 0.76 * (1.0 - tt);
        vec3 rgb = clamp(abs(fract(vec3(hue) + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
        float lumS = dot(c, vec3(0.3, 0.55, 0.15));
        c += rgb * (w1 + w2) * lumS * 1.1 * uWeather.z * smoothstep(0.0, 0.25, d.y + 0.05);
    }

    // Sonnenscheibe, 0,27° Halbmesser, mit Randverdunkelung
    float cs = dot(d, uSunDir);
    float r = acos(clamp(cs, -1.0, 1.0)) / 0.0047;
    if (r < 1.0 && uMirror < 0.5) {
        float limb = 0.4 + 0.6 * sqrt(1.0 - r * r);
        c += uSunColor * 1800.0 * limb * (1.0 - below) * exp(-min(uMist.x * 0.014 * mistDepth(uMist.z, max(d.y, 0.002), 20000.0, uMist.y), 24.0));
    }
    // Mondscheibe mit Phase: die Kugelnormale der Scheibe gegen die Sonnenrichtung; die Nacht-Seite leuchtet schwach (Erdschein)
    float cm = dot(d, uMoonDir);
    float rm = acos(clamp(cm, -1.0, 1.0)) / 0.0045;
    if (rm < 1.0 && uMirror < 0.5) {
        vec3 mt = normalize(cross(vec3(0.0, 1.0, 0.0), uMoonDir));
        vec3 mb = cross(uMoonDir, mt);
        vec2 xy = vec2(dot(d, mt), dot(d, mb)) / 0.0045;
        vec3 nrm = vec3(xy, sqrt(max(1.0 - dot(xy, xy), 0.0)));
        vec3 sl = vec3(dot(uSunDir, mt), dot(uSunDir, mb), dot(uSunDir, uMoonDir));
        float lit = smoothstep(-0.04, 0.04, dot(nrm, sl));
        // leichte Maria: dunklere Flecken, damit die Scheibe nicht leer wirkt
        float maria = 0.82 + 0.18 * vnoise(xy * 3.0 + 4.0) + 0.1 * vnoise(xy * 9.0);
        float surf = (0.035 + 0.965 * lit) * maria;
        c += uMoonColor / max(uMoonLit, 0.04) * 1800.0 * surf * smoothstep(1.0, 0.92, rm) * (1.0 - below);
    }
    // Sterne: zufällig verteilte Punkte, flimmern leicht, verblassen im Dunst am Horizont
    if (uStars > 0.0 && d.y > 0.0) {
        vec3 p = d * 420.0;
        vec3 cell = floor(p);
        float h = hash12(cell.xy + cell.z * 37.0);
        if (h > 0.9955) {
            vec3 f = fract(p) - 0.5;
            float mag = pow((h - 0.9955) / 0.0045, 6.0);
            float s = exp(-dot(f, f) * 60.0) * mag;
            float tw = 0.75 + 0.25 * sin(uTime * 3.0 + h * 900.0);
            vec3 tint = mix(vec3(1.0, 0.85, 0.7), vec3(0.75, 0.85, 1.0), fract(h * 1234.5));
            c += tint * s * tw * uStars * 600.0 * smoothstep(0.0, 0.25, d.y);
        }
    }
    frag = vec4(c, 1.0);
}
