package com.dan.kneiphof.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Das Programmsymbol, gemalt statt geladen: der Dom auf dem Kneiphof mit seinem Westturm und dem
 * grünen Kupferhelm im Abendlicht, davor der Pregel mit Spiegelung und eine gehobene Klappbrücke,
 * deren Laterne leuchtet. Ab 32 Pixel abwärts eine gröbere Fassung, die nur Dom, Turm und Wasser zeigt.
 */
public final class AppIcon {
    public static final int[] SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};
    /** Größen in der .ico-Datei. */
    public static final int[] ICO_SIZES = {16, 24, 32, 48, 64, 128, 256};

    static final Color SKY_TOP = new Color(24, 34, 66), SKY_MID = new Color(92, 78, 112), SKY_LOW = new Color(236, 150, 86),
            BRICK = new Color(58, 30, 30), BRICK_LIT = new Color(150, 62, 44), COPPER = new Color(88, 160, 132),
            WATER_TOP = new Color(70, 62, 88), WATER_LOW = new Color(18, 24, 38), LAMP = new Color(255, 206, 120),
            IRON = new Color(26, 24, 32);

    private AppIcon() { }

    public static List<Image> images() {
        List<Image> l = new ArrayList<>();
        for (int s : SIZES) l.add(paint(s));
        return l;
    }

    /** Setzt das Symbol am Fenster und, wo unterstützt, in der Taskleiste. */
    public static void install(java.awt.Window w) {
        List<Image> l = images();
        w.setIconImages(l);
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar tb = java.awt.Taskbar.getTaskbar();
                if (tb.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) tb.setIconImage(l.get(l.size() - 1));
            }
        } catch (Exception | Error ignored) {
            // Windows nimmt ohnehin die Fenstersymbole
        }
    }

    /** Malt das Symbol in der Kantenlänge s (quadratisch, mit Alpha). */
    public static BufferedImage paint(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(s / 256.0, s / 256.0);
        boolean small = s <= 32;
        RoundRectangle2D tile = new RoundRectangle2D.Double(6, 6, 244, 244, small ? 64 : 54, small ? 64 : 54);
        double horizon = small ? 168 : 160;

        // Abendhimmel
        g.setPaint(new GradientPaint(0, 6, SKY_TOP, 0, (float) horizon, SKY_LOW));
        g.fill(tile);
        g.setClip(tile);
        g.setPaint(new GradientPaint(0, 40, new Color(SKY_MID.getRed(), SKY_MID.getGreen(), SKY_MID.getBlue(), 0),
                0, 120, new Color(SKY_MID.getRed(), SKY_MID.getGreen(), SKY_MID.getBlue(), 140)));
        g.fill(new Rectangle2D.Double(0, 40, 256, 80));
        // Sonne knapp über dem Horizont, rechts hinter der Brücke
        if (!small) {
            g.setPaint(new RadialGradientPaint(204, (float) horizon - 10, 70, new float[]{0f, 0.12f, 1f},
                    new Color[]{new Color(255, 236, 190, 255), new Color(255, 196, 120, 200), new Color(255, 160, 90, 0)}));
            g.fill(new Rectangle2D.Double(110, 60, 146, 110));
        }

        // Dom: Langhaus mit steilem Dach, Westturm mit Kupferhelm
        Path2D dom = new Path2D.Double();
        double nx0 = small ? 58 : 66, nx1 = small ? 196 : 176, eave = horizon - (small ? 40 : 44), ridge = eave - (small ? 34 : 36);
        dom.moveTo(nx0, horizon);
        dom.lineTo(nx0, eave);
        dom.lineTo((nx0 + nx1) / 2, ridge);
        dom.lineTo(nx1, eave);
        dom.lineTo(nx1, horizon);
        dom.closePath();
        double tx0 = small ? 30 : 40, tx1 = small ? 70 : 74, ttop = small ? 74 : 72;
        Path2D tower = new Path2D.Double();
        tower.append(new Rectangle2D.Double(tx0, ttop, tx1 - tx0, horizon - ttop), false);
        g.setColor(BRICK);
        g.fill(dom);
        g.fill(tower);
        // Abendlicht auf der Sonnenseite (rechts) der Giebel
        if (!small) {
            Path2D lit = new Path2D.Double();
            lit.moveTo((nx0 + nx1) / 2, ridge);
            lit.lineTo(nx1, eave);
            lit.lineTo(nx1, horizon);
            lit.lineTo(nx1 - 8, horizon);
            lit.lineTo(nx1 - 8, eave + 2);
            lit.closePath();
            g.setColor(new Color(BRICK_LIT.getRed(), BRICK_LIT.getGreen(), BRICK_LIT.getBlue(), 150));
            g.fill(lit);
            g.setColor(new Color(BRICK_LIT.getRed(), BRICK_LIT.getGreen(), BRICK_LIT.getBlue(), 110));
            g.fill(new Rectangle2D.Double(tx1 - 7, ttop, 7, horizon - ttop));
            // Strebepfeiler
            g.setColor(new Color(40, 20, 22));
            for (int i = 0; i < 5; i++) g.fill(new Rectangle2D.Double(nx0 + 14 + i * 21, eave - 2, 4, horizon - eave + 2));
        }
        // Helm: grüner Kupferspitzhelm
        Path2D helm = new Path2D.Double();
        double hx = (tx0 + tx1) / 2;
        helm.moveTo(tx0 - (small ? 2 : 3), ttop);
        helm.lineTo(hx, ttop - (small ? 50 : 52));
        helm.lineTo(tx1 + (small ? 2 : 3), ttop);
        helm.closePath();
        g.setColor(COPPER);
        g.fill(helm);
        if (!small) {
            g.setColor(new Color(150, 214, 186, 150));
            Path2D hl = new Path2D.Double();
            hl.moveTo(hx, ttop - 52);
            hl.lineTo(tx1 + 3, ttop);
            hl.lineTo(hx + 4, ttop);
            hl.closePath();
            g.fill(hl);
            g.setColor(LAMP);
            g.fill(new Rectangle2D.Double(hx - 0.8, ttop - 62, 1.6, 10));
        }
        // Fenster im Abendlicht
        g.setColor(LAMP);
        int nwin = small ? 3 : 5;
        double ww = small ? 10 : 7, wh = small ? 20 : 22;
        for (int i = 0; i < nwin; i++) {
            double x = nx0 + (small ? 18 : 20) + i * ((nx1 - nx0 - (small ? 36 : 40)) / (nwin - 1)) - ww / 2;
            RoundRectangle2D win = new RoundRectangle2D.Double(x, eave + (small ? 8 : 10), ww, wh, ww, ww);
            g.fill(win);
        }

        // Pregel mit Spiegelung
        g.setPaint(new GradientPaint(0, (float) horizon, WATER_TOP, 0, 250, WATER_LOW));
        g.fill(new Rectangle2D.Double(0, horizon, 256, 256 - horizon));
        if (!small) {
            // gespiegelter Dom, in Streifen gebrochen
            g.setColor(new Color(40, 24, 30, 170));
            for (int k = 0; k < 9; k++) {
                double y = horizon + 4 + k * 8;
                double shift = Math.sin(k * 1.7) * 3;
                double wdt = (nx1 - tx0) * (1 - k * 0.06);
                g.fill(new Rectangle2D.Double(tx0 + shift + k * 2, y, wdt, 4));
            }
            // Sonnenbahn auf dem Wasser
            g.setColor(new Color(255, 200, 130, 200));
            for (int k = 0; k < 8; k++) {
                double y = horizon + 3 + k * 10, w2 = 26 - k * 2.2;
                g.fill(new RoundRectangle2D.Double(204 - w2 / 2 + Math.sin(k * 2.3) * 4, y, w2, 2.6, 2.6, 2.6));
            }
            // gehobene Klappbrücke rechts vorn: Pfeiler und schräge Klappe
            g.setColor(IRON);
            g.fill(new Rectangle2D.Double(176, horizon - 6, 18, 60));
            AffineTransform at = g.getTransform();
            g.rotate(Math.toRadians(-62), 192, horizon - 4);
            g.fill(new Rectangle2D.Double(192, horizon - 10, 70, 9));
            g.setStroke(new BasicStroke(2.2f));
            for (int k = 0; k < 6; k++) g.draw(new java.awt.geom.Line2D.Double(196 + k * 11, horizon - 10, 202 + k * 11, horizon - 20));
            g.draw(new java.awt.geom.Line2D.Double(192, horizon - 20, 262, horizon - 20));
            g.setTransform(at);
            // Laterne
            g.setPaint(new RadialGradientPaint(185, (float) horizon - 22, 14, new float[]{0f, 1f},
                    new Color[]{new Color(255, 214, 140, 230), new Color(255, 190, 110, 0)}));
            g.fill(new Ellipse2D.Double(171, horizon - 36, 28, 28));
            g.setColor(LAMP);
            g.fill(new Ellipse2D.Double(182, horizon - 25, 6, 6));
            g.setColor(IRON);
            g.fill(new Rectangle2D.Double(184, horizon - 19, 2, 14));
        } else {
            g.setColor(new Color(255, 196, 120, 210));
            g.fill(new Rectangle2D.Double(150, horizon + 14, 70, 9));
            g.fill(new Rectangle2D.Double(168, horizon + 34, 40, 9));
        }
        // Ufer-Linie
        g.setColor(new Color(20, 16, 24));
        g.fill(new Rectangle2D.Double(0, horizon - (small ? 3 : 2), 256, small ? 6 : 4));

        g.setClip(null);
        // Goldkante als Rahmen, wie beim Heidelberg-Symbol (ab 48 Pixel; darunter würde sie das Bild verschlucken)
        if (!small) {
            g.setColor(new Color(236, 192, 100));
            g.setStroke(new BasicStroke(5f));
            g.draw(new RoundRectangle2D.Double(8.5, 8.5, 239, 239, 55, 55));
        }
        g.dispose();
        return img;
    }
}
