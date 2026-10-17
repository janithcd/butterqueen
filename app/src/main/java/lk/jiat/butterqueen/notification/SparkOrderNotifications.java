package lk.jiat.butterqueen.notification;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Spark-plan order inbox derived from Firestore orders. No backend writes or
 * Cloud Functions needed. Read markers are scoped to device + signed-in UID.
 * Status history is not persisted: the current status and order creation are
 * represented as notices; former statuses are not retroactively listed.
 */
public final class SparkOrderNotifications {
    private static final String PREFS = "bq_order_notices_";
    private SparkOrderNotifications() {}

    public static final class Notice {
        public final String key, orderId, type, title, body;
        public final long atMillis;

        Notice(String key, String orderId, String type, String title, String body,
               long atMillis) {
            this.key = key;
            this.orderId = orderId;
            this.type = type;
            this.title = title;
            this.body = body;
            this.atMillis = atMillis;
        }
    }

    public interface Listener {
        void onNotices(List<Notice> notices);
        void onError(Exception error);
    }

    private static SharedPreferences prefs(Context context, String uid) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS + uid, Context.MODE_PRIVATE);
    }

    public static boolean isRead(Context context, String uid, Notice notice) {
        return prefs(context, uid).getBoolean(notice.key, false);
    }

    public static int unread(Context context, String uid, List<Notice> notices) {
        int total = 0;
        for (Notice notice : notices) if (!isRead(context, uid, notice)) total++;
        return total;
    }

    public static void markRead(Context context, String uid, List<Notice> notices) {
        SharedPreferences.Editor editor = prefs(context, uid).edit();
        for (Notice notice : notices) editor.putBoolean(notice.key, true);
        editor.apply();
    }

    public static SharedPreferences getReadPreferences(Context context, String uid) {
        return prefs(context, uid);
    }

    public static ListenerRegistration watch(String uid, boolean admin, Listener listener) {
        if (uid == null || uid.isEmpty()) throw new IllegalArgumentException("Missing UID");
        Query query = FirebaseFirestore.getInstance().collection("orders");
        if (!admin) query = query.whereEqualTo("userId", uid);
        // Avoid a composite Firestore index for whereEqualTo + orderBy.
        return query.limit(100).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                listener.onError(error);
                return;
            }
            if (snapshot == null) return;
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null || !uid.equals(auth.getCurrentUser().getUid())) {
                return;
            }
            List<Notice> notices = new ArrayList<>();
            for (DocumentSnapshot order : snapshot) {
                if (!admin && !uid.equals(order.getString("userId"))) continue;
                String id = order.getId();
                Long millis = order.getLong("createdAtMillis");
                long at = millis == null ? 0L : millis;
                String shortId = id.substring(0, Math.min(8, id.length()));
                String status = order.getString("orderStatus");
                if (status == null) status = "placed";
                String label = status.replace('_', ' ');
                if (admin) {
                    notices.add(new Notice("admin_order_" + id, id, "new_order",
                            "Customer order #" + shortId,
                            "A customer order is ready to manage. Tap to view it.", at));
                } else {
                    notices.add(new Notice("customer_order_" + id, id, "order_created",
                            "Order #" + shortId + " received",
                            "Your bakery order was submitted. Tap for the receipt.", at));
                    if (!"placed".equals(status) && !"payment_pending".equals(status)) {
                        notices.add(new Notice("customer_status_" + id + "_" + status,
                                id, "order_status",
                                "Order #" + shortId + " update",
                                "Your order is now " + label + ".", at + 1));
                    }
                }
            }
            Collections.sort(notices, (left, right) ->
                    Long.compare(right.atMillis, left.atMillis));
            listener.onNotices(notices);
        });
    }
}
