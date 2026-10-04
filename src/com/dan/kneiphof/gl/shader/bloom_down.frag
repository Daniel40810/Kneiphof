// bloom_down.frag — halbiert das Bild mit 13 Proben (nach Jimenez, CoD: Advanced Warfare). In der ersten
// Stufe gewichtet nach Helligkeit (Karis), damit einzelne Glanzpunkte im Wasser nicht flackern.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uSrc;
uniform vec2 uTexel;     // Kartenpunkt der Quelle
uniform int uFirst;

vec3 s(vec2 o) {
    vec3 c = textureLod(uSrc, vUV + o * uTexel, 0.0).rgb;
    return any(isnan(c)) || any(isinf(c)) ? vec3(0.0) : min(c, vec3(6.0e4));   // ein kaputter Punkt darf nicht ausbluten
}
float w(vec3 c) { return 1.0 / (1.0 + dot(c, vec3(0.2126, 0.7152, 0.0722))); }

void main() {
    vec3 a = s(vec2(-2, 2)), b = s(vec2(0, 2)), c = s(vec2(2, 2));
    vec3 d = s(vec2(-2, 0)), e = s(vec2(0, 0)), f = s(vec2(2, 0));
    vec3 g = s(vec2(-2, -2)), h = s(vec2(0, -2)), i = s(vec2(2, -2));
    vec3 j = s(vec2(-1, 1)), k = s(vec2(1, 1)), l = s(vec2(-1, -1)), m = s(vec2(1, -1));
    vec3 r;
    if (uFirst == 1) {
        vec3 g0 = (a + b + d + e) * 0.25, g1 = (b + c + e + f) * 0.25, g2 = (d + e + g + h) * 0.25, g3 = (e + f + h + i) * 0.25, g4 = (j + k + l + m) * 0.25;
        float w0 = w(g0), w1 = w(g1), w2 = w(g2), w3 = w(g3), w4 = w(g4);
        r = (g0 * w0 * 0.125 + g1 * w1 * 0.125 + g2 * w2 * 0.125 + g3 * w3 * 0.125 + g4 * w4 * 0.5)
          / (w0 * 0.125 + w1 * 0.125 + w2 * 0.125 + w3 * 0.125 + w4 * 0.5);
    } else {
        r = e * 0.125 + (a + c + g + i) * 0.03125 + (b + d + f + h) * 0.0625 + (j + k + l + m) * 0.125;
    }
    frag = vec4(max(r, 0.0), 1.0);
}
