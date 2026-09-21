package io.matrix.research;

import io.matrix.neuron.Sort;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 424 — Coverage for sorting utilities.
 */
class Exp424SortTest {

    @Test
    void quickSortSortsRandomInput() {
        int[] xs = {5, 3, 1, 4, 2, 8, 7, 6, 0, -1};
        Sort.quickSort(xs);
        assertThat(xs).containsExactly(-1, 0, 1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void mergeSortHandlesDuplicateKeys() {
        int[] xs = {4, 2, 4, 1, 4, 3, 2};
        Sort.mergeSort(xs);
        assertThat(xs).containsExactly(1, 2, 2, 3, 4, 4, 4);
    }

    @Test
    void heapSortSortsAdversarial() {
        int[] xs = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        Sort.heapSort(xs);
        assertThat(xs).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        // Already-sorted is the worst case for naive heap-sort under many sifts;
        // tested via reverse order next.
        int[] ys = {10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
        Sort.heapSort(ys);
        assertThat(ys).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    void allSortsAgreeOnLargeRandomArray() {
        int[] orig = new int[1000];
        Random rng = new Random(0xCAFE);
        for (int i = 0; i < 1000; i++) orig[i] = rng.nextInt(10000) - 5000;

        int[] a1 = Arrays.copyOf(orig, orig.length);
        int[] a2 = Arrays.copyOf(orig, orig.length);
        int[] a3 = Arrays.copyOf(orig, orig.length);
        Sort.quickSort(a1);
        Sort.mergeSort(a2);
        Sort.heapSort(a3);
        int[] expected = Arrays.copyOf(orig, orig.length);
        Arrays.sort(expected);
        assertThat(a1).containsExactly(expected);
        assertThat(a2).containsExactly(expected);
        assertThat(a3).containsExactly(expected);
    }

    @Test
    void fisherYatesProducesPermutation() {
        int[] xs = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[] orig = Arrays.copyOf(xs, xs.length);
        // Try multiple seeds to find one that changes the order
        boolean foundDifferentOrder = false;
        for (long seed = 0xCAFE; seed < 0xCAFE + 20; seed++) {
            Sort.fisherYatesShuffle(xs, new Random(seed));
            // Same multiset of values
            assertThat(xs).containsExactlyInAnyOrder(orig);
            if (!Arrays.equals(xs, orig)) { foundDifferentOrder = true; break; }
        }
        assertThat(foundDifferentOrder)
                .as("Fisher-Yates should produce different orderings for some seed")
                .isTrue();
    }

    @Test
    void fisherYatesIsUnbiasedAveragePositionDistribution() {
        // Verify that position 0 is visited roughly equally often across 10000 shuffles.
        int n = 4;
        int trials = 10000;
        int[] zeroCountByValue = new int[n];  // which value lands at index 0
        for (int t = 0; t < trials; t++) {
            int[] xs = {0, 1, 2, 3};
            Sort.fisherYatesShuffle(xs, new Random(t * 31L));
            zeroCountByValue[xs[0]]++;
        }
        // Each value should appear ~2500 times — check within ±10%
        for (int c : zeroCountByValue) {
            assertThat(c).isBetween(2200, 2800);
        }
    }
}
