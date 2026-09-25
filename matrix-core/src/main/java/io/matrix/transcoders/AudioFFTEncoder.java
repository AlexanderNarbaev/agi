package io.matrix.transcoders;

import java.util.*;

/**
 * W1201 — Pure Symbolic Audio FFT Encoder.
 *
 * Converts spectrograms to HDC vectors using symbolic frequency bands.
 * 100% explainable, no neural network dependencies.
 *
 * Path B (Research): Pure Symbolic alternative to Whisper-Tiny ONNX.
 * Gradually replaces Path A as accuracy improves.
 */
public final class AudioFFTEncoder {

    /**
     * Symbolic frequency band representation.
     */
    public record FrequencyBand(double lowHz, double highHz, double energy) {}

    /**
     * FFT-derived spectral frame.
     */
    public record SpectralFrame(
            float[] magnitudes,
            float[] phases,
            int sampleRate,
            long timestamp
    ) {}

    private final int dimension;
    private final int bandCount;
    private final Random rng;

    public AudioFFTEncoder(int dimension, int bandCount, long seed) {
        this.dimension = dimension;
        this.bandCount = bandCount;
        this.rng = new Random(seed);
    }

    /**
     * Compute simple DFT (Discrete Fourier Transform) for a frame.
     * Symbolic: O(N²) but 100% explainable.
     */
    public SpectralFrame computeDFT(float[] samples, int sampleRate) {
        int n = samples.length;
        float[] magnitudes = new float[n / 2];
        float[] phases = new float[n / 2];

        for (int k = 0; k < n / 2; k++) {
            double real = 0, imag = 0;
            for (int n2 = 0; n2 < n; n2++) {
                double angle = 2 * Math.PI * k * n2 / n;
                real += samples[n2] * Math.cos(angle);
                imag -= samples[n2] * Math.sin(angle);
            }
            magnitudes[k] = (float) Math.sqrt(real * real + imag * imag);
            phases[k] = (float) Math.atan2(imag, real);
        }
        return new SpectralFrame(magnitudes, phases, sampleRate, System.currentTimeMillis());
    }

    /**
     * Decompose spectrum into symbolic frequency bands.
     * Mimics human auditory perception: critical bands (Bark scale).
     */
    public List<FrequencyBand> extractBands(SpectralFrame frame) {
        List<FrequencyBand> bands = new ArrayList<>();
        int bins = frame.magnitudes().length;
        int binsPerBand = Math.max(1, bins / bandCount);

        for (int b = 0; b < bandCount; b++) {
            double lowHz = b * binsPerBand * (double) frame.sampleRate() / (2 * bins);
            double highHz = (b + 1) * binsPerBand * (double) frame.sampleRate() / (2 * bins);

            double energy = 0;
            for (int k = b * binsPerBand; k < Math.min((b + 1) * binsPerBand, bins); k++) {
                energy += frame.magnitudes()[k] * frame.magnitudes()[k];
            }
            energy = Math.sqrt(energy / binsPerBand);
            bands.add(new FrequencyBand(lowHz, highHz, energy));
        }
        return bands;
    }

    /**
     * Encode bands to HDC vector.
     * Each band gets a random hypervector, weighted by energy.
     */
    public boolean[] encodeToHDC(List<FrequencyBand> bands) {
        boolean[] hdc = new boolean[dimension];
        float[] real = new float[dimension];

        for (int b = 0; b < bands.size(); b++) {
            float[] bandVector = generateBandVector(b);
            float weight = (float) bands.get(b).energy();
            for (int i = 0; i < dimension; i++) {
                real[i] += bandVector[i] * weight;
            }
        }

        // Binarize: threshold at median
        float[] sorted = real.clone();
        Arrays.sort(sorted);
        float threshold = sorted[sorted.length / 2];
        for (int i = 0; i < dimension; i++) {
            hdc[i] = real[i] > threshold;
        }
        return hdc;
    }

    private float[] generateBandVector(int bandIndex) {
        // Deterministic random vector per band
        Random bandRng = new Random(bandIndex * 1000L);
        float[] v = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            v[i] = bandRng.nextFloat() * 2 - 1; // [-1, 1]
        }
        return v;
    }

    /**
     * One-shot encode: samples → HDC vector.
     */
    public boolean[] encode(float[] samples, int sampleRate) {
        SpectralFrame frame = computeDFT(samples, sampleRate);
        List<FrequencyBand> bands = extractBands(frame);
        return encodeToHDC(bands);
    }

    public int getDimension() { return dimension; }
    public int getBandCount() { return bandCount; }
}
