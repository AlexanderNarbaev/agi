package io.matrix.guardrail;

import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Standalone guardrail engine — evaluates LLM output against safety filters.
 *
 * <p>Wraps an ethical evaluator function ({@code (text, keywords) → EthicalVerdict})
 * and provides a simple programmatic API: {@code evaluate(text) → GuardrailResult}.
 * No CDI, no JAX-RS — usable directly in tests, scripts, and embedded contexts.
 *
 * <p><b>Threshold-based rejection:</b> if the ethical evaluator returns
 * {@link EthicalVerdict#REJECTED}, the content is blocked.
 * {@link EthicalVerdict#ESCALATED} and {@link EthicalVerdict#MODIFIED}
 * are treated as warnings (allowed but flagged).
 *
 * <p><b>Factory:</b> use {@link #withFilter(EthicalFilter)} to create an engine
 * backed by the FROZEN ethical filter.
 *
 * <p><b>Performance:</b> target p99 latency ≤ 50 ms for typical input
 * (keyword matching against 6 axiom keyword sets).
 *
 * <p>Ref: Phase 2 Guardrail MVP, L7_Ethics.md §3.1–§3.3, EU AI Act Art. 9.
 */
public final class GuardrailEngine {

    private static final Logger log = LoggerFactory.getLogger(GuardrailEngine.class);

    /** Evaluator function: (text, keywords) → EthicalVerdict. */
    private final BiFunction<String, List<String>, EthicalVerdict> evaluator;

    /**
     * Creates a guardrail engine with the given evaluator function.
     *
     * @param evaluator function that evaluates text and returns an ethical verdict
     */
    GuardrailEngine(BiFunction<String, List<String>, EthicalVerdict> evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator must not be null");
    }

    /**
     * Factory: creates an engine backed by the FROZEN EthicalFilter.
     *
     * @param filter the FROZEN ethical filter (non-null)
     * @return a new GuardrailEngine
     */
    public static GuardrailEngine withFilter(EthicalFilter filter) {
        Objects.requireNonNull(filter, "filter must not be null");
        return new GuardrailEngine((text, keywords) -> filter.evaluate(text, keywords));
    }

    /**
     * Evaluates LLM output against all safety guards.
     *
     * <p>Measures wall-clock time and returns a structured result.
     * Null or empty input is treated as safe (no-op).
     *
     * @param llmOutput the text to evaluate (may be null or empty)
     * @return GuardrailResult with allow/block decision, confidence, violations, and latency
     */
    public GuardrailResult evaluate(String llmOutput) {
        return evaluate(llmOutput, null);
    }

    /**
     * Evaluates LLM output with additional caller-supplied keywords.
     *
     * @param llmOutput          the text to evaluate (may be null or empty)
     * @param additionalKeywords  extra keywords for extended axiom checking (may be null or empty)
     * @return GuardrailResult with allow/block decision, confidence, violations, and latency
     */
    public GuardrailResult evaluate(String llmOutput, List<String> additionalKeywords) {
        long start = System.nanoTime();

        // Null/empty input is safe
        if (llmOutput == null || llmOutput.isEmpty()) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.debug("Guardrail: empty input — allowed ({} ms)", elapsedMs);
            return GuardrailResult.pass(elapsedMs);
        }

        // Run through ethical evaluator
        EthicalVerdict verdict = evaluator.apply(llmOutput, additionalKeywords);

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        GuardrailResult result = switch (verdict) {
            case APPROVED -> {
                log.debug("Guardrail: approved ({} ms)", elapsedMs);
                yield GuardrailResult.pass(elapsedMs);
            }
            case REJECTED -> {
                double confidence = computeConfidence(llmOutput);
                log.warn("Guardrail: REJECTED — confidence={} latency={} ms", confidence, elapsedMs);
                yield GuardrailResult.block(confidence,
                        List.of("ethical_filter_rejected"), elapsedMs);
            }
            case ESCALATED -> {
                log.warn("Guardrail: ESCALATED — {} ms", elapsedMs);
                yield new GuardrailResult(true, 0.5,
                        List.of("ethical_filter_escalated"), elapsedMs);
            }
            case MODIFIED -> {
                log.info("Guardrail: MODIFIED — {} ms", elapsedMs);
                yield new GuardrailResult(true, 0.7,
                        List.of("ethical_filter_modified"), elapsedMs);
            }
        };

        return result;
    }

    /**
     * Computes a confidence score for rejected content.
     *
     * <p>Simple heuristic: counts violation-indicating keywords in the text.
     * Higher count → higher confidence the rejection is correct.
     */
    private double computeConfidence(String text) {
        if (text == null || text.isEmpty()) return 0.0;
        String lowered = text.toLowerCase(java.util.Locale.ROOT);

        int matchedCategories = 0;
        int totalCategories = 6;

        String[] violationIndicators = {
                "kill", "murder", "torture", "enslave", "weapon",
                "deceive", "fake", "disinformation", "leak", "dox"
        };
        for (String indicator : violationIndicators) {
            if (lowered.contains(indicator)) {
                matchedCategories = Math.min(totalCategories, matchedCategories + 1);
            }
        }

        return Math.min(1.0, 0.3 + matchedCategories * 0.12);
    }
}
