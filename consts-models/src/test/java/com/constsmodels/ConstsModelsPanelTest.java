package com.constsmodels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import net.runelite.client.util.Filepath;
import org.junit.Before;
import org.junit.Test;

public class ConstsModelsPanelTest
{
    private static final String HOME = "/home/player";

    private ConstsModelsPlugin plugin;
    private ConstsModelsConfig config;

    @Before
    public void setUp()
    {
        plugin = mock(ConstsModelsPlugin.class);
        config = mock(ConstsModelsConfig.class);
        when(plugin.outputFolder()).thenReturn(folder(".runelite", "plugin-data", "consts-models"));
        when(config.captureRadius()).thenReturn(6);
    }

    private static long t = 1_700_000_000_000L;

    /** A capture as the library keeps it, newer than the last one made here. */
    private static ModelLibrary.Capture capture(String name, int triangles)
    {
        t += 60_000;
        return new ModelLibrary.Capture("id" + t, name, t, triangles, null, null, null);
    }

    /** A folder in the home folder, as RuneLite hands one over. */
    private static Filepath folder(String... names)
    {
        return Filepath.Unchecked.getRooted(Paths.get(System.getProperty("user.home"), names));
    }

    private static List<String> titles(ConstsModelsPanel panel)
    {
        List<String> titles = new ArrayList<>();
        panel.rows().forEach(row -> titles.add(row.getTitle()));
        return titles;
    }

    @Test
    public void theSettingsSitClosedAtTheBottomAndSaveAtOnce()
    {
        when(config.format()).thenReturn(ExportFormat.OBJ);
        ConstsModelsPanel panel = new ConstsModelsPanel(plugin, config);
        assertFalse(panel.settingsOpen());
        panel.toggleSettings();
        assertTrue(panel.settingsOpen());
        assertEquals(ExportFormat.OBJ, panel.formatBox().getSelectedItem());
        assertEquals(6, panel.radiusSpinner().getValue());
        // Named as in the Configuration panel, from the same annotations
        assertEquals("Area radius (tiles)",
            ConstsModelsPanel.setting("captureRadius").getAnnotation(net.runelite.client.config.ConfigItem.class).name());

        panel.formatBox().setSelectedItem(ExportFormat.PLY);
        panel.radiusSpinner().setValue(9);
        panel.shiftBox().setSelected(true);
        verify(config).format(ExportFormat.PLY);
        verify(config).captureRadius(9);
        verify(config).shiftOnly(true);

        // A change made in the Configuration panel shows here, and isn't saved back
        when(config.captureRadius()).thenReturn(12);
        panel.refresh();
        assertEquals(12, panel.radiusSpinner().getValue());
        verify(config, never()).captureRadius(12);
    }

    @Test
    public void theNewestCaptureComesFirstOnBothTabs()
    {
        ConstsModelsPanel panel = new ConstsModelsPanel(plugin, config);
        ModelLibrary.Capture goblin = capture("NPC Goblin", 1);
        ModelLibrary.Capture cow = capture("NPC Cow", 1);
        ModelLibrary.Capture area = capture("Area around Lumbridge", 1);
        panel.added(goblin);
        panel.added(cow);
        panel.added(area);

        assertEquals(List.of(area, cow, goblin), panel.session());
        assertEquals(List.of("Area around Lumbridge", "NPC Cow", "NPC Goblin"), titles(panel));
        panel.selectTab(true);
        assertEquals(List.of("Area around Lumbridge", "NPC Cow", "NPC Goblin"), titles(panel));
        assertEquals("Session (3)", panel.sessionTab().getText());
        assertEquals("All models (3)", panel.allTab().getText());
    }

