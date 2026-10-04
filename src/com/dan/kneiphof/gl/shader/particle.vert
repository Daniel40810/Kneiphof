// particle.vert — Rauch und Dampf als Flächen, die zur Kamera zeigen.
layout(location = 0) in vec4 iPos;   // Mitte, Größe (Radius in m)
layout(location = 1) in vec4 iA;     // Alter 0..1, Art (0 Rauch, 1 Dampf), Same, Deckkraft
uniform mat4 uViewProj;
uniform vec3 uRight;
uniform vec3 uUp;
out vec2 vQ;
out vec4 vA;
out vec3 vWorld;
void main() {
    vec2 c = vec2((gl_VertexID & 1) * 2 - 1, (gl_VertexID >> 1) * 2 - 1);
    float ang = iA.z * 6.2831 + iA.x * (iA.y > 0.5 ? 0.6 : 0.3) * (fract(iA.z * 7.0) - 0.5) * 4.0;
    float ca = cos(ang), sa = sin(ang);
    vec2 r = vec2(ca * c.x - sa * c.y, sa * c.x + ca * c.y);
    // der Rand soll nie hart abgeschnitten werden: Fläche etwas größer als der sichtbare Kreis
    vec3 w = iPos.xyz + (uRight * r.x + uUp * r.y) * iPos.w * 1.5;
    vQ = c * 1.5;
    vA = iA;
    vWorld = w;
    gl_Position = uViewProj * vec4(w, 1.0);
}
