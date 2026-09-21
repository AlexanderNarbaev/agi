package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 177 — BrainLoopArchitecture unit tests. */
class BrainLoopArchitectureTest {

    @Test
    void componentsArePresent() {
        assertThat(BrainLoopArchitecture.hasComponent("perception")).isTrue();
        assertThat(BrainLoopArchitecture.hasComponent("saliency")).isTrue();
        assertThat(BrainLoopArchitecture.hasComponent("attention")).isTrue();
        assertThat(BrainLoopArchitecture.hasComponent("deliberation")).isTrue();
        assertThat(BrainLoopArchitecture.hasComponent("gate")).isTrue();
        assertThat(BrainLoopArchitecture.hasComponent("action")).isTrue();
        assertThat(BrainLoopArchitecture.hasComponent("trace")).isTrue();
    }

    @Test
    void unknownComponentReturnsFalse() {
        assertThat(BrainLoopArchitecture.hasComponent("unknown")).isFalse();
    }

    @Test
    void componentCount() {
        assertThat(BrainLoopArchitecture.componentCount()).isEqualTo(7);
    }

    @Test
    void summaryContainsAllComponents() {
        String summary = BrainLoopArchitecture.summary();
        assertThat(summary).contains("perception");
        assertThat(summary).contains("saliency");
        assertThat(summary).contains("gate");
    }

    @Test
    void componentRecord() {
        BrainLoopArchitecture.Component c =
                new BrainLoopArchitecture.Component("X", "Y role");
        assertThat(c.name()).isEqualTo("X");
        assertThat(c.role()).isEqualTo("Y role");
    }
}
