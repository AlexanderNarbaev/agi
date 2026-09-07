package io.matrix.bir;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RUN 174 — KMaxEnforcer unit tests. */
class KMaxEnforcerTest {

    @Test
    void kMaxIs20() {
        assertThat(KMaxEnforcer.K_MAX).isEqualTo(20);
        assertThat(KMaxEnforcer.K_MIN).isEqualTo(1);
    }

    @Test
    void validInputsPass() {
        for (int i = 1; i <= 20; i++) {
            KMaxEnforcer.assertWithinRange(i);
            assertThat(KMaxEnforcer.isValid(i)).isTrue();
        }
    }

    @Test
    void zeroInputsThrows() {
        assertThatThrownBy(() -> KMaxEnforcer.assertWithinRange(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeInputsThrows() {
        assertThatThrownBy(() -> KMaxEnforcer.assertWithinRange(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void over21InputsThrows() {
        assertThatThrownBy(() -> KMaxEnforcer.assertWithinRange(21))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KMaxEnforcer.assertWithinRange(100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isValidMatches() {
        assertThat(KMaxEnforcer.isValid(0)).isFalse();
        assertThat(KMaxEnforcer.isValid(20)).isTrue();
        assertThat(KMaxEnforcer.isValid(21)).isFalse();
    }

    @Test
    void capStaysAtBoundary() {
        assertThat(KMaxEnforcer.cap(15)).isEqualTo(15);
        assertThat(KMaxEnforcer.cap(20)).isEqualTo(20);
        assertThat(KMaxEnforcer.cap(21)).isEqualTo(20); // capped
        assertThat(KMaxEnforcer.cap(100)).isEqualTo(20);
    }

    @Test
    void capOnValidUnchanged() {
        for (int i = 1; i <= 20; i++) {
            assertThat(KMaxEnforcer.cap(i)).isEqualTo(i);
        }
    }
}
