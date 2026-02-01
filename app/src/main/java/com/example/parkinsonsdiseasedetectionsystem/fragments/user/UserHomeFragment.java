package com.example.parkinsonsdiseasedetectionsystem.fragments.user;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.FirebaseRealtimeRepository;
import com.example.parkinsonsdiseasedetectionsystem.data.repository.LocalRepository;
import com.example.parkinsonsdiseasedetectionsystem.models.Report;
import com.example.parkinsonsdiseasedetectionsystem.utils.AuthUtils;
import com.example.parkinsonsdiseasedetectionsystem.utils.WavAudioRecorder;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class UserHomeFragment extends Fragment {

    // ========================================================================================
    // ✅ CLOUD URLs
    // ========================================================================================
    private static final String AUDIO_API_URL = "https://parkinson-api-4so8.onrender.com/predict/";
    private static final String VIDEO_API_URL = "https://saifullahn-parkinson-video-api.hf.space/";

    // CONSTANTS
    private static final int REQUEST_PERMISSION = 100;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 101;
    private static final int REQUEST_SELECT_VOICE = 201;
    private static final int REQUEST_SELECT_VIDEO = 202;
    private static final int REQUEST_RECORD_VIDEO = 203;

    // UI COMPONENTS
    private TextView tvWelcome;
    private Button btnRecordVoice, btnRecordVideo, btnUploadVoice, btnUploadVideo;
    private Button btnRunAI, btnSendDoctor;
    private CardView cardStatus, cardResult, cardAudioResult, cardVideoResult;
    private ImageView imgStatus, imgResultIcon, imgAudioResultIcon, imgVideoResultIcon;
    private TextView tvStatus, tvResultStatus, tvResultConfidence;
    private TextView tvAudioResultStatus, tvAudioResultConfidence, tvAudioResultDetails;
    private TextView tvVideoResultStatus, tvVideoResultConfidence, tvVideoResultDetails;

    // TIMER & RECORDERS
    private CountDownTimer recordingTimer;
    private WavAudioRecorder wavRecorder;

    // FILES
    private File currentVoiceFile;
    private File currentVideoFile;

    // AI & DATA
    private float aiPrediction = -1; // -1 means no result yet

    // Separate storage for combined analysis
    private String videoAnalysisText = "";
    private String audioAnalysisText = "";
    private boolean videoAnalysisComplete = false;
    private boolean audioAnalysisComplete = false;
    private boolean videoAnalysisFailed = false;
    private boolean audioAnalysisFailed = false;

    // Final report fields
    private String summaryText = "";
    private String severityText = "";
    private String diagnosisText = "";
    private String adviceText = "";

    private LocalRepository localRepository;
    private String currentUserId, currentUserName;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        localRepository = LocalRepository.getInstance(requireContext());
        currentUserId = AuthUtils.getUserId(requireContext());
        currentUserName = AuthUtils.getUserName(requireContext());

        new Thread(() -> {
            if (getContext() != null) {
                localRepository.ensureUserRecord(getContext());
            }
        }).start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.user_fragment_home, container, false);
        initializeViews(view);
        setupClickListeners();
        checkPermissions();
        setWelcomeMessage();
        updateUIState();
        return view;
    }

    private void initializeViews(View view) {
        tvWelcome = view.findViewById(R.id.tvWelcome);
        btnRecordVoice = view.findViewById(R.id.btnRecordVoice);
        btnRecordVideo = view.findViewById(R.id.btnRecordVideo);
        btnUploadVoice = view.findViewById(R.id.btnUploadVoice);
        btnUploadVideo = view.findViewById(R.id.btnUploadVideo);
        btnRunAI = view.findViewById(R.id.btnRunAI);
        btnSendDoctor = view.findViewById(R.id.btnSendDoctor);
        cardStatus = view.findViewById(R.id.cardStatus);
        imgStatus = view.findViewById(R.id.imgStatus);
        tvStatus = view.findViewById(R.id.tvStatus);

        cardResult = view.findViewById(R.id.cardResult);
        imgResultIcon = view.findViewById(R.id.imgResultIcon);
        tvResultStatus = view.findViewById(R.id.tvResultStatus);
        tvResultConfidence = view.findViewById(R.id.tvResultConfidence);
        
        // Audio result card views
        cardAudioResult = view.findViewById(R.id.cardAudioResult);
        imgAudioResultIcon = view.findViewById(R.id.imgAudioResultIcon);
        tvAudioResultStatus = view.findViewById(R.id.tvAudioResultStatus);
        tvAudioResultConfidence = view.findViewById(R.id.tvAudioResultConfidence);
        tvAudioResultDetails = view.findViewById(R.id.tvAudioResultDetails);
        
        // Video result card views
        cardVideoResult = view.findViewById(R.id.cardVideoResult);
        imgVideoResultIcon = view.findViewById(R.id.imgVideoResultIcon);
        tvVideoResultStatus = view.findViewById(R.id.tvVideoResultStatus);
        tvVideoResultConfidence = view.findViewById(R.id.tvVideoResultConfidence);
        tvVideoResultDetails = view.findViewById(R.id.tvVideoResultDetails);
    }

    private void setupClickListeners() {
        btnRecordVoice.setOnClickListener(v -> handleVoiceRecording());
        btnRecordVideo.setOnClickListener(v -> handleVideoRecording());
        btnUploadVoice.setOnClickListener(v -> handleVoiceUpload());
        btnUploadVideo.setOnClickListener(v -> handleVideoUpload());
        btnRunAI.setOnClickListener(v -> handleAIAnalysis());
        btnSendDoctor.setOnClickListener(v -> handleSendToDoctor());
    }

    private void setWelcomeMessage() {
        tvWelcome.setText("Welcome, " + currentUserName);
    }

    private void updateUIState() {
        boolean hasAudio = (currentVoiceFile != null);
        boolean hasVideo = (currentVideoFile != null);

        // Update Button Text based on what is loaded
        if (hasAudio && hasVideo) {
            btnRunAI.setText("Analyze Both (Audio + Video)");
        } else if (hasVideo) {
            btnRunAI.setText("Analyze Video");
        } else if (hasAudio) {
            btnRunAI.setText("Analyze Audio");
        } else {
            btnRunAI.setText("Analyze with AI");
        }

        // Enable AI button if ANY file is present
        boolean readyToAnalyze = hasAudio || hasVideo;
        btnRunAI.setEnabled(readyToAnalyze);
        btnRunAI.setAlpha(readyToAnalyze ? 1.0f : 0.5f);

        // Enable Send to Doctor ONLY if AI analysis is complete (prediction >= 0)
        boolean hasResult = (aiPrediction >= 0);
        btnSendDoctor.setEnabled(hasResult);
        btnSendDoctor.setAlpha(hasResult ? 1.0f : 0.5f);

        // Only show "Ready" status if we haven't analyzed yet
        if (!readyToAnalyze) {
            showStatus("Ready: Please record Voice or Video", "info");
        } else if (!hasResult) {
            showStatus("Files Loaded. Tap 'Analyze' to proceed.", "info");
        }
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), permissions, REQUEST_PERMISSION);
                return;
            }
        }
    }

    // ==========================================
    // RECORDING & UPLOAD LOGIC
    // ==========================================

    private void handleVoiceRecording() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }
        startVoiceRecording();
    }

    private void startVoiceRecording() {
        try {
            resetAIResults(); // Clear previous results
            File storageDir = requireContext().getExternalFilesDir(null);
            currentVoiceFile = new File(storageDir, "recorded_voice_" + System.currentTimeMillis() + ".wav");

            wavRecorder = new WavAudioRecorder();
            if (!wavRecorder.startRecording(currentVoiceFile)) {
                Toast.makeText(requireContext(), "Error starting recorder", Toast.LENGTH_SHORT).show();
                return;
            }

            btnRecordVoice.setEnabled(false);
            btnRecordVoice.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_dark));
            showStatus("🎤 Recording... Please say 'Ahhh'", "recording");

            if (recordingTimer != null) recordingTimer.cancel();
            recordingTimer = new CountDownTimer(5000, 1000) {
                @Override
                public void onTick(long millis) {
                    btnRecordVoice.setText("00:0" + millis / 1000);
                }
                @Override
                public void onFinish() {
                    btnRecordVoice.setText("Done");
                    stopVoiceRecording();
                }
            }.start();

        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            btnRecordVoice.setEnabled(true);
        }
    }

    private void stopVoiceRecording() {
        try {
            if (wavRecorder != null) {
                wavRecorder.stopRecording();
                wavRecorder = null;
            }
            btnRecordVoice.setEnabled(true);
            btnRecordVoice.setText("Record");
            btnRecordVoice.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.colorPrimary));
            showStatus("✓ Voice saved", "success");
            updateUIState();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- UPLOAD LOGIC ---
    private void handleVoiceUpload() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select Audio"), REQUEST_SELECT_VOICE);
    }

    private void handleVideoUpload() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select Video"), REQUEST_SELECT_VIDEO);
    }

    private void handleVideoRecording() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 20);
        startActivityForResult(intent, REQUEST_RECORD_VIDEO);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();

        if (requestCode == REQUEST_SELECT_VOICE) {
            currentVoiceFile = copyUriToInternalStorage(uri, "uploaded_voice_" + System.currentTimeMillis() + ".wav");
            resetAIResults(); // Clear old results
            showStatus("✓ Voice loaded", "success");
        }
        else if (requestCode == REQUEST_SELECT_VIDEO || requestCode == REQUEST_RECORD_VIDEO) {
            currentVideoFile = copyUriToInternalStorage(uri, "uploaded_video_" + System.currentTimeMillis() + ".mp4");
            resetAIResults(); // Clear old results
            showStatus("✓ Video attached", "success");
        }
        updateUIState();
    }

    private void resetAIResults() {
        aiPrediction = -1;
        cardResult.setVisibility(View.GONE);
        cardAudioResult.setVisibility(View.GONE);
        cardVideoResult.setVisibility(View.GONE);
        videoAnalysisText = "";
        audioAnalysisText = "";
        videoAnalysisComplete = false;
        audioAnalysisComplete = false;
        videoAnalysisFailed = false;
        audioAnalysisFailed = false;
        // Don't clear files here
    }

    private File copyUriToInternalStorage(Uri uri, String newFileName) {
        try {
            File destFile = new File(requireContext().getExternalFilesDir(null), newFileName);
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            FileOutputStream outputStream = new FileOutputStream(destFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
            inputStream.close();
            return destFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==========================================
    // 🧠 AI ANALYSIS LOGIC (CHAINED)
    // ==========================================

    private void handleAIAnalysis() {
        btnRunAI.setEnabled(false);
        cardResult.setVisibility(View.GONE);
        cardAudioResult.setVisibility(View.GONE);
        cardVideoResult.setVisibility(View.GONE);
        videoAnalysisText = "";
        audioAnalysisText = "";
        videoAnalysisComplete = false;
        audioAnalysisComplete = false;
        videoAnalysisFailed = false;
        audioAnalysisFailed = false;
        aiPrediction = -1; // Reset prediction

        if (currentVideoFile != null && currentVoiceFile != null) {
            // BOTH: Analyze in PARALLEL (separate threads)
            showStatus("☁️ Analyzing Video & Audio separately (5 min timeout)...", "analyzing");
            analyzeVideoWithCloudApi(currentVideoFile, false); // Start video
            analyzeAudioWithCloudApi(currentVoiceFile, false); // Start audio in parallel
        }
        else if (currentVideoFile != null) {
            // VIDEO ONLY
            showStatus("☁️ Analyzing Video (5 min timeout)...", "analyzing");
            analyzeVideoWithCloudApi(currentVideoFile, false);
        }
        else if (currentVoiceFile != null) {
            // AUDIO ONLY
            showStatus("☁️ Analyzing Audio (5 min timeout)...", "analyzing");
            analyzeAudioWithCloudApi(currentVoiceFile, false);
        }
        else {
            Toast.makeText(requireContext(), "No files to analyze", Toast.LENGTH_SHORT).show();
            btnRunAI.setEnabled(true);
        }
    }

    // --- VIDEO ANALYSIS ---
    private void analyzeVideoWithCloudApi(File videoFile, boolean processAudioNext) {
        // 5 minute timeout for cloud server wake-up
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(5, TimeUnit.MINUTES)
                .readTimeout(5, TimeUnit.MINUTES)
                .build();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", videoFile.getName(),
                        RequestBody.create(MediaType.parse("video/*"), videoFile))
                .build();

        Request request = new Request.Builder().url(VIDEO_API_URL).post(requestBody).build();

        new Thread(() -> {
            try {
                okhttp3.Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String jsonResponse = response.body().string();
                    requireActivity().runOnUiThread(() -> {
                        try {
                            parseVideoResult(jsonResponse);
                            videoAnalysisComplete = true;
                            displayVideoResults(); // Show video result immediately
                            
                            // Update status message
                            if (currentVoiceFile != null && !audioAnalysisComplete) {
                                showStatus("✓ Video Done. Audio still analyzing...", "success");
                            }
                            
                            checkAndFinalizeResults(); // Check if all analyses done
                        } catch (JSONException e) {
                            videoAnalysisFailed = true;
                            videoAnalysisComplete = true;
                            displayVideoError("Video Analysis Failed: " + e.getMessage());
                            
                            // Update status message
                            if (currentVoiceFile != null && !audioAnalysisComplete) {
                                showStatus("Video failed. Audio still analyzing...", "error");
                            }
                            
                            checkAndFinalizeResults(); // Check if all analyses done
                        }
                    });
                } else {
                    videoAnalysisFailed = true;
                    videoAnalysisComplete = true;
                    requireActivity().runOnUiThread(() -> {
                        String errorMsg = "Video Server Error: " + response.code();
                        displayVideoError("Analysis Failed: Server timeout (5 min exceeded) or error. Cloud server may be waking up.");
                        checkAndFinalizeResults();
                    });
                }
            } catch (IOException e) {
                videoAnalysisFailed = true;
                videoAnalysisComplete = true;
                requireActivity().runOnUiThread(() -> {
                    displayVideoError("Analysis Failed: Network timeout (5 min exceeded). Cloud server may be waking up.");
                    checkAndFinalizeResults();
                });
            }
        }).start();
    }

    private void parseVideoResult(String jsonString) throws JSONException {
        JSONObject json = new JSONObject(jsonString);
        if (json.has("error")) throw new JSONException(json.getString("error"));

        // Extract all available fields from API
        int probability = json.optInt("parkinson_probability", 0);
        if (probability == 0) {
            // Try alternative fields
            if (json.has("probability")) probability = (int)(json.getDouble("probability") * 100);
            else if (json.has("prediction")) probability = (int)(json.getDouble("prediction") * 100);
        }
        
        String reasoning = json.optString("reasoning", "");
        String clinicalInterpretation = json.optString("clinical_interpretation", "");
        String recommendation = json.optString("recommendation", "");

        // Check for bad video quality
        String lowerReasoning = reasoning.toLowerCase();
        if (lowerReasoning.contains("unclear") || lowerReasoning.contains("no subject") ||
                lowerReasoning.contains("too dark") || lowerReasoning.contains("obscured")) {
            throw new JSONException("Video quality too poor for analysis. Please record again.");
        }

        // Store temporarily
        float probFloat = probability / 100.0f;
        String status = (probFloat > 0.5) ? "Parkinson's Detected" : "Healthy";

        // Extract key symptoms and concise recommendation
        String keySymptoms = extractKeySymptoms(reasoning);
        String conciseRec = extractConciseRecommendation(recommendation, clinicalInterpretation);

        videoAnalysisText = "VIDEO_ANALYSIS|" + status + "|" + probability + "%|" + keySymptoms + "|" + conciseRec;

        // Update main prediction - use video as primary if available, otherwise audio
        if (aiPrediction < 0) {
            aiPrediction = probFloat;
        } else {
            // If both exist, take the maximum (more conservative for safety)
            aiPrediction = Math.max(aiPrediction, probFloat);
        }
    }
    
    private void displayVideoResults() {
        if (videoAnalysisText.isEmpty()) return;
        
        cardVideoResult.setVisibility(View.VISIBLE);
        String[] parts = videoAnalysisText.split("\\|");
        if (parts.length >= 5) {
            String diagnosis = parts[1];
            String riskLevel = parts[2];
            String symptoms = parts[3];
            String recommendation = parts[4];
            
            tvVideoResultStatus.setText(diagnosis);
            tvVideoResultConfidence.setText("Risk Level: " + riskLevel);
            
            StringBuilder details = new StringBuilder();
            details.append("Symptoms: ").append(symptoms.isEmpty() || symptoms.equals("None detected") ? "None detected" : symptoms).append("\n\n");
            details.append("Recommendation: ").append(recommendation);
            tvVideoResultDetails.setText(details.toString());
            
            // Set color based on diagnosis
            int color = diagnosis.contains("Parkinson") || diagnosis.contains("Detected") 
                ? android.R.color.holo_red_dark : android.R.color.holo_green_dark;
            tvVideoResultStatus.setTextColor(ContextCompat.getColor(requireContext(), color));
            imgVideoResultIcon.setColorFilter(ContextCompat.getColor(requireContext(), color));
            imgVideoResultIcon.setImageResource(diagnosis.contains("Parkinson") 
                ? android.R.drawable.stat_sys_warning : android.R.drawable.checkbox_on_background);
        }
    }
    
    private void displayVideoError(String errorMessage) {
        cardVideoResult.setVisibility(View.VISIBLE);
        tvVideoResultStatus.setText("Analysis Failed");
        tvVideoResultConfidence.setText("Error occurred");
        tvVideoResultDetails.setText(errorMessage);
        
        // Set error styling
        int errorColor = android.R.color.holo_red_dark;
        tvVideoResultStatus.setTextColor(ContextCompat.getColor(requireContext(), errorColor));
        imgVideoResultIcon.setColorFilter(ContextCompat.getColor(requireContext(), errorColor));
        imgVideoResultIcon.setImageResource(android.R.drawable.stat_notify_error);
        
        // Store error in format for report
        videoAnalysisText = "VIDEO_ANALYSIS|Analysis Failed|--|Error occurred|" + errorMessage;
    }

    // --- AUDIO ANALYSIS ---
    private void analyzeAudioWithCloudApi(File audioFile, boolean isChained) {
        // 5 minute timeout for cloud server wake-up
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.MINUTES)
                .writeTimeout(5, TimeUnit.MINUTES)
                .readTimeout(5, TimeUnit.MINUTES)
                .build();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", audioFile.getName(),
                        RequestBody.create(MediaType.parse("audio/*"), audioFile))
                .build();

        Request request = new Request.Builder().url(AUDIO_API_URL).post(requestBody).build();

        new Thread(() -> {
            try {
                okhttp3.Response response = client.newCall(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    String jsonResponse = response.body().string();
                    requireActivity().runOnUiThread(() -> {
                        try {
                            parseAudioResult(jsonResponse);
                            audioAnalysisComplete = true;
                            displayAudioResults(); // Show audio result immediately
                            
                            // Update status message
                            if (currentVideoFile != null && !videoAnalysisComplete) {
                                showStatus("✓ Audio Done. Video still analyzing...", "success");
                            }
                            
                            checkAndFinalizeResults(); // Check if all analyses done
                        } catch (JSONException e) {
                            audioAnalysisFailed = true;
                            audioAnalysisComplete = true;
                            displayAudioError("Audio Analysis Failed: " + e.getMessage());
                            
                            // Update status message
                            if (currentVideoFile != null && !videoAnalysisComplete) {
                                showStatus("Audio failed. Video still analyzing...", "error");
                            }
                            
                            checkAndFinalizeResults(); // Check if all analyses done
                        }
                    });
                } else {
                    audioAnalysisFailed = true;
                    audioAnalysisComplete = true;
                    requireActivity().runOnUiThread(() -> {
                        displayAudioError("Analysis Failed: Server timeout (5 min exceeded) or error. Cloud server may be waking up.");
                        checkAndFinalizeResults();
                    });
                }
            } catch (IOException e) {
                audioAnalysisFailed = true;
                audioAnalysisComplete = true;
                requireActivity().runOnUiThread(() -> {
                    displayAudioError("Analysis Failed: Network timeout (5 min exceeded). Cloud server may be waking up.");
                    checkAndFinalizeResults();
                });
            }
        }).start();
    }

    private void parseAudioResult(String jsonString) throws JSONException {
        JSONObject json = new JSONObject(jsonString);
        
        // Extract all available fields from API
        double prob = 0;
        if (json.has("probability")) prob = json.getDouble("probability");
        else if (json.has("parkinson_probability")) prob = json.getInt("parkinson_probability") / 100.0;
        else if (json.has("prediction")) prob = json.getDouble("prediction");
        
        String reasoning = json.optString("reasoning", "");
        String clinicalInterpretation = json.optString("clinical_interpretation", "");
        String recommendation = json.optString("recommendation", "");

        float probFloat = (float) prob;
        String status = (probFloat > 0.5) ? "Parkinson's Detected" : "Healthy";

        // Extract key symptoms and concise recommendation
        String keySymptoms = extractKeySymptoms(reasoning);
        String conciseRec = extractConciseRecommendation(recommendation, clinicalInterpretation);

        audioAnalysisText = "AUDIO_ANALYSIS|" + status + "|" + (int)(probFloat * 100) + "%|" + keySymptoms + "|" + conciseRec;

        // Update main prediction - use maximum if both exist
        if (aiPrediction < 0) {
            aiPrediction = probFloat;
        } else {
            // If both exist, take the maximum (more conservative for safety)
            aiPrediction = Math.max(aiPrediction, probFloat);
        }
    }
    
    private void displayAudioResults() {
        if (audioAnalysisText.isEmpty()) return;
        
        cardAudioResult.setVisibility(View.VISIBLE);
        String[] parts = audioAnalysisText.split("\\|");
        if (parts.length >= 5) {
            String diagnosis = parts[1];
            String riskLevel = parts[2];
            String symptoms = parts[3];
            String recommendation = parts[4];
            
            tvAudioResultStatus.setText(diagnosis);
            tvAudioResultConfidence.setText("Risk Level: " + riskLevel);
            
            StringBuilder details = new StringBuilder();
            details.append("Symptoms: ").append(symptoms.isEmpty() || symptoms.equals("None detected") ? "None detected" : symptoms).append("\n\n");
            details.append("Recommendation: ").append(recommendation);
            tvAudioResultDetails.setText(details.toString());
            
            // Set color based on diagnosis
            int color = diagnosis.contains("Parkinson") || diagnosis.contains("Detected") 
                ? android.R.color.holo_red_dark : android.R.color.holo_green_dark;
            tvAudioResultStatus.setTextColor(ContextCompat.getColor(requireContext(), color));
            imgAudioResultIcon.setColorFilter(ContextCompat.getColor(requireContext(), color));
            imgAudioResultIcon.setImageResource(diagnosis.contains("Parkinson") 
                ? android.R.drawable.stat_sys_warning : android.R.drawable.checkbox_on_background);
        }
    }
    
    private void displayAudioError(String errorMessage) {
        cardAudioResult.setVisibility(View.VISIBLE);
        tvAudioResultStatus.setText("Analysis Failed");
        tvAudioResultConfidence.setText("Error occurred");
        tvAudioResultDetails.setText(errorMessage);
        
        // Set error styling
        int errorColor = android.R.color.holo_red_dark;
        tvAudioResultStatus.setTextColor(ContextCompat.getColor(requireContext(), errorColor));
        imgAudioResultIcon.setColorFilter(ContextCompat.getColor(requireContext(), errorColor));
        imgAudioResultIcon.setImageResource(android.R.drawable.stat_notify_error);
        
        // Store error in format for report
        audioAnalysisText = "AUDIO_ANALYSIS|Analysis Failed|--|Error occurred|" + errorMessage;
    }

    // --- FINALIZE RESULTS (COMBINE) ---
    private void checkAndFinalizeResults() {
        // Only finalize if all requested analyses are complete (success or failure)
        boolean videoRequested = (currentVideoFile != null);
        boolean audioRequested = (currentVoiceFile != null);
        
        boolean videoDone = !videoRequested || videoAnalysisComplete;
        boolean audioDone = !audioRequested || audioAnalysisComplete;
        
        if (!videoDone || !audioDone) {
            // Still waiting for more results
            return;
        }
        
        // All requested analyses are complete, finalize now
        StringBuilder combinedAdvice = new StringBuilder();

        // Add analysis results (already formatted by displayVideoResults/displayAudioResults or displayVideoError/displayAudioError)
        if (!videoAnalysisText.isEmpty()) {
            combinedAdvice.append(videoAnalysisText).append("\n");
        }
        
        if (!audioAnalysisText.isEmpty()) {
            combinedAdvice.append(audioAnalysisText).append("\n");
        }

        // Determine final status and prediction
        if (aiPrediction < 0) {
            // No successful analysis - cannot proceed, don't enable send button
            btnRunAI.setEnabled(true);
            showStatus("Analysis failed. Please try again.", "error");
            updateUIState();
            return;
        }
        
        String finalStatus = (aiPrediction > 0.5) ? "Parkinson's Detected" : "Healthy";
        int finalConf = (int) (aiPrediction * 100);

        // Only add combined result if both analyses succeeded
        if (!videoAnalysisText.isEmpty() && !audioAnalysisText.isEmpty() && 
            !videoAnalysisText.contains("Analysis Failed") && !audioAnalysisText.contains("Analysis Failed")) {
            combinedAdvice.append("COMBINED_RESULT|").append(finalStatus).append("|").append(finalConf).append("%");
        }

        // Set global text variables for the Report
        adviceText = combinedAdvice.toString();
        severityText = (aiPrediction > 0.5) ? "High Probability (" + finalConf + "%)" : "Low Probability (" + finalConf + "%)";
        diagnosisText = finalStatus;

        // UI Update
        btnRunAI.setEnabled(true);
        
        // Show combined result card only if no separate cards are shown
        // Otherwise, separate cards handle the display
        boolean hasSeparateCards = cardAudioResult.getVisibility() == View.VISIBLE || 
                                   cardVideoResult.getVisibility() == View.VISIBLE;
        
        if (hasSeparateCards) {
            cardResult.setVisibility(View.GONE); // Hide legacy card
        } else if (aiPrediction >= 0) {
            displayAIResults(aiPrediction); // Show legacy card if no separate cards
        }
        
        // Show appropriate completion message
        if (videoRequested && audioRequested) {
            if (videoAnalysisFailed && audioAnalysisFailed) {
                showStatus("Both analyses failed. Please try again.", "error");
            } else if (videoAnalysisFailed) {
                showStatus("✓ Audio Analysis Complete. Video analysis failed.", "success");
            } else if (audioAnalysisFailed) {
                showStatus("✓ Video Analysis Complete. Audio analysis failed.", "success");
            } else {
                showStatus("✓ Both Analyses Complete", "success");
            }
        } else if (videoRequested) {
            if (videoAnalysisFailed) {
                showStatus("Video analysis failed. Please try again.", "error");
            } else {
                showStatus("✓ Video Analysis Complete", "success");
            }
        } else if (audioRequested) {
            if (audioAnalysisFailed) {
                showStatus("Audio analysis failed. Please try again.", "error");
            } else {
                showStatus("✓ Audio Analysis Complete", "success");
            }
        }
        
        updateUIState(); // Enables "Send to Doctor" if prediction >= 0
    }

    // ==========================================
    // UI HELPERS
    // ==========================================

    private void displayAIResults(float prediction) {
        cardResult.setVisibility(View.VISIBLE);
        int percent = (int) (prediction * 100);

        tvResultStatus.setText(diagnosisText);
        tvResultConfidence.setText("Risk Level: " + percent + "%");

        int color = diagnosisText.contains("Parkinson") ? android.R.color.holo_red_dark : android.R.color.holo_green_dark;
        tvResultStatus.setTextColor(ContextCompat.getColor(requireContext(), color));
        imgResultIcon.setColorFilter(ContextCompat.getColor(requireContext(), color));
        imgResultIcon.setImageResource(diagnosisText.contains("Healthy") || diagnosisText.contains("Normal") ? android.R.drawable.checkbox_on_background : android.R.drawable.stat_sys_warning);
    }

    private void showError(String message) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                btnRunAI.setEnabled(true);
                showStatus(message, "error");
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            });
        }
    }

    /**
     * Extract only key symptoms (max 3 most important) from reasoning text
     */
    private String extractKeySymptoms(String reasoning) {
        if (reasoning == null || reasoning.isEmpty()) return "None detected";
        
        String lower = reasoning.toLowerCase();
        StringBuilder keySymptoms = new StringBuilder();
        
        // Priority order: most clinically significant first
        if (lower.contains("tremor") || lower.contains("shaking")) {
            keySymptoms.append("Tremors");
        }
        if (lower.contains("gait") || lower.contains("shuffling") || lower.contains("walking")) {
            if (keySymptoms.length() > 0) keySymptoms.append(", ");
            keySymptoms.append("Gait Issues");
        }
        if (lower.contains("bradykinesia") || lower.contains("slow movement") || lower.contains("slowness")) {
            if (keySymptoms.length() > 0) keySymptoms.append(", ");
            keySymptoms.append("Bradykinesia");
        }
        if (keySymptoms.length() == 0 && lower.contains("rigidity")) {
            keySymptoms.append("Rigidity");
        }
        if (keySymptoms.length() == 0 && (lower.contains("voice") || lower.contains("speech"))) {
            keySymptoms.append("Speech Changes");
        }
        
        return keySymptoms.length() > 0 ? keySymptoms.toString() : "None detected";
    }

    /**
     * Extract concise recommendation (max 150 chars) from recommendation and clinical interpretation
     */
    private String extractConciseRecommendation(String recommendation, String clinicalInterpretation) {
        String rec = "";
        
        // Try to extract from clinical interpretation first (usually more concise)
        if (clinicalInterpretation != null && !clinicalInterpretation.isEmpty()) {
            rec = clinicalInterpretation.length() > 150 
                ? clinicalInterpretation.substring(0, 147) + "..." 
                : clinicalInterpretation;
        } else if (recommendation != null && !recommendation.isEmpty()) {
            // Extract recommendation from advice text
            String lower = recommendation.toLowerCase();
            if (lower.contains("consult") || lower.contains("neurologist") || lower.contains("doctor")) {
                rec = "Consult a neurologist for comprehensive evaluation.";
            } else if (lower.contains("monitor") || lower.contains("follow")) {
                rec = "Monitor symptoms and follow up if they persist.";
            } else {
                rec = recommendation.length() > 150 
                    ? recommendation.substring(0, 147) + "..." 
                    : recommendation;
            }
        }
        
        return rec.isEmpty() ? "Consult a neurologist for comprehensive evaluation." : rec;
    }

    private void showStatus(String msg, String type) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            cardStatus.setVisibility(View.VISIBLE);
            tvStatus.setText(msg);
            int color = type.equals("error") ? android.R.color.holo_red_light : android.R.color.holo_green_light;
            if (type.equals("analyzing") || type.equals("recording")) color = android.R.color.holo_blue_light;
            cardStatus.setCardBackgroundColor(ContextCompat.getColor(requireContext(), color));
        });
    }

    // ==========================================
    // 📤 SEND REPORT LOGIC
    // ==========================================
    private void handleSendToDoctor() {
        // Allow sending if analysis is done (prediction >= 0)
        if (aiPrediction < 0) {
            Toast.makeText(requireContext(), "Please analyze data first.", Toast.LENGTH_SHORT).show();
            return;
        }
        showSummaryInputDialog();
    }

    private void showSummaryInputDialog() {
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_summary_input, null);
        TextInputEditText et = v.findViewById(R.id.etSymptomSummary);
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Send Report").setView(v)
                .setPositiveButton("Send", (d, w) -> {
                    summaryText = et.getText().toString();
                    uploadAndSaveReport(summaryText);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void uploadAndSaveReport(String summary) {
        showStatus("☁️ Sending Report...", "analyzing");
        btnSendDoctor.setEnabled(false);

        // Ensure files are saved to permanent storage with report-specific names
        String reportId = UUID.randomUUID().toString();
        String localVideoPath = "No Video";
        String localAudioPath = "";

        // Copy audio file to permanent storage if exists
        if (currentVoiceFile != null && currentVoiceFile.exists()) {
            try {
                File permanentAudioFile = new File(
                    requireContext().getExternalFilesDir(null),
                    "REPORT_" + reportId + "_AUDIO.wav"
                );
                copyFile(currentVoiceFile, permanentAudioFile);
                localAudioPath = permanentAudioFile.getAbsolutePath();
            } catch (Exception e) {
                // Fallback to original path
                localAudioPath = currentVoiceFile.getAbsolutePath();
            }
        }

        // Copy video file to permanent storage if exists
        if (currentVideoFile != null && currentVideoFile.exists()) {
            try {
                File permanentVideoFile = new File(
                    requireContext().getExternalFilesDir(null),
                    "REPORT_" + reportId + "_VIDEO.mp4"
                );
                copyFile(currentVideoFile, permanentVideoFile);
                localVideoPath = permanentVideoFile.getAbsolutePath();
            } catch (Exception e) {
                // Fallback to original path
                localVideoPath = currentVideoFile.getAbsolutePath();
            }
        }

        // Determine Recording Type
        String recordingType;
        if (currentVideoFile != null && currentVoiceFile != null) recordingType = "video_audio";
        else if (currentVideoFile != null) recordingType = "video";
        else recordingType = "audio";

        saveReportToDatabase(summary, localVideoPath, localAudioPath, recordingType, reportId);
    }
    
    private void copyFile(File sourceFile, File destFile) throws IOException {
        if (!sourceFile.exists()) {
            throw new IOException("Source file does not exist: " + sourceFile.getAbsolutePath());
        }
        
        java.io.InputStream in = new java.io.FileInputStream(sourceFile);
        java.io.OutputStream out = new java.io.FileOutputStream(destFile);
        
        byte[] buffer = new byte[1024];
        int length;
        while ((length = in.read(buffer)) > 0) {
            out.write(buffer, 0, length);
        }
        
        in.close();
        out.close();
    }

    private void saveReportToDatabase(String summary, String localVideoPath, String voicePath, String type, String reportId) {
        if (localRepository == null) return;

        localRepository.createSubmissionWithDoctorAssignmentCallback(currentUserId, summary, "Doctor", subId -> {

            Report r = new Report(
                    reportId, currentUserId, currentUserName, type, voicePath, aiPrediction
            );

            r.setSubmissionId(subId);
            r.setSummaryText(summary);
            r.setSeverityText(severityText);
            r.setDiagnosisText(diagnosisText);
            r.setAiResult(diagnosisText); // Set AI result for display
            r.setAdviceText(adviceText); // Contains both audio/video analysis text in concise format (TYPE|DIAGNOSIS|RISK%|SYMPTOMS|RECOMMENDATION)
            r.setDoctorVerification("Pending");
            r.setCreatedAt(System.currentTimeMillis());
            
            // Set video URL (stores local path or "No Video")
            r.setVideoUrl(localVideoPath);
            
            // Note: Both video and audio analysis are stored in adviceText in concise format:
            // - VIDEO_ANALYSIS|... or AUDIO_ANALYSIS|... or both
            // These can be parsed in HistoryDetailsActivity and ReportDetailActivity

            // Save Local
            final Report localReport = r;
            new Thread(() -> localRepository.insertReport(localReport)).start();

            // Save Firebase
            FirebaseRealtimeRepository.getInstance().saveReport(r, new FirebaseRealtimeRepository.ReportCallback() {
                @Override public void onSuccess(Report rep) {
                    if(getActivity()!=null) getActivity().runOnUiThread(()->{
                        Toast.makeText(requireContext(), "Report Sent Successfully!", Toast.LENGTH_LONG).show();
                        showStatus("✓ Sent! Ready for new patient.", "success");

                        // Reset UI for next patient
                        currentVoiceFile = null;
                        currentVideoFile = null;
                        resetAIResults();
                        updateUIState();
                    });
                }
                @Override public void onFailure(Exception e) {
                    if(getActivity()!=null) getActivity().runOnUiThread(()->{
                        showStatus("Database Error: " + e.getMessage(), "error");
                        btnSendDoctor.setEnabled(true);
                    });
                }
            });
        });
    }

    @Override public void onDestroy() { super.onDestroy(); if(recordingTimer!=null) recordingTimer.cancel(); }
}