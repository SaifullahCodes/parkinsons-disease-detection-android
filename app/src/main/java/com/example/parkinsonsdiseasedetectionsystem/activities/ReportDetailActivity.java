package com.example.parkinsonsdiseasedetectionsystem.activities;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.example.parkinsonsdiseasedetectionsystem.models.Submission;
import com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ReportDetailActivity extends AppCompatActivity {

    private static final String TAG = "ReportDetailActivity";

    // Views
    private TextView tvPatientName, tvResult, tvType, tvSummary;
    private TextView tvVideoResultBadge, tvVideoRiskLevel, tvVideoSymptoms, tvVideoRecommendation;
    private TextView tvAudioResultBadge, tvAudioRiskLevel, tvAudioSymptoms, tvAudioRecommendation;
    private MaterialAutoCompleteTextView etSeverity;
    private TextInputEditText etDiagnosis, etAdvice, etDoctorNotes;
    private MaterialButton btnVerify;
    private com.google.android.material.card.MaterialCardView cardVideoAnalysis, cardAudioAnalysis;

    // 🎵 NEW: Audio Player Views
    private MaterialButton btnPlayAudio;
    private MaterialButton btnViewVideo;
    private MediaPlayer mediaPlayer;
    private boolean isPlaying = false;

    private LocalRepository localRepository;
    private Report report;
    private Submission submission;
    private String reportId;
    private String submissionId;
    private boolean isCreatingFromSubmission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_detail);

        // Handle insets
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            MaterialToolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar != null) toolbar.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

        localRepository = LocalRepository.getInstance(getApplicationContext());
        reportId = getIntent().getStringExtra("REPORT_ID");
        submissionId = getIntent().getStringExtra("SUBMISSION_ID");

        setupToolbar();
        bindViews();
        setupSeverityDropdown();

        if (!TextUtils.isEmpty(submissionId)) {
            isCreatingFromSubmission = true;
            loadSubmissionAndCreateReport();
        } else if (!TextUtils.isEmpty(reportId)) {
            loadReport();
        } else {
            Toast.makeText(this, "No report or submission found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }
    
    @Override
    public void onBackPressed() {
        // Simply go back to previous activity
        super.onBackPressed();
    }

    private void bindViews() {
        tvPatientName = findViewById(R.id.patientNameText);
        tvResult = findViewById(R.id.aiResultText);
        tvType = findViewById(R.id.recordingTypeText);
        tvSummary = findViewById(R.id.tvSummary);

        // Video analysis views
        cardVideoAnalysis = findViewById(R.id.cardVideoAnalysis);
        tvVideoResultBadge = findViewById(R.id.tvVideoResultBadge);
        tvVideoRiskLevel = findViewById(R.id.tvVideoRiskLevel);
        tvVideoSymptoms = findViewById(R.id.tvVideoSymptoms);
        tvVideoRecommendation = findViewById(R.id.tvVideoRecommendation);

        // Audio analysis views
        cardAudioAnalysis = findViewById(R.id.cardAudioAnalysis);
        tvAudioResultBadge = findViewById(R.id.tvAudioResultBadge);
        tvAudioRiskLevel = findViewById(R.id.tvAudioRiskLevel);
        tvAudioSymptoms = findViewById(R.id.tvAudioSymptoms);
        tvAudioRecommendation = findViewById(R.id.tvAudioRecommendation);

        etSeverity = findViewById(R.id.etSeverity);
        etDiagnosis = findViewById(R.id.etDiagnosis);
        etAdvice = findViewById(R.id.etAdvice);
        etDoctorNotes = findViewById(R.id.etDoctorNotes);

        btnVerify = findViewById(R.id.verifyAndSendBtn);
        btnVerify.setOnClickListener(v -> saveDoctorReview());

        // 🎵 NEW: Bind Play Button and Video Button
        btnPlayAudio = findViewById(R.id.btnPlayAudio);
        
        // Add video viewing capability if needed (button can be added to layout if required)
        // For now, video can be accessed through file path if needed
    }

    private void loadSubmissionAndCreateReport() {
        submission = localRepository.getSubmissionByIdSync(submissionId);
        if (submission == null) {
            Toast.makeText(this, "Submission not found", Toast.LENGTH_SHORT).show();
            finish(); return;
        }

        com.example.parkinsonsdiseasedetectionsystem.models.User patient =
                localRepository.getUserByIdSync(submission.getUserId());
        String patientName = patient != null ? patient.getName() : "Unknown Patient";

        Report existingReport = localRepository.getReportBySubmissionIdSync(submissionId);

        if (existingReport != null) {
            report = existingReport;
        } else {
            float aiPrediction = 0.5f;
            String aiResult = "Healthy";

            List<Report> userReports = localRepository.getReportsForUserSync(submission.getUserId());
            if (userReports != null && !userReports.isEmpty()) {
                float totalPrediction = 0f;
                for (Report r : userReports) {
                    if (r.getAiPrediction() > 0) totalPrediction += r.getAiPrediction();
                }
                if (totalPrediction > 0) {
                    aiPrediction = totalPrediction / userReports.size();
                    aiResult = aiPrediction >= 0.5f ? "Parkinson's Detected" : "Healthy";
                }
            }

            report = new Report(
                    java.util.UUID.randomUUID().toString(),
                    submission.getUserId(),
                    patientName,
                    "voice",
                    "",
                    aiPrediction
            );
            report.setAiResult(aiResult);
            report.setSubmissionId(submissionId);
            report.setSummaryText(submission.getSummaryText());
            report.setDoctorVerification("Pending");
            report.setCreatedAt(System.currentTimeMillis());
            report.setDoctorId(AuthUtils.getUserId(this));
        }
        displayReportDetails();
    }

    private void loadReport() {
        localRepository.getReportById(reportId, fetched -> {
            if (fetched == null) {
                // Try loading from Firebase if not found locally
                com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository.getInstance()
                    .getReportById(reportId, new com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository.ReportCallback() {
                        @Override
                        public void onSuccess(Report firebaseReport) {
                            report = firebaseReport;
                            // Save locally for offline access
                            if (report != null) {
                                new Thread(() -> localRepository.insertReport(report)).start();
                            }
                            runOnUiThread(() -> displayReportDetails());
                        }
                        
                        @Override
                        public void onFailure(Exception e) {
                            runOnUiThread(() -> {
                                Toast.makeText(ReportDetailActivity.this, "Report not found", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }
                    });
            } else {
                report = fetched;
                displayReportDetails();
            }
        });
    }

    private void displayReportDetails() {
        tvPatientName.setText("Patient: " + report.getPatientName());
        tvResult.setText("AI Result: " + report.getAiResult());
        tvType.setText("Recording Type: " + report.getRecordingType());
        tvSummary.setText("Summary: " + (TextUtils.isEmpty(report.getSummaryText()) ? "--" : report.getSummaryText()));

        // Display detailed AI analysis
        displayDetailedAnalysis();

        // Populate doctor review fields if already filled
        if (!TextUtils.isEmpty(report.getSeverityText())) etSeverity.setText(report.getSeverityText(), false);
        if (!TextUtils.isEmpty(report.getDiagnosisText())) etDiagnosis.setText(report.getDiagnosisText());
        
        // Extract doctor's advice from adviceText (separate from AI analysis)
        String adviceText = report.getAdviceText();
        if (adviceText != null && !adviceText.isEmpty()) {
            // Check if there's a DOCTOR_ADVICE section
            if (adviceText.contains("DOCTOR_ADVICE|")) {
                String[] parts = adviceText.split("DOCTOR_ADVICE\\|");
                if (parts.length > 1) {
                    etAdvice.setText(parts[1].trim());
                }
            } else if (!adviceText.startsWith("VIDEO_ANALYSIS|") && 
                      !adviceText.startsWith("AUDIO_ANALYSIS|") && 
                      !adviceText.startsWith("COMBINED_RESULT|")) {
                // If adviceText doesn't contain structured AI data, it might be doctor's advice
                etAdvice.setText(adviceText);
            }
        }
        
        if (!TextUtils.isEmpty(report.getDoctorNotes())) etDoctorNotes.setText(report.getDoctorNotes());

        // 🎵 NEW: Setup Audio Player
        setupAudioPlayer();
    }

    private void displayDetailedAnalysis() {
        if (report == null) return;

        String adviceText = report.getAdviceText();
        String recordingType = report.getRecordingType();
        
        // Hide both cards initially
        cardVideoAnalysis.setVisibility(android.view.View.GONE);
        cardAudioAnalysis.setVisibility(android.view.View.GONE);

        boolean videoShown = false;
        boolean audioShown = false;

        if (adviceText != null && !adviceText.isEmpty()) {
            // Parse the concise format: TYPE|DIAGNOSIS|RISK%|SYMPTOMS|RECOMMENDATION
            String[] lines = adviceText.split("\n");
            
            for (String line : lines) {
                if (line.startsWith("VIDEO_ANALYSIS|")) {
                    displayVideoAnalysisForDoctor(line);
                    videoShown = true;
                } else if (line.startsWith("AUDIO_ANALYSIS|")) {
                    displayAudioAnalysisForDoctor(line);
                    audioShown = true;
                } else if (line.startsWith("ANALYSIS|")) {
                    // Legacy format
                    if (recordingType != null && recordingType.contains("video")) {
                        displayFallbackVideoForDoctor();
                        videoShown = true;
                    } else {
                        displayFallbackAudioForDoctor();
                        audioShown = true;
                    }
                }
            }
        } else {
            // Fallback based on recording type
            if (recordingType != null && recordingType.contains("video")) {
                displayFallbackVideoForDoctor();
                videoShown = true;
            } else if (recordingType != null && recordingType.contains("audio")) {
                displayFallbackAudioForDoctor();
                audioShown = true;
            }
        }

        // Update constraints dynamically based on what's shown
        androidx.constraintlayout.widget.ConstraintLayout parent = (androidx.constraintlayout.widget.ConstraintLayout) cardVideoAnalysis.getParent();
        if (parent != null) {
            // Update lblDoctorReview position based on what's visible
            android.view.View lblDoctorReview = findViewById(R.id.lblDoctorReview);
            if (lblDoctorReview != null) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = 
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) lblDoctorReview.getLayoutParams();
                
                if (videoShown && audioShown) {
                    // Both shown - position below audio
                    params.topToBottom = cardAudioAnalysis.getId();
                    params.topToTop = -1;
                } else if (videoShown) {
                    // Only video - position below video
                    params.topToBottom = cardVideoAnalysis.getId();
                    params.topToTop = -1;
                } else if (audioShown) {
                    // Only audio - position below audio
                    params.topToBottom = cardAudioAnalysis.getId();
                    params.topToTop = -1;
                } else {
                    // None shown - position below reportCard
                    params.topToBottom = findViewById(R.id.reportCard).getId();
                    params.topToTop = -1;
                }
                lblDoctorReview.setLayoutParams(params);
            }
            
            // Update audio card position
            if (audioShown) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams audioParams = 
                    (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) cardAudioAnalysis.getLayoutParams();
                if (videoShown) {
                    // Audio below video
                    audioParams.topToBottom = cardVideoAnalysis.getId();
                    audioParams.topToTop = -1;
                } else {
                    // Audio below reportCard
                    audioParams.topToBottom = findViewById(R.id.reportCard).getId();
                    audioParams.topToTop = -1;
                }
                cardAudioAnalysis.setLayoutParams(audioParams);
            }
        }
    }

    private void displayVideoAnalysisForDoctor(String line) {
        String[] parts = line.split("\\|");
        if (parts.length >= 5) {
            cardVideoAnalysis.setVisibility(android.view.View.VISIBLE);
            
            String diagnosis = parts[1];
            String riskLevel = parts[2];
            String symptoms = parts[3];
            String recommendation = parts[4];
            
            // Set badge color
            int badgeColor = diagnosis.contains("Parkinson") || diagnosis.contains("Detected") 
                ? android.graphics.Color.parseColor("#EF4444") : android.graphics.Color.parseColor("#10B981");
            tvVideoResultBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(badgeColor));
            tvVideoResultBadge.setText(diagnosis.contains("Parkinson") ? "Detected" : "Healthy");
            
            tvVideoRiskLevel.setText("Risk Level: " + riskLevel);
            tvVideoSymptoms.setText("Symptoms: " + (symptoms.isEmpty() || symptoms.equals("None detected") ? "None detected" : symptoms));
            tvVideoRecommendation.setText(recommendation);
        }
    }

    private void displayAudioAnalysisForDoctor(String line) {
        String[] parts = line.split("\\|");
        if (parts.length >= 5) {
            cardAudioAnalysis.setVisibility(android.view.View.VISIBLE);
            
            String diagnosis = parts[1];
            String riskLevel = parts[2];
            String symptoms = parts[3];
            String recommendation = parts[4];
            
            // Set badge color
            int badgeColor = diagnosis.contains("Parkinson") || diagnosis.contains("Detected") 
                ? android.graphics.Color.parseColor("#EF4444") : android.graphics.Color.parseColor("#10B981");
            tvAudioResultBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(badgeColor));
            tvAudioResultBadge.setText(diagnosis.contains("Parkinson") ? "Detected" : "Healthy");
            
            tvAudioRiskLevel.setText("Risk Level: " + riskLevel);
            tvAudioSymptoms.setText("Symptoms: " + (symptoms.isEmpty() || symptoms.equals("None detected") ? "None detected" : symptoms));
            tvAudioRecommendation.setText(recommendation);
        }
    }

    private void displayFallbackVideoForDoctor() {
        cardVideoAnalysis.setVisibility(android.view.View.VISIBLE);
        String result = report.getAiResult() != null ? report.getAiResult() : "Unknown";
        int risk = (int)(report.getAiPrediction() * 100);
        
        int badgeColor = result.contains("Parkinson") ? android.graphics.Color.parseColor("#EF4444") : android.graphics.Color.parseColor("#10B981");
        tvVideoResultBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(badgeColor));
        tvVideoResultBadge.setText(result.contains("Parkinson") ? "Detected" : "Healthy");
        
        tvVideoRiskLevel.setText("Risk Level: " + risk + "%");
        tvVideoSymptoms.setText("Symptoms: " + (report.getSeverityText() != null ? report.getSeverityText() : "Not available"));
        tvVideoRecommendation.setText("Consult a neurologist for comprehensive evaluation.");
    }

    private void displayFallbackAudioForDoctor() {
        cardAudioAnalysis.setVisibility(android.view.View.VISIBLE);
        String result = report.getAiResult() != null ? report.getAiResult() : "Unknown";
        int risk = (int)(report.getAiPrediction() * 100);
        
        int badgeColor = result.contains("Parkinson") ? android.graphics.Color.parseColor("#EF4444") : android.graphics.Color.parseColor("#10B981");
        tvAudioResultBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(badgeColor));
        tvAudioResultBadge.setText(result.contains("Parkinson") ? "Detected" : "Healthy");
        
        tvAudioRiskLevel.setText("Risk Level: " + risk + "%");
        tvAudioSymptoms.setText("Symptoms: " + (report.getSeverityText() != null ? report.getSeverityText() : "Not available"));
        tvAudioRecommendation.setText("Consult a neurologist for comprehensive evaluation.");
    }

    // 🎵 NEW: Audio Player Logic
    private void setupAudioPlayer() {
        String filePath = report.getFilePath();
        String videoPath = report.getVideoUrl();

        // Setup audio player
        if (filePath == null || filePath.isEmpty() || filePath.equals("No Audio")) {
            btnPlayAudio.setVisibility(View.GONE);
        } else {
            File audioFile = new File(filePath);
            if (!audioFile.exists()) {
                // Try external files directory
                File externalDir = getExternalFilesDir(null);
                if (externalDir != null) {
                    String fileName = audioFile.getName();
                    File altFile = new File(externalDir, fileName);
                    if (altFile.exists()) {
                        filePath = altFile.getAbsolutePath();
                        report.setFilePath(filePath);
                    }
                }
            }
            
            // Create final variable for lambda expression
            final String finalFilePath = filePath;
            File audioFile2 = new File(finalFilePath);
            if (!audioFile2.exists()) {
                btnPlayAudio.setText("⚠ Audio File Missing");
                btnPlayAudio.setEnabled(false);
                btnPlayAudio.setVisibility(View.VISIBLE);
            } else {
                btnPlayAudio.setVisibility(View.VISIBLE);
                btnPlayAudio.setOnClickListener(v -> {
                    if (isPlaying) stopAudio();
                    else playAudio(finalFilePath);
                });
            }
        }
        
        // Note: Video viewing can be added if needed - for now, video path is available in report.getVideoUrl()
        // Doctors can access video through the file path if required
    }

    private void playAudio(String filePath) {
        try {
            if (mediaPlayer == null) mediaPlayer = new MediaPlayer();
            else mediaPlayer.reset();

            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();
            mediaPlayer.start();

            isPlaying = true;
            btnPlayAudio.setText("⏹ Stop Audio");
            btnPlayAudio.setIconResource(android.R.drawable.ic_media_pause);

            mediaPlayer.setOnCompletionListener(mp -> stopAudio());
            Toast.makeText(this, "Playing Audio...", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            Log.e(TAG, "Audio Error: " + e.getMessage());
            Toast.makeText(this, "Error playing audio", Toast.LENGTH_SHORT).show();
            stopAudio();
        }
    }

    private void stopAudio() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.reset();
        }
        isPlaying = false;
        btnPlayAudio.setText("▶ Play Recording");
        btnPlayAudio.setIconResource(android.R.drawable.ic_media_play);
    }

    private void setupSeverityDropdown() {
        String[] severityOptions = {"Low", "Moderate", "High"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, severityOptions);
        etSeverity.setAdapter(adapter);
        etSeverity.setOnItemClickListener((parent, view, position, id) -> {
            etSeverity.setText(severityOptions[position], false);
            etSeverity.clearFocus();
        });
    }

    private void saveDoctorReview() {
        if (report == null) return;

        String severity = etSeverity.getText() != null ? etSeverity.getText().toString().trim() : "";
        String diagnosis = etDiagnosis.getText() != null ? etDiagnosis.getText().toString().trim() : "";
        String advice = etAdvice.getText() != null ? etAdvice.getText().toString().trim() : "";
        String notes = etDoctorNotes.getText() != null ? etDoctorNotes.getText().toString().trim() : "";

        if (TextUtils.isEmpty(severity)) { etSeverity.setError("Required"); return; }
        if (TextUtils.isEmpty(diagnosis)) { etDiagnosis.setError("Required"); return; }

        float originalAiPrediction = report.getAiPrediction();
        String originalAiResult = report.getAiResult();

        // Save doctor's review fields
        report.setSeverityText(severity);
        report.setDiagnosisText(diagnosis);
        
        // IMPORTANT: Preserve original AI analysis in adviceText, append doctor's advice separately
        String originalAdvice = report.getAdviceText();
        StringBuilder finalAdviceText = new StringBuilder();
        
        // Keep original AI analysis if it exists (VIDEO_ANALYSIS, AUDIO_ANALYSIS, etc.)
        if (originalAdvice != null && !originalAdvice.isEmpty()) {
            String[] lines = originalAdvice.split("\n");
            boolean hasStructuredAnalysis = false;
            
            for (String line : lines) {
                if (line.startsWith("VIDEO_ANALYSIS|") || 
                    line.startsWith("AUDIO_ANALYSIS|") || 
                    line.startsWith("COMBINED_RESULT|")) {
                    // Preserve structured AI analysis
                    if (finalAdviceText.length() > 0) finalAdviceText.append("\n");
                    finalAdviceText.append(line);
                    hasStructuredAnalysis = true;
                } else if (!line.startsWith("DOCTOR_ADVICE|")) {
                    // Keep other analysis lines, but remove old DOCTOR_ADVICE
                    if (hasStructuredAnalysis || finalAdviceText.length() == 0) {
                        if (finalAdviceText.length() > 0) finalAdviceText.append("\n");
                        finalAdviceText.append(line);
                    }
                }
            }
            
            // Append doctor's advice if provided
            if (advice != null && !advice.trim().isEmpty()) {
                if (finalAdviceText.length() > 0) finalAdviceText.append("\n\n");
                finalAdviceText.append("DOCTOR_ADVICE|").append(advice.trim());
            }
            
            report.setAdviceText(finalAdviceText.toString());
        } else {
            // No original AI analysis, just use doctor's advice
            report.setAdviceText(advice != null ? advice.trim() : "");
        }
        
        report.setDoctorNotes(notes);
        report.setDoctorVerification("Doctor Verified");
        report.setDoctorId(AuthUtils.getUserId(this));
        report.setDoctorName(AuthUtils.getUserName(this));
        report.setVerifiedAt(System.currentTimeMillis());
        
        // Preserve original AI prediction and result
        report.setAiPrediction(originalAiPrediction);
        if (originalAiResult != null && !originalAiResult.isEmpty()) {
            report.setAiResult(originalAiResult);
        }

        com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository firebaseRepo =
                com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository.getInstance();

        if (isCreatingFromSubmission) {
            Report existingReport = localRepository.getReportBySubmissionIdSync(submissionId);
            if (existingReport != null) report.setId(existingReport.getId());
        }

        firebaseRepo.updateReport(report, new com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository.ReportCallback() {
            @Override
            public void onSuccess(Report savedReport) {
                saveLocally(savedReport);
                runOnUiThread(() -> {
                    Toast.makeText(ReportDetailActivity.this, "Verified & Sent", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
            @Override
            public void onFailure(Exception e) {
                saveLocally(report);
                runOnUiThread(() -> {
                    Toast.makeText(ReportDetailActivity.this, "Verified (Offline Mode)", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void saveLocally(Report reportToSave) {
        if (isCreatingFromSubmission) {
            Report existingReport = localRepository.getReportBySubmissionIdSync(submissionId);
            if (existingReport != null) {
                reportToSave.setId(existingReport.getId());
                localRepository.updateReportByDoctor(reportToSave, null);
            } else {
                localRepository.insertReport(reportToSave);
            }
            if (submission != null) {
                submission.setStatus("reviewed");
                localRepository.updateSubmission(submission);
            }
        } else {
            localRepository.updateReportByDoctor(reportToSave, null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}