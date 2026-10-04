package com.dan.kneiphof.gl;

import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.awt.GLJPanel;
import com.jogamp.opengl.util.Animator;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.function.Consumer;

/**
 * Die 3D-Fläche: ein {@link GLCanvas} mit Kern-Profil OpenGL 4, der Animator im Takt des Bildschirms
 * und die Bedienung mit Maus und Tasten.
 * <ul>
 * <li>Links ziehen: um den Drehpunkt kreisen · rechts (oder Mitte) ziehen: verschieben · Rad: zoomen</li>
 * <li>Pfeiltasten: kreisen · Bild auf/ab und + −: zoomen · Pos1: Übersicht · R: Rundflug</li>
 * <li>F5: Shader neu laden (mit {@code -Dkneiphof.shaders=…})</li>
 * </ul>
 * Mit {@code -Dkneiphof.gljpanel=true} zeichnet statt des schweren GLCanvas ein leichtes GLJPanel
 * (etwas langsamer, weil das Bild über den Hauptspeicher läuft); als Rückfall, falls ein Treiber den
 * Canvas im FFrame nicht zeigt.
 */
public final class SceneView extends JPanel {
    private final Renderer renderer = new Renderer();
    private final java.awt.Component canvas;
    private final GLAutoDrawable drawable;
    public final boolean lightweight = Boolean.getBoolean("kneiphof.gljpanel");
    private Animator animator;
    private Consumer<Boolean> orbitListener = b -> { };
    /** Taste V: Ansicht merken (vom Bedienfeld gesetzt). */
    public volatile Runnable ansichtMerken = () -> { };

    public SceneView() {
        super(new BorderLayout());
        setBackground(new Color(10, 13, 16));
        GLProfile profile;
        try {
            profile = GLProfile.get(GLProfile.GL4);
        } catch (GLException e) {
            throw new GLException("Kein OpenGL-4-Kernprofil verfügbar: " + e.getMessage(), e);
        }
        GLCapabilities caps = new GLCapabilities(profile);
        caps.setDoubleBuffered(true);
        caps.setDepthBits(24);
        caps.setHardwareAccelerated(true);
        if (lightweight) {
            GLJPanel p = new GLJPanel(caps);
            p.addGLEventListener(renderer);
            canvas = p;
            drawable = p;
        } else {
            GLCanvas c = new GLCanvas(caps);
            c.addGLEventListener(renderer);
            canvas = c;
            drawable = c;
        }
        canvas.setFocusable(true);
        add(canvas, BorderLayout.CENTER);
        installInput();
    }

    public Renderer renderer() { return renderer; }

    public void setOrbitListener(Consumer<Boolean> l) { orbitListener = l; }

    public void start() {
        animator = new Animator(drawable);
        animator.setUpdateFPSFrames(60, null);
        animator.start();
        canvas.requestFocusInWindow();
    }

    public void stop() {
        if (animator != null) animator.stop();
    }

    @Override
    public boolean requestFocusInWindow() { return canvas.requestFocusInWindow(); }

    public void goOverview() {
        renderer.follow = -1;
        renderer.camera.autoOrbit = false;
        renderer.camera.lookAt(-40, 4, 10, 320, 26, 1100);
        orbitListener.accept(false);
    }

    public void goTestbed() {
        renderer.follow = -1;
        renderer.showTestbed = true;
        renderer.camera.autoOrbit = false;
        renderer.camera.lookAt(com.dan.kneiphof.world.Testbed.CX, com.dan.kneiphof.world.Testbed.GROUND + 4, com.dan.kneiphof.world.Testbed.CZ, 150, 16, 38);
        orbitListener.accept(false);
    }

    /** Rundgang: durch das Südportal in den Dom und nach Osten; erneut aufgerufen beendet er ihn. */
    public void toggleDomWalk() {
        com.dan.kneiphof.camera.OrbitCamera c = renderer.camera;
        if (c.walking) { c.stopWalk(); return; }
        double[] p = com.dan.kneiphof.world.DomInterior.toWorld(com.dan.kneiphof.world.DomInterior.DOOR_U - 1.0, 8.4);
        c.startWalk(p[0], p[1], 265, com.dan.kneiphof.world.DomInterior.FLOOR);
        orbitListener.accept(false);
    }

