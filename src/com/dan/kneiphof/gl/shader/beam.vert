// beam.vert — Prisma eines Sonnenstrahls im Dom.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in float aT;
uniform mat4 uViewProj;
out vec3 vWorld;
out vec3 vN;
out float vT;
void main() {
    vWorld = aPos;
    vN = aNormal;
    vT = aT;
    gl_Position = uViewProj * vec4(aPos, 1.0);
}
