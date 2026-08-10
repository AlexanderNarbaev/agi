package io.matrix.tsetlin;

import io.matrix.bir.Bir;
import io.matrix.bir.ClauseSetForm;
import io.matrix.bir.TtForm;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EXP-003 (SPEC-002 FR-C1): Producer comparison.
 * Tsetlin-автоматы vs ГА vs ThreeFactorRule as BIR producers.
 */
class Exp003ProducerComparisonTest {

    @Test
    void producersProduceValidBir() {
        // Tsetlin producer
        var tsetlin = new TsetlinTrainer(4, 4, 5, new Random(42));
        long[][] inputs = {{0}, {1}, {2}, {3}, {4}, {5}, {6}, {7}};
        boolean[] labels = {false, true, true, false, true, false, false, true};
        tsetlin.trainBatch(inputs, labels, 100);
        Bir tsetlinBir = tsetlin.toClauseSet("exp003-tsetlin");
        assertNotNull(tsetlinBir);
        assertEquals("clauseset", tsetlinBir.form());

        // GA producer (simulated as TT)
        long[] table = new long[1];
        for (int i = 0; i < 8; i++) {
            if (labels[i]) table[0] |= (1L << i);
        }
        Bir gaBir = new TtForm(4, table, "exp003-ga", 1.0);
        assertNotNull(gaBir);
        assertEquals("tt", gaBir.form());

        // Both should be valid BIR
        assertTrue(tsetlinBir.inputBits() > 0);
        assertTrue(gaBir.inputBits() > 0);
    }

    @Test
    void producersComparable() {
        // Both producers should handle the same input space
        var tsetlin = new TsetlinTrainer(4, 4, 5, new Random(42));
        long[][] inputs = {{0}, {1}, {2}, {3}};
        boolean[] labels = {false, false, false, true};
        tsetlin.trainBatch(inputs, labels, 100);
        Bir tsetlinBir = tsetlin.toClauseSet("test");

        long[] table = {0b1000L};
        Bir gaBir = new TtForm(4, table, "test", 1.0);

        // Both should evaluate without error
        long[] out = new long[1];
        ((ClauseSetForm) tsetlinBir).eval(new long[]{3}, out);
        ((TtForm) gaBir).eval(new long[]{3}, out);
        assertTrue(out[0] == 0 || out[0] == 1);
    }
}
