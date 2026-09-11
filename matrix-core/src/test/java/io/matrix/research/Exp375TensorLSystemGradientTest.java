package io.matrix.research;

import io.matrix.neuron.GradientFlow;
import io.matrix.neuron.LSystem;
import io.matrix.neuron.TensorTrain;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 375 — DESIGN-36/37/38 implementations (Tensor Train, L-System, Gradient Flow).
 */
class Exp375TensorLSystemGradientTest {

    @Test
    void tensorTrainDecompose1D() {
        boolean[] table = new boolean[8];
        for (int i = 0; i < 8; i++) table[i] = (i % 2 == 0);
        List<double[][]> cores = TensorTrain.decompose1D(table, 2);
        assertThat(cores).hasSize(3);  // 8 = 2^3 → 3 cores
        boolean[] reconstructed = TensorTrain.reconstruct1D(cores);
        assertThat(reconstructed).hasSize(8);
    }

    @Test
    void tensorTrainPowerOfTwoValidation() {
        boolean[] notPower = new boolean[7];  // not 2^k
        try {
            TensorTrain.decompose1D(notPower, 2);
            assertThat(false).as("should have thrown").isTrue();
        } catch (IllegalArgumentException expected) {
            // OK
        }
    }

    @Test
    void lSystemDeterministic() {
        // Algae growth: A → AB, B → A
        Map<Character, String> rules = new HashMap<>();
        rules.put('A', "AB");
        rules.put('B', "A");
        // Start with A, iterate 4 times: A → AB → ABA → ABAAB → ABAABABA
        String result = LSystem.generate("A", rules, 4);
        assertThat(result).isEqualTo("ABAABABA");
    }

    @Test
    void lSystemStochasticSeeded() {
        // F → F[+F]F[-F]F or F[+F]F (random choice)
        Map<Character, String[]> rules = new HashMap<>();
        rules.put('F', new String[]{"F[+F]F", "F[-F]F"});
        // Same seed → same result
        String s1 = LSystem.generateStochastic("F", rules, 2, 0xCAFE);
        String s2 = LSystem.generateStochastic("F", rules, 2, 0xCAFE);
        assertThat(s1).isEqualTo(s2);
    }

    @Test
    void lSystemAxiomPreservedForZeroIter() {
        Map<Character, String> rules = new HashMap<>();
        rules.put('A', "B");
        assertThat(LSystem.generate("A", rules, 0)).isEqualTo("A");
    }

    @Test
    void gradientFlowNaturalGradient() {
        double[] params = {0.5, 0.3, 0.2};
        double[] lossGrad = {0.1, 0.2, 0.3};
        double[] fisher = {1.0, 0.5, 0.25};
        double[] natGrad = GradientFlow.naturalGradient(params, lossGrad, fisher);
        assertThat(natGrad).hasSize(3);
        assertThat(natGrad[0]).isCloseTo(-0.1, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(natGrad[1]).isCloseTo(-0.4, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(natGrad[2]).isCloseTo(-1.2, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void gradientFlowAvoidsDivByZero() {
        double[] lossGrad = {0.1};
        double[] fisher = {0.0};  // would be div by zero
        double[] natGrad = GradientFlow.naturalGradient(new double[]{0.5},
                lossGrad, fisher);
        assertThat(natGrad[0]).isFinite();
    }

    @Test
    void riemannianGradientScaledByProb() {
        double[] probs = {0.5, 0.5};
        double[] euclid = {0.1, 0.2};
        double[] riem = GradientFlow.riemannianGradient(probs, euclid);
        assertThat(riem[0]).isCloseTo(0.2, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(riem[1]).isCloseTo(0.4, org.assertj.core.data.Offset.offset(1e-9));
    }
}
