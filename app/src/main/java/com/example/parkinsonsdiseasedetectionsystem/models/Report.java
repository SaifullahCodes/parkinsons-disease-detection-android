package com.example.parkinsonsdiseasedetectionsystem.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "reports")
public class Report {
    @PrimaryKey
    @NonNull
    private String id;
    private String patientId;
    private String patientName;
    private String doctorId;
    private String doctorName;
    private String recordingType;
    private String filePath;
    private float aiPrediction;
    private String aiResult;
    private String doctorVerification;
    private String doctorNotes;
    private long createdAt;
    private long verifiedAt;
    private String summaryText;
    private String severityText;
    private String diagnosisText;
    private String adviceText;
    private String submissionId; // Link to submission

    // 🚀 NEW FIELD FOR VIDEO LINK
    private String videoUrl;

    @Ignore
    public Report() {}

    public Report(@NonNull String id, String patientId, String patientName, String recordingType,
                  String filePath, float aiPrediction) {
        this.id = id;
        this.patientId = patientId;
        this.patientName = patientName;
        this.recordingType = recordingType;
        this.filePath = filePath;
        this.aiPrediction = aiPrediction;
        this.aiResult = aiPrediction >= 0.5f ? "Parkinson's Detected" : "Healthy";
        this.doctorVerification = "Not Sent";
        this.createdAt = System.currentTimeMillis();
        this.summaryText = "";
        this.severityText = "";
        this.diagnosisText = "";
        this.adviceText = "";
        // videoUrl will be set later using the setter
    }

    // Getters and Setters
    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }
    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getDoctorId() { return doctorId; }
    public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getRecordingType() { return recordingType; }
    public void setRecordingType(String recordingType) { this.recordingType = recordingType; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public float getAiPrediction() { return aiPrediction; }
    public void setAiPrediction(float aiPrediction) { this.aiPrediction = aiPrediction; }
    public String getAiResult() { return aiResult; }
    public void setAiResult(String aiResult) { this.aiResult = aiResult; }
    public String getDoctorVerification() { return doctorVerification; }
    public void setDoctorVerification(String doctorVerification) {
        this.doctorVerification = doctorVerification;
    }
    public String getDoctorNotes() { return doctorNotes; }
    public void setDoctorNotes(String doctorNotes) { this.doctorNotes = doctorNotes; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(long verifiedAt) { this.verifiedAt = verifiedAt; }
    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }
    public String getSeverityText() { return severityText; }
    public void setSeverityText(String severityText) { this.severityText = severityText; }
    public String getDiagnosisText() { return diagnosisText; }
    public void setDiagnosisText(String diagnosisText) { this.diagnosisText = diagnosisText; }
    public String getAdviceText() { return adviceText; }
    public void setAdviceText(String adviceText) { this.adviceText = adviceText; }
    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }

    // 🚀 NEW GETTER AND SETTER FOR VIDEO URL
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
}