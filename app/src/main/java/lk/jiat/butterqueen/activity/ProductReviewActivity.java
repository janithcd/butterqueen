package lk.jiat.butterqueen.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Source;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lk.jiat.butterqueen.R;

/**
 * Product reviews are public to read but require a real delivered order to create.
 * The Firestore review document ID is the reviewer's Firebase Auth UID.
 * Never trust the client-side eligibility check alone: Firestore Rules independently
 * verify order owner, delivered status, order item, and exact product ID.
 */
public final class ProductReviewActivity extends AppCompatActivity {
    private static final String PRODUCT = "review_product_id";
    private static final String ORDER = "review_order_id";
    private static final String ITEM = "review_item_id";

    public static Intent forProduct(android.content.Context context, String productId) {
        return new Intent(context, ProductReviewActivity.class).putExtra(PRODUCT, productId);
    }

    public static Intent forDeliveredOrder(android.content.Context context, String productId,
                                           String orderId, String itemId) {
        return forProduct(context, productId).putExtra(ORDER, orderId).putExtra(ITEM, itemId);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String productId;
    private String proofOrderId;
    private String proofItemId;
    private String uid;
    private boolean eligible;
    private boolean existingReview;
    private ListenerRegistration reviewsListener;
    private TextView productLabel;
    private TextView ratingSummary;
    private TextView eligibilityMessage;
    private LinearLayout reviewRows;
    private RatingBar stars;
    private EditText comment;
    private MaterialButton submit;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        productId = getIntent().getStringExtra(PRODUCT);
        proofOrderId = getIntent().getStringExtra(ORDER);
        proofItemId = getIntent().getStringExtra(ITEM);
        if (productId == null || productId.isEmpty() || productId.contains("/")) {
            finish();
            return;
        }
        setContentView(R.layout.activity_product_review);
        View root = findViewById(R.id.reviewsRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        findViewById(R.id.reviewsBack).setOnClickListener(v -> finish());
        productLabel = findViewById(R.id.reviewsProductName);
        ratingSummary = findViewById(R.id.reviewsAverage);
        eligibilityMessage = findViewById(R.id.reviewEligibilityMessage);
        reviewRows = findViewById(R.id.reviewsList);
        stars = findViewById(R.id.reviewStars);
        comment = findViewById(R.id.reviewComment);
        submit = findViewById(R.id.reviewSubmit);
        submit.setEnabled(false);
        submit.setOnClickListener(v -> saveReview());

        db.collection("products").document(productId).get()
                .addOnSuccessListener(doc -> {
                    if (isFinishing() || isDestroyed()) return;
                    productLabel.setText(doc.exists()
                            ? safe(doc.getString("name"), "Product reviews")
                            : "Product reviews");
                });

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = user == null ? null : user.getUid();
        if (uid == null) {
            eligibilityMessage.setText("Sign in to write a verified-purchase review.");
        } else {
            // An existing review can be edited from another delivered order receipt.
            if (proofOrderId != null && proofItemId != null) verifyPurchasedItem();
            else eligibilityMessage.setText(
                    "To write a review, open Orders → a delivered order receipt → Rate product.");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        reviewsListener = db.collection("products").document(productId).collection("reviews")
                .addSnapshotListener((snapshot, error) -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (error != null) {
                        ratingSummary.setText("Reviews unavailable. Check your connection.");
                        return;
                    }
                    if (snapshot == null) return;
                    List<DocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
                    renderReviews(docs);
                });
    }

    @Override
    protected void onStop() {
        if (reviewsListener != null) reviewsListener.remove();
        reviewsListener = null;
        super.onStop();
    }

    private boolean sameAccount() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return uid != null && user != null && uid.equals(user.getUid());
    }

    private void verifyPurchasedItem() {
        eligibilityMessage.setText("Verifying delivered purchase...");
        if (!sameAccount()) return;
        db.collection("orders").document(proofOrderId).get(Source.SERVER)
                .addOnSuccessListener(order -> {
                    if (!sameAccount() || isFinishing() || isDestroyed()) return;
                    if (!order.exists() || !uid.equals(order.getString("userId"))
                            || !"delivered".equals(order.getString("orderStatus"))) {
                        eligibilityMessage.setText("Only products from delivered orders can be reviewed.");
                        return;
                    }
                    db.collection("orders").document(proofOrderId)
                            .collection("items").document(proofItemId).get(Source.SERVER)
                            .addOnSuccessListener(item -> {
                                if (!sameAccount() || isFinishing() || isDestroyed()) return;
                                if (!item.exists() || !productId.equals(item.getString("productId"))) {
                                    eligibilityMessage.setText("This product was not in that order.");
                                    return;
                                }
                                eligible = true;
                                eligibilityMessage.setText("Verified purchase ✓  Rate your experience.");
                                loadOwnReview();
                            })
                            .addOnFailureListener(e ->
                                    eligibilityMessage.setText("Cannot verify item. Check internet."));
                })
                .addOnFailureListener(e ->
                        eligibilityMessage.setText("Cannot verify delivery. Check internet."));
    }

