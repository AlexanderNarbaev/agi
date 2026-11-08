package io.matrix.learning.env;

import io.matrix.cognitive.MicroWorldCodec;
import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.cognitive.microworld.Observation;
import io.matrix.cognitive.microworld.ObservationDisposition;
import io.matrix.knowledge.Case;
import io.matrix.knowledge.KnowledgeStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W51 §7 — the episode runner over the micro-world.
 *
 * <p>Every assertion here is about the EPISODE, not about the fixture: the survivor count
 * is checked against the microworld's own published benchmark, and the utility is checked
 * against the formula that produced it. A test that merely asserted "some hypothesis
 * survived" would pass for a learner that learned nothing.</p>
 */
class MicroWorldEpisodeRunnerTest {

    /** The identifier of the Boolean function that is true only at power=1,switch=1. */
    private static final String CONJUNCTION_ID = "h-0001";

    /** The seed of the first episode. Its value is arbitrary and fixed. */
    private static final long SEED = 42L;

    /** A second seed, used to show that the seed reaches the probe order. */
    private static final long OTHER_SEED = 7L;

    /** The workspace the durable cases are written to. */
    private static final String WORKSPACE = "w51-episode";

    /** The instant the durable records are written with. */
    private static final String TIMESTAMP = MicroWorldEpisodeRunner.DEFAULT_EPISODE_TIMESTAMP;

    private static EpisodeReport greedyEpisode() {
        return new MicroWorldEpisodeRunner(new LampEnvironment(), "ep-w51-alpha", SEED).run();
    }

    @Test
    @DisplayName("the episode narrows the hypothesis space to the one surviving rule")
    void theEpisodeNarrowsTheSpaceToOneSurvivingRule() {
        EpisodeReport report = greedyEpisode();

        // The microworld's published benchmark says the survivors go 16 -> 8 -> 4 -> 2 -> 1
        // over the four assignments, and that the last one standing is the conjunction.
        assertThat(report.terminationReason())
                .isEqualTo(EpisodeReport.TerminationReason.MODEL_SETTLED);
        assertThat(report.finalSurvivors()).isEqualTo(1);
        assertThat(report.survivorId()).isEqualTo(CONJUNCTION_ID);
        assertThat(report.learned()).isTrue();
        assertThat(report.steps()).isEqualTo(MicroWorldEpisodeRunner.EXHAUSTED_INPUT_DOMAIN_SIZE);
        assertThat(report.utility()).isGreaterThan(0.0);
        assertThat(report.distinctAssignments())
                .isEqualTo(MicroWorldEpisodeRunner.EXHAUSTED_INPUT_DOMAIN_SIZE);
        assertThat(report.repeatedProbe()).isFalse();
    }

    @Test
    @DisplayName("two runs of the same seed produce the same report, field for field")
    void twoRunsOfTheSameSeedProduceTheSameReport() {
        EpisodeReport first = greedyEpisode();
        EpisodeReport second = greedyEpisode();

        assertThat(second).isEqualTo(first);
        assertThat(second.trajectory()).isEqualTo(first.trajectory());
        assertThat(second.utility()).isEqualTo(first.utility());
        assertThat(second.survivorId()).isEqualTo(first.survivorId());
        assertThat(second.timestamp()).isEqualTo(first.timestamp());
    }

    @Test
    @DisplayName("the utility is the fraction of the hypothesis space the learner eliminated")
    void theUtilityIsTheFractionOfTheSpaceTheLearnerEliminated() {
        EpisodeReport report = greedyEpisode();

        assertThat(report.utility())
                .isEqualTo(1.0 - (double) report.finalSurvivors()
                        / MicroWorldEpisodeRunner.UNBIASED_HYPOTHESIS_COUNT);
        // And the same formula, applied to the microworld's own space size, gives the value
        // the microworld's benchmark implies.
        assertThat(MicroWorldEpisodeRunner.UNBIASED_HYPOTHESIS_COUNT).isEqualTo(16);
        assertThat(report.utility()).isEqualTo(1.0 - 1.0 / 16.0);
    }

    @Test
    @DisplayName("every step carries the prediction that preceded its observation")
    void everyStepCarriesThePredictionThatPrecededItsObservation() {
        EpisodeReport report = greedyEpisode();

        assertThat(report.trajectory()).hasSize(report.steps());
        // The trajectory starts at ONE. A "step 0" in a report reads like a bug rather than
        // like a first step, and the world's own observations are numbered from one.
        assertThat(report.trajectory().get(0).index()).isEqualTo(1);

        List<String> observedSoFar = new ArrayList<>();
        int index = 1;
        for (EpisodeStep step : report.trajectory()) {
            assertThat(step.index()).isEqualTo(index++);
            assertThat(step.prediction()).isNotNull();
            assertThat(step.observation()).isNotNull();
            // The step number and the observation number are the SAME number: both are
            // one-based, so a trajectory never shows a "step 0" beside an "obs-1".
            assertThat(step.observation().id()).isEqualTo("obs-" + step.index());

            // The prediction rested on the cases the learner already held. The case this
            // step produced is NOT among them: it was not known when the prediction was made.
            assertThat(step.prediction().supportingCaseIds())
                    .doesNotContain(step.observation().id());
            for (String supporting : step.prediction().supportingCaseIds()) {
                assertThat(observedSoFar).contains(supporting);
            }
            observedSoFar.add(step.observation().id());
        }
    }

