package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W40 — the episodic log: what the workspace was asked, and what it
 * still cannot answer.
 *
 * <p>This is the capability the MIND-W3 branch had and `develop` lost. The
 * branch replayed its episodic log, promoted the recurring patterns to
 * canonical identifiers so that paraphrases collapsed, and retired the
 * entries that were no longer true BY TOMBSTONE rather than by deletion.
 */
class QuestionConsolidatorTest {

    /** A question whose terms appear in no claim of the pack. */
    private static final String GAP_QUESTION =
            "What is the extinction coefficient of the antenna pigment?";

    @TempDir
    Path dir;

    private KnowledgeWorkbench workbench;

    @BeforeEach
    void setUp() {
        workbench = KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString());
        workbench.loadBundledPackage("photosynthesis");
    }

    @Test
    @DisplayName("two wordings of the same question collapse onto one record")
    void paraphrasesCollapseOntoOneRecord() {
        assertThat(QuestionConsolidator.canonicalKey("What is photosynthesis?"))
                .isEqualTo(QuestionConsolidator.canonicalKey("photosynthesis is what"));

        workbench.ask("photosynthesis", "What is photosynthesis?", AbstractionLevel.INTRODUCTORY);
        workbench.ask("photosynthesis", "photosynthesis is what", AbstractionLevel.INTRODUCTORY);

        List<AskedQuestion> asked = workbench.askedQuestions("photosynthesis");
        assertThat(asked).hasSize(1);
        assertThat(asked.get(0).timesAsked()).isEqualTo(2);
        assertThat(asked.get(0).status()).isEqualTo(AskedQuestion.Status.OPEN);
    }

    @Test
    @DisplayName("a question the workspace cannot answer is recorded with what was uncovered")
    void anUnanswerableQuestionIsRecorded() {
        workbench.ask("photosynthesis", GAP_QUESTION,
                AbstractionLevel.INTRODUCTORY);

        List<AskedQuestion> asked = workbench.askedQuestions("photosynthesis");
        assertThat(asked).hasSize(1);
        AskedQuestion record = asked.get(0);
        assertThat(record.status()).isEqualTo(AskedQuestion.Status.OPEN);
        assertThat(record.uncoveredTerms()).contains("extinction", "coefficient");
        assertThat(record.timesAsked()).isEqualTo(1);
    }

    @Test
    @DisplayName("a question asked more than once and still unanswered is promoted into the gaps")
    void aRecurringGapIsPromoted() {
        workbench.ask("photosynthesis", GAP_QUESTION,
                AbstractionLevel.INTRODUCTORY);
        workbench.ask("photosynthesis", "the extinction coefficient of the antenna pigment",
                AbstractionLevel.INTRODUCTORY);

        QuestionConsolidator.ConsolidationReport report = workbench.consolidateQuestions("photosynthesis");

        assertThat(report.totalRecords()).isEqualTo(1);
        assertThat(report.openQuestions()).isEqualTo(1);
        assertThat(report.promotedGaps()).isEqualTo(1);
        assertThat(report.notes()).anyMatch(n -> n.contains("promoted"));
    }

    @Test
    @DisplayName("a question that becomes answerable is resolved and KEPT")
    void aResolvedQuestionIsKept() {
        workbench.ask("photosynthesis", GAP_QUESTION,
                AbstractionLevel.INTRODUCTORY);
        assertThat(workbench.askedQuestions("photosynthesis")).hasSize(1);

        // The workspace learns: a claim is corrected so that it covers the
        // question that was asked.
        KnowledgeSnapshot before = workbench.workspace("photosynthesis");
        Claim target = before.claims().stream()
                .filter(c -> c.proposition().contains("light-harvesting complex"))
                .findFirst().orElseThrow();
        workbench.refine("photosynthesis", new KnowledgeRefiner.Refinement(
                target.conceptId(),
                target.id(),
                "Light energy is absorbed and transferred to the photosystem II core by the "
                        + "light-harvesting complex, and the extinction coefficient of the antenna "
                        + "pigment was measured at 85000 per cm-1.",
                "In green plants, in light",
                "general biology",
                ClaimStatus.SUPPORTED,
                0.8,
                SourceKind.HUMAN_ASSERTION,
                "A measured outcome from the greenhouse",
                "urn:experiment:greenhouse-2026-10-31",
                "The measured extinction coefficient of the antenna pigment was 85000 per cm-1.",
                60,
                95,
                RevisionKind.REFINEMENT,
                "The correction adds the extinction coefficient of the antenna pigment.",
                List.of("the extinction coefficient of the antenna pigment"),
                List.of("photosynthesis releases oxygen"),
                List.of("the earlier claim did not mention the yield"),
                List.of("why a dimmer day lowers the measured oxygen release"),
                null, null));

        // The question is now answerable.
        assertThat(workbench.ask("photosynthesis", GAP_QUESTION,
                AbstractionLevel.INTRODUCTORY).answered()).isTrue();

        QuestionConsolidator.ConsolidationReport report = workbench.consolidateQuestions("photosynthesis");

        assertThat(report.openQuestions()).isZero();
        assertThat(report.resolvedQuestions()).isEqualTo(1);

        // The record of the gap is KEPT: a question that was once
        // unanswerable and now is not is evidence that the workspace
        // learned something.
        List<AskedQuestion> asked = workbench.askedQuestions("photosynthesis");
        assertThat(asked).hasSize(1);
        assertThat(asked.get(0).status()).isEqualTo(AskedQuestion.Status.RESOLVED);
        assertThat(asked.get(0).resolutionNote()).contains("can answer");
        assertThat(asked.get(0).uncoveredTerms()).contains("extinction");
    }

    @Test
    @DisplayName("the log survives a restart")
    void theLogSurvivesARestart() {
        workbench.ask("photosynthesis", GAP_QUESTION,
                AbstractionLevel.INTRODUCTORY);
        workbench.close();

        KnowledgeWorkbench reopened = KnowledgeWorkbench.at(dir.resolve("workbench.sqlite").toString());
        try {
            List<AskedQuestion> asked = reopened.askedQuestions("photosynthesis");
            assertThat(asked).hasSize(1);
            assertThat(asked.get(0).question()).contains("extinction");
            assertThat(asked.get(0).uncoveredTerms()).contains("extinction");
        } finally {
            reopened.close();
        }
    }
}
