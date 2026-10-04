// blit.frag — ein Bild (halbe Auflösung) auf das volle Bild legen; der Aufrufer schaltet die Mischung.
in vec2 vUV;
out vec4 frag;
uniform sampler2D uTex;
void main() { frag = vec4(texture(uTex, vUV).rgb, 1.0); }
