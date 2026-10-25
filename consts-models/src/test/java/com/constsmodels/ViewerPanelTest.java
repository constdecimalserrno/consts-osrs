package com.constsmodels;

import static com.constsmodels.TestMeshes.BLUE;
import static com.constsmodels.TestMeshes.RED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import org.junit.Test;

public class ViewerPanelTest
{
    @Test
    public void theLineInTheCornerCountsTriangles()
    {
        assertEquals("12 triangles", ViewerPanel.sizeText(OrbitCameraTest.box(0, 0, 0, 400, 200, 100, 0xFFFFFF)));
        assertEquals("1 triangle", ViewerPanel.sizeText(CoreTest.triangle(0xFF0000)));
        // An actor's animations, which GLB saves
        assertEquals("1 triangle, 2 animations", ViewerPanel.sizeText(TestMeshes.animated(CoreTest.triangle(0xFF0000))));
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
        // The folder's path goes on the clipboard: the Plugin Hub lets no plugin open the file browser
        assertTrue(texts.toString(), texts.containsAll(List.of("Copy path", "Save image", "Save")));
        assertFalse(texts.contains("Open folder"));
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

    @Test
    public void aStillCaptureHasNoAnimationControls()
    {
        Mesh mesh = OrbitCameraTest.box(0, 0, 0, 40, 40, 40, RED);
        ViewerPanel panel = new ViewerPanel(mesh,
            actions(new ArrayList<>(), new int[]{ViewerCanvas.DEFAULT_BACKGROUND}));
        List<AbstractButton> buttons = new ArrayList<>();
        collect(panel, buttons);
        List<String> texts = buttons.stream().map(AbstractButton::getText).collect(Collectors.toList());
        assertFalse(texts.toString(), texts.contains("Pause") || texts.contains("Play") || texts.contains(">"));
        assertSame(mesh, panel.shown());
        assertNull(panel.animations());
        int still = panel.minimumWidth();
        assertTrue(still <= 640);
        ViewerPanel animated = new ViewerPanel(TestMeshes.animated(mesh),
            actions(new ArrayList<>(), new int[]{ViewerCanvas.DEFAULT_BACKGROUND}));
        assertTrue("room for the animation's controls", animated.minimumWidth() > still);
    }

    @Test
    public void anAnimationPlaysPausesAndStepsItsFrames()
    {
        Mesh mesh = TestMeshes.animated(OrbitCameraTest.box(0, 0, 0, 40, 40, 40, RED));
        ViewerPanel panel = new ViewerPanel(mesh,
            actions(new ArrayList<>(), new int[]{ViewerCanvas.DEFAULT_BACKGROUND}));

        // Opens on the first animation, playing, at its first frame
        JComboBox<String> picker = panel.animations();
        assertNotNull(picker);
        assertEquals(2, picker.getItemCount());
        assertEquals("Idle", picker.getItemAt(0));
        assertEquals("Walk", picker.getItemAt(1));
        assertEquals(0, picker.getSelectedIndex());
        assertTrue(panel.playing());
        assertEquals("Frame 1/2", panel.frameText());
        assertEquals(mesh.faceCount(), panel.shown().faceCount());
        assertEquals(mesh.x[0], panel.shown().x[0], 0f);

        // A step pauses and shows that frame's corners; steps go round both ways
        panel.step(1);
        assertFalse(panel.playing());
        assertEquals("Frame 2/2", panel.frameText());
        assertEquals(mesh.x[0] + 10, panel.shown().x[0], 0f);
        panel.step(1);
        assertEquals("Frame 1/2", panel.frameText());
        panel.step(-1);
        assertEquals("Frame 2/2", panel.frameText());

        panel.togglePlay();
        assertTrue(panel.playing());
        panel.togglePlay();
        assertFalse(panel.playing());

        // Space plays and pauses, so no button keeps the focus to take it
        List<AbstractButton> buttons = new ArrayList<>();
        collect(panel, buttons);
        assertTrue(buttons.stream().noneMatch(Component::isFocusable));

        // Another animation starts at its first frame
        picker.setSelectedIndex(1);
        assertEquals("Frame 1/3", panel.frameText());
        panel.step(-1);
        assertEquals("Frame 3/3", panel.frameText());
        assertEquals(mesh.z[0] + 10, panel.shown().z[0], 0f);
    }

    @Test
    public void playbackLoopsFromTheClipsLoopFrameAtTheGamesPace()
    {
        Clip walk = TestMeshes.animated(CoreTest.triangle(RED)).clips.get(1);
        assertEquals(1, ViewerPanel.next(walk, 0));
        assertEquals(2, ViewerPanel.next(walk, 1));
        assertEquals("after the last frame, the loop frame", 1, ViewerPanel.next(walk, 2));
        assertEquals(6 * Clip.TICK_MS, ViewerPanel.delay(walk, 2));
    }

    @Test
    public void aPieceTakenOutKeepsTheFrameShown()
    {
        MeshBuilder b = new MeshBuilder("Two boxes");
        TestMeshes.box(b, 0, 0, 0, 40, 40, 40, true, RED, RED);
        TestMeshes.box(b, 100, 0, 0, 140, 40, 40, true, BLUE, BLUE);
        Mesh mesh = TestMeshes.animated(b.build());
        ViewerPanel panel = new ViewerPanel(mesh,
            actions(new ArrayList<>(), new int[]{ViewerCanvas.DEFAULT_BACKGROUND}));
        panel.step(1);
        panel.picked(0);
        panel.removePicked();
        assertEquals(mesh.faceCount() / 2, panel.shown().faceCount());
        assertEquals("Frame 2/2", panel.frameText());
        Mesh model = panel.model();
        assertEquals(model.clips.get(0).x[1][0], panel.shown().x[0], 0f);
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
            public void copyPath()
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

    static void collect(Container root, List<AbstractButton> out)
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
