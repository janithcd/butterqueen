package lk.jiat.butterqueen.ui;

/** Small pure helper for the 5-second account deletion gesture. */
public final class HoldToDeletePolicy {
    public static final long REQUIRED_MILLIS = 5_000L;

    private HoldToDeletePolicy() {}

    public static boolean completed(long elapsedMillis) {
        return elapsedMillis >= REQUIRED_MILLIS;
    }

    public static int progress(long elapsedMillis) {
        long clamped = Math.max(0L, Math.min(REQUIRED_MILLIS, elapsedMillis));
        return (int) (clamped * 100L / REQUIRED_MILLIS);
    }

    public static int remainingSeconds(long elapsedMillis) {
        long remaining = Math.max(0L, REQUIRED_MILLIS - Math.max(0L, elapsedMillis));
        return (int) ((remaining + 999L) / 1000L);
    }
}
