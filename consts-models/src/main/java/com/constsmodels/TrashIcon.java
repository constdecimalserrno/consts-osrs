package com.constsmodels;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/** A small trash can, drawn rather than loaded, so it's sharp on any screen: what deletes a capture. */
final class TrashIcon implements Icon
{
    /** How wide and tall it is. */
    static final int SIZE = 12;

    private final Color color;

    TrashIcon(Color color)
    {
        this.color = color;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.translate(x, y);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // The lid and its handle
        g2.draw(new Line2D.Double(0.8, 2.6, 11.2, 2.6));
        Path2D.Double handle = new Path2D.Double();
        handle.moveTo(4.2, 2.6);
        handle.lineTo(4.6, 0.8);
        handle.lineTo(7.4, 0.8);
        handle.lineTo(7.8, 2.6);
        g2.draw(handle);

        // The can, a little narrower at the bottom, and its ribs
        Path2D.Double can = new Path2D.Double();
        can.moveTo(2.0, 4.0);
        can.lineTo(2.8, 11.4);
        can.lineTo(9.2, 11.4);
        can.lineTo(10.0, 4.0);
        g2.draw(can);
        g2.draw(new Line2D.Double(4.6, 5.4, 4.8, 9.8));
        g2.draw(new Line2D.Double(7.4, 5.4, 7.2, 9.8));
        g2.dispose();
    }

    @Override
    public int getIconWidth()
    {
        return SIZE;
    }

    @Override
    public int getIconHeight()
    {
        return SIZE;
    }
}
