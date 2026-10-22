package com.constsexp;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ImageUtil;

/**
 * Top-right panel, drawn as ticket 15's mockup A: the title with the preset as a tag, one row per enabled alert (its
 * icon, name and value), and the pause countdown. A quiet row stays dark, with a thin line under it that fills as a
 * timer gets close, or shows Health's and Prayer's level; a firing row lights up in its color and pulses.
 */
@Singleton
class ConstsExpStatusOverlay extends OverlayPanel
{
    private static final String TITLE = "consts-EXP";
    private static final Font FONT = FontManager.getRunescapeSmallFont();
    // Text is measured in the panel's own pixels, whatever the graphics' transform.
    private static final FontRenderContext FRC = new FontRenderContext(null, false, false);
    private static final Color INFO_COLOR = new Color(255, 210, 80);
    private static final Color LABEL_COLOR = new Color(215, 208, 196);
    private static final Color QUIET_COLOR = new Color(16, 13, 10, 153);
    private static final Color TAG_COLOR = new Color(0, 0, 0, 102);
    // A near-black alert color (Potion's default) would vanish on the dark panel: its row is drawn in this instead.
    private static final Color MUTED_INK = new Color(140, 130, 120);
    private static final Map<AlertType, BufferedImage> ICONS = new EnumMap<>(AlertType.class);
    private static final BufferedImage HOURGLASS = ImageUtil.loadImageResource(ConstsExpPlugin.class, "animating.png");
    // The icons, tinted, by mask and color. ponytail: never emptied, as it only grows when a color setting changes.
    private static final Map<Map.Entry<BufferedImage, Color>, BufferedImage> TINTED = new HashMap<>();
    private static final String HELD_TOOLTIP = "The hourglass: you're animating, so Idle waits until you stop.";
    private static final String AGGRO_AREA_UNKNOWN_TOOLTIP = "Aggro ?: the unaggressive area isn't known yet. Teleport or"
        + " enter a dungeon, then walk until the area settles and the ? goes away.</br>Or right-click the panel and Reset"
        + " Aggro Timer to start it where you stand.";
    private static final String AGGRO_AREA_SETTLING_TOOLTIP = "Aggro ?: only part of the unaggressive area is known."
        + " Walk out of the lines until the area settles and the ? goes away.";

    static
    {
        for (AlertType type : AlertType.values())
        {
            ICONS.put(type, ImageUtil.loadImageResource(ConstsExpPlugin.class,
                "alert_" + type.name().toLowerCase(Locale.ROOT) + ".png"));
        }
    }

    private final Client client;
    private final ConstsExpPlugin plugin;
    private final ConstsExpConfig config;
    private final TooltipManager tooltipManager;

    @Inject
    ConstsExpStatusOverlay(Client client, ConstsExpPlugin plugin, ConstsExpConfig config, TooltipManager tooltipManager)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.tooltipManager = tooltipManager;

        setPosition(OverlayPosition.TOP_RIGHT);
        setPriority(PRIORITY_HIGH);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        // Rows sit 1 px apart, as in the mockup.
        panelComponent.setGap(new Point(0, 1));

        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset Aggro Timer", TITLE, e -> plugin.resetAggroTimer());
        for (Preset preset : Preset.values())
        {
            addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Preset: " + preset, TITLE, e -> plugin.applyPreset(preset));
        }
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        // Master Switch off: no status panel, as if the plugin were off.
        if (!config.masterSwitch())
        {
            return null;
        }

