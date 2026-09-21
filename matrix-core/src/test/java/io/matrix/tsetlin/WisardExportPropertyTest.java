package io.matrix.tsetlin;

import io.matrix.bir.ClauseSetForm;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Producer-contract parity (DESIGN-14 / SPEC-002 FR-B2): the WiSARD
 * decision function distilled into CLAUSESET must evaluate identically to
 * {@code classifyLabel} over the whole input space; single-pass
 * memorization must reach perfect training accuracy (seeded).
 */
class WisardExportPropertyTest {

    @Test
    void exportMatchesClassifyExhaustively() {
        int k = 8;
        var w = new WisardProducer(k, 4, 42L);
        Random rnd = new Random(42L);
        var in = new java.util.ArrayList<long[]>();
        var lb = new java.util.ArrayList<Integer>();
        for (int i = 0; i < 40; i++) {
            long x = rnd.nextLong() & ((1L << k) - 1);
            in.add(new long[]{x});
            lb.add(Long.bitCount(x) >= 4 ? 1 : 0);
            w.train(new long[]{x}, Long.bitCount(x) >= 4 ? 1 : 0);
        }
        ClauseSetForm cs = w.toDecisionClauseSet("wisard-distilled");
        assertThat(cs.inputBits()).isEqualTo(k);
        long[] out = new long[1];
        for (int x = 0; x < (1 << k); x++) {
            cs.eval(new long[]{x}, out);
            boolean bir = out[0] == 1L;
            assertThat(bir)
                    .as("x=%s", Long.toBinaryString(x))
                    .isEqualTo(w.classifyLabel(new long[]{x}) == 1);
        }
    }

    @Test
    void singlePassMemorizationPerfectOnTrainSet() {
        int k = 6;
        var w = new WisardProducer(k, 3, 7L);
        var in = new java.util.ArrayList<long[]>();
        var lb = new java.util.ArrayList<Integer>();
        for (int i = 0; i < 30; i++) {
            long x = (i * 5 + 1) & ((1L << k) - 1); // distinct, collision-free
            in.add(new long[]{x});
            int label = i % 2;
            lb.add(label);
            w.train(new long[]{x}, label);
        }
        assertThat(w.accuracy(in, lb)).isEqualTo(1.0);
    }
}
