package io.matrix.experimental;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 200 — AblationStudy unit tests. */
class AblationStudyTest {

    @Test
    void ablatePerception() {
        var result = AblationStudy.run(
                AblationStudy.Component.PERCEPTION, 50);
        assertThat(result.removed())
                .isEqualTo(AblationStudy.Component.PERCEPTION);
        assertThat(result.cycles()).isEqualTo(50);
        // All should accept (no adversarial)
        assertThat(result.accepted()).isEqualTo(50);
    }

    @Test
    void ablateGate() {
        var result = AblationStudy.run(AblationStudy.Component.GATE, 30);
        assertThat(result.removed())
                .isEqualTo(AblationStudy.Component.GATE);
        assertThat(result.cycles()).isEqualTo(30);
    }

    @Test
    void formatProducesReadableString() {
        var result = AblationStudy.run(AblationStudy.Component.ACTION, 10);
        String s = AblationStudy.format(result);
        assertThat(s).contains("Ablation");
        assertThat(s).contains("removed=ACTION");
        assertThat(s).contains("cycles=10");
    }

    @Test
    void resultRecord() {
        var r = new AblationStudy.AblationResult(
                AblationStudy.Component.SALIENCY, 100, 90, 10, 0.5);
        assertThat(r.removed())
                .isEqualTo(AblationStudy.Component.SALIENCY);
        assertThat(r.cycles()).isEqualTo(100);
        assertThat(r.accepted()).isEqualTo(90);
        assertThat(r.denied()).isEqualTo(10);
        assertThat(r.arousalFinal()).isEqualTo(0.5);
    }

    @Test
    void allComponentsEnumValues() {
        assertThat(AblationStudy.Component.values()).hasSize(6);
        assertThat(AblationStudy.Component.valueOf("PERCEPTION")).isNotNull();
        assertThat(AblationStudy.Component.valueOf("ACTION")).isNotNull();
    }
}
