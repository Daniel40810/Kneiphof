// terrain.vert — Gelände: Lage, Normale und Bodenart je Punkt.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec4 aCover;

uniform mat4 uViewProj;
uniform float uClipY = -1.0e4;   // Spiegelung: alles unter dieser Höhe wird abgeschnitten

out vec3 vPos;
out vec3 vNormal;
out vec4 vCover;

void main() {
    vPos = aPos;
    vNormal = aNormal;
    vCover = aCover;
    gl_ClipDistance[0] = aPos.y - uClipY;
    gl_Position = uViewProj * vec4(aPos, 1.0);
}
