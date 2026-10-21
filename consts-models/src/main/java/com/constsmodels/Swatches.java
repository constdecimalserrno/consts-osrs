package com.constsmodels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Squares of color to pick from, and the small buttons beside them, as the background button shows them. */
final class Swatches
{
    private Swatches()
    {
    }

    static JButton smallButton(String text)
    {
        JButton button = new JButton(text);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setForeground(ColorScheme.TEXT_COLOR);
        button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        button.setFocusPainted(false);
        return button;
    }

    static String hex(int rgb)
    {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /** An outline that shows on a swatch of any color: light on dark colors and dark on light ones. */
    static Color outline(int rgb)
    {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        return r * 299 + g * 587 + b * 114 < 60_000 ? new Color(255, 255, 255, 60) : new Color(0, 0, 0, 90);
    }

    /** A square of color to click. */
    static final class Swatch extends JComponent
    {
        private final int rgb;
        private boolean hover;
        private boolean selected;
        private Runnable click;

        Swatch(int rgb, int size)
        {
            this.rgb = rgb;
            setPreferredSize(new Dimension(size, size));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent e)
                {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    hover = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e)
                {
                    if (click != null && SwingUtilities.isLeftMouseButton(e))
                    {
                        click.run();
                    }
                }
            });
        }

        void onClick(Runnable click)
        {
            this.click = click;
        }

        void setSelected(boolean selected)
        {
            this.selected = selected;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(new Color(rgb));
            g2.fillRoundRect(1, 1, w - 2, h - 2, 4, 4);
            if (hover || selected)
            {
                g2.setColor(ColorScheme.BRAND_ORANGE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRoundRect(1, 1, w - 2, h - 2, 4, 4);
            }
            else
            {
                g2.setColor(outline(rgb));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 4, 4);
            }
            g2.dispose();
        }
    }

    /** A small square of color beside a button's text. */
    static final class SwatchIcon implements Icon
    {
        private final int rgb;

        SwatchIcon(int rgb)
        {
            this.rgb = rgb;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            g.setColor(new Color(rgb));
            g.fillRect(x, y, 10, 10);
            g.setColor(outline(rgb));
            g.drawRect(x, y, 9, 9);
        }

        @Override
        public int getIconWidth()
        {
            return 10;
        }

        @Override
        public int getIconHeight()
        {
            return 10;
        }
    }
}
