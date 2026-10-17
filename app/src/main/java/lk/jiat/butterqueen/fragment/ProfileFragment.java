package lk.jiat.butterqueen.fragment;
import lk.jiat.butterqueen.ui.UiFeedback;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import lk.jiat.butterqueen.R;
import lk.jiat.butterqueen.data.LocalProfilePhotoStore;
import lk.jiat.butterqueen.activity.MainActivity;
import lk.jiat.butterqueen.activity.SignInActivity;

public class ProfileFragment extends Fragment {

    private ImageView profileImageView;
    private TextView profileNameText;
    private TextView profileEmailText;
    private TextInputEditText firstNameProfileEditText;
    private TextInputEditText lastNameProfileEditText;
    private TextInputEditText emailProfileEditText;
    private TextInputEditText birthdayProfileEditText;
    private MaterialButton changePhotoButton;
    private MaterialButton updateProfileButton;
    private MaterialButton deleteAccountButton;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firebaseFirestore;
    private ListenerRegistration profileRegistration;
    private final ExecutorService photoWorker = Executors.newSingleThreadExecutor();
    private File cameraFile;
    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private boolean photoSaving;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && isAdded()) saveProfileImage(uri);
            });

    // TakePicture stores the full image in the app's private cache, not a small thumbnail.
    // No CAMERA or gallery/storage permission is needed when using the system apps.
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), saved -> {
                if (!isAdded()) return;
                File captured = cameraFile;
                if (saved && captured != null && captured.isFile()) {
                    saveProfileImage(Uri.fromFile(captured));
                } else {
                    removeCameraFile();
                    Toast.makeText(requireContext(), "Camera photo cancelled",
                            Toast.LENGTH_SHORT).show();
                }
            });

    public ProfileFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        if (state != null) {
            String fileName = state.getString("profileCameraFile");
            if (fileName != null) {
                cameraFile = new File(new File(requireContext().getCacheDir(),
                        "profile_camera"), fileName);
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (cameraFile != null) outState.putString("profileCameraFile", cameraFile.getName());
        super.onSaveInstanceState(outState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        profileImageView = view.findViewById(R.id.profileImageView);
        profileNameText = view.findViewById(R.id.profileNameText);
        profileEmailText = view.findViewById(R.id.profileEmailText);
        firstNameProfileEditText = view.findViewById(R.id.firstNameProfileEditText);
        lastNameProfileEditText = view.findViewById(R.id.lastNameProfileEditText);
        emailProfileEditText = view.findViewById(R.id.emailProfileEditText);
        birthdayProfileEditText = view.findViewById(R.id.birthdayProfileEditText);
        changePhotoButton = view.findViewById(R.id.changePhotoButton);
        updateProfileButton = view.findViewById(R.id.updateProfileButton);
        deleteAccountButton = view.findViewById(R.id.deleteAccountButton);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();

        listenToUserProfile();
        refreshLocalPhoto();

        birthdayProfileEditText.setOnClickListener(v -> showDatePicker());

        changePhotoButton.setOnClickListener(v -> choosePhotoSource());

        updateProfileButton.setOnClickListener(v -> updateProfile());

        deleteAccountButton.setOnClickListener(v -> showDeleteAccountDialog());

        return view;
    }

    private void listenToUserProfile() {
        if (firebaseAuth.getCurrentUser() == null) {
            return;
        }

        String userId = firebaseAuth.getCurrentUser().getUid();

        if (profileRegistration != null) profileRegistration.remove();
        profileRegistration = firebaseFirestore.collection("users")
                .document(userId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (!isAdded()) {
                        return;
                    }

                    if (error != null) {
                        Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String firstName = documentSnapshot.getString("firstName");
                        String lastName = documentSnapshot.getString("lastName");
                        String email = documentSnapshot.getString("email");
                        String birthday = documentSnapshot.getString("birthday");

                        String fullName = ((firstName != null ? firstName : "") + " " +
                                (lastName != null ? lastName : "")).trim();

                        if (fullName.isEmpty()) {
                            fullName = "ButterQueen User";
                        }

                        profileNameText.setText(fullName);
                        profileEmailText.setText(email != null ? email : "");

                        firstNameProfileEditText.setText(firstName != null ? firstName : "");
                        lastNameProfileEditText.setText(lastName != null ? lastName : "");
                        emailProfileEditText.setText(email != null ? email : "");
                        birthdayProfileEditText.setText(birthday != null ? birthday : "");

                        refreshLocalPhoto();
                    }
                });
    }

    private void updateProfile() {
        if (firebaseAuth.getCurrentUser() == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String firstName = String.valueOf(firstNameProfileEditText.getText()).trim();
        String lastName = String.valueOf(lastNameProfileEditText.getText()).trim();
        String birthday = String.valueOf(birthdayProfileEditText.getText()).trim();

        if (TextUtils.isEmpty(firstName)) {
            firstNameProfileEditText.setError("Enter first name");
            firstNameProfileEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(lastName)) {
            lastNameProfileEditText.setError("Enter last name");
            lastNameProfileEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(birthday)) {
            birthdayProfileEditText.setError("Select birthday");
            birthdayProfileEditText.requestFocus();
            return;
        }

        updateProfileButton.setEnabled(false);
        updateProfileButton.setText("Updating...");

        String userId = firebaseAuth.getCurrentUser().getUid();

        Map<String, Object> updates = new HashMap<>();
        updates.put("firstName", firstName);
        updates.put("lastName", lastName);
        updates.put("birthday", birthday);

        firebaseFirestore.collection("users")
                .document(userId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    updateProfileButton.setEnabled(true);
                    updateProfileButton.setText("Update Profile");
                    if (isAdded() && getView() != null)
                        UiFeedback.success(getView(), "Profile saved successfully");
                })
                .addOnFailureListener(e -> {
                    updateProfileButton.setEnabled(true);
                    updateProfileButton.setText("Update Profile");
                    Toast.makeText(requireContext(), "Update failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void choosePhotoSource() {
        if (photoSaving) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Change profile photo")
                .setItems(new String[]{"Take photo with camera", "Choose from gallery"},
                        (dialog, option) -> {
                            if (option == 0) takeCameraPhoto();
                            else imagePickerLauncher.launch("image/*");
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void takeCameraPhoto() {
        if (!isAdded() || photoSaving) return;
        try {
            File dir = new File(requireContext().getCacheDir(), "profile_camera");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create photo folder");
            removeCameraFile();
            cameraFile = File.createTempFile("profile_", ".jpg", dir);
            Uri destination = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".profileprovider", cameraFile);
            cameraLauncher.launch(destination);
        } catch (Exception error) {
            removeCameraFile();
            Toast.makeText(requireContext(), "Could not start camera: " + error.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void refreshLocalPhoto() {
        if (!isAdded() || profileImageView == null) return;
        String uid = firebaseAuth.getCurrentUser() == null
                ? null : firebaseAuth.getCurrentUser().getUid();
        LocalProfilePhotoStore.display(requireContext(), uid, profileImageView);
    }

    private void saveProfileImage(Uri imageUri) {
        if (!isAdded() || photoSaving) return;
        if (firebaseAuth.getCurrentUser() == null) {
            Toast.makeText(requireContext(), "Please sign in to save a photo",
                    Toast.LENGTH_LONG).show();
            removeCameraFile();
            return;
        }

        final String uid = firebaseAuth.getCurrentUser().getUid();
        final Context app = requireContext().getApplicationContext();
        setPhotoSaving(true);

        photoWorker.execute(() -> {
            try {
                // The selected gallery URI may be transient: decode it immediately,
                // normalize EXIF orientation and write an app-owned JPEG copy.
                ImageDecoder.Source source;
                if ("file".equals(imageUri.getScheme())) {
                    source = ImageDecoder.createSource(new File(imageUri.getPath()));
                } else {
                    source = ImageDecoder.createSource(app.getContentResolver(), imageUri);
                }
                Bitmap bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    int width = info.getSize().getWidth();
                    int height = info.getSize().getHeight();
                    float scale = Math.min(1f, 768f / Math.max(width, height));
                    if (scale < 1f) {
                        decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
                                Math.max(1, Math.round(height * scale)));
                    }
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });

                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try {
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                        throw new IOException("Cannot encode profile photo");
                    }
                } finally {
                    bitmap.recycle();
                }
                byte[] jpeg = output.toByteArray();
                if (jpeg.length == 0) throw new IOException("Empty profile photo");
                // Associate each local photo with its own Firebase Auth UID.
                if (FirebaseAuth.getInstance().getCurrentUser() == null
                        || !uid.equals(FirebaseAuth.getInstance().getCurrentUser().getUid())) {
                    throw new IOException("Account changed while saving photo");
                }
                LocalProfilePhotoStore.save(app, uid, jpeg);
                mainThread.post(() -> {
                    removeCameraFile();
                    if (!isAdded()) return;
                    setPhotoSaving(false);
                    if (firebaseAuth.getCurrentUser() != null
                            && uid.equals(firebaseAuth.getCurrentUser().getUid())) {
                        refreshLocalPhoto();
                        if (requireActivity() instanceof MainActivity) {
                            ((MainActivity) requireActivity()).refreshLocalAvatar();
                        }
                        Toast.makeText(requireContext(), "Profile photo saved on this device",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception | OutOfMemoryError error) {
                mainThread.post(() -> {
                    removeCameraFile();
                    if (!isAdded()) return;
                    setPhotoSaving(false);
                    Toast.makeText(requireContext(), "Could not save photo. Please try again.",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setPhotoSaving(boolean saving) {
        photoSaving = saving;
        if (changePhotoButton != null) {
            changePhotoButton.setEnabled(!saving);
            changePhotoButton.setText(saving ? "Saving photo..." : "Change Photo");
        }
    }

    private void removeCameraFile() {
        if (cameraFile != null) {
            if (cameraFile.exists()) cameraFile.delete();
            cameraFile = null;
        }
    }

    @Override
    public void onDestroyView() {
        if (profileRegistration != null) {
            profileRegistration.remove();
            profileRegistration = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        photoWorker.shutdown();
        super.onDestroy();
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to permanently delete your account?")
                .setPositiveButton("Delete", (dialog, which) -> deleteAccount())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void deleteAccount() {
        if (firebaseAuth.getCurrentUser() == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = firebaseAuth.getCurrentUser().getUid();

        firebaseAuth.getCurrentUser()
                .delete()
                .addOnSuccessListener(unused -> {
                    firebaseFirestore.collection("users")
                            .document(userId)
                            .delete();

                    LocalProfilePhotoStore.delete(requireContext(), userId);
                    firebaseAuth.signOut();

                    Toast.makeText(requireContext(), "Account deleted", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(requireContext(), SignInActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                })
                .addOnFailureListener(e -> Toast.makeText(
                        requireContext(),
                        "Delete failed. Please log in again and retry.",
                        Toast.LENGTH_LONG
                ).show());
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    String date = selectedDay + "/" + (selectedMonth + 1) + "/" + selectedYear;
                    birthdayProfileEditText.setText(date);
                    birthdayProfileEditText.setError(null);
                },
                year, month, day
        );

        datePickerDialog.show();
    }
}