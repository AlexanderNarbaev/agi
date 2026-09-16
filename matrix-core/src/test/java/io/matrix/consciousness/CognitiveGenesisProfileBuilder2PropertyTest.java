package io.matrix.consciousness;

import net.jqwik.api.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * W143 — CognitiveGenesisProfileBuilder2 property-based tests.
 */
class CognitiveGenesisProfileBuilder2PropertyTest {

    @Property(tries = 50)
    void propertyBuildHandlesNullExemplars(@ForAll("trajectories") long[] traj) {
        io.matrix.neuron.ConsciousBrain.CycleReport report = minimalReport();
        CognitiveGenesisProfile p = CognitiveGenesisProfileBuilder2.build(report, traj, null, null);
        assertThat(p).isNotNull();
        // With null exemplars → defaults 0.5
        assertThat(p.analogicalSimilarity()).isEqualTo(0.5);
        assertThat(p.conceptualExclusion()).isEqualTo(0.5);
    }

    @Property(tries = 50)
    void propertyBuildWithIdenticalExemplarAnalogicalOne(@ForAll("trajectories") long[] traj) {
        io.matrix.neuron.ConsciousBrain.CycleReport report = minimalReport();
        long[] exemplar = traj.clone();
        long[] alt = new long[traj.length];
        for (int i = 0; i < alt.length; i++) alt[i] = traj[i] ^ 0xFFL;  // bit-complement
        CognitiveGenesisProfile p = CognitiveGenesisProfileBuilder2.build(report, traj, exemplar, alt);
        // Trajectory identical to exemplar → bit similarity = 1.0
        assertThat(p.analogicalSimilarity()).isEqualTo(1.0);
    }

    @Property(tries = 50)
    void propertyBuildHandlesEmptyTrajectory() {
        io.matrix.neuron.ConsciousBrain.CycleReport report = minimalReport();
        CognitiveGenesisProfile p = CognitiveGenesisProfileBuilder2.build(report, new long[0], null, null);
        assertThat(p).isNotNull();
        assertThat(p.kolmogorovK()).isEqualTo(0.0);
    }

    @Property(tries = 50)
    void propertyBuildPreservesPhiFromReport(@ForAll("phiValues") double phi) {
        io.matrix.neuron.ConsciousBrain.CycleReport report = new io.matrix.neuron.ConsciousBrain.CycleReport(
            "obs", "pred", 0.1, false, new float[]{1.0f}, false,
            phi, phi, phi, phi, false,
            null, 0L
        );
        CognitiveGenesisProfile p = CognitiveGenesisProfileBuilder2.build(report, null, null, null);
        assertThat(p.phiBinary()).isEqualTo(phi);
    }

    private io.matrix.neuron.ConsciousBrain.CycleReport minimalReport() {
        return new io.matrix.neuron.ConsciousBrain.CycleReport(
            "obs", "pred", 0.1, false, new float[]{1.0f}, false,
            0.5, 0.5, 0.5, 0.5, false,
            null, 0L
        );
    }

    @Provide
    Arbitrary<long[]> trajectories() {
        return Arbitraries.integers().between(4, 16).flatMap(t ->
            Arbitraries.longs().between(0, 100).array(long[].class).ofSize(t));
    }

    @Provide
    Arbitrary<Double> phiValues() {
        return Arbitraries.doubles().between(0.0, 1.0);
    }
}
