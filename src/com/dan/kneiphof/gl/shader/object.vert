// object.vert — Bauten und Prüfstand: Lage, Normale, Tangente, Flächenkoordinaten, Material, Verdeckung.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aTangent;
layout(location = 3) in vec2 aUV;
layout(location = 4) in float aMat;
layout(location = 5) in float aAO;

uniform mat4 uViewProj;
uniform float uClipY = -1.0e4;
uniform mat4 uModel = mat4(1.0);   // bewegliche Teile (Brückenklappen); sonst Einheitsmatrix

out vec3 vPos;
out vec3 vNormal;
out vec3 vTangent;
out vec2 vUV;
flat out int vMat;
out float vAO;

invariant gl_Position;
void main() {
    vec4 wp = uModel * vec4(aPos, 1.0);
    vPos = wp.xyz;
    vNormal = mat3(uModel) * aNormal;
    vTangent = mat3(uModel) * aTangent;
    vUV = aUV;
    vMat = int(aMat + 0.5);
    vAO = aAO;
    gl_ClipDistance[0] = wp.y - uClipY;
    gl_Position = uViewProj * wp;
}
