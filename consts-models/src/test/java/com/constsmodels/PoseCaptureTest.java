package com.constsmodels;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.IterableHashTable;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import org.junit.Before;
import org.junit.Test;

public class PoseCaptureTest
{
    private static final int IDLE = 808;
    private static final int WALK = 819;
    private static final int RUN = 824;

    private final Client client = mock(Client.class);
    private final NPC actor = mock(NPC.class);
    private final IntPredicate smoothing = id -> true;

    // The actor's pose slot and frame as the fake game has them, and what the smoothing filter was at each model
    private int pose = WALK;
    private int poseFrame = 3;
    private IntPredicate filter = smoothing;
    private final List<IntPredicate> filterAtEachModel = new ArrayList<>();
    private int corners = 2;

    @Before
    public void game()
    {
        when(actor.getAnimation()).thenReturn(-1);
        when(actor.getIdlePoseAnimation()).thenReturn(IDLE);
        when(actor.getIdleRotateLeft()).thenReturn(-1);
        when(actor.getIdleRotateRight()).thenReturn(-1);
        when(actor.getWalkAnimation()).thenReturn(WALK);
        when(actor.getWalkRotate180()).thenReturn(WALK);
        when(actor.getWalkRotateLeft()).thenReturn(-1);
        when(actor.getWalkRotateRight()).thenReturn(-1);
        when(actor.getRunAnimation()).thenReturn(RUN);
        when(actor.getPoseAnimation()).thenAnswer(i -> pose);
        when(actor.getPoseAnimationFrame()).thenAnswer(i -> poseFrame);
        doAnswer(i -> pose = i.getArgument(0)).when(actor).setPoseAnimation(anyInt());
        doAnswer(i -> poseFrame = i.getArgument(0)).when(actor).setPoseAnimationFrame(anyInt());
        when(client.getAnimationInterpolationFilter()).thenAnswer(i -> filter);
        doAnswer(i -> filter = i.getArgument(0)).when(client).setAnimationInterpolationFilter(any());
        // The model as the game would draw it: corner v of pose p at frame f sits at x = p + f, y = -v (down), z = 10 v
        when(actor.getModel()).thenAnswer(i ->
        {
            filterAtEachModel.add(filter);
            Model model = mock(Model.class);
            when(model.getVerticesCount()).thenReturn(corners);
            float[] x = new float[corners + 1];
            float[] y = new float[corners + 1];
            float[] z = new float[corners + 1];
            for (int v = 0; v < corners; v++)
            {
                x[v] = pose + poseFrame;
                y[v] = -v;
                z[v] = 10 * v;
            }
            when(model.getVerticesX()).thenReturn(x);
            when(model.getVerticesY()).thenReturn(y);
            when(model.getVerticesZ()).thenReturn(z);
            return model;
        });
        Animation idle = classic(IDLE, new int[]{21, 21, 21}, 3);
        when(client.loadAnimation(IDLE)).thenReturn(idle);
        Animation walk = classic(WALK, new int[]{6, 0}, 99);
        when(client.loadAnimation(WALK)).thenReturn(walk);
        Animation run = mock(Animation.class);
        when(run.getId()).thenReturn(RUN);
        when(run.isMayaAnim()).thenReturn(true);
        when(run.getNumFrames()).thenReturn(5);
        when(run.getFrameStep()).thenReturn(2);
        when(client.loadAnimation(RUN)).thenReturn(run);
    }

    private static Animation classic(int id, int[] lengths, int step)
    {
        Animation animation = mock(Animation.class);
        when(animation.getId()).thenReturn(id);
        when(animation.getNumFrames()).thenReturn(lengths.length);
        when(animation.getFrameLengths()).thenReturn(lengths);
        when(animation.getFrameStep()).thenReturn(step);
        return animation;
    }

