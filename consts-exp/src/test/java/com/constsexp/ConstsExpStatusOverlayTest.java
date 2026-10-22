package com.constsexp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ImageUtil;
import org.junit.Before;
import org.junit.Test;

public class ConstsExpStatusOverlayTest
{
    // A whole number of 600 ms flash cycles: Inv Full is in its bright half here and dim 300 ms later.
    private static final long NOW = 1_800_000_000_000L;

    private Client client;
    private ConstsExpPlugin plugin;
    private ConstsExpConfig config;
    private TooltipManager tooltips;
    private ConstsExpStatusOverlay overlay;

    @Before
    public void setUp()
    {
        client = mock(Client.class);
        plugin = mock(ConstsExpPlugin.class);
        // Every setting at its default.
        config = mock(ConstsExpConfig.class, CALLS_REAL_METHODS);
        tooltips = mock(TooltipManager.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(plugin.currentPreset()).thenReturn(Preset.WOODCUTTING);
        when(plugin.getCurrentAnimationId()).thenReturn(-1);
        when(plugin.getAggroArea()).thenReturn(new AggroArea());
        overlay = new ConstsExpStatusOverlay(client, plugin, config, tooltips);
    }

    @Test
    public void titleRowShouldShowThePresetAsATag()
    {
        ConstsExpStatusOverlay.TagRow title = (ConstsExpStatusOverlay.TagRow) overlay.rows(NOW).get(0);

        assertEquals("consts-EXP", title.label);
        assertEquals("Woodcutting", title.tag);
    }

    @Test
    public void aPresetTooLongForTheTitleRowShouldGetALineOfItsOwn()
    {
        Graphics2D graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        ConstsExpStatusOverlay.Row title = overlay.rows(NOW).get(0);
        title.setPreferredSize(new java.awt.Dimension(121, 0));
        assertEquals("Woodcutting fits beside the title", 14, title.render(graphics).height);

        when(plugin.currentPreset()).thenReturn(Preset.SALVAGING_ACTIVE);
        title = overlay.rows(NOW).get(0);
        title.setPreferredSize(new java.awt.Dimension(121, 0));
        assertEquals("Salvaging Active goes under it", 27, title.render(graphics).height);
    }

    @Test
    public void eachAlertRowShouldShowItsIconNameAndValue()
    {
        enable(AlertType.IDLE, AlertType.INVENTORY_FULL, AlertType.FORESTRY);
        when(plugin.getIdleDurationMillis()).thenReturn(3_000L);

        assertEquals(List.of("Idle", "Inv Full", "Forestry"),
            alertRows().stream().map(row -> row.label).collect(Collectors.toList()));
        assertRow(alertRow("Idle"), "alert_idle.png", "0:03");
        assertRow(alertRow("Inv Full"), "alert_inventory_full.png", "");
        assertRow(alertRow("Forestry"), "alert_forestry.png", "");
    }

    @Test
    public void everyAlertShouldHaveItsOwnIcon()
    {
        enable(AlertType.values());

        List<ConstsExpStatusOverlay.AlertRow> rows = alertRows();
        assertEquals(AlertType.values().length, rows.size());
        for (ConstsExpStatusOverlay.AlertRow row : rows)
        {
            for (ConstsExpStatusOverlay.AlertRow other : rows)
            {
                if (other != row)
                {
                    assertFalse(row.label + " and " + other.label + " share an icon", samePixels(row.icon, other.icon));
                }
            }
        }
    }

    @Test
    public void aTimersLineShouldFillAsItGetsClose()
    {
        enable(AlertType.IDLE, AlertType.AGGRO, AlertType.LOGOUT);
        // Idle at half its 6 s threshold.
        when(plugin.getIdleDurationMillis()).thenReturn(3_000L);
        // Aggro with 2:30 of its 10 minutes left.
        when(plugin.getNextAggroAlertMillis()).thenReturn(NOW + 150_000L);
        when(plugin.getAggroIntervalMillis()).thenReturn(600_000L);
        // Logout 12 minutes after the last click: 13:00 to the logout, halfway to the 24-minute warning.
        when(plugin.getLogoutTimerRemainingMillis()).thenReturn(780_000L);

        assertEquals(0.5, alertRow("Idle").line, 1e-6);
        assertEquals("2:30", alertRow("Aggro").value);
        assertEquals(0.75, alertRow("Aggro").line, 1e-6);
        assertEquals("13:00", alertRow("Logout").value);
        assertEquals(0.5, alertRow("Logout").line, 1e-6);
    }

    @Test
    public void healthAndPrayerShouldShowTheirLevel()
    {
        enable(AlertType.HEALTH, AlertType.PRAYER);
        when(client.getBoostedSkillLevel(Skill.HITPOINTS)).thenReturn(21);
        when(client.getRealSkillLevel(Skill.HITPOINTS)).thenReturn(99);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(61);
        when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(77);

        assertEquals("21/99", alertRow("Health").value);
        assertEquals(21 / 99.0, alertRow("Health").line, 1e-6);
        assertEquals("61/77", alertRow("Prayer").value);
        assertEquals(61 / 77.0, alertRow("Prayer").line, 1e-6);
    }

    @Test
    public void aFiringRowShouldFillWithItsColorAndPulse()
    {
        enable(AlertType.IDLE, AlertType.INVENTORY_FULL);
        when(plugin.isAlertFiring(AlertType.INVENTORY_FULL)).thenReturn(true);

        assertEquals(new Color(220, 60, 220), alertRow("Inv Full", NOW).fill);
        assertEquals(new Color(99, 27, 99), alertRow("Inv Full", NOW + 300L).fill);
        assertNull("a quiet row stays dark", alertRow("Idle", NOW).fill);
    }

    @Test
    public void aNearBlackAlertShouldLightUpInTheMutedInk()
    {
        enable(AlertType.POTION);
        when(plugin.isAlertFiring(AlertType.POTION)).thenReturn(true);

        // Potion's default black would fill its row black, on the dark panel: no light at all.
        assertEquals(new Color(140, 130, 120), alertRow("Potion", NOW).fill);
    }

    @Test
    public void forestryShouldNameItsEventWhileItFires()
    {
        enable(AlertType.FORESTRY);
        when(plugin.getLastForestryEvent()).thenReturn(ForestryEvent.LEPRECHAUN);
        assertEquals("", alertRow("Forestry").value);

        when(plugin.isAlertFiring(AlertType.FORESTRY)).thenReturn(true);
        assertEquals("Leprechaun", alertRow("Forestry").value);
    }

    @Test
    public void idleHeldByAnAnimationShouldShowTheHourglass()
    {
        enable(AlertType.IDLE);
        when(plugin.getCurrentAnimationId()).thenReturn(879);

        assertTrue(alertRow("Idle").held);
    }

    @Test
    public void idleShouldShowNoHourglassUnlessAnAnimationHoldsIt()
    {
        enable(AlertType.IDLE);
        assertFalse("not animating", alertRow("Idle").held);

        // Custom with Suppress While Animating off: animating doesn't hold Idle.
        when(plugin.currentPreset()).thenReturn(Preset.CUSTOM);
        when(config.idleRequireNoAnimation()).thenReturn(false);
        when(plugin.getCurrentAnimationId()).thenReturn(879);
        assertFalse("Idle isn't held", alertRow("Idle").held);
    }

    @Test
    public void thePanelShouldShowNoAnimationNumber()
    {
        enable(AlertType.IDLE);
        when(plugin.getCurrentAnimationId()).thenReturn(879);

        List<ConstsExpStatusOverlay.Row> rows = overlay.rows(NOW);
        assertEquals("the title and Idle, no animation row", 2, rows.size());
        for (ConstsExpStatusOverlay.Row row : rows)
        {
            assertFalse(text(row), text(row).contains("879") || text(row).contains("Animation"));
        }
    }

    @Test
    public void showAnimationIdShouldStayGone()
    {
        // const: no animation number. Ticket 03 moves this setting, so a merge could bring it back.
        for (Method method : ConstsExpConfig.class.getMethods())
        {
            assertNotEquals("showAnimationId", method.getName());
        }
    }

    @Test
    public void thePauseCountdownShouldBeANoteUnderTheAlerts()
    {
        enable(AlertType.IDLE);
        when(plugin.getPauseRemainingMillis()).thenReturn(2_000L);

        List<ConstsExpStatusOverlay.Row> rows = overlay.rows(NOW);
        ConstsExpStatusOverlay.TagRow pause = (ConstsExpStatusOverlay.TagRow) rows.get(rows.size() - 1);
        assertEquals("Paused", pause.label);
        assertEquals("0:02", pause.tag);
    }

    @Test
    public void hoveringARowShouldShowItsTooltip()
    {
        enable(AlertType.IDLE);
        // The renderer put the panel top right; the mouse is over Idle's row, under the 4 px border and the title.
        overlay.getBounds().setLocation(600, 10);
        when(client.getMouseCanvasPosition()).thenReturn(new net.runelite.api.Point(600 + 30, 10 + 4 + 15 + 7));

        BufferedImage canvas = new BufferedImage(129, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        overlay.render(graphics);
        graphics.dispose();

        verify(tooltips).add(argThat(tooltip -> AlertType.IDLE.getDescription().equals(tooltip.getText())));
    }

    @Test
    public void formatClockShouldRenderMinutesAndSeconds()
    {
        assertEquals("0:00", ConstsExpStatusOverlay.formatClock(0L));
        assertEquals("0:01", ConstsExpStatusOverlay.formatClock(1000L));
        assertEquals("2:34", ConstsExpStatusOverlay.formatClock(154000L));
        assertEquals("10:00", ConstsExpStatusOverlay.formatClock(600000L));
    }

    @Test
    public void aggroBarShouldMarkAnUnsettledAreaWithAQuestionMark()
    {
        assertEquals("9:41", ConstsExpStatusOverlay.aggroValueText(581_000L, true, true));
        assertEquals("? 9:41", ConstsExpStatusOverlay.aggroValueText(581_000L, false, true));
        assertEquals("?", ConstsExpStatusOverlay.aggroValueText(0L, false, false));
    }

    @Test
    public void overlappingAlertsShouldFlashOutOfPhase()
    {
        long interval = 300L;
        // At the end of Health's bright half, alerts in later slots have already dimmed.
        boolean anyDim = false;
        for (AlertType type : AlertType.values())
        {
            anyDim |= !type.isFlashPhaseOn(interval - 1L, interval);
        }
        assertTrue(AlertType.HEALTH.isFlashPhaseOn(interval - 1L, interval));
        assertTrue(anyDim);
        assertFalse(AlertType.HEALTH.isFlashPhaseOn(interval, interval));
    }

    private void enable(AlertType... types)
    {
        for (AlertType type : types)
        {
            when(plugin.isAlertEnabled(type)).thenReturn(true);
        }
    }

    private List<ConstsExpStatusOverlay.AlertRow> alertRows()
    {
        return overlay.rows(NOW).stream()
            .filter(ConstsExpStatusOverlay.AlertRow.class::isInstance)
            .map(ConstsExpStatusOverlay.AlertRow.class::cast)
            .collect(Collectors.toList());
    }

    private ConstsExpStatusOverlay.AlertRow alertRow(String label)
    {
        return alertRow(label, NOW);
    }

    private ConstsExpStatusOverlay.AlertRow alertRow(String label, long now)
    {
        return overlay.rows(now).stream()
            .filter(ConstsExpStatusOverlay.AlertRow.class::isInstance)
            .map(ConstsExpStatusOverlay.AlertRow.class::cast)
            .filter(row -> row.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no " + label + " row"));
    }

    private static String text(ConstsExpStatusOverlay.Row row)
    {
        if (row instanceof ConstsExpStatusOverlay.TagRow)
        {
            ConstsExpStatusOverlay.TagRow tagRow = (ConstsExpStatusOverlay.TagRow) row;
            return tagRow.label + " " + tagRow.tag;
        }
        ConstsExpStatusOverlay.AlertRow alertRow = (ConstsExpStatusOverlay.AlertRow) row;
        return alertRow.label + " " + alertRow.value;
    }

    private static void assertRow(ConstsExpStatusOverlay.AlertRow row, String icon, String value)
    {
        assertTrue(row.label + "'s icon is " + icon, samePixels(ImageUtil.loadImageResource(ConstsExpPlugin.class, icon), row.icon));
        assertEquals(value, row.value);
    }

    private static boolean samePixels(BufferedImage a, BufferedImage b)
    {
        if (a == null || b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
        {
            return false;
        }
        for (int y = 0; y < a.getHeight(); y++)
        {
            for (int x = 0; x < a.getWidth(); x++)
            {
                if (a.getRGB(x, y) != b.getRGB(x, y))
                {
                    return false;
                }
            }
        }
        return true;
    }
}
