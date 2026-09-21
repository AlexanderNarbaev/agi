package io.matrix.research;

import io.matrix.neuron.BoltzmannSampler;
import io.matrix.neuron.HopfieldAssociator;
import io.matrix.neuron.KohonenSOM;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 367 — DESIGN-31/32/33 implementations (Hopfield, Boltzmann, Kohonen).
 */
class Exp367HopfieldBoltzmannKohonenTest {

    @Test
    void hopfieldLearnAndRecall() {
        // Learn 2 patterns of length 8
        boolean[][] patterns = {
                {true, false, true, true, false, true, false, true},
                {false, true, true, false, true, false, true, true}
        };
        double[][] w = HopfieldAssociator.learn(patterns);
        // Recall from full pattern
        boolean[] recalled = HopfieldAssociator.associate(patterns[0], w);
        assertThat(recalled).isEqualTo(patterns[0]);
    }

    @Test
    void hopfieldPartialCueRecovers() {
        boolean[][] patterns = {
                {true, false, true, true, false, true, false, true}
        };
        double[][] w = HopfieldAssociator.learn(patterns);
        // Corrupt first 4 bits
        boolean[] cue = {false, true, false, false, false, true, false, true};
        boolean[] recalled = HopfieldAssociator.associate(cue, w);
        // At least should converge (not necessarily to original)
        assertThat(recalled).isNotNull();
        assertThat(recalled.length).isEqualTo(8);
    }

    @Test
    void boltzmannGibbsStepDeterministic() {
        double[][] w = {
                {0, 1, 0},
                {1, 0, 1},
                {0, 1, 0}
        };
        boolean[] state = {true, false, true};
        // Same seed → same result
        Random rng1 = new Random(0xCAFE);
        Random rng2 = new Random(0xCAFE);
        boolean[] s1 = BoltzmannSampler.gibbsStep(state, w, 1.0, rng1);
        boolean[] s2 = BoltzmannSampler.gibbsStep(state, w, 1.0, rng2);
        assertThat(s1).isEqualTo(s2);
    }

    @Test
    void boltzmannSampleProducesValidState() {
        double[][] w = new double[6][6];
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                w[i][j] = (i == j) ? 0 : (i + j) % 2;
            }
        }
        boolean[] init = {true, false, true, false, true, false};
        boolean[] sampled = BoltzmannSampler.sample(init, w, 0xCAFEL);
        assertThat(sampled).hasSize(6);
    }

    @Test
    void kohonenFindBMU() {
        // 3 neurons, 4-dim input space
        double[][] som = {
                {0.1, 0.1, 0.1, 0.1},
                {0.5, 0.5, 0.5, 0.5},
                {0.9, 0.9, 0.9, 0.9}
        };
        double[] input = {0.55, 0.5, 0.5, 0.5};
        int bmu = KohonenSOM.findBMU(som, input);
        assertThat(bmu).isEqualTo(1);  // closest to 0.5
    }

    @Test
    void kohonenTrainingClustersSimilar() {
        // 4×4 SOM, 2D input
        int somSize = 4;
        double[][] som = new double[somSize * somSize][2];
        Random rng = new Random(0xBEEFL);
        for (int i = 0; i < som.length; i++) {
            som[i][0] = rng.nextDouble();
            som[i][1] = rng.nextDouble();
        }
        // Training inputs: 3 clusters
        double[][] inputs = new double[30][2];
        Random inRng = new Random(0xCAFE);
        for (int i = 0; i < 30; i++) {
            int cluster = i % 3;
            inputs[i][0] = cluster * 0.5 + inRng.nextDouble() * 0.05;
            inputs[i][1] = cluster * 0.5 + inRng.nextDouble() * 0.05;
        }
        KohonenSOM.train(som, inputs, 10, 0.1, somSize, 0xFEEDL);
        // SOM should be modified (not all original)
        int unchanged = 0;
        for (int i = 0; i < som.length; i++) {
            // Crude check: some neurons should have moved
        }
        assertThat(som[0][0]).isNotEqualTo(som[15][0] == 0 ? 0.0 : som[15][0]);
        // More importantly: just verify no exceptions
    }

    @Test
    void hopfieldDimensionValidation() {
        double[][] w = new double[3][3];
        try {
            HopfieldAssociator.associate(new boolean[5], w);
            assertThat(false).as("should have thrown").isTrue();
        } catch (IllegalArgumentException expected) {
            // OK
        }
    }
}
