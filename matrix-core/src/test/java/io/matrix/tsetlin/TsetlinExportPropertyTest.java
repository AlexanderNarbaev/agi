package io.matrix.tsetlin;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import io.matrix.bir.ClauseSetForm;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests for the Tsetlin producer (SPEC-002 FR-B1/B2):
 * state-space bounds under arbitrary feedback sequences, seed determinism,
 * and exactness of the CLAUSESET export — {@code eval(exported)(x)} equals
 * the trainer's own firing semantics for every input.
 */
class TsetlinExportPropertyTest {

    @Provide
    Arbitrary<Integer> ks2to6() {
        return Arbitraries.integers().between(2, 6);
    }

    @Test
    void stateBoundsUnderArbitrarySequences() {
        Random rnd = new Random(7);
        for (int trial = 0; trial < 200; trial++) {
            int n = 1 + rnd.nextInt(10);
            var a = new TsetlinAutomaton(n, 1 + rnd.nextInt(2 * n));
            for (int step = 0; step < 500; step++) {
                switch (rnd.nextInt(3)) {
                    case 0 -> a.reward();
                    case 1 -> a.penalty();
                    default -> a.includeNow();
                }
                assertThat(a.state()).as("state must stay in 1..%d", 2 * n).isBetween(1, 2 * n);
                assertThat(a.includes()).isEqualTo(a.state() > n);
            }
        }
    }

    @Test
    void sameSeedProducesIdenticalClausesets() {
        long[][] inputs = {{0b01}, {0b10}, {0b11}, {0}};
        boolean[] labels = {false, false, true, false};
        var a = new TsetlinTrainer(4, 5, 8, new Random(20260823L));
        var b = new TsetlinTrainer(4, 5, 8, new Random(20260823L));
        a.trainBatch(inputs, labels, 25);
        b.trainBatch(inputs, labels, 25);
        ClauseSetForm ca = a.toClauseSet("det-check");
        ClauseSetForm cb = b.toClauseSet("det-check");
        assertThat(ca.clauses()).hasSameSizeAs(cb.clauses());
        for (int i = 0; i < ca.clauses().size(); i++) {
            assertThat(ca.clauses().get(i).pos).isEqualTo(cb.clauses().get(i).pos);
            assertThat(ca.clauses().get(i).neg).isEqualTo(cb.clauses().get(i).neg);
        }
    }

    @Property
    void exportedClausesetMatchesFiringSemantics(@ForAll("ks2to6") int k,
                                                 @ForAll long seed) {
        int clauses = 4 + (int) (Math.abs(seed) % 4);
        var trainer = new TsetlinTrainer(k, clauses, 6, new Random(seed));
        // brief deterministic training on pseudo-samples
        for (int epoch = 0; epoch < 5; epoch++) {
            for (int x = 0; x < (1 << k); x += 3) {
                trainer.trainStep(new long[]{x}, (x & 1) == 1);
            }
        }
        ClauseSetForm cs = trainer.toClauseSet("export-equivalence");
        long[] in = new long[1];
        long[] out = new long[1];
        for (int x = 0; x < (1 << k); x++) {
            in[0] = x;
            cs.eval(in, out);
            boolean dnf = out[0] == 1L;
            assertThat(dnf)
                    .as("export must reproduce firing semantics at x=%s", Long.toBinaryString(x))
                    .isEqualTo(trainer.predict(x));
        }
    }
}
