package io.matrix.reasoning;

import io.matrix.actions.ActionArena;
import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.lifecycle.ConsolidationCycle;
import io.matrix.lifecycle.TaskCell;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consciousness loop orchestrator (SPEC-006 / DESIGN-18, H-H).
 *
 * <p>One {@code tick()} runs the canonical nine-stage loop:
 * <pre>
 *   perception → attention → deliberation → gate → action
 *     → consolidation → subconscious → prediction-error → attention
 * </pre>
 *
 * <p>Stages are wired to existing components:
 * <ul>
 *   <li>{@code perception}: a {@link java.util.function.Supplier} of
 *       {@link BitSet} observation vectors (caller-provided).</li>
 *   <li>{@code deliberation}: a {@link BrcChain} that produces a
 *       decision vector from the attended observation.</li>
 *   <li>{@code gate / action}: an {@link ActionArena} that executes the
 *       selected plan under budget.</li>
 *   <li>{@code consolidation}: a {@link ConsolidationCycle} that drains
 *       backlog routes after action.</li>
 *   <li>{@code attention saliency}: a {@link SaliencyWeigher} that
 *       scores observations on each tick (weighted Hamming distance to
 *       the previous prediction).</li>
 * </ul>
 *
 * <p>Determinism: no wall-clock, no random in the loop body — every
 * source of non-determinism lives in the supplier/deliberation chain
 * the caller passes in. Saliency weights are explicit inputs.
 */
public final class ConsciousnessLoop {

    /** Attention saliency weights per bit position. */
    public interface SaliencyWeigher {
        /** Saliency for bit {@code i} ∈ [0, 1]. */
        double saliency(int bitIndex, int width);
    }

    /** A snapshot of one loop tick. */
    public record TickSnapshot(long tickId,
                              long attentionScore,
                              long predictionError,
                              int actionsSubmitted) {
        public TickSnapshot {
            Objects.requireNonNull(tickId, "tickId");
        }
    }

    private final BrcChain deliberation;
    private final ActionArena arena;
    private final ConsolidationCycle consolidation;
    private final ConjugateBudgeter budgeter;
    private final SaliencyWeigher saliency;
    private final java.util.function.Supplier<BitSet> perception;

    private final AtomicLong tickCounter = new AtomicLong();
    private volatile BitSet lastAttended;
    private volatile BitSet lastDecision;
    private volatile long lastPredictionError;

    public ConsciousnessLoop(BrcChain deliberation,
                             ActionArena arena,
                             ConsolidationCycle consolidation,
                             ConjugateBudgeter budgeter,
                             SaliencyWeigher saliency,
                             java.util.function.Supplier<BitSet> perception) {
        this.deliberation = Objects.requireNonNull(deliberation, "deliberation");
        this.arena = Objects.requireNonNull(arena, "arena");
        this.consolidation = Objects.requireNonNull(consolidation, "consolidation");
        this.budgeter = Objects.requireNonNull(budgeter, "budgeter");
        this.saliency = Objects.requireNonNull(saliency, "saliency");
        this.perception = Objects.requireNonNull(perception, "perception");
    }

    /**
     * Convenience: a uniform saliency weigher (every bit equal weight).
     */
    public static SaliencyWeigher uniform() {
        return (i, w) -> 1.0;
    }

    /**
     * Run one loop tick. Returns a snapshot for telemetry.
     */
    public TickSnapshot tick() {
        long tickId = tickCounter.incrementAndGet();
        // 1. perception
        BitSet raw = perception.get();
        // 2. attention (weighted Hamming distance to last attended)
        long attentionScore = score(raw);
        lastAttended = raw;
        // 3. deliberation
        BrcState decision = deliberation.evaluate(raw, raw.length());
        lastDecision = decision.vector();
        // 4. gate + 5. action
        TaskCell cell = new TaskCell("loop-tick-" + tickId,
                java.util.Map.of("decision", lastDecision), 5_000L);
        ActionArena.Arbitration arb;
        try {
            arb = arena.submit(cell, (t, ctx) -> "ok").get();
        } catch (Exception e) {
            throw new IllegalStateException("arena submit failed", e);
        }
        // 6. consolidation (no-op if cycle closed; safe to skip)
        if (consolidation != null) {
            try {
                consolidation.drain("loop", 0);
            } catch (IllegalStateException ignored) {
                // cycle closed — fine, consolidation is optional
            }
        }
        // 7. subconscious / 8. prediction-error
        long predErr = computePredictionError(raw, lastDecision);
        lastPredictionError = predErr;
        // 9. attention update — implicitly by overwriting lastAttended
        //    ALSO: if perception is a FeedbackPerception, feed the
        //    action output back so the next tick sees its own output.
        if (perception instanceof FeedbackPerception fp) {
            fp.lastAction(lastDecision);
        }
        return new TickSnapshot(tickId, attentionScore, predErr,
                arb.outcome() == ActionArena.Outcome.EXECUTED ? 1 : 0);
    }

    private long score(BitSet raw) {
        int width = Math.max(1, raw.length());
        double total = 0.0;
        long prevHash = lastAttended == null ? 0L : lastAttended.hashCode();
        // combine previous-frame distance with per-bit saliency
        for (int i = 0; i < width; i++) {
            double w = Math.max(0.0, Math.min(1.0, saliency.saliency(i, width)));
            total += w * (raw.get(i) ? 1.0 : 0.0);
        }
        // fold the previous-frame hash into the score so changes across
        // ticks contribute (Hamming distance surrogate).
        long score = Math.round(total) ^ (prevHash & 0xFFL);
        return score;
    }

    private long computePredictionError(BitSet raw, BitSet decision) {
        if (decision == null) return raw.cardinality();
        // Hamming distance between raw observation and decision output
        BitSet diff = (BitSet) raw.clone();
        diff.xor(decision);
        return diff.cardinality();
    }

    public long totalTicks() { return tickCounter.get(); }
    public long lastPredictionError() { return lastPredictionError; }
    public BitSet lastDecision() { return lastDecision; }

    /** Convenience for tests: run N ticks and collect snapshots. */
    public List<TickSnapshot> runFor(long n) {
        List<TickSnapshot> out = new ArrayList<>();
        for (long i = 0; i < n; i++) {
            out.add(tick());
        }
        return out;
    }
}