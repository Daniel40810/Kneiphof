// water.frag — Pregel, Phase 6: überlagerte Wellenzüge mit der Strömung, echte Spiegelung der Ufer, Häuser,
// Brücken und Schiffe (zweiter Durchgang mit gespiegelter Kamera), Sicht auf Grund und Pfähle durch trübes
// Wasser (Kopie der Szene), Schaum an Mauern, Pfählen und Wellenkämmen, Kielwasser der Schiffe,
// Sonnen- und Mondglanz.
#include "common.glsl"
#include "shadow.glsl"

in vec3 vPos;
out vec4 frag;

uniform sampler2D uSkyLut;
uniform sampler2D uRefl;         // gespiegelte Szene (halbe Auflösung, mit Mips)
uniform sampler2D uSceneColor;   // Szene vor dem Wasser
uniform sampler2D uSceneDepth;   // Tiefe der Szene vor dem Wasser
uniform mat4 uInvViewProj;
uniform vec2 uScreen;
uniform float uPlanar;           // 1: Spiegelung vorhanden
uniform float uReflLevels;
uniform float uRiver;            // 1: Pregel (mit Grund und Schaum), 0: Becken
uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform vec3 uMoonDir;
uniform vec3 uMoonColor;
uniform float uTime;
uniform vec2 uWind;          // Windrichtung (normiert) mal Stärke in m/s
uniform vec2 uFlow;          // Strömung in m/s
uniform float uWaveScale;    // 1 offenes Wasser, kleiner im geschützten Becken
uniform float uClear;        // 0 trübes Flusswasser, 1 klares Brunnenwasser (Grund sichtbar)
uniform float uDepth;        // Wassertiefe bis zum Grund (Becken)
uniform vec3 uFloor;         // Farbe des Beckengrunds
uniform int uWakeN;
uniform vec4 uWakeA[8];      // Lage x, z und Richtung (x, z) des Schiffes
uniform vec4 uWakeB[8];      // Tempo, Länge, Breite, Stärke

// Ein Wellenzug: Richtung, Wellenlänge, Steilheit. Liefert die Ableitungen der Höhe.
vec2 wave(vec2 p, vec2 dir, float lambda, float steep, float t, float footprint) {
    float k = 2.0 * PI / lambda;
    float c = sqrt(9.81 / k);                     // Tiefwasser-Phasengeschwindigkeit
    float a = steep / k;
    float fade = smoothstep(4.0, 1.5, footprint * k);   // feiner als ein Bildpunkt: weg
    float ph = k * dot(dir, p) - k * c * t;
    return dir * (a * k * cos(ph)) * fade;
}

// Kielwasser eines Schiffes: Wellenkämme im Keil (Kelvin, 19,5°) als Steigung, Schaumspur und Bugwelle.
void wake(int i, vec2 p, float footprint, inout vec2 grad, inout float foam) {
    vec4 A = uWakeA[i], B = uWakeB[i];
    vec2 d = p - A.xy;
    vec2 dir = A.zw;
    float a = -dot(d, dir);                      // hinter der Mitte des Schiffes
    float b = dot(d, vec2(-dir.y, dir.x));
    float L = B.y, beam = B.z, str = B.w;
    float ab = a + 0.5 * L;                      // hinter dem Bug
    if (ab < -6.0 || ab > 320.0 || abs(b) > 0.40 * ab + 18.0) return;
    float sp = clamp(B.x / 4.0, 0.3, 1.6);
    float onset = smoothstep(-2.0, 8.0, ab);
    float decay = exp(-ab / (95.0 * sp));

    // Keilwellen
    float e = abs(b) - 0.33 * max(ab, 0.0) - 0.5 * beam;
    float w = 1.6 + 0.025 * ab;
    float lam = 1.8 + 0.030 * ab;
    float A0 = 0.13 * str * sp * onset * decay;
    float env = exp(-e * e / (w * w));
    float ph = 6.2831853 * e / lam;
    float dh = A0 * env * (-2.0 * e / (w * w) * cos(ph) - 6.2831853 / lam * sin(ph));
    float fade = smoothstep(2.5, 0.8, footprint * 6.2831853 / lam);
    vec2 ge = sign(b) * vec2(-dir.y, dir.x) + 0.33 * dir;
    grad += ge * dh * fade;
    float armN = fbm(p * vec2(1.3, 1.3) + 5.0 * float(i), 3);
    foam += 0.34 * str * onset * exp(-ab / (28.0 * sp)) * exp(-e * e / (0.12 * w * w)) * (0.5 + 0.5 * cos(ph)) * smoothstep(0.45, 0.75, armN);

    // Heckwasser (Schraube, Kiel): Schaumband in der Mitte, das mit dem Abstand breiter und schwächer wird
    float st = a - 0.5 * L;                      // hinter dem Heck
    if (st > -2.0) {
        float width = 0.42 * beam + 0.06 * max(st, 0.0);
        float band = exp(-b * b / (width * width));
        float life = exp(-max(st, 0.0) / (38.0 * sp + 14.0)) * smoothstep(-2.0, 2.0, st);
        // Blasenwolken: Rauschen quer zur Spur fein, längs gestreckt, wandert mit der Zeit nach hinten
        vec2 q = vec2(b * 1.7, st * 0.55 - uTime * 0.25);
        float n = 0.6 * fbm(q * 2.4 + 7.0 * float(i), 3) + 0.4 * fbm(q * 7.0 + 3.0 * float(i), 2);
        float f = band * life * smoothstep(0.30, 0.72, n + 0.30 * band * life);
        foam += f * str * 1.0;
        // Aufgewühltes Wasser unter der Spur
        vec2 turb = vec2(vnoise(q * 5.0) - 0.5, vnoise(q * 5.0 + 31.0) - 0.5);
        grad += turb * band * life * 0.30 * str;
    }
    // Bugwelle: Schaumkragen am Vorsteven
    float bow = exp(-pow(max(ab, 0.0) / 3.0, 2.0)) * exp(-b * b / (0.18 * beam * beam + 0.5)) * smoothstep(-6.0, 0.0, ab);
    foam += 0.55 * str * sp * bow * (0.5 + vnoise(p * 2.0 + uTime * 0.3));
}

