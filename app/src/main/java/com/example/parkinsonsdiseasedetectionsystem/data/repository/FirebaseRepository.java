package com.example.parkinsonsdiseasedetectionsystem.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.example.parkinsonsdiseasedetectionsystem.models.Submission;
import com.example.parkinsonsdiseasedetectionsystem.models.User;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FirebaseRepository - Handles all Firebase Firestore operations
 * Works alongside LocalRepository for hybrid data management
 */
public class FirebaseRepository {

    private static final String TAG = "FirebaseRepository";
    private static FirebaseRepository instance;
    
    private final FirebaseFirestore firestore;
    private final FirebaseAuth firebaseAuth;
    
    // Collection names
    private static final String COLLECTION_USERS = "users";
    private static final String COLLECTION_REPORTS = "reports";
    private static final String COLLECTION_SUBMISSIONS = "submissions";
    
    private FirebaseRepository() {
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        
        // Enable offline persistence for Firestore (must be set before any operations)
        try {
            com.google.firebase.firestore.FirebaseFirestoreSettings settings = 
                new com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build();
            firestore.setFirestoreSettings(settings);
            Log.d(TAG, "Firestore offline persistence enabled");
        } catch (Exception e) {
            Log.w(TAG, "Failed to enable Firestore offline persistence (may already be enabled): " + e.getMessage());
            // Offline persistence can only be enabled once, so this is expected on subsequent calls
        }
    }
    
