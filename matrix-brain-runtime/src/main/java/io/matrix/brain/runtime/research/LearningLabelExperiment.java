package io.matrix.brain.runtime.research;

import io.matrix.brain.runtime.EpisodicLog;
import io.matrix.brain.runtime.EpisodeRole;
import io.matrix.brain.runtime.KnowledgeForgeIngest;
import io.matrix.brain.runtime.LearningAdmission;
import io.matrix.brain.runtime.MindResult;
import io.matrix.brain.runtime.MultilingualMind;
import io.matrix.brain.runtime.PersistentHdcStore;
import io.matrix.brain.runtime.PromotionGate;
import io.matrix.brain.runtime.TrueMindCycle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * EPISTEMIC-INTEGRITY P3 — the experiment: does any representation predict an
 * INDEPENDENTLY VERIFIED outcome of an interaction better than chance?
 *
 * <p><b>What it measures.</b> Two targets over the same observations and the same
 * leakage-controlled partitions:</p>
 * <ol>
 *   <li><b>The validated outcome</b> — GROUNDED or NOT_GROUNDED, decided by
 *       {@link StoreGroundingVerifier} against the machine's own knowledge store, with
 *       UNKNOWN and REFUSED kept out of the denominator.</li>
 *   <li><b>The production label</b> — {@code confidence >= 0.7 AND accepted} — measured
 *       over the SAME representations, because the point of the exercise is to compare
 *       the two. The production label is a function of the retrieval overlap, and the
 *       feature contains the reply the retrieval produced: how well it is recovered from
 *       the feature is the MEASUREMENT OF THE SELF-FULFILLING LABEL, and it is published
 *       whether it flatters or not.</li>
 * </ol>
 *
 * <p><b>What it does not touch.</b> The production learning path, the real store, the
 * real log and the quarantine ledger. The store and the log are read from COPIES in a
 * temporary directory; the real {@code data/mind/} is never opened for writing and never
 * written to.</p>
 */
public final class LearningLabelExperiment {

    /** The production label rule, quoted so the two targets cannot drift apart. */
    public static final double PRODUCTION_LABEL_CONFIDENCE = 0.7;

    /** One designed input of the fresh collection. */
    public record Work(String family, String input, String expectation) {
    }

    /** The report of one experiment. */
    public record Report(
            List<OutcomeSignal> signals,
            List<RepresentationComparison.Result> validatedRows,
            List<RepresentationComparison.Result> productionLabelRows,
            List<RepresentationComparison.Result> contaminationRows,
            List<RepresentationComparison.Result> crossTaskRows,
            Map<String, Object> facts) {
    }

    private LearningLabelExperiment() {
    }

    /**
     * Runs the experiment over the observations of a log.
     *
     * @param storePath the knowledge store (a copy; it is opened read-only)
     * @param logPath   the episodic log (a copy)
     * @param dim       the store's dimension
     * @param seed      the seed of the trainer and of the random projection
     * @return the report
     */
    public static Report run(Path storePath, Path logPath, int dim, long seed) {
        Map<String, String> facts = new PersistentHdcStore(storePath, dim).snapshot();
        StoreGroundingVerifier verifier = new StoreGroundingVerifier(facts);
        List<OutcomeSignal> signals = readObservations(logPath, verifier);
        return report(signals, seed, verifier.factCount());
    }

    /**
     * Collects FRESH observations through the production path, into a log of its own.
     *
     * <p>The runtime is the class the gateway serves ({@link TrueMindCycle}); the store is
     * a copy of the machine's own; the log is a new file in the temporary directory. The
     * acquisition cost of every observation is measured here, because the cost of
     * OBTAINING an outcome signal is part of the signal's value.</p>
     *
     * @param storePath the store copy
     * @param logPath   the log to write (in the temporary directory)
     * @param dim       the store's dimension
     * @param workload  the designed inputs
     * @return the observations, in the order they were produced
     */
    public static List<OutcomeSignal> collect(Path storePath, Path logPath, int dim,
                                              List<Work> workload) {
        PersistentHdcStore store = new PersistentHdcStore(storePath, dim);
        TrueMindCycle cycle = new TrueMindCycle(new Random(42L), store);
        MultilingualMind multilingual = new MultilingualMind();
        EpisodicLog log = new EpisodicLog(logPath);
        List<OutcomeSignal> signals = new ArrayList<>();
        int index = 0;
        for (Work work : workload) {
            String original = work.input();
            MultilingualMind.Language language = multilingual.detectLanguage(original);
            String retrievalForm = language == MultilingualMind.Language.RUSSIAN
                    ? multilingual.transliterateCyrillicToLatin(original) : original;
            long started = System.nanoTime();
            MindResult result = cycle.think(retrievalForm, original);
            long acquisitionNanos = System.nanoTime() - started;
            PromotionGate.Decision decision = log.append(retrievalForm, result.reply(),
                    result.confidence(), result.accepted(), result.modulatorsFired(),
                    EpisodeRole.EXPERIMENT);
            boolean refused = !decision.promoted();
            signals.add(new OutcomeSignal(
                    "fresh-" + (index++), work.family(), original, result.reply(),
                    result.confidence(), result.accepted(),
                    OutcomeSignal.Provenance.PREDICTION,
                    OutcomeSignal.Provenance.VALIDATION,
                    OutcomeSignal.Provenance.LEARNING,
                    OutcomeSignal.Provenance.EVALUATION,
                    OutcomeSignal.Outcome.UNKNOWN,
                    "pending", "", refused ? "EXCLUDED" : "ADMITTED",
                    acquisitionNanos, 0L, work.expectation(), false));
        }
        return signals;
    }

