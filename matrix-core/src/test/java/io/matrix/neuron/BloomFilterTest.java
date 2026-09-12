package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BloomFilterTest {

    @Test
    void constructorRejectsBadArgs() {
        assertThatThrownBy(() -> new BloomFilter(0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BloomFilter(1024, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BloomFilter(-1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newlyAddedKeyMayBePresent() {
        BloomFilter bf = new BloomFilter(1024, 3);
        bf.add(42L);
        assertThat(bf.mightContain(42L)).isTrue();
    }

    @Test
    void notAddedKeyIsLikelyAbsent() {
        BloomFilter bf = new BloomFilter(1024, 3);
        bf.add(42L);
        // Other keys should not be in the filter (with high probability)
        boolean anyFalsePositive = false;
        for (long i = 1; i <= 1000; i++) {
            if (i != 42L && bf.mightContain(i)) {
                anyFalsePositive = true;
                break;
            }
        }
        // For 1 add in 1024 bits with k=3, FPR is very low; allow occasional
        assertThat(anyFalsePositive).isFalse();
    }

    @Test
    void multipleAddsStillRetrieveAll() {
        BloomFilter bf = new BloomFilter(1024, 3);
        for (long i = 0; i < 100; i++) bf.add(i);
        for (long i = 0; i < 100; i++) {
            assertThat(bf.mightContain(i)).isTrue();
        }
    }

    @Test
    void falsePositiveRateIncreasesWithSaturation() {
        BloomFilter bf = new BloomFilter(128, 3);
        // Fill to high saturation
        for (long i = 0; i < 100; i++) bf.add(i);
        int falsePositives = 0;
        int trials = 1000;
        for (long i = 10000; i < 10000 + trials; i++) {
            if (bf.mightContain(i)) falsePositives++;
        }
        // At ~78% saturation with k=3, expect significant FPR (>5%)
        double fpr = (double) falsePositives / trials;
        assertThat(fpr).isGreaterThan(0.05);
    }

    @Test
    void cardinalityReturnsNonZeroForFilledFilter() {
        BloomFilter bf = new BloomFilter(1024, 3);
        bf.add(42L);
        int card = bf.cardinality();
        assertThat(card).isEqualTo(3); // k hash functions set 3 bits
    }

    @Test
    void cardinalityIsZeroForEmptyFilter() {
        BloomFilter bf = new BloomFilter(1024, 3);
        assertThat(bf.cardinality()).isEqualTo(0);
    }

    @Test
    void bitsetReturnsNonNullBitSet() {
        BloomFilter bf = new BloomFilter(1024, 3);
        bf.add(42L);
        BitSet bits = bf.bitset();
        assertThat(bits).isNotNull();
        assertThat(bits.cardinality()).isEqualTo(3);
    }

    @Test
    void multipleAddsMayShareBits() {
        BloomFilter bf = new BloomFilter(1024, 3);
        bf.add(42L);
        bf.add(43L);
        // Total bits set may be ≤ 6 (sharing if hash collisions)
        assertThat(bf.cardinality()).isBetween(3, 6);
    }
}
