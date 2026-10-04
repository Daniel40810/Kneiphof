package com.dan.kneiphof.gl;

import com.dan.kneiphof.world.Trees;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Zeichnet die Bäume als Instanzen: je Art und Variante drei Netze (fein, Büschel, Silhouette), dazu
 * ein Puffer mit den Pflanzorten, der je Bild nach Entfernung sortiert wird.
 * <ul>
 * <li>fein: bis 110 m, höchstens die 70 nächsten Bäume</li>
 * <li>Büschel: bis 900 m</li>
 * <li>Silhouette: dahinter</li>
 * </ul>
 * Schatten werfen die Bäume bis 320 m mit dem Büschel-Netz, das spart die meisten Dreiecke in den
 * drei Schattenkarten. Wind bewegt Krone und Blätter im Vertex-Shader.
 * Ändert sich der Tag im Jahr um mehr als drei Tage, werden die Netze im Hintergrund neu gefärbt.
 */
final class TreeRenderer {
    static final float NEAR = 110, MID = 900;
    static final int MAX_NEAR = 70;
    static final int INST = 5;   // x y z yaw scale

    private final Trees trees;
    private int species, variants;
    private final int[][][] vao, vbo, ibo, count;   // [art][variante][stufe]
    private int instBuf, instCap;
    private float[] inst = new float[0];
    /** Start und Zahl der Instanzen je Art, Variante und Stufe im Puffer dieses Bildes. */
    private final int[][][] base, num;
    /** Gruppen je Bild: 0 fein, 1 Büschel nah (wirft Schatten), 2 Büschel fern, 3 Silhouette. */
    static final int GROUPS = 4;
    static final int[] GROUP_MESH = {0, 1, 1, 2};
    static final float SHADOW_RANGE = 320;
    private int builtDay = -999;
    private CompletableFuture<List<Trees.Mesh>> pending;
    private boolean ready;
    public int drawnTriangles;

    TreeRenderer(Trees trees) {
        this.trees = trees;
        species = Trees.NAMES.length;
        variants = Trees.VARIANTS;
        vao = new int[species][variants][Trees.LODS];
        vbo = new int[species][variants][Trees.LODS];
        ibo = new int[species][variants][Trees.LODS];
        count = new int[species][variants][Trees.LODS];
        base = new int[species][variants][GROUPS];
        num = new int[species][variants][GROUPS];
    }

    boolean ready() { return ready; }

    /** Netze für den Tag (neu) anstoßen und fertige hochladen. */
    void update(GL4 gl, int day) {
        if (pending == null && Math.abs(day - builtDay) > 3) {
            final int d = day;
            builtDay = day;
            pending = CompletableFuture.supplyAsync(() -> trees.meshes(d, 0f));
        }
        if (pending != null && pending.isDone()) {
            List<Trees.Mesh> ms = pending.join();
            pending = null;
            for (Trees.Mesh m : ms) upload(gl, m);
            ready = true;
        }
    }

