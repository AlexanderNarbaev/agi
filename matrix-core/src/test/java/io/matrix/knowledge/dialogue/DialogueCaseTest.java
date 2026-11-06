package io.matrix.knowledge.dialogue;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.DialogueAct;
import io.matrix.knowledge.KnowledgeWorkbench;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.3 — the dialogue cases are the instrument, and the instrument
 * is measured.
 */
class DialogueCaseTest {

    @Test
    @DisplayName("the case set has the size the mission asks for, and covers the categories")
    void theCaseSetHasTheSizeTheMissionAsksFor() {
        List<DialogueCase.Case> cases = DialogueCase.load();

        // The mission's §14: at least a hundred reproducible multilingual
        // dialogue cases.
        assertThat(cases).hasSizeGreaterThanOrEqualTo(100);
        // The identifiers are unique.
        assertThat(cases.stream().map(DialogueCase.Case::id).distinct().count())
                .isEqualTo(cases.size());
        // Both languages.
        assertThat(cases.stream().filter(c -> c.language().equals("ru")).count())
                .isGreaterThan(10);
        assertThat(cases.stream().filter(c -> c.language().equals("en")).count())
                .isGreaterThan(10);
        // The mission's §5 list of the cases a dialogue must survive.
        assertThat(cases.stream().map(DialogueCase.Case::category).distinct().toList())
                .contains("MISSPELLING", "MISSING_SPACE", "TRANSPOSITION", "PUNCTUATION",
                        "ASR", "MIXED_SCRIPT", "TRANSLITERATION", "ABBREVIATION",
                        "INCOMPLETE", "AMBIGUOUS", "INFORMAL");
        // And the eight dialogue classes.
        assertThat(cases.stream().map(DialogueCase.Case::expectedAct).distinct().toList())
                .contains(DialogueAct.Act.QUESTION, DialogueAct.Act.CORRECTION,
                        DialogueAct.Act.OBSERVATION, DialogueAct.Act.HYPOTHESIS,
                        DialogueAct.Act.TEACH_REQUEST, DialogueAct.Act.EXAMPLE,
                        DialogueAct.Act.FAILED_EXPLANATION, DialogueAct.Act.EXPERIMENT_RESULT);
        assertThat(cases.stream().map(DialogueCase.Case::expectedAct).distinct().count())
                .isEqualTo(8);
    }

    @Test
    @DisplayName("the run reports the agreements and names the failures")
    void theRunReportsTheAgreementsAndNamesTheFailures(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            DialogueCase.Report report = DialogueCase.run(workbench, DialogueCase.load());

            assertThat(report.count()).isGreaterThanOrEqualTo(100);
            assertThat(report.actAccuracy()).isGreaterThan(0.95);
            assertThat(report.answerabilityAccuracy()).isGreaterThan(0.9);
            // And the scope is reported, so that a number without it is
            // never the whole report.
            assertThat(report.inScope()).isGreaterThan(0);
            assertThat(report.inScope()).isLessThan(report.count());
            assertThat(report.refusalsExpected()).isGreaterThan(0);
            // The failures are named, so that a number without them is
            // never the whole report.
            assertThat(report.failures()).allSatisfy(failure ->
                    assertThat(failure.note()).isNotBlank());
        }
    }

    @Test
    @DisplayName("the cases run the same way twice")
    void theCasesRunTheSameWayTwice(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            List<DialogueCase.Case> cases = DialogueCase.load();
            DialogueCase.Report first = DialogueCase.run(workbench, cases);
            DialogueCase.Report second = DialogueCase.run(workbench, cases);

            assertThat(second.actAgreements()).isEqualTo(first.actAgreements());
            assertThat(second.answerabilityAgreements()).isEqualTo(first.answerabilityAgreements());
        }
    }

    @Test
    @DisplayName("a question that carries a command is refused")
    void aQuestionThatCarriesACommandIsRefused(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            List<DialogueCase.Case> unsafe = DialogueCase.load().stream()
                    .filter(c -> c.category().equals("UNSAFE")).toList();
            assertThat(unsafe).isNotEmpty();

            for (DialogueCase.Case testCase : unsafe) {
                assertThat(workbench.ask("photosynthesis", testCase.utterance(),
                        io.matrix.knowledge.AbstractionLevel.INTRODUCTORY).answered())
                        .as("the refusal of: " + testCase.utterance())
                        .isFalse();
            }
        }
    }
}
