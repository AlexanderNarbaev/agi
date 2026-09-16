package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveLinearAttentionTest {

    @Test
    void constructValid() {
        CognitiveLinearAttention la =
            new CognitiveLinearAttention(32, 16, 42L);
        assertThat(la.dim()).isEqualTo(32);
        assertThat(la.numFeatures()).isEqualTo(16);
        assertThat(la.seed()).isEqualTo(42L);
    }

    @Test
    void invalidDimsThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveLinearAttention(0, 16, 1L)
        );
    }

    @Test
    void featureMapReturnsCorrectSize() {
        CognitiveLinearAttention la =
            new CognitiveLinearAttention(32, 16, 42L);
        double[] x = new double[32];
        for (int i = 0; i < 32; i++) x[i] = i / 32.0;
        double[] phi = la.featureMap(x);
        assertThat(phi.length).isEqualTo(2 * 16);
    }

    @Test
    void nullFeatureMapReturnsNull() {
        CognitiveLinearAttention la =
            new CognitiveLinearAttention(32, 16, 42L);
        assertThat(la.featureMap(null)).isNull();
    }

    @Test
    void wrongDimFeatureMapReturnsNull() {
        CognitiveLinearAttention la =
            new CognitiveLinearAttention(32, 16, 42L);
        double[] x = new double[16];
        assertThat(la.featureMap(x)).isNull();
    }

    @Test
    void computeKVsReturnsCorrectSize() {
        CognitiveLinearAttention la =
            new CognitiveLinearAttention(32, 16, 42L);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 3; i++) profiles.add(makeProfile(i / 3.0));
        double[] kvs = la.computeKVs(profiles);
        assertThat(kvs.length).isEqualTo(32 * 2 * 16);
    }

    @Test
    void emptySequenceReturnsNull() {
        CognitiveLinearAttention la =
            new CognitiveLinearAttention(32, 16, 42L);
        assertThat(la.computeKVs(new ArrayList<>())).isNull();
    }

    @Test
    void attendReturnsCorrectDim() {
        CognitiveLinearAttention la =
            new CognitiveLinearAttention(32, 16, 42L);
        double[] query = new double[32];
        for (int i = 0; i < 32; i++) query[i] = i / 32.0;
        double[] kvs = new double[32 * 2 * 16];
        double[] result = la.attend(query, kvs);
        assertThat(result.length).isEqualTo(32);
    }

    @Test
    void nullAttendReturnsNull() {
        CognitiveLinearAttention la =
            new CognitiveLinearAttention(32, 16, 42L);
        double[] kvs = new double[32 * 2 * 16];
        assertThat(la.attend(null, kvs)).isNull();
    }

    @Test
    void sameSeedDeterministic() {
        CognitiveLinearAttention l1 =
            new CognitiveLinearAttention(32, 16, 42L);
        CognitiveLinearAttention l2 =
            new CognitiveLinearAttention(32, 16, 42L);
        double[] x = new double[32];
        for (int i = 0; i < 32; i++) x[i] = i / 32.0;
        double[] p1 = l1.featureMap(x);
        double[] p2 = l2.featureMap(x);
        for (int i = 0; i < p1.length; i++) {
            assertThat(p1[i]).isCloseTo(p2[i], offset(1e-9));
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
