package lk.jiat.butterqueen.data;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import lk.jiat.butterqueen.model.OrderItem;
import lk.jiat.butterqueen.model.UnitPricing;

/** Snapshot of a customer's order. A pending order record is not proof of payment. */
public final class OrderReceipt {
    public String id;
    public long createdAtMillis;
    public String customerName;
    public String customerPhone;
    public String deliveryAddress;
    public String note;
    public String paymentMethod;
    public String paymentStatus;
    public String orderStatus;
    public String paymentReference;
    public double subtotal;
    public double deliveryFee;
    public double total;
    public final List<OrderItem> items = new ArrayList<>();

    public String date() {
        return createdAtMillis <= 0 ? "Not recorded"
                : new SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
                        .format(new Date(createdAtMillis));
    }

    public String paymentLabel() {
        if ("cash_on_delivery".equalsIgnoreCase(paymentMethod)) return "Cash on delivery";
        if ("payhere".equalsIgnoreCase(paymentMethod)) return "PayHere sandbox";
        return value(paymentMethod);
    }

    public String paymentNotice() {
        if ("sandbox_demo".equalsIgnoreCase(paymentStatus))
            return "University demo only: PayHere sandbox SDK reported success. " +
                    "No real payment was collected or independently verified. " +
                    "This PDF is not proof of payment.";
        if ("paid".equalsIgnoreCase(paymentStatus))
            return "The order record lists payment as paid. Confirm with ButterQueen; this PDF is not proof of payment.";
        if ("payhere".equalsIgnoreCase(paymentMethod))
            return "Sandbox payment awaits independent verification. This order record is not proof of payment.";
        if ("cash_on_delivery".equalsIgnoreCase(paymentMethod))
            return "Cash is due on delivery. This order record is not proof of payment.";
        return "Payment has not been confirmed. This order record is not proof of payment.";
    }

    public String paymentState() {
        if ("sandbox_demo".equalsIgnoreCase(paymentStatus)) {
            return "Sandbox demo (not verified)";
        }
        return value(paymentStatus).replace('_', ' ');
    }

    public String orderState() {
        return value(orderStatus).replace('_', ' ');
    }

    public static String money(double amount) {
        return String.format(Locale.getDefault(), "Rs. %.2f", UnitPricing.roundMoney(amount));
    }

    public static String value(String text) {
        return text == null || text.trim().isEmpty() ? "Not recorded" : text.trim();
    }
}
