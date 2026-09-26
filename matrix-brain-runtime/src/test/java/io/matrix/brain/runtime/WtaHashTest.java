package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRUE-W13 — Sparse-HDC tests.
 *
 * <p>Verifies WTA hashing correctness, determinism, and basic
 * similarity-preservation properties needed by the mind runtime.</p>
 */
class WtaHashTest {

    @Test
    void wta_hash_produces_exactly_k_set_bits() {
        WtaHash h = new WtaHash(256, 16);
        int[] bits = h.hashToken("hello");
        assertThat(bits.length).isEqualTo(16);
        Set<Integer> unique = new HashSet<>();
        for (int b : bits) {
            assertThat(b).isBetween(0, 255);
            unique.add(b);
        }
        assertThat(unique.size()).isEqualTo(16);
    }

    @Test
    void wta_hash_is_deterministic() {
        WtaHash h1 = new WtaHash(256, 16);
        WtaHash h2 = new WtaHash(256, 16);
        int[] a = h1.hashToken("MATRIX");
        int[] b = h2.hashToken("MATRIX");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void wta_hash_different_tokens_overlap_partially() {
        WtaHash h = new WtaHash(256, 64);
        int[] a = h.hashToken("king");
        int[] b = h.hashToken("queen");
        int overlap = 0;
        Set<Integer> setA = new HashSet<>();
        for (int x : a) setA.add(x);
        for (int x : b) if (setA.contains(x)) overlap++;
        // Different tokens should have some overlap but not be identical
        assertThat(overlap).isBetween(0, 50);
    }

    @Test
    void wta_phrase_combines_token_codes() {
        WtaHash h = new WtaHash(256, 16);
        int[] phrase = h.hashPhrase(new String[]{"hello", "world"});
        assertThat(phrase.length).isEqualTo(16);
    }

    @Test
    void wta_different_k_changes_sparsity() {
        WtaHash h16 = new WtaHash(256, 16);
        WtaHash h64 = new WtaHash(256, 64);
        assertThat(h16.hashToken("x").length).isEqualTo(16);
        assertThat(h64.hashToken("x").length).isEqualTo(64);
    }

    @Test
    void wta_hash_produces_more_unique_codes_than_random_collisions() {
        // Sanity: 1000 distinct tokens with D=4096, K=32 should yield at least
        // 500 unique codes (the rest can collide via birthday paradox). If WTA
        // is broken (always same top-k), we'd see 1 unique.
        WtaHash h = new WtaHash(4096, 32);
        Set<Integer> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            int[] bits = h.hashToken("token_" + i);
            codes.add(java.util.Arrays.hashCode(bits));
        }
        assertThat(codes.size()).isGreaterThan(500);
    }

    @Test
    void wta_constructor_validates_inputs() {
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new WtaHash(0, 5))).isNotNull();
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new WtaHash(10, 0))).isNotNull();
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new WtaHash(10, 11))).isNotNull();
    }

    private static int hashBits(int[] bits) {
        return java.util.Arrays.hashCode(bits);
    }
}
