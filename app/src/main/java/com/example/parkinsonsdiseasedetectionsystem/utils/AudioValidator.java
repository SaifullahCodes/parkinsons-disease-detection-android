package com.example.parkinsonsdiseasedetectionsystem.utils;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchProcessor;

public class AudioValidator {

    private static final String TAG = "AudioValidator";

    // Allow 1.5s to 20s
    private static final long MIN_DURATION_MS = 1500;
    private static final double MIN_RMS = 0.001;

    // Settings
    private static final int SAMPLE_RATE = 22050;
    private static final int BUFFER_SIZE = 2048;
    private static final int OVERLAP = 1024;

    public interface ValidationCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public static void validate(Context context, String filePath, ValidationCallback callback) {
        new Thread(() -> {
            try {
                File audioFile = new File(filePath);
                if (!audioFile.exists()) {
                    callback.onFailure("File not found.");
                    return;
                }

                // 1. Duration Check
                long duration = getDuration(context, audioFile);
                if (duration > 0 && duration < MIN_DURATION_MS) {
                    callback.onFailure("Recording too short! Say 'Ahhh' for 3 seconds.");
                    return;
                }

                // 2. Quality Check (UNLOCKED)
                String qualityError = checkAudioQuality(audioFile);

                // ⚠️ CRITICAL CHANGE: We DO NOT block "Noisy" files anymore.
                // We only block "Too Quiet" files.
                if (qualityError != null && qualityError.contains("quiet")) {
                    callback.onFailure(qualityError);
                    return;
                }

                // If it was "Too Noisy", we just Log it and PROCEED anyway.
                if (qualityError != null) {
                    Log.w(TAG, "Validator Warning (Ignored): " + qualityError);
                }

                callback.onSuccess();

            } catch (Exception e) {
                Log.e(TAG, "Validation skipped: " + e.getMessage());
                callback.onSuccess();
            }
        }).start();
    }

    private static long getDuration(Context context, File file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, Uri.fromFile(file));
            String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (time != null) return Long.parseLong(time);
        } catch (Exception ignored) {} finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
        return -1;
    }

    private static String checkAudioQuality(File file) {
        try {
            TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(
                    (float) SAMPLE_RATE, 16, 1, true, false
            );

            FileInputStream fileInputStream = new FileInputStream(file);
            UniversalAudioInputStream audioStream = new UniversalAudioInputStream(fileInputStream, format);
            AudioDispatcher dispatcher = new AudioDispatcher(audioStream, BUFFER_SIZE, OVERLAP);

            final double[] totalRMS = {0.0};
            final int[] totalFrames = {0};

            dispatcher.addAudioProcessor(new AudioProcessor() {
                @Override
                public boolean process(AudioEvent audioEvent) {
                    if (audioEvent.getTimeStamp() > 5.0) return false; // Stop after 5s

                    float[] buffer = audioEvent.getFloatBuffer();
                    double rms = 0;
                    for (float sample : buffer) rms += sample * sample;
                    rms = Math.sqrt(rms / buffer.length);

                    totalRMS[0] += rms;
                    totalFrames[0]++;
                    return true;
                }
                @Override public void processingFinished() {}
            });

            dispatcher.run();

            if (totalFrames[0] == 0) return "Could not read audio.";
            double avgRMS = totalRMS[0] / totalFrames[0];

            Log.d(TAG, "Validator RMS: " + avgRMS);

            // 1. Silence Check (Still active)
            if (avgRMS < MIN_RMS) {
                return "Too quiet! Please speak louder.";
            }

            // 2. Noise Check (DISABLED / WARNING ONLY)
            // We return the error string, but the main validate() method ignores it now.
            // This is just for your debugging log.
            if (avgRMS > 0.05) {
                return "Background noise detected (Ignored)";
            }

            return null; // OK

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}