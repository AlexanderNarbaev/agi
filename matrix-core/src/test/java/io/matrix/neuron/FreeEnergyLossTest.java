package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class FreeEnergyLossTest {

    @Test
    void computeWakeLossIsZeroForPerfectPrediction() {
        float[] obs = {1.0f, 2.0f, 3.0f};
        float[] pred = {1.0f, 2.0f, 3.0f};
        FreeEnergyLoss.FreeEnergyResult r = FreeEnergyLoss.compute(obs, pred, null, null);
        assertThat(r.wakeLoss()).isCloseTo(0.0, within(1e-9));
        assertThat(r.totalFreeEnergy()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void computeWakeLossIsPositiveForMismatch() {
        float[] obs = {1.0f, 2.0f, 3.0f};
        float[] pred = {0.0f, 0.0f, 0.0f};
        FreeEnergyLoss.FreeEnergyResult r = FreeEnergyLoss.compute(obs, pred, null, null);
        // Wake loss is sqrt((1+4+9)/3) = sqrt(14/3) ≈ 2.16
        assertThat(r.wakeLoss()).isCloseTo(Math.sqrt(14.0 / 3.0), within(0.01));
    }

    @Test
    void computeSleepLossWhenFantasyProvided() {
        float[] obs = {1.0f, 2.0f};
        float[] pred = {0.5f, 1.0f};
        float[] fantasy = {0.6f, 1.1f};
        float[] genOutput = {0.7f, 1.2f};
        FreeEnergyLoss.FreeEnergyResult r =
                FreeEnergyLoss.compute(obs, pred, fantasy, genOutput);
        assertThat(r.sleepLoss()).isGreaterThan(0.0);
        assertThat(r.totalFreeEnergy()).isGreaterThan(r.wakeLoss());
    }

    @Test
    void computeNoSleepWhenFantasyNull() {
        float[] obs = {1.0f, 2.0f};
        float[] pred = {0.5f, 1.0f};
        FreeEnergyLoss.FreeEnergyResult r = FreeEnergyLoss.compute(obs, pred, null, null);
        assertThat(r.sleepLoss()).isEqualTo(0.0);
    }

    @Test
    void computeComplexityTerm() {
        float[] obs = {1.0f, 2.0f, 3.0f, 4.0f};
        float[] pred = {0.5f, 1.5f, 2.5f, 3.5f};
        // Residuals are constant (0.5), so variance = 0
        FreeEnergyLoss.FreeEnergyResult r = FreeEnergyLoss.compute(obs, pred, null, null);
        assertThat(r.complexityTerm()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void rejectsNullInputs() {
        assertThatThrownBy(() -> FreeEnergyLoss.compute(null, new float[]{1}, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FreeEnergyLoss.compute(new float[]{1}, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLengthMismatch() {
        assertThatThrownBy(() -> FreeEnergyLoss.compute(new float[]{1, 2}, new float[]{1}, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void freeEnergyDecreasesOverLearningCycles() {
        // Simulate learning: prediction improves with each cycle
        float[] obs = {1.0f, 2.0f, 3.0f};
        float[] pred = {0.0f, 0.0f, 0.0f};
        double firstFE = FreeEnergyLoss.compute(obs, pred, null, null).totalFreeEnergy();
        // Improve prediction iteratively
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < pred.length; j++) {
                pred[j] += (obs[j] - pred[j]) * 0.5f;
            }
        }
        double lastFE = FreeEnergyLoss.compute(obs, pred, null, null).totalFreeEnergy();
        System.out.printf("[FEL] first=%.4f last=%.4f%n", firstFE, lastFE);
        assertThat(lastFE).isLessThan(firstFE);
    }

    @Test
    void resultRecordHasAllFields() {
        FreeEnergyLoss.FreeEnergyResult r = new FreeEnergyLoss.FreeEnergyResult(
                1.0, 0.5, 0.3, 0.2);
        assertThat(r.totalFreeEnergy()).isEqualTo(1.0);
        assertThat(r.wakeLoss()).isEqualTo(0.5);
        assertThat(r.sleepLoss()).isEqualTo(0.3);
        assertThat(r.complexityTerm()).isEqualTo(0.2);
    }
}
