// post.frag — vom HDR-Bild zum fertigen Bild: Bloom, Belichtung, Filmkurve (ACES), Randabdunklung,
// sRGB und eine Spur Rauschen gegen Stufen im Himmel.
#include "common.glsl"

in vec2 vUV;
out vec4 frag;

uniform sampler2D uHdr;
uniform sampler2D uBloom;
uniform float uBloomStrength;
uniform float uBloomNorm;
uniform float uExposure;
uniform float uVignette;
uniform float uTime;
uniform float uBars;         // Höhe eines Kinobalkens, Bruchteil der Bildhöhe
uniform float uFade;         // 1 hell … 0 schwarz
uniform float uNight;        // 0 Tag … 1 Nacht: das Auge sieht nachts weniger Farbe und blauer

vec3 aces(vec3 x) {
    // Näherung von Narkowicz (2015)
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

float rainLayer(vec2 uv, float cols, float rows, float speed, float seed, float slant) {
    float x = uv.x * cols + uv.y * slant;
    float ix = floor(x);
    float h = hash12(vec2(ix, seed));
    float y = uv.y * rows + uTime * speed * (0.8 + 0.5 * h) + h * 53.0;
    float fy = fract(y);
    float fx = abs(fract(x) - 0.5);
    float on = step(0.55, hash12(vec2(ix, floor(y) + seed * 3.0)));
    return on * smoothstep(0.5, 0.0, fx) * smoothstep(0.0, 0.2, fy) * smoothstep(0.75, 0.2, fy);
}

void main() {
    vec3 c = texture(uHdr, vUV).rgb;
    c = mix(c, texture(uBloom, vUV).rgb * uBloomNorm, uBloomStrength) * uExposure;
    // Nachtsehen (Purkinje): weniger Farbe, Verschiebung ins Blaue
    float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(c, lum * vec3(0.62, 0.78, 1.15), uNight * 0.7);
    c = aces(c);
    vec2 q = vUV - 0.5;
    c *= 1.0 - uVignette * dot(q, q) * 1.6;
    if (uWeather.x > 0.01) {
        vec2 uv = vec2(vUV.x * 1.78, vUV.y);
        float st = rainLayer(vUV, 90.0, 6.0, 5.5, 1.0, 5.0) * 0.5 + rainLayer(vUV, 150.0, 5.0, 7.0, 2.0, 6.0) * 0.35 + rainLayer(vUV, 260.0, 4.0, 9.0, 3.0, 7.0) * 0.25;
        c += st * uWeather.x * 0.034 * vec3(0.8, 0.88, 1.0);
        c *= 1.0 - 0.1 * uWeather.x;
    }
    c *= uFade;
    c = pow(c, vec3(1.0 / 2.2));
    if (vUV.y < uBars || vUV.y > 1.0 - uBars) c = vec3(0.0);
    c += (hash12(gl_FragCoord.xy + fract(uTime) * 61.0) - 0.5) / 255.0;
    // Helligkeit für die Kantenglättung im Alphakanal
    frag = vec4(c, dot(c, vec3(0.299, 0.587, 0.114)));
}
