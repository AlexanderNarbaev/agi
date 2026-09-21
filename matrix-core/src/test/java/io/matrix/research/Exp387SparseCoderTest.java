package io.matrix.research;

import io.matrix.neuron.SparseCoder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 387 — DESIGN-42 SparseCoder implementation.
 */
class Exp387SparseCoderTest {

    @Test
    void sparseCodeRecoversSignal() {
        // Dictionary: 2 atoms
        double[][] D = {
                {1.0, 0.5},
                {0.0, 0.866}
        };
        // Signal = 2 * atom0 + 0 * atom1
        double[] x = {2.0, 0.0};
        double[] a = SparseCoder.encode(x, D, 0.1, 100);
        // With L1 penalty small, should recover a ≈ [2, 0]
        assertThat(a[0]).isCloseTo(2.0, org.assertj.core.data.Offset.offset(0.5));
        assertThat(a[1]).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.5));
    }

    @Test
    void highLambdaProducesSparserCode() {
        double[][] D = {
                {1.0, 0.0},
                {0.0, 1.0}
        };
        double[] x = {1.0, 0.5};
        // Low lambda: less sparse
        double[] aLow = SparseCoder.encode(x, D, 0.01, 50);
        // High lambda: more sparse (more zeros)
        double[] aHigh = SparseCoder.encode(x, D, 0.5, 50);
        int zerosLow = (Math.abs(aLow[0]) < 0.01 ? 1 : 0) + (Math.abs(aLow[1]) < 0.01 ? 1 : 0);
        int zerosHigh = (Math.abs(aHigh[0]) < 0.01 ? 1 : 0) + (Math.abs(aHigh[1]) < 0.01 ? 1 : 0);
        assertThat(zerosHigh).isGreaterThanOrEqualTo(zerosLow);
    }

    @Test
    void reconstructionErrorZeroForIdentity() {
        // Identity dictionary + small lambda → low error
        double[][] D = {
                {1.0, 0.0},
                {0.0, 1.0}
        };
        double[] x = {0.5, 0.3};
        double[] a = SparseCoder.encode(x, D, 0.001, 100);
        double err = SparseCoder.reconstructionError(x, D, a);
        assertThat(err).isLessThan(0.1);
    }
}
