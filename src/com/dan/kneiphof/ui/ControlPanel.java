package com.dan.kneiphof.ui;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fslider.FSlider;
import com.dan.kneiphof.db.Dienst;
import com.dan.kneiphof.db.Zustand;
import com.dan.kneiphof.gl.Renderer;
import com.dan.kneiphof.gl.SceneView;
import com.dan.kneiphof.sky.SunClock;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;
import com.dan.foptionpane.FOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Bedienfeld rechts: Ansicht, Zeit, Luft, Bild. Wächst mit jeder Phase. */
public final class ControlPanel extends JPanel implements javax.swing.Scrollable {
    public static final Color BG = new Color(14, 19, 24), INK = new Color(226, 230, 230), MUTED = new Color(140, 152, 160),
            ACCENT = new Color(226, 162, 58);

    private final SceneView view;
    private final Renderer r;
    private final JLabel tourLbl = label(" ");
    private Timer tourTimer;
    private final JLabel dayLbl = label(""), timeLbl = label(""), hazeLbl = label(""), windLbl = label(""),
            scaleLbl = label(""), evLbl = label(""), glLbl = new JLabel();
    private final FSlider day = new FSlider(1, 365, 172), time = new FSlider(0, 24 * 60 - 1, 630),
            haze = new FSlider(0, 100, 30), wind = new FSlider(0, 120, 30), scale = new FSlider(50, 100, 100), ev = new FSlider(-20, 20, 0);
    private boolean fromScene;
    /** Alle Schalter, die im Zustand gesichert werden: Schlüssel = Spalte in KNH_STATE, Kasten und Setzer. */
    private final Map<String, Object[]> flags = new LinkedHashMap<>();
    private FComboBox lapse, mistBox, weatherBox, ansichtBox;
    private final List<com.dan.kneiphof.db.ZustandDb.Eintrag> ansichten = new ArrayList<>();
    private double[] letzteKamera;

    private FCheckBox flag(String key, String text, boolean def, Consumer<Boolean> set) {
        FCheckBox b = new FCheckBox(text);
        b.setSelected(def);
        b.addActionListener(e -> { set.accept(b.isSelected()); view.requestFocusInWindow(); });
        flags.put(key, new Object[]{b, set});
        return b;
    }

