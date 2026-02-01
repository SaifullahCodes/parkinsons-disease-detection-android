package com.example.parkinsonsdiseasedetectionsystem.activities;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.activities.auth.LoginActivity;
import com.example.parkinsonsdiseasedetectionsystem.fragments.admin.DashboardFragment;
import com.example.parkinsonsdiseasedetectionsystem.fragments.admin.DoctorsFragment;
import com.example.parkinsonsdiseasedetectionsystem.fragments.admin.PatientsFragment;
import com.example.parkinsonsdiseasedetectionsystem.fragments.admin.ReportsFragment;
import com.example.parkinsonsdiseasedetectionsystem.fragments.admin.SettingsFragment;
import com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils;
import com.example.parkinsonsdiseasedetectionsystem.utils.SessionManager;
import com.google.android.material.navigation.NavigationView;

public class AdminActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "AdminActivity";
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle toggle;
    private SessionManager sessionManager;
    private long backPressedTime = 0;
    private static final int BACK_PRESS_INTERVAL = 2000;

    // Header Views for Animation
    private ImageView ivProfile;
    private TextView tvName, tvEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        sessionManager = new SessionManager(this);

        // Security Check (works offline)
        if (!AuthUtils.isLoggedIn(this) && !sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_admin);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Setup Header Data
        setupHeader();

        // Setup Toolbar & Toggle
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);

            toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
                    R.string.open_drawer, R.string.close_drawer);
            drawerLayout.addDrawerListener(toggle);
            toggle.syncState();
        }

        // 🔥 ADD CUSTOM ANIMATIONS
        setupDrawerAnimations();

        navigationView.setNavigationItemSelectedListener(this);

        // Restore fragment state if available
        if (savedInstanceState == null) {
            // Check if we should restore from saved state
            Intent intent = getIntent();
            if (intent != null && intent.getBooleanExtra("RESTORE_STATE", false)) {
                String lastFragment = intent.getStringExtra("LAST_FRAGMENT");
                Fragment fragment = getFragmentByName(lastFragment);
                if (fragment != null) {
                    loadFragment(fragment, false);
                    int menuId = getMenuIdForFragment(lastFragment);
                    if (menuId != -1) {
                        navigationView.setCheckedItem(menuId);
                    }
                } else {
                    loadFragment(new DashboardFragment(), false);
                    navigationView.setCheckedItem(R.id.nav_dashboard);
                }
            } else {
                loadFragment(new DashboardFragment(), false);
                navigationView.setCheckedItem(R.id.nav_dashboard);
            }
        } else {
            // Restore from savedInstanceState
            int selectedItem = savedInstanceState.getInt("selected_nav_item", R.id.nav_dashboard);
            navigationView.setCheckedItem(selectedItem);
        }
    }
    
    private Fragment getFragmentByName(String fragmentName) {
        if (fragmentName == null || fragmentName.isEmpty()) {
            return null;
        }
        
        if (fragmentName.equals("DashboardFragment")) {
            return new DashboardFragment();
        } else if (fragmentName.equals("PatientsFragment")) {
            return new PatientsFragment();
        } else if (fragmentName.equals("DoctorsFragment")) {
            return new DoctorsFragment();
        } else if (fragmentName.equals("ReportsFragment")) {
            return new ReportsFragment();
        } else if (fragmentName.equals("SettingsFragment")) {
            return new SettingsFragment();
        }
        return null;
    }
    
    private int getMenuIdForFragment(String fragmentName) {
        if (fragmentName == null || fragmentName.isEmpty()) {
            return -1;
        }
        
        if (fragmentName.equals("DashboardFragment")) {
            return R.id.nav_dashboard;
        } else if (fragmentName.equals("PatientsFragment")) {
            return R.id.nav_manage_patients;
        } else if (fragmentName.equals("DoctorsFragment")) {
            return R.id.nav_manage_doctors;
        } else if (fragmentName.equals("ReportsFragment")) {
            return R.id.nav_reports_overview;
        } else if (fragmentName.equals("SettingsFragment")) {
            return R.id.nav_settings;
        }
        return -1;
    }

    private void setupHeader() {
        View headerView = navigationView.getHeaderView(0);
        ivProfile = headerView.findViewById(R.id.ivAdminProfile);
        tvName = headerView.findViewById(R.id.tvAdminName);
        tvEmail = headerView.findViewById(R.id.tvAdminEmail);

        // Load Real Data
        String name = AuthUtils.getUserName(this);
        String email = AuthUtils.getUserEmail(this);

        tvName.setText(name.isEmpty() ? "Administrator" : name);
        tvEmail.setText(email.isEmpty() ? "admin@parkiscan.com" : email);
    }

    /**
     * 🔥 THE ANIMATION LOGIC
     * Implements Staggered Entry, Profile Scale, and Text Fade
     */
    private void setupDrawerAnimations() {
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                animateMenuItems(); // Trigger stagger animation
            }

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                // Header Parallax & Scale Effect
                if (ivProfile != null) {
                    float scale = 0.5f + (slideOffset * 0.5f); // Scale from 0.5 to 1.0
                    ivProfile.setScaleX(scale);
                    ivProfile.setScaleY(scale);
                    ivProfile.setAlpha(slideOffset);
                }

                // Text Slide-in Effect
                float translationX = 100f * (1 - slideOffset);
                if (tvName != null) tvName.setTranslationX(translationX);
                if (tvEmail != null) tvEmail.setTranslationX(translationX * 1.5f);
            }
        });
    }

    private void animateMenuItems() {
        // Staggered Animation for Menu Items
        for (int i = 0; i < navigationView.getMenu().size(); i++) {
            // Sadly, we can't easily get View references for individual menu items
            // without reflection or custom adapters in standard NavigationView.
            // So we focus on the Header animation mostly, which is reliable.
        }

        // Bounce Animation for Profile
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ivProfile, "scaleX", 1.0f, 1.2f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ivProfile, "scaleY", 1.0f, 1.2f, 1.0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(400);
        set.setInterpolator(new OvershootInterpolator());
        set.start();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment fragment = null;
        String fragmentName = "";
        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            fragment = new DashboardFragment();
            fragmentName = "DashboardFragment";
        } else if (id == R.id.nav_manage_patients) {
            fragment = new PatientsFragment();
            fragmentName = "PatientsFragment";
        } else if (id == R.id.nav_manage_doctors) {
            fragment = new DoctorsFragment();
            fragmentName = "DoctorsFragment";
        } else if (id == R.id.nav_reports_overview) {
            fragment = new ReportsFragment();
            fragmentName = "ReportsFragment";
        } else if (id == R.id.nav_settings) {
            fragment = new SettingsFragment();
            fragmentName = "SettingsFragment";
        }

        if (fragment != null) {
            // Save app state
            sessionManager.saveAppState("AdminActivity", fragmentName, id);
            
            // Add a small delay so the user sees the ripple click effect
            final Fragment finalFragment = fragment;
            new android.os.Handler().postDelayed(() -> loadFragment(finalFragment, true), 200);
            item.setChecked(true);
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    public void loadFragment(Fragment fragment, boolean addToBackStack) {
        if (addToBackStack) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        } else {
            getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.fragment_container, fragment)
                    .commit();
        }
    }

    public void openDrawer() {
        if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (navigationView != null) {
            MenuItem selectedItem = navigationView.getCheckedItem();
            if (selectedItem != null) {
                outState.putInt("selected_nav_item", selectedItem.getItemId());
            }
        }
        
        // Save app state
        String currentFragment = getCurrentFragmentName();
        int selectedId = navigationView != null && navigationView.getCheckedItem() != null 
                ? navigationView.getCheckedItem().getItemId() : R.id.nav_dashboard;
        sessionManager.saveAppState("AdminActivity", currentFragment, selectedId);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Save app state when activity is paused
        String currentFragment = getCurrentFragmentName();
        int selectedId = navigationView != null && navigationView.getCheckedItem() != null 
                ? navigationView.getCheckedItem().getItemId() : R.id.nav_dashboard;
        sessionManager.saveAppState("AdminActivity", currentFragment, selectedId);
    }
    
    private String getCurrentFragmentName() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment != null) {
            return fragment.getClass().getSimpleName();
        }
        return "DashboardFragment";
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            // Handle fragment back stack
            getSupportFragmentManager().popBackStack();
            
            // Update navigation selection based on current fragment
            Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (currentFragment != null) {
                String fragmentName = currentFragment.getClass().getSimpleName();
                int menuId = getMenuIdForFragment(fragmentName);
                if (menuId != -1 && navigationView != null) {
                    navigationView.setCheckedItem(menuId);
                }
            }
        } else {
            // Double tap to exit
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