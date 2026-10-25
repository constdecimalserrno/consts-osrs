package com.constsexp;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
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
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;

/**
 * Top-right panel of refined bars: the title with the preset as a tag, one row per enabled alert (its icon, name and
 * value), XP per hour for the skill you're training, the tick pendulum when it's on, and the pause countdown. A quiet row stays dark, with a thin line under it
 * that fills as a timer gets close, or shows Health's and Prayer's level; a firing row lights up in its color, and
 * pulses with the screen flash. Text is RuneScape's small font whatever RuneLite's interface font is: the 129 px rows
 * are laid out for it.
 */
@Singleton
class ConstsExpStatusOverlay extends OverlayPanel
{
    private static final String TITLE = "consts-EXP";
    private static final Font FONT = FontManager.getRunescapeSmallFont();
    // Text is measured in the panel's own pixels: the same at any graphics scale, such as a 2x render.
    private static final FontRenderContext FRC = new FontRenderContext(null, false, false);
    private static final Color INFO_COLOR = new Color(255, 210, 80);
    private static final Color LABEL_COLOR = new Color(215, 208, 196);
    private static final Color QUIET_COLOR = new Color(16, 13, 10, 153);
    private static final Color TAG_COLOR = new Color(0, 0, 0, 102);
    // A quiet row is lightest over a white scene, where an alert's color shows least. A color with less than
    // 2.75:1 against it there (of the defaults, only Potion's black) is lightened toward white until it has that.
    private static final Color QUIET_ON_WHITE = new Color(66, 62, 58);
    private static final double MIN_CONTRAST = 2.75;
    private static final double INK_FLOOR = (luminance(QUIET_ON_WHITE) + 0.05) * MIN_CONTRAST - 0.05;
    private static final Map<AlertType, BufferedImage> ICONS = new EnumMap<>(AlertType.class);
    static final BufferedImage HOURGLASS = ImageUtil.loadImageResource(ConstsExpPlugin.class, "animating.png");
    // The icons, tinted, by mask and color. ponytail: never emptied, as it only grows when a color setting changes.
    private static final Map<Map.Entry<BufferedImage, Color>, BufferedImage> TINTED = new HashMap<>();
    private static final String HELD_TOOLTIP = "The hourglass: you're animating, so Idle waits until you stop.";
    private static final String AGGRO_AREA_UNKNOWN_TOOLTIP = "Aggro ?: the unaggressive area isn't known yet. Teleport or"
        + " enter a dungeon, then walk until the area settles and the ? goes away.</br>Or Shift + right-click the panel"
        + " and Reset Aggro Timer to start it where you stand.";
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
    private final SkillIconManager skillIconManager;
    // Each skill's icon, cut down to the row.
    private final Map<Skill, BufferedImage> skillIcons = new EnumMap<>(Skill.class);

    @Inject
    ConstsExpStatusOverlay(Client client, ConstsExpPlugin plugin, ConstsExpConfig config, TooltipManager tooltipManager,
        SkillIconManager skillIconManager)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.tooltipManager = tooltipManager;
        this.skillIconManager = skillIconManager;

