package lk.jiat.butterqueen.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import lk.jiat.butterqueen.R;
import lk.jiat.butterqueen.BuildConfig;
import lk.jiat.butterqueen.payment.SandboxOrderPolicy;
import lk.jiat.butterqueen.data.AdminAccess;
import lk.jiat.butterqueen.model.OrderItem;
import lk.jiat.butterqueen.model.Product;
import lk.jiat.butterqueen.model.UnitPricing;

public class AdminDashboardActivity extends AppCompatActivity {
    private FirebaseFirestore firestore;
    private LinearLayout rows;
    private TextView statusText;
    private View addCategoryButton, addProductButton, starterButton;
    private boolean authorized;
    private boolean showingOrders;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_admin_dashboard);
        showingOrders = getIntent().getBooleanExtra("open_orders", false);
        applyWindowInsets();
        firestore = FirebaseFirestore.getInstance();
        rows = findViewById(R.id.adminRows);
        statusText = findViewById(R.id.adminStatusText);
        addCategoryButton = findViewById(R.id.adminAddCategoryButton);
        addProductButton = findViewById(R.id.adminAddProductButton);
        starterButton = findViewById(R.id.adminStarterButton);
        findViewById(R.id.adminCustomCakeRequestsButton).setOnClickListener(v ->
                startActivity(new Intent(this, AdminCustomCakeRequestsActivity.class)));
        findViewById(R.id.adminSalesReportsButton).setOnClickListener(v ->
                startActivity(new Intent(this, AdminSalesReportsActivity.class)));
        findViewById(R.id.adminBack).setOnClickListener(v -> finish());
        findViewById(R.id.adminProductsTab).setOnClickListener(v -> {
            showingOrders = false;
            loadContent();
        });
        findViewById(R.id.adminOrdersTab).setOnClickListener(v -> {
            showingOrders = true;
            loadContent();
        });
        addCategoryButton.setOnClickListener(v -> showCategoryDialog());
        addProductButton.setOnClickListener(v ->
                startActivity(new Intent(this, AdminProductActivity.class)));
        starterButton.setOnClickListener(v -> createStarterCategories());

        AdminAccess.verify(allowed -> {
            if (isFinishing() || isDestroyed()) return;
            if (!allowed) {
                Toast.makeText(this, "Admin access required", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            authorized = true;
            loadContent();
        });
    }

    private void applyWindowInsets() {
        // Keep the toolbar clear of the clock on Android 15+ edge-to-edge devices.
        View root = findViewById(R.id.adminDashboardRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (authorized) {
            AdminAccess.verify(allowed -> {
                if (!allowed) {
                    finish();
                } else if (!isFinishing()) {
                    loadContent();
                }
            });
        }
    }

    private void loadContent() {
        if (!authorized) return;
        ((com.google.android.material.button.MaterialButtonToggleGroup)
                findViewById(R.id.adminTabs)).check(showingOrders
                ? R.id.adminOrdersTab : R.id.adminProductsTab);
        rows.removeAllViews();
        addCategoryButton.setVisibility(showingOrders ? View.GONE : View.VISIBLE);
        addProductButton.setVisibility(showingOrders ? View.GONE : View.VISIBLE);
        starterButton.setVisibility(showingOrders ? View.GONE : View.VISIBLE);
        statusText.setText(showingOrders ? "Loading customer orders..." : "Loading catalog...");
        if (showingOrders) loadOrders(); else loadProducts();
    }

    private void loadProducts() {
        firestore.collection("products").get()
                .addOnSuccessListener(snapshot -> {
                    if (showingOrders || isFinishing()) return;
                    List<DocumentSnapshot> documents = new ArrayList<>(snapshot.getDocuments());
                    documents.sort((left, right) -> Long.compare(
                            createdAt(right), createdAt(left)));
                    statusText.setText(snapshot.isEmpty()
                            ? "No products yet. Set up categories, then add a product."
                            : snapshot.size() + " products • tap one to edit");
                    for (DocumentSnapshot doc : documents) {
                        Product product = doc.toObject(Product.class);
                        if (product == null) continue;
                        product.setId(doc.getId());
                        if (doc.getBoolean("available") == null) product.setAvailable(true);
                        String unit = UnitPricing.normalize(product.getUnitType(),
                                product.getCategoryName());
                        String subtitle = (product.getCategoryName() == null
                                ? "Uncategorized" : product.getCategoryName())
                                + (product.getCakeType() == null || product.getCakeType().isEmpty()
                                ? "" : " • " + product.getCakeType())
                                + " • " + (product.isAvailable() ? "Available" : "Hidden");
                        if (!UnitPricing.isSupported(product.getUnitType())) {
                            subtitle += " • Review old unit/price";
                        }
                        MaterialCardView card = addCard(product.getName(), subtitle,
                                UnitPricing.isSupported(product.getUnitType())
                                        ? UnitPricing.priceLabel(product.getPrice(), unit)
                                        : "Old box/pack price: review before selling");
                        card.setOnClickListener(v -> {
                            Intent intent = new Intent(this, AdminProductActivity.class);
                            intent.putExtra("product_id", product.getId());
                            startActivity(intent);
                        });
                    }
                })
                .addOnFailureListener(error ->
                        statusText.setText("Could not load products: " + error.getMessage()));
    }

    private void loadOrders() {
        firestore.collection("orders").get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    if (!showingOrders || isFinishing()) return;
                    List<DocumentSnapshot> documents = new ArrayList<>(snapshot.getDocuments());
                    documents.sort((left, right) -> Long.compare(
                            createdAt(right), createdAt(left)));
                    statusText.setText(snapshot.isEmpty()
                            ? "No orders yet." : "Latest " + snapshot.size() + " orders");
                    for (DocumentSnapshot doc : documents) {
                        String payment = doc.getString("paymentStatus");
                        String date = new SimpleDateFormat("dd MMM yyyy, HH:mm",
                                Locale.getDefault()).format(new Date(
                                doc.getLong("createdAtMillis") == null ? 0L
                                        : doc.getLong("createdAtMillis")));
                        String title = value(doc.getString("customerName")) + " • "
                                + doc.getId().substring(0, Math.min(6, doc.getId().length()));
                        String detail = date + " • " + value(doc.getString("orderStatus"))
                                + " • " + value(payment);
                        Double total = doc.getDouble("totalAmount");
                        MaterialCardView card = addCard(title, detail,
                                String.format(Locale.getDefault(), "Rs. %.2f",
                                        total == null ? 0.0 : total));
                        card.setOnClickListener(v -> openOrder(doc));
                    }
                })
                .addOnFailureListener(error ->
                        statusText.setText("Could not load orders: " + error.getMessage()));
    }

    private void openOrder(DocumentSnapshot order) {
        firestore.collection("orders").document(order.getId()).collection("items").get()
                .addOnSuccessListener(snapshot -> {
                    StringBuilder message = new StringBuilder();
                    message.append("Customer: ").append(value(order.getString("customerName")))
                            .append("\nPhone: ").append(value(order.getString("customerPhone")))
                            .append("\nAddress: ").append(value(order.getString("deliveryAddress")))
                            .append("\nPayment: ").append(value(order.getString("paymentMethod")))
                            .append(" / ").append(value(order.getString("paymentStatus")))
                            .append("\nStatus: ").append(value(order.getString("orderStatus")))
                            .append("\n\nItems:");
                    for (DocumentSnapshot doc : snapshot) {
                        OrderItem item = doc.toObject(OrderItem.class);
                        if (item == null) continue;
                        message.append("\n• ").append(value(item.getProductName()))
                                .append(" — ").append(UnitPricing.quantityLabel(
                                        item.getQuantity(), item.getUnitType()))
                                .append(String.format(Locale.getDefault(),
                                        " × Rs. %.2f = Rs. %.2f",
                                        item.getUnitPrice(), item.getItemTotal()));
                    }
                    if (snapshot.isEmpty()) message.append("\nNo items recorded");
                    Double total = order.getDouble("totalAmount");
                    message.append(String.format(Locale.getDefault(),
                            "\n\nTotal: Rs. %.2f", total == null ? 0.0 : total));
                    new AlertDialog.Builder(this)
                            .setTitle("Order " + order.getId())
                            .setMessage(message.toString())
                            .setNegativeButton("Close", null)
                            .setNeutralButton("Receipt PDF", (dialog, which) ->
                                    startActivity(OrderReceiptActivity.intentForAdmin(
                                            this, order.getId())))
                            .setPositiveButton("Update status",
                                    (dialog, which) -> chooseStatus(order))
                            .show();
                })
                .addOnFailureListener(error ->
                        Toast.makeText(this, "Could not load order items", Toast.LENGTH_LONG).show());
    }

    private void chooseStatus(DocumentSnapshot cachedOrder) {
        // PayHere can verify a card payment while the order dialog is open.
        firestore.collection("orders").document(cachedOrder.getId()).get(Source.SERVER)
                .addOnSuccessListener(order -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (!order.exists()) {
                        Toast.makeText(this, "Order no longer exists", Toast.LENGTH_LONG).show();
                        return;
                    }
                    showStatusChoices(order);
                })
                .addOnFailureListener(error -> Toast.makeText(this,
                        "Could not refresh order payment status", Toast.LENGTH_LONG).show());
    }

    private void showStatusChoices(DocumentSnapshot order) {
        String paymentMethod = order.getString("paymentMethod");
        String paymentStatus = order.getString("paymentStatus");
        boolean awaitingPayment = !SandboxOrderPolicy.canManageOrder(
                paymentMethod, paymentStatus, BuildConfig.DEBUG);
        if (awaitingPayment) {
            boolean canDemo = SandboxOrderPolicy.canConvertOlderPendingOrder(
                    paymentMethod, paymentStatus, order.getString("orderStatus"),
                    BuildConfig.DEBUG);
            String details = "This PayHere payment is not verified by the provider.\n\n" +
                    "Payment status: " + value(paymentStatus) +
                    "\nPayHere reference: " + value(order.getString("paymentReference"));
            if (canDemo) {
                new AlertDialog.Builder(this)
                        .setTitle("Sandbox payment — viva demo")
                        .setMessage(details + "\n\nContinue as a DEMO (not real payment) " +
                                "to manage order delivery statuses.")
                        .setItems(new String[]{"Continue as sandbox demo", "Cancel order",
                                        "Refresh payment status"}, (dialog, which) -> {
                                    if (which == 0) confirmSandboxDemo(order);
                                    else if (which == 1) {
                                        confirmOrderStatus(order, "cancelled", "Cancelled");
                                    } else chooseStatus(order);
                                })
                        .setNegativeButton("Close", null)
                        .show();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Card payment not verified")
                        .setMessage(details + "\n\nCheck PayHere for this reference.")
                        .setNegativeButton("Close", null)
                        .setNeutralButton("Cancel order", (d, w) ->
                                confirmOrderStatus(order, "cancelled", "Cancelled"))
                        .setPositiveButton("Refresh", (d, w) -> chooseStatus(order))
                        .show();
            }
            return;
        }

        String[] statuses = {"placed", "preparing", "out_for_delivery",
                "delivered", "cancelled"};
        String[] labels = {"Placed", "Preparing", "Out for delivery",
                "Delivered", "Cancelled"};
        new AlertDialog.Builder(this)
                .setTitle("Update order status")
                .setItems(labels, (dialog, which) ->
                        confirmOrderStatus(order, statuses[which], labels[which]))
                .show();
    }

    private void confirmOrderStatus(DocumentSnapshot order, String chosen, String label) {
        if (chosen.equals(order.getString("orderStatus"))) return;
        new AlertDialog.Builder(this)
                .setTitle("Confirm status update")
                .setMessage("Change order #" + order.getId().substring(
                        0, Math.min(8, order.getId().length())) + " to " + label + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Update", (confirm, button) -> {
                    AdminAccess.verify(allowed -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (!allowed) {
                            Toast.makeText(this, "Admin access required",
                                    Toast.LENGTH_LONG).show();
                            finish();
                            return;
                        }
                        firestore.collection("orders").document(order.getId())
                                .update("orderStatus", chosen)
                                .addOnSuccessListener(unused -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    Toast.makeText(this, "Order status updated",
                                            Toast.LENGTH_SHORT).show();
                                    if (showingOrders) loadContent();
                                })
                                .addOnFailureListener(error -> Toast.makeText(this,
                                        "Update failed: " + error.getMessage(),
                                        Toast.LENGTH_LONG).show());
                    });
                }).show();
    }

    private void confirmSandboxDemo(DocumentSnapshot order) {
        new AlertDialog.Builder(this)
                .setTitle("University demo only")
                .setMessage("Continue the PayHere sandbox order without real payment " +
                        "verification? This order will be marked DEMO — NOT PAID.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue demo", (d, w) ->
                        AdminAccess.verify(allowed -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (!allowed) {
                                Toast.makeText(this, "Admin access required",
                                        Toast.LENGTH_LONG).show();
                                finish();
                                return;
                            }
                            firestore.runTransaction(tx -> {
                                DocumentReference ref = firestore.collection("orders")
                                        .document(order.getId());
                                DocumentSnapshot current = tx.get(ref);
                                if (!current.exists() ||
                                        !SandboxOrderPolicy.canConvertOlderPendingOrder(
                                                current.getString("paymentMethod"),
                                                current.getString("paymentStatus"),
                                                current.getString("orderStatus"),
                                                BuildConfig.DEBUG)) {
                                    throw new com.google.firebase.firestore.FirebaseFirestoreException(
                                            "Order no longer eligible for demo",
                                            com.google.firebase.firestore.FirebaseFirestoreException
                                                    .Code.FAILED_PRECONDITION);
                                }
                                tx.update(ref, "paymentStatus", SandboxOrderPolicy.DEMO_PAYMENT_STATUS,
                                        "orderStatus", "placed");
                                return null;
                            }).addOnSuccessListener(unused -> {
                                if (isFinishing() || isDestroyed()) return;
                                Toast.makeText(this, "Order ready for sandbox demo",
                                        Toast.LENGTH_SHORT).show();
                                if (showingOrders) loadContent();
                            }).addOnFailureListener(e -> Toast.makeText(this,
                                    "Demo update failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show());
                        }))
                .show();
    }

    private void showCategoryDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), 0);
        EditText name = new EditText(this);
        name.setHint("Cakes, Cupcakes, Brownies...");
        name.setSingleLine(true);
        form.addView(name);
        Spinner unit = new Spinner(this);
        unit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Price per kg", "Price per piece"}));
        form.addView(unit);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Add category")
                .setView(form).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null).create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String categoryName = name.getText().toString().trim();
                    if (categoryName.isEmpty()) {
                        name.setError("Enter category name");
                        return;
                    }
                    String unitType = unit.getSelectedItemPosition() == 0
                            ? UnitPricing.KG : UnitPricing.PCS;
                    if ("Cakes".equalsIgnoreCase(categoryName)
                            && !UnitPricing.KG.equals(unitType)
                            || ("Cupcakes".equalsIgnoreCase(categoryName)
                            || "Brownies".equalsIgnoreCase(categoryName))
                            && !UnitPricing.PCS.equals(unitType)) {
                        Toast.makeText(this, "Cakes use kg; cupcakes and brownies use pieces",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    firestore.collection("categories").get().addOnSuccessListener(snapshot -> {
                        for (DocumentSnapshot doc : snapshot) {
                            if (categoryName.equalsIgnoreCase(doc.getString("name"))) {
                                name.setError("Category already exists");
                                return;
                            }
                        }
                        DocumentReference ref = firestore.collection("categories").document();
                        ref.set(categoryFields(ref.getId(), categoryName, unitType))
                                .addOnSuccessListener(ok -> {
                                    dialog.dismiss();
                                    Toast.makeText(this, "Category added", Toast.LENGTH_SHORT).show();
                                    loadContent();
                                })
                                .addOnFailureListener(error -> Toast.makeText(this,
                                        "Save failed: " + error.getMessage(),
                                        Toast.LENGTH_LONG).show());
                    }).addOnFailureListener(error ->
                            Toast.makeText(this, "Could not check categories",
                                    Toast.LENGTH_LONG).show());
                }));
        dialog.show();
    }

    private void createStarterCategories() {
        firestore.collection("categories").get().addOnSuccessListener(snapshot -> {
            Set<String> names = new HashSet<>();
            for (DocumentSnapshot doc : snapshot) {
                String name = doc.getString("name");
                if (name != null) names.add(name.toLowerCase(Locale.ROOT));
            }
            WriteBatch batch = firestore.batch();
            int added = 0;
            for (String categoryName : new String[]{"Cakes", "Cupcakes", "Brownies"}) {
                if (names.contains(categoryName.toLowerCase(Locale.ROOT))) continue;
                DocumentReference ref = firestore.collection("categories").document();
                batch.set(ref, categoryFields(ref.getId(), categoryName,
                        "Cakes".equals(categoryName) ? UnitPricing.KG : UnitPricing.PCS));
                added++;
            }
            if (added == 0) {
                Toast.makeText(this, "Starter categories already exist", Toast.LENGTH_SHORT).show();
                return;
            }
            batch.commit().addOnSuccessListener(unused -> Toast.makeText(this,
                    "Starter categories ready", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(error -> Toast.makeText(this,
                            "Category setup failed: " + error.getMessage(),
                            Toast.LENGTH_LONG).show());
        }).addOnFailureListener(error -> Toast.makeText(this,
                "Could not check categories", Toast.LENGTH_LONG).show());
    }

    private Map<String, Object> categoryFields(String id, String name, String unit) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("id", id);
        fields.put("name", name);
        fields.put("description", "");
        fields.put("imageUrl", "");
        fields.put("unitType", unit);
        fields.put("active", true);
        fields.put("createdAtMillis", System.currentTimeMillis());
        return fields;
    }

    private MaterialCardView addCard(String title, String detail, String footer) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(12);
        rows.addView(card, cardParams);
        card.setRadius(dp(18));
        card.setCardElevation(0);
        card.setCardBackgroundColor(ContextCompat.getColor(this,
                R.color.md_theme_surfaceContainerLow));
        card.setStrokeColor(ContextCompat.getColor(this, R.color.md_theme_outlineVariant));
        card.setStrokeWidth(dp(1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.addView(content);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextSize(17);
        heading.setTextColor(ContextCompat.getColor(this, R.color.md_theme_onBackground));
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(heading);
        TextView sub = new TextView(this);
        sub.setText(detail);
        sub.setTextSize(13);
        sub.setTextColor(ContextCompat.getColor(this, R.color.md_theme_onSurfaceVariant));
        content.addView(sub);
        TextView bottom = new TextView(this);
        bottom.setText(footer);
        bottom.setTextSize(15);
        bottom.setTextColor(ContextCompat.getColor(this, R.color.md_theme_primary));
        bottom.setPadding(0, dp(8), 0, 0);
        content.addView(bottom);
        return card;
    }

    private long createdAt(DocumentSnapshot doc) {
        Long timestamp = doc.getLong("createdAtMillis");
        return timestamp == null ? 0 : timestamp;
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }

    private String value(String text) {
        return text == null || text.trim().isEmpty() ? "-" : text;
    }
}
