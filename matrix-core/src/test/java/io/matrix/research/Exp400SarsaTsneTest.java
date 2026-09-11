package io.matrix.research;

import io.matrix.neuron.SARSA;
import io.matrix.neuron.TSNE;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 400 — DESIGN-52/53 implementations (SARSA, t-SNE).
 */
class Exp400SarsaTsneTest {

    @Test
    void sarsaUpdate() {
        // Same scenario as Q-Learning: Q=0.5, reward=1, next state max=0.7, but
        // SARSA uses the actual next action's Q (0.4) instead of max
        // new Q = 0.5 + 0.1 * (1 + 0.9 * 0.4 - 0.5) = 0.5 + 0.1 * 0.86 = 0.586
        double[][] q = {{0.5, 0.0}, {0.7, 0.4}};
        double[][] newQ = SARSA.update(q, 0, 0, 1.0, 1, 1, 0.1, 0.9);
        assertThat(newQ[0][0]).isCloseTo(0.586,
                org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void sarsaUsesActualActionNotMax() {
        // SARSA is on-policy — uses actual next action's Q, not max
        double[][] q = {{0.0, 0.0}, {0.9, 0.0}};  // max = 0.9, but actual = 0.0
        // If next action is 1 (with Q=0), new = 0 + 0.1 * (0 + 0.9 * 0 - 0) = 0
        double[][] newQ = SARSA.update(q, 0, 0, 0.0, 1, 1, 0.1, 0.9);
        assertThat(newQ[0][0]).isEqualTo(0.0);
    }

    @Test
    void sarsaPure() {
        // Same input → same output (CONSTITUTION I)
        double[][] q = {{0.3, 0.1}, {0.4, 0.2}};
        double[][] a = SARSA.update(q, 0, 0, 0.5, 1, 0, 0.1, 0.9);
        double[][] b = SARSA.update(q, 0, 0, 0.5, 1, 0, 0.1, 0.9);
        assertThat(a[0][0]).isEqualTo(b[0][0]);
    }

    @Test
    void tsneProjectsTo2D() {
        // 5 points in 4D, two clusters
        double[][] X = {
                {0, 0, 0, 0},
                {0.1, 0.1, 0.1, 0.1},
                {10, 10, 10, 10},
                {10.1, 10.1, 10.1, 10.1},
                {20, 20, 20, 20}
        };
        var proj = TSNE.project(X, 5.0, 50, 100.0, 0xCAFE);
        assertThat(proj.y().length).isEqualTo(5);
        assertThat(proj.y()[0].length).isEqualTo(2);
    }

    @Test
    void tsneClustersPreserved() {
        // Two well-separated clusters
        double[][] X = new double[10][];
        for (int i = 0; i < 5; i++) {
            X[i] = new double[]{i * 0.1, 0, 0, 0};
        }
        for (int i = 5; i < 10; i++) {
            X[i] = new double[]{100 + i, 0, 0, 0};
        }
        var proj = TSNE.project(X, 5.0, 200, 50.0, 0xCAFE);
        // Points 0-4 should be closer to each other than to points 5-9
        double d00to04 = dist(proj.y()[0], proj.y()[4]);
        double d00to55 = dist(proj.y()[0], proj.y()[5]);
        assertThat(d00to04).isLessThan(d00to55);
    }

    @Test
    void tsneDeterministic() {
        double[][] X = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
        var a = TSNE.project(X, 2.0, 30, 100.0, 0xCAFE);
        var b = TSNE.project(X, 2.0, 30, 100.0, 0xCAFE);
        // Same seed → same projection (CONSTITUTION I)
        for (int i = 0; i < a.y().length; i++) {
            assertThat(a.y()[i][0]).isCloseTo(b.y()[i][0],
                    org.assertj.core.data.Offset.offset(1e-9));
            assertThat(a.y()[i][1]).isCloseTo(b.y()[i][1],
                    org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    private static double dist(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }
}
