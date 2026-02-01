# AudioValidator Integration Example

## Overview
The `AudioValidator` class has been created and integrated into `UserHomeFragment.java`. This document shows how to use it in other parts of your app, including `MainActivity.java`.

## Integration in UserHomeFragment (Already Done)
The validator is already integrated in `UserHomeFragment.handleAIAnalysis()` method. It validates audio before calling `performRealAIAnalysis()`.

## Example: Using AudioValidator in MainActivity

If you want to add audio validation in `MainActivity.java` (e.g., in a button click listener), here's how:

```java
package com.example.parkinsonsdiseasedetectionsystem.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.parkinsonsdiseasedetectionsystem.R;
import com.example.parkinsonsdiseasedetectionsystem.utils.AudioValidator;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainActivity extends AppCompatActivity {
    
    private Button btnValidateAudio;
    private String audioFilePath; // Path to your audio file
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        btnValidateAudio = findViewById(R.id.btnValidateAudio);
        
        btnValidateAudio.setOnClickListener(v -> {
            // Check if audio file path is set
            if (audioFilePath == null || audioFilePath.isEmpty()) {
                Toast.makeText(this, "Please select an audio file first", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Show loading state
            btnValidateAudio.setEnabled(false);
            btnValidateAudio.setText("Validating...");
            
            // Validate audio file
            AudioValidator.validate(this, audioFilePath, new AudioValidator.ValidationCallback() {
                @Override
                public void onSuccess() {
                    // Validation passed
                    runOnUiThread(() -> {
                        btnValidateAudio.setEnabled(true);
                        btnValidateAudio.setText("Validate Audio");
                        
                        // Show success dialog
                        new MaterialAlertDialogBuilder(MainActivity.this)
                                .setTitle("Validation Successful")
                                .setMessage("Audio file meets all quality requirements. Ready for analysis!")
                                .setPositiveButton("OK", (dialog, which) -> {
                                    dialog.dismiss();
                                    // Proceed with AI analysis or other operations
                                })
                                .setIcon(android.R.drawable.checkbox_on_background)
                                .show();
                    });
                }
                
                @Override
                public void onFailure(String errorMessage) {
                    // Validation failed - show error dialog
                    runOnUiThread(() -> {
                        btnValidateAudio.setEnabled(true);
                        btnValidateAudio.setText("Validate Audio");
                        
                        // Show error dialog with validation failure message
                        new MaterialAlertDialogBuilder(MainActivity.this)
                                .setTitle("Audio Validation Failed")
                                .setMessage(errorMessage)
                                .setPositiveButton("OK", (dialog, which) -> {
                                    dialog.dismiss();
                                })
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .show();
                    });
                }
            });
        });
    }
}
```

## Validation Checks Performed

1. **Duration Check:**
   - Minimum: 2.5 seconds
   - Maximum: 7.0 seconds
   - Error messages:
     - "Recording too short! Keep saying 'Ahhh' for at least 3 seconds."
     - "Recording too long! Please limit it to 5-6 seconds."

2. **RMS (Root Mean Square) Check:**
   - Threshold: RMS >= 0.005
   - Detects if audio is too quiet (silence)
   - Error message: "Audio too quiet. Please speak louder and hold the 'Ahhh' sound steadily."

3. **Pitch Probability Check:**
   - Uses YIN algorithm via PitchProcessor
   - Requires at least 40% of frames to have pitch probability > 0.5
   - Error message: "Voice not clear. Please hold the 'Ahhh' sound steadily."

## Current Integration

The `AudioValidator` is already integrated in:
- **UserHomeFragment.java** - `handleAIAnalysis()` method
  - Validates audio before calling `performRealAIAnalysis()`
  - Shows MaterialAlertDialog on validation failure
  - Proceeds with AI analysis on success

## Usage Pattern

```java
AudioValidator.validate(context, filePath, new AudioValidator.ValidationCallback() {
    @Override
    public void onSuccess() {
        // Proceed with processing
    }
    
    @Override
    public void onFailure(String errorMessage) {
        // Show error to user
    }
});
```











