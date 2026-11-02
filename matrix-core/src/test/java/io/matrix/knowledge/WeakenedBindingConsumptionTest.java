package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W42.7 — the weakened binding is CONSUMED by the retrieval.
 *
 * <p>The mission's own definition of an integration asks that a
 * component's result be consumed by another meaningful component. The
 * error-driven decay weakened a concept's binding, the answer said so,
 * and the answer then stood on exactly the same claims as before. This
 * test pins that the decay now reaches the retrieval.
 */
class WeakenedBindingConsumptionTest {

    @Test
    @DisplayName("a weakened binding carries less weight in the retrieval")
    void aWeakenedBindingCarriesLessWeightInTheRetrieval(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");
            Answer before = workbench.ask("photosynthesis", "What is photosynthesis?",
                    AbstractionLevel.INTRODUCTORY);
            assertThat(before.answered()).isTrue();
            String conceptId = before.concepts().get(0).id();

            // The learner has been wrong about this concept, in two
            // different sessions - so the errors are VERIFIED and the
            // decay acts on them.
            workbench.store().recordLearningErrors("photosynthesis", List.of(
                    error("e1", conceptId, "The mitochondrion makes ATP", true),
                    error("e2", conceptId, "The chloroplast makes ATP", true)));
            workbench.applyErrorDrivenDecay("photosynthesis");

            // The state IS weaker now: the comparison against the fresh
            // build says so.
            io.matrix.neuron.HebbianUpdater.State stored =
                    workbench.store().hebbianState("photosynthesis", conceptId);
            assertThat(stored).isNotNull();
            double storedSum = 0.0;
            for (float value : stored.accumulator) {
                storedSum += value;
            }
            double freshSum = 0.0;
            for (float value : HebbianBindings
                    .stateFor(workbench.workspace("photosynthesis"), conceptId).accumulator) {
                freshSum += value;
            }
            assertThat(storedSum).isLessThan(freshSum);

            // AND THE RETRIEVAL CONSUMES IT: the concept's binding is
            // weaker, so its claims carry less of the question's weight.
            float[] fresh = HebbianBindings
                    .stateFor(workbench.workspace("photosynthesis"), conceptId).accumulator;
            double integrity = HebbianBindings.integrityOf(stored.accumulator, fresh);
            assertThat(integrity).isLessThan(1.0);
            assertThat(integrity).isGreaterThanOrEqualTo(0.0);

            // The consumption is OBSERVABLE, and the isolation is the
            // point: the same snapshot, the same question, two retrievers
            // - one that consults the weakened binding and one that does
            // not - and they do not agree.
            KnowledgeSnapshot snapshot = workbench.workspace("photosynthesis");
            Answer withoutBindings = new KnowledgeRetriever(snapshot)
                    .answer("What is photosynthesis?", AbstractionLevel.INTRODUCTORY);
            Answer withBindings = new KnowledgeRetriever(snapshot,
                    KnowledgeRetriever.DEFAULT_MINIMUM_COVERAGE,
                    java.util.Map.of(conceptId, stored.accumulator))
                    .answer("What is photosynthesis?", AbstractionLevel.INTRODUCTORY);
            assertThat(withBindings.answered()).isTrue();
            assertThat(withoutBindings.answered()).isTrue();
            assertThat(withBindings.support().evidenceCoverage())
                    .isLessThan(withoutBindings.support().evidenceCoverage());
        }
    }

    @Test
    @DisplayName("a workspace with no weakened binding counts in full")
    void aWorkspaceWithNoWeakenedBindingCountsInFull(@TempDir Path dir) {
        try (KnowledgeWorkbench workbench =
                KnowledgeWorkbench.at(dir.resolve("w.sqlite").toString())) {
            workbench.loadBundledPackage("photosynthesis");

            // No consolidation has run, so no binding is weakened, and the
            // integrity of every term is the full one.
            assertThat(HebbianBindings.integrityOf(null, null, "ATP")).isEqualTo(1.0);
            assertThat(HebbianBindings.integrityOf(new float[1024], new float[1024], "ATP"))
                    .isEqualTo(1.0);
        }
    }

    private LearningError error(String id, String conceptId, String learnerAnswer, boolean verified) {
        return new LearningError(id, "photosynthesis", "item-" + id, conceptId,
                LearningDimension.RECALL, learnerAnswer, "the correct answer", verified);
    }
}
