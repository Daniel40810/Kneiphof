package com.dan.kneiphof.gl;

import com.dan.kneiphof.camera.OrbitCamera;
import com.dan.kneiphof.math.Mat4;
import com.dan.kneiphof.sky.Atmosphere;
import com.dan.kneiphof.world.Banks;
import com.dan.kneiphof.world.Boats;
import com.dan.kneiphof.world.Bridges;
import com.dan.kneiphof.world.MeshBuilder;
import com.dan.kneiphof.world.People;
import com.dan.kneiphof.world.Site;
import com.dan.kneiphof.world.TerrainMesh;
import com.dan.kneiphof.world.Testbed;
import com.dan.kneiphof.world.Town;
import com.dan.kneiphof.world.Trees;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Die Engine auf der Grafikkarte. Je Bild:
 * <ol>
 * <li>Schattenkaskaden der Sonne (3 × 2048², Tiefe 32F, nur Bauteile werfen Schatten).</li>
 * <li>Himmelstabelle (256 × 128): Einfachstreuung für Sonne und Mond, vorbelichtet.</li>
 * <li>Spiegelung: dieselbe Szene mit an der Wasserfläche gespiegelter Kamera in halber Auflösung (RGBA16F mit Mips), unter dem Wasser abgeschnitten.</li>
 * <li>Szene in einen HDR-Puffer (RGBA16F, umgekehrte 32F-Tiefe): Himmel, Gelände, Bauteile, Schiffe; danach Kopie von Farbe und Tiefe; zuletzt Pregel (Spiegelung, Grund, Schaum, Kielwasser) und Becken.</li>
 * <li>Belichtungsmessung auf 128 × 64 Punkten, alle vier Bilder gelesen.</li>
 * <li>Bloom: sechs Halbierungen und zurück.</li>
 * <li>Nachbearbeitung (Filmkurve, Nachtsehen, Randabdunklung) und Kantenglättung (FXAA).</li>
 * </ol>
 */
public final class Renderer implements GLEventListener {
    public static final int MIN_MAJOR = 4, MIN_MINOR = 3;
    static final int BLOOM_LEVELS = 6;

    public final OrbitCamera camera = new OrbitCamera();
    public final SceneState state = new SceneState();
    public final Cascades cascades = new Cascades();
    /** Zeitraffer: Faktor gegenüber der Uhr (0 = Uhr steht). */
    public volatile double timeLapse = 0;
    /** Anteil der Bildgröße, in dem gerechnet wird (0,5 … 1). */
    public volatile double renderScale = 1.0;
    public volatile boolean shadows = true, bloom = true, fxaa = true, showTestbed = false, showTrees = true, showTown = true;
    /** Spiegelung der Ufer und Häuser im Wasser (zweiter Durchgang mit gespiegelter Kamera, halbe Auflösung). */
    public volatile boolean reflection = true, showBoats = true, lightShafts = true, showSmoke = true;
    /** Schiff, dem die Kamera folgt (−1: keines). */
    public volatile int follow = -1;
    /** Auflösung selbst regeln: Grafikkarte unter 15 ms je Bild halten (60 Bilder/s mit Luft). */
    public volatile boolean autoScale;
    private long lastScaleNanos;

    private final Shader skyLut = new Shader("Himmelstabelle", null, "fullscreen.vert", "skylut.frag");
    private final Shader sky = new Shader("Himmel", null, "fullscreen.vert", "sky.frag");
    private final Shader terrain = new Shader("Gelände", null, "terrain.vert", "terrain.frag");
    private final Shader object = new Shader("Bauteile", null, "object.vert", "object.frag");
    private final Shader shadowDepth = new Shader("Schattentiefe", null, "shadow_depth.vert", "shadow_depth.frag");
    private final Shader water = new Shader("Wasser", null, "water.vert", "water.frag");
    private final Shader lum = new Shader("Messung", null, "fullscreen.vert", "lum.frag");
    private final Shader bloomDown = new Shader("Bloom ab", null, "fullscreen.vert", "bloom_down.frag");
    private final Shader bloomUp = new Shader("Bloom auf", null, "fullscreen.vert", "bloom_up.frag");
    private final Shader post = new Shader("Nachbearbeitung", null, "fullscreen.vert", "post.frag");
    private final Shader aa = new Shader("FXAA", null, "fullscreen.vert", "fxaa.frag");
    private final Shader tree = new Shader("Bäume", null, "tree.vert", "tree.frag");
    private final Shader treeDepth = new Shader("Baumschatten", null, "tree_depth.vert", "shadow_depth.frag");
    private final Shader shafts = new Shader("Lichtstrahlen", null, "fullscreen.vert", "shafts.frag");
    private final Shader blit = new Shader("Blit", null, "fullscreen.vert", "blit.frag");
    private final Shader particle = new Shader("Rauch", null, "particle.vert", "particle.frag");
    private final Particles particles = new Particles();
    private final Shader beam = new Shader("Domstrahlen", null, "beam.vert", "beam.frag");
    public final com.dan.kneiphof.camera.Director director = new com.dan.kneiphof.camera.Director();
    private double lapseSaved = -1;
    private boolean tourWas;
    private final DomBeams domBeams = new DomBeams();
    private final Gpu.Mesh beamMesh = new Gpu.Mesh();
    private final Shader[] all = {particle, beam, skyLut, sky, terrain, object, shadowDepth, water, lum, bloomDown, bloomUp, post, aa, tree, treeDepth, shafts, blit};

    private int emptyVao;
    private java.util.List<double[]> chimneys;
    private final Gpu.Mesh terrainMesh = new Gpu.Mesh(), testbed = new Gpu.Mesh(), banksMesh = new Gpu.Mesh();
    private final TownRenderer townTiles = new TownRenderer();
    private final Gpu.Mesh bridgeStatic = new Gpu.Mesh();
    private Gpu.Mesh[] leafMesh = new Gpu.Mesh[0];
    private Bridges bridges;
    private Boats boats;
    private Gpu.Mesh[] boatMesh = new Gpu.Mesh[0];
    private People people;
    private final com.dan.kneiphof.world.Gulls gulls = new com.dan.kneiphof.world.Gulls();
    private final Gpu.Mesh[] gullMesh = new Gpu.Mesh[com.dan.kneiphof.world.Gulls.FRAMES];
    public volatile boolean showGulls = true;
    /** Foto: wird nach dem nächsten fertigen Bild aus dem Bildspeicher gelesen und als PNG neben das Projekt gelegt. */
    public volatile boolean photoRequest;
    private volatile String photoMsg = "";
    private volatile long photoMsgUntil;
    private final Gpu.Mesh[] personMesh = new Gpu.Mesh[People.VARIANTS * 2];
    public volatile boolean showPeople = true;
    /** Öffnung jeder Brücke 0..1 (aktuell), Wunsch, Betrieb mit Zufallsöffnungen. */
    public final float[] bridgeOpen = new float[7];
    public final boolean[] bridgeWant = new boolean[7];
    public volatile boolean bridgeAuto;
    private final double[] bridgeCloseAt = new double[7];
    private double bridgeTimer = 15;
    private static final float[] IDENT = com.dan.kneiphof.math.Mat4.identity();
    private TreeRenderer trees;
    private CompletableFuture<World> pendingWorld;
    private double lastSortX = 1e9, lastSortY, lastSortZ;
    private int sortAge;

