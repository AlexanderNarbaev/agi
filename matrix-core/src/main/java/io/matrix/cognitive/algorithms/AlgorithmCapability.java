package io.matrix.cognitive.algorithms;

import java.util.Set;

/**
 * W44.2 — what an algorithm declares about itself before the selector is allowed to choose it.
 *
 * <p>The W44 directive gives the shape of this record and the reason for it: <b>a method must
 * declare its assumptions</b>. Dijkstra requires non-negative edge weights; binary search
 * requires sorted input; an exact solver may have an exponential worst case; a cached solution
 * requires validated applicability conditions. An algorithm that is not selected outside its
 * declared preconditions cannot be selected at all.</p>
 *
 * <p>Three fields carry the weight:</p>
 * <ul>
 *   <li>{@code requiredPreconditions} — the conditions under which the answer is CORRECT. They are
 *       checked before selection, and a violated precondition is a refusal, not a fallback to a
 *       wrong answer.</li>
 *   <li>{@code accuracyEvidence} — what was measured, on what, and with what denominator. A
 *       capability with no measurement says so; it is not assumed to be perfect.</li>
 *   <li>{@code fallbackAlgorithmId} — the reference method to use when this method's applicability
 *       conditions no longer hold. A fast path without a fallback is a trap.</li>
 * </ul>
 *
 * <p>All profiles are immutable value objects. {@code accuracyEvidence} deliberately carries the
 * DENOMINATOR, because a score without a denominator is not a measurement (CONSTITUTION
 * Article VI).</p>
 */
public record AlgorithmCapability(
        String id,
        String version,
        Set<String> problemFamilies,
        Set<String> requiredPreconditions,
        Set<String> supportedInputTypes,
        Set<String> outputTypes,
        ComplexityProfile complexity,
        ResourceProfile resourceRequirements,
        AccuracyProfile accuracyEvidence,
        DeterminismProfile determinism,
        ReliabilityProfile reliability,
        VerificationStrategy verification,
        String fallbackAlgorithmId) {

    public AlgorithmCapability {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("algorithm id must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("algorithm version must not be blank");
        }
        problemFamilies = problemFamilies == null ? Set.of() : Set.copyOf(problemFamilies);
        requiredPreconditions = requiredPreconditions == null ? Set.of() : Set.copyOf(requiredPreconditions);
        supportedInputTypes = supportedInputTypes == null ? Set.of() : Set.copyOf(supportedInputTypes);
        outputTypes = outputTypes == null ? Set.of() : Set.copyOf(outputTypes);
    }

    /** The asymptotic cost class of the method. */
    public record ComplexityProfile(String time, String space) {
        public ComplexityProfile {
            time = time == null ? "unknown" : time;
            space = space == null ? "unknown" : space;
        }
    }

    /** The resources the method needs, and what it does when they are missing. */
    public record ResourceProfile(String memory, boolean cpuOnly, boolean gpuAccelerable, String failureMode) {
        public ResourceProfile {
            memory = memory == null ? "unknown" : memory;
            failureMode = failureMode == null ? "unspecified" : failureMode;
        }
    }

    /**
     * What was measured about the method, and on how much data.
     *
     * @param metric        the measured quantity, e.g. "exact-match rate"
     * @param value         the measured value
     * @param denominator   how many items the value is over — never omitted
     * @param corpus        what was measured on
     * @param measuredAt    when the measurement was taken
     */
    public record AccuracyProfile(String metric, double value, int denominator, String corpus, String measuredAt) {
        public AccuracyProfile {
            metric = metric == null ? "unmeasured" : metric;
            corpus = corpus == null ? "none" : corpus;
            measuredAt = measuredAt == null ? "unmeasured" : measuredAt;
        }

        /** The profile of a method nobody has measured yet. */
        public static AccuracyProfile unmeasured() {
            return new AccuracyProfile("unmeasured", 0.0, 0, "none", "unmeasured");
        }
    }

    /** Whether the method produces the same output for the same input. */
    public record DeterminismProfile(boolean deterministic, String seedPolicy) {
        public DeterminismProfile {
            seedPolicy = seedPolicy == null ? "none" : seedPolicy;
        }
    }

    /** How the method fails when it fails. */
    public record ReliabilityProfile(String failureMode, boolean throwsOnViolation, String degradation) {
        public ReliabilityProfile {
            failureMode = failureMode == null ? "unspecified" : failureMode;
            degradation = degradation == null ? "none" : degradation;
        }
    }

    /** How a result of this method is checked. */
    public record VerificationStrategy(String strategy, String verifier) {
        public VerificationStrategy {
            strategy = strategy == null ? "none" : strategy;
            verifier = verifier == null ? "none" : verifier;
        }
    }

    /** True when the capability declares the given precondition. */
    public boolean requires(String precondition) {
        return requiredPreconditions.contains(precondition);
    }

    /** True when the capability serves the given problem family. */
    public boolean serves(String family) {
        return problemFamilies.contains(family);
    }

    @Override
    public String toString() {
        return "AlgorithmCapability[" + id + "@" + version + " families=" + problemFamilies
                + " preconditions=" + requiredPreconditions + "]";
    }
}
