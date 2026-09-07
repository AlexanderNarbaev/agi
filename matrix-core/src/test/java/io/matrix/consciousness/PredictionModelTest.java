package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUN 156 — PredictionModel unit tests. */
class PredictionModelTest {

    @Test
    void predictSumsWeightsTimesStatePlusBias() {
        PredictionModel m = new PredictionModel(3, 0.5);
        m.predict(new double[]{1.0, 2.0, 3.0});
        // 1*1 + 1*2 + 1*3 + 0.5 = 6.5
        assertThat(m.lastPrediction()).isEqualTo(6.5);
    }

    @Test
    void updateErrorAbsoluteDifference() {
        PredictionModel m = new PredictionModel(2, 0.0);
        m.predict(new double[]{1.0, 1.0});
        double err = m.updateError(3.0);
        assertThat(err).isEqualTo(1.0);  // predicted 2.0, actual 3.0
        assertThat(m.lastError()).isEqualTo(1.0);
    }

    @Test
    void predictUpdatesLastPrediction() {
        PredictionModel m = new PredictionModel(2, 0.0);
        m.predict(new double[]{1.0, 2.0});
        assertThat(m.lastPrediction()).isEqualTo(3.0);
        m.predict(new double[]{0.0, 0.0});
        assertThat(m.lastPrediction()).isZero();
    }

    @Test
    void mismatchedStateLengthThrows() {
        PredictionModel m = new PredictionModel(3, 0.0);
        assertThatThrownBy(() -> m.predict(new double[]{1.0}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullStateThrows() {
        PredictionModel m = new PredictionModel(3, 0.0);
        assertThatThrownBy(() -> m.predict(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adaptChangesWeights() {
        PredictionModel m = new PredictionModel(2, 0.0);
        double[] before = m.size() >= 0 ? new double[2] : new double[0];
        for (int i = 0; i < 2; i++) before[i] = 1.0;  // initial
        m.predict(new double[]{1.0, 1.0});
        // predicted 2.0, actual target 5.0
        m.updateError(5.0);
        m.adapt(new double[]{1.0, 1.0}, 5.0, 0.1);
        // After adaptation, prediction improves
        m.predict(new double[]{1.0, 1.0});
        assertThat(m.lastPrediction()).isGreaterThan(2.0);
    }

    @Test
    void zeroSizeThrows() {
        assertThatThrownBy(() -> new PredictionModel(0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sizeAccessorReturnsConfigured() {
        PredictionModel m = new PredictionModel(5, 0.0);
        assertThat(m.size()).isEqualTo(5);
    }
}
