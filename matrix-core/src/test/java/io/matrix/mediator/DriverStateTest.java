package io.matrix.mediator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class DriverStateTest {

    @Test
    void shouldCreateWithDefaults() {
        var energy = DriverState.withDefaults(DriverType.ENERGY);

        assertThat(energy.type()).isEqualTo(DriverType.ENERGY);
        assertThat(energy.level()).isEqualTo(0.3);
        assertThat(energy.target()).isEqualTo(0.2);
        assertThat(energy.adaptationRate()).isEqualTo(0.1);
        assertThat(energy.thresholdHigh()).isEqualTo(0.7);
        assertThat(energy.thresholdLow()).isEqualTo(0.1);
    }

    @Test
    void safetyShouldDefaultToLowTarget() {
        var safety = DriverState.withDefaults(DriverType.SAFETY);
        assertThat(safety.target()).isEqualTo(0.05);
        assertThat(safety.level()).isEqualTo(0.2);
    }

    @Test
    void curiosityShouldDefaultToMediumTarget() {
        var curiosity = DriverState.withDefaults(DriverType.CURIOSITY);
        assertThat(curiosity.target()).isEqualTo(0.4);
        assertThat(curiosity.level()).isEqualTo(0.5);
    }

    // ── New driver defaults (Task 4) ──

    @Test
    void entropyShouldDefaultToTargetFromEnum() {
        var entropy = DriverState.withDefaults(DriverType.ENTROPY);
        assertThat(entropy.target()).isEqualTo(DriverType.ENTROPY.target());
        assertThat(entropy.target()).isEqualTo(0.5);
        assertThat(entropy.level()).isBetween(0.0, 1.0);
    }

    @Test
    void socialShouldDefaultToTargetFromEnum() {
        var social = DriverState.withDefaults(DriverType.SOCIAL);
        assertThat(social.target()).isEqualTo(0.7);
    }

    @Test
    void selfActualizationShouldDefaultToTargetFromEnum() {
        var sa = DriverState.withDefaults(DriverType.SELFACTUALIZATION);
        assertThat(sa.target()).isEqualTo(0.8);
    }

    @Test
    void attentionShouldDefaultToTargetFromEnum() {
        var attention = DriverState.withDefaults(DriverType.ATTENTION);
        assertThat(attention.target()).isEqualTo(0.6);
    }

    @Test
    void ubuntuShouldDefaultToTargetFromEnum() {
        var ubuntu = DriverState.withDefaults(DriverType.UBUNTU);
        assertThat(ubuntu.target()).isEqualTo(0.9);
    }

    @ParameterizedTest
    @EnumSource(DriverType.class)
    void allDriversShouldHaveValidDefaults(DriverType type) {
        var state = DriverState.withDefaults(type);

        assertThat(state.type()).isEqualTo(type);
        assertThat(state.target()).isEqualTo(type.target());
        assertThat(state.level()).isBetween(0.0, 1.0);
        assertThat(state.adaptationRate()).isGreaterThan(0.0);
        assertThat(state.thresholdHigh()).isGreaterThan(state.thresholdLow());
    }

    @Test
    void shouldClampLevelBetweenZeroAndOne() {
        var energy = DriverState.withDefaults(DriverType.ENERGY);
        energy.nudge(10.0);
        assertThat(energy.level()).isEqualTo(1.0);

        var safety = DriverState.withDefaults(DriverType.SAFETY);
        safety.nudge(-10.0);
        assertThat(safety.level()).isEqualTo(0.0);
    }

    @Test
    void shouldDetectHighAndLowThresholds() {
        var energy = new DriverState(DriverType.ENERGY, 0.8, 0.2, 0.1, 0.0, 0.7, 0.1);

        assertThat(energy.isHigh()).isTrue();
        assertThat(energy.isLow()).isFalse();
        assertThat(energy.isActive()).isTrue();
    }

    @Test
    void shouldDetectLowThreshold() {
        var energy = new DriverState(DriverType.ENERGY, 0.05, 0.2, 0.1, 0.0, 0.7, 0.1);

        assertThat(energy.isLow()).isTrue();
        assertThat(energy.isHigh()).isFalse();
        assertThat(energy.isActive()).isFalse();
    }

    @Test
    void shouldUpdateLevelTowardTarget() {
        var rng = new Random(42);
        var energy = DriverState.withDefaults(DriverType.ENERGY);
        double before = energy.level();

        for (int i = 0; i < 10; i++) {
            energy.update(rng);
        }

        assertThat(energy.level()).isBetween(0.0, 1.0);
    }

    @ParameterizedTest
    @EnumSource(DriverType.class)
    void allDriversShouldUpdateTowardTarget(DriverType type) {
        var rng = new Random(42);
        var state = DriverState.withDefaults(type);

        for (int i = 0; i < 100; i++) {
            state.update(rng);
        }

        assertThat(state.level()).isBetween(0.0, 1.0);
    }

    @Test
    void nudgeShouldIncreaseLevel() {
        var energy = new DriverState(DriverType.ENERGY, 0.3, 0.2, 0.1, 0.0, 0.7, 0.1);
        double before = energy.level();

        energy.nudge(0.2);

        assertThat(energy.level()).isGreaterThan(before);
    }

    @Test
    void nudgeShouldDecreaseLevel() {
        var energy = new DriverState(DriverType.ENERGY, 0.3, 0.2, 0.1, 0.0, 0.7, 0.1);
        double before = energy.level();

        energy.nudge(-0.15);

        assertThat(energy.level()).isLessThan(before);
    }

    @Test
    void enumShouldHaveExactly8Drivers() {
        assertThat(DriverType.values()).hasSize(8);
    }

    @Test
    void enumTargetAndDescriptionShouldBeConsistent() {
        assertThat(DriverType.ENTROPY.target()).isEqualTo(0.5);
        assertThat(DriverType.ENTROPY.description()).contains("Exploration");

        assertThat(DriverType.SOCIAL.target()).isEqualTo(0.7);
        assertThat(DriverType.UBUNTU.target()).isEqualTo(0.9);
        assertThat(DriverType.UBUNTU.description()).contains("Collective");
    }
}
