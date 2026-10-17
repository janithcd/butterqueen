package lk.jiat.butterqueen.activity;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.List;

import lk.jiat.butterqueen.R;
import lk.jiat.butterqueen.data.AdminAccess;

/**
 * Admin review moderation: browse ratings across all products and remove
 * inappropriate reviews. Stars and comments cannot be rewritten by admins.
 */
public final class AdminReviewsActivity extends AppCompatActivity {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private LinearLayout rows;
    private TextView feedback;
    private boolean authorized;
    private int pending;
    private final List<ReviewRow> reviews = new ArrayList<>();

    private static final class ReviewRow {
        String productName;
        String productId;
        DocumentSnapshot review;
        ReviewRow(String productName, String productId, DocumentSnapshot review) {
            this.productName = productName;
            this.productId = productId;
            this.review = review;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.md_theme_background));
        root.setPadding(dp(18), 0, dp(18), dp(10));
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets system = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(dp(18) + system.left, system.top,
                    dp(18) + system.right, dp(10) + system.bottom);
            return insets;
        });
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(toolbar);
        MaterialButton back = new MaterialButton(this);
        back.setText("‹ Back");
        back.setOnClickListener(v -> finish());
        toolbar.addView(back);
        TextView header = label("Customer Reviews", 22, true);
        toolbar.addView(header);
        feedback = label("Checking admin access...", 13, false);
        root.addView(feedback);
        MaterialButton refresh = new MaterialButton(this);
        refresh.setText("Refresh reviews");
        refresh.setOnClickListener(v -> { if (authorized) loadReviews(); });
        root.addView(refresh);
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);

        AdminAccess.verify(allowed -> {
            if (isFinishing() || isDestroyed()) return;
            if (!allowed) { Toast.makeText(this, "Admin access required", Toast.LENGTH_LONG).show(); finish(); return; }
            authorized = true;
            loadReviews();
        });
    }

    private void loadReviews() {
        feedback.setText("Loading reviews...");
        rows.removeAllViews();
        reviews.clear();
        db.collection("products").get(Source.SERVER)
                .addOnSuccessListener(products -> {
                    if (!authorized || isFinishing()) return;
                    pending = products.size();
                    if (pending == 0) { render(); return; }
                    for (DocumentSnapshot product : products) {
                        db.collection("products").document(product.getId())
                                .collection("reviews").get(Source.SERVER)
                                .addOnSuccessListener(result -> {
                                    if (isFinishing()) return;
                                    for (DocumentSnapshot review : result) {
                                        reviews.add(new ReviewRow(
                                                name(product.getString("name"), "Product"),
                                                product.getId(), review));
                                    }
                                    completeOne();
                                }).addOnFailureListener(error -> {
                                    if (isFinishing()) return;
                                    Toast.makeText(this, "Could not load reviews for " +
                                            name(product.getString("name"), "product"),
                                            Toast.LENGTH_SHORT).show();
                                    completeOne();
                                });
                    }
                }).addOnFailureListener(error ->
                        feedback.setText("Could not load products. Check network and admin access."));
    }

    private void completeOne() {
        pending--;
        if (pending == 0) render();
    }

    private void render() {
        if (isFinishing()) return;
        rows.removeAllViews();
        feedback.setText(reviews.size() + " reviews across products • Tap Remove to moderate");
        if (reviews.isEmpty()) {
            rows.addView(label("No reviews yet, or review rules are not published.", 14, false));
            return;
        }
        reviews.sort((a, b) -> Long.compare(stamp(b.review), stamp(a.review)));
        for (ReviewRow data : reviews) {
            MaterialCardView card = new MaterialCardView(this);
            card.setRadius(dp(18));
            card.setCardElevation(0);
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(color(R.color.md_theme_outlineVariant));
            card.setCardBackgroundColor(color(R.color.md_theme_surfaceContainerLow));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(12);
            rows.addView(card, lp);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.addView(col);
            col.addView(label(data.productName, 18, true));
            Long stars = data.review.getLong("stars");
            col.addView(label("★ " + (stars == null ? "?" : stars) + "/5 • Verified purchase",
                    14, true));
            col.addView(label(name(data.review.getString("text"), "(No comment)"),
                    15, false));
            // Never expose customer contact details in moderation list.
            MaterialButton remove = new MaterialButton(this);
            remove.setText("Remove review");
            remove.setOnClickListener(v -> confirmRemove(data));
            col.addView(remove);
        }
    }

    private void confirmRemove(ReviewRow row) {
        new AlertDialog.Builder(this)
                .setTitle("Remove customer review?")
                .setMessage("Remove this review from " + row.productName +
                        "? This permanently changes public average rating and cannot be undone.")
                .setNegativeButton("Keep review", null)
                .setPositiveButton("Remove", (dialog, which) ->
                        AdminAccess.verify(allowed -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (!allowed) { finish(); return; }
                            db.collection("products").document(row.productId)
                                    .collection("reviews").document(row.review.getId()).delete()
                                    .addOnSuccessListener(v -> {
                                        Toast.makeText(this, "Review removed", Toast.LENGTH_SHORT).show();
                                        loadReviews();
                                    })
                                    .addOnFailureListener(e -> Toast.makeText(this,
                                            "Cannot remove review: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show());
                        }))
                .show();
    }

    private static long stamp(DocumentSnapshot doc) {
        com.google.firebase.Timestamp time = doc.getTimestamp("updatedAt");
        return time == null ? 0 : time.toDate().getTime();
    }

    private static String name(String text, String fallback) {
        return text == null || text.trim().isEmpty() ? fallback : text.trim();
    }

    private TextView label(String text, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color(R.color.md_theme_onBackground));
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setPadding(dp(4), dp(8), dp(4), dp(8));
        return view;
    }

    private int color(int resId) { return ContextCompat.getColor(this, resId); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
