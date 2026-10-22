package com.constsexp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConstsExpStatusOverlayTest
{
    @Test
    public void formatClockShouldRenderMinutesAndSeconds()
    {
        assertEquals("0:00", ConstsExpStatusOverlay.formatClock(0L));
        assertEquals("0:01", ConstsExpStatusOverlay.formatClock(1000L));
        assertEquals("2:34", ConstsExpStatusOverlay.formatClock(154000L));
        assertEquals("10:00", ConstsExpStatusOverlay.formatClock(600000L));
    }

    @Test
    public void aggroBarShouldMarkAnUnsettledAreaWithAQuestionMark()
    {
        assertEquals("9:41", ConstsExpStatusOverlay.aggroValueText(581_000L, true, true));
        assertEquals("? 9:41", ConstsExpStatusOverlay.aggroValueText(581_000L, false, true));
        assertEquals("?", ConstsExpStatusOverlay.aggroValueText(0L, false, false));
    }

    @Test
    public void overlappingAlertsShouldFlashOutOfPhase()
    {
        long interval = 300L;
        // At the end of Health's bright half, alerts in later slots have already dimmed.
        boolean anyDim = false;
        for (AlertType type : AlertType.values())
        {
            anyDim |= !type.isFlashPhaseOn(interval - 1L, interval);
        }
        assertTrue(AlertType.HEALTH.isFlashPhaseOn(interval - 1L, interval));
        assertTrue(anyDim);
        assertFalse(AlertType.HEALTH.isFlashPhaseOn(interval, interval));
    }
}
