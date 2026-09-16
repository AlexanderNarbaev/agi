package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CausalEmergenceTest {

    @Test
    void effectiveInformationOfUniformIsZero() {
        double[] uniform = {0.25, 0.25, 0.25, 0.25};
        double ei = CausalEmergence.effectiveInformation(uniform);
        assertThat(ei).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void effectiveInformationOfDeltaIsPositive() {
        double[] delta = {1.0, 0.0, 0.0, 0.0};
        double ei = CausalEmergence.effectiveInformation(delta);
        // EI(delta) = log2(N) for delta over N-element alphabet
        assertThat(ei).isCloseTo(2.0, within(1e-9));  // log2(4) = 2
    }

    @Test
    void effectiveInformationOfMixed() {
        double[] mixed = {0.5, 0.25, 0.125, 0.125};
        double ei = CausalEmergence.effectiveInformation(mixed);
        // Should be between 0 and 2
        assertThat(ei).isBetween(0.0, 2.0);
    }

    @Test
    void causalEmergenceIdenticalIsZero() {
        double[] dist = {0.5, 0.3, 0.2};
        double ce = CausalEmergence.causalEmergence(dist, dist);
        assertThat(ce).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void coarsenByBinningReducesSize() {
        double[] micro = {1, 2, 3, 4, 5, 6, 7, 8};
        double[] macro = CausalEmergence.coarsenByBinning(micro, 2);
        assertThat(macro.length).isEqualTo(4);
        assertThat(macro[0]).isEqualTo(3.0);  // 1+2
        assertThat(macro[3]).isEqualTo(15.0);  // 7+8
    }

    @Test
    void maxCausalEmergenceNonNegativeForUniform() {
        // Uniform → EI macro = EI micro → Φ_CE = 0
        double[] uniform = {0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1};
        double maxCE = CausalEmergence.maxCausalEmergence(uniform);
        assertThat(maxCE).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void maxCausalEmergenceForStructuredDistribution() {
        // Highly structured distribution
        double[] structured = {10, 0, 10, 0, 10, 0, 10, 0};
        double maxCE = CausalEmergence.maxCausalEmergence(structured);
        // Should have positive Φ_CE for some bin size
        assertThat(Double.isFinite(maxCE)).isTrue();
    }

    private static org.assertj.core.data.Offset<Double> within(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
