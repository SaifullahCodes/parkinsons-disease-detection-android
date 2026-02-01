package com.example.parkinsonsdiseasedetectionsystem.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.example.parkinsonsdiseasedetectionsystem.models.User;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONObject;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FirebaseRealtimeRepository - Handles all Firebase Realtime Database operations
 * Uses Realtime Database for user data storage with proper security rules
 */
public class FirebaseRealtimeRepository {

    private static final String TAG = "FirebaseRealtimeRepo";
    private static FirebaseRealtimeRepository instance;

    private final FirebaseDatabase database;
    private final FirebaseAuth firebaseAuth;

    // Database paths
    private static final String PATH_USERS = "users";
    private static final String PATH_REPORTS = "reports";

    private FirebaseRealtimeRepository() {
        firebaseAuth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance();

        // Log database URL for debugging
        Log.d(TAG, "Firebase Realtime Database initialized");
        Log.d(TAG, "Database URL: " + database.getReference().toString());

        // DISABLE offline persistence to ensure fresh data from server
        // This prevents stale cached data from being used
        try {
            database.setPersistenceEnabled(false);
            Log.d(TAG, "Realtime Database offline persistence DISABLED - using server data only");
        } catch (Exception e) {
            Log.w(TAG, "Failed to disable Realtime Database persistence: " + e.getMessage());
        }

        // Test connection by checking auth state
        FirebaseUser testUser = firebaseAuth.getCurrentUser();
        if (testUser != null) {
            Log.d(TAG, "Current authenticated user: " + testUser.getUid());
            Log.d(TAG, "User email: " + testUser.getEmail());
        } else {
            Log.w(TAG, "No authenticated user found on initialization");
        }
    }

    public static synchronized FirebaseRealtimeRepository getInstance() {
        if (instance == null) {
            instance = new FirebaseRealtimeRepository();
        }
        return instance;
    }

