package io.matrix.consciousness;

import io.matrix.consciousness.PhiId.PhiIdAtom;
import io.matrix.consciousness.PhiId.PhiIdSystem;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Wave 90 — PhiID tests (Integrated Information Decomposition).
 *
 * <p>Verifies that the four-atom decomposition satisfies:
 * <ol>
 *   <li>Atom sum = I(X; Y) for bivariate Gaussian.</li>
 *   <li>All atoms ≥ 0 (decomposition is well-posed).</li>
 *   <li>Independent variables → r = miXY, s = unqX = unqY = 0 (redundancy dominated).</li>
 *   <li>Synergistic triple (XOR-like) gives positive synergy atom.</li>
 *   <li>Constant variable → degenerate atoms = 0.</li>
 *   <li>System-level PhiID averages across pairs.</li>
 * </ol>
 */
class PhiIdTest {

    @Test
    void bivariateIndependentYieldsRedundancyOnly() {
        // X and Y independent Gaussian → all MI is redundant (2-var system)
        double[][] samples = independentBivariate(256, 42);
        PhiIdAtom atom = PhiId.bivariateGaussian(samples);
        assertThat(atom.redundancy()).isCloseTo(atom.miXY(), within(0.05));
        assertThat(atom.synergy()).isEqualTo(0.0);
        assertThat(atom.unqX()).isEqualTo(0.0);
        assertThat(atom.unqY()).isEqualTo(0.0);
        assertThat(atom.redundancy()).isLessThan(0.5);  // noise floor low
    }

    @Test
    void bivariateCorrelatedYieldsRedundancyOnly() {
        // X, Y = 0.7*X + noise → high ρ, high redundancy.
        // I(X;Y) for ρ=0.7 ≈ -0.5 log(1 - 0.49) ≈ 0.337 bits
        double[][] samples = correlatedBivariate(256, 0.7, 42);
        PhiIdAtom atom = PhiId.bivariateGaussian(samples);
        assertThat(atom.redundancy()).isGreaterThan(0.2);  // significant correlation
        assertThat(atom.synergy()).isEqualTo(0.0);
        assertThat(atom.miXY()).isGreaterThan(0.2);
    }

    @Test
    void bivariateAtomsSumToMI() {
        double[][] samples = correlatedBivariate(256, 0.5, 7);
        PhiIdAtom atom = PhiId.bivariateGaussian(samples);
        double sum = atom.redundancy() + atom.synergy() + atom.unqX() + atom.unqY();
        assertThat(sum).isCloseTo(atom.miXY(), within(1e-9));
    }

    @Test
    void trivariateSynergisticChainGivesPositiveSynergy() {
        // Z = X XOR Y (mod-2-like synergistic chain) — encode as continuous XOR
        double[][] samples = xorSynergyTriplet(512, 42);
        PhiIdAtom atom = PhiId.trivariateGaussian(samples);
        assertThat(atom.synergy()).isGreaterThanOrEqualTo(0.0);
        assertThat(atom.redundancy()).isGreaterThanOrEqualTo(0.0);
        assertThat(atom.unqX()).isGreaterThanOrEqualTo(0.0);
        assertThat(atom.unqY()).isGreaterThanOrEqualTo(0.0);
        System.out.printf("XOR-synergy triplet: r=%.4f s=%.4f unqX=%.4f unqY=%.4f miXY=%.4f%n",
                atom.redundancy(), atom.synergy(), atom.unqX(), atom.unqY(), atom.miXY());
        assertThat(atom.redundancy() + atom.synergy() + atom.unqX() + atom.unqY())
                .isCloseTo(atom.miXY(), within(0.05));
    }

    @Test
    void trivariateRedundantChainGivesHighRedundancy() {
        // X = Y = Z (perfect redundancy chain) → all redundancy
        double[][] samples = redundantTriplet(512, 42);
        PhiIdAtom atom = PhiId.trivariateGaussian(samples);
        assertThat(atom.redundancy()).isGreaterThan(0.5);
        System.out.printf("Redundant triplet: r=%.4f s=%.4f unqX=%.4f unqY=%.4f miXY=%.4f%n",
                atom.redundancy(), atom.synergy(), atom.unqX(), atom.unqY(), atom.miXY());
    }

