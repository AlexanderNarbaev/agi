package io.matrix.dialog;

import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProactiveInterfaceTest {

    @Test
    void shouldNotInitiateWhenDisabled() {
        ProactiveInterface pi = new ProactiveInterface();
        pi.disable();

        var decision = pi.evaluate(
                List.of(DriverState.withDefaults(DriverType.CURIOSITY)),
                List.of(), List.of(), List.of());

        assertThat(decision.shouldInitiate()).isFalse();
    }

    @Test
    void shouldInitiateOnCuriosityFinding() {
        ProactiveInterface pi = new ProactiveInterface();
        var curiosity = DriverState.withDefaults(DriverType.CURIOSITY);
        curiosity.nudge(0.9);

        var decision = pi.evaluate(
                List.of(curiosity),
                List.of("New pattern detected in neuron cluster"),
                List.of(), List.of());

        assertThat(decision.shouldInitiate()).isTrue();
        assertThat(decision.reason()).isEqualTo(
                ProactiveInterface.InitiationReason.CURIOSITY_FINDING);
        assertThat(decision.suggestedMessage()).contains("pattern");
    }

    @Test
    void shouldInitiateOnAnomaly() {
        ProactiveInterface pi = new ProactiveInterface();

        var decision = pi.evaluate(
                List.of(DriverState.withDefaults(DriverType.SAFETY)),
                List.of(),
                List.of("Derangement detected in cluster 3"),
                List.of());

        assertThat(decision.shouldInitiate()).isTrue();
        assertThat(decision.reason()).isEqualTo(
                ProactiveInterface.InitiationReason.ANOMALY_DETECTED);
    }

    @Test
    void shouldInitiateOnMilestone() {
        ProactiveInterface pi = new ProactiveInterface();

        var decision = pi.evaluate(
                List.of(DriverState.withDefaults(DriverType.ENERGY)),
                List.of(), List.of(),
                List.of("Reached 1000 generations"));

        assertThat(decision.shouldInitiate()).isTrue();
        assertThat(decision.reason()).isEqualTo(
                ProactiveInterface.InitiationReason.MILESTONE_REACHED);
    }

    @Test
    void shouldNotInitiateWhenNothingTriggered() {
        ProactiveInterface pi = new ProactiveInterface();
        pi.recordInteraction("hello");

        var decision = pi.evaluate(
                List.of(DriverState.withDefaults(DriverType.ENERGY)),
                List.of(), List.of(), List.of());

        assertThat(decision.shouldInitiate()).isFalse();
    }

    @Test
    void shouldDisableAfterRepeatedIgnore() {
        ProactiveInterface pi = new ProactiveInterface();

        for (int i = 0; i < 5; i++) {
            pi.recordIgnored();
        }

        assertThat(pi.isEnabled()).isFalse();
    }

    @Test
    void shouldRecordInteractions() {
        ProactiveInterface pi = new ProactiveInterface();

        pi.recordInteraction("Hello");
        pi.recordInteraction("How are you?");

        assertThat(pi.interactionHistory()).hasSize(2);
    }

    @Test
    void shouldReEnable() {
        ProactiveInterface pi = new ProactiveInterface();
        pi.disable();
        pi.enable();

        assertThat(pi.isEnabled()).isTrue();
    }
}
