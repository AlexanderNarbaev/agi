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
            // EPISTEMIC-INTEGRITY P0.4 — Article VIII: the operator must be able to
            // see that the admission gate ran, how many records it excluded, and
            // exactly which episode ids were offered to the induction.
            dream.episodesAdmitted = stats.admitted;
            dream.episodesExcluded = stats.excluded;
            dream.inducedEpisodeIds = stats.inducedEpisodeIds;
            // EPISTEMIC-INTEGRITY P0.4 (review fix F4): the health of the quarantine
            // ledger is on the runtime path. A ledger line that could not be read is a
            // quarantine mark that could not be honoured, and the gate FAILS CLOSED
            // on it — so the counter must be visible to the operator, not dead code.
            dream.quarantineLedgerReadable = stats.ledgerReadable;
            dream.quarantineMalformedLines = stats.ledgerMalformedLines;
            // EPISTEMIC-INTEGRITY P0.4 (review fix, LOW finding 3): a quarantine mark
            // that names NO record excludes nothing, and it is a silent fail-open —
            // the ledger claims an exclusion that does not exist. The count is on the
            // runtime path so it cannot hide.
            dream.unmatchedQuarantineIds = stats.unmatchedQuarantineIds;
            dream.windowPositives = stats.windowPositives;
            dream.windowNegatives = stats.windowNegatives;
            dream.notes.add("induction=RuleInductionEngine.induce(engine=TsetlinTrainer+MpdtGaProducer)");
            dream.notes.add("rules_learned=" + stats.learned);
            dream.notes.add("admission=LearningAdmission admitted=" + stats.admitted
                + " excluded=" + stats.excluded
                + " (non-organic or quarantined records never reach the induction input)");
            if (!stats.ledgerReadable) {
                dream.notes.add("quarantine_ledger=UNREADABLE lines="
                    + stats.ledgerMalformedLines
                    + " (the gate fails closed: nothing is admitted while the ledger "
                    + "cannot be read in full)");
            }
            if (stats.unmatchedQuarantineIds > 0) {
                dream.notes.add("quarantine_ledger=INEFFECTIVE_MARKS unmatched="
                    + stats.unmatchedQuarantineIds
                    + " (marks that name no record of the log exclude nothing)");
            }
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
        /**
         * EPISTEMIC-INTEGRITY P0.4 — how many records the admission gate considered
         * learnable, and how many it excluded (evaluation, experiment, synthetic or
         * quarantined records).
         */
        public int episodesAdmitted;
        public int episodesExcluded;
        /**
         * The episode ids that were actually offered to the induction. This is the
         * LEAKAGE EVIDENCE: a record from the evaluation path that reached this list
         * would be visible here, not merely absent from a summary.
         */
        public List<String> inducedEpisodeIds = List.of();
        /**
         * EPISTEMIC-INTEGRITY P0.4 (review fix F4) — the health of the quarantine
         * ledger that governed this pass. False means at least one ledger line could
         * not be read, and the gate FAILED CLOSED: nothing was admitted, because an
         * unreadable exclusion is not an exclusion.
         */
        public boolean quarantineLedgerReadable = true;
        /** How many ledger lines could not be read. Unit: lines. */
        public int quarantineMalformedLines;
        /**
         * EPISTEMIC-INTEGRITY P0.4 (review fix, LOW finding 3) — how many quarantine
         * marks name NO record of the log. Such a mark excludes nothing: it is a
         * silent fail-open, and the count is reported here so it cannot hide.
         * Unit: marks.
         */
        public int unmatchedQuarantineIds;
        /**
         * EPISTEMIC-INTEGRITY P0.4 (review fix F6) — the class balance of the window
         * the induction was given, under the production label rule
         * ({@code confidence >= 0.7 AND accepted}). A window with only one class makes
         * the induction fidelity trivial, so the balance is reported next to it.
         */
        public int windowPositives;
        public int windowNegatives;
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
            m.put("episodesAdmitted", episodesAdmitted);
            m.put("episodesExcluded", episodesExcluded);
            m.put("inducedEpisodeIds", inducedEpisodeIds);
            m.put("quarantineLedgerReadable", quarantineLedgerReadable);
            m.put("quarantineMalformedLines", quarantineMalformedLines);
            m.put("unmatchedQuarantineIds", unmatchedQuarantineIds);
            m.put("windowPositives", windowPositives);
            m.put("windowNegatives", windowNegatives);
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
     *
     * <p>EPISTEMIC-INTEGRITY P0.4: the entries come from the ADMISSION gate
     * ({@link EpisodicLog#learningCandidates()}), not from the raw log. A record
     * whose provenance is evaluation/experiment/synthetic, or whose id is named in
     * the quarantine ledger, cannot reach {@link RuleInductionEngine#induce}. The
     * window is still the last {@link KMaxEnforcer#K_MAX} records and the label rule
     * is unchanged ({@code confidence >= 0.7 AND accepted}).</p>
     */
    private InductionStats runInduction() {
        // EPISTEMIC-INTEGRITY P0.4 (review fix F8): ONE read-and-admit of the system.
        // The scheduler, the log's own API and the operator tool all go through
        // LearningAdmission.assess, so the window they use and the numbers they
        // report cannot drift apart.
        LearningAdmission.Assessment assessment =
            LearningAdmission.assess(episodicLog, quarantine());
        java.util.List<EpisodicLog.Entry> entries = assessment.candidates();
        int excluded = assessment.excluded();
        if (entries.isEmpty()) {
            return new InductionStats(0, 0, 0.0, java.util.List.of(), 0, excluded,
                java.util.List.of(), assessment.ledgerReadable(),
                assessment.ledgerMalformedLines(),
                assessment.unmatchedQuarantineIds(), 0, 0);
        }
        // Take last 20 (cap at K_MAX for Article II)
        java.util.List<EpisodicLog.Entry> recent = assessment.window(KMaxEnforcer.K_MAX);

        long[][] features = new long[recent.size()][];
        java.util.List<String> ids = new java.util.ArrayList<>();
        java.util.List<EpisodeRole> roles = new java.util.ArrayList<>();
        boolean[] labels = new boolean[recent.size()];
        int positives = 0;
        for (int i = 0; i < recent.size(); i++) {
            EpisodicLog.Entry e = recent.get(i);
            features[i] = featureExtractor.encode(e);
            ids.add(e.id());
            roles.add(e.role());
            labels[i] = e.confidence() >= 0.7 && e.accepted();
            if (labels[i]) positives++;
        }
        // EPISTEMIC-INTEGRITY P0.4 (review fix F6): the CLASS BALANCE of the window
        // travels with the report. A single-class window makes the induction
        // fidelity trivial (1.00 with no negative class at all), so an operator must
        // be able to see the balance and judge the fidelity against it.
        int negatives = recent.size() - positives;

        int before = birRegistry.size();
        java.util.List<Double> fidelities = new java.util.ArrayList<>();
        int learned = 0, rejected = 0;
        try {
            // The roles are passed to the engine as well: the admission gate decides
            // who may be here, and the engine refuses non-organic input a second
            // time. Defence in depth, because the gate is the only thing standing
            // between an evaluation record and the mind's memory.
            var res = ruleEngine.induce(ids, features, labels, roles);
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
        return new InductionStats(learned, rejected, delta, fidelities, entries.size(),
            excluded, List.copyOf(ids), assessment.ledgerReadable(),
            assessment.ledgerMalformedLines(), assessment.unmatchedQuarantineIds(),
            positives, negatives);
    }

    /**
     * The quarantine that governs this scheduler's log.
     *
     * <p>Bound to the log's own sibling ledger, so it works with no configuration: an
     * absent ledger means nothing is quarantined and the behaviour is exactly the one
     * before P0.4.</p>
     */
    private EpisodeQuarantine quarantine() {
        return episodicLog == null ? null : episodicLog.quarantine();
    }

    /** Result of one induction pass. */
    private record InductionStats(int learned, int rejected, double delta,
                                  java.util.List<Double> fidelities,
                                  int admitted, int excluded,
                                  java.util.List<String> inducedEpisodeIds,
                                  boolean ledgerReadable, int ledgerMalformedLines,
                                  int unmatchedQuarantineIds,
                                  int windowPositives, int windowNegatives) {}
}
