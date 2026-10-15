package lk.jiat.butterqueen.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;

import lk.jiat.butterqueen.R;
import lk.jiat.butterqueen.data.PasswordResetPolicy;
import lk.jiat.butterqueen.databinding.ActivityForgotPasswordBinding;

/** Firebase Authentication email-link recovery for customers and bakery admins. */
public final class ForgotPasswordActivity extends AppCompatActivity {
    public static final String EXTRA_EMAIL = "email";
    private static final String KEY_CONFIRMATION_VISIBLE = "confirmation_visible";
    private static final String PREF_LAST_SENT = "last_successful_reset_email_at";

    private ActivityForgotPasswordBinding binding;
    private boolean submitting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.forgotPasswordRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left + dp(24), bars.top + dp(24),
                    bars.right + dp(24), bars.bottom + dp(24));
            return insets;
        });

        String suggested = PasswordResetPolicy.normalizeEmail(getIntent().getStringExtra(EXTRA_EMAIL));
        if (!suggested.isEmpty()) binding.resetEmailEditText.setText(suggested);
        if (savedInstanceState != null
                && savedInstanceState.getBoolean(KEY_CONFIRMATION_VISIBLE, false)) {
            binding.resetResultCard.setVisibility(android.view.View.VISIBLE);
            binding.sendResetEmailButton.setText("Resend email");
        }

        binding.resetBackButton.setOnClickListener(v -> finish());
        binding.resetBackToLogin.setOnClickListener(v -> finish());
        binding.sendResetEmailButton.setOnClickListener(v -> sendResetEmail());
        binding.resetEmailEditText.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            sendResetEmail();
            return true;
        });
        binding.resetOpenEmailButton.setOnClickListener(v -> openEmailApp());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_CONFIRMATION_VISIBLE,
                binding.resetResultCard.getVisibility() == android.view.View.VISIBLE);
        super.onSaveInstanceState(outState);
    }

    private void sendResetEmail() {
        if (submitting) return;

        final String email = PasswordResetPolicy.normalizeEmail(
                binding.resetEmailEditText.getText() == null ? ""
                        : binding.resetEmailEditText.getText().toString());
        binding.resetEmailLayout.setError(null);
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.resetEmailLayout.setError("Enter a valid email address");
            binding.resetEmailEditText.requestFocus();
            return;
        }

        // Limit accidental repeated taps; Firebase enforces actual abuse quotas.
        long now = System.currentTimeMillis();
        long last = getPreferences(MODE_PRIVATE).getLong(PREF_LAST_SENT, 0L);
        if (!PasswordResetPolicy.canSend(now, last)) {
            Toast.makeText(this, "Please wait " +
                    PasswordResetPolicy.secondsUntilNext(now, last) +
                    " seconds before requesting another email.", Toast.LENGTH_LONG).show();
            return;
        }

        setSubmitting(true);
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnCompleteListener(this, task -> {
                    if (isFinishing() || isDestroyed()) return;
                    setSubmitting(false);
                    if (task.isSuccessful()) {
                        showConfirmation();
                        return;
                    }

                    Exception error = task.getException();
                    // Some Firebase configurations still return USER_NOT_FOUND.
                    // Never expose whether a registered account exists.
                    if (error instanceof FirebaseAuthException) {
                        String code = ((FirebaseAuthException) error).getErrorCode();
                        if ("ERROR_USER_NOT_FOUND".equals(code)
                                || "ERROR_EMAIL_NOT_FOUND".equals(code)) {
                            showConfirmation();
                            return;
                        }
                    }
                    if (error instanceof FirebaseTooManyRequestsException) {
                        Toast.makeText(this,
                                "Too many requests. Please wait and try again later.",
                                Toast.LENGTH_LONG).show();
                    } else if (error instanceof FirebaseNetworkException) {
                        Toast.makeText(this,
                                "No internet connection. Connect and try again.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,
                                "Unable to request a reset email right now. Please try again.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showConfirmation() {
        getPreferences(MODE_PRIVATE).edit()
                .putLong(PREF_LAST_SENT, System.currentTimeMillis()).apply();
        binding.resetResultCard.setVisibility(android.view.View.VISIBLE);
        binding.sendResetEmailButton.setText("Resend email");
        Toast.makeText(this, "Check your inbox and spam folder", Toast.LENGTH_LONG).show();
    }

    private void setSubmitting(boolean value) {
        submitting = value;
        binding.resetEmailEditText.setEnabled(!value);
        binding.sendResetEmailButton.setEnabled(!value);
        binding.sendResetEmailButton.setText(value ? "Requesting reset..."
                : binding.resetResultCard.getVisibility() == android.view.View.VISIBLE
                    ? "Resend email" : "Send reset link");
    }

    private void openEmailApp() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_APP_EMAIL);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this,
                    "Open your email app and look for the Firebase password reset message.",
                    Toast.LENGTH_LONG).show();
        }
    }
}