    public ControlPanel(SceneView view) {
        this.view = view;
        this.r = view.renderer();
        setBackground(BG);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        head("ANSICHT");
        FButton home = new FButton("Übersicht");
        home.addActionListener(e -> { view.goOverview(); view.requestFocusInWindow(); });
        add(row(home));
        FButton dom = new FButton("Dom betreten");
        dom.addActionListener(e -> { view.toggleDomWalk(); view.requestFocusInWindow(); });
        add(row(dom));
        FButton bed = new FButton("Prüfstand");
        bed.addActionListener(e -> { view.goTestbed(); view.requestFocusInWindow(); });
        add(row(bed));
        FCheckBox orbit = new FCheckBox("Rundflug");
        orbit.addActionListener(e -> { view.setAutoOrbit(orbit.isSelected()); view.requestFocusInWindow(); });
        view.setOrbitListener(orbit::setSelected);
        add(row(orbit));
        note("Links ziehen dreht um den Drehpunkt, rechts ziehen verschiebt, das Rad zoomt vom Geländer bis zum Blick über die ganze Stadt. Pfeiltasten drehen, Bild auf/ab zoomt, Pos1 führt zur Übersicht, R startet den Rundflug, P fliegt zum Prüfstand. „Dom betreten“ (E) führt durch das Südportal hinein: W A S D gehen, Umschalt läuft, Maus oder Pfeiltasten schauen, E oder Esc beenden; wer wieder durch das Portal geht, steht draußen.");

        gap();
        head("REGIE");
        FComboBox tourBox = new FComboBox(r.director.names());
        add(row(tourBox));
        FButton tourGo = new FButton("Fahrt starten");
        tourGo.addActionListener(e -> {
            if (r.director.active()) { r.stopTour(); Dienst.ereignis("FAHRT", "beendet", null); }
            else { r.startTour(Math.max(0, tourBox.getSelectedIndex())); Dienst.ereignis("FAHRT", String.valueOf(tourBox.getSelectedItem()), null); }
            view.requestFocusInWindow();
        });
        add(row(tourGo));
        add(row(tourLbl));
        note("Die Fahrten führen die Kamera selbst, mit Kinobalken und Blenden. Jede Bewegung mit der Maus oder den Pfeiltasten beendet sie. „Ein Tag in 90 Sekunden“ lässt die Uhr durchlaufen.");
        tourTimer = new Timer(400, e -> {
            tourGo.setText(r.director.active() ? "Fahrt beenden" : "Fahrt starten");
            tourLbl.setText(r.director.active() ? r.director.caption() : " ");
        });
        tourTimer.start();

        gap();
        head("ANSICHTEN");
        ansichtBox = new FComboBox(new Object[]{"(keine gemerkt)"});
        add(row(ansichtBox));
        FButton avMerken = new FButton("Ansicht merken … (V)");
        avMerken.addActionListener(e -> ansichtMerken());
        add(row(avMerken));
        FButton avGehen = new FButton("Zur Ansicht springen");
        avGehen.addActionListener(e -> ansichtLaden());
        add(row(avGehen));
        FButton avWeg = new FButton("Ansicht vergessen");
        avWeg.addActionListener(e -> ansichtVergessen());
        add(row(avWeg));
        ansichtNote = note("Eine Ansicht merkt Kamera, Tag, Uhrzeit, Wetter und alle Schalter unter einem Namen (Taste V). Sie liegt in der Datenbank; das Programm sichert außerdem alle 60 Sekunden und beim Beenden und stellt beim nächsten Start den letzten Stand wieder her.");
        view.ansichtMerken = this::ansichtMerken;
        Dienst.beiAenderung(this::ansichtenAktualisieren);
        ansichtenAktualisieren();

        gap();
        head("BRÜCKEN");
        FButton nb = new FButton("Nächste Brücke");
        nb.addActionListener(e -> { view.nextBridge(); view.requestFocusInWindow(); });
        add(row(nb));
        String[] bn = com.dan.kneiphof.world.Bridges.names();
        bridgeBoxes = new FCheckBox[bn.length];
        for (int i = 0; i < bn.length; i++) {
            final int k = i;
            bridgeBoxes[i] = new FCheckBox(bn[i] + " offen");
            bridgeBoxes[i].addActionListener(e -> { r.setBridge(k, bridgeBoxes[k].isSelected()); Dienst.ereignis("BRUECKE", bn[k], bridgeBoxes[k].isSelected() ? "offen" : "geschlossen"); view.requestFocusInWindow(); });
            add(row(bridgeBoxes[i]));
        }
        FCheckBox brAuto = flag("BETRIEB", "Betrieb: Klappen öffnen von selbst", false, v -> r.bridgeAuto = v);
        add(row(brAuto));
        note("Jede Brücke hat zwei Klappen mit Gegengewicht unter der Fahrbahn; sie öffnen in 20 Sekunden. Taste B fliegt zur nächsten Brücke.");
        gap();
        head("SCHIFFE");
        FButton fb = new FButton("Schiff verfolgen");
        fb.addActionListener(e -> { view.followNext(); view.requestFocusInWindow(); });
        add(row(fb));
        FCheckBox boatsOn = flag("SCHIFFE", "Schiffe auf dem Pregel", true, v -> r.showBoats = v);
        add(row(boatsOn));
        note("Zwei Haffkähne und zwei Dampfer fahren auf dem Pregel westlich der Stadt, die Kähne mit dem Strom zum Haff, die Dampfer zurück. Jedes Schiff zieht Bugwelle, Wellenkeil und Schaumspur hinter sich her. Die Taste F fliegt zum nächsten Schiff und hält die Kamera daran; ein Zug mit der rechten Maustaste löst sie wieder.");
        head("ZEIT");
        add(row(dayLbl));
        day.addChangeListener(e -> { if (!fromScene) apply(); updateLabels(); });
        add(row(day));
        add(row(timeLbl));
        time.addChangeListener(e -> { if (!fromScene) apply(); updateLabels(); });
        add(row(time));
        lapse = new FComboBox(new Object[]{"Uhr steht", "Echtzeit", "1 Minute je Sekunde", "10 Minuten je Sekunde", "1 Stunde je Sekunde"});
        double[] factors = {0, 1, 60, 600, 3600};
        lapse.addActionListener(e -> { r.timeLapse = factors[Math.max(0, lapse.getSelectedIndex())]; view.requestFocusInWindow(); });
        add(row(lapse));
        note("Königsberg 1910: Die Uhren gingen nach Mitteleuropäischer Zeit, Sommerzeit gab es noch nicht. Weil die Stadt 20,5° östlich liegt, steht die Sonne schon gegen 11:40 Uhr im Süden. Am 21. Juni geht sie um 3:00 Uhr auf und um 20:18 Uhr unter, die Dämmerung im Norden hält die ganze Nacht; am 21. Dezember sind es 7:59 und 15:13 Uhr.");

        gap();
        head("LUFT");
        add(row(hazeLbl));
        mistBox = new FComboBox(new Object[]{"Nebel nach Jahreszeit", "Kein Nebel", "Dünner Morgennebel", "Dichter Nebel"});
        mistBox.addActionListener(e -> { r.state.mistMode = Math.max(0, mistBox.getSelectedIndex()); Dienst.ereignis("NEBEL", String.valueOf(mistBox.getSelectedItem()), null); view.requestFocusInWindow(); });
        add(row(mistBox));
        weatherBox = new FComboBox(new Object[]{"Wetter: klar", "Wetter: Regen", "Nach dem Regen (Regenbogen)"});
        weatherBox.addActionListener(e -> { r.state.weatherMode = Math.max(0, weatherBox.getSelectedIndex()); Dienst.ereignis("WETTER", String.valueOf(weatherBox.getSelectedItem()), null); view.requestFocusInWindow(); });
        add(row(weatherBox));
        haze.addChangeListener(e -> { r.state.haze = hazeOf(haze.getValue()); updateLabels(); });
        add(row(haze));
        add(row(windLbl));
        wind.addChangeListener(e -> { r.state.windSpeed = wind.getValue() / 10.0; updateLabels(); });
        add(row(wind));
        note("Dunst bestimmt, wie weit man über das Pregeltal sieht und wie rot die Sonne am Horizont steht. Der Wind kommt aus Westsüdwest vom Haff und kräuselt das Wasser.");

        gap();
        head("BILD");
        FButton photo = new FButton("Foto speichern (F12)");
        photo.addActionListener(e -> { r.photoRequest = true; view.requestFocusInWindow(); });
        add(row(photo));
        add(row(scaleLbl));
        scale.addChangeListener(e -> { if (!fromScene) r.renderScale = scale.getValue() / 100.0; updateLabels(); });
        add(row(scale));
        add(row(evLbl));
        ev.addChangeListener(e -> { r.state.ev = ev.getValue() / 10.0; updateLabels(); });
        add(row(ev));
        FCheckBox auto = flag("AUFLOESUNG_AUTO", "Auflösung automatisch (60 Bilder/s)", false, v -> { r.autoScale = v; scale.setEnabled(!v); });
        add(row(auto));
        FCheckBox sh = flag("SCHATTEN", "Schatten", true, v -> r.shadows = v);
        add(row(sh));
        FCheckBox bl = flag("BLOOM", "Überstrahlen (Bloom)", true, v -> r.bloom = v);
        add(row(bl));
        FCheckBox fx = flag("FXAA", "Kantenglättung (FXAA)", true, v -> r.fxaa = v);
        add(row(fx));
        FCheckBox rf = flag("SPIEGELUNG", "Spiegelung im Wasser", true, v -> r.reflection = v);
        add(row(rf));
        FCheckBox ls = flag("STRAHLEN", "Lichtstrahlen im Dunst", true, v -> r.lightShafts = v);
        add(row(ls));
        FCheckBox pp = flag("FUSSGAENGER", "Fußgänger", true, v -> r.showPeople = v);
        add(row(pp));
        FCheckBox gu = flag("MOEWEN", "Möwen", true, v -> r.showGulls = v);
        add(row(gu));
        FCheckBox sm = flag("RAUCH", "Rauch und Dampf", true, v -> r.showSmoke = v);
        add(row(sm));
        FCheckBox tr = flag("BAEUME", "Bäume", true, v -> r.showTrees = v);
        add(row(tr));
        FCheckBox th = flag("HAEUSER", "Häuser und Dom", true, v -> r.showTown = v);
        add(row(th));
        tb = flag("PRUEFSTAND", "Prüfstand zeigen", false, v -> r.showTestbed = v);
        add(row(tb));
        glLbl.setForeground(MUTED);
        glLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        add(row(glLbl));
        note("Die Auflösung unter 100 % rechnet weniger Bildpunkte und vergrößert das Bild danach; automatisch hält sie die Grafikkarte unter 15 ms je Bild. Die Belichtung folgt dem Bild wie ein Auge; der Regler verschiebt sie um bis zu zwei Blendenstufen. Der Prüfstand zeigt Backstein, Marmor, Granit, Kupfer, Eiche, Eisen und klares Wasser im Sonnenlicht.");

        add(Box.createVerticalGlue());
        r.state.haze = hazeOf(haze.getValue());
        apply();
        updateLabels();
        // Bei laufender Uhr die Regler mitführen
        new Timer(500, e -> syncFromScene()).start();
    }