    private int bridgeIdx = -1;

    /** Zur Brücke i fliegen (Blick von der Seite auf das Joch mit der Durchfahrt). */
    public void goBridge(int i) {
        renderer.follow = -1;
        com.dan.kneiphof.world.Bridges br = renderer.bridges();
        if (br == null) return;
        bridgeIdx = ((i % br.list.size()) + br.list.size()) % br.list.size();
        com.dan.kneiphof.world.Bridges.Bridge b = br.list.get(bridgeIdx);
        if (renderer.camera.walking) renderer.camera.stopWalk();
        renderer.camera.autoOrbit = false;
        double yaw = (180.0 - Math.toDegrees(b.angle) + 720.0) % 360.0 + 25.0;
        renderer.camera.lookAt(b.cx, b.deck + 1.0, b.cz, yaw, 15, 80);
        orbitListener.accept(false);
    }

    public void nextBridge() { goBridge(bridgeIdx + 1); }

    /** Kamera an das nächste Schiff hängen (nach dem letzten wieder frei). */
    public void followNext() {
        int n = renderer.boatCount();
        if (n == 0) return;
        int next = renderer.follow + 1;
        if (renderer.camera.walking) renderer.camera.stopWalk();
        if (next >= n) {
            renderer.follow = -1;
            return;
        }
        renderer.follow = next;
        renderer.camera.autoOrbit = false;
        orbitListener.accept(false);
        double[] p = renderer.boatPose(next);
        double yaw = Math.toDegrees(Math.atan2(-p[2] * 0.9 - p[3] * 0.45, -p[3] * 0.9 + p[2] * 0.45));
        renderer.camera.lookAt(p[0], 2.5, p[1], yaw, 9, 70);
    }

    public void stopFollow() { renderer.follow = -1; }

    public void setAutoOrbit(boolean on) { renderer.camera.autoOrbit = on; orbitListener.accept(on); }

