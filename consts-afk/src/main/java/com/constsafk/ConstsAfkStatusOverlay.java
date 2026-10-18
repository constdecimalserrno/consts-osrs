package com.constsafk;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.http.api.worlds.World;

/** Top-right panel: the active preset, one bar per enabled alert, and the interaction pause countdown. */
@Singleton
class ConstsAfkStatusOverlay extends OverlayPanel
{
    private static final String TITLE = "consts-AFK";
    private static final int METRIC_WIDTH = 190;
    private static final Color ON_COLOR = new Color(75, 205, 90);
    private static final Color OFF_COLOR = new Color(210, 95, 95);
    private static final Color INFO_COLOR = new Color(255, 210, 80);
    private static final Color BAR_BACKGROUND = new Color(30, 33, 40, 230);

    private final Client client;
    private final ConstsAfkPlugin plugin;
    private final ConstsAfkConfig config;
    private final TooltipManager tooltipManager;

    @Inject
    private ConstsAfkStatusOverlay(Client client, ConstsAfkPlugin plugin, ConstsAfkConfig config, TooltipManager tooltipManager)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.tooltipManager = tooltipManager;

        setPosition(OverlayPosition.TOP_RIGHT);
        setPriority(PRIORITY_HIGH);
        setLayer(OverlayLayer.ABOVE_WIDGETS);

        addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset Aggro Timer", TITLE, e -> plugin.resetAggroTimer());
        for (Preset preset : Preset.values())
        {
            addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Preset: " + preset, TITLE, e -> plugin.applyPreset(preset));
        }
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        panelComponent.getChildren().clear();
        List<HoverTarget> hoverTargets = new ArrayList<>();

        add(hoverTargets, TitleComponent.builder().text(TITLE).color(Color.WHITE).build(),
            "Live AFK alert status. Right-click to switch preset or reset the aggro timer.");

        if (client.getGameState() != GameState.LOGGED_IN)
        {
            add(hoverTargets, line("Status", "Not Logged In", INFO_COLOR), "Log in to start alert tracking.");
            return renderWithHoverTooltips(graphics, hoverTargets);
        }

        add(hoverTargets, line("Preset", String.valueOf(plugin.currentPreset()), INFO_COLOR),
            "Which alerts are on. Right-click the panel to switch.");

        List<World> worlds = plugin.getSuggestedWorlds();
        if (!worlds.isEmpty())
        {
            add(hoverTargets,
                line("Worlds", worlds.stream().map(w -> String.valueOf(w.getId())).collect(Collectors.joining(", ")), INFO_COLOR),
                WorldSuggestions.describe(plugin.currentPreset()) + worlds.stream()
                    .map(w -> "</br>World " + w.getId() + ": " + String.format("%,d", w.getPlayers()) + " players")
                    .collect(Collectors.joining()));
        }

        for (AlertType type : AlertType.values())
        {
            if (plugin.isAlertEnabled(type))
            {
                add(hoverTargets, alertBar(type), type.getDescription());
            }
        }

        long pauseRemainingMs = plugin.getPauseRemainingMillis();
        if (pauseRemainingMs > 0)
        {
            add(hoverTargets, line("Paused", formatClock(pauseRemainingMs), INFO_COLOR),
                "Alerts are silenced briefly after each click or key press.");
        }

        // Whenever Idle waits for the animation to stop, show the animation, so a quiet Idle explains itself.
        boolean holdingIdle = plugin.isAlertEnabled(AlertType.IDLE)
            && plugin.currentPreset().holdsIdleWhileAnimating(config);
        if (config.showAnimationId() || holdingIdle)
        {
            int animationId = plugin.getCurrentAnimationId();
            boolean animating = animationId != -1;
            add(hoverTargets, line("Animation", animating ? String.valueOf(animationId) : "None", animating ? ON_COLOR : OFF_COLOR),
                holdingIdle
                    ? "Your current animation. Idle waits until it stops, so it doesn't ding while you're busy."
                    : "Current local player animation id.");
        }

