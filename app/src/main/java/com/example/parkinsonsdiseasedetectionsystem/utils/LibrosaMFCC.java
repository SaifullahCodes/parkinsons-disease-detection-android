package com.example.parkinsonsdiseasedetectionsystem.utils;

public class LibrosaMFCC {

    private final int nMfcc;
    private final int nMels;
    private final int nFft;
    private final int hopLength;
    private final float sampleRate;

    private final float[][] melFilterBank;

    public LibrosaMFCC(int nMfcc, int nMels, int nFft, int hopLength, float sampleRate) {
        this.nMfcc = nMfcc;
        this.nMels = nMels;
        this.nFft = nFft;
        this.hopLength = hopLength;
        this.sampleRate = sampleRate;
        this.melFilterBank = createMelFilterBank();
    }

    // ===================== MAIN ENTRY =====================
    public float[][] process(float[] signal) {

        float[][] frames = frameSignal(signal);
        applyHannWindow(frames);

        float[][] powerSpec = powerSpectrum(frames);
        float[][] melSpec = applyMelFilters(powerSpec);
        log10(melSpec);

        return dct(melSpec);
    }

    // ===================== FRAMING =====================
    private float[][] frameSignal(float[] signal) {
        int numFrames = 1 + (signal.length - nFft) / hopLength;
        float[][] frames = new float[numFrames][nFft];

        for (int i = 0; i < numFrames; i++) {
            System.arraycopy(signal, i * hopLength, frames[i], 0, nFft);
        }
        return frames;
    }

    // ===================== HANN WINDOW =====================
    private void applyHannWindow(float[][] frames) {
        for (int i = 0; i < frames.length; i++) {
            for (int j = 0; j < nFft; j++) {
                frames[i][j] *= 0.5f - 0.5f * Math.cos(2.0 * Math.PI * j / nFft);
            }
        }
    }

    // ===================== FFT → POWER =====================
    private float[][] powerSpectrum(float[][] frames) {
        int bins = nFft / 2 + 1;
        float[][] power = new float[frames.length][bins];

        for (int i = 0; i < frames.length; i++) {
            float[] real = new float[nFft];
            float[] imag = new float[nFft];
            System.arraycopy(frames[i], 0, real, 0, nFft);

            fft(real, imag);

            for (int k = 0; k < bins; k++) {
                power[i][k] = real[k] * real[k] + imag[k] * imag[k];
            }
        }
        return power;
    }

    // ===================== MEL FILTER =====================
    private float[][] applyMelFilters(float[][] powerSpec) {
        float[][] mel = new float[powerSpec.length][nMels];

        for (int i = 0; i < powerSpec.length; i++) {
            for (int m = 0; m < nMels; m++) {
                float sum = 0;
                for (int k = 0; k < powerSpec[i].length; k++) {
                    sum += powerSpec[i][k] * melFilterBank[m][k];
                }
                mel[i][m] = sum;
            }
        }
        return mel;
    }

    // ===================== LOG SCALE =====================
    private void log10(float[][] mel) {
        float amin = 1e-10f;
        for (int i = 0; i < mel.length; i++) {
            for (int j = 0; j < mel[i].length; j++) {
                mel[i][j] = 10f * (float) Math.log10(Math.max(mel[i][j], amin));
            }
        }
    }

    // ===================== DCT-II =====================
    private float[][] dct(float[][] logMel) {
        int frames = logMel.length;
        float[][] mfcc = new float[nMfcc][frames];

        for (int i = 0; i < frames; i++) {
            for (int k = 0; k < nMfcc; k++) {
                float sum = 0;
                for (int n = 0; n < nMels; n++) {
                    sum += logMel[i][n] *
                            Math.cos(Math.PI * k * (n + 0.5) / nMels);
                }
                mfcc[k][i] = sum;
            }
        }
        return mfcc;
    }

    // ===================== MEL FILTER BANK =====================
    private float[][] createMelFilterBank() {
        int bins = nFft / 2 + 1;
        float[][] filters = new float[nMels][bins];

        float melMin = hzToMel(0);
        float melMax = hzToMel(sampleRate / 2);

        float[] melPoints = linspace(melMin, melMax, nMels + 2);
        float[] hzPoints = new float[melPoints.length];
        int[] bin = new int[melPoints.length];

        for (int i = 0; i < melPoints.length; i++) {
            hzPoints[i] = melToHz(melPoints[i]);
            bin[i] = (int) Math.floor((nFft + 1) * hzPoints[i] / sampleRate);
        }

        for (int m = 1; m <= nMels; m++) {
            for (int k = bin[m - 1]; k < bin[m]; k++)
                filters[m - 1][k] = (float) (k - bin[m - 1]) / (bin[m] - bin[m - 1]);

            for (int k = bin[m]; k < bin[m + 1]; k++)
                filters[m - 1][k] = (float) (bin[m + 1] - k) / (bin[m + 1] - bin[m]);
        }

        return filters;
    }

    private float hzToMel(float hz) {
        return (float) (2595 * Math.log10(1 + hz / 700));
    }

    private float melToHz(float mel) {
        return (float) (700 * (Math.pow(10, mel / 2595) - 1));
    }

    private float[] linspace(float start, float end, int count) {
        float[] out = new float[count];
        float step = (end - start) / (count - 1);
        for (int i = 0; i < count; i++) out[i] = start + step * i;
        return out;
    }

    // ===================== FFT =====================
    private void fft(float[] real, float[] imag) {
        int n = real.length;
        int j = 0;

        for (int i = 0; i < n; i++) {
            if (i < j) {
                float tr = real[i]; real[i] = real[j]; real[j] = tr;
                float ti = imag[i]; imag[i] = imag[j]; imag[j] = ti;
            }
            int m = n / 2;
            while (m >= 1 && j >= m) {
                j -= m;
                m >>= 1;
            }
            j += m;
        }

        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            float wlenR = (float) Math.cos(ang);
            float wlenI = (float) Math.sin(ang);

            for (int i = 0; i < n; i += len) {
                float wr = 1, wi = 0;
                for (int k = 0; k < len / 2; k++) {
                    int u = i + k;
                    int v = i + k + len / 2;

                    float tr = wr * real[v] - wi * imag[v];
                    float ti = wr * imag[v] + wi * real[v];

                    real[v] = real[u] - tr;
                    imag[v] = imag[u] - ti;
                    real[u] += tr;
                    imag[u] += ti;

                    float nextWr = wr * wlenR - wi * wlenI;
                    wi = wr * wlenI + wi * wlenR;
                    wr = nextWr;
                }
            }
        }
    }
}