    // ========================================
    // AUTHENTICATION METHODS
    // ========================================

    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }

    public boolean isUserLoggedIn() {
        return firebaseAuth.getCurrentUser() != null;
    }

    public void signOut() {
        firebaseAuth.signOut();
    }

    // ========================================
    // USER OPERATIONS
    // ========================================

    public interface UserCallback {
        void onSuccess(User user);
        void onFailure(Exception e);
    }

    public interface UserListCallback {
        void onSuccess(List<User> users);
        void onFailure(Exception e);
    }

    public interface ReportCallback {
        void onSuccess(Report report);
        void onFailure(Exception e);
    }

    public interface ReportListCallback {
        void onSuccess(List<Report> reports);
        void onFailure(Exception e);
    }

    /**
     * Create user in Firebase Auth and Realtime Database
     * 🔹 CRITICAL: Uses secondary Firebase App instance to avoid disrupting current admin session
     */
    public void createUserWithEmailAndPassword(String email, String password, User userData,
                                               UserCallback callback) {
        createUserWithEmailAndPassword(null, email, password, userData, callback);
    }
    
    /**
     * Create user in Firebase Auth and Realtime Database with Context
     * 🔹 CRITICAL: Uses secondary Firebase App instance to avoid disrupting current admin session
     */
    public void createUserWithEmailAndPassword(android.content.Context context, String email, String password, User userData,
                                               UserCallback callback) {
        // 🔹 CRITICAL: Check if this is a patient/user/doctor self-signup or admin creating a user
        // For self-signup (patient/doctor): Use main app and keep user signed in (they need to be authenticated to save their data)
        // For admin creating users: Use secondary app to preserve admin session
        String userRole = userData.getRole();
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        // Self-signup: if no one is logged in, it's a self-signup (patient or doctor)
        // Admin creating: if admin is logged in, it's admin creating a user
        boolean isSelfSignup = currentUser == null;
        
        if (isSelfSignup) {
            // 🔹 SELF-SIGNUP (PATIENT/DOCTOR): Use main app and keep user signed in
            // This allows them to write their own user data to the database
            Log.d(TAG, "Self-signup detected (Role: " + userRole + ") - using main Firebase App");
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            // Use Firebase UID as user ID
                            userData.setId(firebaseUser.getUid());
                            Log.d(TAG, "User created and authenticated in main app: " + firebaseUser.getUid() + " (Role: " + userRole + ")");
                            
                            // Save user data to Realtime Database (user is now authenticated in main app)
                            // Note: For doctors, userData should already have blocked=true (set in signupWithFirebase)
                            saveUserToRealtimeDatabase(userData, callback);
                        } else {
                            mainHandler.post(() -> {
                                if (callback != null) {
                                    callback.onFailure(new Exception("Failed to get Firebase user"));
                                }
                            });
                        }
                    } else {
                        Exception exception = task.getException();
                        Log.e(TAG, "Firebase Auth signup failed", exception);
                        mainHandler.post(() -> {
                            if (callback != null) {
                                String errorMsg = exception != null ? exception.getMessage() : "Signup failed";
                                if (errorMsg != null && errorMsg.contains("email-already-in-use")) {
                                    callback.onFailure(new Exception("Email already registered. Please login instead."));
                                } else {
                                    callback.onFailure(exception != null ? exception : new Exception("Signup failed"));
                                }
                            }
                        });
                    }
                });
        } else {
            // 🔹 ADMIN CREATING USER: Use secondary Firebase App instance
            // This prevents the admin's session from being disrupted
            FirebaseApp secondaryApp = getOrCreateSecondaryFirebaseApp(context);
            FirebaseAuth secondaryAuth = FirebaseAuth.getInstance(secondaryApp);
            
            Log.d(TAG, "Admin creating user - using secondary Firebase App to preserve admin session");
            Log.d(TAG, "Admin session preserved: " + (firebaseAuth.getCurrentUser() != null ? firebaseAuth.getCurrentUser().getEmail() : "none"));
            
            secondaryAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = secondaryAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            // Use Firebase UID as user ID
                            userData.setId(firebaseUser.getUid());
                            
                            // 🔹 CRITICAL: Sign out from secondary app immediately
                            // This ensures the new user doesn't stay logged in
                            secondaryAuth.signOut();
                            Log.d(TAG, "Signed out from secondary app - admin session still active");
                            
                            // Verify admin session is still intact
                            FirebaseUser adminUser = firebaseAuth.getCurrentUser();
                            if (adminUser != null) {
                                Log.d(TAG, "✓ Admin session preserved: " + adminUser.getEmail());
                            } else {
                                Log.w(TAG, "⚠ Admin session lost - this should not happen");
                            }
                            
                            // Save user data to Realtime Database (using main app with admin session)
                            saveUserToRealtimeDatabase(userData, callback);
                        } else {
                            mainHandler.post(() -> {
                                if (callback != null) {
                                    callback.onFailure(new Exception("Failed to get Firebase user"));
                                }
                            });
                        }
                    } else {
                        Exception exception = task.getException();
                        Log.e(TAG, "Firebase Auth signup failed", exception);
                        mainHandler.post(() -> {
                            if (callback != null) {
                                String errorMsg = exception != null ? exception.getMessage() : "Signup failed";
                                if (errorMsg != null && errorMsg.contains("email-already-in-use")) {
                                    callback.onFailure(new Exception("Email already registered. Please login instead."));
                                } else {
                                    callback.onFailure(exception != null ? exception : new Exception("Signup failed"));
                                }
                            }
                        });
                    }
                });
        }
    }
    
    /**
     * Get or create a secondary Firebase App instance for user creation operations
     * This prevents disrupting the main app's authentication session
     */
    private FirebaseApp getOrCreateSecondaryFirebaseApp(android.content.Context context) {
        String secondaryAppName = "secondary_app_for_user_creation";
        try {
            FirebaseApp secondaryApp = FirebaseApp.getInstance(secondaryAppName);
            Log.d(TAG, "Using existing secondary Firebase App");
            return secondaryApp;
        } catch (IllegalStateException e) {
            // Secondary app doesn't exist, create it
            try {
                Log.d(TAG, "Creating secondary Firebase App for user creation");
                FirebaseOptions options = FirebaseApp.getInstance().getOptions();
                
                // Get application context
                android.content.Context appContext = context != null ? 
                    context.getApplicationContext() : 
                    getApplicationContextFromMainApp();
                
                if (appContext == null) {
                    Log.e(TAG, "Cannot get application context, using main app");
                    return FirebaseApp.getInstance();
                }
                
                FirebaseApp secondaryApp = FirebaseApp.initializeApp(
                    appContext,
                    options,
                    secondaryAppName
                );
                Log.d(TAG, "Secondary Firebase App created successfully");
                return secondaryApp;
            } catch (Exception ex) {
                Log.e(TAG, "Failed to create secondary Firebase App, using main app", ex);
                // Fallback to main app if secondary app creation fails
                return FirebaseApp.getInstance();
            }
        }
    }
    
    /**
     * Get application context from the main Firebase App
     * This is a fallback when context is not provided
     */
    private android.content.Context getApplicationContextFromMainApp() {
        try {
            // Try to get context from FirebaseApp's internal state
            // This is a workaround when context is not available
            FirebaseApp mainApp = FirebaseApp.getInstance();
            // Note: FirebaseApp doesn't expose getApplicationContext() directly
            // We'll need to handle this differently
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get application context", e);
            return null;
        }
    }

    /**
     * Sign in with email and password
     * Fetches user data from Realtime Database after authentication
     */
    public void signInWithEmailAndPassword(String email, String password, UserCallback callback) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                if (task.isSuccessful()) {
                    FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                    if (firebaseUser != null) {
                        Log.d(TAG, "Firebase Auth signin successful, fetching user data from Realtime Database");
                        // Fetch user data from Realtime Database (always from server)
                        getUserById(firebaseUser.getUid(), callback);
                    } else {
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onFailure(new Exception("Failed to get Firebase user"));
                            }
                        });
                    }
                } else {
                    Exception exception = task.getException();
                    Log.e(TAG, "Firebase Auth signin failed", exception);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            String errorMsg = exception != null ? exception.getMessage() : "Login failed";
                            if (errorMsg != null && errorMsg.contains("wrong-password")) {
                                callback.onFailure(new Exception("Wrong password. Please try again."));
                            } else if (errorMsg != null && errorMsg.contains("user-not-found")) {
                                callback.onFailure(new Exception("User not found. Please sign up first."));
                            } else {
                                callback.onFailure(exception != null ? exception : new Exception("Login failed"));
                            }
                        }
                    });
                }
            });
    }

    /**
     * Save user to Realtime Database
     * 🔹 CRITICAL: For patient signup, must use auth.uid as the path to match security rules
     */
    public void saveUserToRealtimeDatabase(User user, UserCallback callback) {
        // Check authentication
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User is not authenticated - cannot save user to Realtime Database");
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onFailure(new Exception("User not authenticated. Please login again."));
                }
            });
            return;
        }

        // 🔹 CRITICAL: For self-signup (patient/doctor), ensure user ID matches auth.uid
        // This is required by Firebase security rules: users/$uid where $uid === auth.uid
        String authUid = currentUser.getUid();
        String userRole = user.getRole();
        // Check if this is a self-signup (user is creating their own account)
        // vs admin creating (admin is logged in, creating another user)
        boolean isSelfSignup = authUid.equals(user.getId());
        
        if (isSelfSignup) {
            // 🔹 SELF-SIGNUP (PATIENT/DOCTOR): Must use auth.uid as the database path
            // Ensure user.getId() matches auth.uid
            if (!authUid.equals(user.getId())) {
                Log.w(TAG, "User ID mismatch for self-signup. Setting user ID to auth.uid: " + authUid);
                user.setId(authUid);
            }
            Log.d(TAG, "🔹 SELF-SIGNUP (" + userRole + "): Saving to /users/" + authUid + " (matches auth.uid for security rules)");
        } else {
            // Admin creating user - use the user's ID (which was set from secondary app's auth.uid)
            Log.d(TAG, "Admin creating user (" + userRole + ") - saving to /users/" + user.getId());
        }

        Log.d(TAG, "Saving user to Realtime Database: " + user.getId() + " (Name: " + user.getName() + ", Role: " + user.getRole() + ")");
        Log.d(TAG, "Current authenticated user: " + currentUser.getUid() + " (" + currentUser.getEmail() + ")");

        Map<String, Object> userMap = userToMap(user);
        
        // 🔹 CRITICAL: Ensure user object's ID field in the map matches the path
        // This is important for security rules validation
        userMap.put("id", isSelfSignup ? authUid : user.getId());

        // 🔹 CRITICAL: For self-signup, use auth.uid directly as the path
        // For admin creating users, use user.getId() (which is the new user's auth.uid from secondary app)
        String pathUid = isSelfSignup ? authUid : user.getId();
        DatabaseReference userRef = database.getReference(PATH_USERS).child(pathUid);
        
        Log.d(TAG, "Database path: /users/" + pathUid + " (auth.uid: " + authUid + ")");

        // Use setValue to write data (overwrites existing data)
        userRef.setValue(userMap)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "✓ User saved successfully to Realtime Database: " + user.getId());
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSuccess(user);
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "✗ Error saving user to Realtime Database", e);
                Log.e(TAG, "Error message: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
                if (e instanceof com.google.firebase.database.DatabaseException) {
                    Log.e(TAG, "DatabaseException - This might be a permission issue. Check Firebase database rules.");
                }
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                });
            });
    }

    /**
     * Get user by ID from Realtime Database
     * Always fetches from server for multi-device support
     */
    public void getUserById(String userId, UserCallback callback) {
        Log.d(TAG, "Fetching user from Realtime Database: " + userId);

        DatabaseReference userRef = database.getReference(PATH_USERS).child(userId);

        // Use addListenerForSingleValueEvent to fetch from server
        // This ensures we get fresh data, not cached data
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

                if (snapshot.exists()) {
                    User user = mapToUser(snapshot);
                    if (user != null) {
                        Log.d(TAG, "User fetched from Realtime Database. Role: " + user.getRole());
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onSuccess(user);
                            }
                        });
                    } else {
                        Log.e(TAG, "Failed to map user data from snapshot");
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onFailure(new Exception("Failed to parse user data"));
                            }
                        });
                    }
                } else {
                    Log.e(TAG, "User document does not exist: " + userId);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onFailure(new Exception("User not found in database. Please contact support."));
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching user from Realtime Database", error.toException());
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(error.toException());
                    }
                });
            }
        });
    }

    /**
     * Get user by email from Realtime Database
     */
    public void getUserByEmail(String email, UserCallback callback) {
        DatabaseReference usersRef = database.getReference(PATH_USERS);

        Query query = usersRef.orderByChild("email").equalTo(email).limitToFirst(1);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

                if (snapshot.exists() && snapshot.hasChildren()) {
                    DataSnapshot userSnapshot = snapshot.getChildren().iterator().next();
                    User user = mapToUser(userSnapshot);
                    if (user != null) {
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onSuccess(user);
                            }
                        });
                    } else {
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onFailure(new Exception("Failed to parse user data"));
                            }
                        });
                    }
                } else {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onFailure(new Exception("User not found"));
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(error.toException());
                    }
                });
            }
        });
    }

    /**
     * Get all users from Realtime Database
     * Uses server data only (no cache) to ensure fresh data
     */
    public void getAllUsers(UserListCallback callback) {
        DatabaseReference usersRef = database.getReference(PATH_USERS);

        // Keep reference to ensure we get fresh data
        usersRef.keepSynced(false); // Don't keep synced to avoid cache

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

                List<User> users = new ArrayList<>();
                if (snapshot.exists()) {
                    for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                        User user = mapToUser(userSnapshot);
                        if (user != null) {
                            users.add(user);
                        }
                    }
                }
                Log.d(TAG, "Loaded " + users.size() + " users from Realtime Database");
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSuccess(users);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading users from Realtime Database", error.toException());
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(error.toException());
                    }
                });
            }
        });
    }

    /**
     * Get users by role from Realtime Database
     * Uses server data only (no cache) to ensure fresh data
     */
    public void getUsersByRole(String role, UserListCallback callback) {
        // Check authentication first
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User is not authenticated - cannot fetch users by role");
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onFailure(new Exception("User not authenticated. Please login again."));
                }
            });
            return;
        }

        Log.d(TAG, "Fetching users with role: " + role + " from Firebase Realtime Database");
        Log.d(TAG, "Current authenticated user: " + currentUser.getUid() + " (" + currentUser.getEmail() + ")");

        // Simplified approach: Load all users and filter by role in memory
        // This avoids index requirements and is more reliable
        DatabaseReference usersRef = database.getReference(PATH_USERS);
        usersRef.keepSynced(false);

        Log.d(TAG, "Loading all users and filtering by role: " + role);

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

                List<User> users = new ArrayList<>();
                if (snapshot.exists()) {
                    Log.d(TAG, "Snapshot exists, processing " + snapshot.getChildrenCount() + " total users");
                    int filteredCount = 0;
                    for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                        User user = mapToUser(userSnapshot);
                        if (user != null) {
                            String userRole = user.getRole();
                            if (userRole != null && role.equalsIgnoreCase(userRole)) {
                                users.add(user);
                                filteredCount++;
                                Log.d(TAG, "✓ Mapped user: " + user.getName() + " (ID: " + user.getId() + ", Role: " + user.getRole() + ")");
                            } else {
                                Log.d(TAG, "Skipped user: " + user.getName() + " (Role: " + userRole + ", expected: " + role + ")");
                            }
                        } else {
                            Log.w(TAG, "Failed to map user from snapshot: " + userSnapshot.getKey());
                        }
                    }
                    Log.d(TAG, "✓ Successfully loaded " + filteredCount + " users with role: " + role + " out of " + snapshot.getChildrenCount() + " total users");
                } else {
                    Log.d(TAG, "No users found in database");
                }
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSuccess(users);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "✗ Error loading users by role from Realtime Database");
                Log.e(TAG, "DatabaseError code: " + error.getCode());
                Log.e(TAG, "DatabaseError message: " + error.getMessage());
                Log.e(TAG, "DatabaseError details: " + error.getDetails());

                // If query fails (e.g., missing index), try loading all users and filtering in memory
                if (error.getCode() == DatabaseError.PERMISSION_DENIED) {
                    Log.e(TAG, "PERMISSION DENIED - Check Firebase database rules!");
                    Log.e(TAG, "Make sure authenticated users can read user data in database.rules.json");
                    Log.e(TAG, "Current authenticated user: " + currentUser.getUid());

                    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    mainHandler.post(() -> {
                        if (callback != null) {
                            Exception exception = new Exception("Permission denied. Please check Firebase database rules.");
                            callback.onFailure(exception);
                        }
                    });
                } else {
                    // Try fallback: load all users and filter in memory
                    Log.w(TAG, "Query failed (code: " + error.getCode() + "), trying fallback: load all users and filter by role");
                    Log.w(TAG, "Error message: " + error.getMessage());

                    usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

                            List<User> users = new ArrayList<>();
                            if (snapshot.exists()) {
                                Log.d(TAG, "✓ Fallback: Snapshot exists, processing " + snapshot.getChildrenCount() + " users");
                                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                                    User user = mapToUser(userSnapshot);
                                    if (user != null) {
                                        String userRole = user.getRole();
                                        if (userRole != null && role.equalsIgnoreCase(userRole)) {
                                            users.add(user);
                                            Log.d(TAG, "✓ Fallback: Mapped user: " + user.getName() + " (ID: " + user.getId() + ", Role: " + user.getRole() + ")");
                                        } else {
                                            Log.d(TAG, "Fallback: Skipped user " + user.getName() + " (Role: " + userRole + ", expected: " + role + ")");
                                        }
                                    } else {
                                        Log.w(TAG, "Fallback: Failed to map user from snapshot: " + userSnapshot.getKey());
                                    }
                                }
                            } else {
                                Log.d(TAG, "Fallback: No users found in database");
                            }
                            Log.d(TAG, "✓ Fallback: Successfully loaded " + users.size() + " users with role: " + role);
                            mainHandler.post(() -> {
                                if (callback != null) {
                                    callback.onSuccess(users);
                                }
                            });
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError fallbackError) {
                            Log.e(TAG, "✗ Fallback also failed");
                            Log.e(TAG, "Fallback DatabaseError code: " + fallbackError.getCode());
                            Log.e(TAG, "Fallback DatabaseError message: " + fallbackError.getMessage());
                            Log.e(TAG, "Fallback DatabaseError details: " + fallbackError.getDetails());

                            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                            mainHandler.post(() -> {
                                if (callback != null) {
                                    String errorMessage = "Failed to load users";
                                    if (fallbackError.getCode() == DatabaseError.PERMISSION_DENIED) {
                                        errorMessage = "Permission denied. Please check Firebase database rules.";
                                    } else if (fallbackError.getCode() == DatabaseError.NETWORK_ERROR || fallbackError.getCode() == DatabaseError.DISCONNECTED) {
                                        errorMessage = "Network error. Please check your internet connection.";
                                    } else {
                                        errorMessage = "Database error: " + (fallbackError.getMessage() != null ? fallbackError.getMessage() : "Unknown error");
                                    }

                                    Exception exception = fallbackError.toException();
                                    if (exception == null) {
                                        exception = new Exception(errorMessage);
                                    } else {
                                        exception = new Exception(errorMessage, exception);
                                    }
                                    callback.onFailure(exception);
                                }
                            });
                        }
                    });
                }
            }
        });
    }

    /**
     * Update user in Realtime Database
     */
    public void updateUser(User user, UserCallback callback) {
        // Check authentication
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User is not authenticated - cannot update user");
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onFailure(new Exception("User not authenticated. Please login again."));
                }
            });
            return;
        }

        Log.d(TAG, "Updating user in Realtime Database: " + user.getId() + " (Name: " + user.getName() + ")");
        Log.d(TAG, "Current authenticated user: " + currentUser.getUid());

        Map<String, Object> userMap = userToMap(user);

        DatabaseReference userRef = database.getReference(PATH_USERS).child(user.getId());

        userRef.updateChildren(userMap)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "✓ User updated successfully in Realtime Database: " + user.getId());
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSuccess(user);
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "✗ Error updating user in Realtime Database", e);
                Log.e(TAG, "Error message: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
                if (e instanceof com.google.firebase.database.DatabaseException) {
                    Log.e(TAG, "DatabaseException - This might be a permission issue. Check Firebase database rules.");
                }
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                });
            });
    }

    /**
     * Delete user from Realtime Database
     */
    public void deleteUser(String userId, String email, UserCallback callback) {
        // Check authentication
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User is not authenticated - cannot delete user");
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onFailure(new Exception("User not authenticated. Please login again."));
                }
            });
            return;
        }

        Log.d(TAG, "Deleting user from Realtime Database: " + userId + " (Email: " + email + ")");
        Log.d(TAG, "Current authenticated user: " + currentUser.getUid());

        DatabaseReference userRef = database.getReference(PATH_USERS).child(userId);

        userRef.removeValue()
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "✓ User deleted successfully from Realtime Database: " + userId);
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        // Create a dummy user object for callback
                        User deletedUser = new User();
                        deletedUser.setId(userId);
                        deletedUser.setEmail(email);
                        callback.onSuccess(deletedUser);
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "✗ Error deleting user from Realtime Database", e);
                Log.e(TAG, "Error message: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
                if (e instanceof com.google.firebase.database.DatabaseException) {
                    Log.e(TAG, "DatabaseException - This might be a permission issue. Check Firebase database rules.");
                }
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                });
            });
    }

    /**
     * Update user block status
     */
    public void updateUserBlockStatus(String userId, boolean isBlocked, UserCallback callback) {
        DatabaseReference userRef = database.getReference(PATH_USERS).child(userId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("blocked", isBlocked);

        userRef.updateChildren(updates)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "User block status updated: " + userId);
                getUserById(userId, callback);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error updating user block status", e);
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                });
            });
    }

    // ========================================
    // MAP CONVERSION METHODS
    // ========================================

    private Map<String, Object> userToMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getName() != null ? user.getName() : "");
        map.put("email", user.getEmail() != null ? user.getEmail() : "");
        map.put("phone", user.getPhone() != null ? user.getPhone() : "");
        map.put("role", user.getRole() != null ? user.getRole() : "patient");
        map.put("blocked", user.isBlocked());
        map.put("createdAt", user.getCreatedAt());
        // Note: Password is NOT stored in Realtime Database for security
        return map;
    }

    private User mapToUser(DataSnapshot snapshot) {
        try {
            User user = new User();

            // Use snapshot key as ID if "id" field is missing
            String userId = snapshot.getKey();
            String idField = snapshot.child("id").getValue(String.class);
            if (idField != null && !idField.isEmpty()) {
                userId = idField;
            }
            user.setId(userId);

            user.setName(snapshot.child("name").getValue(String.class));
            user.setEmail(snapshot.child("email").getValue(String.class));
            user.setPhone(snapshot.child("phone").getValue(String.class));

            // CRITICAL: Role must be extracted from Realtime Database
            String role = snapshot.child("role").getValue(String.class);
            user.setRole(role != null ? role : "patient"); // Default to patient if role is missing

            Boolean blocked = snapshot.child("blocked").getValue(Boolean.class);
            user.setBlocked(blocked != null && blocked);

            Long createdAt = snapshot.child("createdAt").getValue(Long.class);
            if (createdAt != null) {
                user.setCreatedAt(createdAt);
            } else {
                user.setCreatedAt(System.currentTimeMillis());
            }

            return user;
        } catch (Exception e) {
            Log.e(TAG, "Error mapping user from snapshot", e);
            return null;
        }
    }

    // ========================================
    // REPORT OPERATIONS
    // ========================================

    /**
     * Test Firebase connection and rules
     */
    public void testConnection(ReportCallback callback) {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "✗ TEST FAILED: No authenticated user");
            if (callback != null) {
                callback.onFailure(new Exception("No authenticated user"));
            }
            return;
        }

        Log.d(TAG, "=== TESTING FIREBASE CONNECTION ===");
        Log.d(TAG, "Auth UID: " + currentUser.getUid());
        Log.d(TAG, "Database URL: " + database.getReference().toString());

        // Try to write a test value
        DatabaseReference testRef = database.getReference("test").child("connection");
        Map<String, Object> testData = new HashMap<>();
        testData.put("timestamp", System.currentTimeMillis());
        testData.put("uid", currentUser.getUid());

        testRef.setValue(testData)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "✓ TEST PASSED: Can write to database");
                // Clean up test data
                testRef.removeValue();
                if (callback != null) {
                    callback.onSuccess(null); // Pass null since this is just a test
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "✗ TEST FAILED: Cannot write to database", e);
                Log.e(TAG, "Error: " + e.getMessage());
                if (callback != null) {
                    callback.onFailure(e);
                }
            });
    }

    /**
     * Save report to Realtime Database
     */
    public void saveReport(Report report, ReportCallback callback) {
        // Validate report
        if (report == null) {
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onFailure(new Exception("Report is null"));
                }
            });
            return;
        }

        if (report.getId() == null || report.getId().isEmpty()) {
            Log.e(TAG, "Report ID is null or empty, generating new ID");
            report.setId(java.util.UUID.randomUUID().toString());
        }

        // Check if user is authenticated - CRITICAL for database rules
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "✗ No authenticated user - cannot save report (Firebase Auth required)");
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onFailure(new Exception("User not authenticated. Please login first."));
                }
            });
            return;
        }

        // CRITICAL: Ensure patientId matches authenticated user's UID
        // Database rules require: newData.child('patientId').val() === auth.uid
        String authUid = currentUser.getUid();
        if (report.getPatientId() == null || report.getPatientId().isEmpty()) {
            Log.w(TAG, "Report patientId is null/empty, setting to authenticated user UID: " + authUid);
            report.setPatientId(authUid);
        } else if (!report.getPatientId().equals(authUid)) {
            Log.w(TAG, "Report patientId (" + report.getPatientId() + ") doesn't match auth.uid (" + authUid + "), updating to match");
            report.setPatientId(authUid);
        }

        Log.d(TAG, "✓ User authenticated: " + authUid);
        Log.d(TAG, "✓ Report patientId: " + report.getPatientId());
        Log.d(TAG, "✓ Saving report as user: " + authUid);

        // Convert report to JSON string for text storage
        String reportJson = reportToJsonString(report);
        if (reportJson == null || reportJson.isEmpty()) {
            Log.e(TAG, "Failed to convert report to JSON string");
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onFailure(new Exception("Failed to serialize report to JSON"));
                }
            });
            return;
        }

        Log.d(TAG, "Saving report to Realtime Database - ID: " + report.getId() + ", Patient: " + report.getPatientId());
        Log.d(TAG, "Report JSON length: " + reportJson.length() + " characters");

        // Store report as text (JSON string) in Realtime Database
        DatabaseReference reportRef = database.getReference(PATH_REPORTS).child(report.getId());

        // Store report as text (JSON string) in Realtime Database
        // Also store key fields separately for querying
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("reportData", reportJson); // PRIMARY: Full report stored as JSON text
        reportData.put("id", report.getId());
        reportData.put("patientId", report.getPatientId() != null ? report.getPatientId() : "");
        reportData.put("patientName", report.getPatientName() != null ? report.getPatientName() : "");
        reportData.put("doctorId", report.getDoctorId() != null ? report.getDoctorId() : "");
        reportData.put("doctorName", report.getDoctorName() != null ? report.getDoctorName() : "");
        reportData.put("recordingType", report.getRecordingType() != null ? report.getRecordingType() : "");
        reportData.put("aiPrediction", report.getAiPrediction());
        reportData.put("aiResult", report.getAiResult() != null ? report.getAiResult() : "");
        reportData.put("doctorVerification", report.getDoctorVerification() != null ? report.getDoctorVerification() : "");
        reportData.put("createdAt", report.getCreatedAt());
        reportData.put("verifiedAt", report.getVerifiedAt());
        reportData.put("submissionId", report.getSubmissionId() != null ? report.getSubmissionId() : "");
        reportData.put("videoUrl", report.getVideoUrl() != null ? report.getVideoUrl() : "");
        reportData.put("filePath", report.getFilePath() != null ? report.getFilePath() : "");

        Log.d(TAG, "=== REPORT SAVE DEBUG INFO ===");
        Log.d(TAG, "Firebase Auth State: " + (currentUser != null ? "AUTHENTICATED" : "NOT AUTHENTICATED"));
        Log.d(TAG, "Auth UID: " + authUid);
        Log.d(TAG, "Report ID: " + report.getId());
        Log.d(TAG, "Report patientId: " + report.getPatientId());
        Log.d(TAG, "Report data keys: " + reportData.keySet().toString());
        Log.d(TAG, "Report patientId in data map: " + reportData.get("patientId"));
        Log.d(TAG, "Database path: " + PATH_REPORTS + "/" + report.getId());
        Log.d(TAG, "JSON text length: " + reportJson.length());

        // CRITICAL: Double-check patientId matches auth.uid
        Object patientIdInData = reportData.get("patientId");
        if (patientIdInData == null || !patientIdInData.toString().equals(authUid)) {
            Log.e(TAG, "✗ CRITICAL ERROR: patientId mismatch!");
            Log.e(TAG, "  patientId in data: " + patientIdInData);
            Log.e(TAG, "  auth.uid: " + authUid);
            Log.e(TAG, "  Fixing patientId...");
            reportData.put("patientId", authUid);
            Log.d(TAG, "  ✓ Fixed: patientId now = " + authUid);
        } else {
            Log.d(TAG, "✓ patientId matches auth.uid: " + authUid);
        }

        // Verify Firebase Auth token
        currentUser.getIdToken(false).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String token = task.getResult().getToken();
                Log.d(TAG, "✓ Firebase Auth token obtained (length: " + (token != null ? token.length() : 0) + ")");
            } else {
                Log.e(TAG, "✗ Failed to get Firebase Auth token: " + task.getException());
            }
        });

        Log.d(TAG, "Attempting to save report to: " + reportRef.toString());
        Log.d(TAG, "=================================");

        reportRef.setValue(reportData)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "✓ Report successfully saved to Realtime Database: " + report.getId());
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSuccess(report);
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "✗ Error saving report to Realtime Database", e);
                Log.e(TAG, "Error details: " + e.getMessage());
                if (e.getMessage() != null) {
                    if (e.getMessage().contains("PERMISSION_DENIED") || e.getMessage().contains("permission")) {
                        Log.e(TAG, "Permission denied - check database security rules");
                    } else if (e.getMessage().contains("network") || e.getMessage().contains("unavailable")) {
                        Log.e(TAG, "Network error - check internet connection");
                    }
                }
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                });
            });
    }

    /**
     * Update report in Realtime Database
     */
    public void updateReport(Report report, ReportCallback callback) {
        // Validate report
        if (report == null) {
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onFailure(new Exception("Report is null"));
                }
            });
            return;
        }

        if (report.getId() == null || report.getId().isEmpty()) {
            Log.e(TAG, "Report ID is null or empty, cannot update");
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onFailure(new Exception("Report ID is required for update"));
                }
            });
            return;
        }

        // Check if user is authenticated
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user, but proceeding with update (may fail due to security rules)");
        } else {
            Log.d(TAG, "Updating report as user: " + currentUser.getUid());
        }

        // Convert report to JSON string for text storage
        String reportJson = reportToJsonString(report);
        if (reportJson == null || reportJson.isEmpty()) {
            Log.e(TAG, "Failed to convert report to JSON string for update");
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onFailure(new Exception("Failed to serialize report to JSON"));
                }
            });
            return;
        }

        Log.d(TAG, "Updating report in Realtime Database - ID: " + report.getId());
        Log.d(TAG, "Report JSON length: " + reportJson.length() + " characters");

        DatabaseReference reportRef = database.getReference(PATH_REPORTS).child(report.getId());

        // Update report as text (JSON string) in Realtime Database
        // Also update key fields separately for querying
        Map<String, Object> reportData = new HashMap<>();
        reportData.put("reportData", reportJson); // PRIMARY: Full report stored as JSON text
        reportData.put("id", report.getId());
        reportData.put("patientId", report.getPatientId() != null ? report.getPatientId() : "");
        reportData.put("patientName", report.getPatientName() != null ? report.getPatientName() : "");
        reportData.put("doctorId", report.getDoctorId() != null ? report.getDoctorId() : "");
        reportData.put("doctorName", report.getDoctorName() != null ? report.getDoctorName() : "");
        reportData.put("recordingType", report.getRecordingType() != null ? report.getRecordingType() : "");
        reportData.put("aiPrediction", report.getAiPrediction());
        reportData.put("aiResult", report.getAiResult() != null ? report.getAiResult() : "");
        reportData.put("doctorVerification", report.getDoctorVerification() != null ? report.getDoctorVerification() : "");
        reportData.put("createdAt", report.getCreatedAt());
        reportData.put("verifiedAt", report.getVerifiedAt());
        reportData.put("submissionId", report.getSubmissionId() != null ? report.getSubmissionId() : "");
        reportData.put("videoUrl", report.getVideoUrl() != null ? report.getVideoUrl() : "");
        reportData.put("filePath", report.getFilePath() != null ? report.getFilePath() : "");

        // Use setValue to ensure all data is saved (creates if doesn't exist, updates if exists)
        reportRef.setValue(reportData)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Report updated in Realtime Database: " + report.getId());
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSuccess(report);
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error updating report in Realtime Database", e);
                Log.e(TAG, "Error details: " + e.getMessage());
                // If update fails, try to set the entire report (create if doesn't exist)
                reportRef.setValue(reportData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Report created/updated in Realtime Database: " + report.getId());
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onSuccess(report);
                            }
                        });
                    })
                    .addOnFailureListener(e2 -> {
                        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onFailure(e2);
                            }
                        });
                    });
            });
    }

    /**
     * Get report by ID from Realtime Database
     */
    public void getReportById(String reportId, ReportCallback callback) {
        DatabaseReference reportRef = database.getReference(PATH_REPORTS).child(reportId);

        reportRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

                if (snapshot.exists()) {
                    Report report = mapToReport(snapshot);
                    if (report != null) {
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onSuccess(report);
                            }
                        });
                    } else {
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onFailure(new Exception("Failed to parse report data"));
                            }
                        });
                    }
                } else {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onFailure(new Exception("Report not found"));
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(error.toException());
                    }
                });
            }
        });
    }

    /**
     * Get all reports for a patient from Realtime Database
     */
    public void getReportsForUser(String patientId, ReportListCallback callback) {
        DatabaseReference reportsRef = database.getReference(PATH_REPORTS);

        Query query = reportsRef.orderByChild("patientId").equalTo(patientId);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

                List<Report> reports = new ArrayList<>();
                if (snapshot.exists()) {
                    for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                        Report report = mapToReport(reportSnapshot);
                        if (report != null) {
                            reports.add(report);
                        }
                    }
                }
                // Sort by createdAt descending
                reports.sort((r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));

                Log.d(TAG, "Loaded " + reports.size() + " reports for patient: " + patientId);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSuccess(reports);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading reports for patient from Realtime Database", error.toException());
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(error.toException());
                    }
                });
            }
        });
    }

    /**
     * Get all reports from Realtime Database
     * Uses server data only (no cache) to ensure fresh data
     */
    public void getAllReports(ReportListCallback callback) {
        DatabaseReference reportsRef = database.getReference(PATH_REPORTS);

        // Keep reference to ensure we get fresh data
        reportsRef.keepSynced(false); // Don't keep synced to avoid cache

        reportsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

                List<Report> reports = new ArrayList<>();
                if (snapshot.exists()) {
                    for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                        Report report = mapToReport(reportSnapshot);
                        if (report != null) {
                            reports.add(report);
                        }
                    }
                }
                // Sort by createdAt descending
                reports.sort((r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));

                Log.d(TAG, "Loaded " + reports.size() + " reports from Realtime Database");
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSuccess(reports);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error loading reports from Realtime Database", error.toException());
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(error.toException());
                    }
                });
            }
        });
    }

    /**
     * Delete report from Realtime Database
     */
    public void deleteReport(String reportId, ReportCallback callback) {
        // Check if user is authenticated
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "✗ No authenticated user - cannot delete report");
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onFailure(new Exception("User not authenticated. Please login first."));
                }
            });
            return;
        }

        Log.d(TAG, "Deleting report from Realtime Database - ID: " + reportId);
        Log.d(TAG, "User: " + currentUser.getUid());

        DatabaseReference reportRef = database.getReference(PATH_REPORTS).child(reportId);

        reportRef.removeValue()
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "✓ Report successfully deleted from Realtime Database: " + reportId);
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        // Create a dummy report object with just the ID for callback
                        Report deletedReport = new Report();
                        deletedReport.setId(reportId);
                        callback.onSuccess(deletedReport);
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "✗ Error deleting report from Realtime Database", e);
                Log.e(TAG, "Error details: " + e.getMessage());
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                });
            });
    }

    /**
     * Get reports by doctor ID from Realtime Database
     * Uses server data only (no cache) to ensure fresh data
     */
    public void getReportsByDoctorId(String doctorId, ReportListCallback callback) {
        // Check authentication first
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "User is not authenticated - cannot fetch reports");
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onFailure(new Exception("User not authenticated. Please login again."));
                }
            });
            return;
        }

        Log.d(TAG, "Fetching reports for doctor: " + doctorId);
        Log.d(TAG, "Current authenticated user: " + currentUser.getUid() + " (" + currentUser.getEmail() + ")");

        DatabaseReference reportsRef = database.getReference(PATH_REPORTS);

        // Don't keep synced to avoid cache
        reportsRef.keepSynced(false);

        Query query = reportsRef.orderByChild("doctorId").equalTo(doctorId);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

                List<Report> reports = new ArrayList<>();
                if (snapshot.exists()) {
                    for (DataSnapshot reportSnapshot : snapshot.getChildren()) {
                        Report report = mapToReport(reportSnapshot);
                        if (report != null) {
                            reports.add(report);
                        }
                    }
                }
                // Sort by createdAt descending
                reports.sort((r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));

                Log.d(TAG, "Loaded " + reports.size() + " reports for doctor: " + doctorId);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSuccess(reports);
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "✗ Error loading reports for doctor from Realtime Database");
                Log.e(TAG, "DatabaseError code: " + error.getCode());
                Log.e(TAG, "DatabaseError message: " + error.getMessage());
                Log.e(TAG, "DatabaseError details: " + error.getDetails());

                // Check if it's a permission error
                if (error.getCode() == DatabaseError.PERMISSION_DENIED) {
                    Log.e(TAG, "PERMISSION DENIED - Check Firebase database rules!");
                    Log.e(TAG, "Make sure doctors can read reports in database.rules.json");
                }

                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        Exception exception = error.toException();
                        if (exception == null) {
                            exception = new Exception("Database error: " + error.getMessage() + " (Code: " + error.getCode() + ")");
                        }
                        callback.onFailure(exception);
                    }
                });
            }
        });
    }

    // ========================================
    // REPORT JSON CONVERSION METHODS (TEXT STORAGE)
    // ========================================

    /**
     * Convert Report to JSON string for text storage in Realtime Database
     */
    private String reportToJsonString(Report report) {
        try {
            JSONObject json = new JSONObject();
            json.put("id", report.getId() != null ? report.getId() : "");
            json.put("patientId", report.getPatientId() != null ? report.getPatientId() : "");
            json.put("patientName", report.getPatientName() != null ? report.getPatientName() : "");
            json.put("doctorId", report.getDoctorId() != null ? report.getDoctorId() : "");
            json.put("doctorName", report.getDoctorName() != null ? report.getDoctorName() : "");
            json.put("recordingType", report.getRecordingType() != null ? report.getRecordingType() : "");
            json.put("filePath", report.getFilePath() != null ? report.getFilePath() : "");
            json.put("aiPrediction", report.getAiPrediction());
            json.put("aiResult", report.getAiResult() != null ? report.getAiResult() : "");
            json.put("doctorVerification", report.getDoctorVerification() != null ? report.getDoctorVerification() : "");
            json.put("doctorNotes", report.getDoctorNotes() != null ? report.getDoctorNotes() : "");
            json.put("createdAt", report.getCreatedAt());
            json.put("verifiedAt", report.getVerifiedAt());
            json.put("summaryText", report.getSummaryText() != null ? report.getSummaryText() : "");
            json.put("severityText", report.getSeverityText() != null ? report.getSeverityText() : "");
            json.put("diagnosisText", report.getDiagnosisText() != null ? report.getDiagnosisText() : "");
            json.put("adviceText", report.getAdviceText() != null ? report.getAdviceText() : "");
            json.put("submissionId", report.getSubmissionId() != null ? report.getSubmissionId() : "");
            json.put("videoUrl", report.getVideoUrl() != null ? report.getVideoUrl() : "");

            String jsonString = json.toString();
            Log.d(TAG, "Report converted to JSON: " + jsonString.length() + " characters");
            return jsonString;
        } catch (JSONException e) {
            Log.e(TAG, "Error converting report to JSON", e);
            return null;
        }
    }

    /**
     * Convert JSON string back to Report object
     */
    private Report jsonStringToReport(String jsonString) {
        try {
            if (jsonString == null || jsonString.isEmpty()) {
                return null;
            }

            JSONObject json = new JSONObject(jsonString);
            Report report = new Report();

            report.setId(json.optString("id", ""));
            report.setPatientId(json.optString("patientId", ""));
            report.setPatientName(json.optString("patientName", ""));
            report.setDoctorId(json.optString("doctorId", ""));
            report.setDoctorName(json.optString("doctorName", ""));
            report.setRecordingType(json.optString("recordingType", ""));
            report.setFilePath(json.optString("filePath", ""));
            report.setAiPrediction((float) json.optDouble("aiPrediction", 0.0));
            report.setAiResult(json.optString("aiResult", ""));
            report.setDoctorVerification(json.optString("doctorVerification", ""));
            report.setDoctorNotes(json.optString("doctorNotes", ""));
            report.setCreatedAt(json.optLong("createdAt", System.currentTimeMillis()));
            report.setVerifiedAt(json.optLong("verifiedAt", 0));
            report.setSummaryText(json.optString("summaryText", ""));
            report.setSeverityText(json.optString("severityText", ""));
            report.setDiagnosisText(json.optString("diagnosisText", ""));
            report.setAdviceText(json.optString("adviceText", ""));
            report.setSubmissionId(json.optString("submissionId", ""));
            report.setVideoUrl(json.optString("videoUrl", ""));

            return report;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON string to report", e);
            return null;
        }
    }

    // ========================================
    // REPORT MAP CONVERSION METHODS (LEGACY - FOR QUERYING)
    // ========================================

    private Map<String, Object> reportToMap(Report report) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", report.getId());
        map.put("patientId", report.getPatientId() != null ? report.getPatientId() : "");
        map.put("patientName", report.getPatientName() != null ? report.getPatientName() : "");
        map.put("doctorId", report.getDoctorId() != null ? report.getDoctorId() : "");
        map.put("doctorName", report.getDoctorName() != null ? report.getDoctorName() : "");
        map.put("recordingType", report.getRecordingType() != null ? report.getRecordingType() : "");
        map.put("filePath", report.getFilePath() != null ? report.getFilePath() : "");
        map.put("aiPrediction", report.getAiPrediction());
        map.put("aiResult", report.getAiResult() != null ? report.getAiResult() : "");
        map.put("doctorVerification", report.getDoctorVerification() != null ? report.getDoctorVerification() : "");
        map.put("doctorNotes", report.getDoctorNotes() != null ? report.getDoctorNotes() : "");
        map.put("createdAt", report.getCreatedAt());
        map.put("verifiedAt", report.getVerifiedAt());
        map.put("summaryText", report.getSummaryText() != null ? report.getSummaryText() : "");
        map.put("severityText", report.getSeverityText() != null ? report.getSeverityText() : "");
        map.put("diagnosisText", report.getDiagnosisText() != null ? report.getDiagnosisText() : "");
        map.put("adviceText", report.getAdviceText() != null ? report.getAdviceText() : "");
        map.put("submissionId", report.getSubmissionId() != null ? report.getSubmissionId() : "");
        return map;
    }

    private Report mapToReport(DataSnapshot snapshot) {
        try {
            // First, try to read from JSON string (reportData field)
            String reportJson = snapshot.child("reportData").getValue(String.class);
            if (reportJson != null && !reportJson.isEmpty()) {
                Log.d(TAG, "Reading report from JSON string (text format)");
                Report report = jsonStringToReport(reportJson);
                if (report != null) {
                    return report;
                }
                Log.w(TAG, "Failed to parse JSON string, falling back to individual fields");
            }

            // Fallback: Read from individual fields (for backward compatibility)
            Log.d(TAG, "Reading report from individual fields");
            Report report = new Report();

            // Use snapshot key as ID if "id" field is missing
            String reportId = snapshot.getKey();
            String idField = snapshot.child("id").getValue(String.class);
            if (idField != null && !idField.isEmpty()) {
                reportId = idField;
            }
            report.setId(reportId);

            report.setPatientId(snapshot.child("patientId").getValue(String.class));
            report.setPatientName(snapshot.child("patientName").getValue(String.class));
            report.setDoctorId(snapshot.child("doctorId").getValue(String.class));
            report.setDoctorName(snapshot.child("doctorName").getValue(String.class));
            report.setRecordingType(snapshot.child("recordingType").getValue(String.class));
            report.setFilePath(snapshot.child("filePath").getValue(String.class));

            Double aiPrediction = snapshot.child("aiPrediction").getValue(Double.class);
            if (aiPrediction != null) {
                report.setAiPrediction(aiPrediction.floatValue());
            }

            report.setAiResult(snapshot.child("aiResult").getValue(String.class));
            report.setDoctorVerification(snapshot.child("doctorVerification").getValue(String.class));
            report.setDoctorNotes(snapshot.child("doctorNotes").getValue(String.class));

            Long createdAt = snapshot.child("createdAt").getValue(Long.class);
            if (createdAt != null) {
                report.setCreatedAt(createdAt);
            } else {
                report.setCreatedAt(System.currentTimeMillis());
            }

            Long verifiedAt = snapshot.child("verifiedAt").getValue(Long.class);
            if (verifiedAt != null) {
                report.setVerifiedAt(verifiedAt);
            }

            report.setSummaryText(snapshot.child("summaryText").getValue(String.class));
            report.setSeverityText(snapshot.child("severityText").getValue(String.class));
            report.setDiagnosisText(snapshot.child("diagnosisText").getValue(String.class));
            report.setAdviceText(snapshot.child("adviceText").getValue(String.class));
            report.setSubmissionId(snapshot.child("submissionId").getValue(String.class));
            report.setVideoUrl(snapshot.child("videoUrl").getValue(String.class));
            
            // Ensure filePath is read (may be stored as filePath or in reportData JSON)
            String filePath = snapshot.child("filePath").getValue(String.class);
            if (filePath == null || filePath.isEmpty()) {
                // Try reading from reportData JSON if filePath is not directly available
                // Reuse the reportJson variable that was already read at the beginning of the method
                if (reportJson != null && !reportJson.isEmpty()) {
                    Report jsonReport = jsonStringToReport(reportJson);
                    if (jsonReport != null && jsonReport.getFilePath() != null) {
                        filePath = jsonReport.getFilePath();
                    }
                }
            }
            report.setFilePath(filePath);

            return report;
        } catch (Exception e) {
            Log.e(TAG, "Error mapping report from snapshot", e);
            return null;
        }
    }
}

