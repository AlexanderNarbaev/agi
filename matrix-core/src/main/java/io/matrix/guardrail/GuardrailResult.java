package io.matrix.guardrail;

import java.util.List;

/**
 * Result of a guardrail evaluation.
 *
 * <p>Immutable record produced by {@link GuardrailEngine#evaluate(String)}.
 * Contains the allow/block decision, confidence score, detected violations,
 * and evaluation latency in milliseconds.
 *
 * <p>Ref: Phase 2 Guardrail MVP — standalone evaluation API.
 *
 * @param allowed    true if the content passes all guardrail checks
 * @param confidence confidence score [0.0 .. 1.0]
 * @param violations list of detected violation tags (empty if passed)
 * @param latencyMs  evaluation wall-clock time in milliseconds
 */
public record GuardrailResult(
        boolean allowed,
        double confidence,
        List<String> violations,
        long latencyMs
) {
    public GuardrailResult {
        if (violations == null) violations = List.of();
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be 0.0-1.0, got " + confidence);
        }
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must be >= 0, got " + latencyMs);
        }
    }

    /** Factory for a passed result. */
    public static GuardrailResult pass(long latencyMs) {
        return new GuardrailResult(true, 1.0, List.of(), latencyMs);
    }

    /** Factory for a blocked result with violations. */
    public static GuardrailResult block(double confidence, List<String> violations,
                                         long latencyMs) {
        return new GuardrailResult(false, confidence, violations, latencyMs);
    }
}
