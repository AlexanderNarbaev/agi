package io.matrix.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RuntimeLimits}: default fallbacks, parsing, and clamping.
 *
 * <p>Environment variables cannot be mutated portably from JUnit, so the
 * public accessors are exercised for their default path (unset env in CI)
 * and the package-private parser is tested directly for all value classes.
 */
class RuntimeLimitsTest {

    // --- parsePositiveInt: value classes ---

    @Test
    void nullValueFallsBackToDefault() {
        assertThat(RuntimeLimits.parsePositiveInt(null, 7)).isEqualTo(7);
    }

    @Test
    void blankValueFallsBackToDefault() {
        assertThat(RuntimeLimits.parsePositiveInt("", 7)).isEqualTo(7);
        assertThat(RuntimeLimits.parsePositiveInt("   ", 7)).isEqualTo(7);
    }

    @Test
    void nonNumericValueFallsBackToDefault() {
        assertThat(RuntimeLimits.parsePositiveInt("abc", 7)).isEqualTo(7);
        assertThat(RuntimeLimits.parsePositiveInt("12x", 3)).isEqualTo(3);
    }

    @Test
    void validValueIsParsedWithTrim() {
        assertThat(RuntimeLimits.parsePositiveInt("42", 7)).isEqualTo(42);
        assertThat(RuntimeLimits.parsePositiveInt(" 42 ", 7)).isEqualTo(42);
    }

    @Test
    void zeroAndNegativeClampToOne() {
        assertThat(RuntimeLimits.parsePositiveInt("0", 7)).isEqualTo(1);
        assertThat(RuntimeLimits.parsePositiveInt("-5", 7)).isEqualTo(1);
    }

    @Test
    void defaultValueItselfClampsToAtLeastOne() {
        assertThat(RuntimeLimits.parsePositiveInt(null, 0)).isEqualTo(1);
        assertThat(RuntimeLimits.parsePositiveInt(null, -10)).isEqualTo(1);
    }

    // --- Public accessors: unset-env default path (CI has no such vars) ---

    @Test
    void brcMaxStepsReturnsAtLeastOne() {
        assertThat(RuntimeLimits.brcMaxSteps()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void ragTopKReturnsAtLeastOne() {
        assertThat(RuntimeLimits.ragTopK()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void mctsIterationsReturnsAtLeastOne() {
        assertThat(RuntimeLimits.mctsIterations()).isGreaterThanOrEqualTo(1);
    }

    // --- Contract constants ---

    @Test
    void documentedDefaultsMatchRepoConvention() {
        assertThat(RuntimeLimits.BRC_MAX_STEPS_DEFAULT).isEqualTo(5);
        assertThat(RuntimeLimits.RAG_TOP_K_DEFAULT).isEqualTo(5);
        assertThat(RuntimeLimits.MCTS_ITERATIONS_DEFAULT).isEqualTo(1000);
    }
}
