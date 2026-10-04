// lighting.glsl — Licht auf einer Oberfläche: Sonne (GGX-Glanz, Schatten), Mond, Himmel als
// Umgebungslicht und Spiegelung des Himmels nach Rauheit (Mip-Stufe der Himmelstabelle).
// Braucht common.glsl und materials.glsl.

uniform sampler2D uSkyLut;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform vec3 uMoonDir;
uniform vec3 uMoonColor;

float D_GGX(float nh, float a) {
    float a2 = a * a;
    float d = nh * nh * (a2 - 1.0) + 1.0;
    return a2 / (PI * d * d);
}

float V_Smith(float nv, float nl, float a) {
    float k = a * 0.5;
    return 0.25 / ((nv * (1.0 - k) + k) * (nl * (1.0 - k) + k));
}

vec3 F_Schlick(vec3 f0, float vh) { return f0 + (1.0 - f0) * pow(clamp(1.0 - vh, 0.0, 1.0), 5.0); }

// Näherung des vorintegrierten Umgebungsglanzes (Lazarov / Karis)
vec3 envBRDF(vec3 f0, float rough, float nv) {
    const vec4 c0 = vec4(-1.0, -0.0275, -0.572, 0.022);
    const vec4 c1 = vec4(1.0, 0.0425, 1.04, -0.04);
    vec4 r = rough * c0 + c1;
    float a004 = min(r.x * r.x, exp2(-9.28 * nv)) * r.x + r.y;
    vec2 ab = vec2(-1.04, 1.04) * a004 + r.zw;
    return f0 * ab.x + ab.y;
}

vec3 directLight(vec3 albedo, vec3 f0, float rough, float metal, vec3 N, vec3 V, vec3 L, vec3 radiance) {
    float nl = max(dot(N, L), 0.0);
    if (nl <= 0.0) return vec3(0.0);
    vec3 H = normalize(L + V);
    float nv = clamp(dot(N, V), 1e-3, 1.0), nh = clamp(dot(N, H), 0.0, 1.0), vh = clamp(dot(V, H), 0.0, 1.0);
    float a = max(rough * rough, 0.002);
    vec3 F = F_Schlick(f0, vh);
    vec3 spec = D_GGX(nh, a) * V_Smith(nv, nl, a) * F;
    vec3 diff = (1.0 - F) * (1.0 - metal) * albedo / PI;
    return (diff + spec) * radiance * nl;
}

// Himmel in Richtung r, unscharf nach Rauheit; unter dem Horizont: dunkler Boden mit Himmelslicht
vec3 envSample(vec3 r, float rough) {
    float lod = rough * 7.0;
    vec3 rr = normalize(vec3(r.x, max(r.y, 0.0), r.z));
    vec3 sky = textureLod(uSkyLut, skyUV(rr), lod).rgb;
    vec3 ground = textureLod(uSkyLut, vec2(0.0, 0.98), 4.0).rgb * 0.12;
    return mix(sky, ground, smoothstep(0.0, -0.15, r.y));
}

vec3 shadeSurface(Surf s, vec3 N, vec3 V, float ao, float sunVis) {
    // Nässe: dunklere Farbe, glatterer Film, am stärksten auf waagerechten Flächen
    float wetK = uWeather.y * (0.45 + 0.55 * clamp(N.y, 0.0, 1.0));
    s.albedo *= 1.0 - 0.38 * wetK;
    s.rough = mix(s.rough, 0.16, 0.8 * wetK);
    vec3 f0 = mix(vec3(0.04), s.albedo, s.metal);
    vec3 c = directLight(s.albedo, f0, s.rough, s.metal, N, V, uSunDir, uSunColor * sunVis);
    c += directLight(s.albedo, f0, max(s.rough, 0.3), s.metal, N, V, uMoonDir, uMoonColor);
    float nv = clamp(dot(N, V), 1e-3, 1.0);
    vec3 E = skyIrradiance(uSkyLut, N, uSunDir);
    // Im Schatten fehlt das Himmelslicht nicht ganz, aber der Himmel ist nach der Sonnenseite verdeckt
    E *= mix(0.75, 1.0, sunVis);
    vec3 kS = envBRDF(f0, s.rough, nv);
    c += (1.0 - kS) * (1.0 - s.metal) * s.albedo / PI * E * ao;
    vec3 R = reflect(-V, N);
    float specOcc = clamp(pow(nv + ao, exp2(-16.0 * s.rough - 1.0)) - 1.0 + ao, 0.0, 1.0);
    c += envSample(R, s.rough) * kS * specOcc;
    return c;
}
