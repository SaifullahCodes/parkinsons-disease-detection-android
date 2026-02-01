package com.example.parkinsonsdiseasedetectionsystem.activities.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.parkinsonsdiseasedetectionsystem.R;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText etEmail;
    private Button btnResetPassword;
    private ImageView ivBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password); // use your layout file name

        // Initialize views
        etEmail = findViewById(R.id.etEmail);
        btnResetPassword = findViewById(R.id.btnResetPassword);
        ivBack = findViewById(R.id.btnBack);

        // Back button → go back to LoginActivity
        ivBack.setOnClickListener(v -> finish());


        // Reset button → navigate to OTP Activity (we will create later)
        btnResetPassword.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();

            // TODO: Add validation and OTP send logic later
            Intent intent = new Intent(ForgotPasswordActivity.this, OtpVerificationActivity.class);
            intent.putExtra("email", email); // pass email to OTP screen
            startActivity(intent);
        });
    }
}