    // Scrollable: so breit wie der sichtbare Bereich, damit rechts nichts abgeschnitten wird
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return 120; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }

    private void apply() {
        r.state.clock.set(day.getValue(), time.getValue() / 60.0);
    }

    private FCheckBox tb;
    private FCheckBox[] bridgeBoxes;

    private void syncFromScene() {
        if (tb != null && tb.isSelected() != r.showTestbed) tb.setSelected(r.showTestbed);
        if (bridgeBoxes != null) for (int i = 0; i < bridgeBoxes.length; i++) if (bridgeBoxes[i].isSelected() != r.bridgeWant[i]) bridgeBoxes[i].setSelected(r.bridgeWant[i]);
        if (r.autoScale) { fromScene = true; scale.setValue((int) Math.round(r.renderScale * 100)); fromScene = false; }
        if (r.timeLapse <= 0) { glLbl.setText(r.glInfo()); updateLabels(); return; }
        fromScene = true;
        day.setValue(r.state.clock.day());
        time.setValue((int) Math.floor(r.state.clock.hour() * 60));
        fromScene = false;
        updateLabels();
        glLbl.setText(r.glInfo());
    }


    // ------------------------------------------------------------------ Zustand und Ansichten

    /** Der Zustand für Datenbank und Datei: Zeit, Wetter, Schalter und Kamera. */
    public Zustand capture() {
        Zustand z = new Zustand();
        z.setze("TAG", day.getValue());
        z.setze("STUNDE", time.getValue() / 60.0);
        z.setze("ZEITRAFFER", Math.max(0, lapse.getSelectedIndex()));
        z.setze("DUNST", haze.getValue());
        z.setze("WIND", wind.getValue());
        z.setze("NEBEL", r.state.mistMode);
        z.setze("WETTER", r.state.weatherMode);
        z.setze("BELICHTUNG", ev.getValue());
        z.setze("AUFLOESUNG", scale.getValue());
        for (Map.Entry<String, Object[]> e : flags.entrySet()) z.setze(e.getKey(), ((FCheckBox) e.getValue()[0]).isSelected());
        com.dan.kneiphof.camera.OrbitCamera c = r.camera;
        // Während einer Fahrt oder im Dom läuft die Kamera nicht auf ihrem Drehpunkt: dann gilt die letzte freie Ansicht
        if (!c.walking && !r.director.active() && r.follow < 0) {
            letzteKamera = new double[]{c.tx, c.ty, c.tz, (Math.toDegrees(c.yaw) % 360 + 360) % 360, Math.toDegrees(c.pitch), c.dist};
        }
        if (letzteKamera != null) {
            z.setze("DREHPUNKT_X", letzteKamera[0]).setze("DREHPUNKT_Y", letzteKamera[1]).setze("DREHPUNKT_Z", letzteKamera[2])
                    .setze("GIER_GRAD", letzteKamera[3]).setze("NICK_GRAD", letzteKamera[4]).setze("ABSTAND_M", letzteKamera[5]);
            z.kameraGesetzt = true;
        }
        return z;
    }

    /** Stellt einen Zustand wieder her: Regler, Schalter, Wetter und Kamera. */
    public void restore(Zustand z) {
        if (z == null) return;
        day.setValue(z.ganz("TAG"));
        time.setValue(Math.max(0, Math.min(24 * 60 - 1, (int) Math.round(z.zahl("STUNDE") * 60))));
        haze.setValue(z.ganz("DUNST"));
        wind.setValue(z.ganz("WIND"));
        ev.setValue(z.ganz("BELICHTUNG"));
        scale.setValue(z.ganz("AUFLOESUNG"));
        r.renderScale = scale.getValue() / 100.0;
        lapse.setSelectedIndex(Math.max(0, Math.min(4, z.ganz("ZEITRAFFER"))));
        mistBox.setSelectedIndex(Math.max(0, Math.min(3, z.ganz("NEBEL"))));
        weatherBox.setSelectedIndex(Math.max(0, Math.min(2, z.ganz("WETTER"))));
        for (Map.Entry<String, Object[]> e : flags.entrySet()) {
            boolean v = z.flag(e.getKey());
            ((FCheckBox) e.getValue()[0]).setSelected(v);
            @SuppressWarnings("unchecked") Consumer<Boolean> set = (Consumer<Boolean>) e.getValue()[1];
            set.accept(v);
        }
        apply();
        r.state.haze = hazeOf(haze.getValue());
        r.state.windSpeed = wind.getValue() / 10.0;
        r.state.ev = ev.getValue() / 10.0;
        updateLabels();
        if (z.kameraGesetzt) {
            if (r.camera.walking) r.camera.stopWalk();
            r.camera.lookAt(z.zahl("DREHPUNKT_X"), z.zahl("DREHPUNKT_Y"), z.zahl("DREHPUNKT_Z"), z.zahl("GIER_GRAD"), z.zahl("NICK_GRAD"), z.zahl("ABSTAND_M"));
            r.camera.snap();
            letzteKamera = new double[]{z.zahl("DREHPUNKT_X"), z.zahl("DREHPUNKT_Y"), z.zahl("DREHPUNKT_Z"), z.zahl("GIER_GRAD"), z.zahl("NICK_GRAD"), z.zahl("ABSTAND_M")};
        }
    }

    /** Taste V oder Knopf: fragt nach einem Namen und merkt den Zustand als Ansicht. */
    public void ansichtMerken() {
        if (!Dienst.bereit()) {
            FOptionPane.showMessageDialog(this, "Ansichten brauchen die Datenbank. Sie ist gerade nicht verbunden: " + Dienst.kurz(), "Kneiphof", FOptionPane.INFORMATION_MESSAGE);
            view.requestFocusInWindow();
            return;
        }
        String name = FOptionPane.showInputDialog(this, "Name der Ansicht:", "Kneiphof · Ansicht merken", FOptionPane.PLAIN_MESSAGE);
        view.requestFocusInWindow();
        if (name == null || name.trim().isEmpty()) return;
        final String n = name.trim();
        final Zustand z = capture();
        Thread t = new Thread(() -> {
            boolean ok = Dienst.merken(z, n);
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (!ok) FOptionPane.showMessageDialog(this, "Die Ansicht konnte nicht gespeichert werden.", "Kneiphof", FOptionPane.WARNING_MESSAGE);
                ansichtenAktualisieren();
            });
        }, "Kneiphof-Ansicht");
        t.setDaemon(true);
        t.start();
    }

    private void ansichtLaden() {
        int i = ansichtBox.getSelectedIndex();
        if (i < 0 || i >= ansichten.size()) return;
        final com.dan.kneiphof.db.ZustandDb.Eintrag e = ansichten.get(i);
        Thread t = new Thread(() -> {
            Zustand z = Dienst.ladeGemerkt(e.id);
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (z != null) { r.stopTour(); restore(z); Dienst.ereignis("ANSICHT", e.name, null); }
                view.requestFocusInWindow();
            });
        }, "Kneiphof-Ansicht");
        t.setDaemon(true);
        t.start();
    }

    private void ansichtVergessen() {
        int i = ansichtBox.getSelectedIndex();
        if (i < 0 || i >= ansichten.size()) return;
        final com.dan.kneiphof.db.ZustandDb.Eintrag e = ansichten.get(i);
        if (FOptionPane.showConfirmDialog(this, "Ansicht „" + e.name + "“ vergessen?", "Kneiphof", FOptionPane.OK_CANCEL_OPTION) != FOptionPane.OK_OPTION) return;
        Thread t = new Thread(() -> {
            Dienst.vergiss(e.id);
            javax.swing.SwingUtilities.invokeLater(this::ansichtenAktualisieren);
        }, "Kneiphof-Ansicht");
        t.setDaemon(true);
        t.start();
    }

    /** Liest die gemerkten Ansichten im Hintergrund und füllt die Liste. */
    private void ansichtenAktualisieren() {
        if (ansichtBox == null) return;
        Thread t = new Thread(() -> {
            final List<com.dan.kneiphof.db.ZustandDb.Eintrag> l = Dienst.gemerkte();
            javax.swing.SwingUtilities.invokeLater(() -> {
                ansichten.clear();
                ansichten.addAll(l);
                Object[] items = l.isEmpty() ? new Object[]{Dienst.bereit() ? "(keine gemerkt)" : "(ohne Datenbank)"} : l.toArray();
                ansichtBox.setModel(new DefaultComboBoxModel(items));
            });
        }, "Kneiphof-Ansichten");
        t.setDaemon(true);
        t.start();
    }

    private void updateLabels() {
        dayLbl.setText("Tag   " + SunClock.dateLabel(day.getValue()));
        timeLbl.setText("Uhrzeit   " + SunClock.timeLabel(time.getValue() / 60.0) + " MEZ");
        hazeLbl.setText(String.format(Locale.GERMAN, "Dunst   %s (%d %%)", haze.getValue() < 25 ? "klar" : haze.getValue() < 60 ? "leicht" : "diesig",
                haze.getValue()));
        windLbl.setText(String.format(Locale.GERMAN, "Wind   %.1f m/s aus WSW", wind.getValue() / 10.0));
        scaleLbl.setText("Auflösung   " + scale.getValue() + " %");
        evLbl.setText(String.format(Locale.GERMAN, "Belichtung   %+.1f EV", ev.getValue() / 10.0));
    }

    /** Regler 0..100 auf Mie-Koeffizient 6e-6 … 1,2e-4 je Meter (logarithmisch). */
    static double hazeOf(int v) {
        return 6e-6 * Math.pow(20, v / 100.0);
    }

    // ------------------------------------------------------------------ Bausteine

    static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(INK);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return l;
    }

    private void head(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(ACCENT);
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        add(row(l));
    }

    private JLabel ansichtNote;

    private JLabel note(String text) {
        JLabel l = new JLabel("<html><body style='width:205px'>" + text + "</body></html>");
        l.setForeground(MUTED);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        add(row(l));
        return l;
    }

    private void gap() { add(Box.createVerticalStrut(14)); }

    private static JComponent row(Component c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(c, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height + 6));
        return p;
    }
}
