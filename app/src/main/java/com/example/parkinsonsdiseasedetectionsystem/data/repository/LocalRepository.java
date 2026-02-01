package com.example.parkinsonsdiseasedetectionsystem.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.parkinsonsdiseasedetectionsystem.data.local.ParkiDatabase;
import com.example.parkinsonsdiseasedetectionsystem.data.local.dao.ReportDao;
import com.example.parkinsonsdiseasedetectionsystem.data.local.dao.SubmissionDao;
import com.example.parkinsonsdiseasedetectionsystem.data.local.dao.UserDao;
import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.example.parkinsonsdiseasedetectionsystem.models.Submission;
import com.example.parkinsonsdiseasedetectionsystem.models.User;
import com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalRepository {

    private static LocalRepository instance;

    private final UserDao userDao;
    private final SubmissionDao submissionDao;
    private final ReportDao reportDao;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final FirebaseRepository firebaseRepo = FirebaseRepository.getInstance();
    private MutableLiveData<List<Report>> cloudReportsLive;
    private MutableLiveData<List<User>> cloudPatientsLive;

    private LocalRepository(Context context) {
        try {
            ParkiDatabase database = ParkiDatabase.getInstance(context);
            userDao = database.userDao();
            submissionDao = database.submissionDao();
            reportDao = database.reportDao();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize database: " + e.getMessage(), e);
        }
    }

    public static synchronized LocalRepository getInstance(Context context) {
        if (instance == null) {
            instance = new LocalRepository(context.getApplicationContext());
            // Ensure default admin account exists
            instance.ensureDefaultAdmin();
        }
        return instance;
    }

    /**
     * Ensure default admin account exists in database
     */
    private void ensureDefaultAdmin() {
        ioExecutor.execute(() -> {
            try {
                User admin = userDao.getUserByEmail("admin@gmail.com");
                if (admin == null) {
                    // Create default admin account
                    User defaultAdmin = new User(
                            UUID.randomUUID().toString(),
                            "Admin",
                            "admin@gmail.com",
                            "",
                            "admin"
                    );
                    defaultAdmin.setPassword("123456");
                    defaultAdmin.setBlocked(false);
                    defaultAdmin.setCreatedAt(System.currentTimeMillis());
                    userDao.insertUser(defaultAdmin);
                    android.util.Log.d("LocalRepository", "Default admin account created in Room database");

                    // Also try to create in Firebase (non-blocking)
                    try {
                        FirebaseRepository firebaseRepo = FirebaseRepository.getInstance();
                        // Try to create admin in Firebase Auth (if not exists)
                        firebaseRepo.getUserByEmail("admin@gmail.com", new FirebaseRepository.UserCallback() {
                            @Override
                            public void onSuccess(User user) {
                                android.util.Log.d("LocalRepository", "Admin already exists in Firebase");
                            }

                            @Override
                            public void onFailure(Exception e) {
                                // Admin doesn't exist in Firebase, but that's okay
                                // Admin can login using Room database fallback
                                android.util.Log.d("LocalRepository", "Admin will use Room database login");
                            }
                        });
                    } catch (Exception e) {
                        android.util.Log.w("LocalRepository", "Firebase not available for admin creation", e);
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("LocalRepository", "Error ensuring default admin", e);
            }
        });
    }

    public void ensureUserRecord(Context context) {
        String userId = AuthUtils.getUserId(context);
        if (TextUtils.isEmpty(userId)) return;

        ioExecutor.execute(() -> {
            User existing = userDao.getUserById(userId);
            if (existing == null) {
                User user = new User(
                        userId,
                        AuthUtils.getUserName(context),
                        AuthUtils.getUserEmail(context),
                        "",
                        AuthUtils.getUserRole(context)
                );
                userDao.insertUser(user);
            }
        });
    }

    public void insertUser(User user) {
        ioExecutor.execute(() -> {
            userDao.insertUser(user);
            // Sync with Firebase in background
            syncUserToFirebase(user);
        });
    }

    /**
     * Sync user to Firebase (background operation)
     */
    private void syncUserToFirebase(User user) {
        firebaseRepo.saveUserToFirestore(user, new FirebaseRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                android.util.Log.d("LocalRepository", "User synced to Firebase: " + user.getId());
            }

            @Override
            public void onFailure(Exception e) {
                android.util.Log.e("LocalRepository", "Failed to sync user to Firebase", e);
            }
        });
    }

    public void insertUserSync(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (user.getId() == null || user.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("User email cannot be null or empty");
        }

        // Check if we're on the main thread - if so, use executor, otherwise call directly
        boolean isMainThread = android.os.Looper.getMainLooper().getThread() == Thread.currentThread();

        if (isMainThread) {
            // On main thread - use executor
            try {
                android.util.Log.d("LocalRepository", "Inserting user on main thread, using executor");
                ioExecutor.submit(() -> {
                    try {
                        userDao.insertUser(user);
                    } catch (Exception e) {
                        android.util.Log.e("LocalRepository", "Database insert error", e);
                        throw new RuntimeException("Database insert error: " + e.getMessage(), e);
                    }
                }).get();
            } catch (Exception e) {
                android.util.Log.e("LocalRepository", "Failed to insert user", e);
                Throwable cause = e.getCause();
                if (cause != null && cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new RuntimeException("Failed to insert user: " + e.getMessage(), e);
            }
        } else {
            // Already on background thread - call directly
            try {
                android.util.Log.d("LocalRepository", "Inserting user on background thread, calling directly");
                userDao.insertUser(user);
                android.util.Log.d("LocalRepository", "User inserted successfully");
            } catch (android.database.sqlite.SQLiteConstraintException e) {
                android.util.Log.e("LocalRepository", "SQLite constraint error", e);
                throw new RuntimeException("Email already exists: " + e.getMessage(), e);
            } catch (Exception e) {
                android.util.Log.e("LocalRepository", "Database insert error", e);
                throw new RuntimeException("Database insert error: " + e.getMessage(), e);
            }
        }
    }

    public void updateUserProfile(User user) {
        ioExecutor.execute(() -> userDao.updateUser(user));
    }

    public void updateUserPassword(String userId, String newPassword) {
        ioExecutor.execute(() -> {
            User user = userDao.getUserById(userId);
            if (user != null) {
                user.setPassword(newPassword);
                userDao.updateUser(user);
            }
        });
    }

    public User getUserByEmailSync(String email) {
        try {
            if (email == null || email.trim().isEmpty()) {
                return null;
            }
            return ioExecutor.submit(() -> {
                try {
                    return userDao.getUserByEmail(email.trim());
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }).get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public User getUserByIdSync(String userId) {
        try {
            return ioExecutor.submit(() -> userDao.getUserById(userId)).get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public LiveData<List<Report>> observeReportsForUser(String userId) {
        // Pull latest from Firebase so doctors/patients stay in sync across devices
        syncReportsFromFirebaseForUser(userId);
        return reportDao.observeReportsForUser(userId);
    }

    public LiveData<List<Report>> observeAllReports() {
        // Keep doctor/admin dashboards updated with cloud data
        syncAllReportsFromFirebase();
        return reportDao.observeAllReports();
    }

    public LiveData<List<Report>> observeAllReportsCloud() {
        if (cloudReportsLive == null) {
            // TODO: Implement LiveData listener - FirebaseRepository doesn't have listenAllReports()
            // For now, create empty LiveData
            cloudReportsLive = new androidx.lifecycle.MutableLiveData<List<Report>>();
            cloudReportsLive.setValue(new java.util.ArrayList<>());
            if (cloudReportsLive != null) {
                cloudReportsLive.observeForever(reports ->
                        ioExecutor.execute(() -> {
                            if (reports != null) {
                                for (Report r : reports) {
                                    reportDao.insertReport(r);
                                }
                            }
                        }));
            }
        }
        return cloudReportsLive != null ? cloudReportsLive : reportDao.observeAllReports();
    }

    private void syncReportsFromFirebaseForUser(String userId) {
        if (TextUtils.isEmpty(userId)) return;
        firebaseRepo.getReportsByPatientId(userId, new FirebaseRepository.ReportListCallback() {
            @Override
            public void onSuccess(List<Report> reports) {
                ioExecutor.execute(() -> {
                    for (Report r : reports) {
                        reportDao.insertReport(r);
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                android.util.Log.w("LocalRepository", "Failed to sync user reports from Firebase", e);
            }
        });
    }

    private void syncAllReportsFromFirebase() {
        firebaseRepo.getAllReports(new FirebaseRepository.ReportListCallback() {
            @Override
            public void onSuccess(List<Report> reports) {
                ioExecutor.execute(() -> {
                    for (Report r : reports) {
                        reportDao.insertReport(r);
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                android.util.Log.w("LocalRepository", "Failed to sync all reports from Firebase", e);
            }
        });
    }

    public LiveData<List<User>> observePatients() {
        return userDao.observePatients();
    }

    public LiveData<List<User>> observePatientsCloud() {
        if (cloudPatientsLive == null) {
            // TODO: Implement LiveData listener - FirebaseRepository doesn't have listenPatients()
            // For now, create empty LiveData
            cloudPatientsLive = new androidx.lifecycle.MutableLiveData<List<User>>();
            cloudPatientsLive.setValue(new java.util.ArrayList<>());
            if (cloudPatientsLive != null) {
                cloudPatientsLive.observeForever(patients ->
                        ioExecutor.execute(() -> {
                            if (patients != null) {
                                for (User u : patients) {
                                    userDao.insertUser(u);
                                }
                            }
                        }));
            }
        }
        return cloudPatientsLive != null ? cloudPatientsLive : userDao.observePatients();
    }

    public interface RepositoryCallback<T> {
        void onComplete(T data);
    }

    public void getLatestReport(String userId, RepositoryCallback<Report> callback) {
        ioExecutor.execute(() -> {
            Report report = reportDao.getLatestReport(userId);
            if (callback != null) {
                mainHandler.post(() -> callback.onComplete(report));
            }
        });
    }

    public void getReportsForUser(String userId, RepositoryCallback<List<Report>> callback) {
        ioExecutor.execute(() -> {
            List<Report> reports = reportDao.getReportsForUser(userId);
            if (callback != null) {
                mainHandler.post(() -> callback.onComplete(reports));
            }
        });
    }

    public void getReportById(String reportId, RepositoryCallback<Report> callback) {
        ioExecutor.execute(() -> {
            Report report = reportDao.getReportById(reportId);
            if (callback != null) {
                mainHandler.post(() -> callback.onComplete(report));
            }
        });
    }

    public Report getReportBySubmissionIdSync(String submissionId) {
        try {
            return ioExecutor.submit(() -> reportDao.getReportBySubmissionId(submissionId)).get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Report> getReportsForUserSync(String userId) {
        try {
            return ioExecutor.submit(() -> reportDao.getReportsForUser(userId)).get();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public List<Report> getAllReportsSync() {
        try {
            return ioExecutor.submit(() -> {
                // Use the observeAllReports LiveData query result
                // Since we need sync, we'll query directly
                // We need to add a sync method to ReportDao
                return reportDao.getAllReportsSync();
            }).get();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void deleteReport(String reportId, Runnable onComplete) {
        ioExecutor.execute(() -> {
            reportDao.deleteReportById(reportId);
            if (onComplete != null) {
                mainHandler.post(onComplete);
            }
        });
    }

    public void clearReportsForUser(String userId, Runnable onComplete) {
        ioExecutor.execute(() -> {
            reportDao.deleteReportsForUser(userId);
            if (onComplete != null) {
                mainHandler.post(onComplete);
            }
        });
    }

    public void insertReport(Report report) {
        ioExecutor.execute(() -> {
            reportDao.insertReport(report);
        });
        // Sync with Firebase in background
        syncReportToFirebase(report);
    }

    /**
     * Sync report to Firebase (background operation)
     */
    private void syncReportToFirebase(Report report) {
        firebaseRepo.saveReport(report, new FirebaseRepository.ReportCallback() {
            @Override
            public void onSuccess(Report report) {
                android.util.Log.d("LocalRepository", "Report synced to Firebase: " + report.getId());
            }

            @Override
            public void onFailure(Exception e) {
                android.util.Log.e("LocalRepository", "Failed to sync report to Firebase", e);
            }
        });
    }

    public void saveSubmissionAndReport(Submission submission, Report report, Runnable onComplete) {
        ioExecutor.execute(() -> {
            submissionDao.insertSubmission(submission);
            reportDao.insertReport(report);
            if (onComplete != null) {
                mainHandler.post(onComplete);
            }
        });
        // Push to Firebase so doctor/admin can see the report
        syncSubmissionToFirebase(submission);
        syncReportToFirebase(report);
    }

    public Submission createSubmission(String userId, String summary, String sendTo, String status) {
        return new Submission(
                UUID.randomUUID().toString(),
                userId,
                null,
                summary,
                sendTo,
                status,
                System.currentTimeMillis()
        );
    }

    public void seedDemoReportsIfEmpty(String userId, String userName) {
        if (TextUtils.isEmpty(userId)) return;
        ioExecutor.execute(() -> {
            List<Report> existing = reportDao.getReportsForUser(userId);
            if (existing != null && !existing.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            Report low = new Report(UUID.randomUUID().toString(), userId, userName, "voice", "", 0.22f);
            low.setSeverityText("Low");
            low.setSummaryText("Baseline voice sample recorded.");
            low.setDiagnosisText("Healthy speech characteristics.");
            low.setAdviceText("Continue regular monitoring.");
            low.setDoctorVerification("Reviewed");
            low.setCreatedAt(now - 6 * 60 * 60 * 1000);

            Report moderate = new Report(UUID.randomUUID().toString(), userId, userName, "video", "", 0.58f);
            moderate.setSeverityText("Moderate");
            moderate.setSummaryText("Mild tremor noted during self-test.");
            moderate.setDiagnosisText("Possible mild Parkinson symptoms.");
            moderate.setAdviceText("Consult neurologist if symptoms persist.");
            moderate.setDoctorVerification("Pending");
            moderate.setCreatedAt(now - 24 * 60 * 60 * 1000);

            Report high = new Report(UUID.randomUUID().toString(), userId, userName, "voice", "", 0.78f);
            high.setSeverityText("High");
            high.setSummaryText("Stiffness and slower speech reported.");
            high.setDiagnosisText("Parkinson-like symptoms detected.");
            high.setAdviceText("Schedule check-up within a week.");
            high.setDoctorVerification("Doctor Verified");
            high.setCreatedAt(now - 4 * 24 * 60 * 60 * 1000);

            reportDao.insertReport(low);
            reportDao.insertReport(moderate);
            reportDao.insertReport(high);
        });
    }

    public void seedDoctorDemoData() {
        ioExecutor.execute(() -> {
            if (reportDao.getReportsCount() >= 5) {
                return;
            }

            if (userDao.getPatientCount() < 3) {
                userDao.insertUser(new User("P001", "Alice Johnson", "alice@example.com", "03001234567", "patient"));
                userDao.insertUser(new User("P002", "Mark Lee", "mark@example.com", "03011234567", "patient"));
                userDao.insertUser(new User("P003", "Sarah Khan", "sarah@example.com", "03121234567", "patient"));
            }

            long now = System.currentTimeMillis();
            insertDemoReport("P001", "Alice Johnson", "voice", 0.62f, "Pending", now - 2 * 60 * 60 * 1000);
            insertDemoReport("P002", "Mark Lee", "video", 0.28f, "Doctor Verified", now - 5 * 60 * 60 * 1000);
            insertDemoReport("P003", "Sarah Khan", "voice", 0.81f, "Pending", now - 26 * 60 * 60 * 1000);
        });
    }

    private void insertDemoReport(String patientId, String patientName, String type,
                                  float aiScore, String status, long createdAt) {
        Report report = new Report(UUID.randomUUID().toString(), patientId, patientName, type, "", aiScore);
        report.setDoctorVerification(status);
        report.setCreatedAt(createdAt);
        if ("Doctor Verified".equalsIgnoreCase(status)) {
            report.setDoctorName("Dr. Demo");
            report.setDoctorId("DOC_DEMO");
            report.setVerifiedAt(createdAt + 30 * 60 * 1000);
            report.setDiagnosisText("Symptoms reviewed, continue monitoring.");
            report.setAdviceText("Follow-up in two weeks.");
            report.setSeverityText(aiScore > 0.7f ? "High" : "Moderate");
        } else {
            report.setSeverityText(aiScore > 0.7f ? "High" : "Moderate");
            report.setDiagnosisText("Awaiting doctor review");
        }
        reportDao.insertReport(report);
    }

    public void updateReportByDoctor(Report updatedReport, Runnable onComplete) {
        ioExecutor.execute(() -> {
            reportDao.insertReport(updatedReport);
            if (onComplete != null) {
                mainHandler.post(onComplete);
            }
        });
        // Propagate doctor review to Firebase
        syncReportToFirebase(updatedReport);
    }

    // ========================================
    // SUBMISSION OPERATIONS
    // ========================================

    public void insertSubmission(Submission submission) {
        ioExecutor.execute(() -> {
            submissionDao.insertSubmission(submission);
        });
        // Sync with Firebase in background
        syncSubmissionToFirebase(submission);
    }

    /**
     * Sync submission to Firebase (background operation)
     */
    private void syncSubmissionToFirebase(Submission submission) {
        firebaseRepo.saveSubmission(submission, new FirebaseRepository.SubmissionCallback() {
            @Override
            public void onSuccess(Submission submission) {
                android.util.Log.d("LocalRepository", "Submission synced to Firebase: " + submission.getSubmissionId());
            }

            @Override
            public void onFailure(Exception e) {
                android.util.Log.e("LocalRepository", "Failed to sync submission to Firebase", e);
            }
        });
    }

    public void updateSubmission(Submission submission) {
        ioExecutor.execute(() -> submissionDao.updateSubmission(submission));
    }

    public LiveData<List<Submission>> getPendingDoctorSubmissions() {
        return submissionDao.getPendingDoctorSubmissions();
    }

    public LiveData<List<Submission>> getPendingSubmissionsForDoctor(String doctorId) {
        return submissionDao.getPendingSubmissionsForDoctor(doctorId);
    }

    public LiveData<List<Submission>> getAllSubmissionsForDoctor(String doctorId) {
        return submissionDao.getAllSubmissionsForDoctor(doctorId);
    }

    public LiveData<List<Submission>> getSubmissionsForUser(String userId) {
        return submissionDao.getSubmissionsForUser(userId);
    }

    public Submission getSubmissionByIdSync(String submissionId) {
        try {
            return ioExecutor.submit(() -> submissionDao.getSubmissionById(submissionId)).get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void assignSubmissionToDoctor(String submissionId, String doctorId, Runnable onComplete) {
        ioExecutor.execute(() -> {
            Submission submission = submissionDao.getSubmissionById(submissionId);
            if (submission != null) {
                submission.setDoctorId(doctorId);
                submissionDao.updateSubmission(submission);
            }
            if (onComplete != null) {
                mainHandler.post(onComplete);
            }
        });
    }

    public User assignSubmissionToAvailableDoctor(String submissionId) {
        try {
            return ioExecutor.submit(() -> {
                User doctor = userDao.getFirstAvailableDoctor();
                if (doctor != null) {
                    Submission submission = submissionDao.getSubmissionById(submissionId);
                    if (submission != null) {
                        submission.setDoctorId(doctor.getId());
                        submissionDao.updateSubmission(submission);
                    }
                }
                return doctor;
            }).get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void createSubmissionWithDoctorAssignment(String userId, String summaryText, String sendTo, Runnable onComplete) {
        ioExecutor.execute(() -> {
            String submissionId = UUID.randomUUID().toString();
            Submission submission = new Submission(
                    submissionId,
                    userId,
                    null, // doctorId will be assigned if sendTo is "Doctor"
                    summaryText,
                    sendTo,
                    "pending",
                    System.currentTimeMillis()
            );

            // If sending to doctor, assign to first available doctor
            if ("Doctor".equalsIgnoreCase(sendTo)) {
                User doctor = userDao.getFirstAvailableDoctor();
                if (doctor != null) {
                    submission.setDoctorId(doctor.getId());
                }
            }

            submissionDao.insertSubmission(submission);

            if (onComplete != null) {
                mainHandler.post(onComplete);
            }
        });
    }

    public interface SubmissionCallback {
        void onSubmissionCreated(String submissionId);
    }

    public void createSubmissionWithDoctorAssignmentCallback(String userId, String summaryText, String sendTo, SubmissionCallback callback) {
        ioExecutor.execute(() -> {
            String submissionId = UUID.randomUUID().toString();
            Submission submission = new Submission(
                    submissionId,
                    userId,
                    null,
                    summaryText,
                    sendTo,
                    "pending",
                    System.currentTimeMillis()
            );

            if ("Doctor".equalsIgnoreCase(sendTo)) {
                User doctor = userDao.getFirstAvailableDoctor();
                if (doctor != null) {
                    submission.setDoctorId(doctor.getId());
                }
            }

            submissionDao.insertSubmission(submission);

            if (callback != null) {
                mainHandler.post(() -> callback.onSubmissionCreated(submissionId));
            }
        });
    }

    // ========================================
    // ADMIN OPERATIONS
    // ========================================

    public LiveData<List<User>> observeAllUsers() {
        return userDao.observeAllUsers();
    }

    public LiveData<List<User>> observeAllDoctors() {
        return userDao.observeAllDoctors();
    }

    public LiveData<List<Submission>> observeAllSubmissions() {
        return submissionDao.observeAllSubmissions();
    }

    public List<Submission> getAllSubmissionsSync() {
        try {
            return ioExecutor.submit(() -> submissionDao.getAllSubmissions()).get();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public void blockUser(String userId, Runnable onComplete) {
        ioExecutor.execute(() -> {
            User user = userDao.getUserById(userId);
            if (user != null) {
                user.setBlocked(true);
                userDao.updateUser(user);
            }
            if (onComplete != null) {
                mainHandler.post(onComplete);
            }
        });
    }

    public void unblockUser(String userId, Runnable onComplete) {
        ioExecutor.execute(() -> {
            User user = userDao.getUserById(userId);
            if (user != null) {
                user.setBlocked(false);
                userDao.updateUser(user);
            }
            if (onComplete != null) {
                mainHandler.post(onComplete);
            }
        });
    }

    public void deleteUser(String userId, Runnable onComplete) {
        deleteUserFromLocalDB(userId, onComplete);
    }

    public void deleteUserFromLocalDB(String userId, Runnable onComplete) {
        ioExecutor.execute(() -> {
            try {
                // Delete user's submissions
                submissionDao.deleteSubmissionsForUser(userId);
                // Delete user's reports
                reportDao.deleteReportsForUser(userId);
                // Delete user from database
                userDao.deleteUser(userId);
                if (onComplete != null) {
                    mainHandler.post(onComplete);
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (onComplete != null) {
                    mainHandler.post(onComplete);
                }
            }
        });
    }

    public void approveDoctor(String doctorId, Runnable onComplete) {
        ioExecutor.execute(() -> {
            User doctor = userDao.getUserById(doctorId);
            if (doctor != null) {
                doctor.setBlocked(false);
                userDao.updateUser(doctor);
            }
            if (onComplete != null) {
                mainHandler.post(onComplete);
            }
        });
    }

    public void removeDoctor(String doctorId, Runnable onComplete) {
        // Use the same delete method
        deleteUserFromLocalDB(doctorId, onComplete);
    }

    public int getTotalUsersCountSync() {
        try {
            return ioExecutor.submit(() -> userDao.getTotalUsersCount()).get();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int getTotalDoctorsCountSync() {
        try {
            return ioExecutor.submit(() -> userDao.getDoctorCount()).get();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int getTotalPatientsCountSync() {
        try {
            return ioExecutor.submit(() -> userDao.getPatientCount()).get();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int getTotalSubmissionsCountSync() {
        try {
            return ioExecutor.submit(() -> submissionDao.getTotalSubmissionsCount()).get();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int getTotalReportsCountSync() {
        try {
            return ioExecutor.submit(() -> reportDao.getReportsCount()).get();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

}

