package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 274 — BrainLoopServiceBuilder unit tests. */
class BrainLoopServiceBuilderTest {

    @Test
    void defaultBuilderCreatesService() {
        var svc = new BrainLoopServiceBuilder().build();
        assertThat(svc).isNotNull();
        assertThat(svc.trace().count()).isZero();
    }

    @Test
    void customArousal() {
        var arousal = new ArousalDynamics(0.1, 0.5);
        var svc = new BrainLoopServiceBuilder().arousal(arousal).build();
        assertThat(svc.arousal()).isEqualTo(0.3); // baseline
    }

    @Test
    void chainingWorks() {
        var svc = new BrainLoopServiceBuilder()
                .encoder(new io.matrix.perception.TextEncoder(128))
                .build();
        assertThat(svc).isNotNull();
    }

    @Test
    void buildIsIndependent() {
        var b = new BrainLoopServiceBuilder();
        var svc1 = b.build();
        var svc2 = b.build();
        svc1.cycle("X");
        assertThat(svc2.trace().count()).isZero();
    }
}
