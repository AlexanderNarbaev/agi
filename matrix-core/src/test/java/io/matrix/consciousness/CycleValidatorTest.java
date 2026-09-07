package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 282 — CycleValidator unit tests. */
class CycleValidatorTest {

    @Test
    void defaultsAcceptsNormal() {
        var v = CycleValidator.defaults();
        assertThat(v.validate("hello")).isTrue();
    }

    @Test
    void defaultsAcceptsEmpty() {
        var v = CycleValidator.defaults();
        assertThat(v.validate("")).isTrue();
        assertThat(v.validate(null)).isTrue();
    }

    @Test
    void defaultsRejectsTooLong() {
        var v = CycleValidator.defaults();
        assertThat(v.validate("x".repeat(10001))).isFalse();
    }

    @Test
    void strictRejectsEmpty() {
        var v = CycleValidator.strict();
        assertThat(v.validate("")).isFalse();
        assertThat(v.validate(null)).isFalse();
    }

    @Test
    void strictRejectsTooLong() {
        var v = CycleValidator.strict();
        assertThat(v.validate("x".repeat(1001))).isFalse();
    }

    @Test
    void customMaxLength() {
        var v = new CycleValidator(5, true);
        assertThat(v.validate("hello")).isTrue();
        assertThat(v.validate("hello!")).isFalse();
    }
}
