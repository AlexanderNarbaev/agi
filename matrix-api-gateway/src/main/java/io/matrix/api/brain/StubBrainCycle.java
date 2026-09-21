package io.matrix.api.brain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WAVE T-02 — Stub BrainCycle for development and tests.
 *
 * <p>Returns deterministic synthetic results so the gateway can be tested
 * end-to-end without spinning up the full matrix-core inference pipeline.
 * T-02.5 replaces this with a real {@code BrainCycle} wired to
 * {@code io.matrix.brain.BirBrainCycle} via dependency injection.</p>
 *
 * <p><b>CONSTITUTION compliance:</b> The stub performs no LLM call. All
 * logic is rule-based and deterministic given a fixed seed.</p>
 */
public final class StubBrainCycle implements BrainCycle {

    private final Map<String, ExplainTrace> traces = new ConcurrentHashMap<>();

    @Override
    public CycleResult cycle(String input, String context, String model) {
        long start = System.nanoTime();
        String explainId = "expl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String reply;
        double confidence;
        boolean accepted;
        List<String> modulators;

        // Deterministic stub logic — keyword-based classification
        String lower = input.toLowerCase().trim();
        if (lower.contains("hello") || lower.contains("hi ") || lower.equals("hi")) {
            reply = "Hello from MATRIX. How can I help you today?";
            confidence = 0.95;
            accepted = true;
            modulators = List.of("ETHICAL_FILTER", "CONSISTENCY_CHECKER");
        } else if (lower.contains("2+2") || lower.contains("2 + 2")) {
            reply = "4";
            confidence = 0.99;
            accepted = true;
            modulators = List.of("ETHICAL_FILTER", "CONSISTENCY_CHECKER");
        } else if (lower.length() > 1024) {
            // Anti-pattern: very long input gets low confidence
            reply = "I need a more focused question.";
            confidence = 0.30;
            accepted = false;
            modulators = List.of("ETHICAL_FILTER", "LIE_DETECTOR");
        } else {
            reply = "I acknowledge your input. (stub)";
            confidence = 0.70;
            accepted = true;
            modulators = List.of("ETHICAL_FILTER");
        }

        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        CycleResult result = new CycleResult(reply, confidence, durationMs, accepted, modulators);
        traces.put(explainId, buildTraceFromResult(explainId, result));
        return result;
    }

    @Override
    public ExplainTrace buildExplain(String explainId) {
        return Optional.ofNullable(traces.get(explainId))
            .orElseThrow(() -> new IllegalArgumentException("Unknown explain_id: " + explainId));
    }

    private ExplainTrace buildTraceFromResult(String explainId, CycleResult result) {
        List<CycleResult> steps = new ArrayList<>();
        steps.add(new CycleResult("INPUT_NORMALIZED", 0.99, 1, true, List.of()));
        steps.add(new CycleResult("BIR_RULES_FIRED", 0.85, 3, true, List.of()));
        steps.add(new CycleResult("HDC_MEMORY_RETRIEVED", 0.78, 5, true, List.of()));
        steps.add(new CycleResult("MCTS_PLAN_SELECTED", 0.72, 4, true, List.of()));
        steps.add(new CycleResult("MODULATORS_APPLIED", result.confidence(), 2, result.accepted(), result.modulatorsFired()));
        steps.add(result);
        return new ExplainTrace(
            explainId, steps,
            0.95, 0.92, 0.88, 0.91,
            List.of("kb-doc-42", "kb-doc-128"),
            0.85, 0.78, 0.72,
            result.confidence()
        );
    }
}
