// tree_depth.vert — Bäume in die Schattenkarte, mit derselben Windbewegung.
#include "tree_common.glsl"
uniform mat4 uLightVP;
void main() {
    vec3 n;
    gl_Position = uLightVP * vec4(treeWorld(n), 1.0);
}
