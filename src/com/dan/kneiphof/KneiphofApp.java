package com.dan.kneiphof;

import com.dan.fframe.FFrame;
import com.dan.kneiphof.db.Dienst;
import com.dan.kneiphof.db.Zustand;
import com.dan.kneiphof.gl.Renderer;
import com.dan.kneiphof.gl.SceneView;
import com.dan.kneiphof.ui.AppIcon;
import com.dan.kneiphof.ui.ControlPanel;
import com.dan.kneiphof.ui.Startbild;
import com.jogamp.opengl.GLProfile;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import com.dan.foptionpane.FOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.IllegalComponentStateException;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

/**
 * Kneiphof — die Dominsel in Königsberg mit ihren sieben Brücken, um 1910, als 3D-Szene auf der
 * Grafikkarte (JOGL, OpenGL 4.3).
 * <p>
 * Einstieg: FFrame beim Start maximiert (Taskleiste bleibt frei), Größe frei veränderbar; Szene in
 * der Mitte, Bedienfeld rechts, Statuszeile unten.
 */
public final class KneiphofApp {
    static final Color BG = new Color(14, 19, 24), STATUS_BG = new Color(8, 11, 14), STATUS_INK = new Color(160, 170, 174);
    public static final String VERSION = "Phase 12";

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        // Der GL-Canvas ist ein schweres AWT-Element: Menüs und Tooltips müssen darüber liegen können
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        Dienst.start();
        Renderer.vorab();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Dienst.ereignis("FEHLER", t.getName(), String.valueOf(e));
            e.printStackTrace();
        });
        GLProfile.initSingleton();
        SwingUtilities.invokeLater(KneiphofApp::open);
    }

    /** Ohne Startbild: wartet kurz auf die Prüfung der Datenbank und nimmt den letzten Zustand. */
    private static Zustand stillLaden() {
        long ende = System.currentTimeMillis() + 8000;
        while (Dienst.lage() == Dienst.Lage.PRUEFT && System.currentTimeMillis() < ende) {
            try { Thread.sleep(50); } catch (InterruptedException e) { break; }
        }
        return Dienst.ladeLetzten();
    }

    private static void open() {
        Zustand start = null;
        if (!"false".equals(System.getProperty("kneiphof.splash"))) {
            Startbild.Wahl w = Startbild.zeigen();
            if (w != null && w.laden) start = w.zustand;
        } else {
            start = stillLaden();
        }
        final Zustand beginn = start;
        FFrame f = new FFrame("Kneiphof · Die sieben Brücken von Königsberg, 1910");
        AppIcon.install(f);
        if (!Boolean.getBoolean("kneiphof.gljpanel")) makeOpaque(f);
        SceneView scene;
        try {
            scene = new SceneView();
        } catch (RuntimeException e) {
            FOptionPane.showMessageDialog(null, "OpenGL lässt sich nicht starten:\n" + e.getMessage(), "Kneiphof", FOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        ControlPanel controls = new ControlPanel(scene);
        JLabel status = new JLabel("Gelände wird aufgebaut …");
        status.setForeground(STATUS_INK);
        status.setFont(new Font("SansSerif", Font.PLAIN, 12));
        status.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        scene.renderer().setStatusListener(s -> SwingUtilities.invokeLater(() -> status.setText(s)));
        scene.renderer().setErrorListener(msg -> SwingUtilities.invokeLater(() ->
                FOptionPane.showMessageDialog(f, msg, "Kneiphof · Grafik", FOptionPane.ERROR_MESSAGE)));

        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(scene, BorderLayout.CENTER);
        JScrollPane side = new JScrollPane(controls, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        side.setBorder(BorderFactory.createEmptyBorder());
        side.getViewport().setBackground(BG);
        com.dan.fscrollbar.FScrollBar bar = new com.dan.fscrollbar.FScrollBar(javax.swing.JScrollBar.VERTICAL);
        bar.setUnitIncrement(16);
        bar.setBlockIncrement(120);
        side.setVerticalScrollBar(bar);
        side.setBackground(BG);
        side.setPreferredSize(new Dimension(330, 600));
        root.add(side, BorderLayout.EAST);
        JLabel dbStatus = new JLabel(Dienst.kurz());
        dbStatus.setForeground(STATUS_INK);
        dbStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));
        dbStatus.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        Dienst.beiAenderung(() -> dbStatus.setText(Dienst.kurz()));
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(STATUS_BG);
        south.add(status, BorderLayout.CENTER);
        south.add(dbStatus, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);

        f.setPreferredFrameSize(new Dimension(1480, 900));
        f.setSize(1480, 900);
        f.setMinimumSize(new Dimension(800, 520));
        f.setLocationRelativeTo(null);
        f.setResizable(true);
        f.setVisible(true);
        startMaximized(f);
        Renderer rd = scene.renderer();
        Dienst.kontext(() -> rd.state.clock.day(), () -> rd.state.clock.hour());
        if (beginn != null) controls.restore(beginn);
        scene.start();
        // Sitzung eintragen, sobald das Fenster steht und die Grafikkarte bekannt ist
        javax.swing.Timer sitzung = new javax.swing.Timer(4000, e -> Dienst.sitzungStart(scene.getWidth(), scene.getHeight(), rd.glInfo(), beginn != null));
        sitzung.setRepeats(false);
        sitzung.start();
        // Alle 60 Sekunden sichern
        new javax.swing.Timer(60000, e -> Dienst.zwischensicherung(controls.capture())).start();
        f.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { beenden(controls, rd); }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { beenden(controls, rd); scene.stop(); }, "Kneiphof-Ende"));
        scene.requestFocusInWindow();
    }

    private static void beenden(ControlPanel controls, Renderer rd) {
        Zustand z = null;
        try { z = controls.capture(); } catch (RuntimeException e) { Dienst.ereignis("WARN", "Zustand", String.valueOf(e)); }
        Runtime rt = Runtime.getRuntime();
        Dienst.beenden(z, new double[]{rd.fps(), rd.gpuMs(), (rt.totalMemory() - rt.freeMemory()) >> 20});
    }

    /**
     * Startet maximiert (MAXIMIZED_BOTH), ohne die Taskleiste zu verdecken. Die normale Größe von
     * 1480 × 900 bleibt als Rückfall; die Schaltfläche „Wiederherstellen“ des FFrame kennt den Zustand
     * und stellt sie wieder her, danach lässt sich das Fenster frei ziehen. Wie in Geyser.
     */
    private static void startMaximized(FFrame f) {
        Rectangle normal = f.getBounds();
        f.setMaximizedBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        f.setExtendedState(f.getExtendedState() | Frame.MAXIMIZED_BOTH);
        try {
            // Die Titelleiste des FFrame führt einen eigenen Maximiert-Zustand; ihn angleichen
            for (java.lang.reflect.Field fd : FFrame.class.getDeclaredFields()) {
                if (!fd.getType().getSimpleName().equals("FTaskbar")) continue;
                fd.setAccessible(true);
                Object bar = fd.get(f);
                Class<?> bc = bar.getClass();
                java.lang.reflect.Field mx = bc.getDeclaredField("maximized"), rb = bc.getDeclaredField("restoreBounds"),
                        btn = bc.getDeclaredField("btnMaximize");
                mx.setAccessible(true);
                rb.setAccessible(true);
                btn.setAccessible(true);
                mx.setBoolean(bar, true);
                rb.set(bar, normal);
                Object icon = btn.get(bar);
                icon.getClass().getMethod("setType", com.dan.ficons.FIconType.class).invoke(icon, com.dan.ficons.FIconType.RESTORE);
                ((javax.swing.JComponent) icon).setToolTipText("Wiederherstellen");
                ((java.awt.Component) icon).addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                        SwingUtilities.invokeLater(() -> {
                            if ((f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) {
                                f.setExtendedState(f.getExtendedState() & ~Frame.MAXIMIZED_BOTH);
                                f.setBounds(normal);
                            }
                        });
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("FFrame-Titelleiste nicht angeglichen: " + e);
        }
    }

    /**
     * Das FFrame ist ein durchscheinendes Fenster (Hintergrund mit Alpha 0), damit seine Ecken rund
     * wirken. In einem solchen Fenster zeigt Windows schwere Elemente wie den GL-Canvas nicht. Darum
     * wird es deckend gemacht und die Rundung als Fensterform gesetzt; maximiert ohne Rundung.
     */
    private static void makeOpaque(FFrame f) {
        f.setBackground(BG);
        java.awt.event.ComponentAdapter shape = new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { reshape(f); }
            @Override public void componentShown(java.awt.event.ComponentEvent e) { reshape(f); }
        };
        f.addComponentListener(shape);
        f.addWindowStateListener(e -> reshape(f));
    }

    private static void reshape(FFrame f) {
        try {
            boolean max = (f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
            f.setShape(max ? null : new java.awt.geom.RoundRectangle2D.Double(0, 0, f.getWidth(), f.getHeight(), 20, 20));
        } catch (UnsupportedOperationException | IllegalComponentStateException e) {
            // Form nicht möglich: dann eben eckig
        }
    }

    private KneiphofApp() { }
}
