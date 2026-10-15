package lk.jiat.butterqueen.data;

/** Minimal, testable client-side retry policy; Firebase applies server quotas too. */
public final class PasswordResetPolicy {
    public static final long RESEND_WAIT_MILLIS = 60_000L;

    private PasswordResetPolicy() {}

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim();
    }

    public static boolean canSend(long nowMillis, long lastSuccessMillis) {
        return lastSuccessMillis <= 0 || nowMillis < lastSuccessMillis
                || nowMillis - lastSuccessMillis >= RESEND_WAIT_MILLIS;
    }

    public static int secondsUntilNext(long nowMillis, long lastSuccessMillis) {
        if (canSend(nowMillis, lastSuccessMillis)) return 0;
        long remaining = RESEND_WAIT_MILLIS - (nowMillis - lastSuccessMillis);
        return (int) ((remaining + 999L) / 1000L);
    }
}
