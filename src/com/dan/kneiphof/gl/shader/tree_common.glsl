// tree_common.glsl — Lage einer Baumecke in der Welt: Instanz (Ort, Drehung, Größe) und Wind.
// Die Krone neigt sich mit der Höhe quadratisch in den Wind und schwingt in zwei Takten; Blätter zittern.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aColor;
layout(location = 3) in float aLeaf;
layout(location = 4) in float aHf;
layout(location = 5) in vec4 aInst;     // x y z Drehung
layout(location = 6) in float aScale;

uniform float uTime;
uniform vec2 uWind;                     // Richtung mal Stärke (m/s), in die der Wind weht

vec3 treeWorld(out vec3 nWorld) {
    float c = cos(aInst.w), s = sin(aInst.w);
    mat3 R = mat3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c);
    vec3 p = R * (aPos * aScale);
    nWorld = R * aNormal;
    float ph = fract(sin(dot(aInst.xz, vec2(12.9898, 78.233))) * 43758.5453) * 6.2831;
    float w = length(uWind);
    vec2 wd = w > 0.01 ? uWind / w : vec2(1.0, 0.0);
    float gust = 0.6 + 0.4 * sin(uTime * 0.35 + aInst.x * 0.013 + aInst.z * 0.011);
    float sway = (0.012 * w + 0.0025 * w * w) * gust * aHf * aHf * (1.0 + 0.35 * sin(uTime * (1.1 + 0.2 * fract(ph)) + ph) + 0.2 * sin(uTime * 2.3 + ph * 1.7));
    float hgt = aPos.y * aScale;
    p.xz += wd * sway * hgt * 0.35;
    p.y -= sway * sway * hgt * 0.05;
    // Blätter zittern
    float fl = aLeaf * (0.012 + 0.006 * w) * sin(uTime * 7.0 + dot(aPos, vec3(13.1, 7.7, 9.3)));
    p += nWorld * fl;
    return aInst.xyz + p;
}
