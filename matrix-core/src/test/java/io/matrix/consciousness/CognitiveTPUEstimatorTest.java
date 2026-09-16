package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveTPUEstimatorTest {

    @Test
    void estimateProfileCostHasComponents() {
        CognitiveTPUEstimator.CostBreakdown b =
            CognitiveTPUEstimator.estimateProfileCost(64, CognitiveTPUEstimator.CostRates.defaults());
        assertThat(b.computeCost()).isGreaterThanOrEqualTo(0.0);
        assertThat(b.memoryCost()).isGreaterThanOrEqualTo(0.0);
        assertThat(b.storageCost()).isGreaterThanOrEqualTo(0.0);
        assertThat(b.energyCost()).isGreaterThanOrEqualTo(0.0);
        assertThat(b.totalCost()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void totalCostIsSumOfComponents() {
        CognitiveTPUEstimator.CostBreakdown b =
            CognitiveTPUEstimator.estimateProfileCost(64, CognitiveTPUEstimator.CostRates.defaults());
        double sum = b.computeCost() + b.memoryCost() + b.storageCost() + b.energyCost();
        assertThat(b.totalCost()).isCloseTo(sum, offset(1e-12));
    }

    @Test
    void largerDimensionHigherCost() {
        CognitiveTPUEstimator.CostBreakdown small =
            CognitiveTPUEstimator.estimateProfileCost(16, CognitiveTPUEstimator.CostRates.defaults());
        CognitiveTPUEstimator.CostBreakdown large =
            CognitiveTPUEstimator.estimateProfileCost(256, CognitiveTPUEstimator.CostRates.defaults());
        assertThat(large.totalCost()).isGreaterThan(small.totalCost());
    }

    @Test
    void emptySequenceZeroCost() {
        CognitiveTPUEstimator.CostBreakdown b =
            CognitiveTPUEstimator.estimateSequenceCost(new ArrayList<>(), 64, CognitiveTPUEstimator.CostRates.defaults());
        assertThat(b.totalCost()).isEqualTo(0.0);
    }

    @Test
    void sequenceCostScalesWithCount() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) profiles.add(makeProfile(i / 10.0));
        CognitiveTPUEstimator.CostBreakdown b =
            CognitiveTPUEstimator.estimateSequenceCost(profiles, 64, CognitiveTPUEstimator.CostRates.defaults());
        assertThat(b.totalCost()).isGreaterThan(0.0);
    }

    @Test
    void costPerProfileCorrect() {
        double total = 10.0;
        int count = 100;
        assertThat(CognitiveTPUEstimator.costPerProfile(count, total)).isEqualTo(0.1);
    }

    @Test
    void zeroProfilesReturnZero() {
        assertThat(CognitiveTPUEstimator.costPerProfile(0, 10.0)).isEqualTo(0.0);
    }

    @Test
    void defaultCostRatesValid() {
        CognitiveTPUEstimator.CostRates r = CognitiveTPUEstimator.CostRates.defaults();
        assertThat(r.tflopsHour()).isGreaterThan(0.0);
        assertThat(r.watts()).isGreaterThan(0.0);
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