    @Test
    @DisplayName("the episode is rebuilt from the observations it produced")
    void theEpisodeIsRebuiltFromItsObservations() {
        EpisodeReport report = greedyEpisode();

        MicroWorldSession rebuilt = new MicroWorldSession("ep-w51-rebuild", new LampEnvironment());
        for (EpisodeStep step : report.trajectory()) {
            rebuilt.applyOutcome(step.observation());
        }

        assertThat(rebuilt.survivingHypotheses()).hasSize(report.finalSurvivors());
        assertThat(rebuilt.survivingHypotheses().get(0).id()).isEqualTo(report.survivorId());
        assertThat(rebuilt.appliedObservations()).hasSize(report.steps());
        assertThat(rebuilt.quarantinedObservations()).isEmpty();
    }

    @Test
    @DisplayName("the episode is rebuilt from the durable cases it wrote")
    void theEpisodeIsRebuiltFromItsDurableCases(@TempDir Path tempDir) {
        EpisodeReport report = greedyEpisode();

        Path database = tempDir.resolve("knowledge.sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            for (EpisodeStep step : report.trajectory()) {
                Case recorded = MicroWorldCodec.toCase(WORKSPACE, step.observation());
                store.recordCase(recorded);
                // The disposition is what says the observation may constrain the model. A
                // case without one is HELD by the conservative default, and a held case
                // teaches the rebuilt session nothing.
                store.adjudicateObservation(WORKSPACE, recorded.id(),
                        ObservationDisposition.ELIGIBLE,
                        "the observation was model data in the episode", null,
                        "the episode test", TIMESTAMP, null, TIMESTAMP);
            }

            List<Observation> restored = MicroWorldCodec.readCases(store, WORKSPACE);
            assertThat(restored).hasSize(report.steps());

            MicroWorldSession rebuilt = new MicroWorldSession.DefaultFactory(new LampEnvironment())
                    .create("ep-w51-rebuild", restored);
            assertThat(rebuilt.survivingHypotheses()).hasSize(report.finalSurvivors());
            assertThat(rebuilt.survivingHypotheses().get(0).id()).isEqualTo(report.survivorId());
            assertThat(rebuilt.appliedObservations()).hasSize(report.steps());
        }
    }

    @Test
    @DisplayName("an episode that runs out of steps is reported as cut short, not as learned")
    void anEpisodeThatRunsOutOfStepsIsReportedAsCutShort() {
        EpisodeReport report = new MicroWorldEpisodeRunner(new LampEnvironment(), "ep-w51-beta",
                SEED, 2).run();

        assertThat(report.terminationReason())
                .isEqualTo(EpisodeReport.TerminationReason.STEP_LIMIT_REACHED);
        assertThat(report.steps()).isEqualTo(2);
        assertThat(report.learned()).isFalse();
        assertThat(report.finalSurvivors()).isGreaterThan(1);
    }

    @Test
    @DisplayName("the exploratory policy probes the assignments in a seeded order")
    void theExploratoryPolicyProbesTheAssignmentsInASeededOrder() {
        EpisodeReport first = exploratoryEpisode(SEED);
        EpisodeReport second = exploratoryEpisode(SEED);

        // The same seed gives the same order, and the order is a permutation of the domain:
        // the learner is never asked the same question twice while the world has an
        // unasked question left.
        assertThat(second.trajectory()).isEqualTo(first.trajectory());
        assertThat(first.distinctAssignments())
                .isEqualTo(MicroWorldEpisodeRunner.EXHAUSTED_INPUT_DOMAIN_SIZE);
        assertThat(first.repeatedProbe()).isFalse();
        // And the order does not change WHAT the learner ends up knowing.
        assertThat(first.finalSurvivors()).isEqualTo(1);
        assertThat(first.survivorId()).isEqualTo(CONJUNCTION_ID);
    }

    @Test
    @DisplayName("a different seed reaches a different probe order")
    void aDifferentSeedReachesADifferentProbeOrder() {
        EpisodeReport first = exploratoryEpisode(SEED);
        EpisodeReport second = exploratoryEpisode(OTHER_SEED);

        assertThat(second.trajectory()).isNotEqualTo(first.trajectory());
        // Whatever the order was, both learners ended up knowing the same rule.
        assertThat(second.finalSurvivors()).isEqualTo(first.finalSurvivors());
        assertThat(second.survivorId()).isEqualTo(first.survivorId());
    }

    @Test
    @DisplayName("the episode carries no clock reading of its own")
    void theEpisodeCarriesNoClockReadingOfItsOwn() {
        EpisodeReport report = greedyEpisode();

        assertThat(report.timestamp())
                .isEqualTo(MicroWorldEpisodeRunner.DEFAULT_EPISODE_TIMESTAMP);
    }

    private static EpisodeReport exploratoryEpisode(long seed) {
        return new MicroWorldEpisodeRunner(new LampEnvironment(), "ep-w51-gamma", seed,
                MicroWorldEpisodeRunner.DEFAULT_STEP_LIMIT, ProbePolicy.EXPLORATORY,
                () -> MicroWorldEpisodeRunner.DEFAULT_EPISODE_TIMESTAMP).run();
    }
}
