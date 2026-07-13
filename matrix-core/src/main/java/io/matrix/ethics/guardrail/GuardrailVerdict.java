package io.matrix.ethics.guardrail;

import java.time.Instant;
import java.util.List;

/**
 * Verdict produced by each guardrail layer after evaluating input or output.
 *
 * <p>Three severity levels:
 * <ul>
 *   <li>{@link #PASS} — content is safe, proceed normally</li>
 *   <li>{@link #WARN} — content has potential issues, proceed with caution and log</li>
 *   <li>{@link #BLOCK} — content violates guardrail policy, request must be rejected</li>
 * </ul>
 *
 * <p>Ref: EU AI Act Art. 9 — Risk Management System
 */
public enum GuardrailVerdict {
    /** Content passed all checks. */
    PASS,
    /** Content has potential issues — logged, may proceed with warning. */
    WARN,
    /** Content violates guardrail policy — request rejected. */
    BLOCK;

    /**
     * Detailed result from a single guard evaluation.
     *
     * @param guardName  name of the guard that produced this result
     * @param verdict    pass/warn/block decision
     * @param reason     human-readable explanation
     * @param confidence confidence score [0.0 .. 1.0]
     * @param patterns   list of detected patterns (e.g., "prompt_injection", "gender_bias")
     * @param timestamp  when the evaluation occurred
     */
    public record GuardResult(
            String guardName,
            GuardrailVerdict verdict,
            String reason,
            double confidence,
            List<String> patterns,
            Instant timestamp
    ) {
        public GuardResult {
            if (guardName == null || guardName.isBlank()) {
                throw new IllegalArgumentException("guardName must not be blank");
            }
            if (verdict == null) throw new IllegalArgumentException("verdict must not be null");
            if (reason == null) reason = "";
            if (patterns == null) patterns = List.of();
            if (timestamp == null) timestamp = Instant.now();
            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("confidence must be 0.0-1.0, got " + confidence);
            }
        }

        /** Convenience factory for a PASS result. */
        public static GuardResult pass(String guardName, String reason) {
            return new GuardResult(guardName, PASS, reason, 1.0, List.of(), Instant.now());
        }

        /** Convenience factory for a WARN result. */
        public static GuardResult warn(String guardName, String reason,
                                       double confidence, String... patterns) {
            return new GuardResult(guardName, WARN, reason, confidence,
                    List.of(patterns), Instant.now());
        }

        /** Convenience factory for a BLOCK result. */
        public static GuardResult block(String guardName, String reason,
                                        double confidence, String... patterns) {
            return new GuardResult(guardName, BLOCK, reason, confidence,
                    List.of(patterns), Instant.now());
        }
    }

    /**
     * Aggregated result from all guardrail layers.
     *
     * @param inputResults  results from input guards
     * @param outputResults results from output guards (empty if input was blocked)
     * @param finalVerdict  most severe verdict across all guards
     */
    public record GuardrailResponse(
            List<GuardResult> inputResults,
            List<GuardResult> outputResults,
            GuardrailVerdict finalVerdict
    ) {
        public GuardrailResponse {
            if (inputResults == null) inputResults = List.of();
            if (outputResults == null) outputResults = List.of();
        }

        /** Returns true if the request should be allowed to proceed. */
        public boolean isAllowed() {
            return finalVerdict != BLOCK;
        }

        /** Returns all results that did not pass. */
        public List<GuardResult> triggered() {
            var all = new java.util.ArrayList<>(inputResults);
            all.addAll(outputResults);
            return all.stream().filter(r -> r.verdict() != PASS).toList();
        }
    }
}
