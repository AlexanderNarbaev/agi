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
     * Source of frame timestamps. Unit: milliseconds.
     *
     * <p>Injected rather than read from {@link System}, so a caller that needs
     * reproducibility can supply a fixed clock and get byte-identical frames. The default
     * is the system clock, which is convenient and NOT deterministic — pass a fixed one
     * in any path whose output is compared, hashed or asserted.</p>
     */
    public interface Clock {
        long nowMillis();
    }

    /** The default clock. Reads the system clock; convenient, not reproducible. */
    public static final Clock SYSTEM_CLOCK = System::currentTimeMillis;

    /**
     * A clock that always reports the same instant. Unit: milliseconds.
     * Use in tests and anywhere a frame must be byte-comparable across runs.
     */
    public static Clock fixedClock(long millis) {
        final long v = millis;
        return () -> v;
    }

    private final Clock clock;

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

    /** Uses the system clock. Convenient; NOT reproducible across runs. */
    public AudioFFTEncoder(int dimension, int bandCount, long seed) {
        this(dimension, bandCount, seed, SYSTEM_CLOCK);
    }

    /**
     * RECON-W32.5: clock-injecting constructor.
     *
     * <p>Article III requires a deterministic function of the input. A frame stamped with
     * {@link System#currentTimeMillis()} is not, so two runs over the same audio gave
     * different {@link SpectralFrame}s and any comparison between them was meaningless.
     * Supplying the clock makes reproducibility a decision of the caller rather than an
     * accident.</p>
     *
     * @param clock source of frame timestamps, never null
     */
    public AudioFFTEncoder(int dimension, int bandCount, long seed, Clock clock) {
        if (clock == null) throw new IllegalArgumentException("clock required");
        this.dimension = dimension;
        this.bandCount = bandCount;
        this.rng = new Random(seed);
        this.clock = clock;
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
        // RECON-W32.5: the frame timestamp comes from an INJECTED clock. This call was
        // System.currentTimeMillis(), a wall clock in the runtime mind path: an Article
        // III breach that also made every frame non-reproducible, so two runs over the
        // same audio produced different SpectralFrames and any comparison of them was
        // meaningless. Article III requires a deterministic function of the input.
        return new SpectralFrame(magnitudes, phases, sampleRate, clock.nowMillis());
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
