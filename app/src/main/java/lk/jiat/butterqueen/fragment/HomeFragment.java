package lk.jiat.butterqueen.fragment;

import android.content.Intent;
import lk.jiat.butterqueen.notification.NotificationBellController;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lk.jiat.butterqueen.activity.ProductDetailsActivity;
import lk.jiat.butterqueen.activity.CustomCakeActivity;
import lk.jiat.butterqueen.activity.MyCustomCakeRequestsActivity;
import lk.jiat.butterqueen.data.AppSettings;
import lk.jiat.butterqueen.adapter.CategoryAdapter;
import lk.jiat.butterqueen.adapter.ProductAdapter;
import lk.jiat.butterqueen.databinding.FragmentHomeBinding;
import lk.jiat.butterqueen.model.Category;
import lk.jiat.butterqueen.model.Product;
import lk.jiat.butterqueen.model.UnitPricing;

public class HomeFragment extends Fragment implements SensorEventListener {
    private NotificationBellController notificationBell;


    private FragmentHomeBinding binding;

    private CategoryAdapter categoryAdapter;
    private ProductAdapter productAdapter;

    private final List<Category> categoryList = new ArrayList<>();
    private final List<Product> allProducts = new ArrayList<>();
    private final List<Product> filteredProducts = new ArrayList<>();

    private FirebaseFirestore firestore;
    private boolean loadingProducts;
    private boolean productsLoadError;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeAt;
    private String selectedCategory = "ALL";
    private String selectedCategoryName = "ALL";
    private String selectedCategoryUnit = "";
    private String selectedCakeType = "ALL";

    public HomeFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        notificationBell = new NotificationBellController(requireActivity(), binding.customerNotificationBellHost);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firestore = FirebaseFirestore.getInstance();
        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Bundle args = getArguments();
        if (args != null) {
            selectedCategory = args.getString("category_id", "ALL");
            selectedCategoryName = args.getString("category_name", "ALL");
            selectedCategoryUnit = args.getString("category_unit", "");
        }