        List<Row> rows = rows(System.currentTimeMillis());
        panelComponent.getChildren().clear();
        panelComponent.getChildren().addAll(rows);
        Dimension dimension = super.render(graphics);
        addHoverTooltip(rows);
        return dimension;
    }

    /** The panel's rows at {@code now}: the title, then each enabled alert, then the pause countdown. */
    List<Row> rows(long now)
    {
        List<Row> rows = new ArrayList<>();
        rows.add(new TagRow(TITLE, Color.WHITE, String.valueOf(plugin.currentPreset()),
            "Live AFK alert status, on the preset in the tag. Right-click to switch preset or reset the aggro timer."));

        if (client.getGameState() != GameState.LOGGED_IN)
        {
            rows.add(new TagRow("Status", LABEL_COLOR, "Not Logged In", "Log in to start alert tracking."));
            return rows;
        }

        for (AlertType type : AlertType.values())
        {
            if (plugin.isAlertEnabled(type))
            {
                rows.add(alertRow(type, now));
            }
        }

        long pauseRemainingMs = plugin.getPauseRemainingMillis();
        if (pauseRemainingMs > 0)
        {
            rows.add(new TagRow("Paused", LABEL_COLOR, formatClock(pauseRemainingMs),
                "Alerts are silenced briefly after each click or key press."));
        }
        return rows;
    }

    private AlertRow alertRow(AlertType type, long now)
    {
        boolean firing = plugin.isAlertFiring(type);
        String value = "";
        // How close a timer is to firing, or Health's and Prayer's level; one-off alerts have no line.
        double line = 0;
        switch (type)
        {
            case HEALTH:
            case PRAYER:
            {
                Skill skill = type == AlertType.HEALTH ? Skill.HITPOINTS : Skill.PRAYER;
                int level = client.getBoostedSkillLevel(skill);
                int max = Math.max(1, client.getRealSkillLevel(skill));
                value = level + "/" + max;
                line = level / (double) max;
                break;
            }
            case IDLE:
            {
                long idleMs = plugin.getIdleDurationMillis();
                value = formatClock(idleMs);
                line = idleMs / (double) Math.max(1000L, config.idleThresholdMs());
                break;
            }
            case AGGRO:
            {
                // Toward running out; with no countdown yet (the area unknown), no line.
                long dueMs = plugin.getNextAggroAlertMillis();
                long remainingMs = Math.max(0L, dueMs - now);
                boolean uncalibrated = isAggroAreaUncalibrated();
                value = aggroValueText(remainingMs, !uncalibrated, plugin.getAggroArea().hasArea());
                line = dueMs > 0 ? 1 - remainingMs / (double) Math.max(1L, plugin.getAggroIntervalMillis()) : 0;
                break;
            }
            case LOGOUT:
            {
                // The value counts down to the 25-minute logout; the line fills toward the warning.
                long remainingMs = plugin.getLogoutTimerRemainingMillis();
                value = formatClock(remainingMs);
                line = (ConstsExpPlugin.LOGOUT_TIMEOUT_MS - remainingMs) / (double) Math.max(60_000L, config.logoutWarningMs());
                break;
            }
            case FORESTRY:
            {
                // Names the event while it fires, e.g. "Forestry Leprechaun".
                ForestryEvent event = plugin.getLastForestryEvent();
                value = firing && event != null ? event.getLabel() : "";
                break;
            }
            default:
                break;
        }

        // Idle waits while you animate: an hourglass on its row says so, without the animation's number.
        boolean held = type == AlertType.IDLE && plugin.currentPreset().holdsIdleWhileAnimating(config)
            && plugin.getCurrentAnimationId() != -1;
        Color ink = ink(type.getFlashColor(config));
        return new AlertRow(ICONS.get(type), type.getLabel(), value, ink, firing ? pulse(type, ink, now) : null,
            Math.max(0, Math.min(1, line)), held, held ? tooltip(type) + "</br>" + HELD_TOOLTIP : tooltip(type));
    }

    private String tooltip(AlertType type)
    {
        if (type == AlertType.AGGRO && isAggroAreaUncalibrated())
        {
            return plugin.getAggroArea().hasArea() ? AGGRO_AREA_SETTLING_TOOLTIP : AGGRO_AREA_UNKNOWN_TOOLTIP;
        }
        return type.getDescription();
    }

    private boolean isAggroAreaUncalibrated()
    {
        return plugin.usesAggroArea() && !plugin.getAggroArea().isCalibrated();
    }

    /** The alert's color, dimmed to 45% on alternate flash phases. */
    private Color pulse(AlertType type, Color base, long now)
    {
        long interval = Math.max(80L, config.flashIntervalMs());
        return type.isFlashPhaseOn(now, interval) ? base : shade(base, 0.45);
    }

    // The rows' bounds are within the panel; the overlay's bounds say where the renderer put the panel.
    private void addHoverTooltip(List<Row> rows)
    {
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        if (mouse == null)
        {
            return;
        }

        for (Row row : rows)
        {
            Rectangle bounds = new Rectangle(row.getBounds());
            bounds.translate(getBounds().x, getBounds().y);
            if (bounds.contains(mouse.getX(), mouse.getY()))
            {
                tooltipManager.add(new Tooltip(row.tooltip));
                return;
            }
        }
    }

    /**
     * The Aggro row's value. Until both area centers are known the countdown may be off, so it wears a "?"; with no
     * area at all there is no countdown, only the "?".
     */
    static String aggroValueText(long remainingMs, boolean calibrated, boolean hasArea)
    {
        String clock = formatClock(remainingMs);
        if (calibrated)
        {
            return clock;
        }
        return hasArea ? "? " + clock : "?";
    }

    static String formatClock(long millis)
    {
        long totalSeconds = Math.max(0L, millis / 1000L);
        return totalSeconds / 60L + ":" + String.format("%02d", totalSeconds % 60L);
    }

    private static Color shade(Color color, double factor)
    {
        return new Color((int) Math.round(color.getRed() * factor), (int) Math.round(color.getGreen() * factor),
            (int) Math.round(color.getBlue() * factor));
    }

    // WCAG relative luminance.
    private static double luminance(Color color)
    {
        double luminance = 0;
        double[] weights = {0.2126, 0.7152, 0.0722};
        int[] channels = {color.getRed(), color.getGreen(), color.getBlue()};
        for (int i = 0; i < 3; i++)
        {
            double c = channels[i] / 255.0;
            luminance += weights[i] * (c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
        }
        return luminance;
    }

    /** The alert's color as the panel draws it. A near-black one (Potion's default) would vanish on the dark panel. */
    private static Color ink(Color color)
    {
        return luminance(color) < 0.01 ? MUTED_INK : color;
    }

    /** Black or white, whichever stands out more on the fill. */
    private static Color contrast(Color fill)
    {
        double luminance = luminance(fill);
        return (luminance + 0.05) / 0.05 >= 1.05 / (luminance + 0.05) ? Color.BLACK : Color.WHITE;
    }

    /** The pixels the text's ink covers when drawn at 0, 0. Like the mockup, rows line text up by its ink. */
    private static Rectangle textBounds(String text)
    {
        return FONT.createGlyphVector(FRC, text).getVisualBounds().getBounds();
    }

    /** Text as RuneLite's TextComponent draws it: a black shadow 1 px down and right, then the text. */
    private static void text(Graphics2D graphics, String text, int x, int y, Color color)
    {
        graphics.setFont(FONT);
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(color);
        graphics.drawString(text, x, y);
    }

    /** A pixel icon in one color, with the text's shadow. */
    private static void icon(Graphics2D graphics, BufferedImage mask, int x, int y, Color color)
    {
        graphics.drawImage(tinted(mask, Color.BLACK), x + 1, y + 1, null);
        graphics.drawImage(tinted(mask, color), x, y, null);
    }

    private static BufferedImage tinted(BufferedImage mask, Color color)
    {
        return TINTED.computeIfAbsent(Map.entry(mask, color), key -> ImageUtil.fillImage(mask, color));
    }

    /** A row of the panel. Its bounds, set as it renders, are for its tooltip. */
    abstract static class Row implements LayoutableRenderableEntity
    {
        final String tooltip;
        private final Rectangle bounds = new Rectangle();
        Point location = new Point();
        int width;

        Row(String tooltip)
        {
            this.tooltip = tooltip;
        }

        @Override
        public Rectangle getBounds()
        {
            return bounds;
        }

        @Override
        public void setPreferredLocation(Point location)
        {
            this.location = location;
        }

        @Override
        public void setPreferredSize(Dimension size)
        {
            width = size.width;
        }

        Dimension size(int height)
        {
            bounds.setBounds(location.x, location.y, width, height);
            return new Dimension(width, height);
        }
    }

    /** A name, and a value in a dark tag: the title with the preset, or a note like the pause countdown. */
    static final class TagRow extends Row
    {
        final String label;
        final Color labelColor;
        final String tag;

        TagRow(String label, Color labelColor, String tag, String tooltip)
        {
            super(tooltip);
            this.label = label;
            this.labelColor = labelColor;
            this.tag = tag;
        }

        @Override
        public Dimension render(Graphics2D graphics)
        {
            Rectangle labelInk = textBounds(label);
            Rectangle tagInk = textBounds(tag);
            // A tag too long to sit 3 px clear of the name (the Salvaging presets) gets its own line under it.
            int tagY = labelInk.width + 3 + tagInk.width + 4 <= width ? location.y : location.y + 13;
            int right = location.x + width;
            text(graphics, label, location.x - labelInk.x, location.y + 12, labelColor);
            graphics.setColor(TAG_COLOR);
            graphics.fillRect(right - tagInk.width - 4, tagY, tagInk.width + 4, 12);
            text(graphics, tag, right - 2 - tagInk.x - tagInk.width, tagY + 12, INFO_COLOR);
            return size(tagY - location.y + 14);
        }
    }

    /** An alert: its icon, name and value, and under a quiet row a thin line. */
    static final class AlertRow extends Row
    {
        private static final int HEIGHT = 15;

        final BufferedImage icon;
        final String label;
        final String value;
        // The alert's color, as the panel draws it.
        final Color ink;
        // Its color in this flash phase while it fires; null while it's quiet.
        final Color fill;
        // How far the thin line under a quiet row reaches, 0 to 1.
        final double line;
        // Idle held by an animation.
        final boolean held;

        AlertRow(BufferedImage icon, String label, String value, Color ink, Color fill, double line, boolean held,
            String tooltip)
        {
            super(tooltip);
            this.icon = icon;
            this.label = label;
            this.value = value;
            this.ink = ink;
            this.fill = fill;
            this.line = line;
            this.held = held;
        }

        @Override
        public Dimension render(Graphics2D graphics)
        {
            int x = location.x;
            int y = location.y;
            Color foreground = fill != null ? contrast(fill) : null;

            graphics.setColor(fill != null ? fill : QUIET_COLOR);
            graphics.fillRect(x, y, width, HEIGHT);
            if (fill == null)
            {
                graphics.setColor(shade(ink, 0.85));
                graphics.fillRect(x, y + HEIGHT - 1, (int) Math.round(width * line), 1);
            }

            Color iconColor = foreground != null ? foreground : shade(ink, 0.9);
            icon(graphics, icon, x + 2, y + 3, iconColor);
            text(graphics, label, x + 14 - textBounds(label).x, y + 14, foreground != null ? foreground : LABEL_COLOR);
            Rectangle valueInk = textBounds(value);
            int valueX = x + width - 2 - valueInk.x - valueInk.width;
            text(graphics, value, valueX, y + 14, foreground != null ? foreground : Color.WHITE);
            if (held)
            {
                // Left of the value, 3 px clear of its ink.
                icon(graphics, HOURGLASS, valueX + valueInk.x - 3 - HOURGLASS.getWidth(), y + 3, iconColor);
            }
            return size(HEIGHT);
        }
    }
}
