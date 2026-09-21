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
 * Main lie detection engine based on probe-question verification.
 *
 * <p>Implements the approach from arXiv:2309.15840 ("Lie Detector"):
 * <ol>
 *   <li>Generate probe questions targeting a claim</li>
 *   <li>Collect agent responses to probes</li>
 *   <li>Evaluate consistency and truthfulness of responses</li>
 *   <li>Aggregate into a deception score</li>
 * </ol>
 *
 * <p>Thread-safe: uses {@link CopyOnWriteArrayList} for claim/probe history.
 * Integrates with {@link EthicalFilter} for axiom-level truthfulness checks.
 *
 * <p>Ref: arXiv:2309.15840 §3 — "Probing for deception"
 */
public final class LieDetector {

    private static final Logger log = LoggerFactory.getLogger(LieDetector.class);

    /** Deception score threshold above which a claim is flagged. */
    private static final double DECEPTION_THRESHOLD = 0.6;

    private final EthicalFilter ethicalFilter;
    private final CopyOnWriteArrayList<VerifiedClaim> claimHistory;

    public LieDetector(EthicalFilter ethicalFilter) {
        this.ethicalFilter = Objects.requireNonNull(ethicalFilter, "ethicalFilter");
        this.claimHistory = new CopyOnWriteArrayList<>();
    }

    /**
     * Result of a lie detection evaluation.
     *
     * @param claim           the original claim
     * @param deceptionScore  aggregated deception likelihood [0..1]
     * @param isDeceptive     true if deception score exceeds threshold
     * @param probeResults    individual probe outcomes
     * @param ethicalVerdict  result from EthicalFilter evaluation
     */
    public record DetectionResult(
            String claim,
            double deceptionScore,
            boolean isDeceptive,
            List<ProbeResult> probeResults,
            EthicalVerdict ethicalVerdict
    ) {}

    /**
     * Result of a single probe evaluation.
     *
     * @param probe          the probe question
     * @param actualAnswer   the agent's response
     * @param passed         true if the answer matches expected
     * @param deviationScore how far the answer deviates from expected [0..1]
     */
    public record ProbeResult(
            ProbeQuestion probe,
            String actualAnswer,
            boolean passed,
            double deviationScore
    ) {}

    /**
     * A claim stored in the verification history.
     *
     * @param claim          the claim text
     * @param timestamp      when the claim was made
     * @param deceptionScore the evaluated deception score
     */
    public record VerifiedClaim(
            String claim,
            long timestamp,
            double deceptionScore
    ) {}

    /**
     * Evaluates a claim using probe questions and returns a detection result.
     *
     * @param claim   the claim to evaluate
     * @param probes  probe questions targeting this claim
     * @param answers agent's answers to the probes (same order)
     * @return detection result with deception score
     * @throws IllegalArgumentException if probes and answers have different sizes
     */
    public DetectionResult evaluate(String claim, List<ProbeQuestion> probes, List<String> answers) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(probes, "probes");
        Objects.requireNonNull(answers, "answers");
        if (probes.size() != answers.size()) {
            throw new IllegalArgumentException(
                    "probes and answers must have same size: " + probes.size() + " vs " + answers.size());
        }

        EthicalVerdict ethicalVerdict = ethicalFilter.evaluate(claim, List.of());
        if (ethicalVerdict == EthicalVerdict.REJECTED) {
            log.warn("Claim rejected by EthicalFilter: {}", claim);
            return new DetectionResult(claim, 1.0, true, List.of(), ethicalVerdict);
        }

        List<ProbeResult> probeResults = new ArrayList<>();
        for (int i = 0; i < probes.size(); i++) {
            probeResults.add(evaluateProbe(probes.get(i), answers.get(i)));
        }

        double deceptionScore = aggregateScore(probeResults);
        boolean isDeceptive = deceptionScore > DECEPTION_THRESHOLD;

        VerifiedClaim verified = new VerifiedClaim(claim, System.currentTimeMillis(), deceptionScore);
        claimHistory.add(verified);

        if (isDeceptive) {
            log.warn("Deceptive claim detected (score={:.3f}): {}", deceptionScore, claim);
        }

        return new DetectionResult(claim, deceptionScore, isDeceptive, probeResults, ethicalVerdict);
    }

    /**
     * Returns an unmodifiable view of the claim history.
     */
    public List<VerifiedClaim> claimHistory() {
        return Collections.unmodifiableList(claimHistory);
    }

    /**
     * Clears the claim history.
     */
    public void clearHistory() {
        claimHistory.clear();
    }

    private ProbeResult evaluateProbe(ProbeQuestion probe, String actualAnswer) {
        if (actualAnswer == null) {
            return new ProbeResult(probe, null, false, 1.0);
        }

        if (probe.expectedAnswer() == null) {
            // No expected answer — evaluate logical coherence only
            double deviation = evaluateLogicalCoherence(probe, actualAnswer);
            return new ProbeResult(probe, actualAnswer, deviation < 0.5, deviation);
        }

        boolean passed = normalizeAnswer(actualAnswer).equals(normalizeAnswer(probe.expectedAnswer()));
        double deviation = passed ? 0.0 : calculateDeviation(probe, actualAnswer);
        return new ProbeResult(probe, actualAnswer, passed, deviation);
    }

    private double evaluateLogicalCoherence(ProbeQuestion probe, String answer) {
        String lowered = answer.toLowerCase();
        // Simple heuristic: hedging language suggests uncertainty or deception
        String[] hedgeWords = {"maybe", "perhaps", "i think", "possibly", "not sure", "might be"};
        int hedgeCount = 0;
        for (String hedge : hedgeWords) {
            if (lowered.contains(hedge)) hedgeCount++;
        }
        return Math.min(1.0, hedgeCount * 0.3);
    }

    private double calculateDeviation(ProbeQuestion probe, String actualAnswer) {
        // Weighted by probe confidence: higher confidence → more penalty for deviation
        double baseDeviation = 0.8;
        return baseDeviation * probe.confidence();
    }

    private double aggregateScore(List<ProbeResult> results) {
        if (results.isEmpty()) return 0.0;
        double totalWeight = 0.0;
        double weightedSum = 0.0;
        for (ProbeResult r : results) {
            double weight = r.probe().confidence();
            weightedSum += r.deviationScore() * weight;
            totalWeight += weight;
        }
        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    private String normalizeAnswer(String answer) {
        return answer.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", "");
    }
}
