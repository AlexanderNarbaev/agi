package io.matrix.cluster;

import io.matrix.bir.BooleanRuntime;
import io.matrix.bir.TruthTableAdapter;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SPEC-002 Критерий A tracer (DESIGN-14 wave 1): migrating
 * {@code NeuronClusterActor.longEvaluate} to the BIR execution path must be
 * observationally equivalent to the legacy {@code TruthTable.evaluate} for
 * arbitrary tables and inputs. Seeded randomization only — no wall-clock.
 */
class BirMigrationEquivalenceTest {

    @Test
    void birPathMatchesLegacyEvaluateOnRandomTables() {
        Random rnd = new Random(20260823L);
        for (int trial = 0; trial < 64; trial++) {
            int k = 1 + rnd.nextInt(12);
            BitSet bits = new BitSet(1 << k);
            for (int i = 0; i < (1 << k); i++) bits.set(i, rnd.nextBoolean());
            TruthTable table = TruthTable.of(k, bits);
            var form = TruthTableAdapter.toBir(table);

            for (int probe = 0; probe < 64; probe++) {
                long packed = rnd.nextLong();
                // legacy path (deprecated, kept until Критерий A completes)
                boolean legacy = table.evaluate(new long[]{packed});
                // BIR path (single execution point)
                boolean bir = BooleanRuntime.evaluate(form, new long[]{packed})[0] == 1L;
                if (legacy != bir) {
                    throw new AssertionError("mismatch k=" + k + " packed=" + packed);
                }
                assertThat(bir).isEqualTo(legacy);
            }
        }
    }
}