    private void installInput() {
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) {
                lx = e.getX(); ly = e.getY();
                canvas.requestFocusInWindow();
            }
            @Override public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lx, dy = e.getY() - ly;
                lx = e.getX(); ly = e.getY();
                double hgt = Math.max(1, canvas.getHeight());
                if (renderer.camera.walking) {
                    renderer.camera.look(-dx * 0.0042, -dy * 0.0042);
                } else if (SwingUtilities.isLeftMouseButton(e) && !e.isShiftDown()) {
                    renderer.camera.orbit(-dx * 0.0062, dy * 0.0052);
                    if (renderer.camera.autoOrbit) setAutoOrbit(false);
                } else {
                    renderer.follow = -1;
                    renderer.camera.pan(dx / hgt, dy / hgt);
                }
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                if (!renderer.camera.walking) renderer.camera.zoom(e.getPreciseWheelRotation());
            }
        };
        canvas.addMouseListener(m);
        canvas.addMouseMotionListener(m);
        canvas.addMouseWheelListener(m);
        final KeyAdapter keys = new KeyAdapter() {
            private boolean walkKey(KeyEvent e, boolean down) {
                com.dan.kneiphof.camera.OrbitCamera c = renderer.camera;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_W: c.kFwd = down; return true;
                    case KeyEvent.VK_S: c.kBack = down; return true;
                    case KeyEvent.VK_A: c.kLeft = down; return true;
                    case KeyEvent.VK_D: c.kRight = down; return true;
                    case KeyEvent.VK_SHIFT: c.kRun = down; return true;
                    case KeyEvent.VK_LEFT: c.kTurnL = down; return true;
                    case KeyEvent.VK_RIGHT: c.kTurnR = down; return true;
                    case KeyEvent.VK_UP: c.kUp = down; return true;
                    case KeyEvent.VK_DOWN: c.kDown = down; return true;
                    default: return false;
                }
            }
            @Override public void keyReleased(KeyEvent e) { walkKey(e, false); }
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_E || (e.getKeyCode() == KeyEvent.VK_ESCAPE && renderer.camera.walking)) {
                    toggleDomWalk();
                    return;
                }
                int kc = e.getKeyCode();
                boolean wasd = kc == KeyEvent.VK_W || kc == KeyEvent.VK_A || kc == KeyEvent.VK_S || kc == KeyEvent.VK_D || kc == KeyEvent.VK_SHIFT;
                if ((renderer.camera.walking || wasd) && walkKey(e, true)) return;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_LEFT: renderer.camera.orbit(0.06, 0); break;
                    case KeyEvent.VK_RIGHT: renderer.camera.orbit(-0.06, 0); break;
                    case KeyEvent.VK_UP: renderer.camera.orbit(0, 0.04); break;
                    case KeyEvent.VK_DOWN: renderer.camera.orbit(0, -0.04); break;
                    case KeyEvent.VK_PAGE_UP: case KeyEvent.VK_PLUS: case KeyEvent.VK_ADD: renderer.camera.zoom(-1); break;
                    case KeyEvent.VK_PAGE_DOWN: case KeyEvent.VK_MINUS: case KeyEvent.VK_SUBTRACT: renderer.camera.zoom(1); break;
                    case KeyEvent.VK_HOME: goOverview(); break;
                    case KeyEvent.VK_P: goTestbed(); break;
                    case KeyEvent.VK_B: nextBridge(); break;
                    case KeyEvent.VK_F: followNext(); break;
                    case KeyEvent.VK_H: renderer.whistle(-1); break;
                    case KeyEvent.VK_R: setAutoOrbit(!renderer.camera.autoOrbit); break;
                    case KeyEvent.VK_F5: renderer.requestShaderReload(); break;
                    case KeyEvent.VK_F12: renderer.photoRequest = true; break;
                    case KeyEvent.VK_V: SwingUtilities.invokeLater(ansichtMerken); break;
                    default: break;
                }
            }
        };
        canvas.addKeyListener(keys);
        canvas.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { releaseKeys(); }
        });
        // Tasten gelten im ganzen Fenster, nicht nur bei Fokus auf der Szene (nach einem Klick ins Bedienfeld hat sonst ein Knopf den Fokus)
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(ev -> {
            if (ev.getSource() == canvas || !canvas.isShowing()) return false;
            if (ev.getID() != KeyEvent.KEY_PRESSED && ev.getID() != KeyEvent.KEY_RELEASED) return false;
            java.awt.Window w = SwingUtilities.getWindowAncestor(canvas);
            if (w == null || !w.isActive() || SwingUtilities.getWindowAncestor(ev.getComponent()) != w) return false;
            java.awt.Component fo = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (fo instanceof javax.swing.text.JTextComponent) return false;
            switch (ev.getKeyCode()) {
                case KeyEvent.VK_W: case KeyEvent.VK_A: case KeyEvent.VK_S: case KeyEvent.VK_D: case KeyEvent.VK_SHIFT:
                case KeyEvent.VK_E: case KeyEvent.VK_V: case KeyEvent.VK_B: case KeyEvent.VK_F: case KeyEvent.VK_H: case KeyEvent.VK_R: case KeyEvent.VK_P:
                    if (ev.getID() == KeyEvent.KEY_PRESSED) keys.keyPressed(ev); else keys.keyReleased(ev);
                    return ev.getKeyCode() != KeyEvent.VK_SHIFT;
                default: return false;
            }
        });
    }

    private void releaseKeys() {
        com.dan.kneiphof.camera.OrbitCamera c = renderer.camera;
        c.kFwd = c.kBack = c.kLeft = c.kRight = c.kRun = c.kTurnL = c.kTurnR = c.kUp = c.kDown = false;
    }

}
