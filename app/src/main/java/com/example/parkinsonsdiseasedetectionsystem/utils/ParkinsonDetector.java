package com.example.parkinsonsdiseasedetectionsystem.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class ParkinsonDetector {

    private static final String TAG = "ParkinsonDetector";
    private static final String MODEL_FILE = "parkinsons_mfcc_model.tflite";

    private static final int SAMPLE_RATE = 22050;
    private static final int MFCC_COEFFICIENTS = 40;
    private static final int MEL_FILTERS = 128;
    private static final int FFT_SIZE = 2048;
    private static final int HOP_LENGTH = 512;
    private static final int INPUT_SIZE = 40;

    private static final float SILENCE_THRESHOLD_RMS = 0.005f;

    // 🔧 CALIBRATION: Aligns Android Volume with Python Training Data
    // Verified to match Python's Librosa output accurately.
    private static final float VOLUME_CALIBRATION_OFFSET = -215.0f;

    // HARDCODED SCALER VALUES (From Training)
    private final float[] HARDCODED_MEAN = {
            -233.2317f, 208.9925f, -69.9622f, -17.9128f, 0.9711f, -39.0326f, 13.3299f, 9.8185f, -26.9655f, 4.2565f,
            3.4152f, -15.0431f, 2.5659f, -6.5641f, -15.3706f, -0.0399f, -7.5988f, -9.1477f, 1.3807f, -7.7014f,
            -6.0665f, 1.1060f, -7.2774f, -3.6193f, 0.3223f, -6.4589f, -1.1921f, 0.3751f, -4.3454f, 1.9299f,
            1.5272f, -2.0412f, 3.4904f, 1.2664f, -0.5769f, 4.8429f, 1.4796f, -0.0748f, 3.3247f, -0.4863f
    };

    private final float[] HARDCODED_SCALE = {
            43.7917f, 25.0983f, 27.4724f, 15.4113f, 15.2996f, 14.5486f, 14.3481f, 11.4235f, 9.4172f, 11.0349f,
            7.4851f, 9.0696f, 8.5412f, 8.5436f, 6.6308f, 7.9040f, 6.6109f, 6.1784f, 7.3238f, 6.2431f,
            5.4420f, 6.4011f, 6.6904f, 6.8973f, 8.4257f, 8.7997f, 8.7925f, 9.0570f, 10.1634f, 11.9492f,
            12.0493f, 11.4711f, 12.2120f, 12.8154f, 12.5421f, 12.3669f, 12.2604f, 12.1008f, 11.7856f, 11.2625f
    };

    private Context context;
    private Interpreter tflite;

    public interface DetectionCallback {
        void onDetectionComplete(float probability, boolean isParkinsonDetected, String error);
    }

    public ParkinsonDetector(Context context) {
        this.context = context;
        initializeModel();
    }

    private void initializeModel() {
        try {
            tflite = new Interpreter(loadModelFile());
        } catch (IOException e) {
            Log.e(TAG, "Error loading model", e);
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetManager assetManager = context.getAssets();
        InputStream inputStream = assetManager.open(MODEL_FILE);
        File tempFile = File.createTempFile("model", ".tflite", context.getCacheDir());
        FileOutputStream outputStream = new FileOutputStream(tempFile);
        byte[] buffer = new byte[4 * 1024];
        int read;
        while ((read = inputStream.read(buffer)) != -1) outputStream.write(buffer, 0, read);
        outputStream.flush();
        outputStream.close();
        inputStream.close();
        FileInputStream fileInputStream = new FileInputStream(tempFile);
        FileChannel fileChannel = fileInputStream.getChannel();
        MappedByteBuffer bufferData = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size());
        fileInputStream.close();
        tempFile.delete();
        return bufferData;
    }

    public void detectFromAudioFile(String audioFilePath, DetectionCallback callback) {
        if (tflite == null) {
            callback.onDetectionComplete(0.0f, false, "Model not loaded");
            return;
        }

        new Thread(() -> {
            try {
                // 1. Extract MFCCs (Using Local Librosa Implementation)
                float[][] mfccFeatures = extractMFCC(audioFilePath);

                if (mfccFeatures == null || mfccFeatures.length == 0) {
                    callback.onDetectionComplete(0.02f, false, null); // Silence -> Healthy
                    return;
                }

                // 2. Mean Calculation
                float[] meanMFCC = calculateMeanMFCC(mfccFeatures);

                // 3. APPLY CALIBRATION
                meanMFCC[0] += VOLUME_CALIBRATION_OFFSET;

                // 4. Normalize
                float[] normalized = normalizeFeatures(meanMFCC);

                // 5. Inference
                float probability = runInference(normalized);
                boolean isParkinson = probability >= 0.5f;

                callback.onDetectionComplete(probability, isParkinson, null);

            } catch (Exception e) {
                callback.onDetectionComplete(0.0f, false, "Error: " + e.getMessage());
            }
        }).start();
    }

    private float[][] extractMFCC(String audioFilePath) throws Exception {
        AudioDecoder decoder = new AudioDecoder();
        float[] samples = decoder.decode(audioFilePath, SAMPLE_RATE);
        if (samples == null || samples.length == 0) return null;

        if (calculateRMS(samples) < SILENCE_THRESHOLD_RMS) return null;

        LibrosaMFCC mfccCalculator = new LibrosaMFCC(MFCC_COEFFICIENTS, MEL_FILTERS, FFT_SIZE, HOP_LENGTH, SAMPLE_RATE);
        return mfccCalculator.process(samples);
    }

    private float calculateRMS(float[] samples) {
        double sum = 0;
        for (float s : samples) sum += s * s;
        return (float) Math.sqrt(sum / samples.length);
    }

    private float[] calculateMeanMFCC(float[][] mfccFrames) {
        int n_mfcc = mfccFrames.length;
        int time_steps = mfccFrames[0].length;
        float[] mean = new float[INPUT_SIZE];
        for (int i = 0; i < n_mfcc && i < INPUT_SIZE; i++) {
            float sum = 0;
            for (int t = 0; t < time_steps; t++) sum += mfccFrames[i][t];
            mean[i] = sum / time_steps;
        }
        return mean;
    }

    private float[] normalizeFeatures(float[] features) {
        float[] normalized = new float[INPUT_SIZE];
        for (int i = 0; i < INPUT_SIZE; i++) {
            float scale = (HARDCODED_SCALE[i] == 0) ? 1.0f : HARDCODED_SCALE[i];
            normalized[i] = (features[i] - HARDCODED_MEAN[i]) / scale;
        }
        return normalized;
    }

    private float runInference(float[] features) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(INPUT_SIZE * 4);
        buffer.order(ByteOrder.nativeOrder());
        for (float f : features) buffer.putFloat(f);
        float[][] output = new float[1][1];
        tflite.run(buffer, output);
        return Math.max(0.0f, Math.min(1.0f, output[0][0]));
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
    }
}