package lk.jiat.butterqueen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import lk.jiat.butterqueen.data.OrderReceipt;

public class OrderReceiptTest {
    @Test
    public void payHerePendingReceiptNeverClaimsPayment() {
        OrderReceipt record = new OrderReceipt();
        record.paymentMethod = "payhere";
        record.paymentStatus = "verification_pending";

        assertTrue(record.paymentNotice().contains("not proof of payment"));
        assertFalse(record.paymentNotice().contains("recorded as paid"));
    }

    @Test
    public void sandboxDemoReceiptNeverClaimsRealPayment() {
        OrderReceipt record = new OrderReceipt();
        record.paymentMethod = "payhere";
        record.paymentStatus = "sandbox_demo";

        assertTrue(record.paymentNotice().contains("No real payment"));
        assertTrue(record.paymentState().contains("not verified"));
    }

    @Test
    public void cashOnDeliveryReceiptStatesPaymentDue() {
        OrderReceipt record = new OrderReceipt();
        record.paymentMethod = "cash_on_delivery";
        record.paymentStatus = "pending";

        assertTrue(record.paymentNotice().contains("due on delivery"));
    }

    @Test
    public void recordedPaidStatusDoesNotClaimIndependentVerification() {
        OrderReceipt record = new OrderReceipt();
        record.paymentMethod = "payhere";
        record.paymentStatus = "paid";

        assertTrue(record.paymentNotice().contains("not proof of payment"));
    }
}