        setupCategoryRecycler();
        setupProductRecycler();
        setupSearch();
        setupActions();
        updateOccasionVisibility();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null && firestore != null) {
            loadCategories();
            loadProducts();
        }
        if (sensorManager != null && accelerometer != null
                && AppSettings.shakeRefresh(requireContext())) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPause() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (binding == null || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER
                || !AppSettings.shakeRefresh(requireContext())) return;
        double x = event.values[0], y = event.values[1], z = event.values[2];
        double acceleration = Math.sqrt(x * x + y * y + z * z);
        long now = SystemClock.elapsedRealtime();
        if (acceleration > 20 && now - lastShakeAt >= 1800) {
            lastShakeAt = now;
            loadCategories();
            loadProducts();
            Toast.makeText(requireContext(), "Refreshing baked treats", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private void updateOccasionVisibility() {
        boolean show = "ALL".equals(selectedCategory)
                || UnitPricing.isKg(UnitPricing.normalize(
                selectedCategoryUnit, selectedCategoryName));
        binding.cakeOccasionChips.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setupCategoryRecycler() {
        binding.recyclerViewHomeCategories.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );

        categoryAdapter = new CategoryAdapter(categoryList, category -> {
            selectedCategory = category.getId() != null ? category.getId() : "ALL";
            selectedCategoryName = category.getName() != null ? category.getName() : "ALL";
            selectedCategoryUnit = category.getUnitType();
            if (!UnitPricing.isKg(UnitPricing.normalize(category.getUnitType(), category.getName()))) {
                selectedCakeType = "ALL";
                binding.cakeOccasionChips.check(lk.jiat.butterqueen.R.id.chipAllOccasions);
            }
            binding.cakeOccasionChips.setVisibility(
                    UnitPricing.isKg(UnitPricing.normalize(category.getUnitType(),
                            category.getName())) ? View.VISIBLE : View.GONE);
            filterProducts();
        });

        binding.recyclerViewHomeCategories.setAdapter(categoryAdapter);
    }

    private void setupProductRecycler() {
        binding.recyclerViewCakes.setLayoutManager(new LinearLayoutManager(requireContext()));

        productAdapter = new ProductAdapter(
                filteredProducts,
                product -> {
                    Intent intent = new Intent(requireContext(), ProductDetailsActivity.class);
                    intent.putExtra("product_id", product.getId());
                    startActivity(intent);
                },
                null
        );

        binding.recyclerViewCakes.setAdapter(productAdapter);
    }

    private void setupSearch() {
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterProducts();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupActions() {
        binding.customCakeButton.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CustomCakeActivity.class)));
        binding.myCustomCakeRequestsButton.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), MyCustomCakeRequestsActivity.class)));
        binding.orderNowButton.setOnClickListener(v ->
                binding.recyclerViewCakes.smoothScrollToPosition(0)
        );

        binding.cakeOccasionChips.setOnCheckedStateChangeListener((group, ids) -> {
            int id = group.getCheckedChipId();
            selectedCakeType = id == lk.jiat.butterqueen.R.id.chipBirthday ? "birthday"
                    : id == lk.jiat.butterqueen.R.id.chipWedding ? "wedding" : "ALL";
            filterProducts();
        });

        binding.emptyProductsActionButton.setOnClickListener(v -> {
            if (productsLoadError) {
                loadProducts();
            } else {
                binding.seeAllTextView.performClick();
            }
        });
        binding.seeAllTextView.setOnClickListener(v -> {
            selectedCategory = "ALL";
            selectedCategoryName = "ALL";
            selectedCategoryUnit = "";
            selectedCakeType = "ALL";
            binding.cakeOccasionChips.check(lk.jiat.butterqueen.R.id.chipAllOccasions);
            binding.cakeOccasionChips.setVisibility(View.VISIBLE);
            binding.searchEditText.setText("");
            filterProducts();
        });
    }

    private void loadCategories() {
        firestore.collection("categories")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (binding == null) return;
                    categoryList.clear();

                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Category category = doc.toObject(Category.class);
                        if (category != null && !Boolean.FALSE.equals(doc.getBoolean("active"))) {
                            category.setId(doc.getId());
                            categoryList.add(category);
                        }
                    }

                    categoryAdapter.notifyDataSetChanged();

                    if (categoryList.isEmpty() && isAdded()) {
                        Toast.makeText(requireContext(), "No categories found", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Failed to load categories: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void loadProducts() {
        if (binding == null) return;
        loadingProducts = true;
        productsLoadError = false;
        binding.homeProductsProgress.setVisibility(View.VISIBLE);
        binding.emptyProductsGroup.setVisibility(View.GONE);
        firestore.collection("products")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (binding == null) return;
                    loadingProducts = false;
                    productsLoadError = false;
                    binding.homeProductsProgress.setVisibility(View.GONE);
                    allProducts.clear();

                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Product product = doc.toObject(Product.class);
                        if (product != null && !Boolean.FALSE.equals(doc.getBoolean("available"))
                                && UnitPricing.isSupported(product.getUnitType())) {
                            product.setAvailable(true);
                            product.setId(doc.getId());
                            allProducts.add(product);
                        }
                    }

                    filterProducts();

                    // An accessible empty-state card explains the absence of products.
                })
                .addOnFailureListener(e -> {
                    if (binding == null) return;
                    loadingProducts = false;
                    productsLoadError = true;
                    binding.homeProductsProgress.setVisibility(View.GONE);
                    filterProducts();
                });
    }

    private void filterProducts() {
        if (binding == null) return;
        String keyword = "";

        if (binding.searchEditText.getText() != null) {
            keyword = binding.searchEditText.getText().toString().trim().toLowerCase(Locale.getDefault());
        }

        filteredProducts.clear();

        for (Product product : allProducts) {
            boolean matchesCategory = selectedCategory.equalsIgnoreCase("ALL")
                    || selectedCategory.equals(product.getCategoryId())
                    || selectedCategoryName.equalsIgnoreCase(product.getCategoryName());
            boolean matchesOccasion = selectedCakeType.equals("ALL")
                    || selectedCakeType.equalsIgnoreCase(product.getCakeType());

            boolean matchesKeyword =
                    (product.getName() != null
                            && product.getName().toLowerCase(Locale.getDefault()).contains(keyword))
                            || (product.getDescription() != null
                            && product.getDescription().toLowerCase(Locale.getDefault()).contains(keyword))
                            || (product.getCategoryName() != null
                            && product.getCategoryName().toLowerCase(Locale.getDefault()).contains(keyword));

            if (matchesCategory && matchesOccasion && matchesKeyword) {
                filteredProducts.add(product);
            }
        }

        productAdapter.notifyDataSetChanged();
        boolean showEmpty = !loadingProducts && (productsLoadError || filteredProducts.isEmpty());
        binding.emptyProductsGroup.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        binding.emptyProductsTextView.setText(productsLoadError
                ? "Couldn't load the bakery catalog. Check your connection and try again."
                : allProducts.isEmpty()
                    ? "Fresh treats are coming soon. Check back again!"
                    : "No treats match those filters.");
        binding.emptyProductsActionButton.setText(productsLoadError
                ? "Try again" : "Clear filters");
        binding.emptyProductsActionButton.setVisibility(!productsLoadError && allProducts.isEmpty()
                ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDestroyView() {
        if (notificationBell != null) {
            notificationBell.close();
            notificationBell = null;
        }
        super.onDestroyView();
        if (binding != null) {
            binding.recyclerViewCakes.setAdapter(null);
            binding.recyclerViewHomeCategories.setAdapter(null);
        }
        binding = null;
    }
}
