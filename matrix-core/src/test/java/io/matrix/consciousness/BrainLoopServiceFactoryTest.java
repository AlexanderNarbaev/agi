package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 275 — BrainLoopServiceFactory unit tests. */
class BrainLoopServiceFactoryTest {

    @Test
    void defaultFactory() {
        var svc = BrainLoopServiceFactory.createDefault();
        assertThat(svc).isNotNull();
        assertThat(svc.arousal()).isEqualTo(0.3);
    }

    @Test
    void fromConfig() {
        var svc = BrainLoopServiceFactory.fromConfig(null);
        assertThat(svc).isNotNull();
    }

    @Test
    void withCustomArousal() {
        var svc = BrainLoopServiceFactory.withArousal(0.5, 0.4, 0.1);
        assertThat(svc).isNotNull();
        assertThat(svc.arousal()).isEqualTo(0.3); // baseline
    }
}
