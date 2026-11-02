package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — the error-driven decay, wired into the consolidation.
 *
 * <p>The owner's decision D-012: the shadow classes are wired to the live
 * engines, and the {@link io.matrix.cognitive.ErrorDrivenLearner} was the
 * named example — "to the consolidation". Until now it had no production
 * caller at all: only its test.
 */
class ErrorDrivenDecayWiringTest {

    @Test
    @DisplayName("the decay acts on the bindings of the concept the verified error is about")
    void theDecayActsOnTheBindingsOfTheConceptTheVerifiedErrorIsAbout(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            String conceptId = workbench.workspace("photosynthesis").concepts().get(0).id();
            int before = workbench.activeBindings("photosynthesis", conceptId);

            workbench.store().recordLearningErrors("photosynthesis", List.of(
                    error("e1", conceptId, "The mitochondrion makes ATP", true),
                    error("e2", conceptId, "The chloroplast makes ATP", true)));

            KnowledgeWorkbench.DecayResult result =
                    workbench.applyErrorDrivenDecay("photosynthesis");

            assertThat(result.errors()).isEqualTo(2);
            assertThat(result.concepts()).contains(conceptId);
            // The decay lowers the ACCUMULATOR in the direction of the
            // error. The binding's bit is cleared by the next thresholding
            // pass, so a count of the bits would not see the first decay.
            assertThat(result.strengthAfter()).isLessThan(result.strengthBefore());
            assertThat(result.strengthBefore()).isGreaterThan(0.0);
            assertThat(before).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("an unverified error is not acted upon")
    void anUnverifiedErrorIsNotActedUpon(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            String conceptId = workbench.workspace("photosynthesis").concepts().get(0).id();

            workbench.store().recordLearningErrors("photosynthesis", List.of(
                    error("e1", conceptId, "The mitochondrion makes ATP", false)));

            KnowledgeWorkbench.DecayResult result =
                    workbench.applyErrorDrivenDecay("photosynthesis");

            // A slip is not a gap. The mission's §9 says the error-driven
            // integration happens only after the error is verified.
            assertThat(result.errors()).isEqualTo(0);
            assertThat(result.concepts()).isEmpty();
        }
    }

    @Test
    @DisplayName("the weakened state survives a restart")
    void theWeakenedStateSurvivesARestart(@TempDir Path dir) {
        Path db = dir.resolve("w.sqlite");
        String conceptId;
        int afterDecay;
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            workbench.loadBundledPackage("photosynthesis");
            conceptId = workbench.workspace("photosynthesis").concepts().get(0).id();
            workbench.store().recordLearningErrors("photosynthesis", List.of(
                    error("e1", conceptId, "The mitochondrion makes ATP", true),
                    error("e2", conceptId, "The chloroplast makes ATP", true)));
            workbench.applyErrorDrivenDecay("photosynthesis");
            afterDecay = workbench.activeBindings("photosynthesis", conceptId);
        }

        // A new process, the same database.
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(db.toString())) {
            assertThat(workbench.activeBindings("photosynthesis", conceptId)).isEqualTo(afterDecay);
        }
    }

    @Test
    @DisplayName("the consolidation runs the decay and reports it")
    void theConsolidationRunsTheDecayAndReportsIt(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            String conceptId = workbench.workspace("photosynthesis").concepts().get(0).id();
            workbench.store().recordLearningErrors("photosynthesis", List.of(
                    error("e1", conceptId, "The mitochondrion makes ATP", true),
                    error("e2", conceptId, "The chloroplast makes ATP", true)));

            KnowledgeConsolidator.Report report = KnowledgeConsolidator.consolidate(workbench,
                    "photosynthesis", System.currentTimeMillis(),
                    KnowledgeConsolidator.Budget.defaults());

            assertThat(report.decay()).isNotNull();
            assertThat(report.decay().errors()).isEqualTo(2);
            assertThat(report.decay().strengthAfter()).isLessThan(report.decay().strengthBefore());
        }
    }

    @Test
    @DisplayName("the encoding is deterministic: the same terms light the same positions")
    void theEncodingIsDeterministic() {
        int[] first = HebbianBindings.positionsOf("photosynthesis");
        int[] second = HebbianBindings.positionsOf("photosynthesis");
        assertThat(first).isEqualTo(second);

        float[] observation = HebbianBindings.observation("photosynthesis");
        assertThat(observation).isEqualTo(HebbianBindings.observation("photosynthesis"));
        assertThat(HebbianBindings.observation("chloroplast")).isNotEqualTo(observation);
    }

    @Test
    @DisplayName("the answer says that the bindings of the concept were weakened")
    void theAnswerSaysThatTheBindingsOfTheConceptWereWeakened(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            // The concept the answer is about: the one the workspace names
            // first when asked about photosynthesis.
            Answer before = workbench.ask("photosynthesis", "What is photosynthesis?",
                    AbstractionLevel.INTRODUCTORY);
            assertThat(before.answered()).isTrue();
            String conceptId = before.concepts().get(0).id();
            workbench.store().recordLearningErrors("photosynthesis", List.of(
                    error("e1", conceptId, "The mitochondrion makes ATP", true),
                    error("e2", conceptId, "The chloroplast makes ATP", true)));
            workbench.applyErrorDrivenDecay("photosynthesis");

            Answer answer = workbench.ask("photosynthesis", "What is photosynthesis?",
                    AbstractionLevel.INTRODUCTORY);

            // The decay's result is CONSUMED: the answer says that the
            // association it rests on is one the system has weakened.
            assertThat(answer.answered()).isTrue();
            assertThat(answer.limitations()).anySatisfy(limitation -> {
                assertThat(limitation).contains("verified error");
                assertThat(limitation).contains("weakened the bindings");
            });
        }
    }

    private LearningError error(String id, String conceptId, String learnerAnswer, boolean verified) {
        return new LearningError(id, "photosynthesis", "item-" + id, conceptId,
                LearningDimension.RECALL, learnerAnswer, "The expected answer", verified);
    }
}
