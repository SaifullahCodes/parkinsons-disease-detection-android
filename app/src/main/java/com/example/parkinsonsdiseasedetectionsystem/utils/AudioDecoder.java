package com.example.parkinsonsdiseasedetectionsystem.utils;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class AudioDecoder {

    private static final int TIMEOUT_US = 10000;

    public float[] decode(String path, int targetSampleRate) throws IOException {

        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(path);

        int trackIndex = selectAudioTrack(extractor);
        if (trackIndex < 0) throw new IOException("No audio track");

        extractor.selectTrack(trackIndex);
        MediaFormat format = extractor.getTrackFormat(trackIndex);

        String mime = format.getString(MediaFormat.KEY_MIME);
        int srcSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();

        List<Float> pcm = new ArrayList<>();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        boolean done = false;
        while (!done) {

            int inIndex = codec.dequeueInputBuffer(TIMEOUT_US);
            if (inIndex >= 0) {
                ByteBuffer buffer = codec.getInputBuffer(inIndex);
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    codec.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
                    extractor.advance();
                }
            }

            int outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US);
            if (outIndex >= 0) {

                ByteBuffer buffer = codec.getOutputBuffer(outIndex);
                buffer.order(ByteOrder.LITTLE_ENDIAN);

                short[] chunk = new short[info.size / 2];
                buffer.asShortBuffer().get(chunk);

                for (int i = 0; i < chunk.length; i += channelCount) {
                    float sum = 0;
                    for (int c = 0; c < channelCount; c++) {
                        sum += chunk[i + c];
                    }
                    pcm.add((sum / channelCount) / 32768f);
                }

                codec.releaseOutputBuffer(outIndex, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) done = true;
            }
        }

        codec.stop();
        codec.release();
        extractor.release();

        float[] samples = toArray(pcm);

        // ✅ LIBROSA PEAK NORMALIZATION (MANDATORY)
        float max = 0f;
        for (float v : samples) max = Math.max(max, Math.abs(v));
        if (max > 0f) {
            for (int i = 0; i < samples.length; i++) {
                samples[i] /= max;
            }
        }

        return resample(samples, srcSampleRate, targetSampleRate);
    }

    private int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) return i;
        }
        return -1;
    }

    private float[] resample(float[] input, int src, int dst) {
        if (src == dst) return input;
        int len = (int) ((long) input.length * dst / src);
        float[] out = new float[len];
        double ratio = (double) input.length / len;

        for (int i = 0; i < len; i++) {
            double idx = i * ratio;
            int i0 = (int) idx;
            int i1 = Math.min(i0 + 1, input.length - 1);
            double frac = idx - i0;
            out[i] = (float) ((1 - frac) * input[i0] + frac * input[i1]);
        }
        return out;
    }

    private float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }
}