    @Test
    public void allModelsHoldsEarlierSessionsToo()
    {
        ConstsModelsPanel panel = new ConstsModelsPanel(plugin, config);
        ModelLibrary.Capture old = capture("NPC Hans", 2);
        ModelLibrary.Capture older = capture("NPC Man", 2);
        ModelLibrary.Capture mine = capture("Player Me", 5);
        panel.added(mine);
        // The library's own list arrives after this session's first capture, which it may or may not hold yet
        panel.setLibrary(List.of(older, old));

        assertEquals(List.of(mine), panel.session());
        assertEquals(List.of(mine, older, old), panel.all());
        assertEquals(List.of("Player Me"), titles(panel));
        panel.selectTab(true);
        assertEquals(List.of("Player Me", "NPC Man", "NPC Hans"), titles(panel));
        assertEquals("Session (1)", panel.sessionTab().getText());
        assertEquals("All models (3)", panel.allTab().getText());
    }

    @Test
    public void aRowShowsTheNameWhenAndTrianglesAndOpensTheCapture()
    {
        ConstsModelsPanel panel = new ConstsModelsPanel(plugin, config);
        ModelLibrary.Capture goblin = capture("<col=ffff00>NPC Goblin</col>", 3);
        panel.added(goblin);

        ActionCard row = panel.rows().get(0);
        assertEquals("NPC Goblin", row.getTitle());
        assertTrue(row.getDetail(), row.getDetail().endsWith("3 triangles"));
        row.click();
        verify(plugin).open(goblin);
    }

    @Test
    public void theTrashCanDeletesAtOnceThenItGoesOffBothTabs()
    {
        ConstsModelsPanel panel = new ConstsModelsPanel(plugin, config);
        ModelLibrary.Capture goblin = capture("NPC Goblin", 1);
        ModelLibrary.Capture cow = capture("NPC Cow", 1);
        panel.added(goblin);
        panel.added(cow);

        panel.delete(cow);
        verify(plugin).delete(cow);
        verify(plugin, never()).delete(goblin);

        // The plugin deletes the files, then tells the panel
        panel.removed(cow);
        assertEquals(List.of(goblin), panel.session());
        assertEquals(List.of(goblin), panel.all());
        assertEquals(List.of("NPC Goblin"), titles(panel));
    }

    @Test
    public void whenSaysTheTimeTodayAndTheDayBefore()
    {
        java.util.Calendar now = java.util.Calendar.getInstance();
        now.set(2026, java.util.Calendar.OCTOBER, 21, 15, 30, 0);
        long today = now.getTimeInMillis();
        now.set(2026, java.util.Calendar.OCTOBER, 21, 9, 5, 0);
        assertEquals("09:05", ConstsModelsPanel.whenText(now.getTimeInMillis(), today));
        now.set(2026, java.util.Calendar.MARCH, 3, 9, 5, 0);
        assertEquals("3 Mar", ConstsModelsPanel.whenText(now.getTimeInMillis(), today));
        now.set(2025, java.util.Calendar.DECEMBER, 31, 9, 5, 0);
        assertEquals("31 Dec 2025", ConstsModelsPanel.whenText(now.getTimeInMillis(), today));
    }

    @Test
    public void trianglesAreCountedWithThousandsSeparators()
    {
        assertEquals("1 triangle", ConstsModelsPanel.trianglesText(1));
        assertEquals("0 triangles", ConstsModelsPanel.trianglesText(0));
        assertEquals("52,310 triangles", ConstsModelsPanel.trianglesText(52_310));
    }

    @Test
    public void theAreaIsTheSquareTheRadiusReaches()
    {
        assertEquals("13 \u00d7 13 tiles around you", ConstsModelsPanel.areaText(6));
        assertEquals("3 \u00d7 3 tiles around you", ConstsModelsPanel.areaText(1));
        assertEquals("49 \u00d7 49 tiles around you", ConstsModelsPanel.areaText(24));
    }

    @Test
    public void theAreaIsReadAgainWhenThePanelOpens()
    {
        ConstsModelsPanel panel = new ConstsModelsPanel(plugin, config);
        assertEquals("13 \u00d7 13 tiles around you", panel.areaCard().getDetail());

        when(config.captureRadius()).thenReturn(10);
        panel.onActivate();
        assertEquals("21 \u00d7 21 tiles around you", panel.areaCard().getDetail());
    }

