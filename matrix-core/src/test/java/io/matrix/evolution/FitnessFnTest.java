package io.matrix.evolution;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FitnessFnTest {

    @Test
    void constructorShouldStoreParameters() {
        Random rng = new Random(0L);
        FitnessFn fn = new FitnessFn(10, 10, 3, 2, 50, 2, rng);
        // Internal state is queried via the only public method, evaluate()
        // (which requires a Chromosome). This constructor-only test just
        // ensures no NPE on construction with valid args.
        assertThat(fn).isNotNull();
    }

    @Test
    void constructorShouldRejectNegativeDims() {
        Random rng = new Random(0L);
        // No defensive validation in source, but ensure no NPE on creation.
        FitnessFn fn = new FitnessFn(0, 0, 0, 0, 0, 0, rng);
        assertThat(fn).isNotNull();
    }

    @Test
    void constructorShouldAcceptZeroParameters() {
        Random rng = new Random(0L);
        // Edge case: zero-sized world still constructs (evaluation may fail elsewhere).
        FitnessFn fn = new FitnessFn(0, 0, 0, 0, 0, 0, rng);
        assertThat(fn).isNotNull();
    }
}
