package io.matrix.safety;

import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Top-level safety monitor that integrates all lie detection components.
 *
 * <p>Orchestrates {@link LieDetector}, {@link ConsistencyChecker}, and
 * {@link ConfidenceCalibrator} to provide a unified safety evaluation
 * of agent outputs. Generates safety reports and triggers alerts on
 * suspicious outputs.
 *
 * <p>Based on arXiv:2309.15840 — "Lie Detector: Determining the veracity
 * of language model outputs through probing."
 *
 * <p>Thread-safe: delegates to thread-safe sub-components and uses
 * {@link CopyOnWriteArrayList} for alert history.
 *
 * <p>Integrates with {@link EthicalFilter} for axiom-level checks.
 */
public final class SafetyMonitor {

    private static final Logger log = LoggerFactory.getLogger(SafetyMonitor.class);

    /** Overall safety score threshold below which output is flagged. */
    private static final double SAFETY_THRESHOLD = 0.5;

    private final LieDetector lieDetector;
    private final ConsistencyChecker consistencyChecker;
    private final ConfidenceCalibrator confidenceCalibrator;
    private final CopyOnWriteArrayList<SafetyAlert> alertHistory;

    public SafetyMonitor(EthicalFilter ethicalFilter) {
        Objects.requireNonNull(ethicalFilter, "ethicalFilter");
        this.lieDetector = new LieDetector(ethicalFilter);
        this.consistencyChecker = new ConsistencyChecker();
        this.confidenceCalibrator = new ConfidenceCalibrator();
        this.alertHistory = new CopyOnWriteArrayList<>();
    }

    /**
     * Comprehensive safety report for an agent output.
     *
     * @param overallSafetyScore aggregated safety score [0..1] (1 = safe)
     * @param isSafe             true if score meets threshold
     * @param detectionResult    lie detection result (may be null)
     * @param consistencyReport  consistency check result
     * @param calibratedConfidence calibrated confidence (may be null)
     * @param alerts             triggered alerts
     */
    public record SafetyReport(
            double overallSafetyScore,
            boolean isSafe,
            LieDetector.DetectionResult detectionResult,
            ConsistencyChecker.ConsistencyReport consistencyReport,
            ConfidenceCalibrator.CalibratedConfidence calibratedConfidence,
            List<SafetyAlert> alerts
    ) {}

    /**
     * A safety alert triggered by suspicious output.
     *
     * @param level     alert severity
     * @param message   human-readable alert message
     * @param component which component triggered the alert
     * @param timestamp when the alert was generated
     */
    public record SafetyAlert(
            AlertLevel level,
            String message,
            String component,
            long timestamp
    ) {}

    /**
     * Alert severity levels.
     */
    public enum AlertLevel {
        INFO, WARNING, CRITICAL
    }

    /**
     * Evaluates an agent output for safety.
     *
     * @param claim           the agent's claim
     * @param probes          probe questions for this claim
     * @param probeAnswers    agent's answers to probes
     * @param topic           consistency topic
     * @param polarity        claim polarity for consistency checking
     * @param rawConfidence   agent's stated confidence [0..1]
     * @param contextKeywords context keywords for confidence adjustment
     * @return comprehensive safety report
     */
    public SafetyReport evaluate(
            String claim,
            List<ProbeQuestion> probes,
            List<String> probeAnswers,
            String topic,
            boolean polarity,
            double rawConfidence,
            List<String> contextKeywords
    ) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(probes, "probes");
        Objects.requireNonNull(probeAnswers, "probeAnswers");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(contextKeywords, "contextKeywords");

        List<SafetyAlert> alerts = new ArrayList<>();

        // 1. Lie detection
        LieDetector.DetectionResult detectionResult = lieDetector.evaluate(claim, probes, probeAnswers);
        if (detectionResult.isDeceptive()) {
            alerts.add(new SafetyAlert(
                    AlertLevel.CRITICAL,
                    "Deceptive claim detected (score=%.3f): %s".formatted(
                            detectionResult.deceptionScore(), claim),
                    "LieDetector",
                    System.currentTimeMillis()
            ));
        }

        // 2. Consistency check
        ConsistencyChecker.ConsistencyReport consistencyReport =
                consistencyChecker.recordAndCheck(claim, topic, polarity);
        if (!consistencyReport.isConsistent()) {
            alerts.add(new SafetyAlert(
                    AlertLevel.WARNING,
                    "Contradiction detected: %d contradictions found".formatted(
                            consistencyReport.contradictions().size()),
                    "ConsistencyChecker",
                    System.currentTimeMillis()
            ));
        }

        // 3. Confidence calibration
        double adjustedConfidence = confidenceCalibrator.adjustForContext(rawConfidence, contextKeywords);
        ConfidenceCalibrator.CalibratedConfidence calibratedConfidence =
                confidenceCalibrator.calibrate(adjustedConfidence);
        if (!calibratedConfidence.isReliable()) {
            alerts.add(new SafetyAlert(
                    AlertLevel.INFO,
                    "Insufficient calibration data (%d observations)".formatted(
                            calibratedConfidence.sampleSize()),
                    "ConfidenceCalibrator",
                    System.currentTimeMillis()
            ));
        }

        // 4. Aggregate safety score
        double safetyScore = aggregateSafetyScore(detectionResult, consistencyReport, calibratedConfidence);
        boolean isSafe = safetyScore >= SAFETY_THRESHOLD;

        if (!isSafe) {
            alerts.add(new SafetyAlert(
                    AlertLevel.CRITICAL,
                    "Overall safety score %.3f below threshold %.3f".formatted(
                            safetyScore, SAFETY_THRESHOLD),
                    "SafetyMonitor",
                    System.currentTimeMillis()
            ));
        }

        alertHistory.addAll(alerts);

        if (!alerts.isEmpty()) {
            log.warn("SafetyMonitor: {} alerts triggered for claim: {}", alerts.size(), claim);
        }

        return new SafetyReport(safetyScore, isSafe, detectionResult, consistencyReport,
                calibratedConfidence, Collections.unmodifiableList(alerts));
    }

    /**
     * Returns the lie detector component.
     */
    public LieDetector lieDetector() {
        return lieDetector;
    }

    /**
     * Returns the consistency checker component.
     */
    public ConsistencyChecker consistencyChecker() {
        return consistencyChecker;
    }

    /**
     * Returns the confidence calibrator component.
     */
    public ConfidenceCalibrator confidenceCalibrator() {
        return confidenceCalibrator;
    }

    /**
     * Returns an unmodifiable view of all triggered alerts.
     */
    public List<SafetyAlert> alertHistory() {
        return Collections.unmodifiableList(alertHistory);
    }

    /**
     * Clears all component histories and alerts.
     */
    public void reset() {
        lieDetector.clearHistory();
        consistencyChecker.clearHistory();
        confidenceCalibrator.clearHistory();
        alertHistory.clear();
    }

    private double aggregateSafetyScore(
            LieDetector.DetectionResult detection,
            ConsistencyChecker.ConsistencyReport consistency,
            ConfidenceCalibrator.CalibratedConfidence confidence
    ) {
        // Weight: 50% lie detection, 30% consistency, 20% confidence reliability
        double lieScore = 1.0 - detection.deceptionScore();
        double consistencyScore = consistency.isConsistent() ? 1.0 :
                consistency.contradictions().stream()
                        .mapToDouble(c -> 1.0 - c.severity())
                        .average().orElse(0.5);
        double confidenceScore = confidence.isReliable() ? confidence.pointEstimate() : 0.5;

        return lieScore * 0.5 + consistencyScore * 0.3 + confidenceScore * 0.2;
    }
}
