package com.example.parkinsonsdiseasedetectionsystem.utils;

import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.example.parkinsonsdiseasedetectionsystem.models.User;

import java.util.ArrayList;
import java.util.List;

/**
 * DataRepository - Centralized data management with mock data
 * TODO: Replace with Firebase Firestore or REST API calls
 */
public class DataRepository {
    private static DataRepository instance;
    private List<User> users;
    private List<Report> reports;

    private DataRepository() {
        initializeMockData();
    }

    public static synchronized DataRepository getInstance() {
        if (instance == null) {
            instance = new DataRepository();
        }
        return instance;
    }

    /**
     * Initialize mock data for testing
     * TODO: Remove and fetch from Firebase
     */
    private void initializeMockData() {
        users = new ArrayList<>();
        reports = new ArrayList<>();

        // Mock Users
        users.add(createUser("1", "John Smith", "john@example.com", "Male", "45", "Early Stage"));
        users.add(createUser("2", "Sarah Johnson", "sarah@example.com", "Female", "52", "Moderate"));
        users.add(createUser("3", "Michael Brown", "michael@example.com", "Male", "61", "Advanced"));
        users.add(createUser("4", "Emily Davis", "emily@example.com", "Female", "48", "Early Stage"));
        users.add(createUser("5", "Robert Wilson", "robert@example.com", "Male", "55", "Moderate"));

        // Mock Reports
        reports.add(createReport("R1", "1", "John Smith", "voice", 0.85f, "Pending", "2 hours ago"));
        reports.add(createReport("R2", "2", "Sarah Johnson", "video", 0.72f, "Pending", "5 hours ago"));
        reports.add(createReport("R3", "3", "Michael Brown", "voice", 0.91f, "Doctor Verified", "1 day ago"));
        reports.add(createReport("R4", "4", "Emily Davis", "video", 0.23f, "Pending", "3 hours ago"));
        reports.add(createReport("R5", "5", "Robert Wilson", "voice", 0.67f, "Sent", "6 hours ago"));
    }

    private User createUser(String id, String name, String email, String gender, String age, String stage) {
        User user = new User(id, name, email, "1234567890", "patient");
        // Set additional fields as needed
        return user;
    }

    private Report createReport(String id, String userId, String userName, String type,
                                float aiScore, String status, String timeAgo) {
        Report report = new Report(id, userId, userName, type, "/path/to/file", aiScore);
        report.setDoctorVerification(status);
        // Set timeAgo as additional field
        return report;
    }

    // ========================================
    // PUBLIC API METHODS
    // ========================================

    /**
     * Get all users
     * TODO: Fetch from Firestore collection "users"
     */
    public List<User> getUsers() {
        return new ArrayList<>(users);
    }

    /**
     * Get user by ID
     * TODO: Fetch from Firestore document
     */
    public User getUserById(String userId) {
        for (User user : users) {
            if (user.getId().equals(userId)) {
                return user;
            }
        }
        return null;
    }

    /**
     * Get all reports
     * TODO: Fetch from Firestore collection "reports"
     */
    public List<Report> getReports() {
        return new ArrayList<>(reports);
    }

    /**
     * Get reports for specific user
     */
    public List<Report> getReportsByUserId(String userId) {
        List<Report> userReports = new ArrayList<>();
        for (Report report : reports) {
            if (report.getPatientId().equals(userId)) {
                userReports.add(report);
            }
        }
        return userReports;
    }

    /**
     * Get report by ID
     */
    public Report getReportById(String reportId) {
        for (Report report : reports) {
            if (report.getId().equals(reportId)) {
                return report;
            }
        }
        return null;
    }

    /**
     * Approve report
     * TODO: Update Firestore document and send notification
     */
    public void approveReport(String reportId) {
        Report report = getReportById(reportId);
        if (report != null) {
            report.setDoctorVerification("Doctor Verified");
            report.setVerifiedAt(System.currentTimeMillis());
        }
    }

    /**
     * Reject report
     * TODO: Update Firestore and notify user
     */
    public void rejectReport(String reportId, String reason) {
        Report report = getReportById(reportId);
        if (report != null) {
            report.setDoctorVerification("Rejected");
            report.setDoctorNotes(reason);
        }
    }

    /**
     * Send report to user
     * TODO: Send push notification and update status
     */
    public void sendReportToUser(String reportId) {
        Report report = getReportById(reportId);
        if (report != null) {
            report.setDoctorVerification("Sent");
        }
    }

    /**
     * Get statistics
     */
    public int getTotalUsers() {
        return users.size();
    }

    public int getPendingReportsCount() {
        int count = 0;
        for (Report report : reports) {
            if ("Pending".equals(report.getDoctorVerification())) {
                count++;
            }
        }
        return count;
    }

    public int getVerifiedReportsCount() {
        int count = 0;
        for (Report report : reports) {
            if ("Doctor Verified".equals(report.getDoctorVerification())) {
                count++;
            }
        }
        return count;
    }
}