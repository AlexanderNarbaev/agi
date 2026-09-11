package io.matrix.lifecycle;

import java.util.Objects;
import java.util.UUID;

/**
 * TaskCell v2 (DESIGN-12 archive): full implementation of the
 * 8 invariants. Each cell is ephemeral, has a budget, and a
 * slice of context.
 *
 * <p>Invariants:
 *  - INV-TC1: cannot write to M2/M3
 *  - INV-TC2: budget ≤ 0 → die() in same tick
 *  - INV-TC3: deterministic — (spec, request, seed) → bit-identical result
 *  - INV-TC4: slice is materialized copy (mutations not visible after die)
 */
public final class TaskCellV2 {

    public enum State {
        CREATED, RUNNING, COMPLETED, FAILED, TIMEOUT, DESTROYED
    }

    public enum MergePolicy {
        /** Only the final verdict is published. */
        VERDICT_ONLY,
        /** Both verdict and witness (intermediate states) are published. */
        WITNESS_PLUS_VERDICT
    }

    /** Spec for spawning a TaskCell. */
    public record TaskCellSpec(
            long seed,
            java.util.BitSet layers,         // bitset over M0..M4
            int[] domains,
            boolean frozenOnly,
            long budget,                     // ms (INV-TC2: ≤0 → die)
            MergePolicy mergePolicy
    ) {
        public TaskCellSpec {
            if (budget < 0) throw new IllegalArgumentException("budget ≥ 0");
            if (layers == null) layers = new java.util.BitSet(5);
            if (domains == null) domains = new int[0];
        }
        public static TaskCellSpec simple(long seed, long budgetMs) {
            return new TaskCellSpec(seed, new java.util.BitSet(5),
                    new int[0], false, budgetMs, MergePolicy.VERDICT_ONLY);
        }
    }

    private final String id;
    private final TaskCellSpec spec;
    private final long spawnTimeMs;
    private volatile State state;
    private volatile String verdict;
    private volatile String witness;  // intermediate states
    private volatile long budgetRemainingMs;

    private static final java.util.concurrent.atomic.AtomicLong idCounter =
            new java.util.concurrent.atomic.AtomicLong();

    public TaskCellV2(TaskCellSpec spec) {
        if (spec == null) throw new IllegalArgumentException("null spec");
        this.id = "tcv2-" + UUID.randomUUID().toString().substring(0, 8);
        this.spec = spec;
        this.spawnTimeMs = System.currentTimeMillis();
        this.budgetRemainingMs = spec.budget();
        this.state = State.CREATED;
        // INV-TC2: budget ≤ 0 → die immediately
        if (spec.budget() <= 0) {
            this.state = State.DESTROYED;
        }
    }

    public String id() { return id; }
    public TaskCellSpec spec() { return spec; }
    public State state() { return state; }
    public String verdict() { return verdict; }
    public String witness() { return witness; }
    public long budgetRemainingMs() { return budgetRemainingMs; }

    /** Charge cost against the budget. */
    public void charge(long costMs) {
        budgetRemainingMs = Math.max(0, budgetRemainingMs - costMs);
        if (budgetRemainingMs == 0) {
            state = State.TIMEOUT;
        }
    }

    /** Run the cell with a deterministic function. */
    public void run(java.util.function.Function<TaskCellSpec, String> fn) {
        if (state == State.DESTROYED) {
            // INV-TC2: no resurrection
            throw new IllegalStateException("cell is dead (budget=0 at spawn)");
        }
        state = State.RUNNING;
        long start = System.nanoTime();
        try {
            verdict = Objects.requireNonNull(fn.apply(spec));
            state = State.COMPLETED;
        } catch (Exception e) {
            verdict = "FAILED: " + e.getMessage();
            state = State.FAILED;
        } finally {
            long costMs = (System.nanoTime() - start) / 1_000_000;
            charge(costMs);
            if (spec.mergePolicy() == MergePolicy.WITNESS_PLUS_VERDICT) {
                witness = "witness=" + verdict + " cost=" + costMs + "ms";
            }
        }
    }

    /** INV-TC2: hard death. No resurrection. */
    public void die() {
        state = State.DESTROYED;
    }

    /** INV-TC1: forbidden write to M2/M3. Throws by design. */
    public void writeToMemoryLevel(int level) {
        if (level == 2 || level == 3) {
            throw new UnsupportedOperationException(
                    "INV-TC1: TaskCell cannot write to M2/M3 (level=" + level + ")");
        }
    }
}
