// shadow_depth.vert — Tiefe aus Sicht der Sonne für eine Kaskade (und die Tiefenvorstufe der Kamera).
layout(location = 0) in vec3 aPos;
uniform mat4 uLightVP;
uniform mat4 uModel = mat4(1.0);
uniform float uClipY = -1.0e4;   // Spiegelung: Tiefenvorstufe schneidet unter dem Wasser ab
invariant gl_Position;
void main() {
    vec4 wp = uModel * vec4(aPos, 1.0);
    gl_ClipDistance[0] = wp.y - uClipY;
    gl_Position = uLightVP * wp;
}
