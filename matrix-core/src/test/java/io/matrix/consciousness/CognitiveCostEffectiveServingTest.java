package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveCostEffectiveServingTest {

    @Test
    void baseCostIsBaseline() {
        CognitiveCostEffectiveServing.ServingCost c =
            CognitiveCostEffectiveServing.estimateCost(false, false, false, false, false);
        assertThat(c.baseCost()).isEqualTo(1.0);
        assertThat(c.totalCost()).isEqualTo(1.0);
    }

    @Test
    void fp4ReducesCost() {
        CognitiveCostEffectiveServing.ServingCost c =
            CognitiveCostEffectiveServing.estimateCost(true, false, false, false, false);
        assertThat(c.quantizedCost()).isLessThan(c.baseCost());
    }

    @Test
    void fp8LessReductionThanFP4() {
        CognitiveCostEffectiveServing.ServingCost fp8 =
            CognitiveCostEffectiveServing.estimateCost(false, true, false, false, false);
        CognitiveCostEffectiveServing.ServingCost fp4 =
            CognitiveCostEffectiveServing.estimateCost(true, false, false, false, false);
        assertThat(fp4.quantizedCost()).isLessThan(fp8.quantizedCost());
    }

    @Test
    void cachingReducesCost() {
        CognitiveCostEffectiveServing.ServingCost c =
            CognitiveCostEffectiveServing.estimateCost(false, false, true, false, false);
        assertThat(c.cachedCost()).isLessThan(c.quantizedCost());
    }

    @Test
    void batchingReducesCost() {
        CognitiveCostEffectiveServing.ServingCost c =
            CognitiveCostEffectiveServing.estimateCost(false, false, false, true, false);
        assertThat(c.batchedCost()).isLessThan(c.cachedCost());
    }

    @Test
    void distillationReducesCost() {
        CognitiveCostEffectiveServing.ServingCost c =
            CognitiveCostEffectiveServing.estimateCost(false, false, false, false, true);
        assertThat(c.distilledCost()).isLessThan(c.batchedCost());
    }

    @Test
    void allOptimizationsMaxSpeedup() {
        CognitiveCostEffectiveServing.ServingCost c =
            CognitiveCostEffectiveServing.estimateCost(true, false, true, true, true);
        assertThat(c.speedupFactor()).isGreaterThan(10.0);
    }

    @Test
    void recommendStrategyNoQuant() {
        assertThat(CognitiveCostEffectiveServing.recommendStrategy(1.0))
            .isEqualTo(CognitiveCostEffectiveServing.Strategy.NO_QUANT);
    }

    @Test
    void recommendStrategyFP8() {
        assertThat(CognitiveCostEffectiveServing.recommendStrategy(0.5))
            .isEqualTo(CognitiveCostEffectiveServing.Strategy.FP8_QUANT);
    }

    @Test
    void recommendStrategyFP4() {
        assertThat(CognitiveCostEffectiveServing.recommendStrategy(0.1))
            .isEqualTo(CognitiveCostEffectiveServing.Strategy.FP4_QUANT);
    }

    @Test
    void compareToBaselineValid() {
        CognitiveCostEffectiveServing.ServingCost c =
            CognitiveCostEffectiveServing.estimateCost(true, false, true, true, true);
        double ratio = CognitiveCostEffectiveServing.compareToBaseline(c, 1.0);
        assertThat(ratio).isGreaterThan(10.0);
    }
}
