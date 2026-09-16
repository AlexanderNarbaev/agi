package io.matrix.consciousness;

import io.matrix.neuron.ConsciousBrain.CycleReport;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveGenesisProfileBuilderTest {

    @Test
    void builderProducesValidProfileFromCycleReport() {
        // Minimal cycle report: nulls allowed, treated as 0.0
        CycleReport report = new CycleReport(
            "obs", "pred", 0.1, false, new float[]{1.0f, 0.0f}, false,
            0.4, 0.3, 0.2, 0.1, false,
            ExtendedIntegrationMetrics.of(0.5, null),
            42L
        );
        long[] traj = {1L, 2L, 3L, 4L, 5L};
        long[] hashes = {100L, 200L, 300L};
        CognitiveGenesisProfile profile = CognitiveGenesisProfileBuilder.fromCycleReport(report, traj, hashes);
        assertThat(profile).isNotNull();
        assertThat(profile.phiBinary()).isEqualTo(0.4);
        assertThat(profile.phiF()).isEqualTo(0.2);
        assertThat(profile.phiLinGauss()).isEqualTo(0.5);
        assertThat(profile.nkEdgeOfChaosK()).isEqualTo(2);
        assertThat(profile.kolmogorovK()).isGreaterThan(0);
    }

    @Test
    void builderHandlesNullExtendedMetrics() {
        CycleReport report = new CycleReport(
            "obs", "pred", 0.1, false, new float[]{1.0f}, false,
            null, null, null, null, null,
            null, 0L
        );
        CognitiveGenesisProfile profile = CognitiveGenesisProfileBuilder.fromCycleReport(report, null, null);
        assertThat(profile).isNotNull();
        assertThat(profile.phiBinary()).isEqualTo(0.0);
    }

    @Test
    void builderHandlesEmptyTrajectory() {
        CycleReport report = new CycleReport(
            "obs", "pred", 0.1, false, new float[0], false,
            0.0, 0.0, 0.0, 0.0, false,
            null, 0L
        );
        CognitiveGenesisProfile profile = CognitiveGenesisProfileBuilder.fromCycleReport(
            report, new long[0], new long[0]);
        assertThat(profile.kolmogorovK()).isEqualTo(0.0);
        assertThat(profile.memristorConductance()).isEqualTo(MemristorSwitch.conductance(0.5));
    }

    @Test
    void builderHandlesEmptyHashes() {
        CycleReport report = new CycleReport(
            "obs", "pred", 0.1, false, new float[0], false,
            0.0, 0.0, 0.0, 0.0, false,
            null, 0L
        );
        CognitiveGenesisProfile profile = CognitiveGenesisProfileBuilder.fromCycleReport(
            report, new long[]{1L, 2L, 3L}, new long[0]);
        assertThat(profile.interAgentPhi()).isEqualTo(0.0);
    }

    @Test
    void builderPreservesPhiLinGaussFromExtended() {
        CycleReport report = new CycleReport(
            "obs", "pred", 0.1, false, new float[0], false,
            0.0, 0.0, 0.0, 0.0, false,
            ExtendedIntegrationMetrics.of(0.789, null),
            0L
        );
        CognitiveGenesisProfile profile = CognitiveGenesisProfileBuilder.fromCycleReport(
            report, null, null);
        assertThat(profile.phiLinGauss()).isEqualTo(0.789);
    }
}
