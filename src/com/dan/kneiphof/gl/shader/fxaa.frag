// fxaa.frag — Kantenglättung nach Lottes (FXAA), auf dem fertigen Bild; die Helligkeit steht im Alphakanal.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uLdr;
uniform vec2 uTexel;
uniform int uOn;

void main() {
    if (uOn == 0) { frag = vec4(textureLod(uLdr, vUV, 0.0).rgb, 1.0); return; }
    const float REDUCE_MIN = 1.0 / 128.0, REDUCE_MUL = 1.0 / 8.0, SPAN_MAX = 8.0;
    float lNW = textureLod(uLdr, vUV + vec2(-1, -1) * uTexel, 0.0).a;
    float lNE = textureLod(uLdr, vUV + vec2(1, -1) * uTexel, 0.0).a;
    float lSW = textureLod(uLdr, vUV + vec2(-1, 1) * uTexel, 0.0).a;
    float lSE = textureLod(uLdr, vUV + vec2(1, 1) * uTexel, 0.0).a;
    vec4 cM = textureLod(uLdr, vUV, 0.0);
    float lM = cM.a;
    float lMin = min(lM, min(min(lNW, lNE), min(lSW, lSE)));
    float lMax = max(lM, max(max(lNW, lNE), max(lSW, lSE)));
    if (lMax - lMin < max(0.0312, lMax * 0.125)) { frag = vec4(cM.rgb, 1.0); return; }
    vec2 dir = vec2(-((lNW + lNE) - (lSW + lSE)), ((lNW + lSW) - (lNE + lSE)));
    float reduce = max((lNW + lNE + lSW + lSE) * 0.25 * REDUCE_MUL, REDUCE_MIN);
    float rcp = 1.0 / (min(abs(dir.x), abs(dir.y)) + reduce);
    dir = clamp(dir * rcp, vec2(-SPAN_MAX), vec2(SPAN_MAX)) * uTexel;
    vec3 a = 0.5 * (textureLod(uLdr, vUV + dir * (1.0 / 3.0 - 0.5), 0.0).rgb + textureLod(uLdr, vUV + dir * (2.0 / 3.0 - 0.5), 0.0).rgb);
    vec3 b = a * 0.5 + 0.25 * (textureLod(uLdr, vUV + dir * -0.5, 0.0).rgb + textureLod(uLdr, vUV + dir * 0.5, 0.0).rgb);
    float lB = dot(b, vec3(0.299, 0.587, 0.114));
    frag = vec4((lB < lMin || lB > lMax) ? a : b, 1.0);
}
