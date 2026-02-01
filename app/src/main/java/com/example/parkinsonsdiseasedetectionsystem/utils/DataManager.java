package com.example.parkinsonsdiseasedetectionsystem.utils;

import com.example.parkinsonsdiseasedetectionsystem.models.Report;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DataManager {
    private static final List<Report> reports = new ArrayList<>();

    public static void addReport(Report report) {
        reports.add(0, report);
    }

    public static List<Report> getPatientReports(String patientId) {
        List<Report> patientReports = new ArrayList<>();
        for (Report report : reports) {
            if (report.getPatientId().equals(patientId)) {
                patientReports.add(report);
            }
        }
        patientReports.sort((r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));
        return patientReports;
    }

    public static List<Report> getPendingReports() {
        List<Report> pendingReports = new ArrayList<>();
        for (Report report : reports) {
            if (report.getDoctorVerification().equals("Pending")) {
                pendingReports.add(report);
            }
        }
        return pendingReports;
    }

    public static List<Report> getAllReports() {
        return new ArrayList<>(reports);
    }

    public static Report getReportById(String reportId) {
        for (Report report : reports) {
            if (report.getId().equals(reportId)) {
                return report;
            }
        }
        return null;
    }

    public static void updateReport(Report report) {
        for (int i = 0; i < reports.size(); i++) {
            if (reports.get(i).getId().equals(report.getId())) {
                reports.set(i, report);
                break;
            }
        }
    }

    public static String generateReportId() {
        return UUID.randomUUID().toString();
    }

    // ========================================
    // USER STATS / HELPERS
    // ========================================

    public static int getTotalReportsForUser(String userId) {
        return getPatientReports(userId).size();
    }

    public static int getPendingReportsForUser(String userId) {
        int count = 0;
        for (Report report : reports) {
            if (report.getPatientId().equals(userId)
                    && "Pending".equalsIgnoreCase(report.getDoctorVerification())) {
                count++;
            }
        }
        return count;
    }

    public static int getSeverityCountForUser(String userId, String severity) {
        int count = 0;
        for (Report report : reports) {
            if (report.getPatientId().equals(userId)
                    && severity.equalsIgnoreCase(report.getSeverityText())) {
                count++;
            }
        }
        return count;
    }

    public static Report getLatestReport(String userId) {
        List<Report> patientReports = getPatientReports(userId);
        return patientReports.isEmpty() ? null : patientReports.get(0);
    }

    public static void clearReportsForUser(String userId) {
        Iterator<Report> iterator = reports.iterator();
        while (iterator.hasNext()) {
            Report report = iterator.next();
            if (report.getPatientId().equals(userId)) {
                iterator.remove();
            }
        }
    }

    public static void deleteReportById(String reportId) {
        Iterator<Report> iterator = reports.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getId().equals(reportId)) {
                iterator.remove();
                break;
            }
        }
    }

    public static void seedDemoReportsIfEmpty(String userId, String userName) {
        boolean hasReports = false;
        for (Report report : reports) {
            if (report.getPatientId().equals(userId)) {
                hasReports = true;
                break;
            }
        }
        if (hasReports) return;

        long now = System.currentTimeMillis();
        addReport(buildMockReport(userId, userName, "voice",
                0.22f, "Low", "Healthy baseline recorded",
                "Stable speech and movement patterns", "Maintain usual routine",
                "Reviewed", now - TimeUnit.HOURS.toMillis(6)));
        addReport(buildMockReport(userId, userName, "video",
                0.58f, "Moderate", "Mild tremor captured on video",
                "Possible mild Parkinson symptoms", "Consult doctor if tremor persists",
                "Pending", now - TimeUnit.DAYS.toMillis(1)));
        addReport(buildMockReport(userId, userName, "voice",
                0.78f, "High", "Patient reported stiffness and slower speech",
                "Parkinson-like symptoms detected", "Schedule check-up within a week",
                "Doctor Verified", now - TimeUnit.DAYS.toMillis(4)));
    }

    private static Report buildMockReport(String userId, String userName, String recordingType,
                                          float aiScore, String severity, String summary,
                                          String diagnosis, String advice,
                                          String status, long createdAt) {
        Report report = new Report(generateReportId(), userId, userName,
                recordingType, "/local/path", aiScore);
        report.setSeverityText(severity);
        report.setSummaryText(summary);
        report.setDiagnosisText(diagnosis);
        report.setAdviceText(advice);
        report.setDoctorVerification(status);
        report.setCreatedAt(createdAt);
        return report;
    }
}