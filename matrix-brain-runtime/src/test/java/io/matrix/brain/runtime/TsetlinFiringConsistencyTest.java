package io.matrix.brain.runtime;

import io.matrix.tsetlin.TsetlinTrainer;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W37.3 — regression guard for the constant-dimension defect in {@link TsetlinTrainer#typeOne}.
 *
 * <p><b>The bug.</b> {@code typeOne} rewarded a literal based on the AUTOMATON'S OWN INCLUSION
 * STATE ({@code if (includeState == literalValue) inc(); else dec();}) rather than on whether the
 * literal was true in the input. Canonical Granmo Type Ia rewards a literal for being TRUE IN
 * {@code x}. The two rules coincide only while the automaton already agrees with the input; on a
 * dimension that never varies they are permanently opposed, so the clause is driven toward
 * requiring the opposite of what every sample contains.</p>
 *
 * <p><b>What it cost.</b> With a 16-bit input whose bits 1..15 were constant zero, not one
 * positive clause could ever fire. All 32 negative clauses fired on every input and contributed a
 * constant vote, so the learner collapsed to "always false": 301 false negatives, 0 false
 * positives out of 600 — chance accuracy on a task the label function determines completely.</p>
 *
 * <pre>
 *   BEFORE  1-bit 100.0%   16-bit constant 62.5% (11/16 seeds at exactly 50%)   noise 98.9%
 *   AFTER   1-bit 100.0%   16-bit constant 100.0% (16/16 seeds at 100%)        noise 100.0%
 * </pre>
 *
 * <p><b>Why it hid for so long.</b> Every fixture written before W37.3 gave the unused bits
 * RANDOM values, which is the one condition under which the old rule happens to bootstrap. The
 * constant-value case is what a real caller hits whenever a feature vector has an inactive
 * dimension — the normal case for a 20-bit automaton fed a 1024-bit HDC vector.</p>
 *
 * <p>Mutation-verified: reverting {@code typeOne} to key on {@code includes()} fails this test.</p>
 */
class TsetlinFiringConsistencyTest {

    private static int accuracy(TsetlinTrainer t, long[] bits, boolean[] labels) {
        int correct = 0;
        for (int i = 0; i < labels.length; i++) {
            if (t.predict(bits[i]) == labels[i]) correct++;
        }
        return correct * 100 / labels.length;
    }

    /** label = bit 0; the remaining dimensions are either constant zero or random. */
    private record Fixture(long[] bits, boolean[] labels) {}

    private static Fixture fixture(int inputBits, boolean noisyUnused, long seed) {
        Random rng = new Random(seed);
        int n = 600;
        long[] bits = new long[n];
        boolean[] labels = new boolean[n];
        for (int i = 0; i < n; i++) {
            boolean p = rng.nextBoolean();
            labels[i] = p;
            long w = 0;
            if (p) w |= 1L;
            if (noisyUnused) {
                for (int b = 1; b < inputBits; b++) if (rng.nextBoolean()) w |= 1L << b;
            }
            bits[i] = w;
        }
        return new Fixture(bits, labels);
    }

    private static int train(int inputBits, Fixture f) {
        long[][] inputs = new long[f.bits().length][];
        for (int i = 0; i < inputs.length; i++) inputs[i] = new long[] {f.bits()[i]};
        var trainer = new TsetlinTrainer(inputBits, 64, 200, new Random(42L));
        trainer.trainBatch(inputs, f.labels(), 10); // RuleInductionEngine's argument
        return accuracy(trainer, f.bits(), f.labels());
    }

    /** The defect itself. Fails if the learner ever collapses to a constant output again. */
    @Test
    @DisplayName("constant input dimensions no longer prevent learning")
    void constantDimensionsAreLearnable() {
        for (long seed = 0; seed < 8; seed++) {
            int acc = train(16, fixture(16, false, seed));
            assertThat(acc)
                    .as("seed %d: a task whose label is bit 0 must be learned even when bits 1..15 "
                            + "never vary. A score near 50 means the clauses require the opposite of "
                            + "what every sample contains.", seed)
                    .isGreaterThanOrEqualTo(95);
        }
    }

    /** The previously-working case must not regress. */
    @Test
    @DisplayName("random unused bits remain learnable")
    void randomUnusedBitsRemainLearnable() {
        for (long seed = 0; seed < 8; seed++) {
            assertThat(train(16, fixture(16, true, seed)))
                    .as("seed %d: fixing the constant case must not regress the noise case", seed)
                    .isGreaterThanOrEqualTo(95);
        }
    }

    /** The trivial case must not regress. */
    @Test
    @DisplayName("single-bit learning is unaffected")
    void singleBitUnaffected() {
        for (long seed = 0; seed < 4; seed++) {
            assertThat(train(1, fixture(1, false, seed)))
                    .as("seed %d", seed)
                    .isGreaterThanOrEqualTo(95);
        }
    }

    /**
     * A dataset with SEVERAL informative dimensions mixed with constant ones — the shape a real
     * feature vector has. The defect scaled with the number of constant dimensions, so this is
     * the case that matters.
     */
    @Test
    @DisplayName("mixed informative and constant dimensions are learned")
    void mixedDimensionsAreLearned() {
        for (long seed = 0; seed < 4; seed++) {
            Random rng = new Random(seed);
            int n = 600;
            long[] bits = new long[n];
            boolean[] labels = new boolean[n];
            for (int i = 0; i < n; i++) {
                // label = XOR of the two informative bits; bits 2..15 are constant zero
                long w = 0;
                if (rng.nextBoolean()) w |= 1L;
                if (rng.nextBoolean()) w |= 2L;
                bits[i] = w;
                labels[i] = (Long.bitCount(w & 3L) & 1) == 1;
            }
            long[][] inputs = new long[n][];
            for (int i = 0; i < n; i++) inputs[i] = new long[] {bits[i]};
            var trainer = new TsetlinTrainer(16, 64, 200, new Random(42L));
            trainer.trainBatch(inputs, labels, 10);

            assertThat(accuracy(trainer, bits, labels))
                    .as("seed %d: XOR of bits 0..1 with 14 constant dimensions", seed)
                    .isGreaterThanOrEqualTo(95);
        }
    }
}