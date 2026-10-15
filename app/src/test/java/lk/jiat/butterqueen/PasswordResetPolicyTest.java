package lk.jiat.butterqueen;

import org.junit.Test;
import static org.junit.Assert.*;

import lk.jiat.butterqueen.data.PasswordResetPolicy;

public class PasswordResetPolicyTest {

    @Test
    public void normalizeEmailTrimsWhitespaceButPreservesCharacters() {
        assertEquals("person@example.com", PasswordResetPolicy.normalizeEmail(
                "  person@example.com  "));
        assertEquals("", PasswordResetPolicy.normalizeEmail(null));
    }

    @Test
    public void retryCooldownPreventsAccidentalRepeatedRequests() {
        long last = 1700000000000L;
        assertFalse(PasswordResetPolicy.canSend(last + 3000, last));
        assertEquals(57, PasswordResetPolicy.secondsUntilNext(last + 3000, last));
        assertTrue(PasswordResetPolicy.canSend(last + 60000, last));
        assertEquals(0, PasswordResetPolicy.secondsUntilNext(last + 60000, last));
    }

    @Test
    public void firstRequestAndClockResetArePermitted() {
        assertTrue(PasswordResetPolicy.canSend(1000, 0));
        assertTrue(PasswordResetPolicy.canSend(2000, 10000));
    }
}
