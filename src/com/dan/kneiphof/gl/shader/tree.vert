// tree.vert — Bäume als Instanzen.
#include "tree_common.glsl"
uniform mat4 uViewProj;
uniform float uClipY = -1.0e4;
out vec3 vPos;
out vec3 vNormal;
out vec3 vColor;
out float vLeaf;
out float vHf;
void main() {
    vec3 n;
    vPos = treeWorld(n);
    vNormal = n;
    vColor = aColor;
    vLeaf = aLeaf;
    vHf = aHf;
    gl_ClipDistance[0] = vPos.y - uClipY;
    gl_Position = uViewProj * vec4(vPos, 1.0);
}
