package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveAttentionTest {

    @Test
    void emptyReturnsEmpty() {
        CognitiveAttention.AttentionResult r = CognitiveAttention.selfAttention(null, 16, 1L);
        assertThat(r.outputVectors()).isEmpty();
        assertThat(r.attentionWeights()).isEmpty();
    }

    @Test
    void singleProfileReturnsSelf() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        CognitiveAttention.AttentionResult r = CognitiveAttention.selfAttention(profiles, 16, 1L);
        assertThat(r.attentionWeights().length).isEqualTo(1);
        assertThat(r.attentionWeights()[0][0]).isCloseTo(1.0, offset(1e-9));
        assertThat(r.sinkScores()[0]).isEqualTo(1.0);
    }

    @Test
    void attentionWeightsSumToOne() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        CognitiveAttention.AttentionResult r = CognitiveAttention.selfAttention(profiles, 16, 42L);
        double[][] w = r.attentionWeights();
        for (int i = 0; i < w.length; i++) {
            double sum = 0;
            for (int j = 0; j < w[i].length; j++) sum += w[i][j];
            assertThat(sum).isCloseTo(1.0, offset(1e-9));
        }
    }

    @Test
    void findSinksReturnsIndices() {
        double[][] w = {
            {0.5, 0.5, 0.0},
            {0.1, 0.8, 0.1},
            {0.2, 0.6, 0.2}
        };
        int[] sinks = CognitiveAttention.findSinks(w, 1);
        assertThat(sinks.length).isEqualTo(1);
        // Sink = index 1 (highest total attention)
        assertThat(sinks[0]).isEqualTo(1);
    }

    @Test
    void classifyUniformAttention() {
        double[][] uniform = new double[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) uniform[i][j] = 0.25;
        }
        assertThat(CognitiveAttention.classifyRegime(uniform)).isEqualTo("UNIFORM");
    }

    @Test
    void classifyConcentratedAttention() {
        double[][] conc = new double[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                conc[i][j] = (j == 0) ? 0.97 : 0.01;
            }
        }
        assertThat(CognitiveAttention.classifyRegime(conc)).isEqualTo("CONCENTRATED");
    }

    @Test
    void outputVectorsHaveCorrectDim() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 3; i++) profiles.add(makeProfile(i / 3.0));
        CognitiveAttention.AttentionResult r = CognitiveAttention.selfAttention(profiles, 32, 42L);
        for (double[] v : r.outputVectors()) {
            assertThat(v.length).isEqualTo(32);
        }
    }

    @Test
    void sameSeedDeterministic() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 3; i++) profiles.add(makeProfile(i / 3.0));
        CognitiveAttention.AttentionResult r1 = CognitiveAttention.selfAttention(profiles, 16, 42L);
        CognitiveAttention.AttentionResult r2 = CognitiveAttention.selfAttention(profiles, 16, 42L);
        for (int i = 0; i < r1.attentionWeights().length; i++) {
            for (int j = 0; j < r1.attentionWeights()[i].length; j++) {
                assertThat(r1.attentionWeights()[i][j])
                    .isCloseTo(r2.attentionWeights()[i][j], offset(1e-9));
            }
        }
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
