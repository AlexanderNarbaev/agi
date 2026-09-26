package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BloomFilterTest {

    @Test
    void added_items_are_always_detected() {
        BloomFilter bf = new BloomFilter(1000, 0.01);
        Set<String> added = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String item = "key-" + i;
            bf.add(item);
            added.add(item);
        }
        for (String item : added) {
            assertThat(bf.mightContain(item)).isTrue();
        }
    }

    @Test
    void false_positive_rate_is_bounded() {
        BloomFilter bf = new BloomFilter(100, 0.05);
        for (int i = 0; i < 100; i++) bf.add("known-" + i);

        // Test that 1000 "unknown" items have < 15% false positives (loose bound)
        int fps = 0;
        for (int i = 0; i < 1000; i++) {
            if (bf.mightContain("unknown-" + i)) fps++;
        }
        assertThat(fps).isLessThan(150);
    }

    @Test
    void empty_filter_never_matches() {
        BloomFilter bf = new BloomFilter(100, 0.01);
        assertThat(bf.mightContain("anything")).isFalse();
    }

    @Test
    void null_input_safe() {
        BloomFilter bf = new BloomFilter(100, 0.01);
        // null input is treated as "no input" -> never matches.
        // (The add(null) is also a no-op.)
        bf.add(null);
        assertThat(bf.mightContain(null)).isFalse();
        // Non-null input with empty string should still work.
        bf.add("");
        assertThat(bf.mightContain("")).isTrue();
    }

    @Test
    void sizing_is_reasonable_for_default_fpr() {
        BloomFilter bf = new BloomFilter(1000, 0.01);
        assertThat(bf.numBits()).isGreaterThan(8000);  // ~10 bits/item
        assertThat(bf.numHashes()).isBetween(5, 10);
    }
}
