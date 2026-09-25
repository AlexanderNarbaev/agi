package io.matrix.brain.runtime;

import java.util.List;

/**
 * MIND-W1 — Result of one full cognitive cycle.
 *
 * <p>Returned by {@link MindCycle#think(String)}. Carries the reply,
 * confidence, FROZEN-modulators fired, and the ordered BRC trace so the
 * caller can see exactly how the mind arrived at the answer.</p>
 */
public record MindResult(
    String reply,
    double confidence,
    long durationMs,
    boolean accepted,
    List<String> modulatorsFired,
    List<BrcStep> trace
) {
    public MindResult {
        modulatorsFired = List.copyOf(modulatorsFired);
        trace = List.copyOf(trace);
    }
}
