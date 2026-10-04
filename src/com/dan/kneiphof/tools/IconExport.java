package com.dan.kneiphof.tools;

import com.dan.kneiphof.ui.AppIcon;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Schreibt das Programmsymbol als {@code kneiphof.ico} (16 bis 256 Pixel, jede Größe als PNG in der
 * ICO-Datei, wie Windows es seit Vista liest) und als {@code kneiphof_256.png} in den Projektordner.
 * <p>In NetBeans: Rechtsklick, Run File. Argument: Zielordner (sonst der Arbeitsordner).</p>
 */
public final class IconExport {
    public static void main(String[] args) throws IOException {
        File dir = new File(args.length > 0 ? args[0] : ".");
        List<byte[]> pngs = new ArrayList<>();
        for (int s : AppIcon.ICO_SIZES) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ImageIO.write(AppIcon.paint(s), "png", bo);
            pngs.add(bo.toByteArray());
        }
        int n = pngs.size();
        ByteBuffer head = ByteBuffer.allocate(6 + 16 * n).order(ByteOrder.LITTLE_ENDIAN);
        head.putShort((short) 0).putShort((short) 1).putShort((short) n);
        int offset = 6 + 16 * n;
        for (int i = 0; i < n; i++) {
            int s = AppIcon.ICO_SIZES[i];
            head.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s));
            head.put((byte) 0).put((byte) 0);              // keine Palette
            head.putShort((short) 1).putShort((short) 32);  // Ebenen, Bit je Pixel
            head.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        File ico = new File(dir, "kneiphof.ico");
        try (FileOutputStream out = new FileOutputStream(ico)) {
            out.write(head.array());
            for (byte[] p : pngs) out.write(p);
        }
        BufferedImage big = AppIcon.paint(256);
        ImageIO.write(big, "png", new File(dir, "kneiphof_256.png"));
        System.out.println("Geschrieben: " + ico.getAbsolutePath() + " (" + ico.length() + " Bytes, " + n + " Größen)");
    }
}
