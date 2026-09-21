package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SelfModelTest {

    @Test
    void modelStepProducesSelfRepresentation() {
        float[] obs = {1.0f, 2.0f, 3.0f};
        float[] pred = {1.1f, 2.1f, 2.9f};
        float[] action = {0.5f, 0.5f};
        float[] consequence = {0.6f, 0.4f};
        SelfModel.SelfModelResult r = SelfModel.modelStep(obs, pred, action, consequence);
        assertThat(r.selfRepresentation()).hasSize(4);
        assertThat(r.primaryPredictionError()).isGreaterThanOrEqualTo(0.0);
        assertThat(r.metaPredictionError()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void perfectPredictionHasZeroError() {
        float[] obs = {1.0f, 2.0f, 3.0f};
        float[] pred = {1.0f, 2.0f, 3.0f};
        float[] action = {1.0f, 1.0f};
        float[] consequence = {1.0f, 1.0f};
        SelfModel.SelfModelResult r = SelfModel.modelStep(obs, pred, action, consequence);
        assertThat(r.primaryPredictionError()).isLessThan(1e-9);
        assertThat(r.metaPredictionError()).isLessThan(1e-9);
    }

    @Test
    void selfRepresentationHasFourComponents() {
        float[] obs = {1.0f};
        float[] pred = {1.0f};
        float[] action = {1.0f};
        float[] consequence = {1.0f};
        SelfModel.SelfModelResult r = SelfModel.modelStep(obs, pred, action, consequence);
        assertThat(r.selfRepresentation()).hasSize(4);
        assertThat(r.selfRepresentation()[0]).isEqualTo(0.0f);
        assertThat(r.selfRepresentation()[2]).isEqualTo(1.0f);
        assertThat(r.selfRepresentation()[3]).isEqualTo(1.0f);
    }

    @Test
    void totalErrorIsSumOfComponents() {
        float[] obs = {1.0f};
        float[] pred = {0.5f};
        float[] action = {0.5f};
        float[] consequence = {0.5f};
        SelfModel.SelfModelResult r = SelfModel.modelStep(obs, pred, action, consequence);
        double expected = r.primaryPredictionError() + r.metaPredictionError()
                + r.selfConsistency();
        assertThat(r.totalError()).isCloseTo(expected, within(1e-9));
    }

    @Test
    void rejectsNullObservation() {
        assertThatThrownBy(() -> SelfModel.modelStep(null, new float[]{1}, new float[]{1}, new float[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullPrediction() {
        assertThatThrownBy(() -> SelfModel.modelStep(new float[]{1}, null, new float[]{1}, new float[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullAction() {
        assertThatThrownBy(() -> SelfModel.modelStep(new float[]{1}, new float[]{1}, null, new float[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullConsequence() {
        assertThatThrownBy(() -> SelfModel.modelStep(new float[]{1}, new float[]{1}, new float[]{1}, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void selfConsistencyReflectsSelfModelStability() {
        float[] obs = {1.0f, 2.0f};
        float[] pred = {1.0f, 2.0f};
        float[] action = {0.5f};
        float[] consequence = {0.5f};
        SelfModel.SelfModelResult r = SelfModel.modelStep(obs, pred, action, consequence);
        assertThat(r.selfConsistency()).isLessThan(1e-6);
    }
}
