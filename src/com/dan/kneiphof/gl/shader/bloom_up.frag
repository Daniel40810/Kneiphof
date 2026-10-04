// bloom_up.frag — vergrößert die kleinere Stufe mit einem 3×3-Zeltfilter; wird auf die größere addiert.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uSrc;
uniform vec2 uTexel;
uniform float uRadius;
void main() {
    vec2 t = uTexel * uRadius;
    vec3 c = textureLod(uSrc, vUV, 0.0).rgb * 4.0;
    c += (textureLod(uSrc, vUV + vec2(-t.x, 0), 0.0).rgb + textureLod(uSrc, vUV + vec2(t.x, 0), 0.0).rgb
        + textureLod(uSrc, vUV + vec2(0, -t.y), 0.0).rgb + textureLod(uSrc, vUV + vec2(0, t.y), 0.0).rgb) * 2.0;
    c += textureLod(uSrc, vUV + vec2(-t.x, -t.y), 0.0).rgb + textureLod(uSrc, vUV + vec2(t.x, -t.y), 0.0).rgb
        + textureLod(uSrc, vUV + vec2(-t.x, t.y), 0.0).rgb + textureLod(uSrc, vUV + vec2(t.x, t.y), 0.0).rgb;
    frag = vec4(c / 16.0, 1.0);
}
