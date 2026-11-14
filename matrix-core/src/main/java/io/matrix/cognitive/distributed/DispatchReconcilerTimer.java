package io.matrix.cognitive.distributed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * W53 — the reconciliation pass on a timer.
 *
 * <p><b>Why this exists.</b> The reconciliation pass used to run when chain A started and
 * nowhere else. That covers the crash: the process died, the unfinished work is on
 * record, the restart finishes it. It does not cover the case where the process stays up
 * and an answer is lost anyway — a connection that drops, a peer that is restarted
 * mid-flight — because then the task waits for the next restart, which may be never.
 * The timer closes that window.</p>
 *
 * <p><b>What the timer sends.</b> The same request again, with the same request id, so
 * the peer's durable inbox recognises it and the logical effect still happens once.
 * That is at-least-once delivery with effectively-once logical effects, and it is
 * deliberately not a distributed exactly-once claim.</p>
 *
 * <p><b>What the timer does not do.</b> It does not decide by the wall clock. The clock
 * decides <i>when</i> the pass runs; the pass decides <i>what</i> to send, by the
 * durable record and by what this process is working on right now — a dispatch that is
 * in flight is left alone, because a second delivery of a request that is still running
 * spends one retry of the durable budget for nothing.</p>
 */
public final class DispatchReconcilerTimer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DispatchReconcilerTimer.class);

    /** How often the pass runs, unless the deployment names another interval. */
    public static final long DEFAULT_INTERVAL_SECONDS = 30L;

    private final DispatchReconciler reconciler;
    private final long intervalSeconds;
    private final ScheduledExecutorService scheduler;

    /** Whether the pass was already scheduled: the timer starts once. */
    private final java.util.concurrent.atomic.AtomicBoolean started =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * The timer, with the default interval.
     *
     * @param reconciler the pass the timer runs
     */
    public DispatchReconcilerTimer(DispatchReconciler reconciler) {
        this(reconciler, DEFAULT_INTERVAL_SECONDS);
    }

    /**
     * The timer, with its own interval.
     *
     * @param reconciler      the pass the timer runs
     * @param intervalSeconds how often the pass runs, in seconds
     */
    public DispatchReconcilerTimer(DispatchReconciler reconciler, long intervalSeconds) {
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("the interval must be positive");
        }
        this.reconciler = reconciler;
        this.intervalSeconds = intervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "w53-dispatch-reconciler");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    /**
     * Starts the pass.
     *
     * <p>The first run is delayed by one interval: the startup pass has already run, and
     * the process has just come up, so there is nothing to finish yet.</p>
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            // A second start would schedule the pass twice, and two passes would send
            // the same unfinished dispatch twice in the same tick.
            return;
        }
        scheduler.scheduleWithFixedDelay(this::runOnce, intervalSeconds, intervalSeconds,
                TimeUnit.SECONDS);
    }

    /**
     * Runs the pass once.
     *
     * <p>This is public so that a test can drive the pass without waiting for a clock.
     * The pass itself is the same either way.</p>
     */
    public void runOnce() {
        try {
            DispatchReconciler.Summary summary = reconciler.reconcileStale();
            if (summary.examined() > 0) {
                LOG.info("the reconciliation timer examined {} unfinished dispatch(es), "
                        + "replayed {}, finished {}", summary.examined(), summary.replayed(),
                        summary.finished());
                summary.reasons().forEach(reason -> LOG.warn("the reconciliation timer: {}",
                        reason));
            }
        } catch (RuntimeException e) {
            // A peer that is down is a fact, not a crash: the next tick tries again.
            LOG.warn("the reconciliation timer could not run: {}", e.getMessage());
        }
    }

    /**
     * Stops the pass.
     */
    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
