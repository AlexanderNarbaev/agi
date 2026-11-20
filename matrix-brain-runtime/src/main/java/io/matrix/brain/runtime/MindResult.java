package io.matrix.brain.runtime;

import java.util.List;

/**
 * MIND-W1 — Result of one full cognitive cycle.
 *
 * <p>Returned by {@link MindCycle#think(String)}. Carries the reply,
 * confidence, FROZEN-modulators fired, and the ordered BRC trace so the
 * caller can see exactly how the mind arrived at the answer.</p>
 *
 * <p>EPISTEMIC-INTEGRITY P0.4 — the result also carries the {@link EpisodeRole} of
 * the traffic that produced it. The role travels with the result so the caller that
 * writes the episode to the durable log can declare the provenance instead of
 * guessing it: an evaluation harness that ran the cycle knows its traffic is an
 * experiment, and the log must record that fact rather than leave it to
 * convention.</p>
 */
public record MindResult(
    String reply,
    double confidence,
    long durationMs,
    boolean accepted,
    List<String> modulatorsFired,
    List<BrcStep> trace,
    EpisodeRole role
) {
    public MindResult {
        modulatorsFired = List.copyOf(modulatorsFired);
        trace = List.copyOf(trace);
        role = role == null ? EpisodeRole.LEGACY_UNTAGGED : role;
    }

    /**
     * The legacy shape: a result with no declared provenance.
     *
     * <p>EPISTEMIC-INTEGRITY P0.4 (review fix F3): UNDECLARED
     * ({@link EpisodeRole#LEGACY_UNTAGGED}), not organic. The result carries no
     * provenance claim, and the caller that writes it declares one if it knows it.
     * An undeclared record is still learning-eligible, so this does not disable
     * learning.</p>
     */
    public MindResult(String reply, double confidence, long durationMs, boolean accepted,
                      List<String> modulatorsFired, List<BrcStep> trace) {
        this(reply, confidence, durationMs, accepted, modulatorsFired, trace,
            EpisodeRole.LEGACY_UNTAGGED);
    }
}
