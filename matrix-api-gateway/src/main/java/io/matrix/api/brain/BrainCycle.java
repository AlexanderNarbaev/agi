package io.matrix.api.brain;

import java.util.List;

/**
 * WAVE T-02 — Brain cycle facade (gateway-side interface).
 *
 * <p>Mirrors the matrix-core {@code BrainCycle} interface but lives in the
 * gateway module to avoid a hard compile-time dependency on matrix-core
 * during T-02 development. T-02.5 will swap the stub implementation for
 * the real matrix-core-backed one via {@code project(':matrix-core')}.</p>
 *
 * <p><b>CONSTITUTION:</b> Inference happens server-side only. No LLM is
 * invoked; BIR/HDC/MCTS run in-process. The gateway is pure transport.</p>
 */
public interface BrainCycle {

    /** Result of one inference cycle. */
    record CycleResult(
        String reply,
        double confidence,
        long durationMs,
        boolean accepted,
        List<String> modulatorsFired
    ) {}

    /** Run one hybrid inference cycle. */
    CycleResult cycle(String input, String context, String model);

    /** Build an XAI explanation for a previously-computed result. */
    ExplainTrace buildExplain(String explainId);
}
