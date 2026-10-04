// lum.frag — Helligkeitsmessung für die Belichtung: Logarithmus der Leuchtdichte auf einem kleinen
// Raster. Über die Mipmap-Stufen gemittelt ergibt das das geometrische Mittel; die Sonnenscheibe und
// Glanzlichter werden gekappt, damit sie das Bild nicht dunkel ziehen.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uHdr;
void main() {
    vec3 c = textureLod(uHdr, vUV, 0.0).rgb;
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    if (isnan(l) || isinf(l)) l = 1.0;          // ein kaputter Bildpunkt soll die Belichtung nicht verderben
    // Mitte stärker gewichten (wie eine mittenbetonte Messung)
    vec2 q = vUV - 0.5;
    float w = 1.0 - 0.6 * dot(q, q) * 2.0;
    frag = vec4(log(clamp(l, 1e-5, 8.0)) * w, w, 0.0, 1.0);
}
