package lk.jiat.butterqueen.activity;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
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
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lk.jiat.butterqueen.R;
import lk.jiat.butterqueen.data.AdminAccess;
import lk.jiat.butterqueen.model.UnitPricing;

/** Manage existing categories without deleting order/product references. */
public final class AdminCategoriesActivity extends AppCompatActivity {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<DocumentSnapshot> categories = new ArrayList<>();
    private LinearLayout rows;
    private TextView feedback;
    private boolean authorized;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(color(R.color.md_theme_background));
        root.setPadding(dp(18), 0, dp(18), dp(10));
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(dp(18) + bars.left, bars.top,
                    dp(18) + bars.right, dp(10) + bars.bottom);
            return insets;
        });
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header);
        MaterialButton back = new MaterialButton(this);
        back.setText("‹ Back");
        back.setOnClickListener(v -> finish());
        header.addView(back);
        header.addView(label("Manage Categories", 22, true));
        feedback = label("Checking admin access...", 13, false);
        root.addView(feedback);
        MaterialButton refresh = new MaterialButton(this);
        refresh.setText("Refresh categories");
        refresh.setOnClickListener(v -> { if (authorized) loadCategories(); });
        root.addView(refresh);
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        AdminAccess.verify(allowed -> {
            if (isFinishing() || isDestroyed()) return;
            if (!allowed) {
                Toast.makeText(this, "Admin access required", Toast.LENGTH_LONG).show();
                finish(); return;
            }
            authorized = true;
            loadCategories();
        });
    }

    private void loadCategories() {
        rows.removeAllViews();
        feedback.setText("Loading categories...");
        db.collection("categories").get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    if (!authorized || isFinishing() || isDestroyed()) return;
                    categories.clear();
                    categories.addAll(snapshot.getDocuments());
                    categories.sort((a, b) -> name(a.getString("name")).compareToIgnoreCase(
                            name(b.getString("name"))));
                    feedback.setText(categories.size() + " categories • Tap a category to edit");
                    for (DocumentSnapshot doc : categories) addCategoryRow(doc);
                    if (categories.isEmpty()) {
                        rows.addView(label("No categories yet. Add one in Bakery Studio.", 15, false));
                    }
                })
                .addOnFailureListener(error ->
                        feedback.setText("Unable to load categories: " + error.getMessage()));
    }

    private void addCategoryRow(DocumentSnapshot doc) {
        String title = name(doc.getString("name"));
        boolean active = !Boolean.FALSE.equals(doc.getBoolean("active"));
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
        col.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.addView(col);
        col.addView(label(title, 18, true));
        String unit = UnitPricing.normalize(doc.getString("unitType"), title);
        col.addView(label((UnitPricing.KG.equals(unit) ? "Per kg" : "Per piece") +
                " • " + (active ? "Visible" : "Hidden") +
                " • Tap to edit", 13, false));
        card.setOnClickListener(v -> showEditor(doc));
    }

    private void showEditor(DocumentSnapshot doc) {
        String oldName = name(doc.getString("name"));
        String oldUnit = UnitPricing.normalize(doc.getString("unitType"), oldName);
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(22), dp(8), dp(22), 0);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText title = input("Category name", oldName, form);
        EditText description = input("Description", name(doc.getString("description")), form);
        EditText image = input("HTTPS image URL (optional)", name(doc.getString("imageUrl")), form);
        Spinner unit = new Spinner(this);
        unit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Price per kg", "Price per piece"}));
        unit.setSelection(UnitPricing.KG.equals(oldUnit) ? 0 : 1);
        form.addView(unit);
        CheckBox active = new CheckBox(this);
        active.setText("Visible in customer categories");
        active.setChecked(!Boolean.FALSE.equals(doc.getBoolean("active")));
        form.addView(active);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit category")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save changes", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String updatedName = title.getText().toString().trim();
                    String descriptionText = description.getText().toString().trim();
                    String imageUrl = image.getText().toString().trim();
                    String updatedUnit = unit.getSelectedItemPosition() == 0
                            ? UnitPricing.KG : UnitPricing.PCS;
                    if (updatedName.isEmpty() || updatedName.length() > 70) {
                        title.setError("Enter a category name (1–70 characters)");
                        return;
                    }
                    for (DocumentSnapshot other : categories) {
                        if (!doc.getId().equals(other.getId()) &&
                                updatedName.equalsIgnoreCase(name(other.getString("name")))) {
                            title.setError("Another category has this name");
                            return;
                        }
                    }
                    if (descriptionText.length() > 300) {
                        description.setError("Maximum 300 characters");
                        return;
                    }
                    if (!imageUrl.isEmpty() && !imageUrl.startsWith("https://")) {
                        image.setError("Use a secure HTTPS image URL");
                        return;
                    }
                    if ((updatedName.equalsIgnoreCase("Cakes") && !UnitPricing.KG.equals(updatedUnit))
                            || ((updatedName.equalsIgnoreCase("Cupcakes")
                            || updatedName.equalsIgnoreCase("Brownies"))
                            && !UnitPricing.PCS.equals(updatedUnit))) {
                        Toast.makeText(this, "Cakes use kg; cupcakes/brownies use pieces",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    saveCategory(doc, updatedName, descriptionText, imageUrl, updatedUnit,
                            active.isChecked(), dialog);
                }));
        dialog.show();
    }

    private void saveCategory(DocumentSnapshot doc, String name, String description,
                              String imageUrl, String unit, boolean active, AlertDialog dialog) {
        AdminAccess.verify(allowed -> {
            if (isFinishing() || isDestroyed()) return;
            if (!allowed) { finish(); return; }
            // Products store categoryName and unitType too. Never silently
            // change product pricing by changing the category unit.
            db.collection("products").whereEqualTo("categoryId", doc.getId())
                    .get(Source.SERVER)
                    .addOnSuccessListener(products -> {
                        String oldName = name(doc.getString("name"));
                        String oldUnit = UnitPricing.normalize(doc.getString("unitType"), oldName);
                        if (!products.isEmpty() && !oldUnit.equals(unit)) {
                            Toast.makeText(this,
                                    "Category has products. Keep its pricing unit; edit product prices first.",
                                    Toast.LENGTH_LONG).show();
                            enableSave(dialog);
                            return;
                        }
                        if (products.size() > 400) {
                            Toast.makeText(this, "Too many linked products for one edit. Contact support.",
                                    Toast.LENGTH_LONG).show();
                            enableSave(dialog);
                            return;
                        }
                        WriteBatch batch = db.batch();
                        Map<String, Object> fields = new HashMap<>();
                        fields.put("name", name);
                        fields.put("description", description);
                        fields.put("imageUrl", imageUrl);
                        fields.put("unitType", unit);
                        fields.put("active", active);
                        batch.update(doc.getReference(), fields);
                        if (!oldName.equals(name)) {
                            for (DocumentSnapshot product : products) {
                                batch.update(product.getReference(), "categoryName", name);
                            }
                        }
                        batch.commit().addOnSuccessListener(v -> {
                            dialog.dismiss();
                            Toast.makeText(this, "Category updated", Toast.LENGTH_SHORT).show();
                            loadCategories();
                        }).addOnFailureListener(error -> {
                            Toast.makeText(this, "Save failed: " + error.getMessage(),
                                    Toast.LENGTH_LONG).show();
                            enableSave(dialog);
                        });
                    }).addOnFailureListener(error -> {
                        Toast.makeText(this, "Could not verify linked products",
                                Toast.LENGTH_LONG).show();
                        enableSave(dialog);
                    });
        });
    }

    private void enableSave(AlertDialog dialog) {
        if (dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
    }

    private EditText input(String hint, String content, LinearLayout form) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setText(content);
        field.setSingleLine(!hint.equals("Description"));
        form.addView(field);
        return field;
    }

    private static String name(String value) { return value == null ? "" : value.trim(); }

    private TextView label(String text, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color(R.color.md_theme_onBackground));
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding(dp(4), dp(8), dp(4), dp(8));
        return t;
    }
    private int color(int id) { return ContextCompat.getColor(this, id); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