vec3 worldAt(vec2 suv, float z) {
    float zn = uZeroOne == 1 ? z : z * 2.0 - 1.0;
    vec4 a = uInvViewProj * vec4(suv * 2.0 - 1.0, zn, 1.0);
    return a.xyz / a.w;
}

bool isBackground(float z) { return uZeroOne == 1 ? z < 1e-6 : z > 0.999999; }

void main() {
    vec3 v = uCamPos - vPos;
    float dist = length(v);
    v /= dist;
    float footprint = length(fwidth(vPos.xz));
    vec2 suv = gl_FragCoord.xy / uScreen;

    vec2 p = vPos.xz - uFlow * uTime;
    float wind = length(uWind);
    vec2 wd = wind > 0.01 ? uWind / wind : vec2(1.0, 0.0);
    float rough = clamp(wind / 8.0, 0.08, 1.0) * uWaveScale;
    vec2 grad = vec2(0.0);
    // Die Züge laufen nicht als gerade Streifen: eine langsame Verzerrung der Lage biegt und staucht die Kämme
    vec2 warp = vec2(vnoise(p * 0.035), vnoise(p * 0.035 + 9.0)) - 0.5;
    vec2 warp2 = vec2(vnoise(p * 0.17 + 3.0), vnoise(p * 0.17 + 21.0)) - 0.5;
    // Zwölf Züge um die Windrichtung, 0,15 m bis 6 m, Längen leicht gestreut, damit kein Muster entsteht
    for (int i = 0; i < 12; i++) {
        float fi = float(i);
        float ang = (hash12(vec2(fi, 3.7)) - 0.5) * 2.0;
        vec2 dir = vec2(cos(ang) * wd.x - sin(ang) * wd.y, sin(ang) * wd.x + cos(ang) * wd.y);
        float lambda = 6.0 * pow(0.70, fi) * (0.8 + 0.4 * hash12(vec2(fi, 9.1)));
        if (footprint * 6.2831853 / lambda > 4.0) continue;     // feiner als ein Bildpunkt: gar nicht erst rechnen
        float steep = (0.05 + 0.07 * float(i) / 11.0) * rough;
        vec2 pw = p + hash22(vec2(fi, 1.3)) * 50.0 + (warp * 2.2 + warp2 * 0.9) * lambda;
        grad += wave(pw, dir, lambda, steep * (0.75 + 0.5 * warp2.x * 2.0 + 0.25), uTime, footprint);
    }
    // feine Kräuselung als Rauschen, mit dem Wind
    vec2 q = p * 2.2 + wd * uTime * 0.6;
    float e = 0.05;
    float fine = 0.35 * rough * smoothstep(0.4, 0.05, footprint);
    if (fine > 0.002) {
        float n0 = fbm(q, 3), nx = fbm(q + vec2(e, 0.0), 3), nz = fbm(q + vec2(0.0, e), 3);
        grad += vec2(nx - n0, nz - n0) / e * fine * 0.12;
    }

    // Regen: Ringe, die sich von den Einschlagpunkten ausbreiten
    if (uWeather.x > 0.01 && footprint < 0.3) {
        vec2 g = p * 1.7;
        vec2 ci = floor(g);
        float fadeR = smoothstep(0.3, 0.06, footprint) * uWeather.x;
        for (int a = -1; a <= 1; a++)
            for (int b = -1; b <= 1; b++) {
                vec2 c = ci + vec2(float(a), float(b));
                vec2 h = hash22(c);
                float ph = fract(uTime * (0.55 + 0.5 * h.y) + h.x * 7.3);
                vec2 d = g - (c + 0.15 + 0.7 * h);
                float r = length(d);
                float ring = exp(-pow((r - ph * 0.85) * 11.0, 2.0)) * (1.0 - ph) * (1.0 - ph);
                grad += d / max(r, 1e-3) * ring * 0.55 * fadeR;
            }
    }

    // Schiffe: Kielwasser in der Weltlage (nicht mit der Strömung verschoben)
    float foam = 0.0;
    if (uRiver > 0.5) {
        for (int i = 0; i < 8; i++) {
            if (i >= uWakeN) break;
            wake(i, vPos.xz, footprint, grad, foam);
        }
    }
    float steepness = length(grad);

    vec3 n = normalize(vec3(-grad.x, 1.0, -grad.y));
    float ndv = clamp(dot(n, v), 0.02, 1.0);
    float F = 0.02 + 0.98 * pow(clamp(1.0 - ndv, 0.0, 1.0), 5.0);

    // Licht auf dem Wasser
    vec3 light = uSunColor * max(uSunDir.y, 0.0) + uMoonColor * max(uMoonDir.y, 0.0) + skyIrradiance(uSkyLut, vec3(0, 1, 0), uSunDir);
    float sv = sunShadow(vPos, vec3(0.0, 1.0, 0.0), uCamPos, gl_FragCoord.xy);

    // ---- Was liegt hinter der Wasserfläche: Grund, Pfähle, Mauerfüße
    vec3 body = vec3(0.012, 0.020, 0.016) * light / PI;
    if (uRiver > 0.5) {
        ivec2 ip = ivec2(gl_FragCoord.xy);
        float zb = texelFetch(uSceneDepth, ip, 0).r;
        float thick = 60.0;
        if (!isBackground(zb)) thick = distance(worldAt(suv, zb), vPos);
        // gebrochener Blick auf den Grund
        vec2 ruv = clamp(suv + n.xz * 0.02 * clamp(thick, 0.0, 3.0) / (1.0 + dist * 0.04), vec2(0.002), vec2(0.998));
        vec3 bed = texture(uSceneColor, ruv).rgb;
        vec3 T = exp(-thick * vec3(0.80, 0.30, 0.45));
        body = bed * T + body * (1.0 - T);
        body *= mix(0.6, 1.0, sv);

        // Kontaktschaum: Ufermauer, Pfähle, Schiffsrümpfe — liegt Festes über dem Wasser in der Nähe dieses
        // Punktes, bilden Wellen einen hellen Saum
        float shore = 0.0;
        if (dist < 260.0) {
            float R = clamp(0.32 + 0.0012 * dist, 0.32, 0.8);                 // Saumbreite in Metern
            float rpx = clamp(R / max(footprint, 1e-4), 2.0, 26.0);
            float acc = 0.0;
            for (int k = 0; k < 5; k++) {
                float ang = (float(k) + hash12(gl_FragCoord.xy)) * 1.2566371;
                vec2 o = vec2(cos(ang), sin(ang)) * rpx;
                ivec2 jp = ivec2(clamp(vec2(ip) + o, vec2(0.0), uScreen - 1.0));
                float zj = texelFetch(uSceneDepth, jp, 0).r;
                if (isBackground(zj)) continue;
                vec3 wj = worldAt((vec2(jp) + 0.5) / uScreen, zj);
                float hd = distance(wj.xz, vPos.xz);
                if (wj.y > 0.30 && wj.y < 2.2 && hd < R) acc = max(acc, 1.0 - hd / R);
            }
            // Wellen schieben den Saum hin und her
            float lap = 0.55 + 0.45 * sin(uTime * 1.3 + dot(vPos.xz, vec2(0.9, 1.3)) + 6.0 * vnoise(vPos.xz * 0.8));
            float nf = fbm(vPos.xz * 3.1 + uTime * vec2(0.15, -0.1), 3);
            shore = acc * smoothstep(0.38, 0.85, nf + 0.35 * lap) * 0.65;
        }
        // Schaumkronen bei Wind
        float cap = smoothstep(4.5, 9.0, wind) * smoothstep(0.10, 0.28, steepness) * smoothstep(0.35, 0.7, fbm(p * 1.3 + uTime * 0.2, 3));
        foam += shore + cap;
    }
    if (uClear > 0.0) {
        // Klares Wasser: Grund durch die Wassersäule, Weg hin und zurück, gebrochen (Snellius)
        float cosT = sqrt(1.0 - (1.0 - ndv * ndv) / 1.769);
        float path = uDepth / max(cosT, 0.2) + uDepth;
        vec3 T = exp(-vec3(0.45, 0.09, 0.05) * path);
        float caust = 0.85 + 0.5 * pow(vnoise((vPos.xz + n.xz * 0.6) * 3.0 + uTime * 0.3), 3.0);
        vec3 floorC = uFloor / PI * light * caust;
        body = mix(body, floorC * T + vec3(0.004, 0.012, 0.012) * light / PI * (1.0 - T), uClear);
    }

    // ---- Spiegelung
    vec3 refl;
    bool fromScene = uPlanar > 0.5 && uRiver > 0.5;
    if (fromScene) {
        // Bild mit der Steigung verschieben (nah mehr, fern weniger), senkrecht dehnen wie echte Wellen
        vec2 off = grad * clamp(26.0 / dist, 0.02, 0.5) * 0.14;
        vec2 uv = suv + vec2(off.x, off.y * 0.6);
        float stretch = (0.6 + rough * 2.2) * (1.0 - ndv) * 0.006;
        float lod = clamp(0.7 + log2(1.0 + dist * 0.015) + rough * 1.4, 0.0, max(uReflLevels - 3.0, 0.0));
        vec3 acc = vec3(0.0);
        float wsum = 0.0;
        for (int k = -2; k <= 2; k++) {
            float wk = 1.0 - abs(float(k)) * 0.22;
            acc += textureLod(uRefl, clamp(uv + vec2(0.0, float(k) * stretch), vec2(0.002), vec2(0.998)), lod).rgb * wk;
            wsum += wk;
        }
        refl = acc / wsum;
    } else {
        vec3 r = reflect(-v, n);
        r.y = abs(r.y);
        refl = texture(uSkyLut, skyUV(r)).rgb;
    }

    // Sonnenglanz (GGX), Rauheit mit dem Wind
    vec3 h = normalize(uSunDir + v);
    float nh = max(dot(n, h), 0.0);
    float alpha = mix(0.04, 0.16, rough);
    float a2 = alpha * alpha;
    float D = a2 / (PI * pow(nh * nh * (a2 - 1.0) + 1.0, 2.0));
    float Fs = 0.02 + 0.98 * pow(clamp(1.0 - dot(h, v), 0.0, 1.0), 5.0);
    float ndl = max(dot(n, uSunDir), 0.0);
    vec3 spec = uSunColor * D * Fs * ndl / (4.0 * ndv * max(ndl, 0.05) + 1e-3) * ndl;

    // Mondglanz: dieselbe Verteilung zum Mond
    vec3 hm = normalize(uMoonDir + v);
    float nhm = max(dot(n, hm), 0.0);
    float Dm = a2 / (PI * pow(nhm * nhm * (a2 - 1.0) + 1.0, 2.0));
    float ndlm = max(dot(n, uMoonDir), 0.0);
    spec += uMoonColor * Dm * 0.02 * ndlm / (4.0 * ndv * max(ndlm, 0.05) + 1e-3) * ndlm;
    spec *= sv;

    // Schaum: hell, streut das Licht von oben, deckt Spiegelung und Grund
    float fm = clamp(foam, 0.0, 1.0);
    vec3 foamC = vec3(0.80, 0.82, 0.78) * (light * mix(0.55, 1.0, sv)) / PI;

    vec3 c;
    if (fromScene) {
        // Die Spiegelung trägt den Dunst ihres ganzen Weges schon in sich; Körper und Glanz brauchen ihn noch
        vec3 lower = mix(body, foamC, fm) * (1.0 - F) + spec * (1.0 - fm);
        c = aerial(lower, -v, dist, uSkyLut) + refl * F * (1.0 - fm);
    } else {
        c = mix(mix(body, foamC, fm), refl, F * (1.0 - fm)) + spec * (1.0 - fm);
        c = aerial(c, -v, dist, uSkyLut);
    }
    frag = vec4(c, 1.0);
}
