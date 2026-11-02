package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.5 — the consolidation is bounded, marked, and leaves the truth
 * of the claims alone.
 *
 * <p>The mission's §8 states the two rules that decide the design: "A
 * knowledge claim must not become false merely because it was recently
 * unused", and forgetting must not silently destroy the historical
 * evidence.
 */
class KnowledgeConsolidatorTest {

    /**
     * The instant of the run, supplied by the caller.
     *
     * <p>The consolidation does not read a clock (the CONSTITUTION's first
     * article): the caller says when the run happens. The test's instant
     * is far enough ahead that the records it asks for are past the
     * window.
     */
    private static final long NOW = System.currentTimeMillis() + 60L * 24 * 60 * 60 * 1000;

    @Test
    @DisplayName("the run measures the knowledge before and after, and keeps the proposal only if it did not fall")
    void theRunMeasuresTheKnowledgeBeforeAndAfter(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.ask("photosynthesis", "What is photosynthesis?",
                    AbstractionLevel.INTRODUCTORY);
            workbench.ask("photosynthesis", "What is the Calvin-Benson cycle?",
                    AbstractionLevel.INTRODUCTORY);

            KnowledgeConsolidator.Report report = KnowledgeConsolidator.consolidate(workbench,
                    "photosynthesis", System.currentTimeMillis() + 60_000,
                    new KnowledgeConsolidator.Budget(10, 0));

            // The acceptance criterion: the sleep consolidator's rule,
            // wired into the knowledge domain.
            KnowledgeConsolidator.Acceptance acceptance = report.acceptance();
            assertThat(acceptance).isNotNull();
            assertThat(acceptance.probed()).isGreaterThan(0);
            assertThat(acceptance.answeredBefore()).isGreaterThanOrEqualTo(0);
            assertThat(acceptance.answeredAfter()).isEqualTo(acceptance.answeredBefore());
            assertThat(acceptance.accepted()).isTrue();
            assertThat(acceptance.rolledBack()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("the probe records nothing: a probe must not change what it measures")
    void theProbeRecordsNothing(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            int askedBefore = workbench.askedQuestions("photosynthesis").size();

            KnowledgeConsolidator.consolidate(workbench, "photosynthesis",
                    System.currentTimeMillis() + 60_000, KnowledgeConsolidator.Budget.defaults());

            assertThat(workbench.askedQuestions("photosynthesis")).hasSize(askedBefore);
        }
    }

    @Test
    @DisplayName("a record the rules name is marked, and it is still there")
    void aRecordTheRulesNameIsMarkedAndItIsStillThere(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.ask("photosynthesis", "What is quantum chromodynamics?",
                    AbstractionLevel.INTRODUCTORY);

            KnowledgeConsolidator.Report report = KnowledgeConsolidator.consolidate(workbench,
                    "photosynthesis", NOW, new KnowledgeConsolidator.Budget(10, 0));

            assertThat(report.examined()).isGreaterThan(0);
            assertThat(report.forgotten()).isGreaterThan(0);
            // The record is NOT gone: it is marked.
            List<AskedQuestion> forgotten = workbench.forgottenQuestions("photosynthesis");
            assertThat(forgotten).isNotEmpty();
            assertThat(forgotten).anySatisfy(question -> {
                assertThat(question.forgotten()).isTrue();
                assertThat(question.forgottenKind()).isNotBlank();
                assertThat(question.forgottenReason()).isNotBlank();
                assertThat(question.question()).isNotBlank();
            });
        }
    }

