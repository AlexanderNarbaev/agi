package io.matrix.safety;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Checks consistency of agent outputs and detects contradictions between claims.
 *
 * <p>Tracks claim history and uses semantic similarity heuristics to detect
 * when new claims contradict prior claims. Part of the Lie Detector pipeline
 * (arXiv:2309.15840 §4 — "Consistency as a deception signal").
 *
 * <p>Thread-safe: uses {@link ConcurrentHashMap} for claim index and
 * {@link CopyOnWriteArrayList} for ordered history.
 */
public final class ConsistencyChecker {

    private static final Logger log = LoggerFactory.getLogger(ConsistencyChecker.class);

    /** Threshold for contradiction detection [0..1]. */
    private static final double CONTRADICTION_THRESHOLD = 0.5;

    /** Key claims indexed by topic for fast lookup. */
    private final ConcurrentHashMap<String, List<TrackedClaim>> claimsByTopic;

    /** Ordered history of all tracked claims. */
    private final CopyOnWriteArrayList<TrackedClaim> claimHistory;

    public ConsistencyChecker() {
        this.claimsByTopic = new ConcurrentHashMap<>();
        this.claimHistory = new CopyOnWriteArrayList<>();
    }

    /**
     * A claim tracked for consistency checking.
     *
     * @param claim     the claim text
     * @param topic     extracted topic key
     * @param polarity  positive (true) or negative (false) assertion
     * @param timestamp when the claim was recorded
     */
    public record TrackedClaim(
            String claim,
            String topic,
            boolean polarity,
            long timestamp
    ) {}

    /**
     * Result of a consistency check.
     *
     * @param isConsistent       true if no contradictions detected
     * @param contradictions     list of detected contradictions
     * @param totalClaimsChecked number of prior claims compared against
     */
    public record ConsistencyReport(
            boolean isConsistent,
            List<Contradiction> contradictions,
            int totalClaimsChecked
    ) {}

    /**
     * A detected contradiction between two claims.
     *
     * @param claimA   the prior claim
     * @param claimB   the new contradicting claim
     * @param severity contradiction severity [0..1]
     */
    public record Contradiction(
            TrackedClaim claimA,
            TrackedClaim claimB,
            double severity
    ) {}

    /**
     * Records a new claim and checks it against all prior claims for contradictions.
     *
     * @param claim    the claim text
     * @param topic    topic key (e.g., "user_age", "system_status")
     * @param polarity true for positive assertion, false for negative
     * @return consistency report
     */
    public ConsistencyReport recordAndCheck(String claim, String topic, boolean polarity) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(topic, "topic");

        TrackedClaim tracked = new TrackedClaim(claim, topic, polarity, System.currentTimeMillis());
        List<Contradiction> contradictions = findContradictions(tracked);

        claimsByTopic.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(tracked);
        claimHistory.add(tracked);

        if (!contradictions.isEmpty()) {
            log.warn("Contradiction detected for topic '{}' ({} instances)", topic, contradictions.size());
        }

        return new ConsistencyReport(contradictions.isEmpty(), contradictions, claimHistory.size() - 1);
    }

    /**
     * Checks a claim without recording it (dry-run).
     *
     * @param claim    the claim text
     * @param topic    topic key
     * @param polarity true for positive assertion
     * @return consistency report
     */
    public ConsistencyReport checkOnly(String claim, String topic, boolean polarity) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(topic, "topic");

        TrackedClaim tracked = new TrackedClaim(claim, topic, polarity, System.currentTimeMillis());
        List<Contradiction> contradictions = findContradictions(tracked);

        return new ConsistencyReport(contradictions.isEmpty(), contradictions, claimHistory.size());
    }

    /**
     * Returns an unmodifiable view of the full claim history.
     */
    public List<TrackedClaim> claimHistory() {
        return Collections.unmodifiableList(claimHistory);
    }

    /**
     * Returns claims for a specific topic.
     */
    public List<TrackedClaim> claimsForTopic(String topic) {
        return Collections.unmodifiableList(
                claimsByTopic.getOrDefault(topic, List.of()));
    }

    /**
     * Clears all tracked claims.
     */
    public void clearHistory() {
        claimsByTopic.clear();
        claimHistory.clear();
    }

    private List<Contradiction> findContradictions(TrackedClaim newClaim) {
        List<Contradiction> contradictions = new ArrayList<>();
        List<TrackedClaim> topicClaims = claimsByTopic.getOrDefault(newClaim.topic(), List.of());

        for (TrackedClaim prior : topicClaims) {
            if (isContradiction(prior, newClaim)) {
                double severity = calculateSeverity(prior, newClaim);
                contradictions.add(new Contradiction(prior, newClaim, severity));
            }
        }
        return contradictions;
    }

    private boolean isContradiction(TrackedClaim a, TrackedClaim b) {
        // Same topic, opposite polarity = contradiction
        if (a.topic().equals(b.topic()) && a.polarity() != b.polarity()) {
            return true;
        }
        // Semantic contradiction: negation patterns
        return hasNegationConflict(a.claim(), b.claim());
    }

    private boolean hasNegationConflict(String claimA, String claimB) {
        String a = claimA.toLowerCase();
        String b = claimB.toLowerCase();

        // Check if one contains negation of the other's core assertion
        boolean aNegated = a.contains(" not ") || a.contains("n't ") || a.startsWith("no ");
        boolean bNegated = b.contains(" not ") || b.contains("n't ") || b.startsWith("no ");

        // If one is negated and they share key terms, likely contradiction
        if (aNegated != bNegated) {
            String coreA = a.replace(" not ", " ").replace("n't ", "").replace("no ", "");
            String coreB = b.replace(" not ", " ").replace("n't ", "").replace("no ", "");
            return sharedTermsAbove(coreA, coreB, 0.5);
        }
        return false;
    }

    private boolean sharedTermsAbove(String a, String b, double threshold) {
        Set<String> termsA = tokenize(a);
        Set<String> termsB = tokenize(b);
        if (termsA.isEmpty() || termsB.isEmpty()) return false;

        long common = termsA.stream().filter(termsB::contains).count();
        double jaccard = (double) common / (termsA.size() + termsB.size() - common);
        return jaccard >= threshold;
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        for (String word : text.split("\\s+")) {
            if (!word.isBlank()) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    private double calculateSeverity(TrackedClaim a, TrackedClaim b) {
        // Same topic, opposite polarity = high severity
        if (a.topic().equals(b.topic()) && a.polarity() != b.polarity()) {
            return 0.9;
        }
        return 0.6;
    }
}
