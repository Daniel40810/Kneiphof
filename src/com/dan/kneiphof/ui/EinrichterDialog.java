package com.dan.kneiphof.ui;

import com.dan.fbutton.FButton;
import com.dan.kneiphof.db.Db;
import com.dan.kneiphof.db.Dienst;
import com.dan.kneiphof.db.Einrichter;

import javax.swing.BorderFactory;
import com.dan.foptionpane.FDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.sql.Connection;

/** Zeigt den Lauf des Einrichters Zeile für Zeile; er legt nur Fehlendes an und meldet am Ende „alles in Ordnung“ oder die Probleme. */
final class EinrichterDialog extends FDialog {
    private final JTextArea log = new JTextArea();
    private final FButton start = new FButton("Einrichter starten"), close = new FButton("Schließen");
    private final JLabel head = new JLabel();
    private volatile boolean running;

    EinrichterDialog(Window owner, boolean autostart) {
        super(owner, "Einrichter · Datenbank", ModalityType.APPLICATION_MODAL);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(ControlPanel.BG);
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        head.setForeground(ControlPanel.INK);
        head.setFont(new Font("SansSerif", Font.PLAIN, 13));
        head.setText("<html>Der Einrichter legt fehlende KNH_-Objekte im Schema DEMO an und prüft sie. Er ändert und löscht nichts, was es schon gibt.</html>");
        p.add(head, BorderLayout.NORTH);
        log.setEditable(false);
        log.setBackground(new Color(8, 12, 15));
        log.setForeground(ControlPanel.INK);
        log.setCaretColor(ControlPanel.INK);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        log.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane sp = new JScrollPane(log);
        sp.setBorder(BorderFactory.createLineBorder(new Color(40, 52, 60)));
        p.add(sp, BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        south.setOpaque(false);
        start.addActionListener(e -> los());
        close.addActionListener(e -> { if (!running) dispose(); });
        south.add(start);
        south.add(close);
        p.add(south, BorderLayout.SOUTH);
        JPanel cp = getComponentPane();
        cp.setLayout(new BorderLayout());
        cp.add(p, BorderLayout.CENTER);
        setPreferredDialogSize(new Dimension(820, 600));
        pack();
        setLocationRelativeTo(owner);
        if (autostart) SwingUtilities.invokeLater(this::los);
    }

    private void zeile(String s) { SwingUtilities.invokeLater(() -> { log.append(s + "\n"); log.setCaretPosition(log.getDocument().getLength()); }); }

    private void los() {
        if (running) return;
        Db.Konfig k = Dienst.konfig();
        if (k == null || !k.hatPasswort()) { log.setText("Keine Zugangsdaten: db/db.properties fehlt oder das Passwort ist nicht eingegeben.\n"); return; }
        running = true;
        start.setEnabled(false);
        close.setEnabled(false);
        log.setText("");
        head.setText("Einrichter läuft für " + k.kurz() + " …");
        Thread t = new Thread(() -> {
            try (Connection c = Dienst.neueVerbindung()) {
                Einrichter.Ergebnis e = Einrichter.lauf(c, Db.ordner(), this::zeile);
                SwingUtilities.invokeLater(() -> head.setText(e.ok ? "Fertig: alles in Ordnung." : "Fertig: " + e.probleme + " Probleme, siehe Protokoll."));
                Dienst.ereignis("EINRICHTER", e.ok ? "alles in Ordnung" : e.probleme + " Probleme", null);
            } catch (Exception ex) {
                zeile("FEHLER: " + String.valueOf(ex.getMessage()).split("\n")[0]);
                SwingUtilities.invokeLater(() -> head.setText("Fehlgeschlagen."));
            } finally {
                running = false;
                Dienst.neuPruefen();
                SwingUtilities.invokeLater(() -> { start.setText("Noch einmal"); start.setEnabled(true); close.setEnabled(true); });
            }
        }, "Kneiphof-Einrichter");
        t.setDaemon(true);
        t.start();
    }
}
