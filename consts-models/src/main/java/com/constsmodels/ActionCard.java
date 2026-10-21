package com.constsmodels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

/**
 * A button the way RuneLite's own sidebar draws its links: a dark box with an optional icon, a title and a line
 * under it. It lights up under the mouse and runs its action when clicked.
 */
class ActionCard extends JPanel
{
    private final JLabel title = new JLabel();
    private final JLabel detail = new JLabel();
    private final JPanel text = new JPanel(new DynamicGridLayout(2, 1, 0, 1));
    private final Runnable action;

    /* Its color, and under the mouse; pressed, a shade lighter still */
    private Color base = ColorScheme.DARKER_GRAY_COLOR;
    private Color hover = ColorScheme.DARKER_GRAY_HOVER_COLOR;

    /** A card with its title in the given font and the line under it in the small font, padded by the given gap. */
    ActionCard(Icon icon, Font titleFont, int padding, Runnable action)
    {
        super(new BorderLayout(10, 0));
        this.action = action;

        setBorder(new EmptyBorder(padding, 10, padding, 10));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        if (icon != null)
        {
            JLabel iconLabel = new JLabel(icon);
            iconLabel.setVerticalAlignment(JLabel.CENTER);
            add(iconLabel, BorderLayout.WEST);
        }

        title.setFont(titleFont);
        title.setForeground(Color.WHITE);
        detail.setFont(FontManager.getRunescapeSmallFont());
        detail.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        text.add(title);
        text.add(detail);
        text.setOpaque(false);
        add(text, BorderLayout.CENTER);

        setBackground(base);

        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                setBackground(hover);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                setBackground(base);
            }

            @Override
            public void mousePressed(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton(e))
                {
                    setBackground(hover.brighter());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (!SwingUtilities.isLeftMouseButton(e))
                {
                    return;
                }
                boolean inside = contains(e.getPoint());
                setBackground(inside ? hover : base);
                if (inside)
                {
                    click();
                }
            }
        });
    }

    @Override
    public Dimension getPreferredSize()
    {
        // As narrow as it likes, so a long title is cut short with dots instead of widening the sidebar, which
        // stretches it to its own width
        return new Dimension(0, super.getPreferredSize().height);
    }

    /** Its color, and its color under the mouse; RuneLite's dark grey box until set. */
    void setColors(Color base, Color hover)
    {
        this.base = base;
        this.hover = hover;
        setBackground(base);
    }

    void setTitle(String text)
    {
        title.setText(text);
    }

    String getTitle()
    {
        return title.getText();
    }

    void setDetail(String text)
    {
        detail.setText(text);
    }

    String getDetail()
    {
        return detail.getText();
    }

    /** Does what a click on the card does. */
    void click()
    {
        action.run();
    }
}
