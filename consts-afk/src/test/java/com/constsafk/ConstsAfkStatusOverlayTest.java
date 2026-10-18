package com.constsafk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConstsAfkStatusOverlayTest
{
    @Test
    public void formatClockShouldRenderMinutesAndSeconds()
    {
        assertEquals("0:00", ConstsAfkStatusOverlay.formatClock(0L));
        assertEquals("0:01", ConstsAfkStatusOverlay.formatClock(1000L));
        assertEquals("2:34", ConstsAfkStatusOverlay.formatClock(154000L));
        assertEquals("10:00", ConstsAfkStatusOverlay.formatClock(600000L));
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
