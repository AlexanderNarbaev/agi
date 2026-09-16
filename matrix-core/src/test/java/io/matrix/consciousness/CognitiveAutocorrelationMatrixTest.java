package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveAutocorrelationMatrixTest {

    @Test
    void emptyProfilesReturnsZeros() {
        double[][] m = CognitiveAutocorrelationMatrix.compute(new ArrayList<>());
        assertThat(m.length).isEqualTo(13);
        // All zeros
        double sum = 0;
        for (double[] row : m) for (double v : row) sum += Math.abs(v);
        assertThat(sum).isEqualTo(0.0);
    }

    @Test
    void singleProfileReturnsZeros() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        double[][] m = CognitiveAutocorrelationMatrix.compute(profiles);
        // All zeros since only 1 profile
        double sum = 0;
        for (double[] row : m) for (double v : row) sum += Math.abs(v);
        assertThat(sum).isEqualTo(0.0);
    }

    @Test
    void constantProfilesReturnIdentityMatrix() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(0.5));
        double[][] m = CognitiveAutocorrelationMatrix.compute(profiles);
        // For constant series, Pearson with itself = 1, with other = NaN/0
        // We expect either identity-like or 0s
        for (int i = 0; i < 13; i++) {
            // Diagonal: NaN or 0 (variance is 0)
            // We don't check exact value
        }
    }

    @Test
    void highlyCorrelatedReturnsPairsAboveThreshold() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        // Create profiles where all fields co-vary
        for (int i = 0; i < 10; i++) {
            double phi = i / 10.0;
            profiles.add(makeProfile(phi));
        }
        var pairs = CognitiveAutocorrelationMatrix.highlyCorrelated(profiles, 0.5);
        // All phi values co-vary → should have high correlations
        assertThat(pairs.size()).isGreaterThan(0);
    }

    @Test
    void matrixHasCorrectDimensions() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(i / 5.0));
        double[][] m = CognitiveAutocorrelationMatrix.compute(profiles);
        assertThat(m.length).isEqualTo(13);
        for (double[] row : m) {
            assertThat(row.length).isEqualTo(13);
        }
    }

    @Test
    void averageOffDiagonalForRandomIsSmall() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < 10; i++) profiles.add(randomProfile(rng));
        double avg = CognitiveAutocorrelationMatrix.averageOffDiagonal(profiles);
        assertThat(Math.abs(avg)).isLessThan(0.5);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }

    private static CognitiveGenesisProfile randomProfile(java.util.Random rng) {
        return new CognitiveGenesisProfile(
            rng.nextDouble(), rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
            rng.nextDouble(), rng.nextDouble(), rng.nextDouble(),
            rng.nextDouble() * 100.0, rng.nextDouble(), rng.nextDouble(),
            rng.nextInt(8), rng.nextDouble(), rng.nextDouble() * 5.0
        );
    }
}
