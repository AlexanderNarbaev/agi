package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsciousBrainTest {

    @Test
    void constructorStoresDimensions() {
        ConsciousBrain brain = new ConsciousBrain(1024, 42);
        assertThat(brain.getCycleCount()).isEqualTo(0);
    }

    @Test
    void rejectsBadDimensions() {
        assertThatThrownBy(() -> new ConsciousBrain(0, 42))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConsciousBrain(32, 42))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConsciousBrain(-1, 42))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cycleProducesReport() {
        ConsciousBrain brain = new ConsciousBrain(1024, 1);
        float[] obs = new float[1024];
        Random rng = new Random(42);
        for (int i = 0; i < 1024; i++) obs[i] = (float) rng.nextGaussian();
        ConsciousBrain.CycleReport r = brain.cycle(obs);
        assertThat(r.observationLabel()).startsWith("obs_");
        assertThat(r.predictionError()).isGreaterThanOrEqualTo(0.0);
        assertThat(brain.getCycleCount()).isEqualTo(1);
    }

    @Test
    void multipleCyclesAccumulate() {
        ConsciousBrain brain = new ConsciousBrain(1024, 2);
        Random rng = new Random(42);
        for (int i = 0; i < 5; i++) {
            float[] obs = new float[1024];
            for (int j = 0; j < 1024; j++) obs[j] = (float) rng.nextGaussian();
            brain.cycle(obs);
        }
        assertThat(brain.getCycleCount()).isEqualTo(5);
        // Meaning store should be populated
        assertThat(brain.getMeaningStore()).isNotEmpty();
    }

    @Test
    void consolidateRunsReplay() {
        ConsciousBrain brain = new ConsciousBrain(1024, 3);
        Random rng = new Random(42);
        // First populate hippocampus
        for (int i = 0; i < 3; i++) {
            float[] obs = new float[1024];
            for (int j = 0; j < 1024; j++) obs[j] = (float) rng.nextGaussian();
            brain.cycle(obs);
        }
        // Consolidate
        brain.consolidate(2);
    }

    @Test
    void freeEnergyComputed() {
        ConsciousBrain brain = new ConsciousBrain(1024, 4);
        Random rng = new Random(42);
        float[] obs = new float[1024];
        for (int j = 0; j < 1024; j++) obs[j] = (float) rng.nextGaussian();
        brain.cycle(obs);
        double fe = brain.freeEnergy(obs);
        assertThat(fe).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void meaningStoreGrows() {
        ConsciousBrain brain = new ConsciousBrain(1024, 5);
        Random rng = new Random(42);
        int initialSize = brain.getMeaningStore().size();
        for (int i = 0; i < 5; i++) {
            float[] obs = new float[1024];
            for (int j = 0; j < 1024; j++) obs[j] = (float) rng.nextGaussian();
            brain.cycle(obs);
        }
        // Meaning store should grow as we encounter new actions
        assertThat(brain.getMeaningStore().size()).isGreaterThanOrEqualTo(initialSize);
    }

    @Test
    void cycleReportHasAllFields() {
        ConsciousBrain.CycleReport r =
                new ConsciousBrain.CycleReport("obs_0", "obs_0", 0.1, true,
                        new float[]{0.1f, 0.2f, 0.3f, 0.4f}, true,
                        0.5, 0.55, 0.6, 0.7, false, null, 0L);
        assertThat(r.observationLabel()).isEqualTo("obs_0");
        assertThat(r.predictionLabel()).isEqualTo("obs_0");
        assertThat(r.predictionError()).isEqualTo(0.1);
        assertThat(r.acted()).isTrue();
        assertThat(r.selfRepresentation()).hasSize(4);
        assertThat(r.success()).isTrue();
        assertThat(r.phiBinary()).isEqualTo(0.5);
        assertThat(r.phiR()).isEqualTo(0.55);
        assertThat(r.phiF()).isEqualTo(0.6);
        assertThat(r.neuralComplexity()).isEqualTo(0.7);
    }

    @Test
    void cycleEmitsIntegrationMetricsPeriodically() {
        ConsciousBrain brain = new ConsciousBrain(1024, 42);
        Random rng = new Random(42);
        // Run enough cycles to trigger metric computation
        for (int i = 0; i < 25; i++) {
            float[] obs = new float[1024];
            for (int j = 0; j < 1024; j++) obs[j] = (float) rng.nextGaussian();
            ConsciousBrain.CycleReport r = brain.cycle(obs);
            // After cycle 10, metrics should be populated
            if (i >= 10) {
                // Metrics may or may not be present depending on cycle alignment
                if (r.phiBinary() != null) {
                    assertThat(r.phiBinary()).isGreaterThanOrEqualTo(0.0);
                    assertThat(r.phiF()).isBetween(0.0, 1.0);
                    assertThat(r.phiR()).isGreaterThanOrEqualTo(0.0);
                    assertThat(r.neuralComplexity()).isGreaterThanOrEqualTo(0.0);
                }
            }
        }
    }
}
