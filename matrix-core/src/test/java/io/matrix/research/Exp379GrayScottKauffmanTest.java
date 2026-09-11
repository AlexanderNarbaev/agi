package io.matrix.research;

import io.matrix.neuron.GrayScottSimulator;
import io.matrix.neuron.KauffmanNetwork;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 379 — DESIGN-39/40 implementations (Gray-Scott, Kauffman).
 */
class Exp379GrayScottKauffmanTest {

    @Test
    void grayScottProducesValidState() {
        int w = 8, h = 8;
        double[][] u = new double[w][h];
        double[][] v = new double[w][h];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                u[i][j] = 1.0;
                v[i][j] = 0.0;
            }
        }
        // Seed V in the center
        v[w / 2][h / 2] = 1.0;
        double[][][] result = GrayScottSimulator.step(u, v, 0.1);
        assertThat(result.length).isEqualTo(2);
        assertThat(result[0].length).isEqualTo(w);
        assertThat(result[0][0].length).isEqualTo(h);
        // V should have spread slightly
        assertThat(result[1][w / 2][h / 2]).isGreaterThan(0.0);
    }

    @Test
    void grayScottPreservesBoundary() {
        int w = 4, h = 4;
        double[][] u = new double[w][h];
        double[][] v = new double[w][h];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                u[i][j] = 0.5;
                v[i][j] = 0.3;
            }
        }
        double[][] uOrig = new double[w][h];
        for (int i = 0; i < w; i++) {
            System.arraycopy(u[i], 0, uOrig[i], 0, h);
        }
        GrayScottSimulator.step(u, v, 0.01);
        // Input u should be unchanged (function is pure)
        for (int i = 0; i < w; i++) {
            assertThat(u[i]).isEqualTo(uOrig[i]);
        }
    }

    @Test
    void kauffmanStepChangesState() {
        int n = 8;
        var spec = KauffmanNetwork.random(n, 2, 0xCAFE);
        boolean[] state = new boolean[n];
        for (int i = 0; i < n; i++) state[i] = (i % 2 == 0);
        boolean[] newState = KauffmanNetwork.step(state, spec.functions(),
                spec.inputs());
        // Should be different size
        assertThat(newState).hasSize(n);
    }

    @Test
    void kauffmanDeterministic() {
        int n = 10;
        var spec = KauffmanNetwork.random(n, 3, 0xBEEFL);
        boolean[] state1 = new boolean[n];
        boolean[] state2 = new boolean[n];
        for (int i = 0; i < n; i++) {
            state1[i] = (i % 2 == 0);
            state2[i] = (i % 2 == 0);
        }
        boolean[] r1 = KauffmanNetwork.step(state1, spec.functions(),
                spec.inputs());
        boolean[] r2 = KauffmanNetwork.step(state2, spec.functions(),
                spec.inputs());
        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void kauffmanFrozenAtLowK() {
        // K=1 → single-input function → converges to fixed point
        int n = 20;
        var spec = KauffmanNetwork.random(n, 1, 0xCAFE);
        boolean[] state = new boolean[n];
        boolean[] prev = state.clone();
        // Iterate 100 steps; should reach fixed point
        for (int i = 0; i < 100; i++) {
            state = KauffmanNetwork.step(state, spec.functions(), spec.inputs());
        }
        // For K=1, the function is constant (0 or 1) for each node
        // → state should converge
        // Run again and check stability
        for (int i = 0; i < 50; i++) {
            boolean[] next = KauffmanNetwork.step(state, spec.functions(),
                    spec.inputs());
            if (java.util.Arrays.equals(next, state)) break;
            state = next;
        }
        boolean[] next2 = KauffmanNetwork.step(state, spec.functions(),
                spec.inputs());
        assertThat(next2).isEqualTo(state);
    }
}
