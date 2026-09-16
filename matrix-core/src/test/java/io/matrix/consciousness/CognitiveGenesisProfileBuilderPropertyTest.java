package io.matrix.consciousness;

import net.jqwik.api.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W142 — CognitiveGenesisProfileBuilder property-based tests.
 */
class CognitiveGenesisProfileBuilderPropertyTest {

    @Property(tries = 100)
    void propertyFromCycleReportHandlesNulls(@ForAll("cycleNumbers") int cycleNum) {
        // Minimal cycle report with nulls
        io.matrix.neuron.ConsciousBrain.CycleReport report = new io.matrix.neuron.ConsciousBrain.CycleReport(
            "obs", "pred", 0.1, false, new float[]{1.0f}, false,
            null, null, null, null, null,
            null, cycleNum
        );
        CognitiveGenesisProfile p = CognitiveGenesisProfileBuilder.fromCycleReport(report, null, null);
        assertThat(p).isNotNull();
        assertThat(p.phiBinary()).isEqualTo(0.0);
    }

    @Property(tries = 50)
    void propertyFromCycleReportPreservesPhiValues(@ForAll("phiValues") double phi) {
        io.matrix.neuron.ConsciousBrain.CycleReport report = new io.matrix.neuron.ConsciousBrain.CycleReport(
            "obs", "pred", 0.1, false, new float[]{1.0f}, false,
            phi, phi, phi, phi, false,
            null, 0L
        );
        CognitiveGenesisProfile p = CognitiveGenesisProfileBuilder.fromCycleReport(report, null, null);
        assertThat(p.phiBinary()).isEqualTo(phi);
        assertThat(p.phiF()).isEqualTo(phi);
        assertThat(p.phiR()).isEqualTo(phi);
    }

    @Property(tries = 50)
    void propertyFromCycleReportHandlesEmptyTrajectory(@ForAll("cycleNumbers") int cycleNum) {
        io.matrix.neuron.ConsciousBrain.CycleReport report = new io.matrix.neuron.ConsciousBrain.CycleReport(
            "obs", "pred", 0.1, false, new float[0], false,
            0.0, 0.0, 0.0, 0.0, false,
            null, cycleNum
        );
        CognitiveGenesisProfile p = CognitiveGenesisProfileBuilder.fromCycleReport(
            report, new long[0], new long[0]);
        assertThat(p.kolmogorovK()).isEqualTo(0.0);
    }

    @Provide
    Arbitrary<Integer> cycleNumbers() {
        return Arbitraries.integers().between(0, Integer.MAX_VALUE);
    }

    @Provide
    Arbitrary<Double> phiValues() {
        return Arbitraries.doubles().between(0.0, 1.0);
    }
}
