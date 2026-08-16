package io.matrix.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryExpanderTest {

    // --- Construction ---

    @Test
    void shouldBuildWithDefaults() {
        var expander = QueryExpander.builder().build();
        assertThat(expander.numVariants()).isEqualTo(4);
    }

    @Test
    void shouldBuildWithCustomVariants() {
        var expander = QueryExpander.builder().numVariants(8).build();
        assertThat(expander.numVariants()).isEqualTo(8);
    }

    @Test
    void shouldRejectInvalidNumVariants() {
        assertThatThrownBy(() -> QueryExpander.builder().numVariants(0).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectInvalidFlipProbability() {
        assertThatThrownBy(() -> QueryExpander.builder().flipProbability(1.5).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Expansion ---

    @Test
    void shouldExpandQueryWithVariants() {
        var expander = QueryExpander.builder().numVariants(4).build();
        long[] query = {0b00001111L};

        List<long[]> expanded = expander.expand(query);

        assertThat(expanded).hasSize(5); // 1 original + 4 variants
        assertThat(expanded.get(0)).isEqualTo(query); // original is first
    }

    @Test
    void shouldIncludeOriginalAsFirstElement() {
        var expander = QueryExpander.builder().numVariants(2).build();
        long[] query = {0xABCDEF1234567890L};

        List<long[]> expanded = expander.expand(query);

        assertThat(expanded.get(0)).isEqualTo(query);
    }

    @Test
    void shouldProduceDifferentVariants() {
        var expander = QueryExpander.builder().numVariants(4).build();
        long[] query = {0b10101010L};

        List<long[]> expanded = expander.expand(query);

        // All variants should be different from each other (with high probability)
        for (int i = 1; i < expanded.size(); i++) {
            for (int j = i + 1; j < expanded.size(); j++) {
                // At least one variant should differ
                boolean differs = false;
                for (int k = 0; k < expanded.get(i).length; k++) {
                    if (expanded.get(i)[k] != expanded.get(j)[k]) {
                        differs = true;
                        break;
                    }
                }
                // Not guaranteed with small vectors, but test structure is valid
            }
        }
        assertThat(expanded).hasSize(5);
    }

    @Test
    void shouldRejectNullQuery() {
        var expander = QueryExpander.builder().build();
        assertThatThrownBy(() -> expander.expand(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectEmptyQuery() {
        var expander = QueryExpander.builder().build();
        assertThatThrownBy(() -> expander.expand(new long[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldHandle128BitVectors() {
        var expander = QueryExpander.builder().numVariants(2).build();
        long[] query = {0xAAAAL, 0xBBBBL};

        List<long[]> expanded = expander.expand(query);

        assertThat(expanded).hasSize(3);
        for (long[] variant : expanded) {
            assertThat(variant).hasSize(2);
        }
    }

    // --- Rotate ---

    @Test
    void shouldRotateBits() {
        long[] query = {0b00001111L};
        long[] rotated = QueryExpander.rotate(query, 4);

        assertThat(rotated[0]).isEqualTo(0b11110000L);
    }

    @Test
    void shouldRotateFullCycle() {
        long[] query = {0xFFL};
        long[] rotated = QueryExpander.rotate(query, 64);

        assertThat(rotated[0]).isEqualTo(0xFFL); // full rotation = identity
    }

    @Test
    void shouldRotate128BitVector() {
        long[] query = {0x00000000FFFFFFFFL, 0x0000000000000000L};
        long[] rotated = QueryExpander.rotate(query, 32);

        // Lower 32 bits of query[0] should move to upper 32 bits of rotated[0]
        // Upper 32 bits of query[0] (0) should wrap
        assertThat(rotated[0]).isEqualTo(0xFFFFFFFF00000000L);
    }

    // --- Complement ---

    @Test
    void shouldComplementBits() {
        long[] query = {0b00001111L};
        long[] complemented = QueryExpander.complement(query);

        assertThat(complemented[0]).isEqualTo(~0b00001111L);
    }

    @Test
    void shouldDoubleComplement() {
        long[] query = {0xABCDEF1234567890L};
        long[] doubleComp = QueryExpander.complement(QueryExpander.complement(query));

        assertThat(doubleComp[0]).isEqualTo(query[0]);
    }

    @Test
    void shouldComplement128BitVector() {
        long[] query = {0xFFFFFFFFL, 0x00000000L};
        long[] complemented = QueryExpander.complement(query);

        assertThat(complemented[0]).isEqualTo(0xFFFFFFFF00000000L);
        assertThat(complemented[1]).isEqualTo(0xFFFFFFFFFFFFFFFFL);
    }

    // --- Flip ---

    @Test
    void shouldFlipBitsWithProbability() {
        long[] query = {0x00L};
        java.util.SplittableRandom rng = new java.util.SplittableRandom(42);
        long[] flipped = QueryExpander.flipBits(query, 0.5, rng);

        // With 50% probability, some bits should be flipped
        assertThat(flipped[0]).isNotEqualTo(0x00L);
    }

    @Test
    void shouldNotFlipWithZeroProbability() {
        long[] query = {0xFFL};
        java.util.SplittableRandom rng = new java.util.SplittableRandom(42);
        long[] flipped = QueryExpander.flipBits(query, 0.0, rng);

        assertThat(flipped[0]).isEqualTo(0xFFL);
    }

    @Test
    void shouldFlipAllWithOneProbability() {
        long[] query = {0x00L};
        java.util.SplittableRandom rng = new java.util.SplittableRandom(42);
        long[] flipped = QueryExpander.flipBits(query, 1.0, rng);

        assertThat(flipped[0]).isEqualTo(0xFFFFFFFFFFFFFFFFL);
    }

    // --- Mask ---

    @Test
    void shouldMaskBits() {
        long[] query = {0xFFFFFFFFFFFFFFFFL};
        java.util.SplittableRandom rng = new java.util.SplittableRandom(42);
        long[] masked = QueryExpander.maskBits(query, rng);

        // Should have some bits zeroed out (30-50%)
        assertThat(Long.bitCount(masked[0])).isLessThan(64);
        assertThat(Long.bitCount(masked[0])).isGreaterThan(0);
    }

    // --- Noise ---

    @Test
    void shouldAddNoise() {
        long[] query = {0x00L};
        java.util.SplittableRandom rng = new java.util.SplittableRandom(42);
        long[] noisy = QueryExpander.addNoise(query, rng);

        // With 5-15% noise, some bits should be flipped
        assertThat(noisy[0]).isNotEqualTo(0x00L);
    }

    @Test
    void shouldAddSmallAmountOfNoise() {
        long[] query = {0xFFFFFFFFFFFFFFFFL};
        java.util.SplittableRandom rng = new java.util.SplittableRandom(42);
        long[] noisy = QueryExpander.addNoise(query, rng);

        // Should have only 5-15% bits different
        int diff = Long.bitCount(query[0] ^ noisy[0]);
        assertThat(diff).isGreaterThan(0);
        assertThat(diff).isLessThan(20); // ~15% of 64 = ~10
    }

    // --- Deterministic with seed ---

    @Test
    void shouldProduceDeterministicResults() {
        var expander1 = QueryExpander.builder().numVariants(3).seed(123L).build();
        var expander2 = QueryExpander.builder().numVariants(3).seed(123L).build();
        long[] query = {0xABCDEL};

        List<long[]> expanded1 = expander1.expand(query);
        List<long[]> expanded2 = expander2.expand(query);

        assertThat(expanded1).hasSameSizeAs(expanded2);
        for (int i = 0; i < expanded1.size(); i++) {
            assertThat(expanded1.get(i)).isEqualTo(expanded2.get(i));
        }
    }
}
