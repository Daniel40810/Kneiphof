#!/usr/bin/env python3
"""Prüfstand ohne JOGL: übersetzt die Shader des Projekts unter Mesa (llvmpipe, OpenGL 4.5 Core)
und rechnet Bilder mit denselben Durchgängen wie gl/Renderer.java: Schattenkaskaden, Himmelstabelle,
Himmel, Gelände, Bauteile, Wasser, Belichtungsmessung, Bloom, Nachbearbeitung, FXAA.
Gelände, Prüfstand und alle Werte (Kamera, Sonne, Kaskaden) kommen von tools/FrameDump (Java).

Aufruf: PYOPENGL_PLATFORM=egl python3 glcheck.py <shader-ordner> <framedump-ordner> <ausgabe> [name ...]
"""
import os, struct, sys
import numpy as np
import moderngl
import OpenGL
OpenGL.ERROR_CHECKING = False
from OpenGL import GL
from PIL import Image

SH, DATA, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
ONLY = set(sys.argv[4:])
W, H = int(os.environ.get('W', 1280)), int(os.environ.get('H', 720))
SHADOW = 2048
os.makedirs(OUT, exist_ok=True)


def src(name, seen=None):
    seen = seen if seen is not None else set()
    if name in seen:
        return ''
    seen.add(name)
    out = []
    for line in open(os.path.join(SH, name), encoding='utf-8').read().split('\n'):
        out.append(src(line.split('"')[1], seen) if line.strip().startswith('#include') else line)
    return '\n'.join(out)


ctx = moderngl.create_standalone_context(backend='egl', require=430)
print(ctx.info['GL_RENDERER'], ctx.info['GL_VERSION'])


def prog(vs, fs):
    try:
        return ctx.program(vertex_shader='#version 430 core\n' + src(vs), fragment_shader='#version 430 core\n' + src(fs))
    except Exception as e:
        print('FEHLER', vs, fs, e)
        raise


P = {k: prog(*v) for k, v in dict(
    lut=('fullscreen.vert', 'skylut.frag'), sky=('fullscreen.vert', 'sky.frag'), ter=('terrain.vert', 'terrain.frag'),
    obj=('object.vert', 'object.frag'), sdep=('shadow_depth.vert', 'shadow_depth.frag'), wat=('water.vert', 'water.frag'),
    down=('fullscreen.vert', 'bloom_down.frag'), up=('fullscreen.vert', 'bloom_up.frag'), post=('fullscreen.vert', 'post.frag'),
    fxaa=('fullscreen.vert', 'fxaa.frag'), tree=('tree.vert', 'tree.frag'), tdep=('tree_depth.vert', 'shadow_depth.frag'), shaf=('fullscreen.vert', 'shafts.frag'), beam=('beam.vert', 'beam.frag'), part=('particle.vert', 'particle.frag'), blit=('fullscreen.vert', 'blit.frag')).items()}
print('Shader übersetzt:', ', '.join(P))


def load(name):
    with open(os.path.join(DATA, name), 'rb') as f:
        nv, ni = struct.unpack('<ii', f.read(8))
        return np.frombuffer(f.read(nv * 4), dtype='<f4'), np.frombuffer(f.read(ni * 4), dtype='<i4')


PART = np.zeros((0, 8), dtype='f4')
if os.path.exists(os.path.join(DATA, 'particles.bin')):
    with open(os.path.join(DATA, 'particles.bin'), 'rb') as f:
        _n = struct.unpack('<i', f.read(4))[0]
        PART = np.frombuffer(f.read(_n * 32), dtype='<f4').reshape(_n, 8).copy()
