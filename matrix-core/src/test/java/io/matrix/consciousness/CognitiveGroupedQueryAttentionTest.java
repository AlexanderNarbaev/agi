package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveGroupedQueryAttentionTest {

    @Test
    void constructValid() {
        CognitiveGroupedQueryAttention gqa =
            new CognitiveGroupedQueryAttention(64, 8, 2, 42L);
        assertThat(gqa.dim()).isEqualTo(64);
        assertThat(gqa.numQueryHeads()).isEqualTo(8);
        assertThat(gqa.numKVHeads()).isEqualTo(2);
    }

    @Test
    void invalidDivisibilityThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveGroupedQueryAttention(64, 8, 3, 1L)
        );
    }

    @Test
    void invalidDimsThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveGroupedQueryAttention(0, 8, 2, 1L)
        );
    }

    @Test
    void attendPreservesDimensions() {
        CognitiveGroupedQueryAttention gqa =
            new CognitiveGroupedQueryAttention(64, 8, 2, 42L);
        double[] v = new double[64];
        for (int i = 0; i < 64; i++) v[i] = i / 64.0;
        double[] result = gqa.attend(v);
        assertThat(result.length).isEqualTo(64);
    }

    @Test
    void nullAttendReturnsNull() {
        CognitiveGroupedQueryAttention gqa =
            new CognitiveGroupedQueryAttention(64, 8, 2, 42L);
        assertThat(gqa.attend(null)).isNull();
    }

    @Test
    void wrongDimReturnsInput() {
        CognitiveGroupedQueryAttention gqa =
            new CognitiveGroupedQueryAttention(64, 8, 2, 42L);
        double[] v = new double[32];
        assertThat(gqa.attend(v)).isSameAs(v);
    }

    @Test
    void compressionRatioCorrect() {
        CognitiveGroupedQueryAttention gqa =
            new CognitiveGroupedQueryAttention(64, 8, 2, 42L);
        assertThat(gqa.compressionRatio()).isEqualTo(4.0);
    }

    @Test
    void attendSequenceReturnsCorrectCount() {
        CognitiveGroupedQueryAttention gqa =
            new CognitiveGroupedQueryAttention(64, 8, 2, 42L);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        double[][] result = gqa.attendSequence(profiles);
        assertThat(result.length).isEqualTo(5);
        for (double[] r : result) assertThat(r.length).isEqualTo(64);
    }

    @Test
    void sameSeedDeterministic() {
        CognitiveGroupedQueryAttention g1 =
            new CognitiveGroupedQueryAttention(64, 8, 2, 42L);
        CognitiveGroupedQueryAttention g2 =
            new CognitiveGroupedQueryAttention(64, 8, 2, 42L);
        double[] v = new double[64];
        for (int i = 0; i < 64; i++) v[i] = i / 64.0;
        double[] r1 = g1.attend(v);
        double[] r2 = g2.attend(v);
        for (int i = 0; i < 64; i++) {
            assertThat(r1[i]).isCloseTo(r2[i], offset(1e-9));
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
