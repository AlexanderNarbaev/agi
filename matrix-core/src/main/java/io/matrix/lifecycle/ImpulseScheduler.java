package io.matrix.lifecycle;

import io.matrix.budgeter.ConjugateBudgeter;
import io.matrix.ethics.EthicalFilter;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fires impulses under budget through a {@link ConjugateBudgeter}.
 *
 * <p>Each impulse:
 * <ul>
 *   <li>is gated by {@link ConjugateBudgeter#allocate} — if the row set
 *       cannot fit the envelope, the impulse is rejected;</li>
 *   <li>is filtered by {@link EthicalFilter} — a forbidden impulse is
 *       rejected even if budget allows (FROZEN gate);</li>
 *   <li>reports an {@link ImpulseOutcome} for telemetry.</li>
 * </ul>
 */
public final class ImpulseScheduler {

    /** Outcome of one impulse fire attempt. */
    public enum ImpulseOutcome {
        FIRED,
        REJECTED_BUDGET,
        REJECTED_ETHICS,
        REJECTED_UNKNOWN
    }

    /** A single fire record. */
    public record FireRecord(AutonomyImpulse impulse,
                             ImpulseOutcome outcome,
                             long envelopeSpent,
                             int rowsAllocated) {
        public FireRecord {
            Objects.requireNonNull(impulse, "impulse");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    private final ConjugateBudgeter budgeter;
    private final EthicalFilter ethics;
    private final AtomicLong fireCounter = new AtomicLong();
    private final AtomicLong rejectionCounter = new AtomicLong();

    public ImpulseScheduler(ConjugateBudgeter budgeter, EthicalFilter ethics) {
        this.budgeter = Objects.requireNonNull(budgeter, "budgeter");
        this.ethics = Objects.requireNonNull(ethics, "ethics");
    }

    /**
     * Fire a single impulse under the given envelope. Cost/Value of
     * the impulse row are derived from {@code budget}: cost is the
     * envelope fraction the impulse wants to claim, value is a heuristic
     * based on impulse priority.
     */
    public FireRecord fire(AutonomyImpulse impulse, long envelope, Map<String, Object> context) {
        Objects.requireNonNull(impulse, "impulse");
        fireCounter.incrementAndGet();

        // FROZEN gate: every impulse must pass the ethical filter.
        if (ethics != null
                && ethics.frozenViolatedAxiom(impulse.name()) != null) {
            rejectionCounter.incrementAndGet();
            return new FireRecord(impulse, ImpulseOutcome.REJECTED_ETHICS, 0L, 0);
        }

        // Budget check: build a synthetic Row and run allocate.
        long cost = costFor(impulse, envelope);
        if (cost > envelope) {
            rejectionCounter.incrementAndGet();
            return new FireRecord(impulse, ImpulseOutcome.REJECTED_BUDGET, 0L, 0);
        }
        ConjugateBudgeter.Row[] rows = {
            new ConjugateBudgeter.Row(impulse.name(), valueFor(impulse), cost)
        };
        ConjugateBudgeter.Allocation alloc = budgeter.allocate(rows, envelope);
        if (alloc.mode() != ConjugateBudgeter.Mode.CONJUGATE
                || !alloc.selected()[0]) {
            rejectionCounter.incrementAndGet();
            return new FireRecord(impulse, ImpulseOutcome.REJECTED_BUDGET, 0L, 0);
        }
        return new FireRecord(impulse, ImpulseOutcome.FIRED,
                alloc.spentEnvelope(), 1);
    }

    private static long costFor(AutonomyImpulse impulse, long envelope) {
        // Fixed per-impulse cost (cheap impulses beat expensive ones when
        // the envelope is small). The envelope parameter is reserved for
        // future dynamic-cost policies and is currently unused.
        return switch (impulse) {
            case CURIOSITY        -> 100L;
            case CONSOLIDATION    -> 50L;
            case INTEGRITY_CHECK  -> 10L;
            case SHARE_DIGEST     -> 5L;
        };
    }

    private static double valueFor(AutonomyImpulse impulse) {
        return switch (impulse) {
            case CURIOSITY       -> 100.0;
            case CONSOLIDATION   -> 80.0;
            case INTEGRITY_CHECK -> 60.0;
            case SHARE_DIGEST    -> 40.0;
        };
    }

    public long totalFires() { return fireCounter.get(); }
    public long totalRejections() { return rejectionCounter.get(); }
}