    @Test
    public void stepsEachPoseFrameByFrameWithSmoothingHeldOffThenPutsTheActorBack()
    {
        List<Clip> clips = PoseCapture.capture(client, actor, 2);

        // Walk back is the walk again, and the turns are -1: each animation once, in the pose set's order
        assertEquals(3, clips.size());
        Clip idle = clips.get(0);
        assertEquals("Idle", idle.label);
        assertEquals(IDLE, idle.animation);
        assertArrayEquals(new int[]{21, 21, 21}, idle.ticks);
        // Corners in the mesh's axes: x east, y north (the game's z), z up (the game's -y)
        assertArrayEquals(new float[]{IDLE, IDLE}, idle.x[0], 0f);
        assertArrayEquals(new float[]{IDLE + 2, IDLE + 2}, idle.x[2], 0f);
        assertArrayEquals(new float[]{0, 10}, idle.y[1], 0f);
        assertArrayEquals(new float[]{0, 1}, idle.z[1], 0f);
        // A step of the whole animation loops from the start
        assertEquals(0, idle.loopFrom);

        Clip walk = clips.get(1);
        assertEquals("Walk", walk.label);
        // A frame never lasts no time; a step past the end loops from the start
        assertArrayEquals(new int[]{6, 1}, walk.ticks);
        assertEquals(0, walk.loopFrom);

        Clip run = clips.get(2);
        assertEquals("Run", run.label);
        // A maya animation's frames are its ticks
        assertArrayEquals(new int[]{1, 1, 1, 1, 1}, run.ticks);
        assertEquals(3, run.loopFrom);

        for (IntPredicate during : filterAtEachModel)
        {
            assertNull("smoothing would blend each frame with the next", during);
        }
        assertEquals(WALK, pose);
        assertEquals(3, poseFrame);
        assertSame(smoothing, filter);
    }

    @Test
    public void aBusyActorGivesNoClips()
    {
        when(actor.getAnimation()).thenReturn(422);
        assertTrue(PoseCapture.capture(client, actor, 2).isEmpty());
        verify(actor, never()).setPoseAnimation(anyInt());
    }

    @Test
    public void anActorWithAGraphicOnItGivesNoClips()
    {
        // A graphic is part of the actor's model, and would stay frozen in every frame
        IterableHashTable<ActorSpotAnim> graphics = mock(IterableHashTable.class);
        when(graphics.iterator()).thenReturn(List.of(mock(ActorSpotAnim.class)).iterator());
        when(actor.getSpotAnims()).thenReturn(graphics);
        assertTrue(PoseCapture.capture(client, actor, 2).isEmpty());
        verify(actor, never()).setPoseAnimation(anyInt());
    }

    @Test
    public void allTheClipsTogetherKeepAtMostSoManyFrames()
    {
        for (int id : new int[]{IDLE, WALK, RUN})
        {
            Animation maya = mock(Animation.class);
            when(maya.getId()).thenReturn(id);
            when(maya.isMayaAnim()).thenReturn(true);
            when(maya.getNumFrames()).thenReturn(PoseCapture.MOST_FRAMES);
            when(client.loadAnimation(id)).thenReturn(maya);
        }
        List<Clip> clips = PoseCapture.capture(client, actor, 2);
        int frames = 0;
        for (Clip clip : clips)
        {
            frames += clip.frameCount();
        }
        assertTrue(frames + " frames", frames <= PoseCapture.MOST_FRAMES_IN_ALL);
        // The first clips keep theirs whole; the last makes do with what's left, or is left out
        assertEquals(PoseCapture.MOST_FRAMES, clips.get(0).frameCount());
    }

    @Test
    public void aClipWhoseModelChangesShapeIsLeftOut()
    {
        corners = 3;
        assertTrue(PoseCapture.capture(client, actor, 2).isEmpty());
        assertEquals(WALK, pose);
        assertSame(smoothing, filter);
    }

    @Test
    public void theActorGoesBackEvenWhenTheGameThrows()
    {
        when(client.loadAnimation(WALK)).thenThrow(new IllegalStateException("no"));
        try
        {
            PoseCapture.capture(client, actor, 2);
        }
        catch (IllegalStateException expected)
        {
            // what matters is that it's put back
        }
        assertEquals(WALK, pose);
        assertEquals(3, poseFrame);
        assertSame(smoothing, filter);
    }

    @Test
    public void aLongAnimationKeepsEveryNthFrameAndItsTiming()
    {
        Animation long_ = mock(Animation.class);
        when(long_.getId()).thenReturn(IDLE);
        when(long_.isMayaAnim()).thenReturn(true);
        when(long_.getNumFrames()).thenReturn(PoseCapture.MOST_FRAMES * 2 + 1);
        when(long_.getFrameStep()).thenReturn(-1);
        when(client.loadAnimation(IDLE)).thenReturn(long_);

        Clip idle = PoseCapture.capture(client, actor, 2).get(0);
        // Every 3rd tick: 161 frames, the last one tick long, the same 481 ticks in all
        assertEquals(PoseCapture.MOST_FRAMES * 2 / 3 + 1, idle.frameCount());
        assertEquals(3, idle.ticks[0]);
        assertEquals(1, idle.ticks[idle.frameCount() - 1]);
        int total = 0;
        for (int t : idle.ticks)
        {
            total += t;
        }
        assertEquals(PoseCapture.MOST_FRAMES * 2 + 1, total);
        assertArrayEquals(new float[]{IDLE + 3, IDLE + 3}, idle.x[1], 0f);
    }
}