    public static synchronized FirebaseRepository getInstance() {
        if (instance == null) {
            instance = new FirebaseRepository();
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
    
    /**
     * Create user in Firebase Auth and Firestore
     */
    public void createUserWithEmailAndPassword(String email, String password, User userData, 
                                               UserCallback callback) {
        firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                // Ensure we're on main thread
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                
                if (task.isSuccessful()) {
                    FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                    if (firebaseUser != null) {
                        // Use Firebase UID as user ID
                        userData.setId(firebaseUser.getUid());
                        // Save user data to Firestore
                        saveUserToFirestore(userData, callback);
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
                            callback.onFailure(exception != null ? exception : new Exception("Signup failed"));
                        }
                    });
                }
            });
    }
    
    /**
     * Sign in with email and password
     * Uses Firebase Auth which works across devices
     */
    public void signInWithEmailAndPassword(String email, String password, 
                                           UserCallback callback) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                if (task.isSuccessful()) {
                    FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                    if (firebaseUser != null) {
                        Log.d(TAG, "Firebase Auth signin successful, fetching user data from Firestore");
                        // Fetch user data from Firestore (will try server first, then cache)
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
                            callback.onFailure(exception != null ? exception : new Exception("Login failed"));
                        }
                    });
                }
            });
    }
    
    /**
     * Save user to Firestore
     */
    public void saveUserToFirestore(User user, UserCallback callback) {
        Map<String, Object> userMap = userToMap(user);
        
        firestore.collection(COLLECTION_USERS)
            .document(user.getId())
            .set(userMap)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "User saved to Firestore: " + user.getId());
                // Ensure callback runs on main thread
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSuccess(user);
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving user to Firestore", e);
                // Ensure callback runs on main thread
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                });
            });
    }
    
    /**
     * Get user by ID from Firestore
     * CRITICAL: Always tries server first for cross-device sync, then falls back to cache
     * This ensures user data (including role) is always fresh from Firebase
     */
    public void getUserById(String userId, UserCallback callback) {
        Log.d(TAG, "Fetching user from Firestore: " + userId);
        
        // CRITICAL FIX: Try server first to ensure we get fresh data from Firebase
        // This is essential for multi-device login - we need the role from Firebase, not cache
        firestore.collection(COLLECTION_USERS)
            .document(userId)
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnCompleteListener(task -> {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document != null && document.exists()) {
                        User user = mapToUser(document);
                        Log.d(TAG, "User fetched from Firestore server. Role: " + user.getRole());
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onSuccess(user);
                            }
                        });
                    } else {
                        // Document doesn't exist in server - try cache as fallback
                        Log.w(TAG, "User document not found on server, trying cache: " + userId);
                        getUserByIdFromCache(userId, callback);
                    }
                } else {
                    // Server failed - try cache explicitly for offline support
                    Exception exception = task.getException();
                    Log.w(TAG, "Failed to get user from server, trying cache", exception);
                    getUserByIdFromCache(userId, callback);
                }
            });
    }
    
    /**
     * Get user by ID from cache (offline support)
     */
    private void getUserByIdFromCache(String userId, UserCallback callback) {
        firestore.collection(COLLECTION_USERS)
            .document(userId)
            .get(com.google.firebase.firestore.Source.CACHE)
            .addOnCompleteListener(task -> {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document != null && document.exists()) {
                        User user = mapToUser(document);
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onSuccess(user);
                            }
                        });
                    } else {
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onFailure(new Exception("User not found in cache or server"));
                            }
                        });
                    }
                } else {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onFailure(task.getException());
                        }
                    });
                }
            });
    }
    
    /**
     * Get user by email from Firestore
     */
    public void getUserByEmail(String email, UserCallback callback) {
        firestore.collection(COLLECTION_USERS)
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    QuerySnapshot querySnapshot = task.getResult();
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        DocumentSnapshot document = querySnapshot.getDocuments().get(0);
                        User user = mapToUser(document);
                        if (callback != null) {
                            callback.onSuccess(user);
                        }
                    } else {
                        if (callback != null) {
                            callback.onFailure(new Exception("User not found"));
                        }
                    }
                } else {
                    if (callback != null) {
                        callback.onFailure(task.getException());
                    }
                }
            });
    }
    
    /**
     * Get all users from Firestore
     */
    public void getAllUsers(UserListCallback callback) {
        firestore.collection(COLLECTION_USERS)
            .get()
            .addOnCompleteListener(task -> {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                if (task.isSuccessful()) {
                    List<User> users = new ArrayList<>();
                    QuerySnapshot querySnapshot = task.getResult();
                    if (querySnapshot != null) {
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            User user = mapToUser(document);
                            users.add(user);
                        }
                    }
                    Log.d(TAG, "Loaded " + users.size() + " users from Firestore");
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onSuccess(users);
                        }
                    });
                } else {
                    Exception exception = task.getException();
                    Log.e(TAG, "Error loading users from Firestore", exception);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onFailure(exception != null ? exception : new Exception("Failed to load users"));
                        }
                    });
                }
            });
    }
    
    /**
     * Get users by role
     */
    public void getUsersByRole(String role, UserListCallback callback) {
        firestore.collection(COLLECTION_USERS)
            .whereEqualTo("role", role)
            .get()
            .addOnCompleteListener(task -> {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                if (task.isSuccessful()) {
                    List<User> users = new ArrayList<>();
                    QuerySnapshot querySnapshot = task.getResult();
                    if (querySnapshot != null) {
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            User user = mapToUser(document);
                            users.add(user);
                        }
                    }
                    Log.d(TAG, "Loaded " + users.size() + " users with role: " + role);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onSuccess(users);
                        }
                    });
                } else {
                    Exception exception = task.getException();
                    Log.e(TAG, "Error loading users by role from Firestore", exception);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onFailure(exception != null ? exception : new Exception("Failed to load users"));
                        }
                    });
                }
            });
    }
    
    /**
     * Update user in Firestore
     */
    public void updateUser(User user, UserCallback callback) {
        Map<String, Object> userMap = userToMap(user);
        
        firestore.collection(COLLECTION_USERS)
            .document(user.getId())
            .set(userMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "User updated in Firestore: " + user.getId());
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSuccess(user);
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error updating user in Firestore", e);
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                });
            });
    }
    
    /**
     * Block/Unblock user
     */
    /**
     * Delete user from Firestore and Firebase Auth
     */
    public void deleteUser(String userId, String email, UserCallback callback) {
        // First delete from Firestore
        firestore.collection(COLLECTION_USERS)
            .document(userId)
            .delete()
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "User deleted from Firestore: " + userId);
                
                // Try to delete from Firebase Auth (requires admin privileges or user's own account)
                // For admin deletion, we'll just delete from Firestore
                // Firebase Auth deletion requires Admin SDK or user's own account
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
                Log.e(TAG, "Error deleting user from Firestore", e);
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                });
            });
    }
    
    public void updateUserBlockStatus(String userId, boolean isBlocked, UserCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("blocked", isBlocked);
        
        firestore.collection(COLLECTION_USERS)
            .document(userId)
            .update(updates)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "User block status updated: " + userId);
                getUserById(userId, callback);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error updating user block status", e);
                if (callback != null) {
                    callback.onFailure(e);
                }
            });
    }
    
    // ========================================
    // REPORT OPERATIONS
    // ========================================
    
    public interface ReportCallback {
        void onSuccess(Report report);
        void onFailure(Exception e);
    }
    
    public interface ReportListCallback {
        void onSuccess(List<Report> reports);
        void onFailure(Exception e);
    }
    
    /**
     * Save report to Firestore
     */
    public void saveReport(Report report, ReportCallback callback) {
        Map<String, Object> reportMap = reportToMap(report);
        
        firestore.collection(COLLECTION_REPORTS)
            .document(report.getId())
            .set(reportMap)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Report saved to Firestore: " + report.getId());
                if (callback != null) {
                    callback.onSuccess(report);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving report to Firestore", e);
                if (callback != null) {
                    callback.onFailure(e);
                }
            });
    }
    
    /**
     * Get report by ID
     */
    public void getReportById(String reportId, ReportCallback callback) {
        firestore.collection(COLLECTION_REPORTS)
            .document(reportId)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document != null && document.exists()) {
                        Report report = mapToReport(document);
                        if (callback != null) {
                            callback.onSuccess(report);
                        }
                    } else {
                        if (callback != null) {
                            callback.onFailure(new Exception("Report not found"));
                        }
                    }
                } else {
                    if (callback != null) {
                        callback.onFailure(task.getException());
                    }
                }
            });
    }
    
    /**
     * Get all reports
     */
    public void getAllReports(ReportListCallback callback) {
        firestore.collection(COLLECTION_REPORTS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnCompleteListener(task -> {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                if (task.isSuccessful()) {
                    List<Report> reports = new ArrayList<>();
                    QuerySnapshot querySnapshot = task.getResult();
                    if (querySnapshot != null) {
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            Report report = mapToReport(document);
                            reports.add(report);
                        }
                    }
                    Log.d(TAG, "Loaded " + reports.size() + " reports from Firestore");
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onSuccess(reports);
                        }
                    });
                } else {
                    Exception exception = task.getException();
                    Log.e(TAG, "Error loading reports from Firestore", exception);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onFailure(exception != null ? exception : new Exception("Failed to load reports"));
                        }
                    });
                }
            });
    }
    
    /**
     * Get reports by patient ID
     */
    public void getReportsByPatientId(String patientId, ReportListCallback callback) {
        firestore.collection(COLLECTION_REPORTS)
            .whereEqualTo("patientId", patientId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnCompleteListener(task -> {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                if (task.isSuccessful()) {
                    List<Report> reports = new ArrayList<>();
                    QuerySnapshot querySnapshot = task.getResult();
                    if (querySnapshot != null) {
                        for (QueryDocumentSnapshot document : querySnapshot) {
                            Report report = mapToReport(document);
                            reports.add(report);
                        }
                    }
                    Log.d(TAG, "Loaded " + reports.size() + " reports for patient: " + patientId);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onSuccess(reports);
                        }
                    });
                } else {
                    Exception exception = task.getException();
                    Log.e(TAG, "Error loading reports by patient ID from Firestore", exception);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onFailure(exception != null ? exception : new Exception("Failed to load reports"));
                        }
                    });
                }
            });
    }
    
    /**
     * Get reports by doctor ID
     */
    public void getReportsByDoctorId(String doctorId, ReportListCallback callback) {
        firestore.collection(COLLECTION_REPORTS)
            .whereEqualTo("doctorId", doctorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    List<Report> reports = new ArrayList<>();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Report report = mapToReport(document);
                        reports.add(report);
                    }
                    if (callback != null) {
                        callback.onSuccess(reports);
                    }
                } else {
                    if (callback != null) {
                        callback.onFailure(task.getException());
                    }
                }
            });
    }
    
    /**
     * Update report in Firestore
     * Uses set with merge to create if doesn't exist, update if exists
     */
    public void updateReport(Report report, ReportCallback callback) {
        Map<String, Object> reportMap = reportToMap(report);
        
        firestore.collection(COLLECTION_REPORTS)
            .document(report.getId())
            .set(reportMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Report saved/updated in Firestore: " + report.getId());
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onSuccess(report);
                    }
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving/updating report in Firestore", e);
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure(e);
                    }
                });
            });
    }
    
    // ========================================
    // SUBMISSION OPERATIONS
    // ========================================
    
    public interface SubmissionCallback {
        void onSuccess(Submission submission);
        void onFailure(Exception e);
    }
    
    public interface SubmissionListCallback {
        void onSuccess(List<Submission> submissions);
        void onFailure(Exception e);
    }
    
    /**
     * Save submission to Firestore
     */
    public void saveSubmission(Submission submission, SubmissionCallback callback) {
        Map<String, Object> submissionMap = submissionToMap(submission);
        
        firestore.collection(COLLECTION_SUBMISSIONS)
            .document(submission.getSubmissionId())
            .set(submissionMap)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Submission saved to Firestore: " + submission.getSubmissionId());
                if (callback != null) {
                    callback.onSuccess(submission);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error saving submission to Firestore", e);
                if (callback != null) {
                    callback.onFailure(e);
                }
            });
    }
    
    /**
     * Get submission by ID
     */
    public void getSubmissionById(String submissionId, SubmissionCallback callback) {
        firestore.collection(COLLECTION_SUBMISSIONS)
            .document(submissionId)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document != null && document.exists()) {
                        Submission submission = mapToSubmission(document);
                        if (callback != null) {
                            callback.onSuccess(submission);
                        }
                    } else {
                        if (callback != null) {
                            callback.onFailure(new Exception("Submission not found"));
                        }
                    }
                } else {
                    if (callback != null) {
                        callback.onFailure(task.getException());
                    }
                }
            });
    }
    
    /**
     * Get pending submissions for a doctor
     */
    public void getPendingSubmissionsForDoctor(String doctorId, SubmissionListCallback callback) {
        firestore.collection(COLLECTION_SUBMISSIONS)
            .whereEqualTo("assignedDoctorId", doctorId)
            .whereEqualTo("status", "pending")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    List<Submission> submissions = new ArrayList<>();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Submission submission = mapToSubmission(document);
                        submissions.add(submission);
                    }
                    if (callback != null) {
                        callback.onSuccess(submissions);
                    }
                } else {
                    if (callback != null) {
                        callback.onFailure(task.getException());
                    }
                }
            });
    }
    
    /**
     * Get submissions by user ID
     */
    public void getSubmissionsByUserId(String userId, SubmissionListCallback callback) {
        firestore.collection(COLLECTION_SUBMISSIONS)
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    List<Submission> submissions = new ArrayList<>();
                    for (QueryDocumentSnapshot document : task.getResult()) {
                        Submission submission = mapToSubmission(document);
                        submissions.add(submission);
                    }
                    if (callback != null) {
                        callback.onSuccess(submissions);
                    }
                } else {
                    if (callback != null) {
                        callback.onFailure(task.getException());
                    }
                }
            });
    }
    
    /**
     * Update submission status
     */
    public void updateSubmissionStatus(String submissionId, String status, SubmissionCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        
        firestore.collection(COLLECTION_SUBMISSIONS)
            .document(submissionId)
            .update(updates)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Submission status updated: " + submissionId);
                getSubmissionById(submissionId, callback);
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Error updating submission status", e);
                if (callback != null) {
                    callback.onFailure(e);
                }
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
        // Note: Password is NOT stored in Firestore for security
        return map;
    }
    
    private User mapToUser(DocumentSnapshot document) {
        User user = new User();
        // Use document ID as primary source, fallback to "id" field
        String userId = document.getId();
        String idField = document.getString("id");
        if (idField != null && !idField.isEmpty()) {
            userId = idField;
        }
        user.setId(userId);
        user.setName(document.getString("name"));
        user.setEmail(document.getString("email"));
        user.setPhone(document.getString("phone"));
        // CRITICAL: Role must be extracted from Firestore document
        String role = document.getString("role");
        user.setRole(role != null ? role : "patient"); // Default to patient if role is missing
        Boolean blocked = document.getBoolean("blocked");
        user.setBlocked(blocked != null && blocked);
        Long createdAt = document.getLong("createdAt");
        if (createdAt != null) {
            user.setCreatedAt(createdAt);
        } else {
            // If createdAt is missing, use current time
            user.setCreatedAt(System.currentTimeMillis());
        }
        return user;
    }
    
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
    
    private Report mapToReport(DocumentSnapshot document) {
        Report report = new Report();
        report.setId(document.getString("id"));
        report.setPatientId(document.getString("patientId"));
        report.setPatientName(document.getString("patientName"));
        report.setDoctorId(document.getString("doctorId"));
        report.setDoctorName(document.getString("doctorName"));
        report.setRecordingType(document.getString("recordingType"));
        report.setFilePath(document.getString("filePath"));
        Double aiPrediction = document.getDouble("aiPrediction");
        if (aiPrediction != null) {
            report.setAiPrediction(aiPrediction.floatValue());
        }
        report.setAiResult(document.getString("aiResult"));
        report.setDoctorVerification(document.getString("doctorVerification"));
        report.setDoctorNotes(document.getString("doctorNotes"));
        Long createdAt = document.getLong("createdAt");
        if (createdAt != null) {
            report.setCreatedAt(createdAt);
        }
        Long verifiedAt = document.getLong("verifiedAt");
        if (verifiedAt != null) {
            report.setVerifiedAt(verifiedAt);
        }
        report.setSummaryText(document.getString("summaryText"));
        report.setSeverityText(document.getString("severityText"));
        report.setDiagnosisText(document.getString("diagnosisText"));
        report.setAdviceText(document.getString("adviceText"));
        report.setSubmissionId(document.getString("submissionId"));
        return report;
    }
    
    private Map<String, Object> submissionToMap(Submission submission) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", submission.getSubmissionId());
        map.put("userId", submission.getUserId() != null ? submission.getUserId() : "");
        map.put("userName", submission.getUserName() != null ? submission.getUserName() : "");
        map.put("recordingType", submission.getRecordingType() != null ? submission.getRecordingType() : "");
        map.put("filePath", submission.getFilePath() != null ? submission.getFilePath() : "");
        map.put("assignedDoctorId", submission.getAssignedDoctorId() != null ? submission.getAssignedDoctorId() : "");
        map.put("status", submission.getStatus() != null ? submission.getStatus() : "pending");
        map.put("createdAt", submission.getCreatedAt());
        map.put("aiPrediction", submission.getAiPrediction());
        map.put("aiResult", submission.getAiResult() != null ? submission.getAiResult() : "");
        return map;
    }
    
    private Submission mapToSubmission(DocumentSnapshot document) {
        Submission submission = new Submission();
        String id = document.getString("id");
        if (id != null) {
            submission.setSubmissionId(id);
        }
        submission.setUserId(document.getString("userId"));
        submission.setUserName(document.getString("userName"));
        submission.setRecordingType(document.getString("recordingType"));
        submission.setFilePath(document.getString("filePath"));
        submission.setAssignedDoctorId(document.getString("assignedDoctorId"));
        submission.setStatus(document.getString("status"));
        Long createdAt = document.getLong("createdAt");
        if (createdAt != null) {
            submission.setCreatedAt(createdAt);
        }
        Double aiPrediction = document.getDouble("aiPrediction");
        if (aiPrediction != null) {
            submission.setAiPrediction(aiPrediction.floatValue());
        }
        submission.setAiResult(document.getString("aiResult"));
        return submission;
    }
}

