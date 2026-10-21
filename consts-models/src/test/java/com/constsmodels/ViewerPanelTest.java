package com.constsmodels;

import static com.constsmodels.TestMeshes.BLUE;
import static com.constsmodels.TestMeshes.RED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import org.junit.Test;

public class ViewerPanelTest
{
    @Test
    public void theLineInTheCornerCountsTriangles()
    {
        assertEquals("12 triangles", ViewerPanel.sizeText(OrbitCameraTest.box(0, 0, 0, 400, 200, 100, 0xFFFFFF)));
        assertEquals("1 triangle", ViewerPanel.sizeText(CoreTest.triangle(0xFF0000)));
    }

    @Test
    public void theTitleNamesTheModelWithoutTheGamesColorTags()
    {
        assertEquals("consts-Models: NPC Goblin", ViewerWindow.title("NPC <col=ffff00>Goblin</col>"));
        assertEquals("consts-Models: Model", ViewerWindow.title(null));
        assertEquals("consts-Models: Model", ViewerWindow.title("<col=ff0000></col>"));
    }

    @Test
    public void theBarSavesTheModelAndFollowsTheBackground()
    {
        MeshBuilder b = new MeshBuilder("Box");
        TestMeshes.box(b, 0, 0, 0, 40, 40, 40, true, RED, BLUE);
        List<String> saved = new ArrayList<>();
        int[] background = {ViewerCanvas.DEFAULT_BACKGROUND};
        ViewerPanel panel = new ViewerPanel(b.build(), actions(saved, background));

        List<AbstractButton> buttons = new ArrayList<>();
        collect(panel, buttons);
        List<String> texts = buttons.stream().map(AbstractButton::getText).collect(Collectors.toList());
        assertTrue(texts.toString(), texts.containsAll(List.of("Open folder", "Save image", "Save")));
        assertFalse("no Print view any more", texts.contains("Print") || texts.contains("Base"));
        BackgroundButton bg = (BackgroundButton) buttons.stream().filter(c -> c instanceof BackgroundButton)
            .findFirst().orElse(null);
        assertNotNull(bg);
        JButton save = (JButton) buttons.stream().filter(c -> "Save".equals(c.getText())).findFirst().orElse(null);
        assertNotNull(save);
        save.doClick();
        assertEquals(List.of("12 OBJ"), saved);

        // A background picked elsewhere shows when the window comes back
        background[0] = 0xFFFFFF;
        panel.refresh();
        assertEquals(0xFFFFFF, panel.canvas().backgroundColor());
        assertTrue(bg.getToolTipText().contains("White"));
    }

    @Test
    public void lightBackgroundsGetDarkText()
    {
        assertFalse(ViewerCanvas.light(ViewerCanvas.DEFAULT_BACKGROUND));
        assertFalse(ViewerCanvas.light(0x000000));
        assertTrue(ViewerCanvas.light(0xFFFFFF));
        assertTrue(ViewerCanvas.light(0x87CEEB));
        assertEquals(BackgroundButton.BACKGROUNDS.length, BackgroundButton.BACKGROUND_NAMES.length);
        assertEquals("#123456", BackgroundButton.name(0x123456));
    }

    /** Viewer actions that note each save as "triangles format", start on OBJ and keep the background given. */
    static ViewerActions actions(List<String> saved, int[] background)
    {
        return new ViewerActions()
        {
            @Override
            public void save(Mesh mesh, ExportFormat format)
            {
                saved.add(mesh.faceCount() + " " + format);
            }

            @Override
            public void saveImage(Mesh mesh, BufferedImage image)
            {
            }

            @Override
            public void openFolder()
            {
            }

            @Override
            public ExportFormat defaultFormat()
            {
                return ExportFormat.OBJ;
            }

            @Override
            public int background()
            {
                return background[0];
            }

            @Override
            public void setBackground(int rgb)
            {
                background[0] = rgb;
            }
        };
    }

    private static void collect(Container root, List<AbstractButton> out)
    {
        for (Component c : root.getComponents())
        {
            if (c instanceof AbstractButton)
            {
                out.add((AbstractButton) c);
            }
            if (c instanceof Container)
            {
                collect((Container) c, out);
            }
        }
    }
}
