package lk.jiat.butterqueen.payment;

/**
 * University viva only. "sandbox_demo" means the Android PayHere sandbox SDK
 * returned success, NOT that a payment was independently verified.
 *
 * Production payment processing must continue to use a trusted backend.
 */
public final class SandboxOrderPolicy {
    public static final String DEMO_PAYMENT_STATUS = "sandbox_demo";

    private SandboxOrderPolicy() { }

    public static boolean canManageOrder(String method, String paymentStatus,
                                         boolean debugBuild) {
        if (!"payhere".equals(method)) return true;
        if ("paid".equals(paymentStatus)) return true;
        return debugBuild && DEMO_PAYMENT_STATUS.equals(paymentStatus);
    }

    public static boolean canConvertOlderPendingOrder(String method, String paymentStatus,
                                                       String orderStatus, boolean debugBuild) {
        return debugBuild && "payhere".equals(method) &&
                "verification_pending".equals(paymentStatus) &&
                "payment_pending".equals(orderStatus);
    }
}
