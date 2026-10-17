package lk.jiat.butterqueen.activity;

import android.app.Activity;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.jiat.butterqueen.R;
import lk.jiat.butterqueen.notification.OrderNotificationHelper;
import lk.jiat.butterqueen.data.CartManager;
import lk.jiat.butterqueen.model.CartItem;
import lk.jiat.butterqueen.model.Product;
import lk.jiat.butterqueen.model.UnitPricing;
import lk.jiat.butterqueen.payment.PayHereConfig;
import lk.payhere.androidsdk.PHConfigs;
import lk.payhere.androidsdk.PHConstants;
import lk.payhere.androidsdk.PHMainActivity;
import lk.payhere.androidsdk.model.InitRequest;
import lk.payhere.androidsdk.model.Item;
import lk.payhere.androidsdk.model.StatusResponse;
import lk.payhere.androidsdk.PHResponse;

public class CheckoutActivity extends AppCompatActivity {

    private static final int PAYHERE_REQUEST = 11001;

    private NestedScrollView checkoutScrollView;
    private LinearLayout checkoutContent;

    private TextInputEditText fullNameEditText;
    private TextInputEditText phoneEditText;
    private TextInputEditText addressEditText;
    private TextInputEditText noteEditText;

    private TextView itemsTextView;
    private TextView subtotalTextView;
    private TextView deliveryFeeTextView;
    private TextView totalTextView;
    private TextView deliveryStatusTextView;
    private TextView deliveryDistanceTextView;

    private MaterialButton placeOrderButton;
    private RadioGroup paymentMethodRadioGroup;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler addressHandler = new Handler(Looper.getMainLooper());

    private Runnable addressRunnable;

    private double currentDeliveryFee = 0.0;
    private boolean deliveryAvailable = false;
    private boolean deliveryCalculated = false;
    private boolean checkingCatalog = false;

    private String latestAddressQuery = "";

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private String pendingFullName = "";
    private String pendingPhone = "";
    private String pendingAddress = "";
    private String pendingNote = "";
    private String pendingPaymentReference = "";
    private List<CartItem> pendingCartItems = new ArrayList<>();

    private static final double CENTER_LAT = 6.977828;
    private static final double CENTER_LNG = 79.927152;

    private static final double FIRST_BAND_METERS = 10_000;
    private static final double SECOND_BAND_METERS = 20_000;
    private static final double THIRD_BAND_METERS = 30_000;

