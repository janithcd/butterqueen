package lk.jiat.butterqueen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import lk.jiat.butterqueen.payment.SandboxOrderPolicy;

public class SandboxOrderPolicyTest {

    @Test
    public void normalCashOrdersMayBeManaged() {
        assertTrue(SandboxOrderPolicy.canManageOrder("cash_on_delivery", "pending", true));
        assertTrue(SandboxOrderPolicy.canManageOrder("cash_on_delivery", "pending", false));
    }

    @Test
    public void sandboxDemoIsAllowedOnlyInDebugBuilds() {
        assertTrue(SandboxOrderPolicy.canManageOrder("payhere", "sandbox_demo", true));
        assertFalse(SandboxOrderPolicy.canManageOrder("payhere", "sandbox_demo", false));
    }

    @Test
    public void backendVerifiedPaymentsRemainSupported() {
        assertTrue(SandboxOrderPolicy.canManageOrder("payhere", "paid", true));
        assertTrue(SandboxOrderPolicy.canManageOrder("payhere", "paid", false));
    }

    @Test
    public void unverifiedPaymentsAreNeverAutomaticallyManageable() {
        assertFalse(SandboxOrderPolicy.canManageOrder("payhere", "verification_pending", true));
        assertFalse(SandboxOrderPolicy.canManageOrder("payhere", "verification_pending", false));
        assertFalse(SandboxOrderPolicy.canManageOrder("payhere", "failed", true));
    }

    @Test
    public void onlyLegacyPendingSandboxOrdersMayBeConvertedToDemo() {
        assertTrue(SandboxOrderPolicy.canConvertOlderPendingOrder(
                "payhere", "verification_pending", "payment_pending", true));
        assertFalse(SandboxOrderPolicy.canConvertOlderPendingOrder(
                "payhere", "verification_pending", "payment_pending", false));
        assertFalse(SandboxOrderPolicy.canConvertOlderPendingOrder(
                "payhere", "paid", "placed", true));
        assertFalse(SandboxOrderPolicy.canConvertOlderPendingOrder(
                "payhere", "verification_pending", "cancelled", true));
        assertFalse(SandboxOrderPolicy.canConvertOlderPendingOrder(
                "cash_on_delivery", "verification_pending", "payment_pending", true));
    }
}
