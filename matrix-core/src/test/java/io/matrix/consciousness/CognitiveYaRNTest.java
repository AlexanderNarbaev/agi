package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveYaRNTest {

    @Test
    void yarnFrequencyPositive() {
        double f = CognitiveYaRN.yarnFrequency(64, 5, 10000.0, 1.0);
        assertThat(f).isGreaterThan(0.0);
    }

    @Test
    void yarnFrequencyScaleAffectsOutput() {
        double f1 = CognitiveYaRN.yarnFrequency(64, 5, 10000.0, 1.0);
        double f2 = CognitiveYaRN.yarnFrequency(64, 5, 10000.0, 2.0);
        // Different scales give different frequencies
        assertThat(f1).isNotEqualTo(f2);
    }

    @Test
    void yarnTemperatureReduces() {
        double t1 = CognitiveYaRN.yarnTemperature(1.0, 1.0);
        double t2 = CognitiveYaRN.yarnTemperature(1.0, 2.0);
        // Higher scale → lower temperature
        assertThat(t2).isLessThan(t1);
    }

    @Test
    void yarnTemperatureZeroScale() {
        double t = CognitiveYaRN.yarnTemperature(1.0, 0.0);
        // Should handle zero scale gracefully (use 1.0 as default)
        assertThat(t).isGreaterThan(0.0);
    }

    @Test
    void applyPreservesDimensions() {
        double[] v = {1.0, 2.0, 3.0, 4.0};
        double[] result = CognitiveYaRN.apply(v, 1, 1.0);
        assertThat(result.length).isEqualTo(4);
    }

    @Test
    void applyNullReturnsNull() {
        assertThat(CognitiveYaRN.apply(null, 0, 1.0)).isNull();
    }

    @Test
    void contextMultiplierClamps() {
        assertThat(CognitiveYaRN.contextMultiplier(0.5)).isEqualTo(1.0);
        assertThat(CognitiveYaRN.contextMultiplier(2.0)).isEqualTo(2.0);
    }
}
