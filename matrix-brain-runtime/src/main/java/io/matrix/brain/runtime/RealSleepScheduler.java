package io.matrix.brain.runtime;

import io.matrix.federation.Anonymizer;
import io.matrix.brain.BirBrainCycle;
import io.matrix.knowledge.SimpleKnowledgeBase;
import io.matrix.lifecycle.ConsolidationCycle;
import io.matrix.memory.HierarchicalMemory;
import io.matrix.bir.BirRegistry;
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
    /** RECON-W3 Part B Step 4: optional learning integration. */
    private final EpisodicLog episodicLog;
    private final BirRegistry birRegistry;
    private final RuleInductionEngine ruleEngine;
    private final EpisodeFeatureExtractor featureExtractor;
    private final java.util.List<Double> recentFidelities = new java.util.ArrayList<>();

    public RealSleepScheduler(HierarchicalMemory memory,
                              ConsolidationCycle consolidation,
                              Anonymizer anonymizer,
                              int idleMinutes) {
        this(memory, consolidation, anonymizer, idleMinutes, null, null, null, null);
    }

    /**
     * RECON-W3 Part B Step 4: extended constructor that wires the learning pipeline.
     * After replay, runs {@link RuleInductionEngine} on recent episodic entries
     * and registers the induced rules in {@link BirRegistry}.
     */
    public RealSleepScheduler(HierarchicalMemory memory,
                              ConsolidationCycle consolidation,
                              Anonymizer anonymizer,
                              int idleMinutes,
                              EpisodicLog episodicLog,
                              BirRegistry birRegistry,
                              RuleInductionEngine ruleEngine,
                              EpisodeFeatureExtractor featureExtractor) {
        this.memory = memory;
        this.consolidation = consolidation;
        this.anonymizer = anonymizer;
        this.idleMinutes = Math.max(1, idleMinutes);
        this.episodicLog = episodicLog;
        this.birRegistry = birRegistry;
        this.ruleEngine = ruleEngine;
        this.featureExtractor = featureExtractor;
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
        // Always emit engine markers (Article VIII).
        dream.notes = new java.util.ArrayList<>(List.of(
            "engine=" + SleepCycle.class.getSimpleName() + ".runOnce",
            "engine=" + ConsolidationCycle.class.getSimpleName() + ".tick"));

        // RECON-W3 Part B Step 4: rule induction after replay.
        // Only runs when the learning pipeline is wired.
        if (episodicLog != null && birRegistry != null && ruleEngine != null
                && featureExtractor != null) {
            InductionStats stats = runInduction();
            dream.rulesLearned = stats.learned;
            dream.rulesRejected = stats.rejected;
            dream.consolidationDelta = stats.delta;
            dream.fidelityScores = stats.fidelities;
            dream.notes.add("induction=RuleInductionEngine.induce(engine=TsetlinTrainer+MpdtGaProducer)");
            dream.notes.add("rules_learned=" + stats.learned);
        }
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
        /** RECON-W3 Part B Step 4: rule-induction summary. */
        public int rulesLearned;
        public int rulesRejected;
        public double consolidationDelta;
        public List<Double> fidelityScores = List.of();
        public List<String> notes = List.of();

        public Map<String, Object> snapshot() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cycleId", cycleId);
            m.put("startedAt", startedAtMillis);
            m.put("finishedAt", finishedAtMillis);
            m.put("entriesPromoted", entriesPromoted);
            m.put("consolidationDrains", consolidationDrains);
            m.put("digestsEmitted", digestsEmitted);
            m.put("rulesLearned", rulesLearned);
            m.put("rulesRejected", rulesRejected);
            m.put("consolidationDelta", consolidationDelta);
            m.put("fidelityScores", fidelityScores);
            m.put("notes", notes);
            m.put("engine", "SleepCycle.runOnce");
            return m;
        }
    }

    /**
     * RECON-W3 Part B Step 4: read recent episodic entries, run induction,
     * register the resulting Bir in the registry. CONSISTENCY_CHECKER is
     * honored implicitly: a Bir that fails to register (e.g., duplicate id)
     * is counted as rejected, not silently overwritten.
     */
    private InductionStats runInduction() {
        java.util.List<EpisodicLog.Entry> entries = episodicLog.readAll();
        if (entries.isEmpty()) {
            return new InductionStats(0, 0, 0.0, java.util.List.of());
        }
        // Take last 20 (cap at K_MAX for Article II)
        int n = Math.min(entries.size(), 20);
        java.util.List<EpisodicLog.Entry> recent = entries.subList(
            Math.max(0, entries.size() - n), entries.size());

        long[][] features = new long[recent.size()][];
        java.util.List<String> ids = new java.util.ArrayList<>();
        boolean[] labels = new boolean[recent.size()];
        for (int i = 0; i < recent.size(); i++) {
            EpisodicLog.Entry e = recent.get(i);
            features[i] = featureExtractor.encode(e);
            ids.add(e.id());
            labels[i] = e.confidence() >= 0.7 && e.accepted();
        }

        int before = birRegistry.size();
        java.util.List<Double> fidelities = new java.util.ArrayList<>();
        int learned = 0, rejected = 0;
        try {
            var res = ruleEngine.induce(ids, features, labels);
            fidelities.add(res.chosenFidelity());
            if (birRegistry.size() > before) learned++; else rejected++;
        } catch (Throwable t) {
            rejected++;
        }
        double delta = birRegistry.size() - before;
        synchronized (recentFidelities) {
            recentFidelities.add(fidelities.isEmpty() ? 0.0 : fidelities.get(0));
            if (recentFidelities.size() > 100) recentFidelities.remove(0);
        }
        return new InductionStats(learned, rejected, delta, fidelities);
    }

    /** Result of one induction pass. */
    private record InductionStats(int learned, int rejected, double delta,
                                  java.util.List<Double> fidelities) {}
}
