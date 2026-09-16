package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W150 — DistributionPhi property-based tests.
 */
class DistributionPhiPropertyTest {

    @Property(tries = 50)
    void propertyEmptyReturnsZero(@ForAll("binCounts") int bins) {
        if (bins < 2) return;
        assertThat(DistributionPhi.phiFromTimeSeries(new double[0], 1, bins)).isEqualTo(0.0);
    }

    @Property(tries = 50)
    void propertyConstantReturnsZero(@ForAll("values") double v,
                                     @ForAll("counts") int count,
                                     @ForAll("bins") int bins) {
        if (count < 1 || bins < 2) return;
        double[] vals = new double[count];
        for (int i = 0; i < count; i++) vals[i] = v;
        assertThat(DistributionPhi.phiFromTimeSeries(vals, 1, bins)).isEqualTo(0.0);
    }

    @Property(tries = 30)
    void propertyRandomNonNegative(@ForAll("anySeed") int seed,
                                    @ForAll("lengths") int length,
                                    @ForAll("bins") int bins) {
        if (length < 4 || bins < 2 || bins > 32) return;
        Random rng = new Random(seed);
        double[] vals = new double[length];
        for (int i = 0; i < length; i++) vals[i] = rng.nextGaussian();
        double phi = DistributionPhi.phiFromTimeSeries(vals, 1, bins);
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
    }

    @Property(tries = 30)
    void property2DRandomNonNegative(@ForAll("anySeed") int seed,
                                      @ForAll("lengths") int length,
                                      @ForAll("bins") int bins) {
        if (length < 4 || bins < 2 || bins > 16) return;
        Random rng = new Random(seed);
        double[] x = new double[length];
        double[] y = new double[length];
        for (int i = 0; i < length; i++) {
            x[i] = rng.nextGaussian();
            y[i] = rng.nextGaussian();
        }
        double phi = DistributionPhi.phiFrom2DTimeSeries(x, y, 1, bins);
        assertThat(phi).isGreaterThanOrEqualTo(0.0);
    }

    @Property(tries = 20)
    void propertyCorrelatedAtLeastIndependent(@ForAll("anySeed") int seed,
                                                @ForAll("lengths") int length) {
        if (length < 4) return;
        Random rng = new Random(seed);
        // Correlated
        double[] x1 = new double[length];
        double[] y1 = new double[length];
        for (int i = 0; i < length; i++) {
            x1[i] = rng.nextGaussian();
            y1[i] = x1[i] + rng.nextGaussian() * 0.1;  // highly correlated
        }
        double phiCorr = DistributionPhi.phiFrom2DTimeSeries(x1, y1, 1, 16);
        // Independent
        double[] x2 = new double[length];
        double[] y2 = new double[length];
        for (int i = 0; i < length; i++) {
            x2[i] = rng.nextGaussian();
            y2[i] = rng.nextGaussian();
        }
        double phiInd = DistributionPhi.phiFrom2DTimeSeries(x2, y2, 1, 16);
        // Correlated MI should be ≥ independent MI (with tolerance for noise)
        assertThat(phiCorr).isGreaterThanOrEqualTo(phiInd - 0.5);
    }

    @Provide
    Arbitrary<Double> values() {
        return Arbitraries.doubles().between(-100.0, 100.0);
    }

    @Provide
    Arbitrary<Integer> counts() {
        return Arbitraries.integers().between(1, 32);
    }

    @Provide
    Arbitrary<Integer> binCounts() {
        return Arbitraries.integers().between(2, 32);
    }

    @Provide
    Arbitrary<Integer> bins() {
        return Arbitraries.integers().between(2, 32);
    }

    @Provide
    Arbitrary<Integer> lengths() {
        return Arbitraries.integers().between(4, 32);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }
}
