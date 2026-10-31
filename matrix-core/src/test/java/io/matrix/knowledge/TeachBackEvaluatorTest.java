package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W40 — the teaching experiment.
 *
 * <p>The point of these tests is not that the numbers are good. It is that
 * the experiment is real: the learner is measured on items the teacher never
 * saw, the expectation is declared before the run, and a lesson that does not
 * move the held-out score is reported as a failure rather than explained
 * away.
 */
class TeachBackEvaluatorTest {

    private KnowledgeSnapshot snapshot;
    private AssessmentDataset dataset;
    private TeachingComposer composer;

    @BeforeEach
    void setUp() {
        snapshot = new KnowledgeImporter().importResource("knowledge/photosynthesis.pack.json");
        dataset = AssessmentDataset.loadResource("knowledge/photosynthesis.assessment.json", snapshot);
        composer = new TeachingComposer(snapshot, dataset);
    }

    private String photosynthesisId() {
        return snapshot.concepts().stream()
                .filter(c -> c.name().equals("Photosynthesis"))
                .findFirst().orElseThrow().id();
    }

    @Test
    @DisplayName("the plan is ordered from the least to the most advanced account")
    void thePlanIsOrdered() {
        TeachingPlan plan = composer.compose(photosynthesisId(), AbstractionLevel.MECHANISTIC);

        assertThat(plan.steps()).hasSize(4);
        assertThat(plan.steps()).extracting(TeachingStep::level).containsExactly(
                AbstractionLevel.INTRODUCTORY, AbstractionLevel.INTERMEDIATE,
                AbstractionLevel.ADVANCED, AbstractionLevel.MECHANISTIC);
        assertThat(plan.steps().get(0).order()).isLessThan(plan.steps().get(3).order());
        // Every step carries an exercise and a check, not only an explanation.
        assertThat(plan.steps()).allMatch(s -> s.exercise() != null && !s.exercise().isBlank());
        assertThat(plan.steps()).allMatch(s -> s.check() != null && !s.check().isBlank());
        // And the plan names the dataset it was built against.
        assertThat(plan.datasetId()).isEqualTo("photosynthesis-v1");
        assertThat(plan.datasetVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("a plan at a lower level is a shorter plan")
    void aLowerLevelIsAShorterPlan() {
        TeachingPlan introductory = composer.compose(photosynthesisId(), AbstractionLevel.INTRODUCTORY);
        TeachingPlan mechanistic = composer.compose(photosynthesisId(), AbstractionLevel.MECHANISTIC);

        assertThat(introductory.size()).isEqualTo(1);
        assertThat(mechanistic.size()).isEqualTo(4);
    }

    @Test
    @DisplayName("the teacher never sees the held-out items")
    void theTeacherDoesNotSeeTheEvaluation() {
        TeachingPlan plan = composer.compose(photosynthesisId(), AbstractionLevel.MECHANISTIC);
        String wholePlan = String.join(" ", plan.steps().stream().map(TeachingStep::statement).toList());

        // The plan is built from the authored representations, not from the
        // evaluation set: no prompt of any assessment item appears in it.
        for (AssessmentItem item : dataset.items()) {
            assertThat(wholePlan).doesNotContain(item.prompt());
        }
        // And every step is exactly an authored representation of the concept.
        var authored = snapshot.representations().stream()
                .filter(r -> r.conceptId().equals(photosynthesisId()))
                .map(Representation::statement)
                .toList();
        assertThat(plan.steps()).extracting(TeachingStep::statement)
                .isSubsetOf(authored);
    }

    @Test
    @DisplayName("the teach-back measures a gain on the held-out items")
    void theTeachBackMeasuresAGain() {
        TeachingPlan plan = composer.compose(photosynthesisId(), AbstractionLevel.MECHANISTIC);
        TeachBackEvaluator evaluator = new TeachBackEvaluator();

        TeachBackEvaluator.TeachBackResult result = evaluator.run(plan, dataset, snapshot, 0.0);

        // The learner knew nothing before the lesson.
        assertThat(result.beforeScore()).isEqualTo(0.0);
        // And the lesson conferred something measurable.
        assertThat(result.afterScore()).isGreaterThan(0.0);
        assertThat(result.gain()).isGreaterThan(0.0);
        assertThat(result.metExpectation()).isTrue();

        // The measurement is on held-out items only.
        assertThat(result.itemsMeasured()).isEqualTo(
                (int) dataset.heldOutItems().stream()
                        .filter(i -> i.conceptId().equals(photosynthesisId())).count());
        assertThat(result.outcomes()).allMatch(o -> o.after() >= o.before());
        assertThat(result.datasetId()).isEqualTo("photosynthesis-v1");
    }

    @Test
    @DisplayName("a declared expectation that is not met is reported as not met")
    void anUnmetExpectationIsReported() {
        TeachingPlan plan = composer.compose(photosynthesisId(), AbstractionLevel.INTRODUCTORY);
        TeachBackEvaluator evaluator = new TeachBackEvaluator();

        // A gain of 0.9 is not going to happen from one introductory account.
        TeachBackEvaluator.TeachBackResult result = evaluator.run(plan, dataset, snapshot, 0.9);

        assertThat(result.metExpectation()).isFalse();
        assertThat(result.conclusion()).contains("did not confer");
        // The result says what was expected, so the failure is legible.
        assertThat(result.expectation()).contains("0.9");
    }

    @Test
    @DisplayName("a concept with no held-out item yields no claim at all")
    void noEvaluationItemMeansNoClaim() {
        String conceptId = snapshot.concepts().stream()
                .filter(c -> c.name().equals("The Calvin-Benson cycle"))
                .findFirst().orElseThrow().id();

        TeachingPlan plan = composer.compose(conceptId, AbstractionLevel.ADVANCED);
        TeachBackEvaluator evaluator = new TeachBackEvaluator();
        TeachBackEvaluator.TeachBackResult result = evaluator.run(plan, dataset, snapshot, 0.5);

        assertThat(result.itemsMeasured()).isZero();
        assertThat(result.metExpectation()).isFalse();
        assertThat(result.conclusion()).contains("Nothing is claimed");
    }

    @Test
    @DisplayName("the learner answers only from what it was taught")
    void theLearnerAnswersOnlyFromWhatItWasTaught() {
        TeachingPlan plan = composer.compose(photosynthesisId(), AbstractionLevel.INTRODUCTORY);
        SyntheticLearner learner = new SyntheticLearner();
        learner.teach(plan);

        assertThat(learner.taughtConcepts()).containsExactly(photosynthesisId());
        assertThat(learner.learnedAccounts()).hasSize(1);

        AssessmentItem item = dataset.heldOutItems().stream()
                .filter(i -> i.conceptId().equals(photosynthesisId()))
                .findFirst().orElseThrow();
        String answer = learner.answer(item);
        assertThat(answer).contains("Photosynthesis");
        assertThat(answer).doesNotContain("RuBisCO");
    }
}
