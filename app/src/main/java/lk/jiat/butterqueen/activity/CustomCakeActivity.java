package lk.jiat.butterqueen.activity;
import lk.jiat.butterqueen.ui.UiFeedback;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lk.jiat.butterqueen.R;
import lk.jiat.butterqueen.notification.CustomCakeLocalAlert;

public class CustomCakeActivity extends AppCompatActivity {
    // Demo on Spark: users can preview/select a photo, but it cannot be uploaded yet.
    // Change this only after configuring Firebase Storage and its security rules.
    private static final boolean PHOTO_UPLOAD_ENABLED = false;
    private EditText messageInput, dateInput, notesInput;
    private Spinner flavourInput, weightInput;
    private ImageView photoPreview;
    private Button submitButton, takePhotoButton, galleryButton, removePhotoButton;
    private byte[] photoBytes;

    private final ActivityResultLauncher<Void> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap != null) setPhoto(bitmap);
            });

    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                try (java.io.InputStream stream = getContentResolver().openInputStream(uri)) {
                    if (stream == null) throw new java.io.IOException("Image unavailable");
                    android.graphics.Bitmap image = android.graphics.BitmapFactory.decodeStream(stream);
                    if (image == null) throw new java.io.IOException("Unsupported image");
                    setPhoto(image);
                } catch (Exception e) {
                    Toast.makeText(this, "Could not load the photo", Toast.LENGTH_LONG).show();
                }
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_custom_cake);
        applyWindowInsets();
        findViewById(R.id.customBack).setOnClickListener(v -> finish());
        messageInput = findViewById(R.id.customMessage);
        dateInput = findViewById(R.id.customDate);
        notesInput = findViewById(R.id.customNotes);
        flavourInput = findViewById(R.id.customFlavour);
        weightInput = findViewById(R.id.customWeight);
        photoPreview = findViewById(R.id.customPhotoPreview);
        submitButton = findViewById(R.id.customSubmit);
        takePhotoButton = findViewById(R.id.customTakePhoto);
        galleryButton = findViewById(R.id.customGallery);
        removePhotoButton = findViewById(R.id.customRemovePhoto);

        flavourInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Vanilla", "Chocolate", "Ribbon", "Red Velvet", "Other"}));
        weightInput.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"0.5 kg", "1 kg", "1.5 kg", "2 kg", "3 kg", "Other"}));
        dateInput.setOnClickListener(v -> {
            java.util.Calendar c = java.util.Calendar.getInstance();
            android.app.DatePickerDialog picker = new android.app.DatePickerDialog(this,
                    (view, year, month, day) -> dateInput.setText(String.format(java.util.Locale.US,
                            "%04d-%02d-%02d", year, month + 1, day)),
                    c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH),
                    c.get(java.util.Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            picker.show();
        });
        takePhotoButton.setOnClickListener(v -> cameraLauncher.launch(null));
        galleryButton.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        removePhotoButton.setOnClickListener(v -> clearSelectedPhoto());
        submitButton.setOnClickListener(v -> submitRequest());
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.customCakeRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void setPhoto(Bitmap bitmap) {
        // Keep a compact local image for on-device preview.
        int max = 1280;
        float scale = Math.min(1f, max / (float) Math.max(bitmap.getWidth(), bitmap.getHeight()));
        Bitmap sized = scale < 1 ? Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * scale)),
                Math.max(1, Math.round(bitmap.getHeight() * scale)), true) : bitmap;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        sized.compress(Bitmap.CompressFormat.JPEG, 80, out);
        photoBytes = out.toByteArray();
        photoPreview.setImageBitmap(sized);
        photoPreview.setVisibility(View.VISIBLE);
        removePhotoButton.setVisibility(View.VISIBLE);
    }

    // Removes the *selected* image before submission, not an image already uploaded to Firebase.
    private void clearSelectedPhoto() {
        if (!submitButton.isEnabled()) return;
        photoBytes = null;
        photoPreview.setImageDrawable(null);
        photoPreview.setVisibility(View.GONE);
        removePhotoButton.setVisibility(View.GONE);
        UiFeedback.success(removePhotoButton, "Reference photo removed");
    }

    private void submitRequest() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please sign in before sending a request", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SignInActivity.class));
            return;
        }
        String date = dateInput.getText().toString().trim();
        if (date.isEmpty()) {
            dateInput.setError("Please choose a required date");
            return;
        }
        submitButton.setEnabled(false);
        submitButton.setText("Sending request...");
        // Prevent form edits while submitting.
        takePhotoButton.setEnabled(false);
        galleryButton.setEnabled(false);
        removePhotoButton.setEnabled(false);

        if (photoBytes != null && !PHOTO_UPLOAD_ENABLED) {
            new AlertDialog.Builder(this)
                    .setTitle("Photo preview only")
                    .setMessage("Photo upload is not available on this demo plan. "
                            + "Send your cake details without the reference photo? "
                            + "The bakery will not receive this picture.")
                    .setPositiveButton("Send details only", (dialog, which) ->
                            saveRequest(user, date, "", ""))
                    .setNegativeButton("Keep editing", (dialog, which) -> resetSubmitControls())
                    .setOnCancelListener(dialog -> resetSubmitControls())
                    .show();
            return;
        }

        if (photoBytes != null) {
            String path = "custom_cake_requests/" + user.getUid() + "/" + UUID.randomUUID() + ".jpg";
            StorageReference ref = FirebaseStorage.getInstance().getReference().child(path);
            ref.putBytes(photoBytes).continueWithTask(task -> {
                        if (!task.isSuccessful()) throw task.getException() != null
                                ? task.getException() : new IllegalStateException("Upload failed");
                        return ref.getDownloadUrl();
                    }).addOnSuccessListener(url -> saveRequest(user, date, url.toString(), path))
                    .addOnFailureListener(error -> showFailure("Photo upload failed. Check Firebase Storage rules."));
        } else {
            saveRequest(user, date, "", "");
        }
    }

    private void saveRequest(FirebaseUser user, String date, String photoUrl, String photoPath) {
        Map<String, Object> request = new HashMap<>();
        request.put("userId", user.getUid());
        request.put("userEmail", user.getEmail() == null ? "" : user.getEmail());
        request.put("flavour", flavourInput.getSelectedItem().toString());
        request.put("weight", weightInput.getSelectedItem().toString());
        request.put("requiredDate", date);
        request.put("cakeMessage", messageInput.getText().toString().trim());
        request.put("notes", notesInput.getText().toString().trim());
        request.put("referenceImageUrl", photoUrl);
        request.put("referenceImagePath", photoPath);
        request.put("status", "pending");
        request.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection("customCakeRequests").add(request)
                .addOnSuccessListener(doc -> {
                    // No Cloud Functions on Spark: confirm submission locally.
                    // The saved Firestore request also appears in both roles' bell feeds.
                    CustomCakeLocalAlert.show(this, "customer_cake_" + doc.getId(),
                            "Custom cake request sent",
                            "The bakery received your request and will review it.");
                    UiFeedback.success(submitButton,
                            "Request received! Your bakery team will review it.");
                    submitButton.postDelayed(() -> {
                        if (!isFinishing() && !isDestroyed()) finish();
                    }, 1250);
                }).addOnFailureListener(error -> showFailure("Could not submit request: " + error.getMessage()));
    }

    private void showFailure(String message) {
        resetSubmitControls();
        UiFeedback.error(submitButton, message);
    }

    private void resetSubmitControls() {
        submitButton.setEnabled(true);
        submitButton.setText("Submit Custom Cake Request");
        takePhotoButton.setEnabled(true);
        galleryButton.setEnabled(true);
        removePhotoButton.setEnabled(photoBytes != null);
    }
}
