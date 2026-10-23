package com.constsmodels;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Date;
import net.runelite.api.Model;
import org.junit.Test;

public class CoreTest
{
    /** A mesh of one triangle, for tests: (0,0,0), (100,0,0), (0,100,50). */
    static Mesh triangle(int rgb)
    {
        MeshBuilder b = new MeshBuilder("Triangle");
        b.addTriangle(b.addVertex(0, 0, 0), b.addVertex(100, 0, 0), b.addVertex(0, 100, 50), rgb);
        return b.build();
    }

    @Test
    public void aMeshKnowsItsBounds()
    {
        assertArrayEquals(new float[]{0, 0, 0, 100, 100, 50}, triangle(0xFF0000).bounds(), 0f);
        assertArrayEquals(new float[6], new MeshBuilder("Empty").build().bounds(), 0f);
    }

    @Test
    public void addingAMeshTurnsItTheGamesWayThenMovesIt()
    {
        // A quarter turn (512) takes east to south and north to east, as the game turns an object
        Mesh turned = new MeshBuilder("Turned").add(triangle(0x00FF00), 512, 1000, 2000, 3).build();
        assertEquals(1000, turned.x[1], 0.01);
        assertEquals(2000 - 100, turned.y[1], 0.01);
        assertEquals(1000 + 100, turned.x[2], 0.01);
        assertEquals(2000, turned.y[2], 0.01);
        assertEquals(53, turned.z[2], 0.01);
        assertEquals(0x00FF00, turned.color[0]);
    }

    @Test
    public void aModelTurnsIntoEastNorthUpWithoutItsHiddenFaces()
    {
        Model model = mock(Model.class);
        when(model.getVerticesCount()).thenReturn(3);
        when(model.getVerticesX()).thenReturn(new float[]{0, 10, 0});
        when(model.getVerticesY()).thenReturn(new float[]{0, 0, -20});
        when(model.getVerticesZ()).thenReturn(new float[]{0, 0, 30});
        when(model.getFaceCount()).thenReturn(3);
        when(model.getFaceIndices1()).thenReturn(new int[]{0, 0, 0});
        when(model.getFaceIndices2()).thenReturn(new int[]{1, 1, 1});
        when(model.getFaceIndices3()).thenReturn(new int[]{2, 2, 2});
        // A flat face, a hidden face, a see-through face
        when(model.getFaceColors1()).thenReturn(new int[]{127, 0, 0});
        when(model.getFaceColors2()).thenReturn(new int[]{0, 0, 0});
        when(model.getFaceColors3()).thenReturn(new int[]{-1, -2, -1});
        when(model.getFaceTransparencies()).thenReturn(new byte[]{0, 0, (byte) 255});

        Mesh mesh = ModelCapture.capture(model, "NPC Goblin", null);
        assertEquals("NPC Goblin", mesh.name);
        assertEquals(1, mesh.faceCount());
        // The game's y is down and z north
        assertEquals(30, mesh.y[2], 0f);
        assertEquals(20, mesh.z[2], 0f);
        // HSL lightness 127 with no saturation is white
        assertTrue(Integer.toHexString(mesh.color[0]), (mesh.color[0] & 0xFF) > 240);
    }

    @Test
    public void noModelIsAnEmptyMesh()
    {
        assertTrue(ModelCapture.capture(null, "Nothing", null).isEmpty());
    }

    @Test
    public void colorsStayInsideTheirChannels()
    {
        for (int hsl = 0; hsl < 65536; hsl += 7)
        {
            int rgb = JagexColor.hslToRgb((short) hsl, JagexColor.BRIGHTNESS_MIN);
            assertEquals(0, rgb & ~0xFFFFFF);
        }
        assertEquals(0x808080, JagexColor.hslToRgbAverage(64, 64, 64) & 0xF0F0F0 & 0x808080);
    }

    @Test
    public void aTexturesColorIsTheAverageOfItsVisiblePixels()
    {
        assertEquals(0x804020, TextureColors.average(new int[]{0, 0x804020, 0x804020, 0}));
        assertEquals(0xFFFFFF, TextureColors.average(new int[]{0, 0}));
    }

    @Test
    public void fileNamesLoseWhatFileSystemsDislike()
    {
        Date when = new Date(0);
        String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(when);
        assertEquals("NPC Goblin (level-2) " + stamp, ConstsModelsPlugin.fileName("NPC <col=ffff00>Goblin</col> (level-2)", when));
        assertEquals("Model " + stamp, ConstsModelsPlugin.fileName("../:*?", when));
    }

    @Test
    public void anErrorInChatSaysWhatWentWrongNotOnlyWhere()
    {
        // Java's file errors often give only the file
        assertEquals("/p/Goblin.obj (access denied)",
            ConstsModelsPlugin.reason(new java.nio.file.AccessDeniedException("/p/Goblin.obj")));
        assertEquals("/p (not found)", ConstsModelsPlugin.reason(new java.nio.file.NoSuchFileException("/p")));
        assertEquals("it ends too soon", ConstsModelsPlugin.reason(new java.io.EOFException()));
        // A message that says why already stays as it is, the plugin's own included
        assertEquals("/p/x.obj: No space left on device", ConstsModelsPlugin.reason(
            new java.nio.file.FileSystemException("/p/x.obj", null, "No space left on device")));
        assertEquals("there's nothing in it",
            ConstsModelsPlugin.reason(new java.io.IOException("there's nothing in it")));
    }
}
