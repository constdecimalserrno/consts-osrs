/*
 * Copyright (c) 2023, Jason O'Neill
 * Copyright (c) 2026, constdecimalserrno (consts-osrs changes: ported from Tree Despawn Timer,
 *   CustomTextComponent.java; setters instead of Lombok, the font sizes fixed at its defaults, always centered)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.constsafk;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.RenderableEntity;

/** A tree's timer as text, centered on the tree, with a dark shadow; a popular tree's is bold, shadowed all round. */
class TreeTimerText implements RenderableEntity
{
    private static final Font STANDARD_FONT =
        FontManager.getRunescapeSmallFont().deriveFont((float) ConstsAfkTreeTimerOverlay.SIZE_NORMAL);
    // The bold font is naturally much larger than the small one, so it's scaled down to keep them in line.
    private static final Font EMPHASIZED_FONT = FontManager.getRunescapeBoldFont()
        .deriveFont((float) Math.round(ConstsAfkTreeTimerOverlay.SIZE_POPULAR * 0.65f));

    private final String text;
    private final Point position;
    private Color color = Color.BLACK;
    private boolean emphasize;

    TreeTimerText(String text, Point position)
    {
        this.text = text;
        this.position = position;
    }

    void setColor(Color color)
    {
        this.color = color;
    }

    void setEmphasize(boolean emphasize)
    {
        this.emphasize = emphasize;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        Font originalFont = graphics.getFont();
        graphics.setFont(emphasize ? EMPHASIZED_FONT : STANDARD_FONT);

        final FontMetrics fontMetrics = graphics.getFontMetrics();
        int textWidth = fontMetrics.stringWidth(text);
        int textHeight = fontMetrics.getAscent();
        // Centered on the position, rounded the way Point rounds.
        Point at = new Point();
        at.setLocation(position.getX() - textWidth / 2d, position.getY() + textHeight / 2d);
        int x = at.x;
        int y = at.y;

        graphics.setColor(Color.BLACK);
        if (emphasize)
        {
            graphics.drawString(text, x, y - 1);
            graphics.drawString(text, x - 1, y);
        }
        graphics.drawString(text, x, y + 1);
        graphics.drawString(text, x + 1, y);

        graphics.setColor(color);
        graphics.drawString(text, x, y);

        if (originalFont != null)
        {
            graphics.setFont(originalFont);
        }

        return new Dimension(textWidth, textHeight);
    }
}
