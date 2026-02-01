package com.example.parkinsonsdiseasedetectionsystem.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * FirebaseAuthUtils - Handles Firebase Authentication with Realtime Database
 * Ensures multi-device login support by always fetching user data from server
 */
public class FirebaseAuthUtils {
    private static final String TAG = "FirebaseAuthUtils";
    private static final String PREFS_NAME = "ParkiScan_Prefs";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    private static FirebaseRealtimeRepository firebaseRepository;
    private static LocalRepository localRepository;
    private static final Executor executor = Executors.newSingleThreadExecutor();

    public static void init(Context context) {
        try {
            if (firebaseRepository == null) {
                firebaseRepository = FirebaseRealtimeRepository.getInstance();
                if (firebaseRepository == null) {
                    Log.e(TAG, "Failed to get FirebaseRealtimeRepository instance");
                }
            }
            if (localRepository == null && context != null) {
                localRepository = LocalRepository.getInstance(context.getApplicationContext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing repositories in FirebaseAuthUtils", e);
        }
    }

    /**
     * Login with Firebase Authentication and Realtime Database
     * CRITICAL: Always fetches user role from Realtime Database, not SharedPreferences
     * This ensures multi-device login works correctly
     */
    public interface LoginCallback {
        void onSuccess(User user);
        void onFailure(String error);
    }

    public static void loginWithFirebase(Context context, String email, String password,
                                        String expectedRole, LoginCallback callback) {
        init(context);
        
        // Ensure repository is initialized
        if (firebaseRepository == null) {
            Log.e(TAG, "FirebaseRepository is null after init - cannot proceed with login");
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onFailure("Failed to initialize Firebase. Please try again.");
                }
            });
            return;
        }

        Log.d(TAG, "Starting Firebase login for: " + email + " with expected role: " + expectedRole);

        // Add timeout handler for Firebase login
        android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable timeoutRunnable = () -> {
            Log.w(TAG, "Firebase login timeout, falling back to Room database");
            fallbackToRoomLogin(context, email, password, expectedRole, callback);
        };
        timeoutHandler.postDelayed(timeoutRunnable, 15000); // 15 second timeout

