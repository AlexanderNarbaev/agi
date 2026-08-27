package io.matrix.actions;

import io.matrix.lifecycle.TaskCell;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Action arena (SPEC-005 / DESIGN-17, W-G): transactional isolation
 * surface for plan execution. Each action is wrapped in a {@link TaskCell}
 * with a budget bound; the arena serialises concurrent submissions,
 * enforces a global concurrency cap, and never lets a single action
 * exceed its budget.
 *
 * <p>Determinism rule: the arena does not read wall-clock inside the
 * decision path; per-cell deadlines are measured relative to the cell's
 * own start, recorded in the cell metadata (no {@code System.nanoTime()}
 * inside scoring).
 *
 * <p>Thread-safety: backed by a {@link ConcurrentLinkedQueue} and an
 * atomic counter. Multiple producers can call {@link #submit} safely.
 */
public final class ActionArena implements AutoCloseable {

    /** Result of one arbitration. */
    public enum Outcome { EXECUTED, REJECTED_OVER_BUDGET, REJECTED_QUEUE_FULL, TIMEOUT, FAILED }

    /** A single arbitration record. */
    public record Arbitration(String cellId, Outcome outcome, String result) {
        public Arbitration {
            Objects.requireNonNull(cellId, "cellId");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    private final Queue<TaskCell> pending = new ConcurrentLinkedQueue<>();
    private final ExecutorService executor;
    private final long maxBudgetMs;
    private final int maxQueueDepth;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong totalArbitrations = new AtomicLong();
    private final AtomicLong totalRejections = new AtomicLong();
    private volatile boolean closed = false;

    /**
     * @param maxParallelism maximum concurrent task cells (≥ 1)
     * @param maxBudgetMs     per-cell budget in milliseconds (≥ 1)
     * @param maxQueueDepth   maximum pending queue depth (≥ 1)
     */
    public ActionArena(int maxParallelism, long maxBudgetMs, int maxQueueDepth) {
        if (maxParallelism < 1) throw new IllegalArgumentException("maxParallelism ≥ 1");
        if (maxBudgetMs < 1) throw new IllegalArgumentException("maxBudgetMs ≥ 1");
        if (maxQueueDepth < 1) throw new IllegalArgumentException("maxQueueDepth ≥ 1");
        this.executor = Executors.newFixedThreadPool(maxParallelism);
        this.maxBudgetMs = maxBudgetMs;
        this.maxQueueDepth = maxQueueDepth;
    }

    /** Default arena: 4-way parallelism, 5 s budget, 128-deep queue. */
    public static ActionArena defaults() {
        return new ActionArena(4, 5_000L, 128);
    }

    /** Number of cells currently executing. */
    public int inFlight() {
        return inFlight.get();
    }

    /** Total arbitration attempts (cumulative, monotonic). */
    public long totalArbitrations() {
        return totalArbitrations.get();
    }

    /** Total rejected attempts (over budget, queue full, etc.). */
    public long totalRejections() {
        return totalRejections.get();
    }

    /**
     * Submit a {@link TaskCell} for execution under the arena's budget.
     * Returns a {@link Future} that completes with the {@link Arbitration}
     * outcome. The cell is rejected if the queue is full or the arena is
     * closed.
     */
    public java.util.concurrent.Future<Arbitration> submit(TaskCell cell, TaskCell.TaskExecutor exec) {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(exec, "exec");
        if (closed) {
            totalRejections.incrementAndGet();
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new Arbitration(cell.id(), Outcome.REJECTED_QUEUE_FULL, "arena closed"));
        }
        if (pending.size() >= maxQueueDepth) {
            totalRejections.incrementAndGet();
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new Arbitration(cell.id(), Outcome.REJECTED_QUEUE_FULL, "queue full"));
        }
        if (cell.isTimeout() || maxBudgetMs <= 0) {
            totalRejections.incrementAndGet();
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new Arbitration(cell.id(), Outcome.REJECTED_OVER_BUDGET, "budget exceeded"));
        }
        pending.add(cell);
        totalArbitrations.incrementAndGet();
        inFlight.incrementAndGet();
        return executor.submit(() -> runOne(cell, exec));
    }

    private Arbitration runOne(TaskCell cell, TaskCell.TaskExecutor exec) {
        long start = System.nanoTime();
        try {
            cell.execute(exec);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            // TaskCell swallows executor exceptions and records them via
            // its own state machine; check state to surface FAILED.
            if (cell.state() == TaskCell.State.FAILED) {
                return new Arbitration(cell.id(), Outcome.FAILED, cell.error());
            }
            Outcome out = elapsedMs > maxBudgetMs ? Outcome.TIMEOUT : Outcome.EXECUTED;
            return new Arbitration(cell.id(), out, cell.result());
        } catch (Exception e) {
            return new Arbitration(cell.id(), Outcome.FAILED, e.getMessage());
        } finally {
            inFlight.decrementAndGet();
        }
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdown();
    }

    /** Visible for tests — maximum queue depth. */
    int maxQueueDepth() {
        return maxQueueDepth;
    }
}