package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W40 — the assessment and mastery contract.
 *
 * <p>Two properties are under test and they are in tension on purpose: the
 * state must be reachable from evidence, and it must be reachable ONLY from
 * evidence. A state that could be asserted without evidence would make every
 * measurement of learning meaningless.
 */
class LearningStateEvaluatorTest {

    private KnowledgeSnapshot snapshot;
    private AssessmentDataset dataset;
    private LearningStateEvaluator evaluator;

    @BeforeEach
    void setUp() {
        snapshot = new KnowledgeImporter().importResource("knowledge/photosynthesis.pack.json");
        dataset = AssessmentDataset.loadResource("knowledge/photosynthesis.assessment.json", snapshot);
        evaluator = new LearningStateEvaluator(dataset.items());
    }

    private AssessmentItem item(String id) {
        return dataset.items().stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow();
    }

    private static AssessmentOutcome passed(String itemId, double confidence) {
        return new AssessmentOutcome(itemId, true, 1.0, confidence, "a good answer", "s1", 1L);
    }

    private static AssessmentOutcome failed(String itemId, double confidence) {
        return new AssessmentOutcome(itemId, false, 0.0, confidence, "a wrong answer", "s1", 2L);
    }

    @Test
    @DisplayName("the dataset is versioned and holds a held-out evaluation set")
    void theDatasetIsVersionedAndHeld() {
        assertThat(dataset.id()).isEqualTo("photosynthesis-v1");
        assertThat(dataset.version()).isEqualTo(1);
        assertThat(dataset.items()).hasSize(22);
        assertThat(dataset.heldOutItems()).hasSize(11);
        assertThat(dataset.trainingItems()).hasSize(11);
        // The two sets must not overlap: an item that is held out cannot
        // also be a training item.
        assertThat(dataset.heldOutItems()).allMatch(AssessmentItem::heldOut);
        assertThat(dataset.trainingItems()).noneMatch(AssessmentItem::heldOut);
    }

    @Test
    @DisplayName("every item names a concept that exists and a dimension")
    void everyItemIsWellFormed() {
        for (AssessmentItem item : dataset.items()) {
            assertThat(snapshot.concepts()).extracting(Concept::id).contains(item.conceptId());
            assertThat(item.keyTerms()).isNotEmpty();
            assertThat(item.prompt()).isNotBlank();
        }
    }

    @Test
    @DisplayName("grading is transparent: the key terms decide")
    void gradingIsTransparent() {
        AssessmentItem item = item("t-recall-1");
        String good = "Photosynthesis uses light, carbon dioxide and water to make sugars.";
        String bad = "Plants are green.";

        assertThat(AssessmentGrader.score(item, good)).isGreaterThanOrEqualTo(
                AssessmentGrader.DEFAULT_PASS_THRESHOLD);
        assertThat(AssessmentGrader.isCorrect(item, good)).isTrue();
        assertThat(AssessmentGrader.isCorrect(item, bad)).isFalse();
        assertThat(AssessmentGrader.missingTerms(item, bad)).contains("light");
    }

    @Test
    @DisplayName("a dimension with no evidence is UNKNOWN")
    void noEvidenceMeansUnknown() {
        LearningState state = evaluator.evaluate(
                item("t-recall-1").conceptId(), LearningDimension.RECALL, List.of(),
                MasteryCriteria.defaults());

        assertThat(state.stage()).isEqualTo(LearningStage.UNKNOWN);
        assertThat(state.evidenceCount()).isZero();
        assertThat(state.note()).isNotBlank();
    }

    @Test
    @DisplayName("one good answer is not competence")
    void oneGoodAnswerIsNotCompetence() {
        LearningState state = evaluator.evaluate(
                item("t-recall-1").conceptId(), LearningDimension.RECALL,
                List.of(passed("t-recall-1", 0.9)), MasteryCriteria.defaults());

        assertThat(state.stage()).isNotEqualTo(LearningStage.COMPETENCE);
        assertThat(state.stage()).isEqualTo(LearningStage.PRACTICE);
    }

    @Test
    @DisplayName("the acceptance criteria are what move the state")
    void theCriteriaMoveTheState() {
        // Two items, two variants, one held out, mean 1.0 -> competence.
        List<AssessmentOutcome> evidence = List.of(
                passed("t-recall-1", 0.9), passed("e-recall-1", 0.9));

        LearningState state = evaluator.evaluate(
                item("t-recall-1").conceptId(), LearningDimension.RECALL, evidence,
                MasteryCriteria.defaults());

        assertThat(state.stage()).isEqualTo(LearningStage.COMPETENCE);
        assertThat(state.evidenceCount()).isEqualTo(2);
        assertThat(state.variantCount()).isEqualTo(2);
        assertThat(state.heldOutCount()).isEqualTo(1);
        assertThat(state.note()).contains("acceptance criteria are met");
    }