    @Test
    @DisplayName("the run is bounded by its budget")
    void theRunIsBoundedByItsBudget(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            for (int i = 0; i < 6; i++) {
                workbench.ask("photosynthesis", "What is question number " + i + "?",
                        AbstractionLevel.INTRODUCTORY);
            }

            KnowledgeConsolidator.Report report = KnowledgeConsolidator.consolidate(workbench,
                    "photosynthesis", NOW, new KnowledgeConsolidator.Budget(2, 0));

            assertThat(report.forgotten()).isLessThanOrEqualTo(2);
        }
    }

    @Test
    @DisplayName("the run is idempotent: a second run forgets nothing new")
    void theRunIsIdempotentASecondRunForgetsNothingNew(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.ask("photosynthesis", "What is quantum chromodynamics?",
                    AbstractionLevel.INTRODUCTORY);

            KnowledgeConsolidator.Budget budget = new KnowledgeConsolidator.Budget(10, 0);
            KnowledgeConsolidator.Report first = KnowledgeConsolidator.consolidate(workbench,
                    "photosynthesis", NOW, budget);
            KnowledgeConsolidator.Report second = KnowledgeConsolidator.consolidate(workbench,
                    "photosynthesis", NOW, budget);

            assertThat(first.forgotten()).isGreaterThan(0);
            assertThat(second.forgotten()).isEqualTo(0);
            assertThat(second.idempotent()).isTrue();
        }
    }

    @Test
    @DisplayName("the run is idempotent PER RECORD when the candidates outnumber the budget")
    void theRunIsIdempotentPerRecordWhenTheCandidatesOutnumberTheBudget(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            for (String question : List.of(
                    "What is quantum chromodynamics?",
                    "What is the Higgs boson?",
                    "What is a hadron?",
                    "What is QCD confinement?")) {
                workbench.ask("photosynthesis", question, AbstractionLevel.INTRODUCTORY);
            }

            // The budget allows ONE record per run, and there are more
            // candidates than that. The run is NOT idempotent over the
            // workspace - and the report says so instead of claiming a
            // guarantee it does not have. What IS idempotent is the
            // record: no record is forgotten twice.
            KnowledgeConsolidator.Budget budget = new KnowledgeConsolidator.Budget(1, 0);
            KnowledgeConsolidator.Report first = KnowledgeConsolidator.consolidate(workbench,
                    "photosynthesis", NOW, budget);
            KnowledgeConsolidator.Report second = KnowledgeConsolidator.consolidate(workbench,
                    "photosynthesis", NOW, budget);

            assertThat(first.forgotten()).isEqualTo(1);
            assertThat(second.forgotten()).isEqualTo(1);
            assertThat(first.idempotent()).isFalse();
            assertThat(second.idempotent()).isFalse();

            // The candidates drop by exactly what was forgotten, and the
            // two runs forgot DIFFERENT records.
            assertThat(second.candidates()).isEqualTo(first.candidates() - 1);
            assertThat(first.marked()).hasSize(1);
            assertThat(second.marked()).hasSize(1);
            assertThat(first.marked().get(0).questionId())
                    .isNotEqualTo(second.marked().get(0).questionId());

            // And the invariant text says what the flag means.
            assertThat(second.invariants()).anyMatch(invariant ->
                    invariant.contains("idempotent PER RECORD"));
        }
    }

    @Test
    @DisplayName("no claim changes: the truth of a claim is not a function of its use")
    void noClaimChangesTheTruthOfAClaimIsNotAFunctionOfItsUse(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            for (int i = 0; i < 5; i++) {
                workbench.ask("photosynthesis", "What is quantum chromodynamics " + i + "?",
                        AbstractionLevel.INTRODUCTORY);
            }
            List<Claim> before = workbench.workspace("photosynthesis").claims();
            List<String> statusesBefore = before.stream()
                    .map(claim -> claim.id() + ":" + claim.status()).toList();

            KnowledgeConsolidator.consolidate(workbench, "photosynthesis", NOW,
                    new KnowledgeConsolidator.Budget(10, 0));

            List<String> statusesAfter = workbench.workspace("photosynthesis").claims().stream()
                    .map(claim -> claim.id() + ":" + claim.status()).toList();
            assertThat(statusesAfter).isEqualTo(statusesBefore);
        }
    }

    @Test
    @DisplayName("the run reports the invariants it holds to")
    void theRunReportsTheInvariantsItHoldsTo(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            KnowledgeConsolidator.Report report = KnowledgeConsolidator.consolidate(workbench,
                    "photosynthesis", NOW, KnowledgeConsolidator.Budget.defaults());

            assertThat(report.invariants()).isNotEmpty();
            assertThat(report.invariants()).anySatisfy(invariant ->
                    assertThat(invariant).contains("claim"));
        }
    }

    @Test
    @DisplayName("the mark survives a restart")
    void theMarkSurvivesARestart(@TempDir Path dir) {
        Path db = dir.resolve("w.sqlite");
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.ask("photosynthesis", "What is quantum chromodynamics?",
                    AbstractionLevel.INTRODUCTORY);
            KnowledgeConsolidator.consolidate(workbench, "photosynthesis", NOW,
                    new KnowledgeConsolidator.Budget(10, 0));
        }

        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            assertThat(workbench.forgottenQuestions("photosynthesis")).isNotEmpty();
            // And the record is still readable as a record of what was asked.
            assertThat(workbench.askedQuestions("photosynthesis")).isNotEmpty();
        }
    }

    @Test
    @DisplayName("a rule names the record it would forget")
    void aRuleNamesTheRecordItWouldForget(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            workbench.ask("photosynthesis", "What is quantum chromodynamics?",
                    AbstractionLevel.INTRODUCTORY);

            KnowledgeConsolidator.Report report = KnowledgeConsolidator.consolidate(workbench,
                    "photosynthesis", NOW, new KnowledgeConsolidator.Budget(10, 0));

            assertThat(report.marked()).isNotEmpty();
            assertThat(report.marked()).allSatisfy(candidate -> {
                assertThat(candidate.kind()).isNotNull();
                assertThat(candidate.reason()).isNotBlank();
            });
        }
    }
}
