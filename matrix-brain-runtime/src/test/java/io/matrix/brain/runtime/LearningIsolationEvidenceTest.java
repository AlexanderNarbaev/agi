package io.matrix.brain.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * EPISTEMIC-INTEGRITY P0.4 — the measured effect of the isolation on THIS machine's
 * real episodic log.
 *
 * <p><b>What this measures.</b> The real log of this machine holds 226 records: 108
 * written by earlier builds and 118 written by two runs of the C2A collection
 * harness through the production write path. This test copies the log into a
 * temporary directory — the real log is NEVER modified — and measures, on the copy:</p>
 * <ol>
 *   <li>the window the learner used to see (the last 20 records of the raw log) and
 *       the induction fidelity on it;</li>
 *   <li>the window the learner sees after the quarantine (the last ADMITTED records)
 *       and the induction fidelity on it;</li>
 *   <li>the RESIDUAL: what is still admitted, and whether any of it is still
 *       designed traffic.</li>
 * </ol>
 *
 * <p><b>The rule is by CONTENT, and therefore by ID (review fix F5).</b> The first
 * version of this test quarantined "the records at log positions &gt;= 108". A
 * position is not an identity: the moment one more interaction is appended, the same
 * rule marks a different set of records — a FRESH ORGANIC interaction included — and
 * frees a record the operator meant to exclude. It also missed the earlier
 * contamination, because the designed traffic was not confined to the positions the
 * two collection runs occupied: 36 of the 53 records that survived the positional
 * rule were designed workload inputs sitting in the pre-collection region.</p>
 *
 * <p>The rule here is therefore:</p>
 * <ul>
 *   <li><b>RULE A — frozen probe.</b> The record's input or reply matches the frozen
 *       evaluation corpus, in the Cyrillic form OR in the transliterated form the
 *       serving path actually writes ({@link PromotionGate#isFrozenProbe});</li>
 *   <li><b>RULE B — designed input.</b> The record's input is one of the collection
 *       harness's own designed workload inputs.</li>
 * </ul>
 *
 * <p>Both rules name RECORDS, and the quarantine is applied by ID, so the decision
 * survives the log growing. The ids are written to a ledger, and the ledger is
 * append-only and reversible.</p>
 *
 * <p><b>What the fidelity numbers mean (review fix F6).</b> A window with only ONE
 * class makes the induction fidelity trivial — 1.00 with no negative class at all is
 * not evidence of better learning, it is evidence of a degenerate window. The class
 * balance is therefore measured and asserted next to the fidelity: a fidelity is
 * only meaningful when both classes are present, and this test asserts that they
 * are.</p>
 */
class LearningIsolationEvidenceTest {

    /** The log of the running gateway, if this machine has it. */
    private static Path realLog() {
        Path start = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (Path directory = start; directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve("data/mind/episodic.ndjson");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    private record Window(int size, int positives, int negatives, List<String> inputs) {}

    private static Window window(List<EpisodicLog.Entry> entries) {
        int positives = 0;
        List<String> inputs = new ArrayList<>();
        for (EpisodicLog.Entry e : entries) {
            if (e.confidence() >= 0.7 && e.accepted()) positives++;
            inputs.add(e.input());
        }
        return new Window(entries.size(), positives, entries.size() - positives, inputs);
    }

    /** The production induction, run against a log (the copy). */
    private static double[] induction(EpisodicLog log) {
        io.matrix.bir.BirRegistry registry = new io.matrix.bir.BirRegistry();
        try (RealSleepScheduler scheduler = new RealSleepScheduler(
                new io.matrix.memory.HierarchicalMemory(),
                new io.matrix.lifecycle.ConsolidationCycle(),
                new io.matrix.federation.Anonymizer(2), 5,
                log, registry, new RuleInductionEngine(registry, null),
                new EpisodeFeatureExtractor())) {
            RealSleepScheduler.DreamReport dream = scheduler.triggerNow();
            double fidelity = dream.fidelityScores.isEmpty() ? Double.NaN
                : dream.fidelityScores.get(0);
            System.out.printf("[P0.4] induction: rulesLearned=%d rulesRejected=%d "
                    + "fidelity=%s admitted=%d excluded=%d inducedIds=%d "
                    + "window=%d+/%d-%n",
                dream.rulesLearned, dream.rulesRejected,
                dream.fidelityScores.isEmpty() ? "none" : String.valueOf(fidelity),
                dream.episodesAdmitted, dream.episodesExcluded,
                dream.inducedEpisodeIds.size(),
                dream.windowPositives, dream.windowNegatives);
            return new double[] { fidelity, dream.episodesAdmitted, dream.episodesExcluded,
                dream.inducedEpisodeIds.size() };
        }
    }

    @Test
    @DisplayName("the measured effect of the quarantine on the real log (on a copy)")
    void theMeasuredEffectOnTheRealLog() throws Exception {
        Path real = realLog();
        assumeTrue(real != null,
            "this machine has no runtime episodic log: the measurement cannot be made here "
                + "(the file is the runtime's own data and is not part of the repository)");

        // ---- work on a COPY: the real log is never modified ------------------
        Path work = Files.createTempDirectory("p0-4-isolation-");
        Path copy = work.resolve("episodic.ndjson");
        Files.copy(real, copy, StandardCopyOption.REPLACE_EXISTING);
        Path ledger = EpisodeQuarantine.defaultLedgerPath(copy);

        List<EpisodicLog.Entry> all = new EpisodicLog(copy).readAll();
        System.out.printf("[P0.4] real log: %s (%d records, sha256=%s)%n",
            real.toAbsolutePath(), all.size(), EpisodeQuarantineTool.sha256(real));

        // ---- BEFORE: the window the learner used to see ----------------------
        Window rawWindow = window(LearningAdmission.window(all, KMaxEnforcer.K_MAX));
        double[] before = induction(new EpisodicLog(copy));
        System.out.printf("[P0.4] BEFORE window(raw): size=%d pos=%d neg=%d fidelity=%s%n",
            rawWindow.size(), rawWindow.positives(), rawWindow.negatives(),
            Double.isNaN(before[0]) ? "none" : String.valueOf(before[0]));

        // ---- apply the quarantine: RULE A + RULE B, BY ID ---------------------
        Set<String> designedInputs = new LinkedHashSet<>();
        for (C2ARealEpisodeCollectionTest.Work w : C2ARealEpisodeCollectionTest.workload()) {
            designedInputs.add(w.input());
        }
        EpisodeQuarantine quarantine = new EpisodeQuarantine(ledger);
        int ruleA = 0;
        int ruleB = 0;
        long stamp = 1L;
        for (EpisodicLog.Entry e : all) {
            boolean probe = PromotionGate.isFrozenProbe(e.input())
                || PromotionGate.isFrozenProbe(e.reply());
            boolean designed = designedInputs.contains(e.input());
            if (!probe && !designed) continue;
            if (quarantine.isQuarantined(e.id())) continue;
            quarantine.quarantine(e.id(),
                (probe ? "RULE A frozen evaluation probe" : "RULE B designed workload input")
                    + " (record id)", stamp++);
            if (probe) ruleA++; else ruleB++;
        }
        System.out.printf("[P0.4] quarantined: ruleA(frozen probe)=%d ruleB(designed input)=%d "
                + "total=%d ledger=%s%n",
            ruleA, ruleB, quarantine.quarantinedIds().size(),
            ledger.toAbsolutePath());

        // ---- AFTER: the window the learner sees now --------------------------
        List<EpisodicLog.Entry> admitted = LearningAdmission.admitted(all, quarantine);
        Window admittedWindow = window(LearningAdmission.window(admitted, KMaxEnforcer.K_MAX));
        double[] after = induction(new EpisodicLog(copy));
        System.out.printf("[P0.4] AFTER window(admitted): size=%d pos=%d neg=%d fidelity=%s%n",
            admittedWindow.size(), admittedWindow.positives(), admittedWindow.negatives(),
            Double.isNaN(after[0]) ? "none" : String.valueOf(after[0]));
        System.out.printf("[P0.4] AFTER admitted=%d excluded=%d inducedIds=%d%n",
            (int) after[1], (int) after[2], (int) after[3]);

        // ---- the invariants that must hold ----------------------------------
        assertThat(Files.readString(copy))
            .as("the quarantine must not change a single byte of the log")
            .isEqualTo(Files.readString(real));
        assertThat(new EpisodicLog(copy).readAll()).hasSize(all.size());
        assertThat(admitted.size()).isLessThan(all.size());
        assertThat(admitted).allSatisfy(e ->
            assertThat(e.role().learningEligible()).isTrue());
        assertThat(admitted).noneSatisfy(e ->
            assertThat(quarantine.isQuarantined(e.id())).isTrue());

        // The RESIDUAL, asserted rather than printed: no designed traffic and no
        // frozen probe may survive the rule.
        List<EpisodicLog.Entry> residualDesigned = admitted.stream()
            .filter(e -> designedInputs.contains(e.input()))
            .toList();
        List<EpisodicLog.Entry> residualProbes = admitted.stream()
            .filter(e -> PromotionGate.isFrozenProbe(e.input())
                || PromotionGate.isFrozenProbe(e.reply()))
            .toList();
        System.out.printf("[P0.4] residual: admitted=%d, of which designed-workload inputs=%d, "
                + "frozen probes=%d%n",
            admitted.size(), residualDesigned.size(), residualProbes.size());
        System.out.println("[P0.4] residual inputs: " + admitted.stream()
            .map(EpisodicLog.Entry::input).distinct().limit(20).toList());

        assertThat(residualDesigned)
            .as("no designed workload input may survive the quarantine")
            .isEmpty();
        assertThat(residualProbes)
            .as("no frozen probe may survive the quarantine")
            .isEmpty();

        // The class balance of the window (review fix F6): a single-class window
        // makes the fidelity trivial, so both classes must be present for the
        // fidelity to mean anything.
        assertThat(admittedWindow.positives())
            .as("the admitted window must hold positive trials")
            .isGreaterThan(0);
        assertThat(admittedWindow.negatives())
            .as("the admitted window must hold negative trials: a window with one "
                + "class only makes the induction fidelity trivial")
            .isGreaterThan(0);
        assertThat(rawWindow.positives())
            .as("the raw window (BEFORE) holds both classes — the measurement is on a "
                + "mixed window")
            .isGreaterThan(0);
        assertThat(rawWindow.negatives()).isGreaterThan(0);

        // The leak check on the PRODUCTION path.
        io.matrix.bir.BirRegistry registry = new io.matrix.bir.BirRegistry();
        try (RealSleepScheduler scheduler = new RealSleepScheduler(
                new io.matrix.memory.HierarchicalMemory(),
                new io.matrix.lifecycle.ConsolidationCycle(),
                new io.matrix.federation.Anonymizer(2), 5,
                new EpisodicLog(copy), registry, new RuleInductionEngine(registry, null),
                new EpisodeFeatureExtractor())) {
            RealSleepScheduler.DreamReport dream = scheduler.triggerNow();
            for (String id : dream.inducedEpisodeIds) {
                assertThat(quarantine.isQuarantined(id))
                    .as("induction input contains a quarantined record: " + id).isFalse();
            }
            assertThat(dream.windowPositives)
                .as("the production window's class balance is reported")
                .isGreaterThan(0);
            assertThat(dream.windowNegatives)
                .as("the production window's class balance is reported")
                .isGreaterThan(0);
        }

        // ---- the extreme variant: quarantine EVERYTHING ---------------------
        EpisodeQuarantine everything = new EpisodeQuarantine(
            EpisodeQuarantine.defaultLedgerPath(work.resolve("exhaustive.ndjson")));
        long stamp2 = 1L;
        for (EpisodicLog.Entry e : all) {
            everything.quarantine(e.id(), "exhaustive variant", stamp2++);
        }
        List<EpisodicLog.Entry> noneAdmitted =
            LearningAdmission.admitted(all, everything);
        System.out.printf("[P0.4] exhaustive variant: admitted=%d window=%d%n",
            noneAdmitted.size(),
            LearningAdmission.window(noneAdmitted, KMaxEnforcer.K_MAX).size());
        assertThat(noneAdmitted).isEmpty();
    }
}