    @Test
    @DisplayName("a critical failure blocks competence")
    void aCriticalFailureBlocksCompetence() {
        List<AssessmentOutcome> evidence = List.of(
                passed("t-eval-1", 0.9), failed("e-eval-1", 0.9));

        LearningState state = evaluator.evaluate(
                item("t-eval-1").conceptId(), LearningDimension.EVALUATION, evidence,
                MasteryCriteria.defaults());

        assertThat(state.criticalFailures()).isEqualTo(1);
        assertThat(state.stage()).isNotEqualTo(LearningStage.COMPETENCE);
        assertThat(state.note()).contains("critical failure");
    }

    @Test
    @DisplayName("a contradicting outcome lowers the state and says so")
    void aContradictingOutcomeLowersTheState() {
        String conceptId = item("t-recall-1").conceptId();
        List<AssessmentOutcome> evidence = new ArrayList<>(List.of(
                passed("t-recall-1", 0.9), passed("e-recall-1", 0.9)));

        LearningState competent = evaluator.evaluate(conceptId, LearningDimension.RECALL,
                evidence, MasteryCriteria.defaults());
        assertThat(competent.stage()).isEqualTo(LearningStage.COMPETENCE);

        evidence.add(failed("t-recall-1", 0.9));
        LearningState regressed = evaluator.evaluate(conceptId, LearningDimension.RECALL,
                evidence, MasteryCriteria.defaults());

        assertThat(regressed.stage()).isNotEqualTo(LearningStage.COMPETENCE);
        assertThat(regressed.note()).contains("contradicts an earlier success");
        // The history is not erased: the earlier successes are still the
        // evidence, and the state says the mean fell.
        assertThat(regressed.evidenceCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("the dimensions are independent: one vector, not one score")
    void theDimensionsAreIndependent() {
        String conceptId = item("t-recall-1").conceptId();
        List<AssessmentOutcome> evidence = List.of(
                passed("t-recall-1", 0.9), passed("e-recall-1", 0.9));

        LearningState recall = evaluator.evaluate(conceptId, LearningDimension.RECALL,
                evidence, MasteryCriteria.defaults());
        LearningState transfer = evaluator.evaluate(conceptId, LearningDimension.TRANSFER,
                evidence, MasteryCriteria.defaults());

        assertThat(recall.stage()).isEqualTo(LearningStage.COMPETENCE);
        assertThat(transfer.stage()).isEqualTo(LearningStage.UNKNOWN);
    }

    @Test
    @DisplayName("calibration is measured over the learner's confidence")
    void calibrationIsMeasured() {
        String conceptId = item("t-recall-1").conceptId();
        List<AssessmentOutcome> overconfident = List.of(
                passed("t-recall-1", 1.0), failed("e-recall-1", 1.0));
        List<AssessmentOutcome> calibrated = List.of(
                passed("t-recall-1", 1.0), failed("e-recall-1", 0.0));

        LearningState bad = evaluator.evaluate(conceptId, LearningDimension.CALIBRATION,
                overconfident, MasteryCriteria.exploratory());
        LearningState good = evaluator.evaluate(conceptId, LearningDimension.CALIBRATION,
                calibrated, MasteryCriteria.exploratory());

        assertThat(bad.score()).isLessThan(good.score());
        assertThat(good.score()).isEqualTo(1.0);
        assertThat(bad.score()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("outcomes for unknown items are ignored rather than counted")
    void unknownItemsAreIgnored() {
        LearningState state = evaluator.evaluate(
                item("t-recall-1").conceptId(), LearningDimension.RECALL,
                List.of(new AssessmentOutcome("not-an-item", true, 1.0, 1.0, "x", "s1", 1L)),
                MasteryCriteria.defaults());

        assertThat(state.stage()).isEqualTo(LearningStage.UNKNOWN);
        assertThat(state.evidenceCount()).isZero();
    }

    @Test
    @DisplayName("duplicate item ids are refused")
    void duplicateItemsAreRefused() {
        List<AssessmentItem> duplicated = List.of(item("t-recall-1"), item("t-recall-1"));
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new LearningStateEvaluator(duplicated)))
                .hasMessageContaining("duplicate");
    }
}
