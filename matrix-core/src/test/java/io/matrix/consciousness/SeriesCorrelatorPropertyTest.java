package io.matrix.consciousness;

import net.jqwik.api.*;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W152 — SeriesCorrelator property-based tests.
 */
class SeriesCorrelatorPropertyTest {

    @Property(tries = 50)
    void propertyPearsonReflexive(@ForAll("series") double[] x) {
        if (x.length < 2) return;
        double p = SeriesCorrelator.pearson(x, x);
        // Either 1.0 (if variance > 0) or 0.0 (if constant)
        assertThat(p).isBetween(0.0, 1.0);
    }

    @Property(tries = 50)
    void propertyPearsonSymmetric(@ForAll("series") double[] x,
                                   @ForAll("series") double[] y) {
        if (x.length != y.length || x.length < 2) return;
        double px = SeriesCorrelator.pearson(x, y);
        double py = SeriesCorrelator.pearson(y, x);
        // Pearson should be symmetric
        assertThat(px).isCloseTo(py, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertyPearsonBounded(@ForAll("series") double[] x,
                                 @ForAll("series") double[] y) {
        if (x.length != y.length || x.length < 2) return;
        double p = SeriesCorrelator.pearson(x, y);
        assertThat(p).isBetween(-1.0, 1.0);
    }

    @Property(tries = 30)
    void propertyAutocorrelationZeroIsOne(@ForAll("series") double[] x) {
        if (x.length < 4) return;
        double[] acf = SeriesCorrelator.autocorrelation(x, 2);
        assertThat(acf[0]).isCloseTo(1.0, Assertions.offset(1e-9));
    }

    @Property(tries = 30)
    void propertyCrossCorrelationAtLagZeroIsPearson(@ForAll("series") double[] x,
                                                       @ForAll("series") double[] y) {
        if (x.length != y.length || x.length < 2) return;
        double cc = SeriesCorrelator.crossCorrelation(x, y, 0);
        double p = SeriesCorrelator.pearson(x, y);
        assertThat(cc).isCloseTo(p, Assertions.offset(1e-9));
    }

    @Property(tries = 50)
    void propertySpearmanBounded(@ForAll("series") double[] x,
                                  @ForAll("series") double[] y) {
        if (x.length != y.length || x.length < 2) return;
        double s = SeriesCorrelator.spearman(x, y);
        assertThat(s).isBetween(-1.0, 1.0);
    }

    @Property(tries = 30)
    void propertyPearsonRandomIndependentIsSmall(@ForAll("anySeed") int seed,
                                                   @ForAll("lengths") int length) {
        if (length < 16) return;
        Random rng = new Random(seed);
        double[] x = new double[length];
        double[] y = new double[length];
        for (int i = 0; i < length; i++) {
            x[i] = rng.nextGaussian();
            y[i] = rng.nextGaussian();
        }
        double p = SeriesCorrelator.pearson(x, y);
        // Independent Gaussian samples have ~zero Pearson
        assertThat(Math.abs(p)).isLessThan(0.5);
    }

    @Property(tries = 30)
    void propertyPearsonCorrelatedIsPositive(@ForAll("anySeed") int seed,
                                                @ForAll("lengths") int length) {
        if (length < 16) return;
        Random rng = new Random(seed);
        double[] x = new double[length];
        double[] y = new double[length];
        for (int i = 0; i < length; i++) {
            x[i] = rng.nextGaussian();
            y[i] = x[i] + rng.nextGaussian() * 0.01;  // very high correlation
        }
        double p = SeriesCorrelator.pearson(x, y);
        assertThat(p).isGreaterThan(0.5);
    }

    @Provide
    Arbitrary<double[]> series() {
        return Arbitraries.integers().between(2, 32).flatMap(n ->
            Arbitraries.doubles().between(-10.0, 10.0).array(double[].class).ofSize(n));
    }

    @Provide
    Arbitrary<Integer> lengths() {
        return Arbitraries.integers().between(16, 64);
    }

    @Provide
    Arbitrary<Integer> anySeed() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    // Provide static offset helper
    private static class Assertions {
        static org.assertj.core.data.Offset<Double> offset(double tol) {
            return org.assertj.core.data.Offset.offset(tol);
        }
    }
}