    private static final double FIRST_BAND_PRICE = 800.00;
    private static final double SECOND_BAND_PRICE = 1500.00;
    private static final double THIRD_BAND_PRICE = 2500.00;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_checkout);

        // Android 13+: ask the customer before showing order notifications.
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1201);
        }

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        initViews();
        findViewById(R.id.checkoutBack).setOnClickListener(view -> finish());
        setupWindowInsets();
        setupAddressWatcher();
        setupActions();
        setupScrollHelpers();

        placeOrderButton.setEnabled(false);
        loadCartForCheckout();
    }

    private void initViews() {
        checkoutScrollView = findViewById(R.id.checkoutScrollView);
        checkoutContent = findViewById(R.id.checkoutContent);

        fullNameEditText = findViewById(R.id.fullNameEditText);
        phoneEditText = findViewById(R.id.phoneEditText);
        addressEditText = findViewById(R.id.addressEditText);
        noteEditText = findViewById(R.id.noteEditText);

        itemsTextView = findViewById(R.id.checkoutItemsTextView);
        subtotalTextView = findViewById(R.id.checkoutSubtotalTextView);
        deliveryFeeTextView = findViewById(R.id.checkoutDeliveryFeeTextView);
        totalTextView = findViewById(R.id.checkoutTotalTextView);
        deliveryStatusTextView = findViewById(R.id.deliveryStatusTextView);
        deliveryDistanceTextView = findViewById(R.id.deliveryDistanceTextView);

        placeOrderButton = findViewById(R.id.placeOrderButton);
        paymentMethodRadioGroup = findViewById(R.id.paymentMethodRadioGroup);
    }

    private void loadCartForCheckout() {
        CartManager.loadCartOnce(new CartManager.CartLoadListener() {
            @Override
            public void onCartUpdated(List<CartItem> cartItems) {
                if (cartItems.isEmpty()) {
                    Toast.makeText(CheckoutActivity.this, "Your cart is empty", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                loadSummary();
                placeOrderButton.setEnabled(true);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(CheckoutActivity.this, message, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(checkoutScrollView, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());

            int bottomInset = Math.max(systemBars.bottom, imeInsets.bottom);

            view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    bottomInset
            );

            return insets;
        });
    }

    private void setupScrollHelpers() {
        setupAutoScroll(fullNameEditText);
        setupAutoScroll(phoneEditText);
        setupAutoScroll(addressEditText);
        setupAutoScroll(noteEditText);

        checkoutContent.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        View focused = getCurrentFocus();
                        if (focused != null) {
                            scrollToView(focused);
                        }
                    }
                }
        );
    }

    private void setupAutoScroll(View targetView) {
        targetView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollToView(v);
            }
        });

        targetView.setOnClickListener(v -> scrollToView(v));
    }

    private void scrollToView(View view) {
        checkoutScrollView.post(() -> {
            int y = view.getTop() - 120;
            if (y < 0) y = 0;
            checkoutScrollView.smoothScrollTo(0, y);
        });
    }

    private void loadSummary() {
        int totalItems = CartManager.getTotalItems();
        double subtotal = CartManager.getTotalPrice();
        double grandTotal = UnitPricing.roundMoney(subtotal + currentDeliveryFee);

        itemsTextView.setText("Products: " + totalItems);
        subtotalTextView.setText(String.format(Locale.getDefault(), "Subtotal: Rs. %.2f", subtotal));
        deliveryFeeTextView.setText(String.format(Locale.getDefault(), "Delivery Fee: Rs. %.2f", currentDeliveryFee));
        totalTextView.setText(String.format(Locale.getDefault(), "Total: Rs. %.2f", grandTotal));
    }

    private void setupAddressWatcher() {
        addressEditText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void onTextChanged(String text) {
                latestAddressQuery = text;

                if (addressRunnable != null) {
                    addressHandler.removeCallbacks(addressRunnable);
                }

                if (text.length() < 8) {
                    deliveryCalculated = false;
                    deliveryAvailable = false;
                    currentDeliveryFee = 0.0;
                    deliveryStatusTextView.setText("Enter a fuller delivery address");
                    deliveryDistanceTextView.setText("Distance: -");
                    loadSummary();
                    return;
                }

                deliveryStatusTextView.setText("Checking delivery area...");
                deliveryDistanceTextView.setText("Distance: calculating...");

                addressRunnable = () -> calculateDeliveryFromAddress(text);
                addressHandler.postDelayed(addressRunnable, 1000);
            }
        });
    }

    private void setupActions() {
        placeOrderButton.setOnClickListener(v -> {
            hideKeyboard();
            placeOrder();
        });
    }

    private void calculateDeliveryFromAddress(String addressText) {
        if (!Geocoder.isPresent()) {
            deliveryCalculated = false;
            deliveryAvailable = false;
            currentDeliveryFee = 0.0;
            deliveryStatusTextView.setText("Address service not available");
            deliveryDistanceTextView.setText("Distance: -");
            loadSummary();
            return;
        }

        executorService.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addressList = geocoder.getFromLocationName(addressText + ", Sri Lanka", 1);

                runOnUiThread(() -> {
                    if (!addressText.equals(latestAddressQuery)) {
                        return;
                    }

                    if (addressList == null || addressList.isEmpty()) {
                        deliveryCalculated = false;
                        deliveryAvailable = false;
                        currentDeliveryFee = 0.0;
                        deliveryStatusTextView.setText("Address not found");
                        deliveryDistanceTextView.setText("Distance: -");
                        loadSummary();
                        return;
                    }

                    Address address = addressList.get(0);
                    DeliveryBandResult result = calculateCharge(address.getLatitude(), address.getLongitude());
                    applyDeliveryResult(result);
                });

            } catch (IOException e) {
                runOnUiThread(() -> {
                    if (!addressText.equals(latestAddressQuery)) {
                        return;
                    }

                    deliveryCalculated = false;
                    deliveryAvailable = false;
                    currentDeliveryFee = 0.0;
                    deliveryStatusTextView.setText("Failed to calculate delivery");
                    deliveryDistanceTextView.setText("Distance: -");
                    loadSummary();
                });
            }
        });
    }

    private DeliveryBandResult calculateCharge(double lat, double lng) {
        float[] results = new float[1];
        Location.distanceBetween(CENTER_LAT, CENTER_LNG, lat, lng, results);

        double distanceMeters = results[0];
        double distanceKm = distanceMeters / 1000.0;

        if (distanceMeters <= FIRST_BAND_METERS) {
            return new DeliveryBandResult(true, "0 - 10 km", FIRST_BAND_PRICE, distanceKm);
        } else if (distanceMeters <= SECOND_BAND_METERS) {
            return new DeliveryBandResult(true, "10 - 20 km", SECOND_BAND_PRICE, distanceKm);
        } else if (distanceMeters <= THIRD_BAND_METERS) {
            return new DeliveryBandResult(true, "20 - 30 km", THIRD_BAND_PRICE, distanceKm);
        } else {
            return new DeliveryBandResult(false, "Over 30 km", 0.0, distanceKm);
        }
    }

    private void applyDeliveryResult(DeliveryBandResult result) {
        deliveryCalculated = true;
        deliveryAvailable = result.isWithinArea;
        currentDeliveryFee = result.price;

        if (result.isWithinArea) {
            deliveryStatusTextView.setText("Delivery available • " + result.bandLabel);
            deliveryDistanceTextView.setText(
                    String.format(Locale.getDefault(), "Distance: %.2f km", result.distanceKm)
            );
            deliveryFeeTextView.setText(
                    String.format(Locale.getDefault(), "Delivery Fee: Rs. %.2f", result.price)
            );
        } else {
            deliveryStatusTextView.setText("Outside delivery area");
            deliveryDistanceTextView.setText(
                    String.format(Locale.getDefault(), "Distance: %.2f km", result.distanceKm)
            );
            deliveryFeeTextView.setText("Delivery Fee: Not available");
        }

        loadSummary();
    }

    private void placeOrder() {
        if (checkingCatalog) return;
        if (firebaseAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Please sign in again", Toast.LENGTH_SHORT).show();
            return;
        }

        List<CartItem> currentCart = new ArrayList<>(CartManager.getCartItems());
        if (currentCart.isEmpty()) {
            Toast.makeText(this, "Your cart is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        for (CartItem item : currentCart) {
            if (!UnitPricing.isSupported(item.getUnitType())) {
                Toast.makeText(this, "Remove and re-add items with old box/pack units",
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        String fullName = getText(fullNameEditText);
        String phone = getText(phoneEditText);
        String address = getText(addressEditText);
        String note = getText(noteEditText);

        if (fullName.isEmpty()) {
            fullNameEditText.setError("Enter full name");
            fullNameEditText.requestFocus();
            return;
        }

        if (phone.isEmpty()) {
            phoneEditText.setError("Enter phone number");
            phoneEditText.requestFocus();
            return;
        }

        if (phone.length() < 10) {
            phoneEditText.setError("Enter valid phone number");
            phoneEditText.requestFocus();
            return;
        }

        if (address.isEmpty()) {
            addressEditText.setError("Enter delivery address");
            addressEditText.requestFocus();
            return;
        }

        if (!deliveryCalculated) {
            Toast.makeText(this, "Please wait until delivery fee is calculated", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!deliveryAvailable) {
            Toast.makeText(this, "Sorry, this address is outside the delivery area", Toast.LENGTH_SHORT).show();
            return;
        }

        int checkedId = paymentMethodRadioGroup.getCheckedRadioButtonId();

        checkingCatalog = true;
        placeOrderButton.setEnabled(false);
        verifyCartAgainstCatalog(currentCart, 0, () -> {
            checkingCatalog = false;
            placeOrderButton.setEnabled(true);
            if (checkedId == R.id.cardRadioButton) {
                startPayHerePayment(currentCart, fullName, phone, address, note);
            } else {
                saveOrderToFirestore(currentCart, fullName, phone, address, note,
                        "cash_on_delivery", "", "pending");
            }
        });
    }

    private void verifyCartAgainstCatalog(List<CartItem> cart, int index, Runnable onValid) {
        if (index >= cart.size()) {
            onValid.run();
            return;
        }
        CartItem item = cart.get(index);
        if (item.getProductId() == null || item.getProductId().isEmpty()) {
            catalogChanged();
            return;
        }
        firestore.collection("products").document(item.getProductId()).get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    Product product = doc.toObject(Product.class);
                    if (!doc.exists() || product == null
                            || Boolean.FALSE.equals(doc.getBoolean("available"))
                            || !UnitPricing.isSupported(product.getUnitType())
                            || !UnitPricing.normalize(product.getUnitType(),
                            product.getCategoryName()).equals(
                            UnitPricing.normalize(item.getUnitType(), null))
                            || Math.abs(product.getPrice() - item.getProductPrice()) > 0.005) {
                        catalogChanged();
                        return;
                    }
                    verifyCartAgainstCatalog(cart, index + 1, onValid);
                })
                .addOnFailureListener(error -> {
                    checkingCatalog = false;
                    placeOrderButton.setEnabled(true);
                    Toast.makeText(this, "Could not verify current product prices. Retry online.",
                            Toast.LENGTH_LONG).show();
                });
    }

    private void catalogChanged() {
        checkingCatalog = false;
        placeOrderButton.setEnabled(true);
        Toast.makeText(this, "A product or price changed. Remove it and add it again.",
                Toast.LENGTH_LONG).show();
    }

    private void startPayHerePayment(List<CartItem> currentCart,
                                     String fullName,
                                     String phone,
                                     String address,
                                     String note) {

        if (PayHereConfig.MERCHANT_ID.isEmpty()) {
            Toast.makeText(this, "PayHere sandbox is not configured on this device", Toast.LENGTH_LONG).show();
            return;
        }

        double subtotal = 0.0;
        for (CartItem item : currentCart) {
            subtotal += item.getItemTotal();
        }
        double totalAmount = UnitPricing.roundMoney(subtotal + currentDeliveryFee);

        String[] nameParts = fullName.trim().split("\\s+", 2);
        String firstName = nameParts.length > 0 ? nameParts[0] : "Customer";
        String lastName = nameParts.length > 1 ? nameParts[1] : ".";

        pendingPaymentReference = "BQ-" + System.currentTimeMillis();
        pendingFullName = fullName;
        pendingPhone = phone;
        pendingAddress = address;
        pendingNote = note;
        pendingCartItems = new ArrayList<>(currentCart);

        InitRequest req = new InitRequest();
        req.setMerchantId(PayHereConfig.MERCHANT_ID);
        req.setCurrency("LKR");
        req.setAmount(totalAmount);
        req.setOrderId(pendingPaymentReference);
        req.setItemsDescription("Butter Queen Order");

        req.getCustomer().setFirstName(firstName);
        req.getCustomer().setLastName(lastName);
        req.getCustomer().setEmail(
                firebaseAuth.getCurrentUser() != null && firebaseAuth.getCurrentUser().getEmail() != null
                        ? firebaseAuth.getCurrentUser().getEmail()
                        : "customer@example.com"
        );
        req.getCustomer().setPhone(phone);
        req.getCustomer().getAddress().setAddress(address);
        req.getCustomer().getAddress().setCity(PayHereConfig.DEFAULT_CITY);
        req.getCustomer().getAddress().setCountry("Sri Lanka");

        for (CartItem cartItem : currentCart) {
            String itemName = cartItem.getProductName();

            itemName += " (" + UnitPricing.quantityLabel(
                    cartItem.getQuantity(), cartItem.getUnitType()) + ")";

            req.getItems().add(new Item(
                    null,
                    itemName,
                    1,
                    cartItem.getItemTotal()
            ));
        }

        // Sandbox only until a trusted backend verifies payment notifications.
        PHConfigs.setBaseUrl(PHConfigs.SANDBOX_URL);

        Intent intent = new Intent(this, PHMainActivity.class);
        intent.putExtra(PHConstants.INTENT_EXTRA_DATA, req);
        startActivityForResult(intent, PAYHERE_REQUEST);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PAYHERE_REQUEST
                && data != null
                && data.hasExtra(PHConstants.INTENT_EXTRA_RESULT)) {

            PHResponse<StatusResponse> response =
                    (PHResponse<StatusResponse>) data.getSerializableExtra(PHConstants.INTENT_EXTRA_RESULT);

            if (resultCode == Activity.RESULT_OK) {
                if (response != null && response.isSuccess()) {
                    saveOrderToFirestore(
                            pendingCartItems,
                            pendingFullName,
                            pendingPhone,
                            pendingAddress,
                            pendingNote,
                            "payhere",
                            pendingPaymentReference,
                            "verification_pending"
                    );

                } else {
                    String msg = response != null ? response.toString() : "Payment failed";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }

            } else if (resultCode == Activity.RESULT_CANCELED) {
                String msg = response != null ? response.toString() : "User canceled the payment";
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void saveOrderToFirestore(List<CartItem> currentCart,
                                      String fullName,
                                      String phone,
                                      String address,
                                      String note,
                                      String paymentMethod,
                                      String paymentReference,
                                      String paymentStatus) {

        placeOrderButton.setEnabled(false);
        placeOrderButton.setText("Placing Order...");

        String userId = firebaseAuth.getCurrentUser().getUid();
        long createdAtMillis = System.currentTimeMillis();

        double subtotal = 0.0;
        for (CartItem item : currentCart) {
            subtotal += item.getItemTotal();
        }

        double totalAmount = subtotal + currentDeliveryFee;
        int totalItems = currentCart.size();

        DocumentReference orderRef = firestore.collection("orders").document();
        WriteBatch batch = firestore.batch();

        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("orderId", orderRef.getId());
        orderMap.put("userId", userId);
        orderMap.put("customerName", fullName);
        orderMap.put("customerPhone", phone);
        orderMap.put("deliveryAddress", address);
        orderMap.put("note", note);
        orderMap.put("subtotalAmount", subtotal);
        orderMap.put("deliveryFee", currentDeliveryFee);
        orderMap.put("totalAmount", totalAmount);
        orderMap.put("totalItems", totalItems);
        orderMap.put("paymentMethod", paymentMethod);
        orderMap.put("paymentReference", paymentReference);
        orderMap.put("paymentStatus", paymentStatus);
        orderMap.put("orderStatus", "payhere".equals(paymentMethod) ? "payment_pending" : "placed");
        orderMap.put("createdAtMillis", createdAtMillis);

        batch.set(orderRef, orderMap);

        for (CartItem item : currentCart) {
            DocumentReference itemRef = orderRef.collection("items").document();

            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("orderItemId", itemRef.getId());
            itemMap.put("productId", item.getProductId());
            itemMap.put("productName", item.getProductName());
            itemMap.put("productImageUrl", item.getProductImageUrl());
            itemMap.put("unitPrice", item.getProductPrice());
            itemMap.put("quantity", item.getQuantity());
            itemMap.put("unitType", item.getUnitType());
            itemMap.put("selectedSize", item.getSelectedSize());
            itemMap.put("selectedFlavor", item.getSelectedFlavor());
            itemMap.put("cakeMessage", item.getCakeMessage());
            itemMap.put("itemTotal", item.getItemTotal());

            batch.set(itemRef, itemMap);

            if (item.getCartItemId() != null && !item.getCartItemId().trim().isEmpty()) {
                DocumentReference cartDocRef = firestore.collection("users")
                        .document(userId)
                        .collection("cart")
                        .document(item.getCartItemId());

                batch.delete(cartDocRef);
            }
        }

        batch.commit()
                .addOnSuccessListener(unused -> {
                    OrderNotificationHelper.showOrderPlaced(
                            this, orderRef.getId(), "payhere".equals(paymentMethod));
                    CartManager.clearLocalCache();

                    String message =
                            ("payhere".equals(paymentMethod)
                                    ? "Sandbox payment submitted. Awaiting independent verification.\n\n"
                                    : "Order placed successfully!\n\n") +
                                    "Customer: " + fullName + "\n" +
                                    "Phone: " + phone + "\n" +
                                    "Address: " + address + "\n" +
                                    "Payment: " + formatPaymentMethod(paymentMethod) + "\n" +
                                    String.format(Locale.getDefault(), "Delivery Fee: Rs. %.2f\n", currentDeliveryFee) +
                                    String.format(Locale.getDefault(), "Total: Rs. %.2f", totalAmount) +
                                    (note.isEmpty() ? "" : "\n\nNote: " + note);

                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                            .setTitle("ButterQueen")
                            .setMessage(message)
                            .setCancelable(false)
                            .setPositiveButton("OK", (dialog, which) -> {
                                Toast.makeText(this, "payhere".equals(paymentMethod)
                                                ? "Payment verification pending" : "Thank you for your order",
                                        Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .show();
                })
                .addOnFailureListener(e -> {
                    placeOrderButton.setEnabled(true);
                    placeOrderButton.setText("Place Order");
                    Toast.makeText(this, "Failed to place order: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private String formatPaymentMethod(String paymentMethod) {
        if ("cash_on_delivery".equalsIgnoreCase(paymentMethod)) {
            return "Cash on Delivery";
        } else if ("payhere".equalsIgnoreCase(paymentMethod)) {
            return "PayHere";
        }
        return paymentMethod;
    }

    private String getText(TextInputEditText editText) {
        if (editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    private void hideKeyboard() {
        View currentView = getCurrentFocus();
        if (currentView != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(currentView.getWindowToken(), 0);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (addressRunnable != null) {
            addressHandler.removeCallbacks(addressRunnable);
        }
        executorService.shutdown();
    }

    private static class DeliveryBandResult {
        final boolean isWithinArea;
        final String bandLabel;
        final double price;
        final double distanceKm;

        DeliveryBandResult(boolean isWithinArea, String bandLabel, double price, double distanceKm) {
            this.isWithinArea = isWithinArea;
            this.bandLabel = bandLabel;
            this.price = price;
            this.distanceKm = distanceKm;
        }
    }
}
