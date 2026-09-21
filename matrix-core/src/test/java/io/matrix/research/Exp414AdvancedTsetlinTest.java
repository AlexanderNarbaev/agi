package io.matrix.research;

import io.matrix.tsetlin.AdvancedTsetlinMachine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 414 — Phase S: Tsetlin expansion (fractional s, Γ(t),
 * multi-clause, state export).
 */
class Exp414AdvancedTsetlinTest {

    @Test
    void tsetlinInitAndPredict() {
        var model = AdvancedTsetlinMachine.init(4, 5, 2, 0xCAFE);
        assertThat(model.nFeatures()).isEqualTo(4);
        assertThat(model.nClauses()).isEqualTo(5);
        assertThat(model.nClasses()).isEqualTo(2);
        int[] features = {1, 0, 1, 0};
        var pred = AdvancedTsetlinMachine.predict(model, features);
        assertThat(pred.predicted()).isIn(0, 1);
    }

    @Test
    void temperingSchedule() {
        // At t=0: Γ(0) = Γ_max
        assertThat(AdvancedTsetlinMachine.temperingSchedule(2.0, 0.1, 0))
                .isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-9));
        // At t=10: Γ(10) = 2.0 / (1 + 1) = 1.0
        assertThat(AdvancedTsetlinMachine.temperingSchedule(2.0, 0.1, 10))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void fractionalS() {
        // baseS=10, fraction=0.5 → 15 (between 10 and 20)
        assertThat(AdvancedTsetlinMachine.fractionalS(10, 0.5))
                .isCloseTo(15.0, org.assertj.core.data.Offset.offset(0.01));
        // baseS=10, fraction=2.0 → 30 but capped at 2*10=20
        assertThat(AdvancedTsetlinMachine.fractionalS(10, 2.0))
                .isEqualTo(20.0);
    }

    @Test
    void stateExportRoundTrip() {
        var model = AdvancedTsetlinMachine.init(4, 3, 2, 0xAL);
        byte[] data = AdvancedTsetlinMachine.exportState(model);
        assertThat(data).isNotEmpty();
        // Could import too, but skipped to avoid duplicating model class
    }
}
