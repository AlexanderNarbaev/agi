package io.matrix.consciousness;

import io.matrix.neuron.ConsciousBrain.CycleReport;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveGenesisProfileBuilder2Test {

    @Test
    void builderUsesAnalogicalSimilarity() {
        CycleReport report = minimalReport();
        long[] traj = {1L, 2L, 3L, 4L, 5L};
        long[] exemplar = {1L, 2L, 3L, 4L, 5L};  // identical
        long[] alt = {100L, 200L, 300L, 400L, 500L};
        CognitiveGenesisProfile p = CognitiveGenesisProfileBuilder2.build(report, traj, exemplar, alt);
        assertThat(p.analogicalSimilarity()).isEqualTo(1.0);  // identical
    }

    @Test
    void builderUsesConceptualExclusion() {
        CycleReport report = minimalReport();
        long[] traj = {1L, 2L, 3L, 4L, 5L};
        long[] exemplar = {1L, 2L, 3L, 4L, 5L};
        long[] alt = {10L, 20L, 30L, 40L, 50L};  // similar but scaled
        CognitiveGenesisProfile p = CognitiveGenesisProfileBuilder2.build(report, traj, exemplar, alt);
        // Bit-mask exclusion will be 0 (same bit pattern), but Jaccard may differ
        assertThat(p.conceptualExclusion()).isBetween(0.0, 1.0);
    }

    @Test
    void builderDefaultsAnalogicalWithoutExemplar() {
        CycleReport report = minimalReport();
        long[] traj = {1L, 2L, 3L};
        CognitiveGenesisProfile p = CognitiveGenesisProfileBuilder2.build(report, traj, null, null);
        assertThat(p.analogicalSimilarity()).isEqualTo(0.5);
        assertThat(p.conceptualExclusion()).isEqualTo(0.5);
    }

    @Test
    void builderHandlesEmptyTrajectory() {
        CycleReport report = minimalReport();
        CognitiveGenesisProfile p = CognitiveGenesisProfileBuilder2.build(report, new long[0], null, null);
        assertThat(p.kolmogorovK()).isEqualTo(0.0);
    }

    private CycleReport minimalReport() {
        return new CycleReport(
            "obs", "pred", 0.1, false, new float[]{1.0f}, false,
            0.4, 0.3, 0.2, 0.1, false,
            null, 0L
        );
    }
}
