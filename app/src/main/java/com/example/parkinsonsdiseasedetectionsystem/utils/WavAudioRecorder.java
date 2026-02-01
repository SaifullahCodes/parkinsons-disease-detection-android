package com.example.parkinsonsdiseasedetectionsystem.utils;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Utility class for recording audio directly to WAV format
 * Uses AudioRecord to capture raw PCM audio and writes WAV header
 */
public class WavAudioRecorder {
    private static final String TAG = "WavAudioRecorder";
    
    // Audio recording parameters
    private static final int SAMPLE_RATE = 22050;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BYTES_PER_SAMPLE = 2; // 16-bit = 2 bytes
    
    private AudioRecord audioRecord;
    private Thread recordingThread;
    private boolean isRecording = false;
    private File outputFile;
    private int bufferSize;
    
    /**
     * Start recording to WAV file
     * @param outputFile File to save WAV recording
     * @return true if recording started successfully
     */
    public boolean startRecording(File outputFile) {
        if (isRecording) {
            Log.w(TAG, "Already recording");
            return false;
        }
        
        this.outputFile = outputFile;
        
        // Calculate buffer size
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size");
            return false;
        }
        
        // Create AudioRecord
        try {
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            );
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed");
                audioRecord.release();
                audioRecord = null;
                return false;
            }
            
            audioRecord.startRecording();
            isRecording = true;
            
            // Start recording thread
            recordingThread = new Thread(new RecordingRunnable());
            recordingThread.start();
            
            Log.d(TAG, "Recording started to: " + outputFile.getAbsolutePath());
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording: " + e.getMessage(), e);
            if (audioRecord != null) {
                audioRecord.release();
                audioRecord = null;
            }
            return false;
        }
    }
    
    /**
     * Stop recording
     */
    public void stopRecording() {
        if (!isRecording) {
            return;
        }
        
        isRecording = false;
        
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error stopping AudioRecord: " + e.getMessage());
            }
        }
        
        // Wait for recording thread to finish
        if (recordingThread != null) {
            try {
                recordingThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for recording thread");
            }
        }
        
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
        
        Log.d(TAG, "Recording stopped");
    }
    
    /**
     * Check if currently recording
     */
    public boolean isRecording() {
        return isRecording;
    }
    
    /**
     * Runnable that handles audio recording in background thread
     */
    private class RecordingRunnable implements Runnable {
        @Override
        public void run() {
            FileOutputStream outputStream = null;
            byte[] buffer = new byte[bufferSize];
            long totalBytes = 0;
            
            try {
                outputStream = new FileOutputStream(outputFile);
                
                // Write WAV header placeholder (will be updated later)
                WavHeader header = new WavHeader(SAMPLE_RATE, 1, BYTES_PER_SAMPLE);
                outputStream.write(header.getHeader());
                totalBytes += header.getHeader().length;
                
                // Read audio data and write to file
                while (isRecording && audioRecord != null) {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                    } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e(TAG, "Invalid operation error");
                        break;
                    } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "Bad value error");
                        break;
                    }
                }
                
                // Update WAV header with actual file size
                outputStream.close();
                updateWavHeader(outputFile, totalBytes - header.getHeader().length);
                
                Log.d(TAG, "Recording completed. Total bytes: " + totalBytes);
                
            } catch (IOException e) {
                Log.e(TAG, "Error writing audio data: " + e.getMessage(), e);
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing output stream: " + e.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * Update WAV header with actual data size
     */
    private void updateWavHeader(File file, long dataSize) {
        try {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "rw");
            
            // Update file size (bytes 4-7)
            raf.seek(4);
            raf.writeInt((int) (dataSize + 36)); // 36 = header size - 8
            
            // Update data chunk size (bytes 40-43)
            raf.seek(40);
            raf.writeInt((int) dataSize);
            
            raf.close();
            
        } catch (IOException e) {
            Log.e(TAG, "Error updating WAV header: " + e.getMessage(), e);
        }
    }
    
    /**
     * WAV file header structure
     */
    private static class WavHeader {
        private static final int HEADER_SIZE = 44;
        private byte[] header;
        
        WavHeader(int sampleRate, int channels, int bytesPerSample) {
            header = new byte[HEADER_SIZE];
            
            // RIFF header
            writeString(header, 0, "RIFF");
            writeInt(header, 4, 0); // File size (will be updated later)
            writeString(header, 8, "WAVE");
            
            // Format chunk
            writeString(header, 12, "fmt ");
            writeInt(header, 16, 16); // Format chunk size
            writeShort(header, 20, (short) 1); // Audio format (1 = PCM)
            writeShort(header, 22, (short) channels); // Number of channels
            writeInt(header, 24, sampleRate); // Sample rate
            writeInt(header, 28, sampleRate * channels * bytesPerSample); // Byte rate
            writeShort(header, 32, (short) (channels * bytesPerSample)); // Block align
            writeShort(header, 34, (short) (bytesPerSample * 8)); // Bits per sample
            
            // Data chunk
            writeString(header, 36, "data");
            writeInt(header, 40, 0); // Data size (will be updated later)
        }
        
        byte[] getHeader() {
            return header;
        }
        
        private void writeString(byte[] buffer, int offset, String value) {
            byte[] bytes = value.getBytes();
            System.arraycopy(bytes, 0, buffer, offset, bytes.length);
        }
        
        private void writeInt(byte[] buffer, int offset, int value) {
            buffer[offset] = (byte) (value & 0xFF);
            buffer[offset + 1] = (byte) ((value >> 8) & 0xFF);
            buffer[offset + 2] = (byte) ((value >> 16) & 0xFF);
            buffer[offset + 3] = (byte) ((value >> 24) & 0xFF);
        }
        
        private void writeShort(byte[] buffer, int offset, short value) {
            buffer[offset] = (byte) (value & 0xFF);
            buffer[offset + 1] = (byte) ((value >> 8) & 0xFF);
        }
    }
}











