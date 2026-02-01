package com.example.parkinsonsdiseasedetectionsystem.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.activities.auth.LoginActivity;
import com.google.android.material.tabs.TabLayout;

public class RoleSelectionActivity extends AppCompatActivity {

    private LinearLayout layoutUser, layoutDoctor, layoutAdmin;
    private ImageView imgUser, imgDoctor, imgAdmin;
    private TextView txtUser, txtDoctor, txtAdmin, tvRoleDescription;
    private Button btnContinue;
    private TabLayout tabLayoutRoles;
    private String selectedRole = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        // Initialize Views
        layoutUser = findViewById(R.id.layout_user);
        layoutDoctor = findViewById(R.id.layout_doctor);
        layoutAdmin = findViewById(R.id.layout_admin);

        imgUser = findViewById(R.id.img_user);
        imgDoctor = findViewById(R.id.img_doctor);
        imgAdmin = findViewById(R.id.img_admin);

        txtUser = findViewById(R.id.txt_user);
        txtDoctor = findViewById(R.id.txt_doctor);
        txtAdmin = findViewById(R.id.txt_admin);

        tvRoleDescription = findViewById(R.id.tvRoleDescription);
        btnContinue = findViewById(R.id.btnContinue);
        tabLayoutRoles = findViewById(R.id.tab_layout_roles);

        setupRoleDots();

        // Click listeners
        layoutUser.setOnClickListener(v -> selectRole("user"));
        layoutDoctor.setOnClickListener(v -> selectRole("doctor"));
        layoutAdmin.setOnClickListener(v -> selectRole("admin"));

        btnContinue.setOnClickListener(v -> {
            if (!selectedRole.isEmpty()) {
                Intent i = new Intent(RoleSelectionActivity.this, LoginActivity.class);
                i.putExtra("role", selectedRole);
                startActivity(i);
            }
        });

        // Default selection
        selectRole("user");
    }

    private void selectRole(String role) {
        selectedRole = role;

        // Reset all to inactive
        layoutUser.setBackgroundResource(R.drawable.role_background_inactive);
        layoutDoctor.setBackgroundResource(R.drawable.role_background_inactive);
        layoutAdmin.setBackgroundResource(R.drawable.role_background_inactive);

        imgUser.setColorFilter(ContextCompat.getColor(this, android.R.color.darker_gray));
        imgDoctor.setColorFilter(ContextCompat.getColor(this, android.R.color.darker_gray));
        imgAdmin.setColorFilter(ContextCompat.getColor(this, android.R.color.darker_gray));

        txtUser.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        txtDoctor.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        txtAdmin.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));

        // Activate selected one
        switch (role) {
            case "user":
                layoutUser.setBackgroundResource(R.drawable.role_background_active);
                imgUser.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary));
                txtUser.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
                tvRoleDescription.setText("As a User, you can record your voice and video and track your Parkinson's progress.");
                break;

            case "doctor":
                layoutDoctor.setBackgroundResource(R.drawable.role_background_active);
                imgDoctor.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary));
                txtDoctor.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
                tvRoleDescription.setText("As a Doctor, you can view patient progress and provide guidance.");
                break;

            case "admin":
                layoutAdmin.setBackgroundResource(R.drawable.role_background_active);
                imgAdmin.setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary));
                txtAdmin.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary));
                tvRoleDescription.setText("As an Admin, you manage users, doctors, and app settings.");
                break;
        }

        updateRoleDots(role);
    }

    private void setupRoleDots() {
        tabLayoutRoles.removeAllTabs();
        for (int i = 0; i < 3; i++) {
            TabLayout.Tab tab = tabLayoutRoles.newTab();
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(16, 16);
            params.setMargins(4, 0, 4, 0);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(i == 0 ? R.drawable.dot_selected : R.drawable.dot_unselected);
            tab.setCustomView(dot);
            tabLayoutRoles.addTab(tab);
        }
    }

    private void updateRoleDots(String role) {
        int position = 0;
        switch (role) {
            case "user": position = 0; break;
            case "doctor": position = 1; break;
            case "admin": position = 2; break;
        }

        for (int i = 0; i < tabLayoutRoles.getTabCount(); i++) {
            View dot = tabLayoutRoles.getTabAt(i).getCustomView();
            if (dot != null) {
                dot.setBackgroundResource(i == position ? R.drawable.dot_selected : R.drawable.dot_unselected);
                if (i == position) {
                    ScaleAnimation anim = new ScaleAnimation(
                            0.8f, 1f, 0.8f, 1f,
                            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
                    );
                    anim.setDuration(200);
                    anim.setFillAfter(true);
                    dot.startAnimation(anim);
                }
            }
        }
    }
}