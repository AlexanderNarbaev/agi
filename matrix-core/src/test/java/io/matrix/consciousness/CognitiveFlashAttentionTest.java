package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveFlashAttentionTest {

    @Test
    void emptyReturnsEmpty() {
        CognitiveFlashAttention.FlashResult r =
            CognitiveFlashAttention.flashAttention(null, 16, 4, 1L);
        assertThat(r.outputVectors()).isEmpty();
    }

    @Test
    void singleProfileReturnsSelf() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        CognitiveFlashAttention.FlashResult r =
            CognitiveFlashAttention.flashAttention(profiles, 16, 4, 1L);
        assertThat(r.outputVectors().length).isEqualTo(1);
        assertThat(r.attentionWeights()[0][0]).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void attentionWeightsSumToOne() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        CognitiveFlashAttention.FlashResult r =
            CognitiveFlashAttention.flashAttention(profiles, 16, 2, 42L);
        double[][] w = r.attentionWeights();
        for (int i = 0; i < w.length; i++) {
            double sum = 0;
            for (int j = 0; j < w[i].length; j++) sum += w[i][j];
            assertThat(sum).isCloseTo(1.0, offset(1e-9));
        }
    }

    @Test
    void operationsSavedNonNegative() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 8; i++) profiles.add(makeProfile(i / 8.0));
        CognitiveFlashAttention.FlashResult r =
            CognitiveFlashAttention.flashAttention(profiles, 16, 4, 42L);
        assertThat(r.operationsSaved()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void recommendTileSizeForSmall() {
        assertThat(CognitiveFlashAttention.recommendTileSize(4)).isEqualTo(2);
    }

    @Test
    void recommendTileSizeForLarge() {
        assertThat(CognitiveFlashAttention.recommendTileSize(200)).isEqualTo(16);
    }

    @Test
    void outputDimensionsCorrect() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 4; i++) profiles.add(makeProfile(i / 4.0));
        CognitiveFlashAttention.FlashResult r =
            CognitiveFlashAttention.flashAttention(profiles, 32, 2, 42L);
        for (double[] v : r.outputVectors()) {
            assertThat(v.length).isEqualTo(32);
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
