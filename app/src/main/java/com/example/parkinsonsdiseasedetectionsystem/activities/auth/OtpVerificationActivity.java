package com.example.parkinsonsdiseasedetectionsystem.activities.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.parkinsonsdiseasedetectionsystem.R;

public class OtpVerificationActivity extends AppCompatActivity {

    private ImageView ivBack;
    private Button btnVerifyOtp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_otp_verification);

        // Initialize views
        ivBack = findViewById(R.id.btnBack);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp); // Make sure this exists in XML

        // Back button click
        ivBack.setOnClickListener(v -> finish());

        // Verify OTP click -> navigate to CreateNewPasswordActivity
        btnVerifyOtp.setOnClickListener(v -> {
            Intent intent = new Intent(OtpVerificationActivity.this, CreateNewPasswordActivity.class);
            startActivity(intent);
        });
    }
}
