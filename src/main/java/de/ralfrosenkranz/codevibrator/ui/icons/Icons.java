package de.ralfrosenkranz.codevibrator.ui.icons;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class Icons {
    private Icons() {}

    public static Icon warning() {
        return make("!", new Color(0xD9A441));
    }

    public static Icon lock() {
        return make("L", new Color(0xB55B5B));
    }

    public static Icon excluded() {
        return make("X", new Color(0x888888));
    }

    private static Icon make(String text, Color color) {
        int w = 16, h = 16;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.fillRoundRect(0, 0, w-1, h-1, 6, 6);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        int tx = (w - fm.stringWidth(text)) / 2;
        int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(text, tx, ty);
        g.dispose();
        return new ImageIcon(img);
    }
}
