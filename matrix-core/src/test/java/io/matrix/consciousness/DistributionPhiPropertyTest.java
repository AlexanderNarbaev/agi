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
        if (length < 8) return;   // RECON-W34.9: calibrated, see the note below
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
        // RECON-W34.9: this asserted a PER-DRAW ordering, `phiCorr >= phiInd`, over random
        // Gaussian data. Measured on this machine across 87,000 draws (seeds 0..2999,
        // lengths 4..32):
        //
        //     correlated < independent in 12,381 draws  (14.2%)
        //     and in some draws the two are exactly equal
        //
        // So the property was not unlucky, it was false: for any given short series the
        // correlated sample can easily score below an independent one, and with 20 tries the
        // chance of at least one violation is about 95%. That is a property of sampling, not
        // of DistributionPhi.
        //
        // The claim that IS true is distributional: correlation RAISES phi ON AVERAGE. The
        // minimum length and the number of draws are then not arbitrary -- they are the point
        // at which the mean separates from the sampling noise. Measured failure rate of the
        // mean claim, over 200 independent seeds each:
        //
        //     length   4,  40 draws -> 22.5% fail       length  4, 200 draws ->  4.5% fail
        //     length   6,  40 draws ->  5.0% fail       length  6, 200 draws ->  0.0% fail
        //     length   8,  40 draws ->  2.0% fail       length  8, 100 draws ->  0.0% fail
        //     length  12,  40 draws ->  0.0% fail
        //
        // So the property requires length >= 8 and 100 draws, which measured 0 failures in
        // 200 trials. At length 4 the true margin is only about +0.056 nats, which no honest
        // number of draws recovers without making the test slow enough to be skipped anyway.
        double sumCorr = 0;
        double sumInd = 0;
        final int draws = 100;
        for (int d = 0; d < draws; d++) {
            double[] cx = new double[length];
            double[] cy = new double[length];
            for (int i = 0; i < length; i++) {
                cx[i] = rng.nextGaussian();
                cy[i] = cx[i] + rng.nextGaussian() * 0.1;
            }
            sumCorr += DistributionPhi.phiFrom2DTimeSeries(cx, cy, 1, 16);
            for (int i = 0; i < length; i++) {
                cx[i] = rng.nextGaussian();
                cy[i] = rng.nextGaussian();
            }
            sumInd += DistributionPhi.phiFrom2DTimeSeries(cx, cy, 1, 16);
        }
        assertThat(sumCorr / draws)
                .as("mean phi over %d draws at length %d: correlated series must score higher "
                        + "than independent ones ON AVERAGE, even though individual draws may not",
                        draws, length)
                .isGreaterThan(sumInd / draws);
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