        setPosition(OverlayPosition.TOP_RIGHT);
        setPriority(PRIORITY_HIGH);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        // Rows sit 1 px apart.
        panelComponent.setGap(new Point(0, 1));
        // RuneLite's resize drag stops at the 129 px the rows are laid out for (see setPreferredSize).
        setMinimumSize(ComponentConstants.STANDARD_WIDTH);

        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset Aggro Timer", TITLE, e -> plugin.resetAggroTimer());
        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset XP/h", TITLE, e -> plugin.getXpRates().resetAll());
        for (Preset preset : Preset.values())
        {
            addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Preset: " + preset, TITLE, e -> plugin.applyPreset(preset));
        }
    }

    /**
     * Never narrower than the standard 129 px the rows are laid out for. The drag already stops there (the minimum
     * size); this catches a narrower width saved by an older version. Wider is fine.
     */
    @Override
    public void setPreferredSize(Dimension size)
    {
        super.setPreferredSize(size == null ? null
            : new Dimension(Math.max(ComponentConstants.STANDARD_WIDTH, size.width), size.height));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        // Master Switch off: no status panel, as if the plugin were off.
        if (!config.masterSwitch())
        {
            return null;
        }

        // OverlayPanel clears the children again after each frame.
        List<Row> rows = rows(System.currentTimeMillis());
        panelComponent.getChildren().addAll(rows);
        Dimension dimension = super.render(graphics);
        addHoverTooltip(rows);
        return dimension;
    }

    /**
     * The panel's rows at {@code now}: the title, each enabled alert, XP per hour, the tick pendulum, then the pause
     * countdown.
     */
    List<Row> rows(long now)
    {
        List<Row> rows = new ArrayList<>();
        rows.add(new TagRow(TITLE, Color.WHITE, String.valueOf(plugin.currentPreset()),
            "Live AFK alert status, on the preset in the tag. Shift + right-click to switch preset or reset the"
                + " aggro timer."));

        for (AlertType type : AlertType.values())
        {
            if (plugin.isAlertEnabled(type))
            {
                rows.add(alertRow(type, now));
            }
        }

        Skill training = plugin.getXpRates().shown();
        if (config.xpPerHour() && training != null)
        {
            rows.add(xpRow(training, now));
        }

        // Above the pause countdown, so the pause row a click brings never moves it.
        if (config.tickPendulum())
        {
            rows.add(new PendulumRow(plugin.getPendulum(), now));
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
                line = idleMs / (double) plugin.getIdleThresholdMillis();
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
                value = formatClock(plugin.getLogoutTimerRemainingMillis());
                line = plugin.getMillisSinceUserInteraction() / (double) plugin.getLogoutWarningMillis();
                break;
            }
            case FORESTRY:
            {
                // Names the event while it fires: Leprechaun, Ent.
                ForestryEvent event = plugin.getLastForestryEvent();
                value = firing && event != null ? event.getLabel() : "";
                break;
            }
            default:
                break;
        }

        // Idle waits while you animate: an hourglass on its row says so, without the animation's number.
        boolean held = type == AlertType.IDLE && plugin.isIdleHeldByAnimation();
        Color ink = ink(type.getFlashColor(config));
        Color fill = null;
        if (firing)
        {
            // Lit, and in step with the screen flash while it flashes: dimmed to 45% in its dark half.
            boolean dark = plugin.isFlashingScreen(type, now) && !plugin.isFlashPhaseOn(type, now);
            fill = dark ? ColorUtil.colorLerp(Color.BLACK, ink, 0.45) : ink;
        }
        return new AlertRow(ICONS.get(type), type.getLabel(), value, ink, fill, line > 0 ? Math.min(1, line) : 0, held,
            held ? tooltip(type) + "</br>" + HELD_TOOLTIP : tooltip(type));
    }

    /** XP per hour for the skill you're training, in game yellow, with its own icon. */
    private XpRow xpRow(Skill skill, long now)
    {
        XpRates rates = plugin.getXpRates();
        BufferedImage icon = skillIcons.computeIfAbsent(skill,
            s -> ImageUtil.resizeImage(skillIconManager.getSkillImage(s, true), XpRow.ICON, XpRow.ICON));
        return new XpRow(icon, skill.getName(), XpRates.format(rates.perHour(skill, now)) + "/h",
            "XP/h for " + skill.getName() + ", the last skill to gain XP: "
                + String.format(Locale.ROOT, "%,d", rates.gained(skill)) + " XP in "
                + formatClock(rates.elapsed(skill, now)) + ".</br>Shift + right-click and Reset XP/h after a break.");
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
        return totalSeconds / 60L + ":" + String.format(Locale.ROOT, "%02d", totalSeconds % 60L);
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

    /** The alert's color as the panel draws it: a dark one, like Potion's black, lightened until it shows. */
    private static Color ink(Color color)
    {
        if (luminance(color) >= INK_FLOOR)
        {
            return color;
        }
        // The least lightening that clears the floor, in 255ths of the way to white.
        int low = 0;
        int high = 255;
        while (low < high)
        {
            int mid = (low + high) / 2;
            if (luminance(ColorUtil.colorLerp(color, Color.WHITE, mid / 255.0)) >= INK_FLOOR)
            {
                high = mid;
            }
            else
            {
                low = mid + 1;
            }
        }
        return ColorUtil.colorLerp(color, Color.WHITE, low / 255.0);
    }

    /** Black or white, whichever stands out more on the fill. */
    private static Color contrast(Color fill)
    {
        double luminance = luminance(fill);
        return (luminance + 0.05) / 0.05 >= 1.05 / (luminance + 0.05) ? Color.BLACK : Color.WHITE;
    }

    /** The pixels the text's ink covers when drawn at 0, 0. Rows line text up by its ink, not its advance. */
    private static Rectangle textBounds(String text)
    {
        return FONT.createGlyphVector(FRC, text).getVisualBounds().getBounds();
    }

    /**
     * Text as RuneLite's TextComponent draws it, a black shadow 1 px down and right, then the text. Black text on a
     * light fill gets no shadow: it would only smear the strokes.
     */
    private static void text(Graphics2D graphics, String text, int x, int y, Color color)
    {
        graphics.setFont(FONT);
        if (!Color.BLACK.equals(color))
        {
            graphics.setColor(Color.BLACK);
            graphics.drawString(text, x + 1, y + 1);
        }
        graphics.setColor(color);
        graphics.drawString(text, x, y);
    }

    /** A pixel icon in one color, with a shadow like the text's. */
    private static void icon(Graphics2D graphics, BufferedImage mask, int x, int y, Color color)
    {
        if (!Color.BLACK.equals(color))
        {
            graphics.drawImage(tinted(mask, Color.BLACK), x + 1, y + 1, null);
        }
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
            // The full 14 px row, so the box covers descenders (Woodcutting's g) and their shadow.
            graphics.setColor(TAG_COLOR);
            graphics.fillRect(right - tagInk.width - 4, tagY, tagInk.width + 4, 14);
            text(graphics, tag, right - 2 - tagInk.x - tagInk.width, tagY + 12, INFO_COLOR);
            return size(tagY - location.y + 14);
        }
    }

    /** An alert: its icon, name and value, and under a quiet row a thin line. */
    static class AlertRow extends Row
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
                graphics.setColor(ColorUtil.colorLerp(Color.BLACK, ink, 0.85));
                // Floored: full only once the alert is due.
                graphics.fillRect(x, y + HEIGHT - 1, (int) (width * line), 1);
            }

            Color iconColor = foreground != null ? foreground : ColorUtil.colorLerp(Color.BLACK, ink, 0.9);
            if (icon != null)
            {
                icon(graphics, icon, x + 2, y + 3, iconColor);
            }
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

    /** XP per hour: a quiet alert row with no line, and the skill's own icon in full color in place of a mask. */
    static final class XpRow extends AlertRow
    {
        static final int ICON = 11;

        private final BufferedImage skillIcon;

        XpRow(BufferedImage skillIcon, String label, String value, String tooltip)
        {
            super(null, label, value, INFO_COLOR, null, 0, false, tooltip);
            this.skillIcon = skillIcon;
        }

        @Override
        public Dimension render(Graphics2D graphics)
        {
            Dimension size = super.render(graphics);
            graphics.drawImage(skillIcon, location.x + 1, location.y + 2, null);
            return size;
        }
    }

    /**
     * The tick pendulum: the count of ticks in a row on a chip at the left, the track, and the
     * ping on a chip at the right, across the row's full width. The ball crosses the track once a swing, one swing a
     * tick; the space between the two orange lines glows while it's time to click, and each click washes the track
     * green (on pace) or red (not).
     */
    static final class PendulumRow extends Row
    {
        static final Color BALL = new Color(0x3cc8ff);
        private static final Color BAR = new Color(30, 33, 40, 230);
        private static final Color CAP = new Color(0x8c8c8c);
        private static final Color LINE = new Color(0xff8c1a);
        private static final Color FLASH = new Color(255, 247, 168, 204);
        private static final Color DIM = new Color(0x9a9a9a);
        private static final Color RED = new Color(0xff3a3a);
        private static final Color YELLOW = new Color(0xffff00);
        private static final Color GOOD = new Color(0x4bcd5a);
        private static final Color PERFECT = new Color(0xa6ff8f);
        private static final Color ORANGE = new Color(0xff981f);
        private static final Map<TickPendulum.Verdict, Color> WASH = new EnumMap<>(Map.of(
            TickPendulum.Verdict.PERFECT, PERFECT, TickPendulum.Verdict.GOOD, GOOD,
            TickPendulum.Verdict.HALF, new Color(0xc8c8c8), TickPendulum.Verdict.BAD, new Color(0xd25f5f)));
        private static final int HEIGHT = 12;
        private static final int COUNT_WIDTH = 23;
        private static final int PING_WIDTH = 30;
        private static final int GAP = 2;
        private static final int TRACK_HEIGHT = 8;
        private static final int DOT = 6;
        // How long each cue lasts, in ms: a click's wash, the count's hop and its red drop, the ping dot's swell.
        private static final int WASH_MS = 200;
        private static final int HOP_MS = 120;
        private static final int DROP_MS = 300;
        private static final int SWELL_MS = 250;
        private static final Rectangle DIGIT = textBounds("0");
        private static final String TOOLTIP = "Tick pendulum: click while the ball is between the two lines; a click"
            + " there is perfect.</br>Left: your ticks in a row. Right: your ping, with ~ while it's learned from your"
            + " prayer and run toggles.";

        private final TickPendulum pendulum;
        private final long now;

        PendulumRow(TickPendulum pendulum, long now)
        {
            super(TOOLTIP);
            this.pendulum = pendulum;
            this.now = now;
        }

        @Override
        public Dimension render(Graphics2D graphics)
        {
            pendulum.advance(now);
            int x = location.x;
            int top = location.y + 3;
            int x0 = x + COUNT_WIDTH + GAP;
            int length = width - COUNT_WIDTH - PING_WIDTH - 2 * GAP;
            int travel = length - DOT;
            int mid = x0 + length / 2;
            TickPendulum.Swing swing = pendulum.swing(now);
            boolean toRight = TickPendulum.toRight(swing.n);

            fill(graphics, BAR, x0, top, length, TRACK_HEIGHT);
            long washAge = now - pendulum.washAt();
            if (washAge >= 0 && washAge < WASH_MS)
            {
                // Faint for a flick's first click, bright when perfect.
                TickPendulum.Verdict wash = pendulum.wash();
                double alpha = wash == TickPendulum.Verdict.HALF ? 0.35
                    : wash == TickPendulum.Verdict.PERFECT ? 0.85 : 0.6;
                fill(graphics, alpha(WASH.get(wash), alpha * (1 - washAge / (double) WASH_MS)), x0, top, length,
                    TRACK_HEIGHT);
            }
            fill(graphics, CAP, x0, top, 1, TRACK_HEIGHT);
            fill(graphics, CAP, x0 + length - 1, top, 1, TRACK_HEIGHT);

            // Every other swing: the off swing is dimmed, its lines and the ball.
            boolean wanted = pendulum.clickSwing(swing.n);
            Composite composite = graphics.getComposite();
            if (!wanted)
            {
                graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            }
            // The zone's lines sit where the ball's center is as the zone starts and ends, never closer in than the
            // ball plus 2 px a side.
            int a = Math.min(ballX(TickPendulum.TICK / 2 - pendulum.zoneMs() / 2, true, x0, travel) + DOT / 2 - 1,
                mid - 1 - (DOT + 4) / 2);
            int b = 2 * mid - 1 - a;
            if (wanted && pendulum.inZone(swing.el))
            {
                fill(graphics, FLASH, a + 1, top, b - a - 1, TRACK_HEIGHT);
            }
            fill(graphics, LINE, a, top - 1, 1, TRACK_HEIGHT + 2);
            fill(graphics, LINE, b, top - 1, 1, TRACK_HEIGHT + 2);
            int ball = ballX(swing.el, toRight, x0, travel);
            fill(graphics, BALL, ball + 1, top + 1, DOT - 2, 1);
            fill(graphics, BALL, ball, top + 2, DOT, TRACK_HEIGHT - 4);
            fill(graphics, BALL, ball + 1, top + TRACK_HEIGHT - 2, DOT - 2, 1);
            graphics.setComposite(composite);

            int baseline = top - 1 + (TRACK_HEIGHT + 2 - DIGIT.height) / 2 - DIGIT.y;
            drawCount(graphics, x, top, x0 - 3, baseline);
            drawPing(graphics, x0 + length + GAP, x + width, top, baseline);
            return size(HEIGHT);
        }

        /** Right-aligned against the track: a dim 0 until a click counts, a hop as it goes up, red as it drops. */
        private void drawCount(Graphics2D graphics, int x, int top, int right, int baseline)
        {
            int run = pendulum.run();
            Color color = run > 0 ? Color.WHITE : DIM;
            int lift = 0;
            if (run > 0 && now - pendulum.upAt() < HOP_MS)
            {
                color = YELLOW;
                lift = 1;
            }
            else if (run == 0 && now - pendulum.dropAt() < DROP_MS)
            {
                color = RED;
            }
            fill(graphics, BAR, x, top - 1, right + 1 - x, TRACK_HEIGHT + 2);
            String count = String.valueOf(run);
            Rectangle ink = textBounds(count);
            text(graphics, count, right - ink.x - ink.width, baseline - lift, color);
            if (now - pendulum.pipAt() < TickPendulum.TICK)
            {
                // The perfect pip, for a tick.
                fill(graphics, PERFECT, right, top - 2, 2, 2);
            }
        }

        /**
         * The ping, "~" while it's learned, and a dot on the corner colored like World Hopper's ping (green under 80
         * ms, yellow to 150, orange above) that swells for a moment as the learner corrects it.
         */
        private void drawPing(Graphics2D graphics, int x, int right, int top, int baseline)
        {
            int ping = pendulum.ping();
            fill(graphics, BAR, x, top - 1, right - x, TRACK_HEIGHT + 2);
            text(graphics, (pendulum.learned() ? "~" : "") + ping, x + 2, baseline, Color.WHITE);
            Color dot = ping < 80 ? GOOD : ping <= 150 ? YELLOW : ORANGE;
            if (pendulum.learned() && now - pendulum.fixAt() < SWELL_MS)
            {
                fill(graphics, dot, x - 2, top - 3, 3, 3);
            }
            else
            {
                fill(graphics, dot, x - 1, top - 2, 2, 2);
            }
        }

        /** The ball's left edge {@code ms} into a swing; both directions round the same way, so marks mirror. */
        private static int ballX(double ms, boolean toRight, int x0, int travel)
        {
            int d = (int) Math.round(ms / TickPendulum.TICK * travel);
            return toRight ? x0 + d : x0 + travel - d;
        }

        private static void fill(Graphics2D graphics, Color color, int x, int y, int w, int h)
        {
            graphics.setColor(color);
            graphics.fillRect(x, y, w, h);
        }

        private static Color alpha(Color color, double alpha)
        {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) Math.round(255 * alpha));
        }
    }
}
