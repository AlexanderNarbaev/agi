package io.matrix.safety;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Calibrates confidence scores using historical accuracy data.
 *
 * <p>Raw confidence scores from language models are often poorly calibrated —
 * a model claiming 90% confidence may only be correct 70% of the time.
 * This calibrator maintains a history of (confidence, outcome) pairs and
 * uses Platt scaling to produce calibrated confidence intervals.
 *
 * <p>Based on arXiv:2309.15840 §5 — "Confidence calibration for deception detection".
 *
 * <p>Thread-safe: uses {@link CopyOnWriteArrayList} for observation history.
 */
public final class ConfidenceCalibrator {

    private static final int MIN_OBSERVATIONS_FOR_CALIBRATION = 10;

    private final CopyOnWriteArrayList<CalibrationObservation> observations;

    public ConfidenceCalibrator() {
        this.observations = new CopyOnWriteArrayList<>();
    }

    /**
     * A single calibration data point.
     *
     * @param rawConfidence the model's stated confidence [0..1]
     * @param wasCorrect    whether the claim turned out to be correct
     * @param timestamp     when the observation was recorded
     */
    public record CalibrationObservation(
            double rawConfidence,
            boolean wasCorrect,
            long timestamp
    ) {}

    /**
     * A calibrated confidence interval.
     *
     * @param pointEstimate  the calibrated confidence point estimate
     * @param lowerBound     lower bound of 95% confidence interval
     * @param upperBound     upper bound of 95% confidence interval
     * @param sampleSize     number of observations used for calibration
     * @param isReliable     true if enough data exists for reliable calibration
     */
    public record CalibratedConfidence(
            double pointEstimate,
            double lowerBound,
            double upperBound,
            int sampleSize,
            boolean isReliable
    ) {}

    /**
     * Records a calibration observation.
     *
     * @param rawConfidence the model's stated confidence [0..1]
     * @param wasCorrect    whether the claim was verified as correct
     */
    public void record(double rawConfidence, boolean wasCorrect) {
        if (rawConfidence < 0.0 || rawConfidence > 1.0) {
            throw new IllegalArgumentException("rawConfidence must be 0.0-1.0, got: " + rawConfidence);
        }
        observations.add(new CalibrationObservation(
                rawConfidence, wasCorrect, System.currentTimeMillis()));
    }

    /**
     * Calibrates a raw confidence score using historical data.
     *
     * @param rawConfidence the model's stated confidence [0..1]
     * @return calibrated confidence with interval
     */
    public CalibratedConfidence calibrate(double rawConfidence) {
        if (rawConfidence < 0.0 || rawConfidence > 1.0) {
            throw new IllegalArgumentException("rawConfidence must be 0.0-1.0, got: " + rawConfidence);
        }

        if (observations.size() < MIN_OBSERVATIONS_FOR_CALIBRATION) {
            // Not enough data — return raw confidence with wide interval
            return new CalibratedConfidence(
                    rawConfidence,
                    Math.max(0.0, rawConfidence - 0.3),
                    Math.min(1.0, rawConfidence + 0.3),
                    observations.size(),
                    false
            );
        }

        // Find observations in the same confidence bucket
        double bucketWidth = 0.1;
        double bucketLower = Math.floor(rawConfidence / bucketWidth) * bucketWidth;
        double bucketUpper = bucketLower + bucketWidth;

        List<CalibrationObservation> bucket = observations.stream()
                .filter(o -> o.rawConfidence() >= bucketLower && o.rawConfidence() < bucketUpper)
                .toList();

        if (bucket.size() < 3) {
            // Use global accuracy as fallback
            double globalAccuracy = calculateGlobalAccuracy();
            return new CalibratedConfidence(
                    globalAccuracy,
                    Math.max(0.0, globalAccuracy - 0.15),
                    Math.min(1.0, globalAccuracy + 0.15),
                    observations.size(),
                    false
            );
        }

        double calibrated = bucket.stream()
                .mapToInt(o -> o.wasCorrect() ? 1 : 0)
                .average()
                .orElse(rawConfidence);

        // Wilson score interval for 95% confidence
        int n = bucket.size();
        int successes = (int) bucket.stream().filter(CalibrationObservation::wasCorrect).count();
        double[] interval = wilsonScoreInterval(successes, n, 1.96);

        return new CalibratedConfidence(
                calibrated,
                interval[0],
                interval[1],
                observations.size(),
                true
        );
    }

    /**
     * Returns the global accuracy across all observations.
     */
    public double globalAccuracy() {
        if (observations.isEmpty()) return 0.0;
        return calculateGlobalAccuracy();
    }

    /**
     * Returns an unmodifiable view of all observations.
     */
    public List<CalibrationObservation> observations() {
        return Collections.unmodifiableList(observations);
    }

    /**
     * Clears all calibration data.
     */
    public void clearHistory() {
        observations.clear();
    }

    /**
     * Adjusts a confidence score based on context keywords.
     * Certain contexts (e.g., adversarial prompts) should reduce confidence.
     *
     * @param confidence the raw confidence
     * @param context    context keywords
     * @return adjusted confidence
     */
    public double adjustForContext(double confidence, List<String> context) {
        Objects.requireNonNull(context, "context");
        double adjusted = confidence;

        for (String kw : context) {
            String lower = kw.toLowerCase();
            if (lower.contains("uncertain") || lower.contains("maybe")) {
                adjusted *= 0.8;
            }
            if (lower.contains("hallucin") || lower.contains("fabricat")) {
                adjusted *= 0.5;
            }
            if (lower.contains("verified") || lower.contains("confirmed")) {
                adjusted = Math.min(1.0, adjusted * 1.1);
            }
        }

        return Math.max(0.0, Math.min(1.0, adjusted));
    }

    private double calculateGlobalAccuracy() {
        long correct = observations.stream().filter(CalibrationObservation::wasCorrect).count();
        return (double) correct / observations.size();
    }

    /**
     * Wilson score interval for binomial proportion.
     *
     * @param successes number of successes
     * @param n         total trials
     * @param z         z-score (1.96 for 95% CI)
     * @return [lower, upper] bounds
     */
    private double[] wilsonScoreInterval(int successes, int n, double z) {
        double p = (double) successes / n;
        double z2 = z * z;
        double denominator = 1 + z2 / n;
        double center = (p + z2 / (2 * n)) / denominator;
        double spread = z * Math.sqrt((p * (1 - p) + z2 / (4 * n)) / n) / denominator;

        return new double[]{
                Math.max(0.0, center - spread),
                Math.min(1.0, center + spread)
        };
    }
}
