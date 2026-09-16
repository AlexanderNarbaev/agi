package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveEmbeddingTest {

    @Test
    void embedReturnsCorrectDimension() {
        CognitiveEmbedding emb = new CognitiveEmbedding(64, 42L);
        CognitiveGenesisProfile p = makeProfile(0.5);
        double[] v = emb.embed(p);
        assertThat(v.length).isEqualTo(64);
    }

    @Test
    void customDimension() {
        CognitiveEmbedding emb = new CognitiveEmbedding(128, 1L);
        assertThat(emb.dimension()).isEqualTo(128);
    }

    @Test
    void invalidDimensionThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveEmbedding(0, 0L)
        );
    }

    @Test
    void nullProfileReturnsZeros() {
        CognitiveEmbedding emb = new CognitiveEmbedding(64, 42L);
        double[] v = emb.embed(null);
        assertThat(v.length).isEqualTo(64);
        for (double x : v) assertThat(x).isEqualTo(0.0);
    }

    @Test
    void cosineSimilarityIsSymmetric() {
        double[] a = {1.0, 0.0, 0.0};
        double[] b = {0.0, 1.0, 0.0};
        double ab = CognitiveEmbedding.cosineSimilarity(a, b);
        double ba = CognitiveEmbedding.cosineSimilarity(b, a);
        assertThat(ab).isCloseTo(ba, offset(1e-9));
        assertThat(ab).isCloseTo(0.0, offset(1e-9));
    }

    @Test
    void cosineSimilarityIdenticalVectors() {
        double[] a = {0.5, 0.5, 0.5};
        double c = CognitiveEmbedding.cosineSimilarity(a, a);
        assertThat(c).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void cosineSimilarityNullReturnsZero() {
        assertThat(CognitiveEmbedding.cosineSimilarity(null, null)).isEqualTo(0.0);
    }

    @Test
    void l2DistanceIsSymmetric() {
        double[] a = {1.0, 2.0, 3.0};
        double[] b = {4.0, 5.0, 6.0};
        double ab = CognitiveEmbedding.l2Distance(a, b);
        double ba = CognitiveEmbedding.l2Distance(b, a);
        assertThat(ab).isCloseTo(ba, offset(1e-9));
    }

    @Test
    void l2DistanceIdenticalIsZero() {
        double[] a = {1.0, 2.0, 3.0};
        assertThat(CognitiveEmbedding.l2Distance(a, a)).isCloseTo(0.0, offset(1e-9));
    }

    @Test
    void nearestNeighborsFindsSelf() {
        CognitiveEmbedding emb = new CognitiveEmbedding(64, 42L);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        CognitiveGenesisProfile target = makeProfile(0.7);
        profiles.add(makeProfile(0.1));
        profiles.add(target);
        profiles.add(makeProfile(0.5));
        profiles.add(makeProfile(0.3));
        int[] nn = emb.nearestNeighbors(target, profiles, 1);
        assertThat(nn.length).isEqualTo(1);
        assertThat(nn[0]).isEqualTo(1); // target is at index 1
    }

    @Test
    void nearestNeighborsRespectsK() {
        CognitiveEmbedding emb = new CognitiveEmbedding(64, 42L);
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) profiles.add(makeProfile(i / 10.0));
        int[] nn3 = emb.nearestNeighbors(makeProfile(0.0), profiles, 3);
        assertThat(nn3.length).isEqualTo(3);
    }

    @Test
    void seedRecorded() {
        CognitiveEmbedding emb = new CognitiveEmbedding(64, 12345L);
        assertThat(emb.seed()).isEqualTo(12345L);
    }

    @Test
    void sameSeedSameProjection() {
        CognitiveEmbedding emb1 = new CognitiveEmbedding(64, 42L);
        CognitiveEmbedding emb2 = new CognitiveEmbedding(64, 42L);
        CognitiveGenesisProfile p = makeProfile(0.5);
        double[] v1 = emb1.embed(p);
        double[] v2 = emb2.embed(p);
        for (int i = 0; i < v1.length; i++) {
            assertThat(v1[i]).isCloseTo(v2[i], offset(1e-9));
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
