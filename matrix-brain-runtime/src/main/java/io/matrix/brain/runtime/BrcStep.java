package io.matrix.brain.runtime;

import java.util.List;

/**
 * MIND-W1 — Boolean Reasoning Chain step.
 *
 * <p>One record per cognitive stage the mind traversed while answering.
 * Returned to the caller as an ordered list, providing a verifiable
 * explanation of how the answer was produced (CONSTITUTION Article VIII —
 * "no shadow logic, every decision is a BRC chain").</p>
 *
 * <p>CONSTITUTION:</p>
 * <ul>
 *   <li>Article I: No LLM in runtime. Each stage is MATRIX-native (BIR/HDC/reflex/MCTS).</li>
 *   <li>Article VI: Use "cognitive stage", "emergent coordination" — not consciousness claims.</li>
 * </ul>
 */
public record BrcStep(
    /** Stage name (e.g. "REFLEX", "PERCEPTION", "BIR_RULES", "MCTS", "MODULATORS"). */
    String stage,
    /** Whether this stage fired / contributed to the final answer. */
    boolean fired,
    /** Stage-internal confidence in [0,1]. */
    double confidence,
    /** Evidence the stage produced (document IDs, rule IDs, retrieved neighbors, plan steps). */
    List<String> evidence
) {
    public static BrcStep of(String stage, boolean fired, double confidence, List<String> evidence) {
        return new BrcStep(stage, fired, confidence, List.copyOf(evidence));
    }

    public static BrcStep skipped(String stage) {
        return new BrcStep(stage, false, 0.0, List.of());
    }
}
