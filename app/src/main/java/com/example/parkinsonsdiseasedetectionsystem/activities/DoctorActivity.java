package com.example.parkinsonsdiseasedetectionsystem.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.activities.auth.LoginActivity;
import com.example.parkinsonsdiseasedetectionsystem.fragments.doctor.DoctorDashboardFragment;
import com.example.parkinsonsdiseasedetectionsystem.fragments.doctor.DoctorPatientsFragment;
import com.example.parkinsonsdiseasedetectionsystem.fragments.doctor.DoctorProfileFragment;
import com.example.parkinsonsdiseasedetectionsystem.fragments.doctor.DoctorReportsFragment;
import com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils;
import com.example.parkinsonsdiseasedetectionsystem.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class DoctorActivity extends AppCompatActivity {
    private static final String TAG = "DoctorActivity";
    private BottomNavigationView bottomNav;
    private SessionManager sessionManager;
    private long backPressedTime = 0;
    private static final int BACK_PRESS_INTERVAL = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        sessionManager = new SessionManager(this);
        
        // Check if user is logged in (works offline)
        if (!AuthUtils.isLoggedIn(this) && !sessionManager.isLoggedIn()) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        
        setContentView(R.layout.activity_doctor);
        
        // Handle window insets - only apply top padding for status bar, bottom navigation handles its own insets
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            // Only apply top padding for status bar, not bottom
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        bottomNav = findViewById(R.id.bottom_navigation);
        
        // Apply window insets to bottom navigation so it sits directly on system navigation bar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            // Apply bottom inset to bottom navigation so it extends to system navigation bar
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });
        bottomNav.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_SELECTED);

        // Restore fragment state if available
        if (savedInstanceState == null) {
            // Check if we should restore from saved state
            Intent intent = getIntent();
            if (intent != null && intent.getBooleanExtra("RESTORE_STATE", false)) {
                String lastFragment = intent.getStringExtra("LAST_FRAGMENT");
                int bottomNavSelected = intent.getIntExtra("BOTTOM_NAV", -1);
                
                if (bottomNavSelected != -1) {
                    bottomNav.setSelectedItemId(bottomNavSelected);
                }
                
                Fragment fragment = getFragmentByName(lastFragment);
                if (fragment != null) {
                    loadFragment(fragment, false);
                } else {
                    loadFragment(new DoctorDashboardFragment(), false);
                }
            } else {
                loadFragment(new DoctorDashboardFragment(), false);
            }
        } else {
            // Restore from savedInstanceState
            int selectedItem = savedInstanceState.getInt("selected_nav_item", R.id.nav_dashboard);
            bottomNav.setSelectedItemId(selectedItem);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            String fragmentName = "";

            // Using if-else instead of switch
            if (item.getItemId() == R.id.nav_dashboard) {
                selectedFragment = new DoctorDashboardFragment();
                fragmentName = "DoctorDashboardFragment";
            } else if (item.getItemId() == R.id.nav_patients) {
                selectedFragment = new DoctorPatientsFragment();
                fragmentName = "DoctorPatientsFragment";
            } else if (item.getItemId() == R.id.nav_reports) {
                selectedFragment = new DoctorReportsFragment();
                fragmentName = "DoctorReportsFragment";
            } else if (item.getItemId() == R.id.nav_profile) {
                selectedFragment = new DoctorProfileFragment();
                fragmentName = "DoctorProfileFragment";
            }

            if (selectedFragment != null) {
                // Save app state
                sessionManager.saveAppState("DoctorActivity", fragmentName, item.getItemId());
                // Bottom navigation tab switches should NOT add to back stack
                // Only add to back stack for actual navigation flows (like detail fragments)
                loadFragment(selectedFragment, false);
            }

            return true;
        });
    }
    
    private Fragment getFragmentByName(String fragmentName) {
        if (fragmentName == null || fragmentName.isEmpty()) {
            return null;
        }
        
        if (fragmentName.equals("DoctorDashboardFragment")) {
            return new DoctorDashboardFragment();
        } else if (fragmentName.equals("DoctorPatientsFragment")) {
            return new DoctorPatientsFragment();
        } else if (fragmentName.equals("DoctorReportsFragment")) {
            return new DoctorReportsFragment();
        } else if (fragmentName.equals("DoctorProfileFragment")) {
            return new DoctorProfileFragment();
        }
        return null;
    }
    
    private void loadFragment(Fragment fragment, boolean addToBackStack) {
        if (addToBackStack) {
            // For child/detail fragments - add to back stack
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        } else {
            // For bottom nav tab switches - replace without back stack
            // Clear back stack when switching tabs to avoid confusion
            if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                getSupportFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (bottomNav != null) {
            outState.putInt("selected_nav_item", bottomNav.getSelectedItemId());
        }
        
        // Save app state
        String currentFragment = getCurrentFragmentName();
        sessionManager.saveAppState("DoctorActivity", currentFragment, 
                bottomNav != null ? bottomNav.getSelectedItemId() : R.id.nav_dashboard);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Save app state when activity is paused
        String currentFragment = getCurrentFragmentName();
        sessionManager.saveAppState("DoctorActivity", currentFragment, 
                bottomNav != null ? bottomNav.getSelectedItemId() : R.id.nav_dashboard);
    }
    
    private String getCurrentFragmentName() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment != null) {
            return fragment.getClass().getSimpleName();
        }
        return "DoctorDashboardFragment";
    }
    
    @Override
    public void onBackPressed() {
        // First, check if there are fragments in back stack (for child/detail fragments)
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            // Pop back stack and return to previous fragment
            getSupportFragmentManager().popBackStack();
            
            // Update bottom nav selection after popping
            getSupportFragmentManager().executePendingTransactions();
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment != null && bottomNav != null) {
                String fragmentName = currentFragment.getClass().getSimpleName();
                int navId = R.id.nav_dashboard;
                if (fragmentName.equals("DoctorPatientsFragment")) {
                    navId = R.id.nav_patients;
                } else if (fragmentName.equals("DoctorReportsFragment")) {
                    navId = R.id.nav_reports;
                } else if (fragmentName.equals("DoctorProfileFragment")) {
                    navId = R.id.nav_profile;
                }
                bottomNav.setSelectedItemId(navId);
            }
            return;
        }
        
        // Check current fragment and implement sequential navigation
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment != null) {
            String fragmentName = currentFragment.getClass().getSimpleName();
            
            // Navigation order: Profile -> Reports -> Patients -> Dashboard -> Exit
            // Or simplified: any non-dashboard -> Dashboard -> Exit
            if (fragmentName.equals("DoctorProfileFragment")) {
                // From Profile, go to Reports (or Dashboard - you can customize order)
                Fragment reportsFragment = new DoctorReportsFragment();
                loadFragment(reportsFragment, false);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.nav_reports);
                }
                sessionManager.saveAppState("DoctorActivity", "DoctorReportsFragment", R.id.nav_reports);
            } else if (fragmentName.equals("DoctorReportsFragment")) {
                // From Reports, go to Patients
                Fragment patientsFragment = new DoctorPatientsFragment();
                loadFragment(patientsFragment, false);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.nav_patients);
                }
                sessionManager.saveAppState("DoctorActivity", "DoctorPatientsFragment", R.id.nav_patients);
            } else if (fragmentName.equals("DoctorPatientsFragment")) {
                // From Patients, go to Dashboard
                Fragment dashboardFragment = new DoctorDashboardFragment();
                loadFragment(dashboardFragment, false);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.nav_dashboard);
                }
                sessionManager.saveAppState("DoctorActivity", "DoctorDashboardFragment", R.id.nav_dashboard);
            } else if (fragmentName.equals("DoctorDashboardFragment")) {
                // On Dashboard - show double-tap to exit
                if (backPressedTime + BACK_PRESS_INTERVAL > System.currentTimeMillis()) {
                    super.onBackPressed();
                    finish();
                } else {
                    Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
                    backPressedTime = System.currentTimeMillis();
                }
            } else {
                // Unknown fragment - go to Dashboard
                Fragment dashboardFragment = new DoctorDashboardFragment();
                loadFragment(dashboardFragment, false);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.nav_dashboard);
                }
                sessionManager.saveAppState("DoctorActivity", "DoctorDashboardFragment", R.id.nav_dashboard);
            }
        } else {
            // Fallback: double-tap to exit
            if (backPressedTime + BACK_PRESS_INTERVAL > System.currentTimeMillis()) {
                super.onBackPressed();
                finish();
            } else {
                Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
                backPressedTime = System.currentTimeMillis();
            }
        }
    }
}
