// water.vert — eine Wasserfläche als Rechteck: der Pregel (y = 0, bis 16 km) oder ein Becken.
// Das Gelände liegt über dem Spiegel und deckt das Wasser an Land zu.
uniform mat4 uViewProj;
uniform vec4 uRect;          // Mitte x, z und halbe Ausdehnung x, z
uniform float uWaterY;       // Höhe des Wasserspiegels
out vec3 vPos;

void main() {
    vec2 c = vec2(gl_VertexID & 1, (gl_VertexID >> 1) & 1) * 2.0 - 1.0;
    vPos = vec3(uRect.x + c.x * uRect.z, uWaterY, uRect.y + c.y * uRect.w);
    gl_Position = uViewProj * vec4(vPos, 1.0);
}
