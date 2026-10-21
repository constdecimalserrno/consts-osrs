package com.constsmodels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The viewer's Background button, its color beside the word: click it for backgrounds to draw the model on, from the
 * viewer's own dark fall to white or a green screen, or any color at all. Pictures saved from the viewer are drawn on
 * it too.
 */
final class BackgroundButton extends JButton
{
    /** Backgrounds to choose from, the viewer's own first, and their names. */
    static final int[] BACKGROUNDS = {
        ViewerCanvas.DEFAULT_BACKGROUND, 0x000000, 0x404040, 0x808080, 0xC8C8C8,
        0xFFFFFF, 0x00B140, 0x0047BB, 0x87CEEB, 0xD9C7A5,
    };
    static final String[] BACKGROUND_NAMES = {
        "The viewer's own", "Black", "Dark grey", "Grey", "Light grey",
        "White", "Green screen", "Blue screen", "Sky", "Sand",
    };

    // The color a swatch shows for the viewer's own background, its fall being from 0x323232 to the darker grey
    private static final int DEFAULT_SWATCH = 0x2B2B2B;

    private final IntConsumer picked;
    private int background;

    BackgroundButton(int background, IntConsumer picked)
    {
        super("Background");
        this.picked = picked;
        setFont(FontManager.getRunescapeFont());
        setForeground(ColorScheme.TEXT_COLOR);
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setFocusPainted(false);
        setIconTextGap(6);
        setBackgroundColor(background);
        setPreferredSize(new Dimension(getPreferredSize().width, 28));
        addActionListener(e -> open());
    }

    /** Shows this background as the one chosen, without telling anyone. */
    void setBackgroundColor(int background)
    {
        this.background = background;
        setIcon(new FilamentControls.SwatchIcon(swatch(background)));
        setToolTipText("Background: " + name(background) + ". Click to change it");
    }

    static int swatch(int background)
    {
        return background == ViewerCanvas.DEFAULT_BACKGROUND ? DEFAULT_SWATCH : background;
    }

    static String name(int background)
    {
        for (int i = 0; i < BACKGROUNDS.length; i++)
        {
            if (BACKGROUNDS[i] == background)
            {
                return BACKGROUND_NAMES[i];
            }
        }
        return FilamentControls.hex(background);
    }

    private void open()
    {
        JPopupMenu popup = new JPopupMenu();
        popup.setBorder(BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR));
        popup.add(palette(() -> popup.setVisible(false)));
        Dimension size = popup.getPreferredSize();
        popup.show(this, (getWidth() - size.width) / 2, -size.height - 8);
    }

    /** What the popup holds: the backgrounds to choose from, and any color. */
    JPanel palette(Runnable close)
    {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("Background");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(ColorScheme.TEXT_COLOR);
        panel.add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 5, 4, 4));
        grid.setOpaque(false);
        for (int i = 0; i < BACKGROUNDS.length; i++)
        {
            int choice = BACKGROUNDS[i];
            FilamentControls.Swatch swatch = new FilamentControls.Swatch(swatch(choice), 24);
            swatch.setSelected(choice == background);
            swatch.setToolTipText(BACKGROUND_NAMES[i]);
            swatch.onClick(() ->
            {
                close.run();
                choose(choice);
            });
            grid.add(swatch);
        }
        panel.add(grid, BorderLayout.CENTER);

        JButton custom = FilamentControls.smallButton("Custom…");
        custom.setToolTipText("Any color");
        custom.addActionListener(e ->
        {
            close.run();
            Color chosen = JColorChooser.showDialog(this, "Background", new Color(swatch(background)));
            if (chosen != null)
            {
                choose(chosen.getRGB() & 0xFFFFFF);
            }
        });
        panel.add(custom, BorderLayout.SOUTH);
        return panel;
    }

    private void choose(int background)
    {
        setBackgroundColor(background);
        picked.accept(background);
    }
}
