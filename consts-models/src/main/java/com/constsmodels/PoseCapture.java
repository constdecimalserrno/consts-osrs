package com.constsmodels;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import net.runelite.api.Actor;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.Model;

/**
 * An actor's pose animations (its idle, turns, walks and run) as {@link Clip}s, stepped one exact frame at a time:
 * each goes in the actor's pose slot, and its model is copied at frame 0, 1, 2 and so on. Then the actor is put back
 * as it was. It all happens in one go on the client thread, so the game draws none of it.
 *
 * <p>Animation Smoothing is held off while it runs. With it on, the game blends the frame an actor's model shows with
 * the next, by how far into the frame the actor is, so a frame set here would come out part-way to the next one.
 */
final class PoseCapture
{
    /** The most frames a clip keeps. A longer animation keeps every 2nd, 3rd... frame, its timing kept whole. */
    // ponytail: every nth frame past the cap, not sparse glTF targets; a 1,200-tick maya clip stays under ~4 MB
    static final int MOST_FRAMES = 240;

    private static final String[] LABELS = {"Idle", "Turn left", "Turn right", "Walk", "Walk back", "Sidestep left",
        "Sidestep right", "Run"};

    private PoseCapture()
    {
    }

    /**
     * The actor's pose animations, each one once, for a mesh of this many corners; none while the actor is busy with
     * an action, which changes what its model shows. A clip whose model doesn't have that many corners is left out.
     * Call it on the client thread.
     */
    static List<Clip> capture(Client client, Actor actor, int corners)
    {
        List<Clip> clips = new ArrayList<>();
        if (actor.getAnimation() != -1)
        {
            return clips;
        }
        int[] ids = {actor.getIdlePoseAnimation(), actor.getIdleRotateLeft(), actor.getIdleRotateRight(),
            actor.getWalkAnimation(), actor.getWalkRotate180(), actor.getWalkRotateLeft(), actor.getWalkRotateRight(),
            actor.getRunAnimation()};

        int pose = actor.getPoseAnimation();
        int poseFrame = actor.getPoseAnimationFrame();
        IntPredicate smoothing = client.getAnimationInterpolationFilter();
        try
        {
            client.setAnimationInterpolationFilter(null);
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < ids.length; i++)
            {
                if (ids[i] < 0 || !seen.add(ids[i]))
                {
                    continue;
                }
                Animation animation = client.loadAnimation(ids[i]);
                Clip clip = animation == null ? null : clip(actor, ids[i], animation, LABELS[i], corners);
                if (clip != null)
                {
                    clips.add(clip);
                }
            }
        }
        finally
        {
            actor.setPoseAnimation(pose);
            actor.setPoseAnimationFrame(poseFrame);
            client.setAnimationInterpolationFilter(smoothing);
        }
        return clips;
    }

    private static Clip clip(Actor actor, int id, Animation animation, String label, int corners)
    {
        // A classic animation's frames are keyframes, each some ticks long; a maya animation's are its ticks
        int frames = animation.getNumFrames();
        int[] lengths = animation.isMayaAnim() ? null : animation.getFrameLengths();
        if (frames <= 0 || (lengths != null && lengths.length < frames))
        {
            return null;
        }
        int every = (frames + MOST_FRAMES - 1) / MOST_FRAMES;
        int kept = (frames + every - 1) / every;
        int[] ticks = new int[kept];
        float[][] x = new float[kept][];
        float[][] y = new float[kept][];
        float[][] z = new float[kept][];

        actor.setPoseAnimation(id);
        for (int k = 0; k < kept; k++)
        {
            int first = k * every;
            actor.setPoseAnimationFrame(first);
            Model model = actor.getModel();
            if (model == null || model.getVerticesCount() != corners)
            {
                return null;
            }
            // The game's x east, y down, z north into the mesh's x east, y north, z up, as ModelCapture turns them
            float[] vx = model.getVerticesX();
            float[] vy = model.getVerticesY();
            float[] vz = model.getVerticesZ();
            x[k] = new float[corners];
            y[k] = new float[corners];
            z[k] = new float[corners];
            for (int v = 0; v < corners; v++)
            {
                x[k][v] = vx[v];
                y[k][v] = vz[v];
                z[k][v] = -vy[v];
            }
            for (int f = first; f < Math.min(first + every, frames); f++)
            {
                ticks[k] += lengths == null ? 1 : Math.max(1, lengths[f]);
            }
        }

        // The game loops by going back the animation's frame step from its end, or to the start when that's past it
        int step = animation.getFrameStep();
        int loopFrom = step > 0 && step <= frames ? (frames - step) / every : 0;
        return new Clip(label, id, ticks, loopFrom, x, y, z);
    }
}