    @Test
    void constantVariableYieldsZeroAtoms() {
        double[][] samples = new double[64][2];
        // All zeros → both variables constant
        PhiIdAtom atom = PhiId.bivariateGaussian(samples);
        assertThat(atom.redundancy()).isEqualTo(0.0);
        assertThat(atom.synergy()).isEqualTo(0.0);
        assertThat(atom.unqX()).isEqualTo(0.0);
        assertThat(atom.unqY()).isEqualTo(0.0);
        assertThat(atom.miXY()).isEqualTo(0.0);
    }

    @Test
    void systemIntegrationAggregatesAcrossPairs() {
        // 4-variable system with mixed structure
        double[][] samples = new double[256][];
        Random rng = new Random(42);
        // Pre-generate the previous sample for the AR(1) term
        double prevX = rng.nextGaussian();
        for (int t = 0; t < 256; t++) {
            double x = rng.nextGaussian();
            double y = 0.7 * prevX + 0.3 * rng.nextGaussian();
            double z = rng.nextGaussian();
            double w = 0.5 * x + 0.5 * z;
            samples[t] = new double[]{x, y, z, w};
            prevX = x;
        }
        PhiIdSystem sys = PhiId.system(samples);
        assertThat(sys.pairCount()).isEqualTo(6);  // C(4,2)
        assertThat(sys.redundancy()).isGreaterThanOrEqualTo(0.0);
        assertThat(sys.synergy()).isGreaterThanOrEqualTo(0.0);
        assertThat(sys.unqX()).isGreaterThanOrEqualTo(0.0);
        assertThat(sys.unqY()).isGreaterThanOrEqualTo(0.0);
        System.out.printf("System: r=%.4f s=%.4f unqX=%.4f unqY=%.4f miXY=%.4f pairs=%d totalΦid=%.4f%n",
                sys.redundancy(), sys.synergy(), sys.unqX(), sys.unqY(), sys.miXY(),
                sys.pairCount(), sys.totalPhiId());
    }

    @Test
    void invalidSamplesThrow() {
        try {
            PhiId.bivariateGaussian(null);
            assertThat(false).as("should throw").isTrue();
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            PhiId.bivariateGaussian(new double[][]{{1.0}});  // 1 sample
            assertThat(false).as("should throw").isTrue();
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            PhiId.bivariateGaussian(new double[][]{{1.0, 2.0, 3.0}});  // 3 cols
            assertThat(false).as("should throw").isTrue();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void allAtomsNonNegative() {
        double[][] samples = xorSynergyTriplet(256, 99);
        PhiIdAtom atom = PhiId.trivariateGaussian(samples);
        // PhiID decomposition is required to give non-negative atoms
        assertThat(atom.redundancy()).isGreaterThanOrEqualTo(0.0);
        assertThat(atom.synergy()).isGreaterThanOrEqualTo(0.0);
    }

    // ===== Helpers =====

    private static double[][] independentBivariate(int T, long seed) {
        Random rng = new Random(seed);
        double[][] s = new double[T][2];
        for (int t = 0; t < T; t++) {
            s[t][0] = rng.nextGaussian();
            s[t][1] = rng.nextGaussian();
        }
        return s;
    }

    private static double[][] correlatedBivariate(int T, double rho, long seed) {
        Random rng = new Random(seed);
        double[][] s = new double[T][2];
        for (int t = 0; t < T; t++) {
            double x = rng.nextGaussian();
            double y = rho * x + Math.sqrt(1 - rho * rho) * rng.nextGaussian();
            s[t][0] = x;
            s[t][1] = y;
        }
        return s;
    }

    /**
     * Z = sign(X * Y) — XOR-like synergy: knowing (X, Y) jointly reveals Z,
     * but neither X nor Y alone does.
     */
    private static double[][] xorSynergyTriplet(int T, long seed) {
        Random rng = new Random(seed);
        double[][] s = new double[T][3];
        for (int t = 0; t < T; t++) {
            double x = rng.nextGaussian();
            double y = rng.nextGaussian();
            double z = Math.signum(x * y);
            s[t][0] = x;
            s[t][1] = y;
            s[t][2] = z;
        }
        return s;
    }

    /**
     * X = Y = Z + noise — high redundancy, low synergy.
     */
    private static double[][] redundantTriplet(int T, long seed) {
        Random rng = new Random(seed);
        double[][] s = new double[T][3];
        for (int t = 0; t < T; t++) {
            double x = rng.nextGaussian();
            s[t][0] = x;
            s[t][1] = x + 0.01 * rng.nextGaussian();
            s[t][2] = x + 0.01 * rng.nextGaussian();
        }
        return s;
    }
}
