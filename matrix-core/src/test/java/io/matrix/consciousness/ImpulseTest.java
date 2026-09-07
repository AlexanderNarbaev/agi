package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 153 — Impulse unit tests. */
class ImpulseTest {

    @Test
    void impulseStoresFields() {
        Impulse i = new Impulse(Impulse.Source.CURIOSITY, 0.5, 0.8, "weather");
        assertThat(i.source).isEqualTo(Impulse.Source.CURIOSITY);
        assertThat(i.priority).isEqualTo(0.5);
        assertThat(i.weight).isEqualTo(0.8);
        assertThat(i.target).isEqualTo("weather");
    }

    @Test
    void effectiveScoreIsPriorityTimesWeight() {
        Impulse i = new Impulse(Impulse.Source.INTEGRITY, 0.5, 0.4, "X");
        assertThat(i.effectiveScore()).isEqualTo(0.2);
    }

    @Test
    void priorityClampedToRange() {
        Impulse high = new Impulse(Impulse.Source.GOAL, 1.5, 0.5, "X");
        assertThat(high.priority).isEqualTo(1.0);
        Impulse low = new Impulse(Impulse.Source.GOAL, -0.5, 0.5, "X");
        assertThat(low.priority).isZero();
    }

    @Test
    void weightClampedToRange() {
        Impulse high = new Impulse(Impulse.Source.NOVELTY, 0.5, 1.5, "X");
        assertThat(high.weight).isEqualTo(1.0);
    }

    @Test
    void nullTargetBecomesEmptyString() {
        Impulse i = new Impulse(Impulse.Source.REFLEX, 0.5, 0.5, null);
        assertThat(i.target).isEmpty();
    }

    @Test
    void equalsAndHashCode() {
        Impulse a = new Impulse(Impulse.Source.CURIOSITY, 0.5, 0.5, "x");
        Impulse b = new Impulse(Impulse.Source.CURIOSITY, 0.5, 0.5, "x");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void differentSourcesNotEqual() {
        Impulse a = new Impulse(Impulse.Source.CURIOSITY, 0.5, 0.5, "x");
        Impulse b = new Impulse(Impulse.Source.INTEGRITY, 0.5, 0.5, "x");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void sourceEnumHasAllExpected() {
        assertThat(Impulse.Source.values()).hasSize(5);
        assertThat(Impulse.Source.valueOf("CURIOSITY")).isNotNull();
        assertThat(Impulse.Source.valueOf("INTEGRITY")).isNotNull();
        assertThat(Impulse.Source.valueOf("GOAL")).isNotNull();
        assertThat(Impulse.Source.valueOf("NOVELTY")).isNotNull();
        assertThat(Impulse.Source.valueOf("REFLEX")).isNotNull();
    }
}
