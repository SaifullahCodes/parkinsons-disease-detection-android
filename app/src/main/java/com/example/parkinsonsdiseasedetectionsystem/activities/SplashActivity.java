package com.example.parkinsonsdiseasedetectionsystem.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils;
import com.example.parkinsonsdiseasedetectionsystem.utils.SessionManager;

public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";
    private static final long SPLASH_DELAY = 2000; // 2 seconds
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        sessionManager = new SessionManager(this);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            navigateToNextScreen();
        }, SPLASH_DELAY);
    }
    
    private void navigateToNextScreen() {
        // Check if user is logged in (works offline)
        boolean isLoggedIn = AuthUtils.isLoggedIn(this) || sessionManager.isLoggedIn();
        String userRole = sessionManager.getUserRole();
        
        if (isLoggedIn && !userRole.isEmpty()) {
            // User is logged in - route to appropriate activity
            Log.d(TAG, "User logged in as: " + userRole);
            
            Intent intent;
            if ("admin".equalsIgnoreCase(userRole)) {
                intent = new Intent(this, AdminActivity.class);
            } else if ("doctor".equalsIgnoreCase(userRole)) {
                intent = new Intent(this, DoctorActivity.class);
            } else {
                // patient or user
                intent = new Intent(this, MainActivity.class);
            }
            
            // Check if we have saved app state
            if (sessionManager.hasSavedAppState()) {
                String lastActivity = sessionManager.getLastActivity();
                String lastFragment = sessionManager.getLastFragment();
                int bottomNav = sessionManager.getLastBottomNavSelection();
                
                // Pass saved state info
                intent.putExtra("RESTORE_STATE", true);
                intent.putExtra("LAST_FRAGMENT", lastFragment);
                intent.putExtra("BOTTOM_NAV", bottomNav);
                
                Log.d(TAG, "Restoring app state: " + lastActivity + " -> " + lastFragment);
            }
            
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            // User not logged in - check onboarding
            SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
            boolean hasOnboarded = prefs.getBoolean("hasOnboarded", false);

            Intent next = new Intent(SplashActivity.this,
                    hasOnboarded ? RoleSelectionActivity.class : OnboardingActivity.class);
            startActivity(next);
            finish();
        }
    }
}