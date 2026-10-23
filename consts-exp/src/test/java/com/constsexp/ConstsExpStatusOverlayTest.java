package com.constsexp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.Point;
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
        title.setPreferredSize(new Dimension(121, 0));
        assertEquals("Woodcutting fits beside the title", 14, title.render(graphics).height);

        when(plugin.currentPreset()).thenReturn(Preset.SALVAGING_ACTIVE);
        title = overlay.rows(NOW).get(0);
        title.setPreferredSize(new Dimension(121, 0));
        assertEquals("Salvaging Active goes under it", 27, title.render(graphics).height);
    }

    @Test
    public void thePresetTagsBoxShouldCoverItsDescenders()
    {
        // Woodcutting's g hangs below the baseline, and its shadow below that.
        BufferedImage title = render(overlay.rows(NOW).get(0));

        // The box is the 40% black; the tag's text and shadow are opaque.
        int left = 121;
        int top = 30;
        int bottom = -1;
        for (int y = 0; y < 30; y++)
        {
            for (int x = 0; x < 121; x++)
            {
                if (title.getRGB(x, y) >>> 24 == 102)
                {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        for (int y = 0; y < 30; y++)
        {
            for (int x = left; x < 121; x++)
            {
                if (title.getRGB(x, y) >>> 24 == 255)
                {
                    assertTrue("tag ink at " + x + "," + y + " outside the box's rows " + top + ".." + bottom,
                        y >= top && y <= bottom);
                }
            }
        }
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
        assertNotNull("the hourglass", ConstsExpStatusOverlay.HOURGLASS);
        for (ConstsExpStatusOverlay.AlertRow row : rows)
        {
            assertNotNull(row.label + "'s icon", row.icon);
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
        when(plugin.getIdleThresholdMillis()).thenReturn(6_000L);
        // Aggro with 2:30 of its 10 minutes left.
        when(plugin.getNextAggroAlertMillis()).thenReturn(NOW + 150_000L);
        when(plugin.getAggroIntervalMillis()).thenReturn(600_000L);
        // Logout 12 minutes after the last click: 13:00 to the logout, halfway to the 24-minute warning.
        when(plugin.getMillisSinceUserInteraction()).thenReturn(720_000L);
        when(plugin.getLogoutWarningMillis()).thenReturn(1_440_000L);
        when(plugin.getLogoutTimerRemainingMillis()).thenReturn(780_000L);

        assertEquals(0.5, alertRow("Idle").line, 1e-6);
        assertEquals("2:30", alertRow("Aggro").value);
        assertEquals(0.75, alertRow("Aggro").line, 1e-6);
        assertEquals("13:00", alertRow("Logout").value);
        assertEquals(0.5, alertRow("Logout").line, 1e-6);
    }

    @Test
    public void logoutsLineShouldFillToItsWarningEvenPastTheLogout()
    {
        enable(AlertType.LOGOUT);
        // Warn After at 30 minutes, past the 25-minute logout: 27 minutes in, the countdown already reads 0:00.
        when(config.logoutWarningMs()).thenReturn(1_800_000);
        when(plugin.getMillisSinceUserInteraction()).thenReturn(1_620_000L);
        when(plugin.getLogoutWarningMillis()).thenReturn(1_800_000L);
        when(plugin.getLogoutTimerRemainingMillis()).thenReturn(0L);

        assertEquals("0:00", alertRow("Logout").value);
        assertEquals(0.9, alertRow("Logout").line, 1e-6);
    }

    @Test
    public void aLineShouldBeFullOnlyOnceItsAlertIsDue()
    {
        enable(AlertType.LOGOUT);
        // 23:57 of the 24-minute warning: 99.8% of the 121 px row is 120.75 px, still short of full.
        when(plugin.getMillisSinceUserInteraction()).thenReturn(1_437_000L);
        when(plugin.getLogoutWarningMillis()).thenReturn(1_440_000L);

        BufferedImage row = render(alertRow("Logout"));
        assertNotEquals("the line's last pixel waits for the warning", row.getRGB(0, 14), row.getRGB(120, 14));
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
    public void aFiringRowShouldFillWithItsColorAndPulseWithTheScreenFlash()
    {
        enable(AlertType.IDLE, AlertType.INVENTORY_FULL);
        when(plugin.isAlertFiring(AlertType.INVENTORY_FULL)).thenReturn(true);
        when(plugin.isFlashingScreen(eq(AlertType.INVENTORY_FULL), anyLong())).thenReturn(true);
        when(plugin.isFlashPhaseOn(AlertType.INVENTORY_FULL, NOW)).thenReturn(true);

        assertEquals("the flash's bright half", new Color(220, 60, 220), alertRow("Inv Full", NOW).fill);
        assertEquals("its dark half", new Color(99, 27, 99), alertRow("Inv Full", NOW + 300L).fill);
        assertNull("a quiet row stays dark", alertRow("Idle", NOW).fill);
    }

    @Test
    public void aFiringRowShouldStayLitAndSteadyWhileItDoesntFlashTheScreen()
    {
        enable(AlertType.INVENTORY_FULL);
        // Held by the Click Pause, or in a burst's panel window after its flash: still lit, no longer flashing.
        when(plugin.isAlertFiring(AlertType.INVENTORY_FULL)).thenReturn(true);

        assertEquals(new Color(220, 60, 220), alertRow("Inv Full", NOW).fill);
        assertEquals(new Color(220, 60, 220), alertRow("Inv Full", NOW + 300L).fill);
    }

    @Test
    public void aDarkColorShouldBeLightenedUntilItShowsOnAQuietRow()
    {
        // Toward white, to 2.75:1 against a quiet row over the brightest scene, where colors show least.
        assertEquals("black, Potion's default", new Color(130, 130, 130), potionInk(Color.BLACK));
        assertEquals("near-black grey", new Color(130, 130, 130), potionInk(new Color(30, 30, 30)));
        assertEquals("maroon", new Color(171, 116, 116), potionInk(new Color(100, 0, 0)));
        assertEquals("navy", new Color(125, 125, 181), potionInk(new Color(0, 0, 110)));
        assertEquals("Prayer's blue already shows", new Color(88, 190, 255), potionInk(new Color(88, 190, 255)));
    }

    @Test
    public void aFiringPotionShouldLightUpLightenedToo()
    {
        enable(AlertType.POTION);
        when(plugin.isAlertFiring(AlertType.POTION)).thenReturn(true);
        when(plugin.isFlashingScreen(eq(AlertType.POTION), anyLong())).thenReturn(true);
        when(plugin.isFlashPhaseOn(AlertType.POTION, NOW)).thenReturn(true);

        // Its default black would fill the row black, on the dark panel: no light at all.
        assertEquals(new Color(130, 130, 130), alertRow("Potion", NOW).fill);
        assertEquals(new Color(59, 59, 59), alertRow("Potion", NOW + 300L).fill);
    }

    @Test
    public void blackTextAndIconsShouldHaveNoShadow()
    {
        enable(AlertType.PRAYER);
        when(client.getBoostedSkillLevel(Skill.PRAYER)).thenReturn(61);
        when(client.getRealSkillLevel(Skill.PRAYER)).thenReturn(77);
        when(plugin.isAlertFiring(AlertType.PRAYER)).thenReturn(true);
        when(plugin.isFlashingScreen(eq(AlertType.PRAYER), anyLong())).thenReturn(true);
        when(plugin.isFlashPhaseOn(AlertType.PRAYER, NOW)).thenReturn(true);

        // The bright half letters the row black and the dark half white, on the same glyphs. White text keeps its
        // black shadow; black text has none, so its pixels are exactly the white text's.
        BufferedImage black = render(alertRow("Prayer", NOW));
        BufferedImage white = render(alertRow("Prayer", NOW + 300L));
        for (int y = 0; y < 15; y++)
        {
            for (int x = 0; x < 121; x++)
            {
                assertEquals("pixel " + x + "," + y, white.getRGB(x, y) == 0xFFFFFFFF,
                    black.getRGB(x, y) == 0xFF000000);
            }
        }
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
        when(plugin.isIdleHeldByAnimation()).thenReturn(true);

        assertTrue(alertRow("Idle").held);
    }

    @Test
    public void idleShouldShowNoHourglassUnlessAnAnimationHoldsIt()
    {
        enable(AlertType.IDLE);
        // Animating, but the plugin doesn't hold Idle for it (Custom with Suppress While Animating off, say).
        when(plugin.getCurrentAnimationId()).thenReturn(879);

        assertFalse(alertRow("Idle").held);
    }

    @Test
    public void thePanelShouldShowNoAnimationNumber()
    {
        enable(AlertType.IDLE);
        when(plugin.getCurrentAnimationId()).thenReturn(879);
        when(plugin.isIdleHeldByAnimation()).thenReturn(true);

        List<ConstsExpStatusOverlay.Row> rows = overlay.rows(NOW);
        assertEquals("the title and Idle, no animation row", 2, rows.size());
        for (ConstsExpStatusOverlay.Row row : rows)
        {
            assertFalse(text(row), text(row).contains("879") || text(row).contains("Animation"));
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
    public void thePanelShouldNeverBeNarrowerThanItsRows()
    {
        enable(AlertType.IDLE);
        Graphics2D graphics = new BufferedImage(200, 60, BufferedImage.TYPE_INT_ARGB).createGraphics();

        // A width dragged smaller, or saved that way by an older version: the rows are laid out for 129 px.
        // (The panel sizes itself from the frame before, so each width is drawn twice.)
        overlay.setPreferredSize(new Dimension(80, 0));
        assertEquals(129, overlay.getPreferredSize().width);
        overlay.render(graphics);
        assertEquals(129, overlay.render(graphics).width);

        overlay.setPreferredSize(new Dimension(200, 0));
        overlay.render(graphics);
        assertEquals("wider is fine", 200, overlay.render(graphics).width);

        // RuneLite's own drag stops there too, so a dragged west edge doesn't push the panel along.
        assertEquals(129, overlay.getMinimumSize());
    }

    @Test
    public void tooltipsShouldSayShiftRightClickForThePanelsMenu()
    {
        // RuneLite only offers an overlay's menu entries while Shift is held.
        enable(AlertType.AGGRO);
        when(plugin.usesAggroArea()).thenReturn(true); // the area isn't known yet: Aggro's "?" tooltip

        List<String> texts = new ArrayList<>();
        for (ConstsExpStatusOverlay.Row row : overlay.rows(NOW))
        {
            texts.add(row.tooltip);
        }
        for (AlertType type : AlertType.values())
        {
            texts.add(type.getDescription());
        }
        for (String text : texts)
        {
            if (text.toLowerCase(Locale.ROOT).contains("right-click"))
            {
                assertTrue(text, text.contains("Shift + right-click"));
            }
        }
    }

    @Test
    public void hoveringARowShouldShowItsTooltip()
    {
        enable(AlertType.IDLE);
        // The renderer put the panel top right; the mouse is over Idle's row, under the 4 px border and the title.
        overlay.getBounds().setLocation(600, 10);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(600 + 30, 10 + 4 + 15 + 7));

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
    public void clocksShouldUseWesternDigitsInAnyLocale()
    {
        Locale before = Locale.getDefault();
        try
        {
            Locale.setDefault(Locale.forLanguageTag("th-TH-u-nu-thai"));
            assertEquals("1:05", ConstsExpStatusOverlay.formatClock(65_000L));
        }
        finally
        {
            Locale.setDefault(before);
        }
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
        return alertRows(NOW);
    }

    private List<ConstsExpStatusOverlay.AlertRow> alertRows(long now)
    {
        return overlay.rows(now).stream()
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
        return alertRows(now).stream()
            .filter(row -> row.label.equals(label))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no " + label + " row"));
    }

    private Color potionInk(Color color)
    {
        when(config.potionExpiryFlashColor()).thenReturn(color);
        enable(AlertType.POTION);
        return alertRow("Potion").ink;
    }

    /** A row drawn alone, 121 px wide as in the standard panel, on a transparent canvas. */
    private static BufferedImage render(ConstsExpStatusOverlay.Row row)
    {
        BufferedImage image = new BufferedImage(121, 30, BufferedImage.TYPE_INT_ARGB);
        row.setPreferredSize(new Dimension(121, 0));
        Graphics2D graphics = image.createGraphics();
        row.render(graphics);
        graphics.dispose();
        return image;
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
        BufferedImage expected = ImageUtil.loadImageResource(ConstsExpPlugin.class, icon);
        assertTrue(row.label + "'s icon is " + icon, samePixels(expected, row.icon));
        assertEquals(value, row.value);
    }

    private static boolean samePixels(BufferedImage a, BufferedImage b)
    {
        if (a == null || b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
        {
            return false;
        }
        int width = a.getWidth();
        int height = a.getHeight();
        return Arrays.equals(a.getRGB(0, 0, width, height, null, 0, width),
            b.getRGB(0, 0, width, height, null, 0, width));
    }
}