    /** Was im Hintergrund gebaut wird: Ufer und Bäume. */
    static final class World {
        final Banks banks;
        final MeshBuilder banksMesh;
        final java.util.List<Town.Tile> townTiles;
        final int townTris;
        final Town town;
        final Trees trees;
        final Bridges bridges;
        final Boats boats = new Boats();
        People people;
        World() {
            vorabSchritt = 0;
            vorabText = "Ufer und Kaimauern";
            banks = new Banks();
            banksMesh = banks.build();
            vorabSchritt = 1;
            vorabText = "Brücken, Schiffe und Fußgänger";
            bridges = new Bridges();
            boats.attach(bridges);
            people = new People(bridges);
            vorabSchritt = 2;
            vorabText = "Häuser und Dom";
            town = new Town();
            town.reserveBridges(bridges);
            townTiles = town.buildTiles();
            vorabSchritt = 3;
            vorabText = "Bäume";
            int tr = 0;
            for (Town.Tile t : townTiles) tr += t.mb.indexCount() / 3;
            townTris = tr;
            trees = new Trees();
            trees.town = town;
            trees.plant(banks);
            vorabSchritt = 4;
            vorabText = "fertig";
        }
    }

    // ------------------------------------------------------------------ Vorab bauen (Startbild)

    private static CompletableFuture<TerrainMesh> preTerrain;
    private static CompletableFuture<MeshBuilder> preTestbed;
    private static CompletableFuture<World> preWorld;
    private static volatile int vorabSchritt;
    private static volatile String vorabText = "Gelände";
    private static volatile boolean vorabGelaende, vorabPruefstand;

    /** Startet den Aufbau von Gelände, Prüfstand und Stadt im Hintergrund, noch vor dem Fenster (vom Startbild gezeigt). */
    public static synchronized void vorab() {
        if (preWorld != null) return;
        preTerrain = CompletableFuture.supplyAsync(() -> { TerrainMesh t = new TerrainMesh(640); vorabGelaende = true; return t; });
        preTestbed = CompletableFuture.supplyAsync(() -> { MeshBuilder m = Testbed.build(); vorabPruefstand = true; return m; });
        preWorld = CompletableFuture.supplyAsync(World::new);
    }

    /** Fortschritt des Vorab-Aufbaus, 0 bis 1. */
    public static double vorabFortschritt() {
        if (preWorld == null) return 0;
        if (preWorld.isDone() && vorabGelaende && vorabPruefstand) return 1;
        return Math.min(0.99, (vorabGelaende ? 0.2 : 0) + (vorabPruefstand ? 0.03 : 0) + 0.77 * vorabSchritt / 4.0);
    }

