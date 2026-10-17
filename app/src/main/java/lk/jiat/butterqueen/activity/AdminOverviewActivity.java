package lk.jiat.butterqueen.activity;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import lk.jiat.butterqueen.fragment.AdminProfileFragment;
import lk.jiat.butterqueen.notification.NotificationBellController;
import lk.jiat.butterqueen.notification.PushTokenRegistrar;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lk.jiat.butterqueen.R;
import lk.jiat.butterqueen.data.AdminAccess;

/**
 * Administrator-only overview. Firestore Security Rules remain the source of authorization.
 * All insights use existing orders; no demo or hardcoded numbers.
 */
public class AdminOverviewActivity extends AppCompatActivity {
    private static final String[] STATUSES = {
            "placed", "preparing", "out_for_delivery", "delivered",
            "cancelled", "payment_pending", "other"
    };
    private static final String[] LABELS = {
            "Placed", "Preparing", "On the way", "Delivered",
            "Cancelled", "Payment pending", "Other"
    };
    private static final int[] COLORS = {
            0xFF8B5CF6, 0xFFF59E0B, 0xFF06B6D4, 0xFF10B981,
            0xFFF43F5E, 0xFF64748B, 0xFFB45309
    };

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private TextView sync, totalText, activeText, deliveredText, valueText;
    private LinearLayout legend, monthRows, recentRows;
    private DonutView donut;
    private boolean authorized;
    private boolean loading;
    private NestedScrollView dashboardScroll;
    private FrameLayout adminProfileHost;
    private BottomNavigationView adminNav;
    private boolean profileTabSelected;
    private NotificationBellController notificationBell;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildScreen();
        if (state != null && state.getBoolean("admin_profile_tab")) {
            adminNav.setSelectedItemId(R.id.bottom_profile);
        }
        refreshAdmin();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (authorized) refreshAdmin();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("admin_profile_tab", profileTabSelected);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onBackPressed() {
        if (profileTabSelected) {
            adminNav.setSelectedItemId(R.id.bottom_home);
        } else {
            super.onBackPressed();
        }
    }

