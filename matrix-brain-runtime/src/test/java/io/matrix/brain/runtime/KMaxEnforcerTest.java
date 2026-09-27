package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KMaxEnforcerTest {

    @Test
    void compliant_input_does_not_throw() {
        KMaxEnforcer.enforce(0);
        KMaxEnforcer.enforce(1);
        KMaxEnforcer.enforce(20);
    }

    @Test
    void exceeding_kmax_throws_article_ii_violation() {
        assertThatThrownBy(() -> KMaxEnforcer.enforce(21))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Article II")
            .hasMessageContaining("K_MAX=20");
        assertThatThrownBy(() -> KMaxEnforcer.enforce(100))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isCompliant_returns_correct_boolean() {
        assertThat(KMaxEnforcer.isCompliant(0)).isTrue();
        assertThat(KMaxEnforcer.isCompliant(20)).isTrue();
        assertThat(KMaxEnforcer.isCompliant(21)).isFalse();
    }
}