    /**
     * Verifies the freshly collected observations.
     *
     * @param raw      the collected observations, with the verdict still pending
     * @param verifier the independent checker
     * @return the verified observations
     */
    public static List<OutcomeSignal> verify(List<OutcomeSignal> raw,
                                             StoreGroundingVerifier verifier) {
        List<OutcomeSignal> out = new ArrayList<>();
        for (OutcomeSignal signal : raw) {
            boolean refused = "REFUSED".equals(signal.learningDisposition())
                    || signal.reply() == null || signal.reply().isBlank();
            StoreGroundingVerifier.Verdict verdict =
                    verifier.verify(signal.input(), signal.reply(), refused);
            out.add(new OutcomeSignal(
                    signal.observationId(), signal.family(), signal.input(), signal.reply(),
                    signal.confidence(), signal.accepted(),
                    signal.predictionProvenance(), signal.validationProvenance(),
                    signal.learningProvenance(), signal.evaluationProvenance(),
                    verdict.outcome(), "store-grounding", verdict.evidence(),
                    signal.learningDisposition(), signal.acquisitionNanos(), verdict.nanos(),
                    signal.evaluationExpectation(), false));
        }
        return out;
    }

    /**
     * Reads the observations of a log and verifies each of them.
     *
     * @param logPath  the log to read
     * @param verifier the independent checker
     * @return the signals
     */
    public static List<OutcomeSignal> readObservations(Path logPath,
                                                       StoreGroundingVerifier verifier) {
        EpisodicLog log = new EpisodicLog(logPath);
        EpisodeQuarantineState quarantine = EpisodeQuarantineState.of(log);
        List<OutcomeSignal> signals = new ArrayList<>();
        List<EpisodicLog.Entry> entries = log.readAll();
        for (int i = 0; i < entries.size(); i++) {
            EpisodicLog.Entry entry = entries.get(i);
            boolean refused = entry.role() == null || entry.role().nonOrganic();
            StoreGroundingVerifier.Verdict verdict =
                    verifier.verify(entry.input(), entry.reply(), refused);
            signals.add(new OutcomeSignal(
                    entry.id(), "machine-log", entry.input(), entry.reply(),
                    entry.confidence(), entry.accepted(),
                    OutcomeSignal.Provenance.PREDICTION,
                    OutcomeSignal.Provenance.VALIDATION,
                    OutcomeSignal.Provenance.LEARNING,
                    OutcomeSignal.Provenance.EVALUATION,
                    verdict.outcome(), "store-grounding", verdict.evidence(),
                    quarantine.disposition(entry.id()),
                    0L, verdict.nanos(), "", false));
        }
        return signals;
    }