    private void displayTab(boolean showProfile) {
        profileTabSelected = showProfile;
        dashboardScroll.setVisibility(showProfile ? View.GONE : View.VISIBLE);
        adminProfileHost.setVisibility(showProfile ? View.VISIBLE : View.GONE);
        if (showProfile && getSupportFragmentManager()
                .findFragmentById(R.id.contentFrame) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.contentFrame, new AdminProfileFragment())
                    .commit();
        }
    }

    @Override
    protected void onDestroy() {
        if (notificationBell != null) notificationBell.close();
        super.onDestroy();
    }

    private void refreshAdmin() {
        sync.setText("Checking administrator permission...");
        AdminAccess.verify(allowed -> {
            if (isFinishing() || isDestroyed()) return;
            if (!allowed) {
                Toast.makeText(this, "Admin access required", Toast.LENGTH_LONG).show();
                finish();
            } else {
                authorized = true;
                PushTokenRegistrar.register(this);
                loadOverview();
            }
        });
    }

    private void buildScreen() {
        NestedScrollView scroll = new NestedScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(color(R.color.md_theme_background));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), dp(26));
        scroll.addView(root);
        dashboardScroll = scroll;

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(color(R.color.md_theme_background));

        FrameLayout content = new FrameLayout(this);
        shell.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(dashboardScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        adminProfileHost = new FrameLayout(this);
        adminProfileHost.setId(R.id.contentFrame);
        adminProfileHost.setVisibility(View.GONE);
        content.addView(adminProfileHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        adminNav = new BottomNavigationView(this);
        adminNav.getMenu().add(0, R.id.bottom_home, 0, "Home").setIcon(R.drawable.ic_bq_home);
        adminNav.getMenu().add(0, R.id.bottom_profile, 1, "Profile").setIcon(R.drawable.ic_bq_profile);
        adminNav.setLabelVisibilityMode(
                com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);
        adminNav.setBackgroundColor(color(R.color.md_theme_surfaceContainerLow));
        adminNav.setElevation(dp(5));
        shell.addView(adminNav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(shell);
        ViewCompat.setOnApplyWindowInsetsListener(shell, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(shell);
        adminNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.bottom_home || item.getItemId() == R.id.bottom_profile) {
                displayTab(item.getItemId() == R.id.bottom_profile);
                return true;
            }
            return false;
        });
        LinearLayout topBar = row(root, 4);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView adminCaption = addText(topBar, "B U T T E R   Q U E E N   /   A D M I N",
                11, R.color.md_theme_primary, true, 0);
        adminCaption.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        FrameLayout bellSlot = new FrameLayout(this);
        topBar.addView(bellSlot, new LinearLayout.LayoutParams(dp(56), dp(56)));
        notificationBell = new NotificationBellController(this, bellSlot, true);
        addText(root, "Bakery Studio", 31, R.color.md_theme_onBackground, true, 5);
        addText(root, "Your bakery, at a glance", 15,
                R.color.md_theme_onSurfaceVariant, false, 4);
        sync = addText(root, "Loading insights...", 12,
                R.color.md_theme_onSurfaceVariant, false, 13);
        MaterialButton reload = button("↻  Refresh insights", false);
        root.addView(reload, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(47)));
        reload.setOnClickListener(v -> { if (authorized) loadOverview(); });

        LinearLayout first = row(root, 14);
        totalText = metric(first, "TOTAL ORDERS", "—", "All order records", true);
        activeText = metric(first, "ACTIVE ORDERS", "—", "Pending & in progress", false);
        LinearLayout second = row(root, 10);
        deliveredText = metric(second, "DELIVERED", "—", "Completed orders", true);
        valueText = metric(second, "DELIVERED VALUE", "—", "Not confirmed cash", false);
        addText(root, "Quick management", 19,
                R.color.md_theme_onBackground, true, 23);

        LinearLayout actions = row(root, 9);
        action(actions, "Manage Orders", true, () -> openManager(true), true);
        action(actions, "Manage Products", false, () -> openManager(false), false);
        LinearLayout more = row(root, 9);
        action(more, "Custom Requests", false,
                () -> startActivity(new Intent(this, AdminCustomCakeRequestsActivity.class)), true);
        action(more, "Sales Reports", false,
                () -> startActivity(new Intent(this, AdminSalesReportsActivity.class)), false);

        addText(root, "Business insights", 19,
                R.color.md_theme_onBackground, true, 24);
        LinearLayout chart = card(root, 9);
        addText(chart, "Order status distribution", 17,
                R.color.md_theme_onBackground, true, 0);
        addText(chart, "Live Firestore data • segmented donut chart", 12,
                R.color.md_theme_onSurfaceVariant, false, 4);
        LinearLayout chartRow = row(chart, 16);
        chartRow.setGravity(Gravity.CENTER_VERTICAL);
        donut = new DonutView();
        chartRow.addView(donut, new LinearLayout.LayoutParams(dp(154), dp(154)));
        legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams legendLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        legendLp.leftMargin = dp(8);
        chartRow.addView(legend, legendLp);

        LinearLayout monthly = card(root, 13);
        addText(monthly, "Monthly delivered value", 17,
                R.color.md_theme_onBackground, true, 0);
        addText(monthly, "Last 6 months • order creation month", 12,
                R.color.md_theme_onSurfaceVariant, false, 5);
        monthRows = new LinearLayout(this);
        monthRows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.topMargin = dp(16);
        monthly.addView(monthRows, mp);

        addText(root, "Recent orders", 19,
                R.color.md_theme_onBackground, true, 24);
        addText(root, "Tap to update status or open order management", 12,
                R.color.md_theme_onSurfaceVariant, false, 5);
        recentRows = new LinearLayout(this);
        recentRows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = dp(12);
        root.addView(recentRows, rp);
        addText(root, "Delivered order value is not verified cash revenue. "
                + "PayHere orders are included only when payment is verified.",
                11, R.color.md_theme_onSurfaceVariant, false, 18);
    }

    private LinearLayout row(LinearLayout parent, int top) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top);
        parent.addView(r, lp);
        return r;
    }

    private TextView metric(LinearLayout r, String title, String initial,
                            String hint, boolean left) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        card.setMinimumHeight(dp(124));
        if (left) cp.rightMargin = dp(6); else cp.leftMargin = dp(6);
        r.addView(card, cp);
        decorate(card);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(15), dp(11), dp(12));
        card.addView(content);
        addText(content, title, 10, R.color.md_theme_onSurfaceVariant, true, 0);
        TextView result = addText(content, initial, title.equals("DELIVERED VALUE") ? 18 : 27,
                R.color.md_theme_primary, true, 11);
        result.setMaxLines(2);
        addText(content, hint, 10, R.color.md_theme_onSurfaceVariant, false, 4);
        return result;
    }

    private void action(LinearLayout r, String name, boolean primary, Runnable fn, boolean left) {
        MaterialButton b = button(name, primary);
        b.setSingleLine(false);
        b.setMaxLines(2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(64), 1);
        if (left) lp.rightMargin = dp(5); else lp.leftMargin = dp(5);
        r.addView(b, lp);
        b.setOnClickListener(v -> fn.run());
    }

    private MaterialButton button(String label, boolean filled) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setCornerRadius(dp(16));
        if (filled) {
            button.setTextColor(color(R.color.md_theme_onPrimary));
            button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.md_theme_primary)));
        } else {
            button.setTextColor(color(R.color.md_theme_primary));
            button.setBackgroundTintList(ColorStateList.valueOf(
                    color(R.color.md_theme_surfaceContainerLow)));
            button.setStrokeColor(ColorStateList.valueOf(
                    color(R.color.md_theme_outlineVariant)));
            button.setStrokeWidth(dp(1));
        }
        return button;
    }

    private LinearLayout card(LinearLayout parent, int marginTop) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(marginTop);
        parent.addView(card, cp);
        decorate(card);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(17), dp(18), dp(17), dp(17));
        box.setOrientation(LinearLayout.VERTICAL);
        card.addView(box);
        return box;
    }

    private void decorate(MaterialCardView card) {
        card.setRadius(dp(20));
        card.setCardElevation(0);
        card.setCardBackgroundColor(color(R.color.md_theme_surfaceContainerLow));
        card.setStrokeColor(color(R.color.md_theme_outlineVariant));
        card.setStrokeWidth(dp(1));
    }

    private TextView addText(LinearLayout box, String text, int size,
                             int resource, boolean bold, int top) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(color(resource));
        t.setTextSize(size);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        box.addView(t, p);
        return t;
    }

    private void openManager(boolean orders) {
        Intent intent = new Intent(this, AdminDashboardActivity.class);
        intent.putExtra("open_orders", orders);
        startActivity(intent);
    }

    private void loadOverview() {
        if (!authorized || loading || isFinishing() || isDestroyed()) return;
        loading = true;
        sync.setText("Syncing with Firebase...");
        db.collection("orders").get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    loading = false;
                    if (isFinishing() || isDestroyed()) return;
                    List<DocumentSnapshot> records = new ArrayList<>(snapshot.getDocuments());
                    int[] counts = new int[STATUSES.length];
                    int active = 0, delivered = 0;
                    double deliveredValue = 0.0;
                    LinkedHashMap<String, Double> months = new LinkedHashMap<>();
                    LinkedHashMap<String, String> labels = new LinkedHashMap<>();
                    Calendar cal = Calendar.getInstance();
                    cal.set(Calendar.DAY_OF_MONTH, 1);
                    cal.add(Calendar.MONTH, -5);
                    for (int i = 0; i < 6; i++) {
                        String key = monthKey(cal);
                        months.put(key, 0.0);
                        labels.put(key, new SimpleDateFormat("MMM yy", Locale.getDefault())
                                .format(cal.getTime()));
                        cal.add(Calendar.MONTH, 1);
                    }
                    for (DocumentSnapshot doc : records) {
                        String status = doc.getString("orderStatus");
                        counts[statusIndex(status)]++;
                        if ("placed".equals(status) || "preparing".equals(status)
                                || "out_for_delivery".equals(status)
                                || "payment_pending".equals(status)) active++;
                        if (!"delivered".equals(status)) continue;
                        delivered++;
                        String method = doc.getString("paymentMethod");
                        String payment = doc.getString("paymentStatus");
                        if (!"cash_on_delivery".equals(method)
                                && !("payhere".equals(method) && "paid".equals(payment))) continue;
                        Object obj = doc.get("totalAmount");
                        double amount = obj instanceof Number
                                ? Math.max(0, ((Number) obj).doubleValue()) : 0;
                        deliveredValue += amount;
                        Long millis = doc.getLong("createdAtMillis");
                        if (millis != null && millis > 0) {
                            Calendar date = Calendar.getInstance();
                            date.setTimeInMillis(millis);
                            String key = monthKey(date);
                            if (months.containsKey(key)) {
                                months.put(key, months.get(key) + amount);
                            }
                        }
                    }
                    totalText.setText(String.valueOf(records.size()));
                    activeText.setText(String.valueOf(active));
                    deliveredText.setText(String.valueOf(delivered));
                    valueText.setText(money(deliveredValue));
                    donut.setCounts(counts);
                    renderLegend(counts);
                    renderMonths(months, labels);
                    renderRecent(records);
                    sync.setText(records.size() + " orders • Updated from Firestore");
                })
                .addOnFailureListener(error -> {
                    loading = false;
                    if (isFinishing() || isDestroyed()) return;
                    sync.setText("Could not refresh • Check network and admin access");
                    Toast.makeText(this, "Unable to load reports: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void renderLegend(int[] counts) {
        legend.removeAllViews();
        int sum = 0;
        for (int count : counts) sum += count;
        if (sum == 0) {
            addText(legend, "No orders yet", 12, R.color.md_theme_onSurfaceVariant, false, 0);
            return;
        }
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] == 0) continue;
            LinearLayout r = row(legend, 6);
            r.setGravity(Gravity.CENTER_VERTICAL);
            View circle = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setColor(COLORS[i]);
            d.setCornerRadius(dp(5));
            circle.setBackground(d);
            r.addView(circle, new LinearLayout.LayoutParams(dp(10), dp(10)));
            TextView name = new TextView(this);
            name.setText(LABELS[i]);
            name.setTextSize(11);
            name.setTextColor(color(R.color.md_theme_onSurface));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1);
            np.leftMargin = dp(7);
            r.addView(name, np);
            TextView n = new TextView(this);
            n.setText(String.valueOf(counts[i]));
            n.setTextSize(12);
            n.setTypeface(null, Typeface.BOLD);
            n.setTextColor(color(R.color.md_theme_onBackground));
            r.addView(n);
        }
    }

    private void renderMonths(Map<String, Double> months, Map<String, String> labels) {
        monthRows.removeAllViews();
        double highest = 0;
        for (double amount : months.values()) highest = Math.max(highest, amount);
        for (Map.Entry<String, Double> m : months.entrySet()) {
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.bottomMargin = dp(12);
            monthRows.addView(column, cp);
            LinearLayout title = row(column, 0);
            TextView month = addText(title, labels.get(m.getKey()), 12,
                    R.color.md_theme_onSurface, false, 0);
            month.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            TextView figure = new TextView(this);
            figure.setText(money(m.getValue()));
            figure.setTextSize(12);
            figure.setTypeface(null, Typeface.BOLD);
            figure.setTextColor(color(R.color.md_theme_primary));
            title.addView(figure);
            LinearLayout track = row(column, 6);
            track.setWeightSum(100f);
            GradientDrawable background = new GradientDrawable();
            background.setColor(color(R.color.md_theme_surfaceContainerHighest));
            background.setCornerRadius(dp(6));
            track.setBackground(background);
            track.getLayoutParams().height = dp(10);
            float fraction = highest <= 0 ? 0 : (float) (m.getValue() / highest * 100);
            if (fraction > 0) {
                View bar = new View(this);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(color(R.color.md_theme_primary));
                bg.setCornerRadius(dp(6));
                bar.setBackground(bg);
                track.addView(bar, new LinearLayout.LayoutParams(0, -1, fraction));
            }
            track.addView(new View(this), new LinearLayout.LayoutParams(
                    0, -1, 100f - fraction));
        }
    }

    private void renderRecent(List<DocumentSnapshot> records) {
        recentRows.removeAllViews();
        Collections.sort(records, (a, b) -> Long.compare(createdAt(b), createdAt(a)));
        if (records.isEmpty()) {
            addText(recentRows, "No orders yet", 14,
                    R.color.md_theme_onSurfaceVariant, false, 5);
            return;
        }
        for (int i = 0; i < Math.min(4, records.size()); i++) {
            DocumentSnapshot order = records.get(i);
            LinearLayout content = card(recentRows, 9);
            String shortId = order.getId().substring(0, Math.min(8, order.getId().length()));
            addText(content, "Order #" + shortId + "   •   " + LABELS[statusIndex(
                    order.getString("orderStatus"))], 14,
                    R.color.md_theme_onBackground, true, 0);
            Object sum = order.get("totalAmount");
            double amount = sum instanceof Number ? ((Number) sum).doubleValue() : 0;
            Long stamp = order.getLong("createdAtMillis");
            String date = stamp == null ? "Date unavailable" :
                    new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                            .format(new Date(stamp));
            addText(content, money(amount) + "   •   " + date + "   •   Tap to manage",
                    12, R.color.md_theme_onSurfaceVariant, false, 7);
            ((MaterialCardView) content.getParent()).setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Order #" + shortId)
                            .setItems(new String[]{"Update order status", "Open full order manager"},
                                    (dialog, which) -> {
                                        if (which == 0) chooseStatus(order);
                                        else openManager(true);
                                    })
                            .show());
        }
    }

    private void chooseStatus(DocumentSnapshot order) {
        String method = order.getString("paymentMethod");
        String payment = order.getString("paymentStatus");
        boolean awaitingPayment = "payhere".equals(method) && !"paid".equals(payment);
        String[] codes = awaitingPayment
                ? new String[]{"payment_pending", "cancelled"}
                : new String[]{"placed", "preparing", "out_for_delivery", "delivered", "cancelled"};
        String[] labels = awaitingPayment
                ? new String[]{"Payment verification pending", "Cancelled"}
                : new String[]{"Placed", "Preparing", "Out for delivery", "Delivered", "Cancelled"};
        new AlertDialog.Builder(this)
                .setTitle("Set order status")
                .setItems(labels, (dialog, selected) -> {
                    String code = codes[selected];
                    if (code.equals(order.getString("orderStatus"))) return;
                    new AlertDialog.Builder(this)
                            .setTitle("Confirm status change")
                            .setMessage("Change order #" + order.getId().substring(0,
                                    Math.min(8, order.getId().length())) + " to "
                                    + labels[selected] + "?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Update", (d, w) -> updateStatus(order.getId(), code))
                            .show();
                }).show();
    }

    private void updateStatus(String orderId, String code) {
        // Recheck role. Only allowed 'orderStatus' updates are sent; payment status is untouched.
        AdminAccess.verify(allowed -> {
            if (isFinishing() || isDestroyed()) return;
            if (!allowed) {
                Toast.makeText(this, "Admin access required", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            db.collection("orders").document(orderId).update("orderStatus", code)
                    .addOnSuccessListener(v -> {
                        Toast.makeText(this, "Order status updated", Toast.LENGTH_SHORT).show();
                        loadOverview();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this,
                            "Update failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        });
    }

    private int statusIndex(String status) {
        for (int i = 0; i < STATUSES.length - 1; i++) {
            if (STATUSES[i].equals(status)) return i;
        }
        return STATUSES.length - 1;
    }

    private long createdAt(DocumentSnapshot doc) {
        Long n = doc.getLong("createdAtMillis");
        return n == null ? 0 : n;
    }

    private String monthKey(Calendar cal) {
        return String.format(Locale.ROOT, "%04d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
    }

    private String money(double amount) {
        return String.format(Locale.getDefault(), "Rs. %,.0f", amount);
    }

    private int color(int id) {
        return ContextCompat.getColor(this, id);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class DonutView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final int[] segments = new int[COLORS.length];
        private int all;

        DonutView() { super(AdminOverviewActivity.this); }

        void setCounts(int[] values) {
            all = 0;
            for (int i = 0; i < segments.length; i++) {
                segments[i] = values[i];
                all += values[i];
            }
            setContentDescription("Order status donut chart showing " + all + " orders");
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) / 2f - dp(14);
            float thick = dp(18);
            bounds.set(cx - radius + thick / 2, cy - radius + thick / 2,
                    cx + radius - thick / 2, cy + radius - thick / 2);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(thick);
            paint.setStrokeCap(Paint.Cap.BUTT);
            if (all == 0) {
                paint.setColor(color(R.color.md_theme_outlineVariant));
                canvas.drawArc(bounds, -90, 360, false, paint);
            } else {
                float start = -90;
                for (int i = 0; i < segments.length; i++) {
                    if (segments[i] == 0) continue;
                    float sweep = segments[i] * 360f / all;
                    paint.setColor(COLORS[i]);
                    canvas.drawArc(bounds, start, sweep, false, paint);
                    start += sweep;
                }
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setFakeBoldText(true);
            paint.setTextSize(26 * getResources().getDisplayMetrics().scaledDensity);
            paint.setColor(color(R.color.md_theme_onSurface));
            canvas.drawText(String.valueOf(all), cx, cy + dp(3), paint);
            paint.setFakeBoldText(false);
            paint.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
            paint.setColor(color(R.color.md_theme_onSurfaceVariant));
            canvas.drawText("ORDERS", cx, cy + dp(20), paint);
        }
    }
}
