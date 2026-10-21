package com.constsmodels;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The print's colors, along the left of the viewer's bar: whether the view shows the game's model or the print,
 * whether the print stands on a base, how many filaments the print uses (1 to {@link PrintColors#MAX}), and a swatch
 * for each. Click a swatch to choose the filament you'll load for it, from common filament colors or any color at all.
 *
 * <p>The model's colors are merged down to the filaments off the Swing thread when the model is big. Choosing a
 * color, or how many, or turning the base on or off, shows the print.
 */
final class FilamentControls extends JPanel
{
    private static final Logger log = LoggerFactory.getLogger(FilamentControls.class);

    /** Common filament colors to choose from, and their names. */
    static final int[] FILAMENTS = {
        0xF4F4F2, 0xC4C7CA, 0x8C8E90, 0x4A4C4E, 0x1C1C1E, 0xD9C7A5,
        0xC62828, 0xEF6C1A, 0xF6C312, 0x7CB342, 0x2E7D32, 0x00897B,
        0x4FA3E0, 0x1E4FB4, 0x6A3FA0, 0xEE8CB4, 0x6D4426, 0xC9A04A,
    };
    static final String[] FILAMENT_NAMES = {
        "White", "Silver", "Grey", "Dark grey", "Black", "Beige",
        "Red", "Orange", "Yellow", "Lime", "Green", "Teal",
        "Sky blue", "Blue", "Purple", "Pink", "Brown", "Gold",
    };

    // Big models are merged off the Swing thread
    private static final int MERGE_HERE = 30_000;

    private static final int SWATCH = 16;
    private static final int CONTROL_HEIGHT = 28;

    private final Mesh mesh;
    private final Runnable changed;
    private final Toggle show = new Toggle("Game", "Print");
    private final Switch base = new Switch("Base");
    private final JComboBox<Integer> count = new JComboBox<>();
    private final JPanel swatches = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));

    // The filaments as merged, and as chosen; null until the first merge is done
    private PrintColors merged;
    private PrintColors colors;

    // Which merge was asked for last, so an older one finishing late is let go
    private int asked;

    /**
     * @param start how many filaments to start with, as in the settings
     * @param onBase whether the print starts on a base, as in the settings
     * @param changed called on the Swing thread when the filaments, the base, or what the view shows, change
     */
    FilamentControls(Mesh mesh, int start, boolean onBase, Runnable changed)
    {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        this.mesh = mesh;
        this.changed = changed;
        setOpaque(false);

        show.setToolTipText("<html>Game: the model as the game has it.<br>"
            + "Print: made into one closed solid that prints as is, in your filaments' colors.<br>"
            + "Save saves the one you see.</html>");
        show.onChange(changed);

        base.setOn(onBase, false);
        base.setToolTipText("<html>Whether the print stands on a solid base, in its darkest filament.<br>"
            + "Starts as the Add a base setting.</html>");
        base.onChange(() ->
        {
            if (show.isRight())
            {
                changed.run();
            }
            else
            {
                show.setRight(true);
            }
        });

        // No more filaments than the model has colors
        int most = Math.max(1, Math.min(PrintColors.MAX, distinctColors(mesh, PrintColors.MAX)));
        for (int i = 1; i <= most; i++)
        {
            count.addItem(i);
        }
        int first = Math.max(1, Math.min(most, start));
        count.setSelectedItem(first);
        count.setFont(FontManager.getRunescapeFont());
        count.setFocusable(false);
        count.setPreferredSize(new Dimension(80, CONTROL_HEIGHT));
        count.setToolTipText("How many filament colors the print uses");
        count.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                boolean focused)
            {
                int n = value == null ? 0 : (Integer) value;
                return super.getListCellRendererComponent(list, n == 1 ? "1 color" : n + " colors", index, selected,
                    focused);
            }
        });
        count.addActionListener(e ->
        {
            Integer n = (Integer) count.getSelectedItem();
            if (n != null && (merged == null || n != merged.count()))
            {
                show.setRight(true);
                merge(n);
            }
        });

        swatches.setOpaque(false);
        swatches.setBorder(new EmptyBorder((CONTROL_HEIGHT - SWATCH) / 2, 0, 0, 0));

        add(show);
        add(gap(6));
        add(base);
        add(gap(6));
        add(count);
        add(gap(8));
        add(swatches);
        merge(first);
    }

    /** The filaments as chosen, or null while the first merge is still going. */
    PrintColors colors()
    {
        return colors;
    }

    /** Whether the view is on the print rather than the game's model. */
    boolean showsPrint()
    {
        return show.isRight();
    }

    /** Whether the print stands on a base. */
    boolean base()
    {
        return base.isOn();
    }

    /** Puts the print on a base or takes it off, as when the setting changes, without changing what the view shows. */
    void setBase(boolean on)
    {
        if (on != base.isOn())
        {
            base.setOn(on, false);
            changed.run();
        }
    }

    /** Each triangle's color in the filaments' colors while the print is on, or null for the game's own. */
    int[] shownColors()
    {
        return show.isRight() && colors != null ? colors.applyTo(mesh).color : null;
    }

    /** How many different colors the mesh has, counting no further than most + 1. */
    static int distinctColors(Mesh mesh, int most)
    {
        Set<Integer> seen = new HashSet<>();
        for (int f = 0; f < mesh.faceCount() && seen.size() <= most; f++)
        {
            seen.add(mesh.color[f]);
        }
        return seen.size();
    }

    private void merge(int n)
    {
        int ticket = ++asked;
        if (mesh.faceCount() <= MERGE_HERE)
        {
            merged(PrintColors.of(mesh, n));
            return;
        }
        Thread thread = new Thread(() ->
        {
            try
            {
                PrintColors made = PrintColors.of(mesh, n);
                SwingUtilities.invokeLater(() ->
                {
                    if (ticket == asked)
                    {
                        merged(made);
                    }
                });
            }
            catch (RuntimeException e)
            {
                log.warn("Couldn't merge the colors of {}", mesh.name, e);
            }
        }, "consts-Models colors");
        thread.setDaemon(true);
        thread.start();
    }

    private void merged(PrintColors made)
    {
        merged = made;
        colors = made;
        rebuild();
        changed.run();
    }

    private void choose(int filament, int rgb)
    {
        colors = colors.withColor(filament, rgb);
        show.setRight(true);
        rebuild();
        changed.run();
    }

    private void rebuild()
    {
        swatches.removeAll();
        for (int i = 0; i < colors.count(); i++)
        {
            int filament = i;
            Swatch swatch = new Swatch(colors.color(i), SWATCH);
            swatch.setToolTipText("Filament " + (i + 1) + ": " + hex(colors.color(i)) + ". Click to choose its color");
            swatch.onClick(() -> openPalette(filament, swatch));
            swatches.add(swatch);
        }
        swatches.revalidate();
        swatches.repaint();
    }

    /** The colors to choose from for one filament, opening above its swatch. */
    private void openPalette(int filament, Swatch swatch)
    {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR));
        popup.add(palette(filament, () -> popup.setVisible(false)));
        swatch.setSelected(true);
        popup.addPopupMenuListener(new PopupMenuListener()
        {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e)
            {
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
            {
                swatch.setSelected(false);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e)
            {
            }
        });
        Dimension size = popup.getPreferredSize();
        popup.show(swatch, (swatch.getWidth() - size.width) / 2, -size.height - 8);
    }

    /** What the palette holds: common filament colors, the model's own color for this filament, and any color. */
    JPanel palette(int filament, Runnable close)
    {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("Filament " + (filament + 1));
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(ColorScheme.TEXT_COLOR);
        panel.add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 6, 4, 4));
        grid.setOpaque(false);
        for (int i = 0; i < FILAMENTS.length; i++)
        {
            int rgb = FILAMENTS[i];
            Swatch choice = new Swatch(rgb, 24);
            choice.setSelected(rgb == colors.color(filament));
            choice.setToolTipText(FILAMENT_NAMES[i]);
            choice.onClick(() ->
            {
                close.run();
                choose(filament, rgb);
            });
            grid.add(choice);
        }
        panel.add(grid, BorderLayout.CENTER);

        // The model's own color for it, and any color at all
        int own = merged.color(filament);
        JButton model = smallButton("Model's color");
        model.setIcon(new SwatchIcon(own));
        model.setToolTipText("Back to the color merged from the model: " + hex(own));
        model.addActionListener(e ->
        {
            close.run();
            choose(filament, own);
        });
        JButton custom = smallButton("Custom…");
        custom.setToolTipText("Any color");
        custom.addActionListener(e ->
        {
            close.run();
            Color picked = JColorChooser.showDialog(this, "Filament " + (filament + 1),
                new Color(colors.color(filament)));
            if (picked != null)
            {
                choose(filament, picked.getRGB() & 0xFFFFFF);
            }
        });
        JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
        buttons.setOpaque(false);
        buttons.add(model);
        buttons.add(custom);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
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

    private static JComponent gap(int width)
    {
        JPanel gap = new JPanel();
        gap.setOpaque(false);
        gap.setPreferredSize(new Dimension(width, CONTROL_HEIGHT));
        return gap;
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

    /** One choice, lit while it's on, with a mark that's filled: whether the print stands on a base. */
    static final class Switch extends JComponent
    {
        private static final int MARK = 8;

        private final String text;
        private boolean on;
        private Runnable change;

        Switch(String text)
        {
            this.text = text;
            setFont(FontManager.getRunescapeFont());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            FontMetrics metrics = getFontMetrics(getFont());
            setPreferredSize(new Dimension(metrics.stringWidth(text) + MARK + 18, CONTROL_HEIGHT));
            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent e)
                {
                    if (SwingUtilities.isLeftMouseButton(e))
                    {
                        setOn(!on, true);
                    }
                }
            });
        }

        void onChange(Runnable change)
        {
            this.change = change;
        }

        boolean isOn()
        {
            return on;
        }

        /** Turns it on or off, and says so if asked to and it changed. */
        void setOn(boolean on, boolean tell)
        {
            if (on == this.on)
            {
                return;
            }
            this.on = on;
            repaint();
            if (tell && change != null)
            {
                change.run();
            }
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();
            g2.setColor(on ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
            g2.fillRect(0, 0, w, h);
            g2.setColor(ColorScheme.BORDER_COLOR);
            g2.drawRect(0, 0, w - 1, h - 1);

            g2.setFont(getFont());
            FontMetrics metrics = g2.getFontMetrics();
            int left = (w - MARK - 5 - metrics.stringWidth(text)) / 2;
            int top = (h - MARK) / 2;
            if (on)
            {
                g2.setColor(ColorScheme.BRAND_ORANGE);
                g2.fillRect(left, top, MARK, MARK);
            }
            else
            {
                g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
                g2.drawRect(left, top, MARK - 1, MARK - 1);
            }
            int baseline = (h - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.setColor(on ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
            g2.drawString(text, left + MARK + 5, baseline);
            g2.dispose();
        }
    }

    /** Two choices side by side, one of them picked: the game's colors or the print's. */
    private static final class Toggle extends JComponent
    {
        private final String left;
        private final String right;
        private boolean onRight;
        private Runnable change;

        Toggle(String left, String right)
        {
            this.left = left;
            this.right = right;
            setFont(FontManager.getRunescapeFont());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            FontMetrics metrics = getFontMetrics(getFont());
            int half = Math.max(metrics.stringWidth(left), metrics.stringWidth(right)) + 18;
            setPreferredSize(new Dimension(half * 2, CONTROL_HEIGHT));
            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent e)
                {
                    if (SwingUtilities.isLeftMouseButton(e))
                    {
                        setRight(e.getX() >= getWidth() / 2);
                    }
                }
            });
        }

        void onChange(Runnable change)
        {
            this.change = change;
        }

        boolean isRight()
        {
            return onRight;
        }

        void setRight(boolean right)
        {
            if (right == onRight)
            {
                return;
            }
            onRight = right;
            repaint();
            if (change != null)
            {
                change.run();
            }
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();
            int half = w / 2;
            g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
            g2.fillRect(0, 0, w, h);
            g2.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
            g2.fillRect(onRight ? half : 0, 0, onRight ? w - half : half, h);
            g2.setColor(ColorScheme.BORDER_COLOR);
            g2.drawRect(0, 0, w - 1, h - 1);

            g2.setFont(getFont());
            FontMetrics metrics = g2.getFontMetrics();
            int baseline = (h - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.setColor(onRight ? ColorScheme.LIGHT_GRAY_COLOR : Color.WHITE);
            g2.drawString(left, (half - metrics.stringWidth(left)) / 2, baseline);
            g2.setColor(onRight ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
            g2.drawString(right, half + (w - half - metrics.stringWidth(right)) / 2, baseline);
            g2.dispose();
        }
    }
}
