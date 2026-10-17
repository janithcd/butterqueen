package lk.jiat.butterqueen.activity;

import android.content.Intent;
import android.Manifest;
import android.os.Build;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import lk.jiat.butterqueen.notification.SparkCakeNotifications;
import lk.jiat.butterqueen.notification.SparkOrderNotifications;
import java.util.List;
import java.util.Locale;

import lk.jiat.butterqueen.R;

/** Per-user notification inbox. Opening it marks unread records as seen. */
public final class NotificationInboxActivity extends AppCompatActivity {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration listener;
    private ListenerRegistration cakeListener;
    private ListenerRegistration orderListener;
    private List<DocumentSnapshot> cloudDocs = new ArrayList<>();
    private List<SparkCakeNotifications.Notice> cakeDocs = new ArrayList<>();
    private List<SparkOrderNotifications.Notice> orderDocs = new ArrayList<>();
    private LinearLayout content;
    private String uid;
    private boolean marking;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        // Ask only after the user deliberately opens Notifications.
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 721);
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.md_theme_background));
        root.setPadding(dp(18), 0, dp(18), dp(12));
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(dp(18) + bars.left, bars.top, dp(18) + bars.right, dp(12) + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        TextView brand = text("B U T T E R   Q U E E N   /   UPDATES", 11, true);
        brand.setTextColor(color(R.color.md_theme_primary));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.topMargin = dp(15);
        root.addView(brand, bp);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(76)));
        MaterialButton back = new MaterialButton(this);
        back.setText("‹ Back");
        back.setAllCaps(false);
        back.setCornerRadius(dp(16));
        back.setTextColor(color(R.color.md_theme_primary));
        back.setBackgroundTintList(ColorStateList.valueOf(
                color(R.color.md_theme_surfaceContainerLow)));
        LinearLayout.LayoutParams backParams =
                new LinearLayout.LayoutParams(dp(92), dp(48));
        backParams.rightMargin = dp(12);
        header.addView(back, backParams);
        back.setOnClickListener(v -> finish());
        TextView title = text("Your activity", 26, true);
        header.addView(title);
        TextView subtitle = text("Fresh news, orders & lovely little updates.", 14, false);
        subtitle.setTextColor(color(R.color.md_theme_onSurfaceVariant));
        root.addView(subtitle);
        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams sc = new LinearLayout.LayoutParams(-1, 0, 1);
        sc.topMargin = dp(22);
        root.addView(scroll, sc);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!sameUser()) return;
        listener = db.collection("users").document(uid).collection("notifications")
                .orderBy("createdAtMillis", Query.Direction.DESCENDING).limit(100)
                .addSnapshotListener((value, error) -> {
                    if (!sameUser() || isFinishing() || isDestroyed()) return;
                    if (error != null) {
                        content.removeAllViews();
                        content.addView(text("Could not load notifications. Check connection and rules.",
                                14, false));
                        return;
                    }
                    if (value != null) {
                        cloudDocs = new ArrayList<>(value.getDocuments());
                        renderAll();
                    }
                });
        // Spark-compatible Firestore-derived messages; no unsafe client writes.
        db.collection("users").document(uid).get().addOnSuccessListener(profile -> {
            if (!sameUser() || isFinishing() || isDestroyed()) return;
            boolean admin = "admin".equals(profile.getString("role"));
            orderListener = SparkOrderNotifications.watch(uid, admin,
                    new SparkOrderNotifications.Listener() {
                        @Override
                        public void onNotices(List<SparkOrderNotifications.Notice> notices) {
                            if (!sameUser() || isFinishing() || isDestroyed()) return;
                            orderDocs = notices;
                            SparkOrderNotifications.markRead(NotificationInboxActivity.this,
                                    uid, notices);
                            renderAll();
                        }
                        @Override
                        public void onError(Exception error) {
                            if (!isFinishing() && !isDestroyed())
                                Toast.makeText(NotificationInboxActivity.this,
                                        "Order updates unavailable. Check connection.",
                                        Toast.LENGTH_SHORT).show();
                        }
                    });
            cakeListener = SparkCakeNotifications.watch(uid, admin,
                    new SparkCakeNotifications.Listener() {
                        @Override
                        public void onNotices(List<SparkCakeNotifications.Notice> notices) {
                            if (!sameUser() || isFinishing() || isDestroyed()) return;
                            cakeDocs = notices;
                            SparkCakeNotifications.markRead(NotificationInboxActivity.this, uid, notices);
                            renderAll();
                        }
                        @Override
                        public void onError(Exception error) {
                            if (!isFinishing() && !isDestroyed()) {
                                Toast.makeText(NotificationInboxActivity.this,
                                        "Cake updates unavailable. Check permissions.",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
        });
        markAllUnread();
    }

    @Override
    protected void onStop() {
        if (listener != null) listener.remove();
        if (cakeListener != null) cakeListener.remove();
        if (orderListener != null) orderListener.remove();
        listener = null;
        cakeListener = null;
        orderListener = null;
        super.onStop();
    }

    private boolean sameUser() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                && uid.equals(FirebaseAuth.getInstance().getCurrentUser().getUid());
    }

    private void markAllUnread() {
        if (marking || !sameUser()) return;
        marking = true;
        db.collection("users").document(uid).collection("notifications")
                .whereEqualTo("read", false).limit(300).get()
                .addOnSuccessListener(snapshot -> {
                    if (!sameUser()) { marking = false; return; }
                    if (snapshot.isEmpty()) { marking = false; return; }
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : snapshot) batch.update(doc.getReference(), "read", true);
                    batch.commit().addOnCompleteListener(result -> {
                        marking = false;
                        if (result.isSuccessful() && snapshot.size() == 300 && sameUser())
                            markAllUnread();
                    });
                })
                .addOnFailureListener(error -> {
                    marking = false;
                    if (!isFinishing()) Toast.makeText(this,
                            "Cannot mark notifications read", Toast.LENGTH_SHORT).show();
                });
    }

    private void renderAll() {
        content.removeAllViews();
        if (cloudDocs.isEmpty() && cakeDocs.isEmpty() && orderDocs.isEmpty()) {
            MaterialCardView empty = new MaterialCardView(this);
            empty.setRadius(dp(26));
            empty.setCardElevation(0);
            empty.setCardBackgroundColor(color(R.color.md_theme_surfaceContainerLow));
            empty.setStrokeWidth(dp(1));
            empty.setStrokeColor(color(R.color.md_theme_outlineVariant));
            content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout box = new LinearLayout(this);
            box.setPadding(dp(24), dp(42), dp(24), dp(42));
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER);
            empty.addView(box);
            TextView icon = text("♡", 42, false);
            icon.setTextColor(color(R.color.md_theme_primary));
            box.addView(icon);
            TextView heading = text("All caught up!", 21, true);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
            hp.topMargin = dp(12);
            box.addView(heading, hp);
            TextView note = text("New bakery updates will appear right here.", 14, false);
            note.setGravity(Gravity.CENTER);
            note.setTextColor(color(R.color.md_theme_onSurfaceVariant));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
            np.topMargin = dp(8);
            box.addView(note, np);
            return;
        }
        // Merge both sources without losing the original Firestore inbox.
        class Entry {
            String title, body, type;
            long timestamp;
            Runnable action;
            Entry(String title, String body, String type, long timestamp, Runnable action) {
                this.title = title; this.body = body; this.type = type;
                this.timestamp = timestamp; this.action = action;
            }
        }
        List<Entry> entries = new ArrayList<>();
        for (DocumentSnapshot doc : cloudDocs) {
            Long when = doc.getLong("createdAtMillis");
            entries.add(new Entry(doc.getString("title"), doc.getString("body"),
                    doc.getString("type"), when == null ? 0L : when,
                    () -> openTarget(doc)));
        }
        for (SparkCakeNotifications.Notice notice : cakeDocs) {
            entries.add(new Entry(notice.title, notice.body, notice.type, notice.atMillis,
                    () -> openCake(notice)));
        }
        for (SparkOrderNotifications.Notice notice : orderDocs) {
            entries.add(new Entry(notice.title, notice.body, notice.type, notice.atMillis,
                    () -> openOrder(notice)));
        }
        Collections.sort(entries, (left, right) -> Long.compare(right.timestamp, left.timestamp));
        int index = 0;
        for (Entry entry : entries) {
            MaterialCardView card = new MaterialCardView(this);
            card.setRadius(dp(22));
            card.setCardElevation(0);
            card.setCardBackgroundColor(color(R.color.md_theme_surfaceContainerLowest));
            card.setStrokeColor(color(R.color.md_theme_outlineVariant));
            card.setStrokeWidth(dp(1));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.bottomMargin = dp(12);
            content.addView(card, cp);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(dp(14), dp(16), dp(14), dp(16));
            card.addView(row);

            String symbol = "✦";
            if ("new_order".equals(entry.type) || "admin_new".equals(entry.type)
                    || "customer_sent".equals(entry.type)) symbol = "♡";
            if ("order_status".equals(entry.type) || "customer_status".equals(entry.type)
                    || "custom_status".equals(entry.type)) symbol = "✓";
            if ("new_product".equals(entry.type)) symbol = "★";
            TextView icon = text(symbol, 20, true);
            icon.setTextColor(color(R.color.md_theme_primary));
            icon.setGravity(Gravity.CENTER);
            GradientDrawable circle = new GradientDrawable();
            circle.setColor(color(R.color.md_theme_primaryContainer));
            circle.setCornerRadius(dp(17));
            icon.setBackground(circle);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(50), dp(50));
            ip.rightMargin = dp(13);
            row.addView(icon, ip);

            LinearLayout detailColumn = new LinearLayout(this);
            detailColumn.setOrientation(LinearLayout.VERTICAL);
            row.addView(detailColumn, new LinearLayout.LayoutParams(0, -2, 1));
            TextView heading = text(
                    entry.title == null ? "Butter Queen" : entry.title, 16, true);
            detailColumn.addView(heading);
            TextView detail = text(entry.body == null ? "" : entry.body, 13, false);
            detail.setTextColor(color(R.color.md_theme_onSurfaceVariant));
            LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(-1, -2);
            dp1.topMargin = dp(6);
            detailColumn.addView(detail, dp1);
            if (entry.timestamp > 0) {
                String formatted = new SimpleDateFormat("dd MMM yyyy  ·  h:mm a",
                        Locale.getDefault()).format(new Date(entry.timestamp));
                TextView date = text(formatted + "  →", 11, true);
                date.setTextColor(color(R.color.md_theme_primary));
                LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-1, -2);
                dateParams.topMargin = dp(12);
                detailColumn.addView(date, dateParams);
            }
            card.setClickable(true);
            card.setFocusable(true);
            card.setOnClickListener(v -> entry.action.run());
            card.setAlpha(0f);
            card.setTranslationY(dp(8));
            card.animate().alpha(1f).translationY(0)
                    .setDuration(170 + Math.min(index++, 5) * 35L).start();
        }
    }

    private void openOrder(SparkOrderNotifications.Notice notice) {
        if (!sameUser()) { finish(); return; }
        if ("new_order".equals(notice.type)) {
            startActivity(new Intent(this, AdminDashboardActivity.class)
                    .putExtra("open_orders", true));
        } else {
            startActivity(OrderReceiptActivity.intentFor(this, notice.orderId));
        }
    }

    private void openCake(SparkCakeNotifications.Notice notice) {
        if (!sameUser()) { finish(); return; }
        // Access to admin management remains protected by AdminAccess and rules.
        if ("admin_new".equals(notice.type)) {
            startActivity(new Intent(this, AdminCustomCakeRequestsActivity.class));
        } else {
            startActivity(new Intent(this, MyCustomCakeRequestsActivity.class));
        }
    }

    private void openTarget(DocumentSnapshot doc) {
        if (!sameUser()) { finish(); return; }
        String type = doc.getString("type");
        String ref = doc.getString("targetId");
        Intent target = null;
        switch (type == null ? "" : type) {
            case "new_order":
                target = new Intent(this, AdminDashboardActivity.class)
                        .putExtra("open_orders", true);
                break;
            case "new_product":
                if (ref != null && !ref.isEmpty())
                    target = new Intent(this, ProductDetailsActivity.class)
                            .putExtra("product_id", ref);
                break;
            case "order_status":
                if (ref != null && !ref.isEmpty())
                    target = OrderReceiptActivity.intentFor(this, ref);
                break;
            case "custom_request":
                target = new Intent(this, AdminCustomCakeRequestsActivity.class);
                break;
            case "custom_status":
                target = new Intent(this, MyCustomCakeRequestsActivity.class);
                break;
            default:
                break;
        }
        if (target != null) startActivity(target);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color(R.color.md_theme_onBackground));
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private int color(int id) { return ContextCompat.getColor(this, id); }
    private int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }
}
