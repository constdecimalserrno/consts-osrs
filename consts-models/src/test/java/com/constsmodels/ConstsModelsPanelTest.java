package com.constsmodels;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
        when(plugin.outputFolder()).thenReturn(new File(System.getProperty("user.home"), ".runelite/consts-models"));
        when(config.captureRadius()).thenReturn(6);
    }

    /** A mesh of one triangle, for the list. */
    private static Mesh mesh(String name, int triangles)
    {
        MeshBuilder b = new MeshBuilder(name);
        for (int i = 0; i < triangles; i++)
        {
            b.addTriangle(b.addVertex(0, 0, i), b.addVertex(100, 0, i), b.addVertex(0, 100, i), 0xFFFF00);
        }
        return b.build();
    }

    @Test
    public void theNewestCaptureComesFirst()
    {
        ConstsModelsPanel panel = new ConstsModelsPanel(plugin, config);
        Mesh goblin = mesh("NPC Goblin", 1);
        Mesh cow = mesh("NPC Cow", 1);
        Mesh area = mesh("Area around Lumbridge", 1);
        panel.addRecent(goblin);
        panel.addRecent(cow);
        panel.addRecent(area);

        assertEquals(List.of(area, cow, goblin), panel.recent());
        List<String> titles = new ArrayList<>();
        panel.recentRows().forEach(row -> titles.add(row.getTitle()));
        assertEquals(List.of("Area around Lumbridge", "NPC Cow", "NPC Goblin"), titles);
    }

    @Test
    public void theListKeepsTheLatestTwenty()
    {
        ConstsModelsPanel panel = new ConstsModelsPanel(plugin, config);
        List<Mesh> meshes = new ArrayList<>();
        for (int i = 1; i <= 25; i++)
        {
            Mesh m = mesh("Capture " + i, 1);
            meshes.add(m);
            panel.addRecent(m);
        }

        List<Mesh> shown = panel.recent();
        assertEquals(ConstsModelsPanel.MAX_RECENT, shown.size());
        assertEquals(20, shown.size());
        assertEquals(meshes.get(24), shown.get(0));
        assertEquals(meshes.get(5), shown.get(19));
        assertEquals(20, panel.recentRows().size());
        assertEquals("Capture 6", panel.recentRows().get(19).getTitle());
    }

    @Test
    public void aRowShowsTheNameAndTrianglesAndReopensTheCapture()
    {
        ConstsModelsPanel panel = new ConstsModelsPanel(plugin, config);
        Mesh goblin = mesh("<col=ffff00>NPC Goblin</col>", 3);
        panel.addRecent(goblin);

        ActionCard row = panel.recentRows().get(0);
        assertEquals("NPC Goblin", row.getTitle());
        assertEquals("3 triangles", row.getDetail());
        row.click();
        verify(plugin).reopen(goblin);
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
        String full = plugin.outputFolder().getAbsolutePath();
        String sep = File.separator;
        assertEquals("~" + sep + ".runelite" + sep + "consts-models", panel.folderLabel().getText());
        assertEquals(full, panel.folderLabel().getToolTipText());

        // A folder chosen later shows once the panel reads it again
        when(plugin.outputFolder()).thenReturn(new File(System.getProperty("user.home"), "Prints"));
        panel.onActivate();
        assertEquals("~" + sep + "Prints", panel.folderLabel().getText());
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
