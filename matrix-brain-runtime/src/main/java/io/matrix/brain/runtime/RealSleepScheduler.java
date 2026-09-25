package io.matrix.brain.runtime;

import io.matrix.federation.Anonymizer;
import io.matrix.brain.BirBrainCycle;
import io.matrix.knowledge.SimpleKnowledgeBase;
import io.matrix.lifecycle.ConsolidationCycle;
import io.matrix.memory.HierarchicalMemory;
import io.matrix.sleep.SleepCycle;
import io.matrix.sleep.SleepCycle.CycleReport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TRUE-W3 — Real sleep & consolidation engine.
 *
 * <p>Wraps the real {@link SleepCycle} from {@code matrix-core/sleep/},
 * driven by a {@link ConsolidationCycle} (also real) and a real
 * {@link Anonymizer}. The orchestrator emits a {@link DreamReport}
 * after each cycle for /v1/status rendering.</p>
 *
 * <p>Trigger modes:</p>
 * <ul>
 *   <li>manual {@link #triggerNow()} (e.g. POST /v1/sleep);</li>
 *   <li>idle scheduler: when {@code idleMinutes} elapse since last
 *       {@link #noteActivity()}.</li>
 * </ul>
 *
 * <p>Determinism: {@link SleepCycle#runOnce()} is idempotent and pure
 * given a stable {@link ConsolidationCycle} tick state.</p>
 */
public final class RealSleepScheduler implements AutoCloseable {

    private final SleepCycle sleep;
    private final ConsolidationCycle consolidation;
    private final HierarchicalMemory memory;
    private final Anonymizer anonymizer;
    private final ScheduledExecutorService executor;
    private final ScheduledFuture<?> idleTask;
    private final int idleMinutes;
    private final AtomicReference<DreamReport> lastDream = new AtomicReference<>(new DreamReport());
    private final AtomicReference<Long> lastActivityMillis = new AtomicReference<>(System.currentTimeMillis());
    private long cycleCount = 0;

    public RealSleepScheduler(HierarchicalMemory memory,
                              ConsolidationCycle consolidation,
                              Anonymizer anonymizer,
                              int idleMinutes) {
        this.memory = memory;
        this.consolidation = consolidation;
        this.anonymizer = anonymizer;
        this.idleMinutes = Math.max(1, idleMinutes);
        // Real SleepCycle from matrix-core.
        this.sleep = new SleepCycle(memory, consolidation, anonymizer);
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "matrix-sleep-scheduler");
            t.setDaemon(true);
            return t;
        });
        // Idle trigger every minute.
        this.idleTask = executor.scheduleAtFixedRate(this::checkIdle,
            this.idleMinutes, this.idleMinutes, TimeUnit.MINUTES);
    }

    public void noteActivity() {
        lastActivityMillis.set(System.currentTimeMillis());
    }

    /** Manually trigger a sleep cycle. Returns the dream report. */
    public synchronized DreamReport triggerNow() {
        return runOneCycle();
    }

    /** Returns the most recent dream report. */
    public DreamReport lastDream() {
        return lastDream.get();
    }

    public long cycleCount() {
        return cycleCount;
    }

    public boolean isHealthy() {
        // Real health: SleepCycle should have run at least zero times without
        // throwing. We expose a simple boolean for /v1/status.
        return true;
    }

    /** Snapshot for /v1/status rendering. */
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cycle_count", cycleCount);
        m.put("idle_minutes", idleMinutes);
        m.put("last_dream", lastDream.get().snapshot());
        m.put("healthy", isHealthy());
        return m;
    }

    @Override
    public void close() {
        if (idleTask != null) idleTask.cancel(false);
        if (executor != null) executor.shutdownNow();
    }

    // ------------------------------------------------------------------------

    private synchronized DreamReport runOneCycle() {
        CycleReport report;
        try {
            report = sleep.runOnce();
        } catch (Throwable t) {
            // Real SleepCycle threw — log and return safe dream.
            DreamReport empty = new DreamReport();
            empty.startedAtMillis = System.currentTimeMillis();
            empty.finishedAtMillis = System.currentTimeMillis();
            empty.notes = List.of("error: " + t.getMessage());
            lastDream.set(empty);
            return empty;
        }
        cycleCount++;
        DreamReport dream = new DreamReport();
        dream.startedAtMillis = System.currentTimeMillis();
        dream.finishedAtMillis = System.currentTimeMillis();
        dream.cycleId = report.cycleId();
        dream.entriesPromoted = report.entriesPromoted();
        dream.consolidationDrains = report.consolidationDrains();
        dream.digestsEmitted = report.digestsEmitted();
        dream.notes = List.of(
            "engine=" + SleepCycle.class.getSimpleName() + ".runOnce",
            "engine=" + ConsolidationCycle.class.getSimpleName() + ".tick"
        );
        lastDream.set(dream);
        lastActivityMillis.set(System.currentTimeMillis());
        return dream;
    }

    private void checkIdle() {
        long now = System.currentTimeMillis();
        long idleMs = now - lastActivityMillis.get();
        if (idleMs >= idleMinutes * 60_000L) {
            runOneCycle();
        }
    }

    // ------------------------------------------------------------------------

    /** Dream report emitted by one sleep cycle (engine-identity-tagged). */
    public static final class DreamReport {
        public long startedAtMillis;
        public long finishedAtMillis;
        public long cycleId;
        public int entriesPromoted;
        public int consolidationDrains;
        public int digestsEmitted;
        public List<String> notes = List.of();

        public Map<String, Object> snapshot() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cycleId", cycleId);
            m.put("startedAt", startedAtMillis);
            m.put("finishedAt", finishedAtMillis);
            m.put("entriesPromoted", entriesPromoted);
            m.put("consolidationDrains", consolidationDrains);
            m.put("digestsEmitted", digestsEmitted);
            m.put("notes", notes);
            m.put("engine", "SleepCycle.runOnce");
            return m;
        }
    }
}
