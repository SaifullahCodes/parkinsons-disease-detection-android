package com.example.parkinsonsdiseasedetectionsystem.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * SessionManager - Professional session management for persistent login state
 * Works offline - login state persists even without internet
 * Supports User, Doctor, and Admin roles
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREFS_NAME = "ParkiScan_Session";
    
    // Session keys
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_LOGIN_TIMESTAMP = "login_timestamp";
    private static final String KEY_LAST_ACTIVITY = "last_activity";
    private static final String KEY_LAST_FRAGMENT = "last_fragment";
    private static final String KEY_SESSION_TOKEN = "session_token";
    
    // App state keys
    private static final String KEY_APP_STATE_SAVED = "app_state_saved";
    private static final String KEY_CURRENT_ACTIVITY = "current_activity";
    private static final String KEY_CURRENT_FRAGMENT = "current_fragment";
    private static final String KEY_BOTTOM_NAV_SELECTED = "bottom_nav_selected";
    
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private Context context;
    
    public SessionManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }
    
    // ========================================
    // SESSION MANAGEMENT
    // ========================================
    
    /**
     * Create user session - persists login state offline
     */
    public void createSession(String userId, String userName, String userEmail, String userRole) {
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_NAME, userName);
        editor.putString(KEY_USER_EMAIL, userEmail);
        editor.putString(KEY_USER_ROLE, userRole);
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis());
        editor.putString(KEY_SESSION_TOKEN, java.util.UUID.randomUUID().toString());
        editor.putBoolean(KEY_APP_STATE_SAVED, false);
        editor.apply();
        
        Log.d(TAG, "Session created for user: " + userName + " (" + userRole + ")");
    }
    
    /**
     * Check if user is logged in (works offline)
     */
    public boolean isLoggedIn() {
        boolean loggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false);
        if (loggedIn) {
            // Verify session is still valid (optional: add timeout check)
            long loginTime = prefs.getLong(KEY_LOGIN_TIMESTAMP, 0);
            long currentTime = System.currentTimeMillis();
            // Session valid for 30 days (optional timeout)
            long sessionTimeout = 30L * 24L * 60L * 60L * 1000L; // 30 days
            if (currentTime - loginTime > sessionTimeout) {
                Log.w(TAG, "Session expired");
                clearSession();
                return false;
            }
        }
        return loggedIn;
    }
    
    /**
     * Get current user ID
     */
    public String getUserId() {
        return prefs.getString(KEY_USER_ID, "");
    }
    
    /**
     * Get current user name
     */
    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "User");
    }
    
    /**
     * Get current user email
     */
    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, "");
    }
    
    /**
     * Get current user role
     */
    public String getUserRole() {
        return prefs.getString(KEY_USER_ROLE, "");
    }
    
    /**
     * Clear session (logout)
     */
    public void clearSession() {
        editor.clear();
        editor.apply();
        Log.d(TAG, "Session cleared");
    }
    
    /**
     * Update user info in session
     */
    public void updateUserInfo(String userName, String userEmail) {
        editor.putString(KEY_USER_NAME, userName);
        editor.putString(KEY_USER_EMAIL, userEmail);
        editor.apply();
    }
    
    // ========================================
    // APP STATE MANAGEMENT
    // ========================================
    
    /**
     * Save current app state (activity and fragment)
     */
    public void saveAppState(String activityName, String fragmentName, int bottomNavSelected) {
        editor.putBoolean(KEY_APP_STATE_SAVED, true);
        editor.putString(KEY_CURRENT_ACTIVITY, activityName);
        editor.putString(KEY_CURRENT_FRAGMENT, fragmentName);
        editor.putInt(KEY_BOTTOM_NAV_SELECTED, bottomNavSelected);
        editor.putLong("last_state_save_time", System.currentTimeMillis());
        editor.apply();
        
        Log.d(TAG, "App state saved: " + activityName + " -> " + fragmentName);
    }
    
    /**
     * Get last activity name
     */
    public String getLastActivity() {
        return prefs.getString(KEY_CURRENT_ACTIVITY, "");
    }
    
    /**
     * Get last fragment name
     */
    public String getLastFragment() {
        return prefs.getString(KEY_CURRENT_FRAGMENT, "");
    }
    
    /**
     * Get last bottom navigation selection
     */
    public int getLastBottomNavSelection() {
        return prefs.getInt(KEY_BOTTOM_NAV_SELECTED, -1);
    }
    
    /**
     * Check if app state was saved
     */
    public boolean hasSavedAppState() {
        return prefs.getBoolean(KEY_APP_STATE_SAVED, false);
    }
    
    /**
     * Clear saved app state
     */
    public void clearAppState() {
        editor.putBoolean(KEY_APP_STATE_SAVED, false);
        editor.remove(KEY_CURRENT_ACTIVITY);
        editor.remove(KEY_CURRENT_FRAGMENT);
        editor.remove(KEY_BOTTOM_NAV_SELECTED);
        editor.apply();
    }
    
    /**
     * Save last activity for navigation
     */
    public void saveLastActivity(String activityName) {
        editor.putString(KEY_LAST_ACTIVITY, activityName);
        editor.apply();
    }
    
    /**
     * Get last activity for navigation
     */
    public String getLastActivityForNav() {
        return prefs.getString(KEY_LAST_ACTIVITY, "");
    }
    
    /**
     * Save last fragment
     */
    public void saveLastFragment(String fragmentName) {
        editor.putString(KEY_LAST_FRAGMENT, fragmentName);
        editor.apply();
    }
    
    /**
     * Get last fragment
     */
    public String getLastFragmentForNav() {
        return prefs.getString(KEY_LAST_FRAGMENT, "");
    }
}