tv, ti = load('terrain.bin')
bv, bi = load('testbed.bin')
vbo_t, ibo_t = ctx.buffer(tv.tobytes()), ctx.buffer(ti.tobytes())
vbo_b, ibo_b = ctx.buffer(bv.tobytes()), ctx.buffer(bi.tobytes())
vao_ter = ctx.vertex_array(P['ter'], [(vbo_t, '3f 3f 4f', 'aPos', 'aNormal', 'aCover')], ibo_t)
vao_obj = ctx.vertex_array(P['obj'], [(vbo_b, '3f 3f 3f 2f 1f 1f', 'aPos', 'aNormal', 'aTangent', 'aUV', 'aMat', 'aAO')], ibo_b)
vao_sdep = ctx.vertex_array(P['sdep'], [(vbo_b, '3f 40x', 'aPos')], ibo_b)
# Bäume: Pflanzorte und Netze je Tag (wie TreeRenderer.java)
spots = np.loadtxt(os.path.join(DATA, 'trees.txt'), dtype='f4').reshape(-1, 7)
TREES = {}
def load_trees(day):
    if day in TREES:
        return TREES[day]
    meshes = {}
    with open(os.path.join(DATA, 'trees_%d.bin' % day), 'rb') as f:
        while True:
            h = f.read(20)
            if len(h) < 20:
                break
            sp, va, lod, nv, ni = struct.unpack('<5i', h)
            v = np.frombuffer(f.read(nv * 4), dtype='<f4'); ix = np.frombuffer(f.read(ni * 4), dtype='<i4')
            meshes[(sp, va, lod)] = (ctx.buffer(v.tobytes()), ctx.buffer(ix.tobytes()), ni)
    TREES[day] = meshes
    return meshes

def tree_buckets(cam):
    d2 = (spots[:, 2] - cam[0]) ** 2 + (spots[:, 3] + 8 - cam[1]) ** 2 + (spots[:, 4] - cam[2]) ** 2
    lod = np.where(d2 < 110 ** 2, 0, np.where(d2 < 320 ** 2, 1, np.where(d2 < 900 ** 2, 2, 3)))
    near = np.nonzero(lod == 0)[0]
    if len(near) > 70:
        lim = np.sort(d2[near])[69]
        lod[(lod == 0) & (d2 > lim)] = 1
    b = {}
    for key in set(zip(spots[:, 0].astype(int), spots[:, 1].astype(int), lod)):
        sel = (spots[:, 0] == key[0]) & (spots[:, 1] == key[1]) & (lod == key[2])
        inst = spots[sel][:, [2, 3, 4, 5, 6]].astype('f4')
        b[key] = inst
    return b

def draw_trees(prog, meshes, buckets, shadow=False):
    for (sp, va, lod), inst in buckets.items():
        if shadow:
            if lod >= 2:
                continue
            lod = 1
        else:
            lod = [0, 1, 1, 2][lod]
        vb, ib, ni = meshes[(sp, va, lod)]
        ibuf = ctx.buffer(inst.tobytes())
        if shadow:
            vao = ctx.vertex_array(prog, [(vb, '3f 32x', 'aPos'), (ibuf, '4f 1f/i', 'aInst', 'aScale')], ib)
        else:
            vao = ctx.vertex_array(prog, [(vb, '3f 3f 3f 1f 1f', 'aPos', 'aNormal', 'aColor', 'aLeaf', 'aHf'), (ibuf, '4f 1f/i', 'aInst', 'aScale')], ib)
        vao.render(moderngl.TRIANGLES, instances=len(inst))
        vao.release(); ibuf.release()

empty = {k: ctx.vertex_array(P[k], []) for k in ('lut', 'sky', 'wat', 'down', 'up', 'post', 'fxaa', 'shaf', 'blit')}

