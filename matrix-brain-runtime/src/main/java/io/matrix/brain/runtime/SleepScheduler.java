package io.matrix.brain.runtime;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MIND-W3 — Sleep scheduler.
 *
 * <p>Triggers a consolidation cycle either:</p>
 * <ul>
 *   <li>manually (via {@link #triggerNow()}) — e.g. on POST /v1/sleep;</li>
 *   <li>periodically (after {@code idleMinutes} of no activity).</li>
 * </ul>
 *
 * <p>The most recent dream report is cached in {@link #lastDream()} so the
 * gateway can surface it via /v1/status.</p>
 */
public final class SleepScheduler implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SleepScheduler.class.getName());

    private final EpisodicLog episodic;
    private final PersistentHdcStore hdc;
    private final ConsolidationCycle cycle;
    private final int idleMinutes;
    private final AtomicReference<ConsolidationCycle.DreamReport> lastDream =
        new AtomicReference<>(new ConsolidationCycle.DreamReport());
    private final AtomicReference<Long> lastActivityMillis = new AtomicReference<>(System.currentTimeMillis());
    private final ScheduledExecutorService executor;
    private final ScheduledFuture<?> idleTask;

    public SleepScheduler(EpisodicLog episodic, PersistentHdcStore hdc,
                          ConsolidationCycle cycle, int idleMinutes) {
        this.episodic = episodic;
        this.hdc = hdc;
        this.cycle = cycle;
        this.idleMinutes = Math.max(1, idleMinutes);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "matrix-sleep-scheduler");
            t.setDaemon(true);
            return t;
        });
        // Check every minute for idleness.
        this.idleTask = executor.scheduleAtFixedRate(this::checkIdle,
            this.idleMinutes, this.idleMinutes, TimeUnit.MINUTES);
        LOG.log(Level.INFO, "SleepScheduler armed: idleMinutes={0}", this.idleMinutes);
    }

    /** Called by gateway on every cycle() so the scheduler can track activity. */
    public void noteActivity() {
        lastActivityMillis.set(System.currentTimeMillis());
    }

    /** Manually trigger a sleep/consolidation cycle. Returns the dream report. */
    public ConsolidationCycle.DreamReport triggerNow() {
        return runCycle();
    }

    /** Returns the most recent dream report (or an empty placeholder). */
    public ConsolidationCycle.DreamReport lastDream() {
        return lastDream.get();
    }

    /** Count of times a sleep cycle has completed. */
    private final java.util.concurrent.atomic.AtomicInteger cycleCount =
        new java.util.concurrent.atomic.AtomicInteger(0);

    public int cycleCount() { return cycleCount.get(); }

    private void checkIdle() {
        long now = System.currentTimeMillis();
        long last = lastActivityMillis.get();
        long idleMs = now - last;
        long thresholdMs = idleMinutes * 60_000L;
        if (idleMs >= thresholdMs) {
            LOG.log(Level.INFO,
                "SleepScheduler: idle {0} ms >= {1} ms threshold — triggering consolidation",
                new Object[]{idleMs, thresholdMs});
            runCycle();
            // Reset activity so we don't immediately re-trigger.
            lastActivityMillis.set(System.currentTimeMillis());
        }
    }

    private ConsolidationCycle.DreamReport runCycle() {
        try {
            ConsolidationCycle.DreamReport report = cycle.run(episodic, hdc);
            lastDream.set(report);
            cycleCount.incrementAndGet();
            LOG.log(Level.INFO, "Sleep cycle #{0}: replayed={1}, promoted={2}, merged={3}, forgotten={4}",
                new Object[]{cycleCount.get(), report.entriesReplayed,
                    report.promoted.size(), report.merged.size(), report.tombstoned});
            return report;
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Sleep cycle failed", t);
            ConsolidationCycle.DreamReport empty = new ConsolidationCycle.DreamReport();
            empty.startedAtMillis = System.currentTimeMillis();
            empty.finishedAtMillis = System.currentTimeMillis();
            return empty;
        }
    }

    @Override
    public void close() {
        if (idleTask != null) idleTask.cancel(false);
        if (executor != null) executor.shutdownNow();
    }

    /** Convenience constructor: standard file paths under {@code data/mind/}. */
    public static SleepScheduler standard(Path mindDir) {
        return new SleepScheduler(
            new EpisodicLog(mindDir.resolve("episodic.ndjson")),
            new PersistentHdcStore(mindDir.resolve("hdc_kb.ndjson"), 256),
            new ConsolidationCycle(),
            5 /* idleMinutes */
        );
    }
}
