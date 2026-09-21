package io.matrix.ethics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Probe-based output verification — lie detection for agent responses.
 *
 * <p>Implements the "Lie Detector Safety Layer" pattern from Research Synthesis
 * 2026 Q3 v2 (Phase C4). Uses deterministic probes to classify agent outputs
 * on honesty, consistency, and ethical alignment dimensions.
 *
 * <h3>Probe Types</h3>
 * <ul>
 *   <li>{@link ProbeType#CONSISTENCY_CHECK} — verifies response doesn't contradict itself</li>
 *   <li>{@link ProbeType#KNOWLEDGE_BOUNDARY} — detects fabricated facts / hallucination markers</li>
 *   <li>{@link ProbeType#ETHICAL_ALIGNMENT} — checks against forbidden content patterns</li>
 *   <li>{@link ProbeType#TRUTH_CLAIM} — validates factual assertions against known context</li>
 * </ul>
 *
 * <h3>Verdict Levels</h3>
 * <ul>
 *   <li>{@link Verdict#PASS} — all probes clean</li>
 *   <li>{@link Verdict#SUSPICIOUS} — one or more probes flagged concerns</li>
 *   <li>{@link Verdict#DECEPTIVE} — clear evidence of fabrication or ethical violation</li>
 * </ul>
 *
 * <p>Thread-safe — all methods are stateless.
 *
 * @since 3.25
 */
public final class LieDetector {

    // ── Probe Types ───────────────────────────────────────────────────────

    public enum ProbeType {
        CONSISTENCY_CHECK,
        KNOWLEDGE_BOUNDARY,
        ETHICAL_ALIGNMENT,
        TRUTH_CLAIM
    }

    // ── Verdict ───────────────────────────────────────────────────────────

    public enum Verdict {
        PASS("All probes passed"),
        SUSPICIOUS("One or more probes raised concerns"),
        DECEPTIVE("Clear evidence of deception or fabrication");

        private final String description;

        Verdict(String description) { this.description = description; }
        public String description() { return description; }
    }

    // ── Probe Result ──────────────────────────────────────────────────────

    /**
     * Result of a single probe evaluation.
     */
    public record ProbeResult(
            ProbeType type,
            boolean passed,
            String reason,
            double confidence
    ) {
        public ProbeResult {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(reason, "reason");
            confidence = Math.clamp(confidence, 0.0, 1.0);
        }

        public static ProbeResult pass(ProbeType type, String reason) {
            return new ProbeResult(type, true, reason, 1.0);
        }

        public static ProbeResult fail(ProbeType type, String reason, double confidence) {
            return new ProbeResult(type, false, reason, confidence);
        }
    }

    // ── Detection Result ──────────────────────────────────────────────────

    /**
     * Aggregated result from all probe evaluations.
     */
    public record DetectionResult(
            Verdict verdict,
            List<ProbeResult> probeResults,
            double overallConfidence,
            String summary
    ) {
        public DetectionResult {
            probeResults = List.copyOf(probeResults);
            overallConfidence = Math.clamp(overallConfidence, 0.0, 1.0);
            Objects.requireNonNull(summary, "summary");
        }

        /** Returns true if output is safe to proceed. */
        public boolean isClean() {
            return verdict == Verdict.PASS;
        }

        /** Returns all failed probes. */
        public List<ProbeResult> failures() {
            return probeResults.stream()
                    .filter(r -> !r.passed())
                    .toList();
        }
    }

    // ── Forbidden patterns (ethical alignment) ────────────────────────────

    private static final Set<String> FORBIDDEN_TERMS = Set.of(
            "kill", "murder", "torture", "enslave", "weaponize",
            "bio-weapon", "autonomous weapon", "chemical weapon",
            "nuclear weapon", "mass surveillance", "genocide"
    );

    // ── Hallucination markers ─────────────────────────────────────────────

    private static final List<String> HALLUCINATION_MARKERS = List.of(
            "research shows", "studies have shown", "experts agree",
            "it is well known", "according to recent", "scientists have discovered",
            "undoubtedly", "without question", "it is proven that"
    );

    private static final List<String> VAGUE_QUANTIFIERS = List.of(
            "many", "most", "some", "various", "several", "numerous",
            "countless", "a lot of"
    );

    // ── Self-contradiction patterns ───────────────────────────────────────

    private static final Pattern BUT_CONTRADICTION = Pattern.compile(
            "\\b(?:is|are|was|were|will be|has been)\\b.*\\bbut\\b.*\\b(?:is not|are not|was not|were not|will not be|has not been)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ABSOLUTE_CLAIM = Pattern.compile(
            "\\b(?:always|never|every|all|none|absolutely|certainly|definitely)\\b",
            Pattern.CASE_INSENSITIVE);

    // ── Constructor ───────────────────────────────────────────────────────

    private final Set<ProbeType> activeProbes;
    private final boolean strict;

    private LieDetector(Set<ProbeType> activeProbes, boolean strict) {
        this.activeProbes = Collections.unmodifiableSet(EnumSet.copyOf(activeProbes));
        this.strict = strict;
    }

    /** Creates a detector with all probes enabled. */
    public static LieDetector allProbes() {
        return new LieDetector(EnumSet.allOf(ProbeType.class), true);
    }

    /** Creates a detector with specified probes. */
    public static LieDetector of(Set<ProbeType> probes, boolean strict) {
        return new LieDetector(probes, strict);
    }

    /** Creates a detector with only essential probes (consistency + ethics). */
    public static LieDetector essential() {
        return new LieDetector(EnumSet.of(ProbeType.CONSISTENCY_CHECK, ProbeType.ETHICAL_ALIGNMENT), true);
    }

    // ── Main API ──────────────────────────────────────────────────────────

    /**
     * Evaluates an agent output against all active probes.
     *
     * @param output the agent's textual response
     * @param context additional context (previous turns, known facts, etc.)
     * @return aggregated detection result
     */
    public DetectionResult detect(String output, Map<String, String> context) {
        Objects.requireNonNull(output, "output");
        List<ProbeResult> results = new ArrayList<>();

        String lower = output.toLowerCase();

        if (activeProbes.contains(ProbeType.ETHICAL_ALIGNMENT)) {
            results.add(probeEthicalAlignment(lower));
        }
        if (activeProbes.contains(ProbeType.CONSISTENCY_CHECK)) {
            results.add(probeConsistency(output, lower));
        }
        if (activeProbes.contains(ProbeType.KNOWLEDGE_BOUNDARY)) {
            results.add(probeKnowledgeBoundary(lower, context));
        }
        if (activeProbes.contains(ProbeType.TRUTH_CLAIM)) {
            results.add(probeTruthClaims(output, lower, context));
        }

        Verdict verdict = computeVerdict(results);
        double confidence = computeConfidence(results);
        String summary = buildSummary(verdict, results);

        return new DetectionResult(verdict, results, confidence, summary);
    }

    /**
     * Checks a single output claim without full context.
     */
    public DetectionResult detect(String output) {
        return detect(output, Map.of());
    }

    // ── Probe Implementations ─────────────────────────────────────────────

    private ProbeResult probeEthicalAlignment(String lower) {
        List<String> found = new ArrayList<>();
        for (String term : FORBIDDEN_TERMS) {
            if (lower.contains(term)) {
                found.add(term);
            }
        }
        if (!found.isEmpty()) {
            return ProbeResult.fail(ProbeType.ETHICAL_ALIGNMENT,
                    "Contains forbidden terms: " + found, 0.95);
        }
        return ProbeResult.pass(ProbeType.ETHICAL_ALIGNMENT, "No forbidden content detected");
    }

    private ProbeResult probeConsistency(String output, String lower) {
        // Check for self-contradiction patterns
        if (BUT_CONTRADICTION.matcher(output).find()) {
            return ProbeResult.fail(ProbeType.CONSISTENCY_CHECK,
                    "Self-contradiction pattern detected (X but not X)", 0.7);
        }

        // Count absolute claims — too many is suspicious
        long absoluteCount = ABSOLUTE_CLAIM.matcher(output).results().count();
        long wordCount = output.split("\\s+").length;
        double absoluteRatio = wordCount > 0 ? (double) absoluteCount / wordCount : 0;

        if (absoluteRatio > 0.15) {
            return ProbeResult.fail(ProbeType.CONSISTENCY_CHECK,
                    String.format("Excessive absolute claims: %d in %d words (%.1f%%)",
                            absoluteCount, wordCount, absoluteRatio * 100), 0.6);
        }

        return ProbeResult.pass(ProbeType.CONSISTENCY_CHECK, "No consistency issues detected");
    }

    private ProbeResult probeKnowledgeBoundary(String lower, Map<String, String> context) {
        int markers = 0;
        int totalWords = lower.split("\\s+").length;

        // Check hallucination markers
        for (String marker : HALLUCINATION_MARKERS) {
            if (lower.contains(marker.toLowerCase())) {
                markers++;
            }
        }

        // Check vague quantifiers
        int vagueCount = 0;
        for (String vq : VAGUE_QUANTIFIERS) {
            vagueCount += countOccurrences(lower, vq);
        }

        double markerRatio = totalWords > 0 ? (double) markers / totalWords : 0;
        double vagueRatio = totalWords > 0 ? (double) vagueCount / totalWords : 0;

        // High markers without context = suspicious
        if (markerRatio > 0.05 && context.isEmpty()) {
            return ProbeResult.fail(ProbeType.KNOWLEDGE_BOUNDARY,
                    String.format("Hallucination markers (%.1f%%) without supporting context", markerRatio * 100),
                    0.65);
        }
        if (vagueRatio > 0.1) {
            return ProbeResult.fail(ProbeType.KNOWLEDGE_BOUNDARY,
                    String.format("Excessive vague quantifiers (%.1f%%)", vagueRatio * 100),
                    0.5);
        }

        return ProbeResult.pass(ProbeType.KNOWLEDGE_BOUNDARY, "No knowledge boundary concerns");
    }

    private ProbeResult probeTruthClaims(String output, String lower, Map<String, String> context) {
        // Check if claims can be verified against context
        if (context.isEmpty()) {
            // No context to verify against — cannot fully evaluate
            if (ABSOLUTE_CLAIM.matcher(output).find()) {
                return ProbeResult.fail(ProbeType.TRUTH_CLAIM,
                        "Absolute truth claims without verifiable context", 0.4);
            }
            return ProbeResult.pass(ProbeType.TRUTH_CLAIM, "No context for verification");
        }

        // Check if output makes claims not supported by context
        int unsupportedClaims = 0;
        for (var entry : context.entrySet()) {
            String claim = entry.getKey().toLowerCase();
            if (lower.contains(claim)) {
                // Claim matches context — OK
                continue;
            }
            // Check for claims that look like they need verification
            if (lower.contains("is ") || lower.contains("was ") || lower.contains("has ")) {
                unsupportedClaims++;
            }
        }

        if (unsupportedClaims > 2) {
            return ProbeResult.fail(ProbeType.TRUTH_CLAIM,
                    "Multiple claims without context support", 0.55);
        }

        return ProbeResult.pass(ProbeType.TRUTH_CLAIM, "Claims consistent with available context");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Verdict computeVerdict(List<ProbeResult> results) {
        long failed = results.stream().filter(r -> !r.passed()).count();
        if (failed == 0) return Verdict.PASS;

        // DECEPTIVE if high-confidence probe failed (ethical violation) or multiple failures
        boolean hasHighConfidence = results.stream()
                .anyMatch(r -> !r.passed() && r.confidence() >= 0.9);
        if (hasHighConfidence || failed >= 3) {
            return Verdict.DECEPTIVE;
        }
        return Verdict.SUSPICIOUS;
    }

    private double computeConfidence(List<ProbeResult> results) {
        if (results.isEmpty()) return 1.0;
        return results.stream()
                .mapToDouble(ProbeResult::confidence)
                .average()
                .orElse(1.0);
    }

    private String buildSummary(Verdict verdict, List<ProbeResult> results) {
        long failed = results.stream().filter(r -> !r.passed()).count();
        if (failed == 0) return "All " + results.size() + " probes passed";

        StringBuilder sb = new StringBuilder();
        sb.append(verdict).append(": ").append(failed).append("/").append(results.size())
                .append(" probes flagged. ");
        for (var r : results) {
            if (!r.passed()) {
                sb.append("[").append(r.type()).append(": ").append(r.reason()).append("] ");
            }
        }
        return sb.toString().trim();
    }

    private static int countOccurrences(String text, String word) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(word, idx)) != -1) {
            count++;
            idx += word.length();
        }
        return count;
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public Set<ProbeType> activeProbes() { return activeProbes; }
    public boolean strict() { return strict; }

    // ── equals / hashCode / toString ─────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LieDetector that)) return false;
        return strict == that.strict && activeProbes.equals(that.activeProbes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(activeProbes, strict);
    }

    @Override
    public String toString() {
        return "LieDetector{active=" + activeProbes + ", strict=" + strict + "}";
    }
}
