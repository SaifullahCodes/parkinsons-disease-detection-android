package com.example.parkinsonsdiseasedetectionsystem.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.User;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * AuthUtils - Updated to use Firebase Authentication by default
 * Maintains backward compatibility with Room database
 */
public class AuthUtils {
    private static final String PREFS_NAME = "ParkiScan_Prefs";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    private static LocalRepository localRepository;
    private static final Executor executor = Executors.newSingleThreadExecutor();

    public static void init(Context context) {
        if (localRepository == null) {
            localRepository = LocalRepository.getInstance(context.getApplicationContext());
        }
    }

    // Use Firebase Authentication by default
    private static final boolean USE_FIREBASE = true;

    public static boolean login(Context context, String email, String password) {
        return login(context, email, password, null);
    }

    public static boolean login(Context context, String email, String password, String expectedRole) {
        if (USE_FIREBASE) {
            // Use Firebase Authentication
            final boolean[] loginSuccess = {false};
            FirebaseAuthUtils.loginWithFirebase(context, email, password, expectedRole,
                new FirebaseAuthUtils.LoginCallback() {
                    @Override
                    public void onSuccess(User user) {
                        loginSuccess[0] = true;
                    }

                    @Override
                    public void onFailure(String error) {
                        loginSuccess[0] = false;
                    }
                });
            // Wait a bit for async operation (not ideal, but maintains sync interface)
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return loginSuccess[0];
        } else {
            // Fallback to Room database
            try {
                init(context);
                if (localRepository == null) {
                    return false;
                }
                User user = localRepository.getUserByEmailSync(email);
                if (user != null && user.getPassword() != null && user.getPassword().equals(password)) {
                    if (user.isBlocked()) {
                        return false;
                    }
                    // Domain-specific validation: check if user role matches expected role
                    if (expectedRole != null && !expectedRole.isEmpty()) {
                        String userRole = user.getRole();
                        if (!expectedRole.equalsIgnoreCase(userRole)) {
                            // Role mismatch - user trying to login from wrong domain
                            return false;
                        }
                    }
                    saveUserSession(context, user);
                    return true;
                }
                return false;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    public static boolean signup(Context context, String name, String email, String phone,
                                 String password, String role) {
        if (USE_FIREBASE) {
            // Use Firebase Authentication
            final boolean[] signupSuccess = {false};
            FirebaseAuthUtils.signupWithFirebase(context, name, email, phone, password, role,
                new FirebaseAuthUtils.SignupCallback() {
                    @Override
                    public void onSuccess(User user) {
                        signupSuccess[0] = true;
                    }

                    @Override
                    public void onFailure(String error) {
                        signupSuccess[0] = false;
                    }
                });
            // Wait a bit for async operation
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return signupSuccess[0];
        } else {
            // Fallback to Room database
            init(context);
            User existingUser = localRepository.getUserByEmailSync(email);
            if (existingUser != null) {
                return false; // User already exists
            }

            String userId = UUID.randomUUID().toString();
            User newUser = new User(userId, name, email, phone, role);
            newUser.setPassword(password);
            localRepository.insertUser(newUser);
            saveUserSession(context, newUser);
            return true;
        }
    }

    public static void saveUserSession(Context context, User user) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_USER_ID, user.getId());
        editor.putString(KEY_USER_NAME, user.getName());
        editor.putString(KEY_USER_EMAIL, user.getEmail());
        editor.putString(KEY_USER_ROLE, user.getRole());
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.apply();
        
        // Also save to SessionManager for persistent offline login
        SessionManager sessionManager = new SessionManager(context);
        sessionManager.createSession(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }

    public static void logout(Context context) {
        if (USE_FIREBASE) {
            FirebaseAuthUtils.logout(context);
        } else {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();
        }
        
        // Clear SessionManager
        SessionManager sessionManager = new SessionManager(context);
        sessionManager.clearSession();
        sessionManager.clearAppState();
    }

    public static boolean isLoggedIn(Context context) {
        if (USE_FIREBASE) {
            return FirebaseAuthUtils.isLoggedIn(context);
        } else {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            boolean fromPrefs = prefs.getBoolean(KEY_IS_LOGGED_IN, false);
            
            // Also check SessionManager for persistent offline login
            SessionManager sessionManager = new SessionManager(context);
            boolean fromSession = sessionManager.isLoggedIn();
            
            return fromPrefs || fromSession;
        }
    }

    public static String getUserRole(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_ROLE, "");
    }

    public static String getUserName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_NAME, "User");
    }

    public static String getUserEmail(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_EMAIL, "");
    }

    public static String getUserId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_ID, "");
    }

    // ========================================
    // PROFILE HELPERS
    // ========================================

    public static User getCurrentUser(Context context) {
        init(context);
        String userId = getUserId(context);
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        return localRepository.getUserByIdSync(userId);
    }

    public static void updateCurrentUserProfile(Context context, String name, String email, String phone) {
        init(context);
        String userId = getUserId(context);
        if (userId == null || userId.isEmpty()) {
            return;
        }
        User user = localRepository.getUserByIdSync(userId);
        if (user != null) {
            user.setName(name);
            user.setEmail(email);
            user.setPhone(phone);
            localRepository.updateUserProfile(user);
            saveUserSession(context, user);
        }
    }

    public static boolean updateCurrentUserPassword(Context context, String newPassword) {
        init(context);
        String userId = getUserId(context);
        if (userId == null || userId.isEmpty() || newPassword == null || newPassword.trim().isEmpty()) {
            return false;
        }
        User user = localRepository.getUserByIdSync(userId);
        if (user != null) {
            user.setPassword(newPassword.trim());
            localRepository.updateUserProfile(user);
            return true;
        }
        return false;
    }
}