        // Step 1: Authenticate with Firebase Auth
        firebaseRepository.signInWithEmailAndPassword(email, password, new FirebaseRealtimeRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                timeoutHandler.removeCallbacks(timeoutRunnable);

                // CRITICAL FIX: The user object from signInWithEmailAndPassword already has data from Realtime Database
                // This ensures we're using the role from Firebase, not any cached data

                Log.d(TAG, "Firebase Auth successful, user fetched from Realtime Database. Role: " + user.getRole());

                // Validate user data is complete
                if (user == null || user.getId() == null || user.getId().isEmpty()) {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onFailure("Failed to retrieve user profile. Please try again.");
                        }
                    });
                    return;
                }

                // Check if user is blocked (from Realtime Database)
                if (user.isBlocked()) {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        if (callback != null) {
                            // Different messages for doctors vs other users
                            if ("doctor".equalsIgnoreCase(user.getRole())) {
                                callback.onFailure("Your doctor account is pending admin approval. Please wait for approval or contact admin.");
                            } else {
                                callback.onFailure("Your account has been blocked. Please contact admin.");
                            }
                        }
                    });
                    return;
                }

                // CRITICAL FIX: Check role from Realtime Database user object (not SharedPreferences)
                // The user object is already fetched from Realtime Database by signInWithEmailAndPassword
                if (expectedRole != null && !expectedRole.isEmpty()) {
                    String userRoleFromFirebase = user.getRole();

                    Log.d(TAG, "Validating role - Expected: " + expectedRole + ", User role from Firebase: " + userRoleFromFirebase);

                    if (userRoleFromFirebase == null || userRoleFromFirebase.isEmpty()) {
                        // Role is missing in Firebase - this shouldn't happen, but handle it
                        Log.e(TAG, "User role is missing in Realtime Database for user: " + user.getId());
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onFailure("User profile is incomplete. Please contact support.");
                            }
                        });
                        return;
                    }

                    if (!expectedRole.equalsIgnoreCase(userRoleFromFirebase)) {
                        Log.w(TAG, "Role mismatch - Expected: " + expectedRole + ", Got: " + userRoleFromFirebase);
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onFailure("Invalid role. Please login from the correct portal.");
                            }
                        });
                        return;
                    }

                    Log.d(TAG, "Role validation passed");
                }

                // Save to local database for offline access
                executor.execute(() -> {
                    try {
                        localRepository.insertUser(user);
                        Log.d(TAG, "User saved to local database for offline access");
                    } catch (Exception e) {
                        Log.e(TAG, "Error saving user to local database", e);
                        // Don't fail login if local save fails
                    }
                });

                // Save session (this saves to SharedPreferences, but role comes from Realtime Database user object)
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    saveUserSession(context, user);
                    Log.d(TAG, "Login successful - Session saved. User role: " + user.getRole());
                    if (callback != null) {
                        callback.onSuccess(user);
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                timeoutHandler.removeCallbacks(timeoutRunnable);
                Log.e(TAG, "Firebase login failed", e);
                String tempErrorMessage = e != null ? e.getMessage() : "Unknown error";
                if (tempErrorMessage == null || tempErrorMessage.isEmpty()) {
                    tempErrorMessage = "Login failed. Please check your credentials.";
                }

                final String errorMessage = tempErrorMessage; // Make it final for lambda

                // Check if it's a network/Firebase unavailable error
                if (errorMessage.contains("network") ||
                    errorMessage.contains("unavailable") ||
                    errorMessage.contains("timeout") ||
                    errorMessage.contains("Unable to resolve host") ||
                    errorMessage.contains("INTERNAL_ERROR") ||
                    errorMessage.contains("UNAVAILABLE")) {
                    Log.w(TAG, "Firebase unavailable, falling back to Room database");
                    fallbackToRoomLogin(context, email, password, expectedRole, callback);
                    return;
                }

                // For admin account, always try Room database fallback
                if ("admin@gmail.com".equalsIgnoreCase(email)) {
                    Log.d(TAG, "Admin login attempt, trying Room database fallback");
                    fallbackToRoomLogin(context, email, password, expectedRole, callback);
                    return;
                }

                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(errorMessage);
                    }
                });
            }
        });
    }

    /**
     * Fallback to Room database login when Firebase is unavailable
     */
    private static void fallbackToRoomLogin(Context context, String email, String password,
                                           String expectedRole, LoginCallback callback) {
        Log.d(TAG, "Using Room database fallback for login");
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

        executor.execute(() -> {
            try {
                User user = localRepository.getUserByEmailSync(email);
                if (user != null && user.getPassword() != null && user.getPassword().equals(password)) {
                    if (user.isBlocked()) {
                        mainHandler.post(() -> {
                            if (callback != null) {
                                // Different messages for doctors vs other users
                                if ("doctor".equalsIgnoreCase(user.getRole())) {
                                    callback.onFailure("Your doctor account is pending admin approval. Please wait for approval or contact admin.");
                                } else {
                                    callback.onFailure("Your account has been blocked. Please contact admin.");
                                }
                            }
                        });
                        return;
                    }

                    // Check role if expected
                    if (expectedRole != null && !expectedRole.isEmpty()) {
                        String userRole = user.getRole();
                        if (!expectedRole.equalsIgnoreCase(userRole)) {
                            mainHandler.post(() -> {
                                if (callback != null) {
                                    callback.onFailure("Invalid role. Please login from the correct portal.");
                                }
                            });
                            return;
                        }
                    }

                    // Save session
                    mainHandler.post(() -> {
                        saveUserSession(context, user);
                        Log.d(TAG, "Login successful via Room database");
                        if (callback != null) {
                            callback.onSuccess(user);
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onFailure("Invalid email or password");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Room database login failed", e);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure("Login failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
                    }
                });
            }
        });
    }

    /**
     * Signup with Firebase Authentication and Realtime Database
     */
    public interface SignupCallback {
        void onSuccess(User user);
        void onFailure(String error);
    }

    public static void signupWithFirebase(Context context, String name, String email,
                                         String phone, String password, String role,
                                         SignupCallback callback) {
        Log.d(TAG, "Starting signup process for: " + email);
        init(context);
        
        // Ensure repository is initialized
        if (firebaseRepository == null) {
            Log.e(TAG, "FirebaseRepository is null after init - cannot proceed with signup");
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onFailure("Failed to initialize Firebase. Please try again.");
                }
            });
            return;
        }

        // Add timeout handler
        android.os.Handler timeoutHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable timeoutRunnable = () -> {
            Log.e(TAG, "Signup timeout - Firebase may not be available, falling back to Room database");
            // Fallback to Room database signup
            fallbackToRoomSignup(context, name, email, phone, password, role, callback);
        };
        timeoutHandler.postDelayed(timeoutRunnable, 15000); // 15 second timeout

        // Create a temporary user object (ID will be set by Firebase Auth)
        User newUser = new User("", name, email, phone, role);
        newUser.setPassword(password); // For local storage only
        // 🔹 CRITICAL: Doctors need admin approval - set blocked=true for doctors
        // Patients can login immediately, doctors must wait for admin approval
        if ("doctor".equalsIgnoreCase(role)) {
            newUser.setBlocked(true); // Doctor needs admin approval
            Log.d(TAG, "Doctor signup detected - account will be blocked until admin approval");
        } else {
            newUser.setBlocked(false); // Patients can login immediately
        }
        newUser.setCreatedAt(System.currentTimeMillis());

        Log.d(TAG, "Creating Firebase Auth user...");
        // Create Firebase Auth user and save to Realtime Database
        // Firebase Auth will handle checking if email already exists
        // 🔹 CRITICAL: Pass context to use secondary Firebase App instance
        // This preserves any existing user session during signup
        firebaseRepository.createUserWithEmailAndPassword(context, email, password, newUser,
            new FirebaseRealtimeRepository.UserCallback() {
                @Override
                public void onSuccess(User user) {
                    timeoutHandler.removeCallbacks(timeoutRunnable); // Cancel timeout
                    Log.d(TAG, "Firebase Auth user created successfully. User ID: " + user.getId());
                    // Ensure we're on main thread for UI updates
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        Log.d(TAG, "Saving user to local database and session...");
                        // Save to local database
                        executor.execute(() -> {
                            try {
                                localRepository.insertUser(user);
                                Log.d(TAG, "User saved to local database");
                            } catch (Exception e) {
                                Log.e(TAG, "Error saving to local database", e);
                            }
                        });

                        // Save session
                        saveUserSession(context, user);
                        Log.d(TAG, "User session saved. Calling success callback...");

                        if (callback != null) {
                            callback.onSuccess(user);
                        } else {
                            Log.w(TAG, "Signup callback is null!");
                        }
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    timeoutHandler.removeCallbacks(timeoutRunnable); // Cancel timeout
                    Log.e(TAG, "Signup failed", e);

                    // Check if it's a network/Firebase unavailable error
                    String tempErrorMessage = e != null ? e.getMessage() : "Unknown error";
                    if (tempErrorMessage == null || tempErrorMessage.isEmpty()) {
                        tempErrorMessage = "Signup failed. Please try again.";
                    }

                    final String initialErrorMessage = tempErrorMessage; // Make it final for lambda

                    // If Firebase is not available, fallback to Room
                    if (initialErrorMessage.contains("network") ||
                        initialErrorMessage.contains("unavailable") ||
                        initialErrorMessage.contains("timeout") ||
                        initialErrorMessage.contains("Unable to resolve host")) {
                        Log.w(TAG, "Firebase unavailable, falling back to Room database");
                        fallbackToRoomSignup(context, name, email, phone, password, role, callback);
                        return;
                    }

                    // Ensure we're on main thread for UI updates
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        // Check for specific Firebase Auth errors
                        String finalErrorMessage = initialErrorMessage;
                        if (finalErrorMessage.contains("email-already-in-use") ||
                            finalErrorMessage.contains("already exists") ||
                            finalErrorMessage.contains("ERROR_EMAIL_ALREADY_IN_USE")) {
                            finalErrorMessage = "Email already registered. Please login instead.";
                        } else if (finalErrorMessage.contains("weak-password") ||
                                   finalErrorMessage.contains("ERROR_WEAK_PASSWORD")) {
                            finalErrorMessage = "Password is too weak. Please use a stronger password.";
                        } else if (finalErrorMessage.contains("invalid-email") ||
                                   finalErrorMessage.contains("ERROR_INVALID_EMAIL")) {
                            finalErrorMessage = "Invalid email address. Please check your email.";
                        }
                        Log.e(TAG, "Signup error: " + finalErrorMessage);
                        if (callback != null) {
                            callback.onFailure(finalErrorMessage);
                        } else {
                            Log.w(TAG, "Signup failure callback is null!");
                        }
                    });
                }
            });
    }

    /**
     * Fallback to Room database signup when Firebase is unavailable
     */
    private static void fallbackToRoomSignup(Context context, String name, String email,
                                            String phone, String password, String role,
                                            SignupCallback callback) {
        Log.d(TAG, "Using Room database fallback for signup");
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

        executor.execute(() -> {
            try {
                // Check if user already exists
                User existingUser = localRepository.getUserByEmailSync(email);
                if (existingUser != null) {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onFailure("Email already registered. Please login instead.");
                        }
                    });
                    return;
                }

                // Create new user
                String userId = UUID.randomUUID().toString();
                User newUser = new User(userId, name, email, phone, role);
                newUser.setPassword(password);
                // 🔹 CRITICAL: Doctors need admin approval - set blocked=true for doctors
                if ("doctor".equalsIgnoreCase(role)) {
                    newUser.setBlocked(true); // Doctor needs admin approval
                } else {
                    newUser.setBlocked(false); // Patients can login immediately
                }
                newUser.setCreatedAt(System.currentTimeMillis());

                // Save to Room database
                localRepository.insertUserSync(newUser);

                mainHandler.post(() -> {
                    Log.d(TAG, "User created in Room database successfully");
                    // 🔹 CRITICAL: Don't save session for blocked doctors (they need admin approval)
                    if (!newUser.isBlocked()) {
                        saveUserSession(context, newUser);
                    }
                    if (callback != null) {
                        callback.onSuccess(newUser);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Room database signup failed", e);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure("Signup failed: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
                    }
                });
            }
        });
    }

    /**
     * Save user session to SharedPreferences
     * Note: This is for caching only. Role validation always uses Firebase data.
     */
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

    /**
     * Logout from Firebase
     */
    public static void logout(Context context) {
        // Initialize repository if not already initialized
        init(context);
        
        // Sign out from Firebase Auth (safe even if repository is null)
        if (firebaseRepository != null) {
            firebaseRepository.signOut();
        } else {
            // Fallback: sign out directly from Firebase Auth
            FirebaseAuth.getInstance().signOut();
            Log.w(TAG, "FirebaseRepository was null, signed out directly from FirebaseAuth");
        }
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        
        // Clear SessionManager
        SessionManager sessionManager = new SessionManager(context);
        sessionManager.clearSession();
        sessionManager.clearAppState();
    }

    /**
     * Check if user is logged in (works offline)
     */
    public static boolean isLoggedIn(Context context) {
        // First check Firebase Auth (if available)
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            return true;
        }
        
        // Fallback to SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean fromPrefs = prefs.getBoolean(KEY_IS_LOGGED_IN, false);
        
        // Also check SessionManager for persistent offline login
        SessionManager sessionManager = new SessionManager(context);
        boolean fromSession = sessionManager.isLoggedIn();
        
        return fromPrefs || fromSession;
    }

    /**
     * Get current Firebase user
     */
    public static FirebaseUser getCurrentFirebaseUser() {
        return FirebaseAuth.getInstance().getCurrentUser();
    }

    /**
     * Get user role from SharedPreferences (for caching only)
     * CRITICAL: Always fetch role from Firebase for validation
     */
    public static String getUserRole(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_ROLE, "");
    }

    /**
     * Get user name from SharedPreferences
     */
    public static String getUserName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_NAME, "User");
    }

    /**
     * Get user email from SharedPreferences
     */
    public static String getUserEmail(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_EMAIL, "");
    }

    /**
     * Get user ID from SharedPreferences
     */
    public static String getUserId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_ID, "");
    }

    /**
     * Get current user from Realtime Database
     */
    public static void getCurrentUser(Context context, FirebaseRealtimeRepository.UserCallback callback) {
        init(context);
        
        if (firebaseRepository == null) {
            if (callback != null) {
                callback.onFailure(new Exception("Firebase repository not initialized"));
            }
            return;
        }
        
        String userId = getUserId(context);
        if (userId == null || userId.isEmpty()) {
            // Try to get from Firebase Auth
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser != null) {
                userId = firebaseUser.getUid();
            }
        }
        if (userId == null || userId.isEmpty()) {
            if (callback != null) {
                callback.onFailure(new Exception("No user logged in"));
            }
            return;
        }
        firebaseRepository.getUserById(userId, callback);
    }

    /**
     * Update user profile
     */
    public static void updateUserProfile(Context context, String name, String email,
                                        String phone, FirebaseRealtimeRepository.UserCallback callback) {
        init(context);
        
        if (firebaseRepository == null) {
            if (callback != null) {
                callback.onFailure(new Exception("Firebase repository not initialized"));
            }
            return;
        }
        
        String userId = getUserId(context);
        if (userId == null || userId.isEmpty()) {
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser != null) {
                userId = firebaseUser.getUid();
            }
        }
        if (userId == null || userId.isEmpty()) {
            if (callback != null) {
                callback.onFailure(new Exception("No user logged in"));
            }
            return;
        }

        firebaseRepository.getUserById(userId, new FirebaseRealtimeRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                user.setName(name);
                user.setEmail(email);
                user.setPhone(phone);

                // Update in Realtime Database
                firebaseRepository.updateUser(user, new FirebaseRealtimeRepository.UserCallback() {
                    @Override
                    public void onSuccess(User updatedUser) {
                        // Update in local database
                        executor.execute(() -> {
                            localRepository.updateUserProfile(updatedUser);
                        });

                        // Update session
                        saveUserSession(context, updatedUser);

                        if (callback != null) {
                            callback.onSuccess(updatedUser);
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        if (callback != null) {
                            callback.onFailure(e);
                        }
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                if (callback != null) {
                    callback.onFailure(e);
                }
            }
        });
    }

    /**
     * Change password
     */
    public static void changePassword(String newPassword, PasswordChangeCallback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (callback != null) {
                callback.onFailure("No user logged in");
            }
            return;
        }

        user.updatePassword(newPassword)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // Also update in local database
                    String userId = user.getUid();
                    executor.execute(() -> {
                        localRepository.updateUserPassword(userId, newPassword);
                    });

                    if (callback != null) {
                        callback.onSuccess();
                    }
                } else {
                    if (callback != null) {
                        callback.onFailure(task.getException().getMessage());
                    }
                }
            });
    }

    public interface PasswordChangeCallback {
        void onSuccess();
        void onFailure(String error);
    }
}
