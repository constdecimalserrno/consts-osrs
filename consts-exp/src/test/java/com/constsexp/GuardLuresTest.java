package com.constsexp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
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

    private static NPC npc(int id, WorldPoint at, int orientation)
    {
        NPC npc = mock(NPC.class);
        when(npc.getId()).thenReturn(id);
        when(npc.getWorldLocation()).thenReturn(at);
        when(npc.getCurrentOrientation()).thenReturn(orientation);
        return npc;
    }

    @Test
    public void outsidePortPiscariliusThereIsNothingToShow()
    {
        List<NPC> lured = List.of(npc(6980, EAST, 0), npc(6980, SOUTH_EAST, 512));
        assertNull(GuardLures.check(new WorldPoint(3222, 3218, 0), lured));
        assertNull(GuardLures.check(new WorldPoint(1738, 3740, 0), lured));
    }

    @Test
    public void eachGuardIsLuredOnlyOnItsPostFacingItsWay()
    {
        assertArrayEquals(new boolean[]{true, true},
            GuardLures.check(IN_PISC, List.of(npc(6980, EAST, 0), npc(6980, SOUTH_EAST, 512))));
        // Facing the other's way.
        assertArrayEquals(new boolean[]{false, false},
            GuardLures.check(IN_PISC, List.of(npc(6980, EAST, 512), npc(6980, SOUTH_EAST, 0))));
        // Off its post, or another patrol on it.
        assertArrayEquals(new boolean[]{false, true},
            GuardLures.check(IN_PISC, List.of(npc(6980, new WorldPoint(1777, 3745, 0), 0), npc(6979, EAST, 0),
                npc(6980, SOUTH_EAST, 512))));
        assertArrayEquals(new boolean[]{false, false}, GuardLures.check(IN_PISC, List.of()));
    }
}
