package lk.jiat.butterqueen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import lk.jiat.butterqueen.ui.HoldToDeletePolicy;

public class HoldToDeletePolicyTest {
    @Test
    public void requiresFiveFullSeconds() {
        assertFalse(HoldToDeletePolicy.completed(0));
        assertFalse(HoldToDeletePolicy.completed(4_999L));
        assertTrue(HoldToDeletePolicy.completed(5_000L));
        assertTrue(HoldToDeletePolicy.completed(5_200L));
    }

    @Test
    public void progressIsClampedForSafety() {
        assertEquals(0, HoldToDeletePolicy.progress(-300L));
        assertEquals(0, HoldToDeletePolicy.progress(0));
        assertEquals(25, HoldToDeletePolicy.progress(1_250L));
        assertEquals(50, HoldToDeletePolicy.progress(2_500L));
        assertEquals(99, HoldToDeletePolicy.progress(4_999L));
        assertEquals(100, HoldToDeletePolicy.progress(5_000L));
        assertEquals(100, HoldToDeletePolicy.progress(9_000L));
    }

    @Test
    public void countdownRoundsUpUntilHoldIsComplete() {
        assertEquals(5, HoldToDeletePolicy.remainingSeconds(0));
        assertEquals(5, HoldToDeletePolicy.remainingSeconds(100L));
        assertEquals(4, HoldToDeletePolicy.remainingSeconds(1_000L));
        assertEquals(1, HoldToDeletePolicy.remainingSeconds(4_999L));
        assertEquals(0, HoldToDeletePolicy.remainingSeconds(5_000L));
        assertEquals(0, HoldToDeletePolicy.remainingSeconds(10_000L));
    }
}