    public static String vorabText() { return vorabGelaende || vorabSchritt > 0 ? vorabText : "Gelände"; }
    private int lutTex, lutFbo, hdrTex, depthTex, hdrFbo, lumTex, lumFbo, ldrTex, ldrFbo;
    private int reflTex, reflDepth, reflFbo, reflW, reflH, copyTex, copyDepth, copyFbo, shaftTex, shaftFbo;
    private static final float[] MIRROR = {1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    private final float[] wakeA = new float[4 * Boats.MAX], wakeB = new float[4 * Boats.MAX];
    private int wakeN;
    private int shadowTex;
    private final int[] shadowFbo = new int[Cascades.COUNT];
    private final int[] bloomTex = new int[BLOOM_LEVELS], bloomFbo = new int[BLOOM_LEVELS], bloomW = new int[BLOOM_LEVELS], bloomH = new int[BLOOM_LEVELS];
    private int hdrW, hdrH, surfW, surfH;
    private boolean reversedZ, ready;
    private CompletableFuture<TerrainMesh> pendingTerrain;
    private CompletableFuture<MeshBuilder> pendingTestbed;
    private long lastNanos;
    private double simTime;
    private int frame;
    private final FloatBuffer lumRead = Buffers.newDirectFloatBuffer(4);

    // Anzeige
    private String glInfo = "";
    private volatile String error;
    private double fps;
    private long statsNanos;
    private int statsFrames;
    private double gpuMs;
    private Consumer<String> statusListener = s -> { };
    private Consumer<String> errorListener = s -> { };
    private volatile boolean reloadShaders;
    private final int[] timer = new int[2];
    private int timerIdx;
    private boolean timerArmed;

    public void setStatusListener(Consumer<String> l) { statusListener = l; }
    public void setErrorListener(Consumer<String> l) { errorListener = l; }
    public String glInfo() { return glInfo; }
    public String error() { return error; }
    public void requestShaderReload() { reloadShaders = true; }
    public double fps() { return fps; }
    public double gpuMs() { return gpuMs; }

    // ------------------------------------------------------------------ Lebenslauf

    @Override
    public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        int major = d.getContext().getGLVersionNumber().getMajor(), minor = d.getContext().getGLVersionNumber().getMinor();
        String renderer = gl.glGetString(GL.GL_RENDERER), vendor = gl.glGetString(GL.GL_VENDOR), version = gl.glGetString(GL.GL_VERSION);
        glInfo = renderer + " · OpenGL " + major + "." + minor;
        System.out.println("Kneiphof: " + vendor + " · " + renderer + " · " + version);
        if (major < MIN_MAJOR || (major == MIN_MAJOR && minor < MIN_MINOR)) {
            fail("Die Grafikkarte meldet OpenGL " + major + "." + minor + " (" + renderer + ", Treiber " + version
                    + "). Kneiphof braucht OpenGL " + MIN_MAJOR + "." + MIN_MINOR + ".\nBitte den Grafiktreiber aktualisieren.");
            return;
        }
        gl.setSwapInterval(1);
        reversedZ = (major > 4 || minor >= 5) || gl.isExtensionAvailable("GL_ARB_clip_control");
        if (reversedZ) gl.glClipControl(GL4.GL_LOWER_LEFT, GL4.GL_ZERO_TO_ONE);
        try {
            for (Shader s : all) s.build(gl);
        } catch (RuntimeException e) {
            fail(e.getMessage());
            return;
        }
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        emptyVao = v[0];
        particles.init(gl);
        lutTex = Gpu.texture(gl, 256, 128, GL4.GL_RGBA16F, true);
        gl.glBindTexture(GL.GL_TEXTURE_2D, lutTex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        lutFbo = Gpu.fbo(gl, lutTex, 0);
        lumTex = Gpu.texture(gl, 128, 64, GL4.GL_RG16F, true);
        lumFbo = Gpu.fbo(gl, lumTex, 0);
        // Schattenkarten: ein Feld aus drei Tiefenkarten mit Vergleich
        gl.glGenTextures(1, v, 0);
        shadowTex = v[0];
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, shadowTex);
        gl.glTexStorage3D(GL4.GL_TEXTURE_2D_ARRAY, 1, GL4.GL_DEPTH_COMPONENT32F, Cascades.SIZE, Cascades.SIZE, Cascades.COUNT);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_S, GL4.GL_CLAMP_TO_BORDER);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_T, GL4.GL_CLAMP_TO_BORDER);
        gl.glTexParameterfv(GL4.GL_TEXTURE_2D_ARRAY, GL4.GL_TEXTURE_BORDER_COLOR, new float[]{1, 1, 1, 1}, 0);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL4.GL_TEXTURE_COMPARE_MODE, GL4.GL_COMPARE_REF_TO_TEXTURE);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL4.GL_TEXTURE_COMPARE_FUNC, GL.GL_LEQUAL);
        for (int c = 0; c < Cascades.COUNT; c++) {
            gl.glGenFramebuffers(1, v, 0);
            shadowFbo[c] = v[0];
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, shadowFbo[c]);
            gl.glFramebufferTextureLayer(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, shadowTex, 0, c);
            gl.glDrawBuffer(GL.GL_NONE);
            gl.glReadBuffer(GL.GL_NONE);
            int st = gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER);
            if (st != GL.GL_FRAMEBUFFER_COMPLETE) { fail("Schattenkarte unvollständig: 0x" + Integer.toHexString(st)); return; }
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        gl.glGenQueries(2, timer, 0);
        // Gelände und Prüfstand im Hintergrund bauen, damit das Fenster sofort steht
        vorab();
        pendingTerrain = preTerrain;
        pendingTestbed = preTestbed;
        pendingWorld = preWorld;
        camera.snap();
        lastNanos = System.nanoTime();
        statsNanos = lastNanos;
        ready = true;
    }

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {
        surfW = Math.max(1, d.getSurfaceWidth());
        surfH = Math.max(1, d.getSurfaceHeight());
    }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        if (!ready) {
            gl.glClearColor(0.05f, 0.06f, 0.08f, 1);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            return;
        }
        if (reloadShaders) {
            reloadShaders = false;
            try {
                for (Shader s : all) s.build(gl);
                System.out.println("Shader neu geladen.");
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
                errorListener.accept(e.getMessage());
            }
        }
        // Zeit der Grafikkarte für das letzte Bild (Abfrage vom vorletzten, damit nichts wartet)
        if (timerArmed) {
            int[] avail = new int[1];
            gl.glGetQueryObjectiv(timer[timerIdx ^ 1], GL4.GL_QUERY_RESULT_AVAILABLE, avail, 0);
            if (avail[0] != 0) {
                long[] ns = new long[1];
                gl.glGetQueryObjecti64v(timer[timerIdx ^ 1], GL4.GL_QUERY_RESULT, ns, 0);
                gpuMs = gpuMs * 0.9 + ns[0] / 1e6 * 0.1;
            }
        }
        gl.glBeginQuery(GL4.GL_TIME_ELAPSED, timer[timerIdx]);

        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        simTime += dt;
        if (timeLapse > 0) state.clock.advance(dt * timeLapse / 3600.0);
        // Regie: Fahrt führen; jede eigene Bewegung beendet sie
        if (!director.active()) camera.userMoved = false;
        else if (camera.userMoved || camera.walking) director.stop();
        director.update(dt, camera);
        if (tourWas && !director.active()) {
            if (lapseSaved >= 0) { timeLapse = lapseSaved; lapseSaved = -1; }
            camera.fovy = 50;
        }
        tourWas = director.active();
        camera.update(dt);
        updateBridges(dt);
        gulls.update(simTime);
        if (boats != null) {
            boats.update(dt, simTime, bridgeOpen);
            if (people != null) people.update(dt, bridgeOpen, boats.need);
            if (follow >= 0 && follow < boats.list.size() && !camera.walking) {
                Boats.Boat fb = boats.list.get(follow);
                camera.tx = fb.x;
                camera.ty = 2.5;
                camera.tz = fb.z;
            }
            wakeN = 0;
            for (Boats.Boat b : boats.list) {
                if (wakeN >= Boats.MAX) break;
                int k = wakeN * 4;
                wakeA[k] = (float) b.x; wakeA[k + 1] = (float) b.z; wakeA[k + 2] = (float) b.hx; wakeA[k + 3] = (float) b.hz;
                wakeB[k] = (float) b.v; wakeB[k + 1] = (float) b.length; wakeB[k + 2] = (float) b.beam; wakeB[k + 3] = b.name.startsWith("Dampfer") ? 1f : 0.55f;
                wakeN++;
            }
        }
        state.update(dt);
        uploadIfReady(gl);
        if (showSmoke && chimneys != null) {
            double[] wv = state.windVector();
            double cold = 0.5 + 0.5 * Math.cos(2 * Math.PI * (state.clock.day() - 20) / 365.0);
            particles.update(dt, new double[]{camera.ex, camera.ey, camera.ez}, wv, cold, chimneys, boats == null ? null : boats.list, showBoats);
        }
        if (trees != null) {
            trees.update(gl, state.clock.day());
            double mv = Math.abs(camera.ex - lastSortX) + Math.abs(camera.ey - lastSortY) + Math.abs(camera.ez - lastSortZ);
            if (mv > 4 || ++sortAge > 30) {
                trees.sort(gl, camera.ex, camera.ey, camera.ez);
                lastSortX = camera.ex; lastSortY = camera.ey; lastSortZ = camera.ez;
                sortAge = 0;
            }
        }
        boolean drawTrees = showTrees && trees != null && trees.ready();

        surfW = Math.max(1, d.getSurfaceWidth());
        surfH = Math.max(1, d.getSurfaceHeight());
        int w = Math.max(64, (int) Math.round(surfW * renderScale)), h = Math.max(64, (int) Math.round(surfH * renderScale));
        if (w != hdrW || h != hdrH) resizeTargets(gl, w, h);

        float[] view = camera.view();
        float[] proj = camera.projection(w / (double) h, reversedZ);
        float[] vp = Mat4.mul(proj, view);
        float[] inv = Mat4.invert(vp);
        double[] cam = {camera.ex, camera.ey, camera.ez};
        double[] fwd = camera.forward();
        double[] wind = state.windVector();
        boolean sunUp = state.clock.elevationDeg > -1;
        cascades.update(cam, fwd, camera.fovy, w / (double) h, state.clock.dir, camera.distance(), reversedZ);
        gl.glBindVertexArray(emptyVao);

        // 1 Schatten
        boolean shadowPass = shadows && sunUp;
        if (shadowPass) {
            gl.glViewport(0, 0, Cascades.SIZE, Cascades.SIZE);
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(GL.GL_LESS);
            gl.glDepthMask(true);
            gl.glClearDepth(1.0);
            gl.glEnable(GL.GL_POLYGON_OFFSET_FILL);
            gl.glPolygonOffset(1.6f, 2.0f);
            shadowDepth.use(gl);
            for (int c = 0; c < Cascades.COUNT; c++) {
                gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, shadowFbo[c]);
                gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
                shadowDepth.use(gl);
                shadowDepth.mat(gl, "uLightVP", cascades.lightVP[c]);
                if (showTestbed) testbed.draw(gl);
                banksMesh.draw(gl);
                drawBridges(gl, shadowDepth);
                drawBoats(gl, shadowDepth);
                drawPeople(gl, shadowDepth, 80);
                if (showTown) townTiles.draw(gl, cascades.lightVP[c]);
                if (drawTrees) {
                    gl.glDisable(GL.GL_CULL_FACE);
                    treeDepth.use(gl);
                    treeDepth.mat(gl, "uLightVP", cascades.lightVP[c]);
                    treeDepth.set(gl, "uTime", (float) (simTime % 3600));
                    treeDepth.set(gl, "uWind", (float) wind[0], (float) wind[1]);
                    trees.drawShadow(gl);
                }
            }
            gl.glDisable(GL.GL_POLYGON_OFFSET_FILL);
            gl.glBindVertexArray(emptyVao);
        }

        // 2 Himmelstabelle
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, lutFbo);
        gl.glViewport(0, 0, 256, 128);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_CULL_FACE);
        skyLut.use(gl);
        skyLut.set(gl, "uSunDir", state.clock.dir);
        skyLut.set(gl, "uMoonDir", state.clock.moonDir);
        skyLut.set(gl, "uMoonLit", (float) state.clock.moonLit);
        skyLut.set(gl, "uOvercast", (float) state.overcast);
        skyLut.set(gl, "uSunPower", (float) Atmosphere.SUN_POWER);
        skyLut.set(gl, "uMie", (float) state.haze);
        skyLut.set(gl, "uMieG", 0.76f);
        skyLut.set(gl, "uScale", (float) state.exposure);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, lutTex);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);

        // 3 Szene
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, shadowTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, lutTex);
        float shadowOn = shadowPass ? 1f : 0f;

        // 3a Spiegelung: dieselbe Szene mit an der Wasserfläche gespiegelter Kamera, halbe Auflösung,
        // alles unter dem Wasser abgeschnitten (gl_ClipDistance)
        boolean planar = reflection && camera.ey > 0.3 && camera.ey < 220;
        if (planar) {
            float[] vpR = Mat4.mul(vp, MIRROR);
            float[] invR = Mat4.invert(vpR);
            double[] camR = {cam[0], -cam[1], cam[2]};
            double[] fwdR = {fwd[0], -fwd[1], fwd[2]};
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, reflFbo);
            gl.glViewport(0, 0, reflW, reflH);
            gl.glDepthMask(true);
            gl.glClearDepth(reversedZ ? 0.0 : 1.0);
            gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
            gl.glFrontFace(GL.GL_CW);
            drawScene(gl, vpR, invR, camR, fwdR, wind, shadowOn, drawTrees, true);
            gl.glDisable(GL4.GL_CLIP_DISTANCE0);
            gl.glFrontFace(GL.GL_CCW);
            gl.glBindTexture(GL.GL_TEXTURE_2D, reflTex);
            gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
            gl.glBindTexture(GL.GL_TEXTURE_2D, lutTex);
        }

        // 3b Szene aus der Kamera
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glDepthMask(true);
        gl.glClearDepth(reversedZ ? 0.0 : 1.0);
        gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
        drawScene(gl, vp, inv, cam, fwd, wind, shadowOn, drawTrees, false);

        // 3c Kopie von Farbe und Tiefe, damit das Wasser den Grund, Ufer und Pfähle dahinter sieht
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, hdrFbo);
        gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, copyFbo);
        gl.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glBindVertexArray(emptyVao);
        water.use(gl);
        common(gl, water, vp, inv, cam, fwd, wind, shadowOn);
        gl.glActiveTexture(GL.GL_TEXTURE2);
        gl.glBindTexture(GL.GL_TEXTURE_2D, reflTex);
        gl.glActiveTexture(GL.GL_TEXTURE3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, copyTex);
        gl.glActiveTexture(GL.GL_TEXTURE4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, copyDepth);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        water.set(gl, "uRefl", 2);
        water.set(gl, "uSceneColor", 3);
        water.set(gl, "uSceneDepth", 4);
        water.set(gl, "uScreen", (float) w, (float) h);
        water.set(gl, "uPlanar", planar ? 1f : 0f);
        water.set(gl, "uReflLevels", (float) (Math.floor(Math.log(Math.max(reflW, reflH)) / Math.log(2))));
        water.set(gl, "uWakeN", showBoats ? wakeN : 0);
        gl.glUniform4fv(water.loc(gl, "uWakeA[0]"), Boats.MAX, wakeA, 0);
        gl.glUniform4fv(water.loc(gl, "uWakeB[0]"), Boats.MAX, wakeB, 0);
        water.set(gl, "uWaterY", 0f);
        gl.glUniform4f(water.loc(gl, "uRect"), 0f, 0f, (float) Site.FAR, (float) Site.FAR);
        water.set(gl, "uWaveScale", 1f);
        water.set(gl, "uClear", 0f);
        water.set(gl, "uRiver", 1f);
        gl.glDrawArrays(GL.GL_TRIANGLE_STRIP, 0, 4);
        if (showTestbed) {
            water.set(gl, "uRiver", 0f);
            water.set(gl, "uPlanar", 0f);
            water.set(gl, "uWakeN", 0);
            water.set(gl, "uWaterY", (float) Testbed.BASIN_WATER);
            gl.glUniform4f(water.loc(gl, "uRect"), (float) Testbed.BASIN_X, (float) Testbed.BASIN_Z, (float) Testbed.BASIN_HALF, (float) Testbed.BASIN_HALF);
            water.set(gl, "uWaveScale", 0.25f);
            water.set(gl, "uClear", 1f);
            water.set(gl, "uDepth", (float) (Testbed.BASIN_WATER - Testbed.GROUND - 0.13));
            water.set(gl, "uFloor", 0.30f, 0.32f, 0.31f);
            water.set(gl, "uFlow", 0f, 0f);
            gl.glDrawArrays(GL.GL_TRIANGLE_STRIP, 0, 4);
        }
        // 3d Sonnenstrahlen durch die Domfenster (nur wenn die Kamera im Schiff steht)
        double[] fr = com.dan.kneiphof.world.DomInterior.toFrame(camera.ex, camera.ez);
        boolean inDom = fr[0] > com.dan.kneiphof.world.DomInterior.U0 && fr[0] < com.dan.kneiphof.world.DomInterior.U1
                && Math.abs(fr[1]) < com.dan.kneiphof.world.DomInterior.WI && camera.ey < com.dan.kneiphof.world.DomInterior.SPRING + 6;
        if (inDom && lightShafts && state.clock.dir[1] > 0.05) {
            if (domBeams.rebuild(state.clock.dir)) {
                if (domBeams.verts.length > 0) beamMesh.upload(gl, domBeams.verts, domBeams.idx, new int[]{3, 3, 1});
                else beamMesh.dispose(gl);
            }
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthMask(false);
            gl.glDisable(GL.GL_CULL_FACE);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            beam.use(gl);
            common(gl, beam, vp, inv, cam, fwd, wind, shadowOn);
            gl.glActiveTexture(GL.GL_TEXTURE4);
            gl.glBindTexture(GL.GL_TEXTURE_2D, copyDepth);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            beam.set(gl, "uSceneDepth", 4);
            beam.mat(gl, "uInvViewProj2", inv);
            beam.set(gl, "uScreen", (float) w, (float) h);
            beam.set(gl, "uZeroOne2", reversedZ ? 1f : 0f);
            beam.set(gl, "uStrength", 0.0022f);
            beamMesh.draw(gl);
            gl.glDisable(GL.GL_BLEND);
            gl.glDepthMask(true);
            gl.glBindVertexArray(emptyVao);
        }

        // 3d Rauch und Dampf: weiche Flächen, Tiefe nur prüfen
        if (showSmoke && particles.count() > 0) {
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthMask(false);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
            gl.glDisable(GL.GL_CULL_FACE);
            particle.use(gl);
            common(gl, particle, vp, inv, cam, fwd, wind, shadowOn);
            gl.glActiveTexture(GL.GL_TEXTURE4);
            gl.glBindTexture(GL.GL_TEXTURE_2D, copyDepth);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            particle.set(gl, "uSceneDepth", 4);
            particle.mat(gl, "uInvViewProj2", inv);
            particle.set(gl, "uScreen", (float) w, (float) h);
            particle.set(gl, "uZeroOne2", reversedZ ? 1f : 0f);
            float[] vw = camera.view();
            particle.set(gl, "uRight", vw[0], vw[4], vw[8]);
            particle.set(gl, "uUp", vw[1], vw[5], vw[9]);
            particles.draw(gl, cam);
            gl.glDisable(GL.GL_BLEND);
            gl.glDepthMask(true);
            gl.glBindVertexArray(emptyVao);
        }
        gl.glDisable(GL.GL_DEPTH_TEST);

        // 3d Lichtstrahlen: Streuung im Dunst, halbe Auflösung, additiv aufs Bild
        if (lightShafts && shadowPass && !camera.walking && !inDom && (state.clock.elevationDeg < 32 || state.mist > 0.15)) {
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, shaftFbo);
            gl.glViewport(0, 0, Math.max(1, w / 2), Math.max(1, h / 2));
            shafts.use(gl);
            common(gl, shafts, vp, inv, cam, fwd, wind, shadowOn);
            gl.glActiveTexture(GL.GL_TEXTURE5);
            gl.glBindTexture(GL.GL_TEXTURE_2D, copyDepth);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            shafts.set(gl, "uDepth", 5);
            shafts.set(gl, "uFull", (float) w, (float) h);
            shafts.set(gl, "uHaze", 0.00017f);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            blit.use(gl);
            gl.glBindTexture(GL.GL_TEXTURE_2D, shaftTex);
            blit.set(gl, "uTex", 0);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            gl.glDisable(GL.GL_BLEND);
            gl.glBindTexture(GL.GL_TEXTURE_2D, lutTex);
        }

        // 4 Belichtungsmessung
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, lumFbo);
        gl.glViewport(0, 0, 128, 64);
        lum.use(gl);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        lum.set(gl, "uHdr", 0);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        if ((frame & 3) == 0) {
            gl.glBindTexture(GL.GL_TEXTURE_2D, lumTex);
            gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
            lumRead.clear();
            gl.glGetTexImage(GL.GL_TEXTURE_2D, 7, GL4.GL_RG, GL.GL_FLOAT, lumRead);
            float sum = lumRead.get(0), wsum = lumRead.get(1);
            if (wsum > 1e-6) state.measured(Math.exp(sum / wsum));
        }

        // 5 Bloom
        if (bloom) {
            bloomDown.use(gl);
            bloomDown.set(gl, "uSrc", 0);
            int src = hdrTex, sw = w, sh = h;
            for (int i = 0; i < BLOOM_LEVELS; i++) {
                gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, bloomFbo[i]);
                gl.glViewport(0, 0, bloomW[i], bloomH[i]);
                gl.glBindTexture(GL.GL_TEXTURE_2D, src);
                bloomDown.set(gl, "uTexel", 1f / sw, 1f / sh);
                bloomDown.set(gl, "uFirst", i == 0 ? 1 : 0);
                gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
                src = bloomTex[i]; sw = bloomW[i]; sh = bloomH[i];
            }
            bloomUp.use(gl);
            bloomUp.set(gl, "uSrc", 0);
            bloomUp.set(gl, "uRadius", 1.0f);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            for (int i = BLOOM_LEVELS - 1; i > 0; i--) {
                gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, bloomFbo[i - 1]);
                gl.glViewport(0, 0, bloomW[i - 1], bloomH[i - 1]);
                gl.glBindTexture(GL.GL_TEXTURE_2D, bloomTex[i]);
                bloomUp.set(gl, "uTexel", 1f / bloomW[i], 1f / bloomH[i]);
                gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            }
            gl.glDisable(GL.GL_BLEND);
        }

        // 6 Nachbearbeitung in ein Bild, dann Kantenglättung auf den Bildschirm
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, ldrFbo);
        gl.glViewport(0, 0, w, h);
        post.use(gl);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, bloomTex[0]);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        post.set(gl, "uHdr", 0);
        post.set(gl, "uBloom", 1);
        // Die Bloom-Stufen sind aufaddiert: durch ihre Zahl teilen, dann 5 % beimischen
        post.set(gl, "uBloomStrength", bloom ? 0.05f : 0f);
        post.set(gl, "uBloomNorm", 1f / BLOOM_LEVELS);
        post.set(gl, "uExposure", 1.0f);
        post.set(gl, "uVignette", 0.30f);
        post.set(gl, "uTime", (float) (simTime % 1000));
        post.set(gl, "uNight", (float) state.night);
        gl.glUniform4f(post.loc(gl, "uWeather"), (float) state.rain, (float) state.wet, (float) state.rainbow, (float) state.overcast);
        post.set(gl, "uBars", (float) director.bars);
        post.set(gl, "uFade", (float) director.fade);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);

        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        gl.glViewport(0, 0, surfW, surfH);
        aa.use(gl);
        gl.glBindTexture(GL.GL_TEXTURE_2D, ldrTex);
        aa.set(gl, "uLdr", 0);
        aa.set(gl, "uTexel", 1f / w, 1f / h);
        aa.set(gl, "uOn", fxaa ? 1 : 0);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        if (photoRequest) { photoRequest = false; takePhoto(gl, surfW, surfH); }

        gl.glEndQuery(GL4.GL_TIME_ELAPSED);
        timerIdx ^= 1;
        timerArmed = true;
        frame++;
        if (autoScale && now - lastScaleNanos > 400_000_000L && gpuMs > 0) {
            lastScaleNanos = now;
            if (gpuMs > 15.0) renderScale = Math.max(0.5, renderScale - 0.05);
            else if (gpuMs < 10.5) renderScale = Math.min(1.0, renderScale + 0.05);
        }
        stats(now, dt, w, h);
    }

    /** Möwen, bis 500 m Entfernung, je ein Aufruf mit eigener Bewegungsmatrix. */
    private void drawGulls(GL4 gl, Shader sh) {
        if (!showGulls || gullMesh[0] == null) return;
        for (com.dan.kneiphof.world.Gulls.Gull g : gulls.list) {
            double dx = g.x - camera.ex, dy = g.y - camera.ey, dz = g.z - camera.ez;
            if (dx * dx + dy * dy + dz * dz > 500.0 * 500.0) continue;
            sh.mat(gl, "uModel", gulls.matrix(g));
            gullMesh[g.frame].draw(gl);
        }
        sh.mat(gl, "uModel", IDENT);
    }

    /** Fußgänger in der Nähe der Kamera, je ein Aufruf mit eigener Bewegungsmatrix. */
    private void drawPeople(GL4 gl, Shader sh, double radius) {
        if (people == null || !showPeople || personMesh[0] == null) return;
        double r2 = radius * radius;
        for (People.Person p : people.list) {
            double dx = p.x - camera.ex, dz = p.z - camera.ez;
            if (dx * dx + dz * dz > r2) continue;
            sh.mat(gl, "uModel", people.matrix(p));
            personMesh[p.variant * 2 + People.frame(p)].draw(gl);
        }
        sh.mat(gl, "uModel", IDENT);
    }

    /** Schiffe mit ihrer Bewegungsmatrix. */
    private void drawBoats(GL4 gl, Shader sh) {
        if (boats == null || !showBoats) return;
        for (int i = 0; i < boatMesh.length; i++) {
            sh.mat(gl, "uModel", boats.list.get(i).matrix());
            boatMesh[i].draw(gl);
        }
        sh.mat(gl, "uModel", IDENT);
    }

    /**
     * Himmel, Gelände, Bauteile und Bäume in den gebundenen Bildspeicher. mirror: Durchgang für die
     * Spiegelung (gespiegelte Matrizen, Schnitt an der Wasserfläche).
     */
    private void drawScene(GL4 gl, float[] vp, float[] inv, double[] cam, double[] fwd, double[] wind, float shadowOn,
                           boolean drawTrees, boolean mirror) {
        float clip = mirror ? 0.02f : -1.0e4f;
        sky.use(gl);
        common(gl, sky, vp, inv, cam, fwd, wind, shadowOn);
        sky.set(gl, "uStars", (float) state.stars);
        sky.set(gl, "uMoonLit", (float) state.clock.moonLit);
        sky.set(gl, "uMirror", mirror ? 1f : 0f);
        gl.glBindVertexArray(emptyVao);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glDepthMask(true);
        // Schnittebene erst nach dem Himmel (sein Shader schreibt keine Schnittabstände)
        if (mirror) gl.glEnable(GL4.GL_CLIP_DISTANCE0);

        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glEnable(GL.GL_CULL_FACE);
        gl.glCullFace(GL.GL_BACK);
        if (terrainMesh.count > 0) {
            terrain.use(gl);
            common(gl, terrain, vp, inv, cam, fwd, wind, shadowOn);
            terrain.set(gl, "uClipY", clip);
            terrain.set(gl, "uShadowQ", mirror ? 0f : 1f);
            terrainMesh.draw(gl);
        }
        // Tiefenvorstufe: Gebäude einmal nur in den Tiefenpuffer, damit die teuren Oberflächen je Pixel nur einmal laufen
        gl.glColorMask(false, false, false, false);
        shadowDepth.use(gl);
        shadowDepth.mat(gl, "uLightVP", vp);
        shadowDepth.set(gl, "uClipY", clip);
        if (showTestbed) testbed.draw(gl);
        banksMesh.draw(gl);
        drawBridges(gl, shadowDepth);
        drawBoats(gl, shadowDepth);
        if (!mirror) drawPeople(gl, shadowDepth, 260);
        double reach = mirror ? 450 : Double.MAX_VALUE;
        if (showTown) townTiles.draw(gl, vp, cam[0], cam[2], reach);
        gl.glColorMask(true, true, true, true);
        gl.glDepthMask(false);
        gl.glDepthFunc(reversedZ ? GL.GL_GEQUAL : GL.GL_LEQUAL);
        object.use(gl);
        common(gl, object, vp, inv, cam, fwd, wind, shadowOn);
        object.set(gl, "uClipY", clip);
        object.set(gl, "uShadowQ", mirror ? 0f : 1f);
        double lampOn = Atmosphere.smooth(2, -3, state.clock.elevationDeg);
        // Gaslicht: fest im Bildspeicher (nicht vorbelichtet), damit es nachts glimmt und nicht blendet
        object.set(gl, "uLampColor", (float) (22 * lampOn), (float) (16 * lampOn), (float) (8 * lampOn));
        object.set(gl, "uTimeLamp", (float) (simTime % 600));
        if (showTestbed) testbed.draw(gl);
        banksMesh.draw(gl);
        drawBridges(gl, object);
        drawBoats(gl, object);
        if (!mirror) drawPeople(gl, object, 260);
        drawGulls(gl, object);
        if (showTown) townTiles.draw(gl, vp, cam[0], cam[2], reach);
        gl.glDepthMask(true);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDisable(GL.GL_CULL_FACE);
        if (drawTrees && (!mirror || camera.distance() < 300)) {
            tree.use(gl);
            common(gl, tree, vp, inv, cam, fwd, wind, shadowOn);
            tree.set(gl, "uClipY", clip);
            tree.set(gl, "uShadowQ", mirror ? 0f : 1f);
            trees.draw(gl);
        }
        gl.glBindVertexArray(emptyVao);
    }

    /** Brücken: feste Teile und Klappen (mit ihrer Drehung in uModel). */
    private void drawBridges(GL4 gl, Shader sh) {
        sh.mat(gl, "uModel", IDENT);
        bridgeStatic.draw(gl);
        if (bridges == null) return;
        for (int i = 0; i < leafMesh.length; i++) {
            double o = bridgeOpen[i];
            double ang = Bridges.MAX_ANGLE * o * o * (3 - 2 * o);
            Bridges.Bridge b = bridges.list.get(i);
            for (int k = 0; k < 2; k++) {
                sh.mat(gl, "uModel", bridges.leafMatrix(b, k, ang));
                leafMesh[i].draw(gl);
            }
        }
        sh.mat(gl, "uModel", IDENT);
    }

    private void updateBridges(double dt) {
        double now = simTime;
        if (bridgeAuto) {
            bridgeTimer -= dt;
            if (bridgeTimer <= 0) {
                int i = (int) (Math.random() * bridgeWant.length);
                if (!bridgeWant[i]) { bridgeWant[i] = true; bridgeCloseAt[i] = now + 40 + Math.random() * 30; }
                bridgeTimer = 25 + Math.random() * 50;
            }
            for (int i = 0; i < bridgeWant.length; i++) if (bridgeWant[i] && bridgeCloseAt[i] > 0 && now > bridgeCloseAt[i]) { bridgeWant[i] = false; bridgeCloseAt[i] = 0; }
        }
        for (int i = 0; i < bridgeOpen.length; i++) {
            float t = (bridgeWant[i] || (boats != null && i < boats.need.length && boats.need[i])) ? 1f : 0f;
            float d = (float) (dt / 20.0);
            bridgeOpen[i] += Math.max(-d, Math.min(d, t - bridgeOpen[i]));
        }
    }

    /** Dampfpfeife des Schiffs i (oder aller, wenn i < 0). */
    public void whistle(int i) {
        if (i >= 0) particles.whistle(i);
        else for (int k = 0; k < boatCount(); k++) particles.whistle(k);
    }

    public int boatCount() { return boats == null ? 0 : boats.list.size(); }

    /** Lage und Richtung von Schiff i: x, z, Richtung x, Richtung z. */
    public double[] boatPose(int i) {
        Boats.Boat b = boats.list.get(i);
        return new double[]{b.x, b.z, b.hx, b.hz};
    }

    /** Regiefahrt i starten: Uhr und Zeitraffer stellen, alles Eigene loslassen. */
    public void startTour(int i) {
        if (camera.walking) camera.stopWalk();
        follow = -1;
        camera.autoOrbit = false;
        camera.userMoved = false;
        com.dan.kneiphof.camera.Director.Tour t = director.tour(i);
        if (lapseSaved >= 0) { timeLapse = lapseSaved; lapseSaved = -1; }
        if (t.day >= 0) state.clock.set(t.day, t.hour);
        if (t.lapse >= 0) { lapseSaved = timeLapse; timeLapse = t.lapse; }
        director.start(i, new com.dan.kneiphof.camera.Director.Ctx() {
            public int boatCount() { return Renderer.this.boatCount(); }
            public double[] boatPose(int k) { return Renderer.this.boatPose(k); }
            public boolean boatIsSteamer(int k) { return boats.list.get(k).name.startsWith("Dampfer"); }
            public int bridgeCount() { return bridges == null ? 0 : bridges.list.size(); }
            public double[] bridgePose(int k) { com.dan.kneiphof.world.Bridges.Bridge b = bridges.list.get(k); return new double[]{b.cx, b.cz, b.angle, b.deck}; }
            public String bridgeName(int k) { return bridges.list.get(k).name; }
        });
        tourWas = true;
    }

    public void stopTour() { director.stop(); }

    public void setBridge(int i, boolean open) { bridgeWant[i] = open; bridgeCloseAt[i] = 0; }
    public Bridges bridges() { return bridges; }

    private void common(GL4 gl, Shader s, float[] vp, float[] inv, double[] cam, double[] fwd, double[] wind, float shadowOn) {
        s.mat(gl, "uViewProj", vp);
        s.mat(gl, "uInvViewProj", inv);
        s.set(gl, "uCamPos", cam);
        s.set(gl, "uCamFwd", fwd);
        s.set(gl, "uSkyLut", 0);
        s.set(gl, "uShadow", 1);
        s.set(gl, "uSunDir", state.clock.dir);
        s.set(gl, "uSunColor", state.sunColor);
        s.set(gl, "uMoonDir", state.clock.moonDir);
        s.set(gl, "uMoonColor", state.moonColor);
        s.set(gl, "uExtinction", state.extinction);
        s.set(gl, "uTime", (float) (simTime % 3600));
        s.set(gl, "uWind", (float) wind[0], (float) wind[1]);
        s.set(gl, "uFlow", (float) -state.flowSpeed, 0f);
        s.mats(gl, "uLightVP", cascades.lightVP);
        s.set(gl, "uSplits", cascades.splits);
        s.set(gl, "uTexel", cascades.texel);
        s.set(gl, "uZeroOne", reversedZ ? 1 : 0);
        s.set(gl, "uShadowOn", shadowOn);
        s.set(gl, "uExposure", (float) state.exposure);
        gl.glUniform4f(s.loc(gl, "uWeather"), (float) state.rain, (float) state.wet, (float) state.rainbow, (float) state.overcast);
        gl.glUniform4f(s.loc(gl, "uMist"), (float) state.mist, 22f, (float) cam[1], 0f);
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        for (Shader s : all) s.dispose(gl);
        particles.dispose(gl);
        beamMesh.dispose(gl);
        for (Gpu.Mesh m : personMesh) if (m != null) m.dispose(gl);
        for (Gpu.Mesh m : gullMesh) if (m != null) m.dispose(gl);
        terrainMesh.dispose(gl);
        testbed.dispose(gl);
        banksMesh.dispose(gl);
        townTiles.dispose(gl);
        bridgeStatic.dispose(gl);
        for (Gpu.Mesh m : leafMesh) m.dispose(gl);
        for (Gpu.Mesh m : boatMesh) m.dispose(gl);
    }

    // ------------------------------------------------------------------ Hilfen

    private void uploadIfReady(GL4 gl) {
        if (pendingWorld != null && pendingWorld.isDone()) {
            World w;
            try {
                w = pendingWorld.join();
            } catch (RuntimeException e) {
                pendingWorld = null;
                fail("Ufer und Bäume ließen sich nicht bauen: " + e);
                return;
            }
            pendingWorld = null;
            banksMesh.upload(gl, w.banksMesh.vertices(), w.banksMesh.indices(), new int[]{3, 3, 3, 2, 1, 1});
            townTiles.upload(gl, w.townTiles);
            chimneys = w.town.chimneys;
            bridges = w.bridges;
            bridgeStatic.upload(gl, w.bridges.statics.vertices(), w.bridges.statics.indices(), new int[]{3, 3, 3, 2, 1, 1});
            leafMesh = new Gpu.Mesh[w.bridges.list.size()];
            for (int i = 0; i < leafMesh.length; i++) {
                leafMesh[i] = new Gpu.Mesh();
                MeshBuilder lm = w.bridges.list.get(i).leaf;
                leafMesh[i].upload(gl, lm.vertices(), lm.indices(), new int[]{3, 3, 3, 2, 1, 1});
            }
            boats = w.boats;
            people = w.people;
            for (int gv = 0; gv < gullMesh.length; gv++) {
                MeshBuilder gm = com.dan.kneiphof.world.Gulls.figure(gv);
                if (gullMesh[gv] == null) gullMesh[gv] = new Gpu.Mesh();
                gullMesh[gv].upload(gl, gm.vertices(), gm.indices(), new int[]{3, 3, 3, 2, 1, 1});
            }
            for (int pv = 0; pv < personMesh.length; pv++) {
                MeshBuilder fm = People.figure(pv / 2, pv & 1);
                if (personMesh[pv] == null) personMesh[pv] = new Gpu.Mesh();
                personMesh[pv].upload(gl, fm.vertices(), fm.indices(), new int[]{3, 3, 3, 2, 1, 1});
            }
            boatMesh = new Gpu.Mesh[boats.list.size()];
            for (int i = 0; i < boatMesh.length; i++) {
                boatMesh[i] = new Gpu.Mesh();
                MeshBuilder bm = boats.list.get(i).mesh;
                boatMesh[i].upload(gl, bm.vertices(), bm.indices(), new int[]{3, 3, 3, 2, 1, 1});
            }
            trees = new TreeRenderer(w.trees);
            System.out.printf(Locale.ROOT, "Ufer: %d Züge, %d Dreiecke; Stadt: %d Häuser (%d mit Fenstern), %d Dreiecke; Bäume: %d%n",
                    w.banks.lines.size(), w.banksMesh.indexCount() / 3, w.town.houses, w.town.detailed, w.townTris, w.trees.spots.size());
        }
        if (pendingTerrain != null && pendingTerrain.isDone()) {
            TerrainMesh tm = pendingTerrain.join();
            pendingTerrain = null;
            terrainMesh.upload(gl, tm.verts, tm.indices, new int[]{3, 3, 4});
            System.out.printf(Locale.ROOT, "Gelände: %d Punkte, %d Dreiecke%n", tm.vertexCount(), tm.indices.length / 3);
        }
        if (pendingTestbed != null && pendingTestbed.isDone()) {
            MeshBuilder mb = pendingTestbed.join();
            pendingTestbed = null;
            testbed.upload(gl, mb.vertices(), mb.indices(), new int[]{3, 3, 3, 2, 1, 1});
            System.out.printf(Locale.ROOT, "Prüfstand: %d Punkte, %d Dreiecke%n", mb.vertexCount(), mb.indexCount() / 3);
        }
    }

    private void resizeTargets(GL4 gl, int w, int h) {
        if (hdrFbo != 0) {
            gl.glDeleteFramebuffers(2, new int[]{hdrFbo, ldrFbo}, 0);
            gl.glDeleteTextures(3, new int[]{hdrTex, depthTex, ldrTex}, 0);
            gl.glDeleteFramebuffers(2, new int[]{reflFbo, copyFbo}, 0);
            gl.glDeleteTextures(4, new int[]{reflTex, reflDepth, copyTex, copyDepth}, 0);
            gl.glDeleteFramebuffers(1, new int[]{shaftFbo}, 0);
            gl.glDeleteTextures(1, new int[]{shaftTex}, 0);
            gl.glDeleteFramebuffers(BLOOM_LEVELS, bloomFbo, 0);
            gl.glDeleteTextures(BLOOM_LEVELS, bloomTex, 0);
        }
        hdrW = w;
        hdrH = h;
        hdrTex = Gpu.texture(gl, w, h, GL4.GL_RGBA16F, false);
        depthTex = Gpu.texture(gl, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
        hdrFbo = Gpu.fbo(gl, hdrTex, depthTex);
        reflW = Math.max(64, w / 2);
        reflH = Math.max(64, h / 2);
        reflTex = Gpu.texture(gl, reflW, reflH, GL4.GL_RGBA16F, true);
        reflDepth = Gpu.texture(gl, reflW, reflH, GL4.GL_DEPTH_COMPONENT32F, false);
        reflFbo = Gpu.fbo(gl, reflTex, reflDepth);
        copyTex = Gpu.texture(gl, w, h, GL4.GL_RGBA16F, false);
        copyDepth = Gpu.texture(gl, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
        copyFbo = Gpu.fbo(gl, copyTex, copyDepth);
        shaftTex = Gpu.texture(gl, Math.max(1, w / 2), Math.max(1, h / 2), GL4.GL_RGBA16F, false);
        shaftFbo = Gpu.fbo(gl, shaftTex, 0);
        ldrTex = Gpu.texture(gl, w, h, GL.GL_RGBA8, false);
        ldrFbo = Gpu.fbo(gl, ldrTex, 0);
        int bw = w, bh = h;
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            bw = Math.max(1, bw / 2);
            bh = Math.max(1, bh / 2);
            bloomW[i] = bw;
            bloomH[i] = bh;
            bloomTex[i] = Gpu.texture(gl, bw, bh, GL4.GL_R11F_G11F_B10F, false);
            bloomFbo[i] = Gpu.fbo(gl, bloomTex[i], 0);
        }
    }

    private void fail(String msg) {
        error = msg;
        System.err.println(msg);
        errorListener.accept(msg);
    }

    /** Liest das fertige Bild (ohne Bedienfeld) aus dem hinteren Bildspeicher und speichert es im Hintergrund. */
    private void takePhoto(GL4 gl, int w, int h) {
        try {
            java.nio.ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
            gl.glReadBuffer(GL.GL_BACK);
            gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
            gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
            String name = "Foto_" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".png";
            java.io.File file = new java.io.File(System.getProperty("user.dir"), name);
            Thread t = new Thread(() -> {
                try {
                    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    int[] row = new int[w];
                    for (int y = 0; y < h; y++) {
                        int o = (h - 1 - y) * w * 4;
                        for (int x = 0; x < w; x++) {
                            int i = o + x * 4;
                            row[x] = ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255);
                        }
                        img.setRGB(0, y, w, 1, row, 0, w);
                    }
                    javax.imageio.ImageIO.write(img, "png", file);
                    photoMsg = "Foto gespeichert: " + file.getName() + " (" + w + " × " + h + ")";
                    com.dan.kneiphof.db.Dienst.ereignis("FOTO", file.getName(), w + " x " + h);
                } catch (Exception ex) {
                    photoMsg = "Foto fehlgeschlagen: " + ex.getMessage();
                }
                photoMsgUntil = System.nanoTime() + 8_000_000_000L;
            }, "Foto");
            t.setDaemon(true);
            t.start();
            photoMsg = "Foto wird gespeichert …";
            photoMsgUntil = System.nanoTime() + 8_000_000_000L;
        } catch (RuntimeException ex) {
            photoMsg = "Foto fehlgeschlagen: " + ex.getMessage();
            photoMsgUntil = System.nanoTime() + 8_000_000_000L;
        }
    }

    private void stats(long now, double dt, int w, int h) {
        statsFrames++;
        if (now - statsNanos < 250_000_000L) return;
        fps = statsFrames / ((now - statsNanos) / 1e9);
        statsFrames = 0;
        statsNanos = now;
        String time = com.dan.kneiphof.sky.SunClock.dateLabel(state.clock.day()) + ", " + com.dan.kneiphof.sky.SunClock.timeLabel(state.clock.hour()) + " MEZ";
        String sun = String.format(Locale.GERMAN, "Sonne %.1f° hoch, %03.0f° (%s)", state.clock.elevationDeg, state.clock.azimuthDeg, compass(state.clock.azimuthDeg));
        String s = String.format(Locale.GERMAN, "%s   ·   %.0f Bilder/s, GPU %.1f ms   ·   %d × %d (%d %%)   ·   %s   ·   %s   ·   Abstand %.0f m   ·   Bäume %,d Dreiecke",
                glInfo, fps, gpuMs, w, h, (int) Math.round(renderScale * 100), time, sun, camera.distance(), trees == null ? 0 : trees.drawnTriangles);
        if (now < photoMsgUntil) s = photoMsg + "   ·   " + s;
        statusListener.accept(s);
    }

    static String compass(double az) {
        String[] n = {"N", "NO", "O", "SO", "S", "SW", "W", "NW"};
        return n[(int) Math.round(az / 45) % 8];
    }
}
