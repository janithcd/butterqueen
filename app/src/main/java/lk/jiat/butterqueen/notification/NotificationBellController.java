package lk.jiat.butterqueen.notification;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lk.jiat.butterqueen.R;
import lk.jiat.butterqueen.activity.NotificationInboxActivity;

/** Firebase inbox plus a Firestore-derived Spark custom-cake notification badge. */
public final class NotificationBellController {
    private final Activity activity;
    private final FrameLayout host;
    private final ImageButton bell;
    private final TextView badge;
    private final boolean admin;
    private final String uid;
    private ListenerRegistration inboxRegistration;
    private ListenerRegistration cakeRegistration;
    private ListenerRegistration orderRegistration;
    private SharedPreferences readPrefs;
    private SharedPreferences orderReadPrefs;
    private SharedPreferences.OnSharedPreferenceChangeListener readListener;
    private List<SparkCakeNotifications.Notice> cakes = new ArrayList<>();
    private List<SparkOrderNotifications.Notice> orders = new ArrayList<>();
    private Set<String> previousOrderKeys;
    private Set<String> previousKeys;
    private int cloudUnread;
    private int previouslyDisplayed;

    public NotificationBellController(Activity activity, FrameLayout host) {
        this(activity, host, false);
    }

    public NotificationBellController(Activity activity, FrameLayout host, boolean admin) {
        this.activity = activity;
        this.host = host;
        this.admin = admin;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user == null ? null : user.getUid();
        host.removeAllViews();
        host.setBackgroundResource(R.drawable.bg_bq_bell_surface);

        bell = new ImageButton(activity);
        bell.setImageResource(R.drawable.ic_bq_bell);
        bell.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(activity, R.color.md_theme_onSurface)));
        bell.setBackgroundResource(android.R.color.transparent);
        bell.setContentDescription("Open notifications");
        host.addView(bell, new FrameLayout.LayoutParams(-1, -1));
        bell.setOnClickListener(v -> activity.startActivity(
                new Intent(activity, NotificationInboxActivity.class)));

        badge = new TextView(activity);
        badge.setTextColor(0xFFFFFFFF);
        badge.setTextSize(10);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(5), 0, dp(5), 0);
        badge.setMinWidth(dp(19));
        badge.setMinHeight(dp(19));
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setColor(0xFFE53935);
        background.setCornerRadius(dp(14));
        badge.setBackground(background);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.TOP | Gravity.END);
        lp.topMargin = dp(3);
        lp.rightMargin = dp(2);
        host.addView(badge, lp);
        badge.setVisibility(View.GONE);
        if (uid == null) return;

        readPrefs = SparkCakeNotifications.getReadPreferences(activity, uid);
        readListener = (preferences, key) -> updateBadge();
        readPrefs.registerOnSharedPreferenceChangeListener(readListener);
        orderReadPrefs = SparkOrderNotifications.getReadPreferences(activity, uid);
        orderReadPrefs.registerOnSharedPreferenceChangeListener(readListener);

        inboxRegistration = FirebaseFirestore.getInstance()
                .collection("users").document(uid).collection("notifications")
                .whereEqualTo("read", false).limit(100)
                .addSnapshotListener((snapshot, error) -> {
                    if (!sameUser()) return;
                    cloudUnread = error == null && snapshot != null ? snapshot.size() : 0;
                    updateBadge();
                });
        orderRegistration = SparkOrderNotifications.watch(uid, admin,
                new SparkOrderNotifications.Listener() {
                    @Override
                    public void onNotices(List<SparkOrderNotifications.Notice> notices) {
                        if (!sameUser()) return;
                        Set<String> keys = new HashSet<>();
                        for (SparkOrderNotifications.Notice notice : notices) keys.add(notice.key);
                        if (previousOrderKeys != null) {
                            for (SparkOrderNotifications.Notice notice : notices) {
                                if (!previousOrderKeys.contains(notice.key)
                                        && !SparkOrderNotifications.isRead(activity, uid, notice)) {
                                    CustomCakeLocalAlert.show(activity, uid + "_" + notice.key,
                                            notice.title, notice.body);
                                }
                            }
                        }
                        previousOrderKeys = keys;
                        orders = notices;
                        updateBadge();
                    }

                    @Override
                    public void onError(Exception error) {
                        if (sameUser()) updateBadge();
                    }
                });
        cakeRegistration = SparkCakeNotifications.watch(uid, admin,
                new SparkCakeNotifications.Listener() {
                    @Override
                    public void onNotices(List<SparkCakeNotifications.Notice> notices) {
                        if (!sameUser()) return;
                        Set<String> now = new HashSet<>();
                        for (SparkCakeNotifications.Notice notice : notices) now.add(notice.key);
                        if (previousKeys != null) {
                            for (SparkCakeNotifications.Notice notice : notices) {
                                if (!previousKeys.contains(notice.key)
                                        && !SparkCakeNotifications.isRead(activity, uid, notice)
                                        && (admin && "admin_new".equals(notice.type)
                                        || !admin && "customer_status".equals(notice.type))) {
                                    CustomCakeLocalAlert.show(activity,
                                            uid + "_" + notice.key, notice.title, notice.body);
                                }
                            }
                        }
                        previousKeys = now;
                        cakes = notices;
                        updateBadge();
                    }

                    @Override
                    public void onError(Exception error) {
                        // Keep existing Firestore-inbox count even if cake query is denied.
                        if (sameUser()) updateBadge();
                    }
                });
    }

    private boolean sameUser() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null && uid != null && uid.equals(user.getUid())
                && !activity.isFinishing() && !activity.isDestroyed();
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private void updateBadge() {
        if (!sameUser()) {
            badge.setVisibility(View.GONE);
            return;
        }
        int count = cloudUnread + SparkCakeNotifications.unread(activity, uid, cakes)
                + SparkOrderNotifications.unread(activity, uid, orders);
        badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        badge.setText(count > 99 ? "99+" : String.valueOf(count));
        if (count > previouslyDisplayed) {
            badge.setScaleX(0.7f);
            badge.setScaleY(0.7f);
            badge.animate().scaleX(1f).scaleY(1f).setDuration(200).start();
        }
        previouslyDisplayed = count;
        bell.setContentDescription(count + " unread notifications");
    }

    public void close() {
        if (inboxRegistration != null) inboxRegistration.remove();
        if (cakeRegistration != null) cakeRegistration.remove();
        if (orderRegistration != null) orderRegistration.remove();
        inboxRegistration = null;
        cakeRegistration = null;
        orderRegistration = null;
        if (readPrefs != null && readListener != null)
            readPrefs.unregisterOnSharedPreferenceChangeListener(readListener);
        if (orderReadPrefs != null && readListener != null)
            orderReadPrefs.unregisterOnSharedPreferenceChangeListener(readListener);
        host.removeAllViews();
    }
}
