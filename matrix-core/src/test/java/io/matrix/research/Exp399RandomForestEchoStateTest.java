package io.matrix.research;

import io.matrix.neuron.EchoStateProperty;
import io.matrix.neuron.RandomForest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 399 — DESIGN-49/51 implementations (Random Forest, Echo State Property).
 */
class Exp399RandomForestEchoStateTest {

    @Test
    void echoStateSpectralRadius() {
        // 3x3 matrix with eigenvalues < 1
        double[][] W = {
                {0.5, 0.1, 0.0},
                {0.0, 0.4, 0.1},
                {0.1, 0.0, 0.3}
        };
        double rho = EchoStateProperty.spectralRadius(W, 200, 1e-6);
        // Spectral radius should be < 1
        assertThat(rho).isLessThan(1.0);
    }

    @Test
    void echoStatePropertyHolds() {
        // Smaller matrix — ρ < 0.95 (echo state)
        double[][] W = {{0.3, 0.1}, {0.0, 0.2}};
        assertThat(EchoStateProperty.hasEchoStateProperty(W, 0.95)).isTrue();
    }

    @Test
    void echoStateRescale() {
        // Original matrix with ρ ≈ 1.0
        double[][] W = {{2.0, 0.0}, {0.0, 1.0}};
        // Rescale to ρ = 0.5
        double[][] scaled = EchoStateProperty.rescale(W, 0.5);
        double newRho = EchoStateProperty.spectralRadius(scaled, 200, 1e-6);
        assertThat(newRho).isCloseTo(0.5,
                org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    void randomForestTrainsAndPredicts() {
        // Simple linearly-separable data: y = 1 if x[0] + x[1] > 1, else 0
        List<RandomForest.Sample> data = new ArrayList<>();
        java.util.Random rng = new java.util.Random(0xCAFE);
        for (int i = 0; i < 200; i++) {
            double x0 = rng.nextDouble() * 2;
            double x1 = rng.nextDouble() * 2;
            int label = (x0 + x1 > 1) ? 1 : 0;
            data.add(new RandomForest.Sample(new double[]{x0, x1}, label));
        }
        List<RandomForest.TreeNode> forest = RandomForest.train(
                data, 10, 5, 0xBEEFL);
        assertThat(forest).hasSize(10);
        // Predict on a known training point
        var pred = RandomForest.predict(forest, new double[]{1.5, 1.0});
        // Should be 1 (since 1.5 + 1.0 > 1)
        assertThat(pred.predicted()).isEqualTo(1);
    }

    @Test
    void randomForestPureLeaf() {
        // All same label — should converge to leaf
        List<RandomForest.Sample> data = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            data.add(new RandomForest.Sample(new double[]{i, i * 2}, 7));
        }
        List<RandomForest.TreeNode> forest = RandomForest.train(data, 1, 3, 0xAL);
        var pred = RandomForest.predict(forest, new double[]{100, 200});
        assertThat(pred.predicted()).isEqualTo(7);
        assertThat(pred.confidence()).isEqualTo(1.0);
    }

    @Test
    void randomForestSingleTreeIsDeterministic() {
        List<RandomForest.Sample> data = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            data.add(new RandomForest.Sample(new double[]{i % 3, i % 5}, i % 2));
        }
        var a = RandomForest.train(data, 5, 4, 0xCAFE);
        var b = RandomForest.train(data, 5, 4, 0xCAFE);
        // Same input → same forest (CONSTITUTION I)
        for (int i = 0; i < a.size(); i++) {
            assertThat(a.get(i).leafLabel).isEqualTo(b.get(i).leafLabel);
        }
    }
}