    /**
     * Builds the report: the comparison rows, the controls and the facts.
     *
     * @param signals    the verified observations
     * @param seed       the seed of the trainer
     * @param factCount  how many store facts the checker had
     * @return the report
     */
    public static Report report(List<OutcomeSignal> signals, long seed, int factCount) {
        List<OutcomeSignal> labelled = new ArrayList<>();
        for (OutcomeSignal signal : signals) {
            if (signal.labelled()) labelled.add(signal);
        }
        LeakageControlledPartition byFamily =
                LeakageControlledPartition.byFamily(labelled, 0.3);
        LeakageControlledPartition byInput =
                LeakageControlledPartition.byInput(labelled, 0.3);
        LeakageControlledPartition stratified =
                LeakageControlledPartition.stratifiedByLabel(labelled, 0.3);
        LeakageControlledPartition leak =
                LeakageControlledPartition.withLeak(labelled);

        List<RepresentationComparison.Result> validated = new ArrayList<>();
        validated.addAll(RepresentationComparison.compare(labelled, byFamily, seed));
        validated.addAll(RepresentationComparison.compare(labelled, byInput, seed));
        validated.addAll(RepresentationComparison.compare(labelled, stratified, seed));
        List<RepresentationComparison.Result> crossTask = new ArrayList<>();
        for (LeakageControlledPartition fold
                : LeakageControlledPartition.leaveOneFamilyOut(labelled)) {
            crossTask.addAll(RepresentationComparison.compare(labelled, fold, seed));
        }
        List<RepresentationComparison.Result> production = new ArrayList<>();
        production.addAll(RepresentationComparison.compare(labelled, byFamily, seed, true));
        production.addAll(RepresentationComparison.compare(labelled, byInput, seed, true));
        List<RepresentationComparison.Result> contamination =
                RepresentationComparison.compare(labelled, leak, seed);

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("storeFacts", factCount);
        facts.put("observations", signals.size());
        facts.put("labelled", labelled.size());
        facts.put("grounded", count(labelled, true));
        facts.put("notGrounded", count(labelled, false));
        facts.put("refused", countOutcome(signals, OutcomeSignal.Outcome.REFUSED));
        facts.put("unknown", countOutcome(signals, OutcomeSignal.Outcome.UNKNOWN));
        facts.put("partitionByFamilyTrain", byFamily.trainSize());
        facts.put("partitionByFamilyTest", byFamily.testSize());
        facts.put("partitionByInputTrain", byInput.trainSize());
        facts.put("partitionByInputTest", byInput.testSize());
        facts.put("partitionStratifiedTrain", stratified.trainSize());
        facts.put("partitionStratifiedTest", stratified.testSize());
        facts.put("leakedInputsStratified", stratified.leakedInputs(labelled).size());
        facts.put("leakedInputsByFamily", byFamily.leakedInputs(labelled).size());
        facts.put("leakedInputsByInput", byInput.leakedInputs(labelled).size());
        facts.put("leakedInputsContamination", leak.leakedInputs(labelled).size());
        facts.put("acquisitionNanosTotal", acquisitionNanos(signals));
        facts.put("validationNanosTotal", validationNanos(signals));
        facts.put("productionLabelPositives", productionPositives(signals));
        return new Report(signals, validated, production, contamination, crossTask, facts);
    }

    private static int count(List<OutcomeSignal> signals, boolean positive) {
        int n = 0;
        for (OutcomeSignal signal : signals) if (signal.positive() == positive) n++;
        return n;
    }

    private static int countOutcome(List<OutcomeSignal> signals, OutcomeSignal.Outcome outcome) {
        int n = 0;
        for (OutcomeSignal signal : signals) if (signal.outcome() == outcome) n++;
        return n;
    }

    private static long acquisitionNanos(List<OutcomeSignal> signals) {
        long total = 0;
        for (OutcomeSignal signal : signals) total += signal.acquisitionNanos();
        return total;
    }

    private static long validationNanos(List<OutcomeSignal> signals) {
        long total = 0;
        for (OutcomeSignal signal : signals) total += signal.validationNanos();
        return total;
    }

    private static int productionPositives(List<OutcomeSignal> signals) {
        int n = 0;
        for (OutcomeSignal signal : signals) if (signal.productionPositive()) n++;
        return n;
    }

    /** The admission state of the machine's quarantine ledger, read from a log's sibling. */
    record EpisodeQuarantineState(java.util.Set<String> quarantined) {

        static EpisodeQuarantineState of(EpisodicLog log) {
            io.matrix.brain.runtime.EpisodeQuarantine quarantine =
                    log.quarantine();
            return new EpisodeQuarantineState(quarantine.quarantinedIds());
        }

        String disposition(String id) {
            return quarantined.contains(id) ? "QUARANTINED" : "ADMITTED";
        }
    }

    /** Whether the production learning path would admit the observation. */
    public static boolean learningEligible(OutcomeSignal signal) {
        return signal.labelled();
    }

    /** The number of records the admission gate would admit, for the report. */
    public static int admittedCount(EpisodicLog log) {
        return LearningAdmission.admitted(log.readAll(), log.quarantine()).size();
    }

    /** The dimension the production store uses. */
    public static int storeDimension() {
        return KnowledgeForgeIngest.DEFAULT_DIM;
    }
}
