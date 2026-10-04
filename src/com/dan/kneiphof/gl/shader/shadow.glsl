// shadow.glsl — Sonnenschatten aus drei Kaskaden (je 2048²), weich durch 12 gedrehte Proben je Punkt.
// Am Ende jeder Kaskade wird über 10 % in die nächste übergeblendet, damit keine Kante sichtbar ist.

uniform sampler2DArrayShadow uShadow;
uniform mat4 uLightVP[3];
uniform vec3 uSplits;        // Enden der Kaskaden in Metern Sichttiefe
uniform vec3 uTexel;         // Größe eines Kartenpunkts in Metern
uniform int uZeroOne;        // 1: Tiefe 0..1 (glClipControl), 0: −1..1
uniform vec3 uCamFwd;
uniform float uShadowOn;
uniform float uShadowQ = 1.0;   // 1: volle Güte, 0: Spiegelbild und Fernes (3 Proben, keine Überblendung)

const vec2 POISSON[12] = vec2[](
    vec2(-0.326, -0.406), vec2(-0.840, -0.074), vec2(-0.696, 0.457), vec2(-0.203, 0.621),
    vec2(0.962, -0.195), vec2(0.473, -0.480), vec2(0.519, 0.767), vec2(0.185, -0.893),
    vec2(0.507, 0.064), vec2(0.896, 0.412), vec2(-0.322, -0.933), vec2(-0.792, -0.598));

float shadowCascade(int c, vec3 pos, vec3 n, float rot) {
    vec3 p = pos + n * uTexel[c] * 1.6;
    vec4 lp = uLightVP[c] * vec4(p, 1.0);
    vec3 q = lp.xyz / lp.w;
    vec2 uv = q.xy * 0.5 + 0.5;
    float z = uZeroOne == 1 ? q.z : q.z * 0.5 + 0.5;
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0))) || z > 1.0) return 1.0;
    float radius = 1.8 / 2048.0;
    float s = sin(rot), co = cos(rot);
    mat2 R = mat2(co, -s, s, co);
    float sum = 0.0;
    int taps = uShadowQ < 0.5 ? 3 : (c == 0 ? 12 : c == 1 ? 8 : 6);
    for (int i = 0; i < taps; i++) {
        vec2 o = R * POISSON[i] * radius;
        sum += texture(uShadow, vec4(uv + o, float(c), z - 0.00015 * float(c + 1)));
    }
    return sum / float(taps);
}

// 1 = Sonne, 0 = Schatten
float sunShadow(vec3 pos, vec3 n, vec3 camPos, vec2 fragCoord) {
    if (uShadowOn < 0.5) return 1.0;
    float d = dot(pos - camPos, uCamFwd);
    float rot = hash12(fragCoord) * 6.2831853;
    for (int c = 0; c < 3; c++) {
        if (d < uSplits[c]) {
            float s = shadowCascade(c, pos, n, rot);
            float edge = uSplits[c] * 0.9;
            if (d > edge && uShadowQ > 0.5) {
                float s2 = c < 2 ? shadowCascade(c + 1, pos, n, rot) : 1.0;
                s = mix(s, s2, (d - edge) / (uSplits[c] - edge));
            }
            return s;
        }
    }
    return 1.0;
}
