package com.example.parkinsonsdiseasedetectionsystem.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.activities.auth.LoginActivity;
import com.example.parkinsonsdiseasedetectionsystem.fragments.user.UserHomeFragment;
import com.example.parkinsonsdiseasedetectionsystem.fragments.user.UserHistoryFragment;
import com.example.parkinsonsdiseasedetectionsystem.fragments.user.UserProfileFragment;
import com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils;
import com.example.parkinsonsdiseasedetectionsystem.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private BottomNavigationView bottomNav;
    private String userRole;
    private SessionManager sessionManager;
    private long backPressedTime = 0;
    private static final int BACK_PRESS_INTERVAL = 2000; // 2 seconds

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

        setContentView(R.layout.activity_main);
        
        // Handle window insets - only apply top padding for status bar, bottom navigation handles its own insets
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            // Only apply top padding for status bar, not bottom
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        userRole = AuthUtils.getUserRole(this);
        if (userRole == null || userRole.isEmpty()) {
            userRole = sessionManager.getUserRole();
        }

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
                    loadFragment(new UserHomeFragment(), false);
                }
            } else {
                loadFragment(new UserHomeFragment(), false);
            }
        } else {
            // Restore from savedInstanceState
            int selectedItem = savedInstanceState.getInt("selected_nav_item", R.id.nav_home);
            bottomNav.setSelectedItemId(selectedItem);
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment;
            String fragmentName = "";
            if (item.getItemId() == R.id.nav_home) {
                selectedFragment = new UserHomeFragment();
                fragmentName = "UserHomeFragment";
            } else if (item.getItemId() == R.id.nav_history) {
                selectedFragment = new UserHistoryFragment();
                fragmentName = "UserHistoryFragment";
            } else if (item.getItemId() == R.id.nav_profile) {
                selectedFragment = new UserProfileFragment();
                fragmentName = "UserProfileFragment";
            } else {
                return false;
            }
            
            // Save app state
            sessionManager.saveAppState("MainActivity", fragmentName, item.getItemId());
            // Bottom navigation tab switches should NOT add to back stack
            // Only add to back stack for actual navigation flows (like detail fragments)
            loadFragment(selectedFragment, false);
            return true;
        });
    }
    
    private Fragment getFragmentByName(String fragmentName) {
        if (fragmentName == null || fragmentName.isEmpty()) {
            return null;
        }
        
        if (fragmentName.equals("UserHomeFragment")) {
            return new UserHomeFragment();
        } else if (fragmentName.equals("UserHistoryFragment")) {
            return new UserHistoryFragment();
        } else if (fragmentName.equals("UserProfileFragment")) {
            return new UserProfileFragment();
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
        sessionManager.saveAppState("MainActivity", currentFragment, 
                bottomNav != null ? bottomNav.getSelectedItemId() : R.id.nav_home);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Save app state when activity is paused
        String currentFragment = getCurrentFragmentName();
        sessionManager.saveAppState("MainActivity", currentFragment, 
                bottomNav != null ? bottomNav.getSelectedItemId() : R.id.nav_home);
    }
    
    private String getCurrentFragmentName() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment != null) {
            return fragment.getClass().getSimpleName();
        }
        return "UserHomeFragment";
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
                int navId = R.id.nav_home;
                if (fragmentName.equals("UserHistoryFragment")) {
                    navId = R.id.nav_history;
                } else if (fragmentName.equals("UserProfileFragment")) {
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
            
            // Navigation order: Profile -> History -> Home -> Exit
            if (fragmentName.equals("UserProfileFragment")) {
                // From Profile, go to History
                Fragment historyFragment = new UserHistoryFragment();
                loadFragment(historyFragment, false);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.nav_history);
                }
                sessionManager.saveAppState("MainActivity", "UserHistoryFragment", R.id.nav_history);
            } else if (fragmentName.equals("UserHistoryFragment")) {
                // From History, go to Home
                Fragment homeFragment = new UserHomeFragment();
                loadFragment(homeFragment, false);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.nav_home);
                }
                sessionManager.saveAppState("MainActivity", "UserHomeFragment", R.id.nav_home);
            } else if (fragmentName.equals("UserHomeFragment")) {
                // On Home - show double-tap to exit
                if (backPressedTime + BACK_PRESS_INTERVAL > System.currentTimeMillis()) {
                    super.onBackPressed();
                    finish();
                } else {
                    Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
                    backPressedTime = System.currentTimeMillis();
                }
            } else {
                // Unknown fragment - go to Home
                Fragment homeFragment = new UserHomeFragment();
                loadFragment(homeFragment, false);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.nav_home);
                }
                sessionManager.saveAppState("MainActivity", "UserHomeFragment", R.id.nav_home);
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