    @Test
    public void theFolderLabelShortensHomeAndKeepsTheFullPathInItsTooltip()
    {
        ConstsModelsPanel panel = new ConstsModelsPanel(plugin, config);
        String full = plugin.outputFolder().toString();
        String sep = File.separator;
        // Too long for the bar: the folders between home and the last make way for dots
        assertEquals("~" + sep + "..." + sep + "consts-models", panel.folderLabel().getText());
        assertEquals(full, panel.folderLabel().getToolTipText());

        // A folder chosen later shows once the panel reads it again
        when(plugin.outputFolder()).thenReturn(folder("Prints"));
        panel.onActivate();
        assertEquals("~" + sep + "Prints", panel.folderLabel().getText());
    }

    @Test
    public void theFolderButtonsCopyItsPathAndChooseAnotherForTheSession()
    {
        ConstsModelsPanel panel = new ConstsModelsPanel(plugin, config);
        List<AbstractButton> buttons = new ArrayList<>();
        ViewerPanelTest.collect(panel, buttons);
        AbstractButton copy = button(buttons, "Copy path");
        AbstractButton choose = button(buttons, "Choose folder");
        assertTrue(buttons.stream().noneMatch(b -> "Open folder".equals(b.getText())));
        // A folder chosen can't be kept past a restart: RuneLite hands it over for the session only
        assertTrue(choose.getToolTipText(), choose.getToolTipText().contains("until RuneLite closes"));

        copy.doClick();
        verify(plugin).copyPath();
    }

    private static AbstractButton button(List<AbstractButton> buttons, String text)
    {
        return buttons.stream().filter(b -> text.equals(b.getText())).findFirst()
            .orElseThrow(() -> new AssertionError("no " + text + " button"));
    }

    @Test
    public void theHomeFolderBecomesATilde()
    {
        assertEquals("~/.runelite/consts-models", fit(HOME + "/.runelite/consts-models", 100));
        assertEquals("~", fit(HOME, 100));
        assertEquals("~\\Prints", ConstsModelsPanel.shortPath("C:\\Home\\Player\\Prints", "C:\\Home\\Player", 100,
            String::length));
    }

    @Test
    public void aFolderOutsideHomeKeepsItsPath()
    {
        assertEquals("/mnt/prints/osrs", fit("/mnt/prints/osrs", 100));
        // Only a whole folder name is home: /home/player2 isn't inside /home/player
        assertEquals("/home/player2/prints", fit("/home/player2/prints", 100));
        assertEquals("D:\\Prints", ConstsModelsPanel.shortPath("D:\\Prints", "C:\\Home\\Player", 100, String::length));
    }

    @Test
    public void aLongPathKeepsItsFirstAndLastFoldersWithDotsBetween()
    {
        // Widths here are in characters
        assertEquals("~/.../consts-models", fit(HOME + "/.runelite/consts-models", 24));
        String path = "/mnt/storage/3d/prints/osrs";
        assertEquals(path, fit(path, path.length()));
        assertEquals("/mnt/.../3d/prints/osrs", fit(path, 23));
        assertEquals("/mnt/.../prints/osrs", fit(path, 20));
        assertEquals("/mnt/.../osrs", fit(path, 15));
        String windows = "C:\\Games\\RuneLite\\3D prints\\osrs";
        assertEquals("C:\\...\\3D prints\\osrs", ConstsModelsPanel.shortPath(windows, null, 21, String::length));
    }

    @Test
    public void aPathTooLongForThatLosesItsFirstFolderToo()
    {
        assertEquals(".../osrs", fit("/mnt/storage/3d/prints/osrs", 10));
        assertEquals("...\\osrs", ConstsModelsPanel.shortPath("E:\\storage\\3d\\osrs", null, 10, String::length));
    }

    @Test
    public void aLastFolderTooWideKeepsItsEnd()
    {
        assertEquals("...ts-models", fit("/mnt/consts-models", 12));
        assertEquals("...", fit("/mnt/consts-models", 2));
    }

    private static String fit(String path, int width)
    {
        return ConstsModelsPanel.shortPath(path, HOME, width, String::length);
    }
}
