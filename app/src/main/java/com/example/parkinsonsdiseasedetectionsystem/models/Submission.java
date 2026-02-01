package com.example.parkinsonsdiseasedetectionsystem.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "submissions")
public class Submission {

    @PrimaryKey
    @NonNull
    private String submissionId;
    private String userId;
    private String userName;
    private String doctorId;
    private String assignedDoctorId; // Alias for doctorId for Firebase compatibility
    private String summaryText;
    private String sendTo;
    private String status;
    private String recordingType;
    private String filePath;
    private float aiPrediction;
    private String aiResult;
    private long createdAt;

    @Ignore
    public Submission() {}

    public Submission(@NonNull String submissionId, String userId, String doctorId,
                      String summaryText, String sendTo, String status, long createdAt) {
        this.submissionId = submissionId;
        this.userId = userId;
        this.doctorId = doctorId;
        this.assignedDoctorId = doctorId;
        this.summaryText = summaryText;
        this.sendTo = sendTo;
        this.status = status;
        this.createdAt = createdAt;
        this.aiPrediction = 0.0f;
        this.aiResult = "";
    }

    // Convenience method for Firebase compatibility
    public String getId() {
        return submissionId;
    }
    
    public void setId(String id) {
        this.submissionId = id;
    }

    @NonNull
    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(@NonNull String submissionId) { 
        this.submissionId = submissionId; 
    }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getDoctorId() { return doctorId; }
    public void setDoctorId(String doctorId) { 
        this.doctorId = doctorId;
        this.assignedDoctorId = doctorId; // Keep in sync
    }
    public String getAssignedDoctorId() { 
        return assignedDoctorId != null ? assignedDoctorId : doctorId; 
    }
    public void setAssignedDoctorId(String assignedDoctorId) { 
        this.assignedDoctorId = assignedDoctorId;
        this.doctorId = assignedDoctorId; // Keep in sync
    }
    public String getSummaryText() { return summaryText; }
    public void setSummaryText(String summaryText) { this.summaryText = summaryText; }
    public String getSendTo() { return sendTo; }
    public void setSendTo(String sendTo) { this.sendTo = sendTo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRecordingType() { return recordingType; }
    public void setRecordingType(String recordingType) { this.recordingType = recordingType; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public float getAiPrediction() { return aiPrediction; }
    public void setAiPrediction(float aiPrediction) { this.aiPrediction = aiPrediction; }
    public String getAiResult() { return aiResult; }
    public void setAiResult(String aiResult) { this.aiResult = aiResult; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}

