package com.example.parkinsonsdiseasedetectionsystem.activities.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.activities.AdminActivity;
import com.example.parkinsonsdiseasedetectionsystem.activities.DoctorActivity;
import com.example.parkinsonsdiseasedetectionsystem.activities.MainActivity;
import com.example.parkinsonsdiseasedetectionsystem.activities.RoleSelectionActivity;
import com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils;
import com.example.parkinsonsdiseasedetectionsystem.utils.FirebaseAuthUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private TextView tvTitle, tvSubtitle, tvForgotPassword;
    private TextInputEditText etEmail, etPassword;
    private TextInputLayout layoutEmail, layoutPassword;
    private MaterialButton btnLogin, btnSignUp;
    private ImageView ivBack;
    private ProgressBar progressBar;

    private String selectedRole = "patient"; // default role if not passed
    private final Executor executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Get role from previous activity
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("role")) {
            String role = intent.getStringExtra("role");
            if (role != null) {
                selectedRole = role.equalsIgnoreCase("Doctor") ? "doctor" :
                              role.equalsIgnoreCase("Admin") ? "admin" : "patient";
            }
        }

        // Initialize views
        initializeViews();

        // Update UI based on role
        setRoleUI(selectedRole);

        // Setup click listeners
        setupClickListeners();
    }

    private void initializeViews() {
        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        layoutEmail = findViewById(R.id.layoutEmail);
        layoutPassword = findViewById(R.id.layoutPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnSignUp = findViewById(R.id.btnSignUp);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        ivBack = findViewById(R.id.btnBack);
        progressBar = findViewById(R.id.progressBar);

        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> handleLogin());

        btnSignUp.setOnClickListener(v -> {
            Intent signupIntent = new Intent(LoginActivity.this, SignupActivity.class);
            signupIntent.putExtra("role", selectedRole);
            startActivity(signupIntent);
        });

        tvForgotPassword.setOnClickListener(v -> {
            Intent forgotIntent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(forgotIntent);
        });

        ivBack.setOnClickListener(v -> {
            Intent forgotIntent = new Intent(LoginActivity.this, RoleSelectionActivity.class);
            startActivity(forgotIntent);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Intent intent = new Intent(LoginActivity.this, RoleSelectionActivity.class);
        startActivity(intent);
        finish();
    }

    private void handleLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Clear previous errors
        clearErrors();

        // Validation
        if (!validateInputs(email, password)) {
            return;
        }

        // Show progress
        showProgress(true);

        // Use Firebase Authentication with Realtime Database
        FirebaseAuthUtils.loginWithFirebase(
            LoginActivity.this, email, password, selectedRole,
            new FirebaseAuthUtils.LoginCallback() {
                @Override
                public void onSuccess(com.example.parkinsonsdiseasedetectionsystem.models.User user) {
                    showProgress(false);
                    // CRITICAL FIX: Get role directly from user object (from Realtime Database), not SharedPreferences
                    String actualRole = user != null ? user.getRole() : null;
                    if (actualRole == null || actualRole.isEmpty()) {
                        // Fallback to session if user object doesn't have role (shouldn't happen)
                        actualRole = FirebaseAuthUtils.getUserRole(LoginActivity.this);
                    }
                    Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                    navigateToHome(actualRole);
                }

                @Override
                public void onFailure(String error) {
                    showProgress(false);
                    layoutPassword.setError("Invalid email or password");
                    etPassword.requestFocus();
                    String errorMsg = error != null ? error : "Login failed. Please check your credentials.";

                    // Provide specific error messages
                    if (errorMsg.contains("wrong-password") || errorMsg.contains("Wrong password")) {
                        layoutPassword.setError("Wrong password. Please try again.");
                    } else if (errorMsg.contains("user-not-found") || errorMsg.contains("User not found")) {
                        layoutEmail.setError("User not found. Please sign up first.");
                    } else if (errorMsg.contains("Invalid role")) {
                        Toast.makeText(LoginActivity.this, "Invalid role. Please login from the correct portal.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                }
            });
    }

    private boolean validateInputs(String email, String password) {
        boolean isValid = true;

        if (TextUtils.isEmpty(email)) {
            layoutEmail.setError("Email is required");
            etEmail.requestFocus();
            isValid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            layoutEmail.setError("Enter a valid email address");
            etEmail.requestFocus();
            isValid = false;
        }

        if (TextUtils.isEmpty(password)) {
            layoutPassword.setError("Password is required");
            etPassword.requestFocus();
            isValid = false;
        } else if (password.length() < 6) {
            layoutPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            isValid = false;
        }

        return isValid;
    }

    private void clearErrors() {
        layoutEmail.setError(null);
        layoutPassword.setError(null);
    }

    private void showProgress(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        btnLogin.setEnabled(!show);
    }

    private void navigateToHome(String role) {
        Intent intent;
        if ("doctor".equalsIgnoreCase(role)) {
            intent = new Intent(this, DoctorActivity.class);
        } else if ("admin".equalsIgnoreCase(role)) {
            intent = new Intent(this, AdminActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setRoleUI(String role) {
        if (role == null) role = "patient";

        switch (role.toLowerCase()) {
            case "doctor":
                tvTitle.setText("Doctor Login");
                tvSubtitle.setText("Login to manage your patients and appointments");
                btnSignUp.setVisibility(View.VISIBLE); // Allow doctors to sign up
                break;

            case "admin":
                tvTitle.setText("Admin Login");
                tvSubtitle.setText("Login to manage the platform and users");
                btnSignUp.setVisibility(View.GONE); // Hide signup for admin
                break;

            case "patient":
            default:
                tvTitle.setText("User Login");
                tvSubtitle.setText("Login to access health services and stay connected");
                btnSignUp.setVisibility(View.VISIBLE);
                break;
        }
    }
}
