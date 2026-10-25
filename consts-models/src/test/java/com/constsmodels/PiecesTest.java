package com.constsmodels;

import static com.constsmodels.TestMeshes.BLUE;
import static com.constsmodels.TestMeshes.GREEN;
import static com.constsmodels.TestMeshes.RED;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PiecesTest
{
    /** A box and, apart from it, a second box, then a flat square floating beside them: three pieces. */
    static Mesh scene()
    {
        MeshBuilder b = new MeshBuilder("Scene");
        // Each side on corners of its own, as game models come: a piece once welded
        TestMeshes.box(b, 0, 0, 0, 40, 40, 40, true, RED);
        TestMeshes.box(b, 60, 0, 0, 100, 40, 60, true, BLUE);
        TestMeshes.quad(b, new float[]{0, 60, 2}, new float[]{40, 60, 2}, new float[]{40, 100, 2},
            new float[]{0, 100, 2}, GREEN);
        return b.build();
    }

    @Test
    public void aModelSplitsIntoThePiecesThatDontTouch()
    {
        Mesh scene = scene();
        Pieces pieces = Pieces.of(scene);
        assertEquals(3, pieces.count);
        assertArrayEquals(new int[]{12, 12, 2}, pieces.sizes());
        for (int f = 0; f < scene.faceCount(); f++)
        {
            assertEquals(f < 12 ? 0 : f < 24 ? 1 : 2, pieces.piece[f]);
        }

    }

    @Test
    public void takingAPieceOutKeepsTheClipsOnTheCornersLeft()
    {
        Mesh scene = TestMeshes.animated(scene());
        Pieces pieces = Pieces.of(scene);
        Mesh left = Pieces.only(scene, pieces.kept(new boolean[]{true, false, false}));
        assertEquals(2, left.clips.size());
        Clip idle = left.clips.get(0);
        assertEquals(left.vertexCount(), idle.vertexCount());
        assertEquals("Idle", idle.label);
        // The first corner left is the blue box's first, 10 east of where it stands at frame 1
        assertEquals(left.x[0] + 10, idle.x[1][0], 0f);
        assertEquals(left.y[0], idle.y[1][0], 0f);
    }

    @Test
    public void takingAPieceOutLeavesTheRest()
    {
        Mesh scene = scene();
        Pieces pieces = Pieces.of(scene);
        int[] kept = pieces.kept(new boolean[]{false, true, false});
        assertEquals(14, kept.length);
        assertEquals(11, kept[11]);
        assertEquals(24, kept[12]);
        Mesh left = Pieces.only(scene, kept);
        assertEquals(14, left.faceCount());
        assertEquals(24 + 4, left.vertexCount());
        assertEquals(GREEN, left.color[13]);
        float[] box = left.bounds();
        assertEquals(40, box[3], 0f);
        assertEquals(40, box[5], 0f);
    }

    @Test
    public void theViewerPicksRemovesAndBringsBackPieces()
    {
        Mesh scene = scene();
        List<String> saved = new ArrayList<>();
        ViewerPanel panel = new ViewerPanel(scene,
            ViewerPanelTest.actions(saved, new int[]{ViewerCanvas.DEFAULT_BACKGROUND}));
        assertSame(scene, panel.model());

        // A click on the blue box picks it; Delete takes it out of the model, and Save saves the rest
        panel.picked(15);
        panel.removePicked();
        assertEquals(14, panel.model().faceCount());
        assertEquals(GREEN, panel.model().color[13]);
        panel.getActionMap().get("save").actionPerformed(null);
        assertEquals(List.of("14 OBJ"), saved);

        // Clicking a piece again lets it go; clicking nothing does too
        panel.picked(0);
        panel.picked(0);
        panel.removePicked();
        assertEquals(14, panel.model().faceCount());
        panel.picked(0);
        panel.picked(-1);
        panel.removePicked();
        assertEquals(14, panel.model().faceCount());

        // The last piece left can't go
        panel.picked(0);
        panel.removePicked();
        assertEquals(2, panel.model().faceCount());
        panel.picked(0);
        panel.removePicked();
        assertEquals(2, panel.model().faceCount());

        // Bring back puts every piece back
        panel.restoreAll();
        assertSame(scene, panel.model());
    }
}
