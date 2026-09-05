package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 118 — CostCalculator unit tests. */
class CostCalculatorTest {

    @Test
    void customConstructor() {
        CostCalculator c = new CostCalculator("test", 1.0, 3.0);
        assertThat(c.getCost().model()).isEqualTo("test");
        assertThat(c.getCost().inputCostPerMTok()).isEqualTo(1.0);
        assertThat(c.getCost().outputCostPerMTok()).isEqualTo(3.0);
    }

    @Test
    void qwenSelfHostedFactory() {
        CostCalculator c = CostCalculator.qwen05bSelfHosted();
        assertThat(c.getCost().model()).contains("Qwen");
        assertThat(c.getCost().inputCostPerMTok()).isEqualTo(0.10);
        assertThat(c.getCost().outputCostPerMTok()).isEqualTo(0.30);
    }

    @Test
    void cloudLowTierFactory() {
        CostCalculator c = CostCalculator.cloudLowTier();
        assertThat(c.getCost().model()).isEqualTo("cloud-low");
    }

    @Test
    void costForOneMillionEach() {
        // 1M input + 1M output at $0.10/$0.30 = $0.10 + $0.30 = $0.40
        CostCalculator c = CostCalculator.qwen05bSelfHosted();
        double cost = c.costFor(1_000_000, 1_000_000);
        assertThat(cost).isCloseTo(0.40, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void costForSmallAmount() {
        CostCalculator c = CostCalculator.qwen05bSelfHosted();
        // 100 input + 50 output = 0.00001 + 0.000015 = $0.000025
        double cost = c.costFor(100, 50);
        assertThat(cost).isCloseTo(0.000025, org.assertj.core.data.Offset.offset(0.0000001));
    }

    @Test
    void costForZero() {
        CostCalculator c = CostCalculator.qwen05bSelfHosted();
        assertThat(c.costFor(0, 0)).isZero();
    }

    @Test
    void modelCostRecordAccessors() {
        CostCalculator.ModelCost m =
                new CostCalculator.ModelCost("x", 1.5, 2.5);
        assertThat(m.model()).isEqualTo("x");
        assertThat(m.inputCostPerMTok()).isEqualTo(1.5);
        assertThat(m.outputCostPerMTok()).isEqualTo(2.5);
    }
}