lut = ctx.texture((256, 128), 4, dtype='f2'); lut.repeat_x = True; lut.repeat_y = False
lut.filter = (moderngl.LINEAR_MIPMAP_LINEAR, moderngl.LINEAR)
fb_lut = ctx.framebuffer([lut])
hdr = ctx.texture((W, H), 4, dtype='f2'); hdr.repeat_x = hdr.repeat_y = False
depth = ctx.depth_texture((W, H))
fb_hdr = ctx.framebuffer([hdr], depth)
ldr = ctx.texture((W, H), 4); ldr.repeat_x = ldr.repeat_y = False
fb_ldr = ctx.framebuffer([ldr])
out_tex = ctx.texture((W, H), 4); fb_out = ctx.framebuffer([out_tex])
blooms, bw, bh = [], W, H
for i in range(6):
    bw, bh = max(1, bw // 2), max(1, bh // 2)
    t = ctx.texture((bw, bh), 4, dtype='f2'); t.repeat_x = t.repeat_y = False
    blooms.append((t, ctx.framebuffer([t]), bw, bh))

RW, RH = W // 2, H // 2
refl = ctx.texture((RW, RH), 4, dtype='f2'); refl.repeat_x = refl.repeat_y = False
refl.filter = (moderngl.LINEAR_MIPMAP_LINEAR, moderngl.LINEAR)
refl_depth = ctx.depth_texture((RW, RH))
fb_refl = ctx.framebuffer([refl], refl_depth)
copy_col = ctx.texture((W, H), 4, dtype='f2'); copy_col.repeat_x = copy_col.repeat_y = False
copy_depth = ctx.depth_texture((W, H)); copy_depth.compare_func = ''; copy_depth.filter = (moderngl.NEAREST, moderngl.NEAREST)
fb_copy = ctx.framebuffer([copy_col], copy_depth)
shaft_tex = ctx.texture((W // 2, H // 2), 4, dtype='f2'); shaft_tex.repeat_x = shaft_tex.repeat_y = False
fb_shaft = ctx.framebuffer([shaft_tex])
WAKES = np.loadtxt(os.path.join(DATA, 'wakes.txt'), dtype='f4').reshape(-1, 8) if os.path.exists(os.path.join(DATA, 'wakes.txt')) else np.zeros((0, 8), 'f4')
WAKE_A = np.zeros((8, 4), 'f4'); WAKE_B = np.zeros((8, 4), 'f4')
for i, wk in enumerate(WAKES[:8]):
    WAKE_A[i] = wk[0:4]; WAKE_B[i] = wk[4:8]

# Schattenkarten als Feld (moderngl kennt keine Tiefen-Felder: über PyOpenGL)
shadow_tex = GL.glGenTextures(1)
GL.glBindTexture(GL.GL_TEXTURE_2D_ARRAY, shadow_tex)
GL.glTexStorage3D(GL.GL_TEXTURE_2D_ARRAY, 1, GL.GL_DEPTH_COMPONENT32F, SHADOW, SHADOW, 3)
for k, v in ((GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR), (GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR),
             (GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_BORDER), (GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_BORDER),
             (GL.GL_TEXTURE_COMPARE_MODE, GL.GL_COMPARE_REF_TO_TEXTURE), (GL.GL_TEXTURE_COMPARE_FUNC, GL.GL_LEQUAL)):
    GL.glTexParameteri(GL.GL_TEXTURE_2D_ARRAY, k, v)
GL.glTexParameterfv(GL.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_BORDER_COLOR, [1, 1, 1, 1])
shadow_fb = []
for c in range(3):
    f = GL.glGenFramebuffers(1)
    GL.glBindFramebuffer(GL.GL_FRAMEBUFFER, f)
    GL.glFramebufferTextureLayer(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, shadow_tex, 0, c)
    GL.glDrawBuffer(GL.GL_NONE)
    shadow_fb.append(ctx.detect_framebuffer(int(f)))


def setu(p, name, v):
    if name == 'uShadow':   # moderngl kennt sampler2DArrayShadow nicht richtig: direkt setzen
        loc = GL.glGetUniformLocation(p.glo, b'uShadow')
        if loc >= 0:
            GL.glProgramUniform1i(p.glo, loc, int(v))
        return
    if name in p:
        p[name].value = v


KEY = float(os.environ.get('KEY', 0.055))


def render(t, expo):
    global weather
    vec = lambda key, n: tuple(float(x) for x in t[t.index(key) + 1:t.index(key) + 1 + n])
    vp = vec('vp', 16); cam = vec('cam', 3); fwd = vec('fwd', 3); sun = vec('sun', 3); suncol = vec('suncol', 3)
    moon = vec('moon', 3); mooncol = vec('mooncol', 3); stars = vec('stars', 1)[0]; night = vec('night', 1)[0]
    moonlit = vec('moonlit', 1)[0]; ext = vec('ext', 3); haze = vec('haze', 1)[0]; mist = vec('mist', 1)[0]; weather = vec('weather', 4); wind = vec('wind', 2); flow = vec('flow', 2)
    lvp = vec('lvp', 48); splits = vec('splits', 3); texel = vec('texel', 3); sunel = vec('sunel', 1)[0]
    m = np.array(vp, dtype='f4').reshape(4, 4); inv = np.linalg.inv(m.T).T.astype('f4')
    sc = tuple(c * expo for c in suncol); mc = tuple(c * expo for c in mooncol)
    shadow_on = 1.0 if sunel > -1 else 0.0
    day = int(vec('day', 1)[0]); lamp = vec('lamp', 1)[0]
    meshes = load_trees(172 if abs(day - 172) < abs(day - 285) else 285)
    buckets = tree_buckets(cam)
    # 1 Schatten
    ctx.enable(moderngl.DEPTH_TEST); ctx.depth_func = '<'
    GL.glEnable(GL.GL_POLYGON_OFFSET_FILL); GL.glPolygonOffset(1.6, 2.0)
    for c in range(3):
        shadow_fb[c].use(); ctx.viewport = (0, 0, SHADOW, SHADOW)
        shadow_fb[c].clear(depth=1.0)
        P['sdep']['uLightVP'].value = lvp[16 * c:16 * c + 16]
        vao_sdep.render(moderngl.TRIANGLES)
        P['tdep']['uLightVP'].value = lvp[16 * c:16 * c + 16]
        setu(P['tdep'], 'uTime', 12.5); setu(P['tdep'], 'uWind', wind)
        draw_trees(P['tdep'], meshes, buckets, shadow=True)
    GL.glDisable(GL.GL_POLYGON_OFFSET_FILL)
    ctx.disable(moderngl.DEPTH_TEST)
    # 2 Himmelstabelle
    fb_lut.use(); ctx.viewport = (0, 0, 256, 128)
    for k, v in dict(uSunDir=sun, uMoonDir=moon, uMoonLit=moonlit, uSunPower=22.0, uMie=haze, uMieG=0.76, uScale=expo, uOvercast=weather[3]).items():
        setu(P['lut'], k, v)
    empty['lut'].render(moderngl.TRIANGLES, vertices=3)
    lut.build_mipmaps()
    # 3 Szene
    lut.use(0)
    GL.glActiveTexture(GL.GL_TEXTURE1); GL.glBindTexture(GL.GL_TEXTURE_2D_ARRAY, shadow_tex); GL.glActiveTexture(GL.GL_TEXTURE0)

    def scene(mm, inv_m, camp, fwdv, mirror):
        for k in ('sky', 'ter', 'obj', 'wat', 'tree'):
            p = P[k]
            for name, v in dict(uSkyLut=0, uShadow=1, uCamPos=camp, uCamFwd=fwdv, uSunDir=sun, uSunColor=sc, uMoonDir=moon, uMoonColor=mc,
                                uStars=stars * expo, uExtinction=ext, uTime=12.5, uWind=wind, uFlow=flow, uSplits=splits, uTexel=texel,
                                uZeroOne=0, uShadowOn=shadow_on, uViewProj=tuple(mm.flatten()), uInvViewProj=tuple(inv_m.flatten()),
                                uMirror=1.0 if mirror else 0.0, uMoonLit=moonlit, uClipY=0.02 if mirror else -1.0e4, uMist=(mist, 22.0, camp[1], 0.0), uWeather=tuple(weather)).items():
                setu(p, name, v)
            for nm in ('uLightVP[0]', 'uLightVP'):
                if nm in p:
                    p[nm].write(np.array(lvp, dtype='f4').tobytes())
                    break
        ctx.disable(moderngl.DEPTH_TEST | moderngl.CULL_FACE)
        empty['sky'].render(moderngl.TRIANGLES, vertices=3)
        if mirror: GL.glEnable(GL.GL_CLIP_DISTANCE0)
        ctx.enable(moderngl.DEPTH_TEST | moderngl.CULL_FACE); ctx.depth_func = '<'
        vao_ter.render(moderngl.TRIANGLES)
        setu(P['obj'], 'uLampColor', (22 * lamp, 16 * lamp, 8 * lamp)); setu(P['obj'], 'uTimeLamp', 3.3); setu(P['obj'], 'uExposure', expo)
        vao_obj.render(moderngl.TRIANGLES)
        ctx.disable(moderngl.CULL_FACE)
        if not os.environ.get('NOTREES'): draw_trees(P['tree'], meshes, buckets)

    # 3a Spiegelung
    mir = np.diag([1.0, -1.0, 1.0, 1.0]).astype('f4')
    mR = (mir @ m).astype('f4'); invR = np.linalg.inv(mR.T).T.astype('f4')
    if not os.environ.get('NOREFL'):
        fb_refl.use(); ctx.viewport = (0, 0, RW, RH); fb_refl.clear(0, 0, 0, 1, depth=1.0)
        GL.glFrontFace(GL.GL_CW)
        scene(mR, invR, (cam[0], -cam[1], cam[2]), (fwd[0], -fwd[1], fwd[2]), True)
        GL.glDisable(GL.GL_CLIP_DISTANCE0); GL.glFrontFace(GL.GL_CCW)
        refl.build_mipmaps()
    # 3b Szene
    fb_hdr.use(); ctx.viewport = (0, 0, W, H); fb_hdr.clear(0, 0, 0, 1, depth=1.0)
    scene(m, inv, cam, fwd, False)
    # 3c Kopie
    GL.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, fb_hdr.glo); GL.glBindFramebuffer(GL.GL_DRAW_FRAMEBUFFER, fb_copy.glo)
    GL.glBlitFramebuffer(0, 0, W, H, 0, 0, W, H, GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST)
    fb_hdr.use()
    ctx.enable(moderngl.DEPTH_TEST | moderngl.CULL_FACE); ctx.depth_func = '<'; ctx.disable(moderngl.CULL_FACE)
    refl.use(2); copy_col.use(3); copy_depth.use(4)
    w = P['wat']
    for name, v in dict(uRefl=2, uSceneColor=3, uSceneDepth=4, uScreen=(float(W), float(H)), uPlanar=0.0 if os.environ.get('NOREFL') else 1.0,
                        uReflLevels=float(int(np.log2(max(RW, RH)))), uWakeN=len(WAKES[:8]), uRiver=1.0).items():
        setu(w, name, v)
    for nm, arr in (('uWakeA', WAKE_A), ('uWakeB', WAKE_B)):
        for key in (nm + '[0]', nm):
            if key in w:
                w[key].write(arr.tobytes()); break
    setu(w, 'uWaterY', 0.0); setu(w, 'uRect', (0.0, 0.0, 16000.0, 16000.0)); setu(w, 'uWaveScale', 1.0); setu(w, 'uClear', 0.0)
    empty['wat'].render(moderngl.TRIANGLE_STRIP, vertices=4)
    bx, bz, bh_, by = BASIN
    setu(w, 'uRiver', 0.0); setu(w, 'uPlanar', 0.0); setu(w, 'uWakeN', 0)
    setu(w, 'uWaterY', by); setu(w, 'uRect', (bx, bz, bh_, bh_)); setu(w, 'uWaveScale', 0.25); setu(w, 'uFlow', (0.0, 0.0)); setu(w, 'uClear', 1.0); setu(w, 'uDepth', by - 3.0 - 0.13); setu(w, 'uFloor', (0.30, 0.32, 0.31))
    empty['wat'].render(moderngl.TRIANGLE_STRIP, vertices=4)
    ctx.disable(moderngl.DEPTH_TEST)
    if len(PART) and not os.environ.get('NOSMOKE'):
        d2 = ((PART[:, :3] - np.array(cam, dtype='f4')) ** 2).sum(axis=1)
        pb = ctx.buffer(np.ascontiguousarray(PART[np.argsort(-d2)]).tobytes())
        pv = ctx.vertex_array(P['part'], [(pb, '4f 4f/i', 'iPos', 'iA')])
        f_ = np.array(fwd, dtype='f4'); f_ /= np.linalg.norm(f_)
        rt = np.cross(f_, np.array([0, 1, 0], dtype='f4')); rt /= np.linalg.norm(rt); upv = np.cross(rt, f_)
        pp = P['part']
        for name, v in dict(uSkyLut=0, uCamPos=cam, uSunDir=sun, uSunColor=sc, uMoonDir=moon, uMoonColor=mc, uExtinction=ext,
                            uMist=(mist, 22.0, cam[1], 0.0), uWeather=tuple(weather), uViewProj=tuple(m.flatten()), uInvViewProj2=tuple(inv.flatten()),
                            uRight=tuple(rt), uUp=tuple(upv), uSceneDepth=4, uScreen=(float(W), float(H)), uZeroOne2=0.0).items():
            setu(pp, name, v)
        lut.use(0); copy_depth.use(4)
        ctx.enable(moderngl.DEPTH_TEST | moderngl.BLEND); ctx.depth_func = '<'; ctx.blend_func = moderngl.ONE, moderngl.ONE_MINUS_SRC_ALPHA
        ctx.depth_mask = False
        pv.render(moderngl.TRIANGLE_STRIP, vertices=4, instances=len(PART))
        ctx.depth_mask = True
        ctx.disable(moderngl.BLEND | moderngl.DEPTH_TEST)
    bf = os.path.join(DATA, 'beams_%s.bin' % t[0])
    if os.path.exists(bf) and not os.environ.get('NOBEAM'):
        with open(bf, 'rb') as f:
            nv, ni = struct.unpack('<ii', f.read(8))
            bvv = f.read(nv * 28); bii = f.read(ni * 4)
        if ni:
            bvbo = ctx.buffer(bvv); bibo = ctx.buffer(bii)
            bvao = ctx.vertex_array(P['beam'], [(bvbo, '3f 3f 1f', 'aPos', 'aNormal', 'aT')], bibo)
            bp_ = P['beam']
            for name, v in dict(uSkyLut=0, uCamPos=cam, uSunDir=sun, uSunColor=sc, uTime=12.5, uViewProj=tuple(m.flatten()),
                                uSceneDepth=4, uInvViewProj2=tuple(inv.flatten()), uScreen=(float(W), float(H)), uZeroOne2=0.0,
                                uStrength=float(os.environ.get('BEAMK', 0.006)), uExtinction=ext, uMist=(0.0, 22.0, cam[1], 0.0)).items():
                setu(bp_, name, v)
            lut.use(0); copy_depth.use(4)
            ctx.enable(moderngl.DEPTH_TEST | moderngl.BLEND); ctx.depth_func = '<'; ctx.blend_func = moderngl.ONE, moderngl.ONE
            ctx.depth_mask = False
            bvao.render(moderngl.TRIANGLES)
            ctx.depth_mask = True
            ctx.disable(moderngl.BLEND | moderngl.DEPTH_TEST)
    if not os.environ.get('NOSHAFT') and shadow_on > 0.5:
        fb_shaft.use(); ctx.viewport = (0, 0, W // 2, H // 2)
        sp = P['shaf']
        for name, v in dict(uSkyLut=0, uShadow=1, uCamPos=cam, uCamFwd=fwd, uSunDir=sun, uSunColor=sc, uSplits=splits, uTexel=texel,
                            uZeroOne=0, uShadowOn=shadow_on, uViewProj=tuple(m.flatten()), uInvViewProj=tuple(inv.flatten()),
                            uMist=(mist, 22.0, cam[1], 0.0), uDepth=5, uFull=(float(W), float(H)), uHaze=0.00017).items():
            setu(sp, name, v)
        for nm in ('uLightVP[0]', 'uLightVP'):
            if nm in sp:
                sp[nm].write(np.array(lvp, dtype='f4').tobytes()); break
        GL.glActiveTexture(GL.GL_TEXTURE5); copy_depth.use(5); GL.glActiveTexture(GL.GL_TEXTURE0)
        lut.use(0)
        empty['shaf'].render(moderngl.TRIANGLES, vertices=3)
        fb_hdr.use(); ctx.viewport = (0, 0, W, H)
        ctx.enable(moderngl.BLEND); ctx.blend_func = moderngl.ONE, moderngl.ONE
        shaft_tex.use(0); setu(P['blit'], 'uTex', 0)
        empty['blit'].render(moderngl.TRIANGLES, vertices=3)
        ctx.disable(moderngl.BLEND)
        lut.use(0)
    # Messung (wie lum.frag, hier auf der CPU)
    hh = np.frombuffer(fb_hdr.read(components=4, dtype='f2'), dtype='f2').astype('f4').reshape(H, W, 4)[:, :, :3]
    lum = np.clip(hh @ np.array([0.2126, 0.7152, 0.0722], dtype='f4'), 1e-5, 8.0)
    yy, xx = np.mgrid[0:H, 0:W]
    wgt = 1.0 - 1.2 * ((xx / W - 0.5) ** 2 + (yy / H - 0.5) ** 2)
    bad = ~np.isfinite(hh).all(axis=2)
    if bad.any():
        ys, xs = np.nonzero(bad); print('  NaN-Punkte', bad.sum(), 'z.B.', xs[:3], ys[:3])
    lum = np.where(np.isfinite(lum), lum, 1e-5)
    g = float(np.exp((np.log(lum) * wgt).sum() / wgt.sum()))
    return g, night


def finish(name, night):
    global weather
    # 5 Bloom
    src_tex, sw, sh = hdr, W, H
    for i, (t, fb, bw, bh) in enumerate(blooms):
        fb.use(); ctx.viewport = (0, 0, bw, bh); src_tex.use(0)
        setu(P['down'], 'uSrc', 0); setu(P['down'], 'uTexel', (1.0 / sw, 1.0 / sh)); setu(P['down'], 'uFirst', 1 if i == 0 else 0)
        empty['down'].render(moderngl.TRIANGLES, vertices=3)
        src_tex, sw, sh = t, bw, bh
    ctx.enable(moderngl.BLEND); ctx.blend_func = moderngl.ONE, moderngl.ONE
    for i in range(5, 0, -1):
        t, fb, bw, bh = blooms[i]
        blooms[i - 1][1].use(); ctx.viewport = (0, 0, blooms[i - 1][2], blooms[i - 1][3]); t.use(0)
        setu(P['up'], 'uSrc', 0); setu(P['up'], 'uTexel', (1.0 / bw, 1.0 / bh)); setu(P['up'], 'uRadius', 1.0)
        empty['up'].render(moderngl.TRIANGLES, vertices=3)
    ctx.disable(moderngl.BLEND)
    # 6 Nachbearbeitung, FXAA
    fb_ldr.use(); ctx.viewport = (0, 0, W, H); hdr.use(0); blooms[0][0].use(1)
    for k, v in dict(uHdr=0, uBloom=1, uBloomStrength=0.05, uBloomNorm=1 / 6, uExposure=1.0, uVignette=0.30, uTime=0.3, uNight=night, uWeather=tuple(weather), uFade=1.0, uBars=float(os.environ.get('BARS', 0.0))).items():
        setu(P['post'], k, v)
    empty['post'].render(moderngl.TRIANGLES, vertices=3)
    fb_out.use(); ldr.use(0)
    for k, v in dict(uLdr=0, uTexel=(1.0 / W, 1.0 / H), uOn=1).items():
        setu(P['fxaa'], k, v)
    empty['fxaa'].render(moderngl.TRIANGLES, vertices=3)
    img = Image.frombytes('RGBA', (W, H), fb_out.read(components=4)).transpose(Image.FLIP_TOP_BOTTOM).convert('RGB')
    img.save(os.path.join(OUT, name + '.png'))


BASIN = tuple(float(x) for x in open(os.path.join(DATA, 'basin.txt')).read().split())
for line in open(os.path.join(DATA, 'frames.txt'), encoding='utf-8'):
    t = line.split()
    if ONLY and t[0] not in ONLY:
        continue
    expo = float(t[t.index('exposure') + 1])
    for it in range(4):
        g, night = render(t, expo)
        expo = KEY * (g / expo) ** -0.88
    g, night = render(t, expo)
    finish(t[0], night)
    print(f'{t[0]:22s} Belichtung {expo:10.4f}  Messung {g:.4f}')
