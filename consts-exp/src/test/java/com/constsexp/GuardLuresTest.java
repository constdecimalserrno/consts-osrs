package com.constsexp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class GuardLuresTest
{
    private static final WorldPoint IN_PISC = new WorldPoint(1790, 3740, 0);
    private static final WorldPoint EAST = new WorldPoint(1777, 3746, 0);
    private static final WorldPoint SOUTH_EAST = new WorldPoint(1780, 3731, 0);

    private final GuardLures guards = new GuardLures();

    static NPC npc(int id, WorldPoint at, int orientation)
    {
        NPC npc = mock(NPC.class);
        when(npc.getId()).thenReturn(id);
        when(npc.getWorldLocation()).thenReturn(at);
        when(npc.getCurrentOrientation()).thenReturn(orientation);
        return npc;
    }

    private void assertLured(boolean east, boolean southEast)
    {
        assertTrue(guards.isEastLured() == east && guards.isSouthEastLured() == southEast);
    }

    @Test
    public void outsidePortPiscariliusItReadsNothing()
    {
        List<NPC> lured = List.of(npc(6980, EAST, 0), npc(6980, SOUTH_EAST, 512));
        assertFalse(guards.update(new WorldPoint(3222, 3218, 0), lured));
        assertFalse(guards.update(new WorldPoint(1738, 3740, 0), lured));
        assertLured(false, false);
        // Upstairs in a target house still counts.
        assertTrue(guards.update(new WorldPoint(1790, 3740, 1), lured));
    }

    @Test
    public void eachGuardIsLuredOnItsPostFacingItsWayAndKeepsItOutOfSight()
    {
        assertTrue(guards.update(IN_PISC, List.of(npc(6980, EAST, 0), npc(6980, SOUTH_EAST, 512))));
        assertLured(true, true);

        // Out of sight, off its post, or another patrol on it: each keeps its last answer.
        guards.update(IN_PISC, List.of());
        guards.update(IN_PISC, List.of(npc(6980, new WorldPoint(1777, 3745, 0), 512), npc(6979, EAST, 512)));
        assertLured(true, true);

        // Seen on its post facing the other way.
        guards.update(IN_PISC, List.of(npc(6980, EAST, 512), npc(6980, SOUTH_EAST, 0)));
        assertLured(false, false);

        guards.update(IN_PISC, List.of(npc(6980, EAST, 0)));
        guards.clear();
        assertLured(false, false);
    }
}
