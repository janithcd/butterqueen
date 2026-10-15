package lk.jiat.butterqueen.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

import lk.jiat.butterqueen.R;
import lk.jiat.butterqueen.databinding.ActivitySignInBinding;

public class SignInActivity extends AppCompatActivity {

    private ActivitySignInBinding binding;
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivitySignInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        firebaseAuth = FirebaseAuth.getInstance();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.signInButton.setOnClickListener(v -> signInUser());

        binding.signUpText.setOnClickListener(v -> {
            Intent intent = new Intent(SignInActivity.this, SignUpActivity.class);
            startActivity(intent);
        });

        binding.forgotPasswordText.setOnClickListener(v -> {
            Intent reset = new Intent(this, ForgotPasswordActivity.class);
            if (binding.emailEditText.getText() != null) {
                reset.putExtra(ForgotPasswordActivity.EXTRA_EMAIL,
                        binding.emailEditText.getText().toString().trim());
            }
            // Do not retain the old password while the user visits a recovery screen.
            binding.passwordEditText.setText("");
            startActivity(reset);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (firebaseAuth.getCurrentUser() != null) {
            AccountHomeRouter.openHome(this);
        }
    }

    private void signInUser() {
        String email = binding.emailEditText.getText() != null
                ? binding.emailEditText.getText().toString().trim()
                : "";

        String password = binding.passwordEditText.getText() != null
                ? binding.passwordEditText.getText().toString().trim()
                : "";

        if (email.isEmpty()) {
            binding.emailEditText.setError("Enter email");
            binding.emailEditText.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailEditText.setError("Enter a valid email");
            binding.emailEditText.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            binding.passwordEditText.setError("Enter password");
            binding.passwordEditText.requestFocus();
            return;
        }

        binding.signInButton.setEnabled(false);
        binding.signInButton.setText("Signing In...");

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    binding.signInButton.setEnabled(true);
                    binding.signInButton.setText("Sign In");

                    if (task.isSuccessful()) {
                        Toast.makeText(SignInActivity.this, "Sign in successful", Toast.LENGTH_SHORT).show();

                        AccountHomeRouter.openHome(this);
                    } else {
                        String errorMessage = task.getException() != null
                                ? task.getException().getMessage()
                                : "Sign in failed";

                        Log.e("SIGN_IN_ERROR", errorMessage);

                        Toast.makeText(
                                SignInActivity.this,
                                errorMessage,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }
}