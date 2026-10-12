package io.matrix.brain.runtime;

import io.matrix.tsetlin.TsetlinTrainer;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W37.3 — CHARACTERISATION of {@link TsetlinTrainer} on constant vs. noisy unused bits.
 *
 * <p><b>This is not a fix and does not assert one.</b> It pins a measured property of the
 * trainer so the behaviour cannot change silently, and it records the open defect that W37.3
 * must actually close.</p>
 *
 * <p><b>Measured behaviour (production config: 16 bits, 64 clauses, 200 states, 10 epochs).</b></p>
 *
 * <pre>
 *   label = bit 0, bits 1..15 CONSTANT ZERO      ->  46-53%   (chance)
 *   label = bit 0, bits 1..15 RANDOM             -> 100%
 *   same data, InitStrategy.RANDOM               -> 46% / 100%
 *   same data, InitStrategy.COMPLEMENTARY        -> 46% / 100%
 * </pre>
 *
 * <p>The gap between those two rows is the defect. The label is fully determined by bit 0 in
 * both cases, so noise in the unused bits should be irrelevant. Instead noise is worth ~50
 * points of accuracy. The trainer is relying on noise to make unused dimensions
 * <em>reachable</em>: a clause that includes a bit which never varies can never fire, so on
 * constant-zero inputs the only clauses able to vote are the few keyed on bit 0, and the vote
 * degenerates toward a constant answer ({@code predict(0) == predict(1)}).</p>
 *
 * <p>Hypotheses tested and ELIMINATED, so they are not re-tried:</p>
 * <ul>
 *   <li>Initialisation strategy — both RANDOM and COMPLEMENTARY behave identically.</li>
 *   <li>Epoch count — 46% at 10, 50, 200 and 1000 epochs alike.</li>
 *   <li>The update/predict firing mismatch ({@code updateMode ? true : anyIncluded}). Changing
 *       it to standard Tsetlin semantics ("a clause fires iff no included literal is
 *       violated") was TRIED and REVERTED: it made {@code predict(0)==predict(1)} on constant
 *       inputs, because with consistent semantics every empty clause fires on every input and
 *       the polarity sum no longer depends on the input at all. It was reverted rather than
 *       committed, because it traded one degenerate regime for another.</li>
 * </ul>
 *
 * <p>The remaining hypothesis is that a constant dimension must be <em>detected</em> and the
 * clauses keyed on it excluded from initialisation — i.e. the learner needs feature-variance
 * awareness, not a different firing rule. Unverified; see KnownFailures.md.</p>
 *
 * <p>Note for whoever tunes this next: a fixture whose unused bits are RANDOM will report 100%
 * and hide the defect completely. The constant-zero case is the one that shows the truth, and
 * it is the case a real caller hits whenever a feature vector has an inactive dimension.</p>
 */
class TsetlinFiringConsistencyTest {

    private static int accuracy(TsetlinTrainer t, long[] bits, boolean[] labels) {
        int correct = 0;
        for (int i = 0; i < labels.length; i++) {
            if (t.predict(bits[i]) == labels[i]) correct++;
        }
        return correct * 100 / labels.length;
    }

    private record Fixture(long[] clean, long[] noisy, boolean[] labels) {}

    private static Fixture fixture() {
        Random rng = new Random(7);
        int n = 600;
        long[] clean = new long[n];
        long[] noisy = new long[n];
        boolean[] labels = new boolean[n];
        for (int i = 0; i < n; i++) {
            boolean p = rng.nextBoolean();
            labels[i] = p;
            clean[i] = p ? 1L : 0L;
            long w = 0;
            for (int b = 1; b < 16; b++) if (rng.nextBoolean()) w |= 1L << b;
            noisy[i] = p ? (w | 1L) : w;
        }
        return new Fixture(clean, noisy, labels);
    }

    private static int train(long[] bits, boolean[] labels) {
        long[][] inputs = new long[bits.length][];
        for (int i = 0; i < bits.length; i++) inputs[i] = new long[] {bits[i]};
        var trainer = new TsetlinTrainer(16, 64, 200, new Random(42L));
        trainer.trainBatch(inputs, labels, 10); // RuleInductionEngine's argument
        return accuracy(trainer, bits, labels);
    }

    /**
     * Pins the measured asymmetry. If this ever fails, the trainer changed and the numbers
     * recorded in KnownFailures.md must be revisited.
     */
    @Test
    @DisplayName("CHARACTERISATION: random noise in unused bits is worth ~50 accuracy points")
    void noiseInUnusedBitsDominatesResult() {
        Fixture f = fixture();
        int clean = train(f.clean(), f.labels());
        int noisy = train(f.noisy(), f.labels());

        System.out.printf("  constant-zero unused bits: %d%%%n", clean);
        System.out.printf("  random unused bits:        %d%%%n", noisy);

        assertThat(noisy)
                .as("noise must not be load-bearing for learning")
                .isGreaterThan(clean + 30);
    }

    /**
     * The defect itself, stated as a failing assertion on purpose would leave the suite red, so
     * it is stated as a documented inequality that is currently FALSE.
     *
     * <p>This asserts the honest gap rather than a desired number: if someone fixes the trainer,
     * this fails and forces the KnownFailures entry to be updated. It passes today because the
     * defect is present.</p>
     */
    @Test
    @DisplayName("OPEN DEFECT: constant dimensions make a learnable task unreachable")
    void constantDimensionsAreCurrentlyHarmful() {
        int clean = train(fixture().clean(), fixture().labels());
        System.out.printf("  documented open defect: constant-bit accuracy is %d%%%n", clean);
        assertThat(clean)
                .as("currently 46-53%, i.e. chance. If this assertion FAILS, the trainer has "
                        + "been fixed and KnownFailures.md must be updated to say so.")
                .isLessThan(70);
    }
}
