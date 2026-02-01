package com.example.parkinsonsdiseasedetectionsystem.models;

import java.io.Serializable;

/**
 * Model class representing a single detection record
 * Contains all information about a Parkinson's disease detection test
 */
public class DetectionRecord implements Serializable {

    private String id;                    // Unique record ID
    private String date;                  // Test date (e.g., "March 15, 2024")
    private String time;                  // Test time (e.g., "10:30 AM")
    private String prediction;            // Prediction result (e.g., "Parkinson Detected")
    private float confidence;             // Confidence score (0-100)
    private boolean parkinsonDetected;    // True if Parkinson detected, false otherwise
    private long timestamp;               // Unix timestamp for sorting

    // Optional fields for detailed information
    private String audioFilePath;         // Path to audio file used for detection
    private String videoFilePath;         // Path to video file (if applicable)
    private String notes;                 // Additional notes or observations
    private String modelVersion;          // AI model version used

    /**
     * Empty constructor required for Firebase
     */
    public DetectionRecord() {
    }

    /**
     * Constructor with essential fields
     */
    public DetectionRecord(String id, String date, String time, String prediction,
                           float confidence, boolean parkinsonDetected) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.prediction = prediction;
        this.confidence = confidence;
        this.parkinsonDetected = parkinsonDetected;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Full constructor with all fields
     */
    public DetectionRecord(String id, String date, String time, String prediction,
                           float confidence, boolean parkinsonDetected, long timestamp,
                           String audioFilePath, String videoFilePath, String notes,
                           String modelVersion) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.prediction = prediction;
        this.confidence = confidence;
        this.parkinsonDetected = parkinsonDetected;
        this.timestamp = timestamp;
        this.audioFilePath = audioFilePath;
        this.videoFilePath = videoFilePath;
        this.notes = notes;
        this.modelVersion = modelVersion;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getPrediction() {
        return prediction;
    }

    public void setPrediction(String prediction) {
        this.prediction = prediction;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public boolean isParkinsonDetected() {
        return parkinsonDetected;
    }

    public void setParkinsonDetected(boolean parkinsonDetected) {
        this.parkinsonDetected = parkinsonDetected;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getAudioFilePath() {
        return audioFilePath;
    }

    public void setAudioFilePath(String audioFilePath) {
        this.audioFilePath = audioFilePath;
    }

    public String getVideoFilePath() {
        return videoFilePath;
    }

    public void setVideoFilePath(String videoFilePath) {
        this.videoFilePath = videoFilePath;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    /**
     * Get confidence as integer percentage
     */
    public int getConfidencePercentage() {
        return Math.round(confidence);
    }

    /**
     * Get status color based on detection result
     * @return Color code as string
     */
    public String getStatusColor() {
        return parkinsonDetected ? "#EF4444" : "#10B981";
    }

    /**
     * Get status text for display
     */
    public String getStatusText() {
        return parkinsonDetected ? "Positive" : "Negative";
    }

    @Override
    public String toString() {
        return "DetectionRecord{" +
                "id='" + id + '\'' +
                ", date='" + date + '\'' +
                ", time='" + time + '\'' +
                ", prediction='" + prediction + '\'' +
                ", confidence=" + confidence +
                ", parkinsonDetected=" + parkinsonDetected +
                ", timestamp=" + timestamp +
                '}';
    }
}