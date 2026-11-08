package io.matrix.safety;

import io.matrix.cognitive.MicroWorldCodec;
import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.cognitive.microworld.Observation;
import io.matrix.cognitive.microworld.ObservationDisposition;
import io.matrix.knowledge.Case;
import io.matrix.knowledge.KnowledgeStore;
import io.matrix.learning.env.EpisodeReport;
import io.matrix.learning.env.EpisodeStep;
import io.matrix.learning.env.MicroWorldEpisodeRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W51 §11 — the learning episode across a restart.
 *
 * <p><b>What this file is for.</b> §11 asks what a restart in the middle of a learning
 * episode leaves behind. The hazard is specific: an episode that was cut short must not
 * leave a "learned" state behind, and an episode that finished must be restored with exactly
 * the knowledge it had — not more, and not less. A system that recovers a model it never
 * earned would be reporting a conclusion it did not reach.</p>
 *
 * <p><b>Why the durable cases are the thing under test.</b> The microworld keeps no private
 * copy of what it saw: the cases in the store ARE its episodic memory, and the session that
 * comes back after a restart is rebuilt from them. The test therefore writes the cases the
 * way the kernel writes them, reads them back the way the kernel reads them, and compares
 * the model that comes out with the model that went in.</p>
 *
 * <p><b>Why the interruption is explicit.</b> The episode is cut short by a step bound, not
 * by a simulated crash: an episode that ran out of steps is a real episode that ended early,
 * and the record of it says so.</p>
 */
class EpisodeRestartSafetyTest {

    /** The instant every durable record in this file carries. */
    private static final String AT = "1970-01-01T00:00:00Z";

    /** The workspace the durable cases are written to. */
    private static final String WORKSPACE = "safety-episode";

    /** The number of probes the interrupted episode is allowed to make. */
    private static final int INTERRUPTED_AFTER_STEPS = 2;

    /** The seed of the episodes in this file. */
    private static final long SEED = 42L;

    /** The identity of the first episode. */
    private static final String FIRST_EPISODE = "ep-safety-first";

    /** The identity of the interrupted episode. */
    private static final String INTERRUPTED_EPISODE = "ep-safety-interrupted";

    @TempDir
    Path directory;

    @Test
    @DisplayName("§11.11 — an episode that was cut short leaves no learned state, and the restart reproduces what it knew")
    void anEpisodeThatWasCutShortLeavesNoLearnedState() {
        EpisodeReport report = new MicroWorldEpisodeRunner(new LampEnvironment(),
                INTERRUPTED_EPISODE, SEED, INTERRUPTED_AFTER_STEPS).run();

        assertThat(report.terminationReason())
                .isEqualTo(EpisodeReport.TerminationReason.STEP_LIMIT_REACHED);
        assertThat(report.learned())
                .as("an episode that was cut short has not learned the rule")
                .isFalse();
        assertThat(report.finalSurvivors()).isGreaterThan(1);

        MicroWorldSession restored = restore(report);

        assertThat(restored.survivingHypotheses())
                .as("the restart must reproduce exactly what the episode knew")
                .hasSize(report.finalSurvivors());
        assertThat(restored.survivingHypotheses().get(0).id())
                .as("an interrupted episode must not come back as a settled one")
                .isNotBlank();
        assertThat(restored.survivingHypotheses())
                .as("an interrupted episode must not come back as a settled one")
                .hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("§11.11 — a finished episode is restored with the same survivor count and the same rule")
    void aFinishedEpisodeIsRestoredWithTheSameRule() {
        EpisodeReport report = new MicroWorldEpisodeRunner(new LampEnvironment(),
                FIRST_EPISODE, SEED).run();

        assertThat(report.learned()).isTrue();
        assertThat(report.finalSurvivors()).isEqualTo(1);

        MicroWorldSession restored = restore(report);

        assertThat(restored.survivingHypotheses()).hasSize(report.finalSurvivors());
        assertThat(restored.survivingHypotheses().get(0).id()).isEqualTo(report.survivorId());
        assertThat(restored.appliedObservations()).hasSize(report.steps());
    }

    /**
     * Writes the episode's observations the way the kernel writes them, reads them back the
     * way the kernel reads them, and rebuilds the session from what came back.
     */
    private MicroWorldSession restore(EpisodeReport report) {
        Path database = directory.resolve(report.episodeId() + ".sqlite");
        try (KnowledgeStore store = new KnowledgeStore(database)) {
            for (EpisodeStep step : report.trajectory()) {
                Case recorded = MicroWorldCodec.toCase(WORKSPACE, step.observation());
                store.recordCase(recorded);
                // The disposition is what says the observation may constrain the model. A case
                // without one is HELD by the conservative default, and a held case teaches the
                // restored session nothing.
                store.adjudicateObservation(WORKSPACE, recorded.id(),
                        ObservationDisposition.ELIGIBLE,
                        "the observation was model data in the episode", null, "the safety test",
                        AT, null, AT);
            }

            List<Observation> remembered = MicroWorldCodec.readCases(store, WORKSPACE);
            assertThat(remembered).hasSize(report.steps());
            return new MicroWorldSession.DefaultFactory(new LampEnvironment())
                    .create(report.episodeId(), remembered);
        }
    }
}