        return renderWithHoverTooltips(graphics, hoverTargets);
    }

    private ProgressBarComponent alertBar(AlertType type)
    {
        switch (type)
        {
            case HEALTH:
                return skillBar(type, Skill.HITPOINTS);
            case PRAYER:
                return skillBar(type, Skill.PRAYER);
            case IDLE:
            {
                long idleMs = plugin.getIdleDurationMillis();
                long thresholdMs = Math.max(1000L, config.idleThresholdMs());
                return bar(type, formatClock(idleMs), Math.min(idleMs, thresholdMs), thresholdMs);
            }
            case AGGRO:
            {
                long remainingMs = Math.max(0L, plugin.getNextAggroAlertMillis() - System.currentTimeMillis());
                return bar(type, formatClock(remainingMs), remainingMs, plugin.getAggroIntervalMillis());
            }
            case LOGOUT:
            {
                long remainingMs = plugin.getLogoutTimerRemainingMillis();
                return bar(type, formatClock(remainingMs), remainingMs, ConstsAfkPlugin.LOGOUT_TIMEOUT_MS);
            }
            case FORESTRY:
            {
                // Names the event while it fires, e.g. "Forestry Leprechaun".
                boolean firing = plugin.isAlertFiring(type);
                ForestryEvent event = plugin.getLastForestryEvent();
                return bar(type, firing && event != null ? event.getLabel() : "0/1", firing ? 1L : 0L, 1L);
            }
            default:
            {
                // One-off alerts (chat or NPC triggered) fill up while they are firing.
                boolean firing = plugin.isAlertFiring(type);
                return bar(type, firing ? "1/1" : "0/1", firing ? 1L : 0L, 1L);
            }
        }
    }

    private ProgressBarComponent skillBar(AlertType type, Skill skill)
    {
        int current = client.getBoostedSkillLevel(skill);
        int max = Math.max(1, client.getRealSkillLevel(skill));
        return bar(type, current + "/" + max, current, max);
    }

    private ProgressBarComponent bar(AlertType type, String valueText, long value, long max)
    {
        long safeMax = Math.max(1L, max);
        long clampedValue = Math.max(0L, Math.min(value, safeMax));
        Color color = pulse(type);
        // The label is centered, so it sits on the fill once the bar is at least half full.
        boolean labelOnFill = clampedValue * 2 >= safeMax;

        ProgressBarComponent bar = new ProgressBarComponent();
        bar.setMinimum(0L);
        bar.setMaximum(safeMax);
        bar.setValue(clampedValue);
        bar.setForegroundColor(color);
        bar.setBackgroundColor(BAR_BACKGROUND);
        bar.setFontColor(labelOnFill && isLight(color) ? Color.BLACK : Color.WHITE);
        bar.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
        bar.setCenterLabel(type.getLabel() + " " + valueText);
        bar.setPreferredSize(new Dimension(METRIC_WIDTH, 16));
        return bar;
    }

    /** The alert's color, dimmed on alternate flash phases while it is firing. */
    private Color pulse(AlertType type)
    {
        Color base = type.getFlashColor(config);
        long interval = Math.max(80L, config.flashIntervalMs());
        if (!plugin.isAlertFiring(type) || type.isFlashPhaseOn(System.currentTimeMillis(), interval))
        {
            return base;
        }

        return new Color((int) (base.getRed() * 0.45f), (int) (base.getGreen() * 0.45f), (int) (base.getBlue() * 0.45f));
    }

    private static boolean isLight(Color color)
    {
        return 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue() > 186;
    }

    private static LineComponent line(String left, String right, Color rightColor)
    {
        return LineComponent.builder()
            .left(left)
            .right(right)
            .rightColor(rightColor)
            .build();
    }

    private void add(List<HoverTarget> hoverTargets, LayoutableRenderableEntity component, String tooltip)
    {
        panelComponent.getChildren().add(component);
        hoverTargets.add(new HoverTarget(component, tooltip));
    }

    private Dimension renderWithHoverTooltips(Graphics2D graphics, List<HoverTarget> hoverTargets)
    {
        Dimension dimension = super.render(graphics);
        maybeAddHoverTooltip(hoverTargets);
        return dimension;
    }

    private void maybeAddHoverTooltip(List<HoverTarget> hoverTargets)
    {
        net.runelite.api.Point mousePosition = client.getMouseCanvasPosition();
        if (mousePosition == null)
        {
            return;
        }

        for (HoverTarget target : hoverTargets)
        {
            if (isMouseInsideComponent(target.component.getBounds(), mousePosition.getX(), mousePosition.getY()))
            {
                tooltipManager.add(new Tooltip(target.tooltip));
                return;
            }
        }
    }

    private boolean isMouseInsideComponent(Rectangle componentBounds, int mouseX, int mouseY)
    {
        if (componentBounds == null)
        {
            return false;
        }

        if (componentBounds.contains(mouseX, mouseY))
        {
            return true;
        }

        // Child bounds may be relative to the panel rather than the canvas.
        Rectangle panelBounds = panelComponent.getBounds();
        if (panelBounds == null)
        {
            return false;
        }

        Rectangle translated = new Rectangle(componentBounds);
        translated.translate(panelBounds.x, panelBounds.y);
        return translated.contains(mouseX, mouseY);
    }

    static String formatClock(long millis)
    {
        long totalSeconds = Math.max(0L, millis / 1000L);
        return totalSeconds / 60L + ":" + String.format("%02d", totalSeconds % 60L);
    }

    private static final class HoverTarget
    {
        private final LayoutableRenderableEntity component;
        private final String tooltip;

        private HoverTarget(LayoutableRenderableEntity component, String tooltip)
        {
            this.component = component;
            this.tooltip = tooltip;
        }
    }
}
