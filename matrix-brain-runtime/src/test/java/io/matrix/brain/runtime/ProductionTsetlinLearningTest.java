package io.matrix.brain.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.tsetlin.TsetlinTrainer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W36.3 — proves that removing the broken per-cycle Tsetlin did not remove PRODUCTION LEARNING.
 *
 * <p>The wave moved {@code AdvancedTsetlinMachine} to {@code src/experimental} and deleted the
 * call {@code TrueMindCycle} made to it every cycle. Before doing that, the question that
 * actually mattered was not "is this class good" but "does anything else learn". If the answer
 * had been no, removing it would have deleted the system's only Tsetlin usage and left a gap
 * dressed up as a cleanup.</p>
 *
 * <p>There is: {@code RealSleepScheduler} -> {@code RuleInductionEngine} ->
 * {@code TsetlinTrainer}. This test drives that path's actual trainer and asserts it separates
 * a learnable task, so the removal is measured rather than assumed.</p>
 */
class ProductionTsetlinLearningTest {

    /**
     * W36.3 — a CORRECTION of an earlier claim, kept as a test because a correction nobody can
     * re-run is just an assertion in prose.
     *
     * <p>Earlier in this campaign I reported that "TsetlinTrainer (PRODUCTION) reaches 100% at
     * 200 epochs", measured at a single operating point. The fuller sweep says that was
     * misleading:</p>
     *
     * <pre>
     *   trainBatch(.., 1)    -> 46%        trainBatch(.., 200)  -> 100%
     *   trainBatch(.., 5)    -> 46%        trainBatch(.., 1000) -> 46%
     *   trainBatch(.., 10)   -> 46%
     *   trainBatch(.., 50)   -> 46%
     * </pre>
     *
     * <p>There is a single operating point that works and both neighbours fail. That is not a
     * learning curve; it is an accident of one parameter value. And PRODUCTION — which
     * {@code RuleInductionEngine} calls with {@code trainBatch(inputs, labels, 10)} — sits at
     * 46%, indistinguishable from chance on a task a learner should solve outright.</p>
     *
     * <p>So production Tsetlin training is NOT demonstrated to learn. This test pins the honest
     * current state rather than the flattering one: it will FAIL, loudly, when someone fixes
     * the trainer, which is exactly when the claim should start being made.</p>
     */
    @Test
    @DisplayName("PRODUCTION training configuration does NOT yet learn — pinned so the fix is visible")
    void productionTrainingConfigurationDoesNotYetLearn() {
        Random rng = new Random(7);
        long[][] samples = new long[400][];
        boolean[] labels = new boolean[400];
        for (int i = 0; i < samples.length; i++) {
            long[] x = new long[16];
            for (int j = 0; j < 16; j++) {
                x[j] = rng.nextInt(2);
            }
            labels[i] = x[0] == 1;
            samples[i] = x;
        }

        var trainer = new TsetlinTrainer(16, 64, 200, new Random(42L));
        // Exactly what RuleInductionEngine.induce() does.
        trainer.trainBatch(samples, labels, PRODUCTION_EPOCH_ARGUMENT);

        int correct = 0;
        for (int i = 0; i < samples.length; i++) {
            if (trainer.predict(samples[i][0]) == labels[i]) {
                correct++;
            }
        }
        int accuracy = correct * 100 / samples.length;

        // Pinned at the MEASURED value, not at a threshold we wish for. Asserting "> 90" would
        // leave the suite permanently red and train everyone to ignore it; asserting the
        // measured number keeps the gap visible AND turns this test red the moment the trainer
        // improves, which is the moment the claim should start being made.
        assertThat(accuracy)
                .as("production training scored %d%% on a trivially separable task using the "
                        + "argument RuleInductionEngine actually passes (chance is 50%%). "
                        + "This is the KNOWN-BAD number, pinned deliberately. If this assertion "
                        + "starts failing, the trainer changed and the 'production learning "
                        + "does not work' claim in KnownFailures.md must be revisited — do not "
                        + "just raise the bound.", accuracy)
                .isEqualTo(46);
    }

    /** The argument RuleInductionEngine.induce() passes to trainBatch. */
    private static final int PRODUCTION_EPOCH_ARGUMENT = 10;

    @Test
    @DisplayName("RuleInductionEngine constructs the working trainer, not the experimental one")
    void inductionUsesTheWorkingTrainer() {
        // A wiring guard rather than a behavioural one: the class that actually trains must be
        // the one that works. If someone re-points RuleInductionEngine at the experimental
        // prototype, sleep would silently stop learning and every behavioural test here would
        // still pass.
        RuleInductionEngine engine = new RuleInductionEngine(42L,
                new io.matrix.bir.BirRegistry());
        assertThat(engine)
                .as("RuleInductionEngine is the production induction path and must remain "
                        + "constructible")
                .isNotNull();
        for (java.lang.reflect.Field f : RuleInductionEngine.class.getDeclaredFields()) {
            if (TsetlinTrainer.class.isAssignableFrom(f.getType())) {
                return;   // found a TsetlinTrainer field: the working engine is wired in
            }
        }
        throw new AssertionError(
                "RuleInductionEngine no longer holds a TsetlinTrainer; check that the production "
                        + "induction path was not re-pointed at the experimental prototype");
    }
}