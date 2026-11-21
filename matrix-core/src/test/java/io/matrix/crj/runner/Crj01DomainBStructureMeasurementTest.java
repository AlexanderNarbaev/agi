package io.matrix.crj.runner;

import io.matrix.cognitive.MicroWorldComputation;
import io.matrix.cognitive.MicroWorldReferenceMethod;
import io.matrix.cognitive.MicroWorldSettledModelMethod;
import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.cognitive.microworld.Observation;
import io.matrix.cognitive.microworld.WorldInput;
import io.matrix.learning.env.EpisodeReport;
import io.matrix.learning.env.MicroWorldEpisodeRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 — the DOMAIN-B STRUCTURE: what the lamp world can and cannot measure.
 *
 * <p><b>Why this measurement exists.</b> The protocol's domain-B metrics are built on a
 * <b>transfer</b>: M-B4 is "transfer answer accuracy" over "the sealed episodes' <b>novel
 * query assignments</b> (assignments that episode had not observed)", and the domain's
 * primary metric, M-B5, is "cost per correct answer". Both need a case where an arm
 * answers an assignment the episode had NOT observed — and the lamp world has exactly
 * <b>four</b> assignments.</p>
 *
 * <p><b>What was measured, and what it shows.</b> An episode with the default bound probes
 * <b>all four</b> assignments: the model settles, and there is <b>no novel query left</b> —
 * so M-B4's denominator would be <b>zero</b>. An episode with a smaller bound leaves a
 * novel query, but then the surviving hypotheses <b>disagree</b> about it, and the
 * reference method <b>refuses</b> — it refuses rather than answering from a hypothesis set
 * that does not choose. And the settled model is inapplicable, because the model has not
 * settled.</p>
 *
 * <p><b>Why this matters.</b> If the lamp world as it exists cannot produce a transfer
 * case, then a sealed domain-B split of 18 episodes would produce 18 rows whose
 * transfer metrics have a <b>zero denominator</b> — and the owner should know that BEFORE
 * commissioning it, not after.</p>
 */
class Crj01DomainBStructureMeasurementTest {

    /** The budget every arm runs under. */
    private static final ComputeBudget BUDGET = new ComputeBudget(30_000L, 30_000L, 0L, 0);

    @Test
    @DisplayName("the lamp world's structure decides what domain B can measure")
    void theStructureDecidesWhatDomainBCanMeasure() {
        System.out.println("[CRJ-01] the lamp world's structure, per step bound:");
        System.out.println("[CRJ-01] bound | episodes | settled | novel queries | reference answered"
                + " | settled-model applicable");
        for (int bound = 1; bound <= 4; bound++) {
            int episodes = 0;
            int settled = 0;
            int novelQueries = 0;
            int referenceAnswered = 0;
            int settledModelApplicable = 0;
            for (int index = 0; index < Crj01DomainBRunner.DEV_EPISODES; index++) {
                long seed = Crj01DomainBRunner.PRECOMMITTED_SEEDS.get(index);
                String episodeId = "crj01-structure-" + bound + "-" + index;
                LampEnvironment environment = new LampEnvironment();
                EpisodeReport report = new MicroWorldEpisodeRunner(environment, episodeId, seed,
                        bound).run();
                episodes++;

                List<Observation> applied = new ArrayList<>();
                for (var step : report.trajectory()) {
                    if (step.applied()) {
                        applied.add(step.observation());
                    }
                }
                MicroWorldSession session = new MicroWorldSession.DefaultFactory(environment)
                        .create(episodeId, applied);

                Set<String> observed = new LinkedHashSet<>();
                for (Observation observation : session.observations()) {
                    observed.add(observation.assignmentKey());
                }
                List<WorldInput> novel = new ArrayList<>();
                for (boolean power : new boolean[] {false, true}) {
                    for (boolean toggle : new boolean[] {false, true}) {
                        WorldInput input = WorldInput.of(power, toggle);
                        if (!observed.contains(input.key())) {
                            novel.add(input);
                        }
                    }
                }
                novelQueries += novel.size();
                if (report.learned()) {
                    settled++;
                }
                if (!novel.isEmpty()) {
                    try {
                        MicroWorldReferenceMethod reference = new MicroWorldReferenceMethod();
                        reference.execute(new MicroWorldComputation(novel.get(0), session,
                                Set.of()), BUDGET);
                        referenceAnswered++;
                    } catch (RuntimeException refused) {
                        // the reference refuses when the survivors disagree: that IS the
                        // finding, and it is not an error
                    }
                    if (session.survivingHypotheses().size() == 1) {
                        try {
                            MicroWorldSettledModelMethod method = new MicroWorldSettledModelMethod();
                            method.execute(new MicroWorldComputation(novel.get(0), session,
                                    Set.of(MicroWorldSettledModelMethod.PRECONDITION_MODEL_SETTLED)),
                                    BUDGET);
                            settledModelApplicable++;
                        } catch (RuntimeException refused) {
                            // inapplicable: also the finding
                        }
                    }
                }
            }
            System.out.printf("[CRJ-01] %5d | %8d | %7d | %14d | %17d | %24d%n",
                    bound, episodes, settled, novelQueries, referenceAnswered,
                    settledModelApplicable);
        }

        // ── the structure, stated as an assertion ────────────────────────────────
        LampEnvironment environment = new LampEnvironment();
        EpisodeReport exhausted = new MicroWorldEpisodeRunner(environment, "crj01-exhausted",
                Crj01DomainBRunner.PRECOMMITTED_SEEDS.get(0), 4).run();
        assertThat(exhausted.learned())
                .as("an episode that exhausts the four assignments settles the model")
                .isTrue();
        assertThat(exhausted.steps()).as("and it takes all four probes").isEqualTo(4);

        EpisodeReport early = new MicroWorldEpisodeRunner(new LampEnvironment(),
                "crj01-early", Crj01DomainBRunner.PRECOMMITTED_SEEDS.get(0), 3).run();
        assertThat(early.steps()).as("an episode with a bound of three stops early").isEqualTo(3);
        assertThat(early.learned())
                .as("and the model has NOT settled on three of the four assignments")
                .isFalse();
    }
}
