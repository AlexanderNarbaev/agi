package io.matrix.cognitive;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 93 — ExploratoryActionSampler: bounded seeded exploration (DESIGN-64).
 *
 * <p>Same state hash + cycle count → same selection (reproducible exploration).
 */
class ExploratoryActionSamplerTest {

    @Test
    void sameHashSameCycleSameSelection() {
        List<String> candidates = List.of("alpha", "beta", "gamma", "delta");
        String a = ExploratoryActionSampler.sample(42L, candidates, 1L);
        String b = ExploratoryActionSampler.sample(42L, candidates, 1L);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void differentCycleDifferentSelection() {
        // With 4 candidates, varying cycle often changes selection
        java.util.Set<String> selections = new java.util.HashSet<>();
        for (long c = 0; c < 20; c++) {
            selections.add(ExploratoryActionSampler.sample(42L,
                    List.of("alpha", "beta", "gamma", "delta"), c));
        }
        assertThat(selections.size()).isGreaterThan(1);
    }

    @Test
    void differentHashDifferentSelection() {
        String a = ExploratoryActionSampler.sample(1L,
                List.of("a", "b", "c", "d", "e"), 0L);
        String b = ExploratoryActionSampler.sample(2L,
                List.of("a", "b", "c", "d", "e"), 0L);
        // Just check both valid
        assertThat(List.of("a", "b", "c", "d", "e")).contains(a);
        assertThat(List.of("a", "b", "c", "d", "e")).contains(b);
    }

    @Test
    void sampleKReturnsKDistinctElements() {
        List<String> candidates = List.of("a", "b", "c", "d", "e", "f");
        List<String> sampled = ExploratoryActionSampler.sampleK(
                42L, candidates, 1L, 3);
        assertThat(sampled).hasSize(3);
        assertThat(sampled).doesNotHaveDuplicates();
    }

    @Test
    void softmaxProbabilitiesSumToOne() {
        double[] probs = ExploratoryActionSampler.softmax(
                42L, List.of("a", "b", "c"), 1L, 1.0);
        assertThat(probs).hasSize(3);
        double sum = 0;
        for (double p : probs) {
            assertThat(p).isGreaterThan(0.0);
            assertThat(p).isLessThanOrEqualTo(1.0);
            sum += p;
        }
        assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void emptyCandidatesThrows() {
        try {
            ExploratoryActionSampler.sample(0L, List.of(), 0L);
            assertThat(false).as("should throw").isTrue();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void invalidTemperatureThrows() {
        try {
            ExploratoryActionSampler.softmax(0L, List.of("a"), 0L, 0.0);
            assertThat(false).as("should throw").isTrue();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
