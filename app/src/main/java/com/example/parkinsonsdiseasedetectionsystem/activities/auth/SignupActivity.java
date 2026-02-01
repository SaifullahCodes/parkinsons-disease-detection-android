package com.example.parkinsonsdiseasedetectionsystem.activities.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.activities.AdminActivity;
import com.example.parkinsonsdiseasedetectionsystem.activities.DoctorActivity;
import com.example.parkinsonsdiseasedetectionsystem.activities.MainActivity;
import com.example.parkinsonsdiseasedetectionsystem.models.User;
import com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils;
import com.example.parkinsonsdiseasedetectionsystem.utils.FirebaseAuthUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SignupActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etPassword, etConfirmPassword;
    private TextInputLayout inputLayoutName, inputLayoutEmail, inputLayoutPassword, inputLayoutConfirmPassword;
    private CheckBox cbTerms;
    private MaterialButton btnSignUp;
    private ProgressBar progressBar;

    private String selectedRole = "patient";
    private final Executor executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Get role from previous activity if passed
        if (getIntent() != null && getIntent().hasExtra("role")) {
            String role = getIntent().getStringExtra("role");
            if (role != null) {
                selectedRole = role.equalsIgnoreCase("Doctor") ? "doctor" :
                              role.equalsIgnoreCase("Admin") ? "admin" : "patient";
            }
        }

        initializeViews();
        setupClickListeners();
    }

    private void initializeViews() {
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        inputLayoutName = findViewById(R.id.inputLayoutName);
        inputLayoutEmail = findViewById(R.id.inputLayoutEmail);
        inputLayoutPassword = findViewById(R.id.inputLayoutPassword);
        inputLayoutConfirmPassword = findViewById(R.id.inputLayoutConfirmPassword);

        cbTerms = findViewById(R.id.cbTerms);
        btnSignUp = findViewById(R.id.btnSignUp);
        progressBar = findViewById(R.id.progressBar);

        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void setupClickListeners() {
        btnSignUp.setOnClickListener(v -> handleSignup());
    }

    private void handleSignup() {
        // Get input values
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        // Clear previous errors
        clearErrors();

        // Validation
        if (!validateInputs(name, email, password, confirmPassword)) {
            return;
        }

        // Check terms acceptance
        if (!cbTerms.isChecked()) {
            Toast.makeText(this, "Please accept Terms & Privacy Policy", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show progress
        showProgress(true);

        // Create user in Firebase Auth first (for cross-device login)
        String phone = "";
        FirebaseAuthUtils.signupWithFirebase(
            SignupActivity.this, name, email, phone, password, selectedRole,
            new FirebaseAuthUtils.SignupCallback() {
                @Override
                public void onSuccess(User user) {
                    // User created in Firebase Auth and Realtime Database
                    // Also save to Room for offline access
                    executor.execute(() -> {
                        try {
                            com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository localRepo =
                                com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository.getInstance(SignupActivity.this);
                            localRepo.insertUserSync(user);
                        } catch (Exception e) {
                            android.util.Log.e("SignupActivity", "Error saving to Room", e);
                        }
                    });

                    showProgress(false);
                    
                    // 🔹 CRITICAL: Check if doctor is blocked (needs admin approval)
                    if ("doctor".equalsIgnoreCase(selectedRole) && user.isBlocked()) {
                        // Doctor account created but needs admin approval
                        // Don't save session or login - redirect to login page
                        Toast.makeText(SignupActivity.this, "Account created! Please wait for admin approval. You'll be notified once approved.", Toast.LENGTH_LONG).show();
                        
                        // Navigate to login/role selection instead
                        android.content.Intent intent = new android.content.Intent(SignupActivity.this, 
                            com.example.parkinsonsdiseasedetectionsystem.activities.RoleSelectionActivity.class);
                        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        // Patient or approved user - save session and navigate
                        AuthUtils.saveUserSession(SignupActivity.this, user);
                        Toast.makeText(SignupActivity.this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                        navigateToHome(selectedRole);
                    }
                }

                @Override
                public void onFailure(String error) {
                    android.util.Log.e("SignupActivity", "Firebase signup failed: " + error);
                    showProgress(false);

                    // Check if it's an email already exists error
                    if (error != null && (error.contains("email-already-in-use") ||
                        error.contains("already exists") ||
                        error.contains("already registered"))) {
                        inputLayoutEmail.setError("Email already registered. Please login instead.");
                        etEmail.requestFocus();
                    } else {
                        Toast.makeText(SignupActivity.this, "Signup failed: " + (error != null ? error : "Unknown error"), Toast.LENGTH_LONG).show();
                    }
                }
            });
    }

    private boolean validateInputs(String name, String email, String password, String confirmPassword) {
        boolean isValid = true;

        if (TextUtils.isEmpty(name)) {
            inputLayoutName.setError("Name is required");
            etName.requestFocus();
            isValid = false;
        } else if (name.length() < 2) {
            inputLayoutName.setError("Name must be at least 2 characters");
            etName.requestFocus();
            isValid = false;
        }

        if (TextUtils.isEmpty(email)) {
            inputLayoutEmail.setError("Email is required");
            etEmail.requestFocus();
            isValid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            inputLayoutEmail.setError("Enter a valid email address");
            etEmail.requestFocus();
            isValid = false;
        }

        if (TextUtils.isEmpty(password)) {
            inputLayoutPassword.setError("Password is required");
            etPassword.requestFocus();
            isValid = false;
        } else if (password.length() < 6) {
            inputLayoutPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            isValid = false;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            inputLayoutConfirmPassword.setError("Please confirm your password");
            etConfirmPassword.requestFocus();
            isValid = false;
        } else if (!password.equals(confirmPassword)) {
            inputLayoutConfirmPassword.setError("Passwords do not match");
            etConfirmPassword.requestFocus();
            isValid = false;
        }

        return isValid;
    }

    private void clearErrors() {
        inputLayoutName.setError(null);
        inputLayoutEmail.setError(null);
        inputLayoutPassword.setError(null);
        inputLayoutConfirmPassword.setError(null);
    }

    private void showProgress(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        btnSignUp.setEnabled(!show);
    }

    private void navigateToHome(String role) {
        android.content.Intent intent;
        if ("doctor".equalsIgnoreCase(role)) {
            intent = new android.content.Intent(this, DoctorActivity.class);
        } else if ("admin".equalsIgnoreCase(role)) {
            intent = new android.content.Intent(this, AdminActivity.class);
        } else {
            intent = new android.content.Intent(this, MainActivity.class);
        }
        intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
