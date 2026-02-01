package com.example.parkinsonsdiseasedetectionsystem.activities;

import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HistoryDetailsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvDetailDate, tvDetailTime;
    private TextView tvStatusTitle, tvStatusDescription;
    private TextView tvVideoPercentage, tvVideoStatusText, tvVideoSymptoms, tvVideoRecommendation;
    private TextView tvAudioPercentage, tvAudioStatusText, tvAudioSymptoms, tvAudioRecommendation;
    private TextView tvDoctorStatus, tvDoctorNotes, tvDoctorVideoStatus, tvDoctorAudioStatus;
    private ImageView ivStatusIcon;
    private ProgressBar progressVideoAnalysis, progressAudioAnalysis;
    private MaterialCardView statusCard, cardVideoAnalysis, cardAudioAnalysis, cardDoctorReview;
    private View viewDoctorDivider;
    private MaterialButton btnPlayAudio, btnViewVideo, btnShareReport, btnDeleteRecord;

    private Report report;
    private String reportId;
    private LocalRepository localRepository;
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history_details);

        localRepository = LocalRepository.getInstance(getApplicationContext());
        reportId = getIntent().getStringExtra("REPORT_ID");

        if (reportId == null) {
            finish(); return;
        }

        bindViews();
        setupToolbar();
        loadReport();
    }
    
    @Override
    public void onBackPressed() {
        // Simply go back to previous activity
        super.onBackPressed();
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbarDetails);
        tvDetailDate = findViewById(R.id.tvDetailDate);
        tvDetailTime = findViewById(R.id.tvDetailTime);

        statusCard = findViewById(R.id.statusCard);
        ivStatusIcon = findViewById(R.id.ivStatusIcon);
        tvStatusTitle = findViewById(R.id.tvStatusTitle);
        tvStatusDescription = findViewById(R.id.tvStatusDescription);

        btnPlayAudio = findViewById(R.id.btnPlayAudio);
        btnViewVideo = findViewById(R.id.btnViewVideo);
        btnShareReport = findViewById(R.id.btnShareReport);
        btnDeleteRecord = findViewById(R.id.btnDeleteRecord);
        
        // Video analysis views
        cardVideoAnalysis = findViewById(R.id.cardVideoAnalysis);
        tvVideoPercentage = findViewById(R.id.tvVideoPercentage);
        tvVideoStatusText = findViewById(R.id.tvVideoStatusText);
        progressVideoAnalysis = findViewById(R.id.progressVideoAnalysis);
        tvVideoSymptoms = findViewById(R.id.tvVideoSymptoms);
        tvVideoRecommendation = findViewById(R.id.tvVideoRecommendation);
        
        // Audio analysis views
        cardAudioAnalysis = findViewById(R.id.cardAudioAnalysis);
        tvAudioPercentage = findViewById(R.id.tvAudioPercentage);
        tvAudioStatusText = findViewById(R.id.tvAudioStatusText);
        progressAudioAnalysis = findViewById(R.id.progressAudioAnalysis);
        tvAudioSymptoms = findViewById(R.id.tvAudioSymptoms);
        tvAudioRecommendation = findViewById(R.id.tvAudioRecommendation);
        
        // Doctor review views
        cardDoctorReview = findViewById(R.id.cardDoctorReview);
        tvDoctorStatus = findViewById(R.id.tvDoctorStatus);
        tvDoctorNotes = findViewById(R.id.tvDoctorNotes);
        tvDoctorVideoStatus = findViewById(R.id.tvDoctorVideoStatus);
        tvDoctorAudioStatus = findViewById(R.id.tvDoctorAudioStatus);
        viewDoctorDivider = findViewById(R.id.viewDoctorDivider);

        btnPlayAudio.setOnClickListener(v -> toggleAudio());
        btnViewVideo.setOnClickListener(v -> playVideo());
        btnShareReport.setOnClickListener(v -> shareReport());
        btnDeleteRecord.setOnClickListener(v -> confirmDelete());
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadReport() {
        localRepository.getReportById(reportId, r -> {
            if (r == null) {
                // If not found locally, try fetching from Firebase as fallback
                FirebaseRealtimeRepository.getInstance().getReportById(reportId, new FirebaseRealtimeRepository.ReportCallback() {
                    @Override
                    public void onSuccess(Report firebaseReport) {
                        report = firebaseReport;
                        runOnUiThread(() -> displayData());
                    }
                    @Override
                    public void onFailure(Exception e) {
                        runOnUiThread(() -> {
                            Toast.makeText(HistoryDetailsActivity.this, "Report not found", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                });
            } else {
                report = r;
                runOnUiThread(this::displayData);
            }
        });
    }

    private void displayData() {
        if (report == null) return;

        // 1. Status Card (Top)
        boolean isVerified = "Doctor Verified".equalsIgnoreCase(report.getDoctorVerification()) || 
                             "Verified".equalsIgnoreCase(report.getDoctorVerification());
        if (isVerified) {
            statusCard.setCardBackgroundColor(Color.parseColor("#ECFDF5"));
            statusCard.setStrokeColor(Color.parseColor("#059669"));
            ivStatusIcon.setImageResource(R.drawable.ic_check_circle);
            ivStatusIcon.setColorFilter(Color.parseColor("#059669"));
            tvStatusTitle.setText("Verified");
            tvStatusTitle.setTextColor(Color.parseColor("#059669"));
            tvStatusDescription.setText("Report verified by doctor");
        } else {
            statusCard.setCardBackgroundColor(Color.parseColor("#FFFBEB"));
            statusCard.setStrokeColor(Color.parseColor("#D97706"));
            ivStatusIcon.setImageResource(android.R.drawable.ic_dialog_info);
            ivStatusIcon.setColorFilter(Color.parseColor("#D97706"));
            tvStatusTitle.setText("Pending");
            tvStatusTitle.setTextColor(Color.parseColor("#D97706"));
            tvStatusDescription.setText("Waiting for doctor review");
        }

        // 2. Date & Time
        Date date = new Date(report.getCreatedAt());
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        tvDetailDate.setText(dateFormat.format(date));
        tvDetailTime.setText(timeFormat.format(date));

        // 3. Media Files Check - Files are saved in Room DB on user's phone
        boolean hasAudio = false;
        String audioPath = report.getFilePath();
        if (audioPath != null && !audioPath.isEmpty() && !audioPath.equals("No Audio")) {
            File audioFile = new File(audioPath);
            hasAudio = audioFile.exists() && audioFile.canRead();
            if (!hasAudio) {
                // Try to find in external files directory
                File externalDir = getExternalFilesDir(null);
                if (externalDir != null) {
                    String fileName = audioFile.getName();
                    File alternativeFile = new File(externalDir, fileName);
                    if (alternativeFile.exists() && alternativeFile.canRead()) {
                        audioPath = alternativeFile.getAbsolutePath();
                        hasAudio = true;
                        // Update report path for future use
                        report.setFilePath(audioPath);
                    }
                }
            }
        }
        
        boolean hasVideo = false;
        String videoPath = report.getVideoUrl();
        if (videoPath != null && !videoPath.isEmpty() && 
            !videoPath.equals("No Video") && 
            !videoPath.equals("Available on device")) {
            File videoFile = new File(videoPath);
            hasVideo = videoFile.exists() && videoFile.canRead();
            if (!hasVideo) {
                // Try to find in external files directory
                File externalDir = getExternalFilesDir(null);
                if (externalDir != null) {
                    String fileName = videoFile.getName();
                    File alternativeFile = new File(externalDir, fileName);
                    if (alternativeFile.exists() && alternativeFile.canRead()) {
                        videoPath = alternativeFile.getAbsolutePath();
                        hasVideo = true;
                        // Update report path for future use
                        report.setVideoUrl(videoPath);
                    }
                }
            }
        }

        // Set button visibility and text
        if (hasAudio) {
            btnPlayAudio.setVisibility(View.VISIBLE);
            btnPlayAudio.setText("Listen");
            btnPlayAudio.setEnabled(true);
            btnPlayAudio.setAlpha(1.0f);
        } else {
            btnPlayAudio.setVisibility(View.VISIBLE);
            btnPlayAudio.setText("No Audio");
            btnPlayAudio.setEnabled(false);
            btnPlayAudio.setAlpha(0.5f);
        }

        if (hasVideo) {
            btnViewVideo.setVisibility(View.VISIBLE);
            btnViewVideo.setText("Watch");
            btnViewVideo.setEnabled(true);
            btnViewVideo.setAlpha(1.0f);
        } else {
            btnViewVideo.setVisibility(View.VISIBLE);
            btnViewVideo.setText("No Video");
            btnViewVideo.setEnabled(false);
            btnViewVideo.setAlpha(0.5f);
        }
        
        // Store paths for playback
        if (hasAudio) {
            report.setFilePath(audioPath);
        }
        if (hasVideo) {
            report.setVideoUrl(videoPath);
        }

        // 4. Display Professional AI Analysis Report
        displayProfessionalAnalysis();
        
        // 5. Display Doctor Review Section (always show, visibility managed inside)
        cardDoctorReview.setVisibility(View.VISIBLE);
        displayDoctorReview(hasVideo, hasAudio, isVerified);
    }
    
    private void displayDoctorReview(boolean hasVideo, boolean hasAudio, boolean isVerified) {
        // Always show doctor review card
        cardDoctorReview.setVisibility(View.VISIBLE);
        
        if (isVerified) {
            // Doctor has verified - show all details
            tvDoctorStatus.setText("Verified by Doctor");
            tvDoctorStatus.setTextColor(Color.parseColor("#059669"));
            
            // Show doctor name if available
            if (report.getDoctorName() != null && !report.getDoctorName().isEmpty()) {
                tvDoctorStatus.setText("Verified by Dr. " + report.getDoctorName());
            }
            
            // Build complete doctor review text
            StringBuilder doctorReviewText = new StringBuilder();
            
            // Severity
            if (report.getSeverityText() != null && !report.getSeverityText().isEmpty()) {
                doctorReviewText.append("Severity: ").append(report.getSeverityText()).append("\n\n");
            }
            
            // Diagnosis
            if (report.getDiagnosisText() != null && !report.getDiagnosisText().isEmpty()) {
                doctorReviewText.append("Diagnosis:\n").append(report.getDiagnosisText()).append("\n\n");
            }
            
            // Extract doctor's advice from adviceText (separate from AI analysis)
            String adviceText = report.getAdviceText();
            if (adviceText != null && !adviceText.isEmpty()) {
                // Check for DOCTOR_ADVICE section
                if (adviceText.contains("DOCTOR_ADVICE|")) {
                    String[] parts = adviceText.split("DOCTOR_ADVICE\\|");
                    if (parts.length > 1) {
                        String doctorAdvice = parts[1].trim();
                        if (!doctorAdvice.isEmpty()) {
                            doctorReviewText.append("Medical Advice:\n").append(doctorAdvice).append("\n\n");
                        }
                    }
                }
            }
            
            // Doctor Notes (most important)
            if (report.getDoctorNotes() != null && !report.getDoctorNotes().isEmpty()) {
                doctorReviewText.append("Doctor's Notes:\n").append(report.getDoctorNotes());
            }
            
            if (doctorReviewText.length() > 0) {
                tvDoctorNotes.setVisibility(View.VISIBLE);
                tvDoctorNotes.setText(doctorReviewText.toString().trim());
                viewDoctorDivider.setVisibility(View.VISIBLE);
            } else {
                tvDoctorNotes.setVisibility(View.GONE);
                viewDoctorDivider.setVisibility(View.GONE);
            }
            
            // Show verified date if available
            if (report.getVerifiedAt() > 0) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", java.util.Locale.getDefault());
                String verifiedDate = sdf.format(new java.util.Date(report.getVerifiedAt()));
                // Could add a separate TextView for verified date if needed
            }
        } else {
            // Waiting for approval - show waiting message
            tvDoctorStatus.setText("Waiting for Doctor Verification");
            tvDoctorStatus.setTextColor(Color.parseColor("#D97706"));
            tvDoctorNotes.setVisibility(View.VISIBLE);
            tvDoctorNotes.setText("Your report has been sent to a doctor for review. You will be notified once the doctor has reviewed and verified your report.");
            tvDoctorNotes.setTextColor(Color.parseColor("#64748B"));
            viewDoctorDivider.setVisibility(View.GONE);
        }
        
        // Always show media file status for doctor review
        tvDoctorVideoStatus.setVisibility(View.VISIBLE);
        tvDoctorAudioStatus.setVisibility(View.VISIBLE);
        
        tvDoctorVideoStatus.setText(hasVideo ? "Video: Available" : "Video: No video added");
        tvDoctorVideoStatus.setTextColor(hasVideo ? Color.parseColor("#059669") : Color.parseColor("#94A3B8"));
        
        tvDoctorAudioStatus.setText(hasAudio ? "Audio: Available" : "Audio: No audio added");
        tvDoctorAudioStatus.setTextColor(hasAudio ? Color.parseColor("#059669") : Color.parseColor("#94A3B8"));
    }

    private void displayProfessionalAnalysis() {
        if (report == null) return;

        String adviceText = report.getAdviceText();
        String recordingType = report.getRecordingType();
        
        // Hide both cards initially
        cardVideoAnalysis.setVisibility(View.GONE);
        cardAudioAnalysis.setVisibility(View.GONE);

        if (adviceText != null && !adviceText.isEmpty()) {
            // Parse the concise format: TYPE|DIAGNOSIS|RISK%|SYMPTOMS|RECOMMENDATION
            String[] lines = adviceText.split("\n");
            
            for (String line : lines) {
                if (line.startsWith("VIDEO_ANALYSIS|")) {
                    displayVideoAnalysis(line);
                } else if (line.startsWith("AUDIO_ANALYSIS|")) {
                    displayAudioAnalysis(line);
                } else if (line.startsWith("COMBINED_RESULT|")) {
                    // Combined result - already shown in main prediction
                } else if (line.startsWith("ANALYSIS|")) {
                    // Legacy format - show as single analysis
                    displayLegacyAnalysis(line);
                }
            }
        } else {
            // Fallback: Show basic info based on recording type
            if (recordingType != null && recordingType.contains("video")) {
                displayFallbackVideo();
            } else if (recordingType != null && recordingType.contains("audio")) {
                displayFallbackAudio();
            }
        }
    }

    private void displayVideoAnalysis(String line) {
        String[] parts = line.split("\\|");
        if (parts.length >= 5) {
            cardVideoAnalysis.setVisibility(View.VISIBLE);
            
            String diagnosis = parts[1];
            String riskLevel = parts[2].replace("%", "");
            String symptoms = parts[3];
            String recommendation = parts[4];
            
            // Check if analysis failed
            if (diagnosis.contains("Failed") || riskLevel.equals("--")) {
                tvVideoPercentage.setText("--");
                tvVideoStatusText.setText("Analysis Failed");
                tvVideoStatusText.setTextColor(Color.parseColor("#EF4444"));
                progressVideoAnalysis.setProgress(0);
                progressVideoAnalysis.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#EF4444")));
                tvVideoSymptoms.setText("Error occurred during analysis");
                tvVideoRecommendation.setText("Please try recording again.");
                return;
            }
            
            // Parse percentage
            int percentage = 0;
            try {
                percentage = Integer.parseInt(riskLevel);
            } catch (NumberFormatException e) {
                percentage = 0;
            }
            
            boolean isDetected = diagnosis.contains("Parkinson") || diagnosis.contains("Detected");
            int color = isDetected ? Color.parseColor("#EF4444") : Color.parseColor("#10B981");
            
            // Display percentage and status
            tvVideoPercentage.setText(percentage + "%");
            tvVideoPercentage.setTextColor(color);
            tvVideoStatusText.setText(isDetected ? "Parkinson's Detected" : "Healthy");
            tvVideoStatusText.setTextColor(color);
            
            // Progress bar (green line) - for healthy, show as inverse (100 - risk%)
            int progressValue = isDetected ? percentage : (100 - percentage);
            progressVideoAnalysis.setProgress(progressValue);
            progressVideoAnalysis.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
            
            // Symptoms and Recommendation
            tvVideoSymptoms.setText(symptoms.isEmpty() || symptoms.equals("None detected") ? "None detected" : symptoms);
            tvVideoRecommendation.setText(recommendation);
        }
    }

    private void displayAudioAnalysis(String line) {
        String[] parts = line.split("\\|");
        if (parts.length >= 5) {
            cardAudioAnalysis.setVisibility(View.VISIBLE);
            
            String diagnosis = parts[1];
            String riskLevel = parts[2].replace("%", "");
            String symptoms = parts[3];
            String recommendation = parts[4];
            
            // Check if analysis failed
            if (diagnosis.contains("Failed") || riskLevel.equals("--")) {
                tvAudioPercentage.setText("--");
                tvAudioStatusText.setText("Analysis Failed");
                tvAudioStatusText.setTextColor(Color.parseColor("#EF4444"));
                progressAudioAnalysis.setProgress(0);
                progressAudioAnalysis.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#EF4444")));
                tvAudioSymptoms.setText("Error occurred during analysis");
                tvAudioRecommendation.setText("Please try recording again.");
                return;
            }
            
            // Parse percentage
            int percentage = 0;
            try {
                percentage = Integer.parseInt(riskLevel);
            } catch (NumberFormatException e) {
                percentage = 0;
            }
            
            boolean isDetected = diagnosis.contains("Parkinson") || diagnosis.contains("Detected");
            int color = isDetected ? Color.parseColor("#EF4444") : Color.parseColor("#10B981");
            
            // Display percentage and status
            tvAudioPercentage.setText(percentage + "%");
            tvAudioPercentage.setTextColor(color);
            tvAudioStatusText.setText(isDetected ? "Parkinson's Detected" : "Healthy");
            tvAudioStatusText.setTextColor(color);
            
            // Progress bar (green line) - for healthy, show as inverse (100 - risk%)
            int progressValue = isDetected ? percentage : (100 - percentage);
            progressAudioAnalysis.setProgress(progressValue);
            progressAudioAnalysis.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
            
            // Symptoms and Recommendation
            tvAudioSymptoms.setText(symptoms.isEmpty() || symptoms.equals("None detected") ? "None detected" : symptoms);
            tvAudioRecommendation.setText(recommendation);
        }
    }

    private void displayLegacyAnalysis(String line) {
        String[] parts = line.split("\\|");
        if (parts.length >= 4) {
            // Legacy format - show as single analysis based on recording type
            if (report.getRecordingType() != null && report.getRecordingType().contains("video")) {
                displayFallbackVideo();
            } else if (report.getRecordingType() != null && report.getRecordingType().contains("audio")) {
                displayFallbackAudio();
            } else {
                // Default to showing as audio
                displayFallbackAudio();
            }
        }
    }

    private void displayFallbackVideo() {
        cardVideoAnalysis.setVisibility(View.VISIBLE);
        String result = report.getAiResult() != null ? report.getAiResult() : "Unknown";
        int risk = (int)(report.getAiPrediction() * 100);
        
        boolean isDetected = result.contains("Parkinson") || result.contains("Detected");
        int color = isDetected ? Color.parseColor("#EF4444") : Color.parseColor("#10B981");
        
        tvVideoPercentage.setText(risk + "%");
        tvVideoPercentage.setTextColor(color);
        tvVideoStatusText.setText(isDetected ? "Parkinson's Detected" : "Healthy");
        tvVideoStatusText.setTextColor(color);
        
        int progressValue = isDetected ? risk : (100 - risk);
        progressVideoAnalysis.setProgress(progressValue);
        progressVideoAnalysis.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        
        tvVideoSymptoms.setText("Symptoms: " + (report.getSeverityText() != null ? report.getSeverityText() : "Not available"));
        tvVideoRecommendation.setText(report.getAdviceText() != null && !report.getAdviceText().isEmpty() 
            ? report.getAdviceText() : "Consult a neurologist for evaluation.");
    }

    private void displayFallbackAudio() {
        cardAudioAnalysis.setVisibility(View.VISIBLE);
        String result = report.getAiResult() != null ? report.getAiResult() : "Unknown";
        int risk = (int)(report.getAiPrediction() * 100);
        
        boolean isDetected = result.contains("Parkinson") || result.contains("Detected");
        int color = isDetected ? Color.parseColor("#EF4444") : Color.parseColor("#10B981");
        
        tvAudioPercentage.setText(risk + "%");
        tvAudioPercentage.setTextColor(color);
        tvAudioStatusText.setText(isDetected ? "Parkinson's Detected" : "Healthy");
        tvAudioStatusText.setTextColor(color);
        
        int progressValue = isDetected ? risk : (100 - risk);
        progressAudioAnalysis.setProgress(progressValue);
        progressAudioAnalysis.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        
        tvAudioSymptoms.setText("Symptoms: " + (report.getSeverityText() != null ? report.getSeverityText() : "Not available"));
        tvAudioRecommendation.setText(report.getAdviceText() != null && !report.getAdviceText().isEmpty() 
            ? report.getAdviceText() : "Consult a neurologist for evaluation.");
    }

    private void toggleAudio() { if (isPlaying) stopAudio(); else playAudio(); }
    private void playAudio() {
        try {
            String audioPath = report.getFilePath();
            if (audioPath == null || audioPath.isEmpty() || audioPath.equals("No Audio")) {
                Toast.makeText(this, "Audio file not available", Toast.LENGTH_SHORT).show();
                return;
            }
            
            File audioFile = new File(audioPath);
            if (!audioFile.exists() || !audioFile.canRead()) {
                Toast.makeText(this, "Audio file not found or cannot be read", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
            } else {
                mediaPlayer.reset();
            }
            
            mediaPlayer.setDataSource(audioPath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            isPlaying = true;
            btnPlayAudio.setText("Stop");
            mediaPlayer.setOnCompletionListener(mp -> stopAudio());
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                stopAudio();
                Toast.makeText(HistoryDetailsActivity.this, "Audio playback error", Toast.LENGTH_SHORT).show();
                return true;
            });
        } catch (IOException e) {
            Toast.makeText(this, "Playback Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            stopAudio();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            stopAudio();
        }
    }
    private void stopAudio() {
        if (mediaPlayer != null) { if (mediaPlayer.isPlaying()) mediaPlayer.stop(); mediaPlayer.reset(); }
        isPlaying = false; btnPlayAudio.setText("Play Audio");
    }
    private void playVideo() {
        String videoPath = report.getVideoUrl();
        if (videoPath == null || videoPath.isEmpty() || 
            videoPath.equals("No Video") || videoPath.equals("Available on device")) {
            Toast.makeText(this, "Video file not available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            
            // Check if it's a URL or local file path
            if (videoPath.startsWith("http://") || videoPath.startsWith("https://")) {
                // Remote URL
                intent.setDataAndType(Uri.parse(videoPath), "video/*");
            } else {
                // Local file
                File videoFile = new File(videoPath);
                if (!videoFile.exists() || !videoFile.canRead()) {
                    Toast.makeText(this, "Video file not found or cannot be read", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Use FileProvider for secure file access
                Uri uri = FileProvider.getUriForFile(
                    this, 
                    getApplicationContext().getPackageName() + ".fileprovider", 
                    videoFile
                );
                intent.setDataAndType(uri, "video/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            
            // Try to find a video player app
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "No video player app found. Please install a video player.", Toast.LENGTH_LONG).show();
            }
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Cannot play video: Invalid file path", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Cannot play video: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareReport() {
        if (report == null) return;

        StringBuilder shareText = new StringBuilder();
        shareText.append("Parkinson's Disease Detection Report\n");
        shareText.append("=====================================\n\n");

        // Date & Time
        Date date = new Date(report.getCreatedAt());
        shareText.append("Date: ").append(new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(date)).append("\n\n");

        // Patient Name
        if (report.getPatientName() != null && !report.getPatientName().isEmpty()) {
            shareText.append("Patient: ").append(report.getPatientName()).append("\n");
        }

        // AI Result
        String result = report.getAiResult();
        if (result == null || result.isEmpty()) {
            result = (report.getAiPrediction() >= 0.5f) ? "Parkinson's Detected" : "Healthy";
        }
        shareText.append("Result: ").append(result).append("\n");
        shareText.append("Confidence: ").append((int)(report.getAiPrediction() * 100)).append("%\n\n");

        // Parse and add Video Analysis if available
        String adviceText = report.getAdviceText();
        if (adviceText != null && !adviceText.isEmpty()) {
            String[] lines = adviceText.split("\n");
            for (String line : lines) {
                if (line.startsWith("VIDEO_ANALYSIS|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 5) {
                        shareText.append("📹 VIDEO ANALYSIS:\n");
                        shareText.append("  Diagnosis: ").append(parts[1]).append("\n");
                        shareText.append("  Risk Level: ").append(parts[2]).append("\n");
                        shareText.append("  Symptoms: ").append(parts[3]).append("\n");
                        shareText.append("  Recommendation: ").append(parts[4]).append("\n\n");
                    }
                } else if (line.startsWith("AUDIO_ANALYSIS|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 5) {
                        shareText.append("🎤 AUDIO ANALYSIS:\n");
                        shareText.append("  Diagnosis: ").append(parts[1]).append("\n");
                        shareText.append("  Risk Level: ").append(parts[2]).append("\n");
                        shareText.append("  Symptoms: ").append(parts[3]).append("\n");
                        shareText.append("  Recommendation: ").append(parts[4]).append("\n\n");
                    }
                }
            }
        }

        // Severity
        if (report.getSeverityText() != null && !report.getSeverityText().isEmpty()) {
            shareText.append("Severity: ").append(report.getSeverityText()).append("\n");
        }

        // Doctor Verification Status
        String verification = report.getDoctorVerification();
        if (verification != null && !verification.isEmpty()) {
            shareText.append("Doctor Status: ").append(verification).append("\n");
        }

        // Doctor Notes if available
        if (report.getDoctorNotes() != null && !report.getDoctorNotes().isEmpty()) {
            shareText.append("\nDoctor Notes: ").append(report.getDoctorNotes()).append("\n");
        }

        shareText.append("\n--- Generated by Parkinson's Disease Detection System ---");

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Parkinson's Disease Detection Report");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());
        startActivity(Intent.createChooser(shareIntent, "Share Report via"));
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this).setTitle("Delete Report").setMessage("Are you sure you want to delete this report permanently?")
                .setPositiveButton("Delete", (dialog, which) -> performDelete()).setNegativeButton("Cancel", null).show();
    }
    private void performDelete() {
        FirebaseRealtimeRepository.getInstance().deleteReport(report.getId(), new FirebaseRealtimeRepository.ReportCallback() {
            @Override public void onSuccess(Report r) { deleteLocal(); }
            @Override public void onFailure(Exception e) { deleteLocal(); }
        });
    }
    private void deleteLocal() {
        localRepository.deleteReport(report.getId(), () -> runOnUiThread(() -> { 
            Toast.makeText(this, "Report deleted successfully", Toast.LENGTH_SHORT).show(); 
            finish(); 
        }));
    }
    @Override protected void onDestroy() { super.onDestroy(); if (mediaPlayer != null) mediaPlayer.release(); }
}