    private void loadOwnReview() {
        if (!sameAccount()) return;
        db.collection("products").document(productId).collection("reviews").document(uid)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    if (!sameAccount() || isFinishing() || isDestroyed()) return;
                    existingReview = doc.exists();
                    if (existingReview) {
                        Long value = doc.getLong("stars");
                        stars.setRating(value == null ? 0 : value);
                        comment.setText(doc.getString("text"));
                        submit.setText("Update My Review");
                    } else {
                        submit.setText("Publish Review");
                    }
                    submit.setEnabled(eligible);
                })
                .addOnFailureListener(e ->
                        eligibilityMessage.setText("Cannot load your review. Check connection."));
    }

    private void saveReview() {
        if (!eligible || !sameAccount() || !submit.isEnabled()) return;
        int value = Math.round(stars.getRating());
        String message = comment.getText() == null ? "" : comment.getText().toString().trim();
        if (value < 1 || value > 5) {
            Toast.makeText(this, "Select between 1 and 5 stars", Toast.LENGTH_SHORT).show();
            return;
        }
        if (message.length() < 3 || message.length() > 800) {
            comment.setError("Write a short review (3–800 characters)");
            return;
        }
        submit.setEnabled(false);
        submit.setText("Saving review...");
        DocumentReference ref = db.collection("products").document(productId)
                .collection("reviews").document(uid);
        Map<String, Object> values = new HashMap<>();
        values.put("stars", value);
        values.put("text", message);
        values.put("updatedAt", FieldValue.serverTimestamp());
        com.google.android.gms.tasks.Task<Void> action;
        if (existingReview) {
            action = ref.update(values);
        } else {
            values.put("userId", uid);
            values.put("orderId", proofOrderId);
            values.put("itemId", proofItemId);
            values.put("createdAt", FieldValue.serverTimestamp());
            action = ref.set(values);
        }
        action.addOnSuccessListener(unused -> {
            if (isFinishing() || isDestroyed()) return;
            existingReview = true;
            submit.setEnabled(true);
            submit.setText("Update My Review");
            Toast.makeText(this, "Thank you! Your review is published.", Toast.LENGTH_LONG).show();
        }).addOnFailureListener(error -> {
            if (isFinishing() || isDestroyed()) return;
            submit.setEnabled(true);
            submit.setText(existingReview ? "Update My Review" : "Publish Review");
            Toast.makeText(this, "Review was not saved: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        });
    }

    private void renderReviews(List<DocumentSnapshot> docs) {
        reviewRows.removeAllViews();
        double total = 0;
        int rated = 0;
        for (DocumentSnapshot doc : docs) {
            Long star = doc.getLong("stars");
            if (star != null && star >= 1 && star <= 5) {
                total += star;
                rated++;
            }
        }
        ratingSummary.setText(rated == 0 ? "No reviews yet. Be the first after delivery!"
                : String.format(Locale.getDefault(), "★ %.1f / 5  •  %d verified review%s",
                total / rated, rated, rated == 1 ? "" : "s"));

        Collections.sort(docs, (a, b) -> Long.compare(timestamp(b), timestamp(a)));
        int displayed = 0;
        for (DocumentSnapshot doc : docs) {
            Long star = doc.getLong("stars");
            if (star == null || star < 1 || star > 5) continue;
            if (displayed++ >= 15) break;
            MaterialCardView card = new MaterialCardView(this);
            card.setRadius(dp(16));
            card.setCardElevation(0);
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(color(R.color.md_theme_outlineVariant));
            card.setCardBackgroundColor(color(R.color.md_theme_surfaceContainerLow));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.bottomMargin = dp(12);
            reviewRows.addView(card, cp);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.addView(col);
            TextView heading = new TextView(this);
            StringBuilder five = new StringBuilder();
            for (int i = 1; i <= 5; i++) five.append(i <= star ? "★" : "☆");
            heading.setText(five + "  Verified customer");
            heading.setTextColor(color(R.color.md_theme_primary));
            heading.setTextSize(15);
            heading.setTypeface(null, android.graphics.Typeface.BOLD);
            col.addView(heading);
            TextView body = new TextView(this);
            body.setText(safe(doc.getString("text"), ""));
            body.setTextColor(color(R.color.md_theme_onBackground));
            body.setTextSize(14);
            body.setPadding(0, dp(8), 0, 0);
            col.addView(body);
        }
    }

    private static long timestamp(DocumentSnapshot doc) {
        Timestamp stamp = doc.getTimestamp("updatedAt");
        return stamp == null ? 0 : stamp.toDate().getTime();
    }
    private static String safe(String input, String fallback) {
        return input == null || input.trim().isEmpty() ? fallback : input.trim();
    }
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
    private int color(int id) {
        return ContextCompat.getColor(this, id);
    }
}
