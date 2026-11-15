package io.matrix.brain.runtime;

import io.matrix.bir.BirRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EPISTEMIC-INTEGRITY P0.4 — the parts of the isolation that are about STRUCTURE
 * rather than about a particular record.
 *
 * <p>Three properties are pinned here, each of them the fix of a defect the review
 * found:</p>
 * <ol>
 *   <li><b>ONE definition of the production window (F8).</b> The scheduler, the
 *       log's own API and the operator tool each used to compute the window
 *       themselves. They agreed on the day they were written, and nothing kept them
 *       in agreement. There is now one definition, and this test asserts that the
 *       three call sites agree on it — including the ids the induction was
 *       actually given.</li>
 *   <li><b>ONE read of the ledger per pass (F7).</b> The gate asked the ledger
 *       about every record separately, and every question re-read and re-parsed the
 *       whole file: the cost of an admission pass grew with the square of the log
 *       size.</li>
 *   <li><b>NO training without a declared provenance (F10).</b> The engine had an
 *       overload that applied no role filter at all, so an evaluation record handed
 *       to it would be trained on. That overload now refuses.</li>
 * </ol>
 */
class LearningWindowDefinitionTest {

    /** The instant the fixtures claim. */
    private static final String AT = "2026-11-15T12:00:00Z";

    /** The workspace the fixtures belong to. */
    private static final String WORKSPACE = "bridged";

    @Test
    @DisplayName("the scheduler, the log and the tool agree on ONE production window")
    void theThreeCallSitesAgreeOnOneWindow(@TempDir Path dir) {
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);
        for (int i = 0; i < 25; i++) {
            log.append("organic question " + i, "organic answer " + i,
                i % 2 == 0 ? 0.9 : 0.2, true, List.of("mat:hdc"));
        }
        // One non-organic record at the tail: it must not be in the window.
        log.append("evaluation probe", "Paris", 0.95, true, List.of("mat:hdc"),
            EpisodeRole.EVALUATION);

        List<EpisodicLog.Entry> viaLog = log.learningWindow();
        List<EpisodicLog.Entry> viaGate = LearningAdmission.learningWindow(log,
            log.quarantine(), KMaxEnforcer.K_MAX);
        assertThat(viaLog).isEqualTo(viaGate);

        BirRegistry registry = new BirRegistry();
        List<String> induced;
        try (RealSleepScheduler scheduler = new RealSleepScheduler(
                new io.matrix.memory.HierarchicalMemory(),
                new io.matrix.lifecycle.ConsolidationCycle(),
                new io.matrix.federation.Anonymizer(2), 5,
                log, registry, new RuleInductionEngine(registry, null),
                new EpisodeFeatureExtractor())) {
            RealSleepScheduler.DreamReport dream = scheduler.triggerNow();
            induced = dream.inducedEpisodeIds;
        }

        assertThat(induced)
            .as("the ids the induction was given are exactly the window's ids")
            .containsExactlyElementsOf(viaLog.stream().map(EpisodicLog.Entry::id).toList());
        assertThat(induced)
            .as("the non-organic record must not be in the window")
            .hasSize(KMaxEnforcer.K_MAX);
        assertThat(viaLog)
            .as("every record in the window is learning-eligible")
            .allSatisfy(e -> assertThat(e.role().learningEligible()).isTrue());
    }

    @Test
    @DisplayName("one admission pass reads the ledger ONCE")
    void oneAdmissionPassReadsTheLedgerOnce(@TempDir Path dir) {
        Path logPath = dir.resolve("episodic.ndjson");
        EpisodicLog log = new EpisodicLog(logPath);
        for (int i = 0; i < 40; i++) {
            log.append("organic question " + i, "organic answer " + i, 0.9, true,
                List.of("mat:hdc"));
        }
        EpisodeQuarantine quarantine = log.quarantine();
        String named = log.readAll().get(0).id();
        quarantine.quarantine(named, "named record", 1000L);

        long before = quarantine.ledgerReads();
        List<EpisodicLog.Entry> admitted = LearningAdmission.admitted(log.readAll(), quarantine);
        long reads = quarantine.ledgerReads() - before;

        assertThat(reads)
            .as("the ledger must be read once for the whole pass, not once per record")
            .isEqualTo(1L);
        assertThat(admitted)
            .as("the named record is still excluded")
            .hasSize(39)
            .noneSatisfy(e -> assertThat(e.id()).isEqualTo(named));
    }

    @Test
    @DisplayName("the provenance-free induction overload refuses")
    void theProvenanceFreeInductionOverloadRefuses() {
        BirRegistry registry = new BirRegistry();
        RuleInductionEngine engine = new RuleInductionEngine(registry, null);
        long[][] features = { { 1L, 2L }, { 3L, 4L } };
        boolean[] labels = { true, false };

        assertThatThrownBy(() -> engine.induce(List.of("ep-1", "ep-2"), features, labels))
            .as("a training call that cannot say where its episodes came from is refused")
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("provenance");
    }

    /**
     * EPISTEMIC-INTEGRITY P0.4 (review fix F9) — the frozen corpus is a pure function
     * of its source.
     *
     * <p>The corpus builder carried an unused random handle. A random handle that is
     * unused is harmless today and is an invitation tomorrow: the next change that
     * consumes it would make the PROBE FIREWALL's corpus non-reproducible, and the
     * firewall is what decides whether a record may be learned from.</p>
     */
    @Test
    @DisplayName("the frozen corpus is the same every time it is built")
    void theFrozenCorpusIsTheSameEveryTimeItIsBuilt() {
        assertThat(EvalBattery.standardBattery())
            .as("the frozen battery must be a pure function of its source")
            .isEqualTo(EvalBattery.standardBattery());
        assertThat(PromotionGate.frozenCorpusSize())
            .as("the corpus the firewall guards must not shrink")
            .isGreaterThanOrEqualTo(47);
        assertThat(PromotionGate.transliteratedCorpusSize())
            .as("the transliterated corpus must not shrink either")
            .isGreaterThanOrEqualTo(5);
    }
}