    private void upload(GL4 gl, Trees.Mesh m) {
        if (instBuf == 0) {
            int[] b = new int[1];
            gl.glGenBuffers(1, b, 0);
            instBuf = b[0];
            instCap = 4096;
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, instBuf);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) instCap * INST * 4, null, GL4.GL_STREAM_DRAW);
        }
        int s = m.species, v = m.variant, l = m.lod;
        if (vao[s][v][l] == 0) {
            int[] a = new int[1], b = new int[2];
            gl.glGenVertexArrays(1, a, 0);
            gl.glGenBuffers(2, b, 0);
            vao[s][v][l] = a[0];
            vbo[s][v][l] = b[0];
            ibo[s][v][l] = b[1];
        }
        gl.glBindVertexArray(vao[s][v][l]);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[s][v][l]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) m.verts.length * 4, Buffers.newDirectFloatBuffer(m.verts), GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo[s][v][l]);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) m.indices.length * 4, Buffers.newDirectIntBuffer(m.indices), GL.GL_STATIC_DRAW);
        int st = Trees.STRIDE * 4;
        int[] sizes = {3, 3, 3, 1, 1};
        int off = 0;
        for (int i = 0; i < sizes.length; i++) {
            gl.glEnableVertexAttribArray(i);
            gl.glVertexAttribPointer(i, sizes[i], GL.GL_FLOAT, false, st, off * 4L);
            off += sizes[i];
        }
        bindInstances(gl);
        gl.glBindVertexArray(0);
        count[s][v][l] = m.indices.length;
    }

    private void bindInstances(GL4 gl) {
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, instBuf);
        gl.glEnableVertexAttribArray(5);
        gl.glVertexAttribPointer(5, 4, GL.GL_FLOAT, false, INST * 4, 0);
        gl.glVertexAttribDivisor(5, 1);
        gl.glEnableVertexAttribArray(6);
        gl.glVertexAttribPointer(6, 1, GL.GL_FLOAT, false, INST * 4, 16);
        gl.glVertexAttribDivisor(6, 1);
    }

    /** Pflanzorte nach Entfernung auf die Stufen verteilen und in den Puffer schreiben. */
    void sort(GL4 gl, double cx, double cy, double cz) {
        // Die Netze (und damit instBuf) werden asynchron vorbereitet. Der Renderer
        // kann schon vorher eine Sortierung anfordern; ohne diesen Schutz würde
        // glBufferData auf den nicht gebundenen Puffer 0 schreiben.
        if (!ready || instBuf == 0) return;
        List<Trees.Spot> spots = trees.spots;
        int n = spots.size();
        // Stufe je Baum
        int[] lod = new int[n];
        float[] d2 = new float[n];
        for (int i = 0; i < n; i++) {
            Trees.Spot s = spots.get(i);
            float dx = (float) (s.x - cx), dy = (float) (s.y + 8 - cy), dz = (float) (s.z - cz);
            d2[i] = dx * dx + dy * dy + dz * dz;
            lod[i] = d2[i] < NEAR * NEAR ? 0 : d2[i] < SHADOW_RANGE * SHADOW_RANGE ? 1 : d2[i] < MID * MID ? 2 : 3;
        }
        // höchstens MAX_NEAR fein: die übrigen eine Stufe gröber
        int near = 0;
        for (int i = 0; i < n; i++) if (lod[i] == 0) near++;
        if (near > MAX_NEAR) {
            float[] ds = new float[near];
            int k = 0;
            for (int i = 0; i < n; i++) if (lod[i] == 0) ds[k++] = d2[i];
            java.util.Arrays.sort(ds);
            float lim = ds[MAX_NEAR - 1];
            for (int i = 0; i < n; i++) if (lod[i] == 0 && d2[i] > lim) lod[i] = 1;
        }
        for (int[][] a : num) for (int[] b : a) java.util.Arrays.fill(b, 0);
        for (int i = 0; i < n; i++) num[spots.get(i).species][spots.get(i).variant][lod[i]]++;
        // Reihenfolge je Art und Variante: fein, Büschel, Silhouette (fein und Büschel hintereinander für die Schatten)
        int pos = 0;
        for (int s = 0; s < species; s++)
            for (int v = 0; v < variants; v++)
                for (int l = 0; l < GROUPS; l++) { base[s][v][l] = pos; pos += num[s][v][l]; }
        if (inst.length < n * INST) inst = new float[n * INST];
        int[][][] fill = new int[species][variants][GROUPS];
        for (int i = 0; i < n; i++) {
            Trees.Spot s = spots.get(i);
            int at = (base[s.species][s.variant][lod[i]] + fill[s.species][s.variant][lod[i]]++) * INST;
            inst[at] = s.x; inst[at + 1] = s.y; inst[at + 2] = s.z; inst[at + 3] = s.yaw; inst[at + 4] = s.scale;
        }
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, instBuf);
        if (n > instCap) {
            instCap = n + 1024;
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) instCap * INST * 4, null, GL4.GL_STREAM_DRAW);
        } else {
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) instCap * INST * 4, null, GL4.GL_STREAM_DRAW);   // verwaisen lassen
        }
        FloatBuffer fb = Buffers.newDirectFloatBuffer(inst, 0, n * INST);
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, (long) n * INST * 4, fb);
    }

    /** Alle Bäume in ihrer Stufe zeichnen. */
    void draw(GL4 gl) {
        if (!ready) return;
        drawnTriangles = 0;
        for (int s = 0; s < species; s++)
            for (int v = 0; v < variants; v++)
                for (int g = 0; g < GROUPS; g++) {
                    int c = num[s][v][g], l = GROUP_MESH[g];
                    if (c == 0 || vao[s][v][l] == 0) continue;
                    gl.glBindVertexArray(vao[s][v][l]);
                    gl.glDrawElementsInstancedBaseInstance(GL.GL_TRIANGLES, count[s][v][l], GL.GL_UNSIGNED_INT, 0, c, base[s][v][g]);
                    drawnTriangles += count[s][v][l] / 3 * c;
                }
    }

    /** Schatten: fein und Büschel zusammen mit dem Büschel-Netz. */
    void drawShadow(GL4 gl) {
        if (!ready) return;
        for (int s = 0; s < species; s++)
            for (int v = 0; v < variants; v++) {
                int c = num[s][v][0] + num[s][v][1];
                if (c == 0 || vao[s][v][1] == 0) continue;
                gl.glBindVertexArray(vao[s][v][1]);
                gl.glDrawElementsInstancedBaseInstance(GL.GL_TRIANGLES, count[s][v][1], GL.GL_UNSIGNED_INT, 0, c, base[s][v][0]);
            }
    }
}
