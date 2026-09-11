package io.matrix.neuron;

import java.util.Random;

/**
 * RUN 424 — Classic sorting algorithms (in-place or O(n log n)). Pure functions.
 * <ul>
 *   <li>{@link #quickSort} — quicksort with Lomuto partition, stable for distinct keys</li>
 *   <li>{@link #mergeSort} — top-down recursive merge sort, stable, O(n log n) guaranteed</li>
 *   <li>{@link #heapSort} — in-place binary-heap sort, O(n log n) worst case</li>
 *   <li>{@link #fisherYatesShuffle} — unbiased in-place permutation</li>
 * </ul>
 * CONSTITUTION I-safe: shuffler takes {@link Random} argument.
 */
public final class Sort {

    private Sort() {}

    public static void quickSort(int[] a) {
        if (a.length > 1) quickSortRec(a, 0, a.length - 1);
    }

    private static void quickSortRec(int[] a, int lo, int hi) {
        if (lo >= hi) return;
        int pivot = a[(lo + hi) >>> 1];
        int i = lo - 1, j = hi + 1;
        while (true) {
            do { i++; } while (a[i] < pivot);
            do { j--; } while (a[j] > pivot);
            if (i >= j) break;
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
        quickSortRec(a, lo, j);
        quickSortRec(a, j + 1, hi);
    }

    public static void mergeSort(int[] a) {
        if (a.length < 2) return;
        int[] buf = new int[a.length];
        mergeSortRec(a, buf, 0, a.length - 1);
    }

    private static void mergeSortRec(int[] a, int[] buf, int lo, int hi) {
        if (lo >= hi) return;
        int mid = (lo + hi) >>> 1;
        mergeSortRec(a, buf, lo, mid);
        mergeSortRec(a, buf, mid + 1, hi);
        // merge
        System.arraycopy(a, lo, buf, lo, hi - lo + 1);
        int i = lo, j = mid + 1;
        for (int k = lo; k <= hi; k++) {
            if (i > mid) a[k] = buf[j++];
            else if (j > hi) a[k] = buf[i++];
            else if (buf[i] <= buf[j]) a[k] = buf[i++];
            else a[k] = buf[j++];
        }
    }

    public static void heapSort(int[] a) {
        int n = a.length;
        // Sift-down heapify
        for (int i = (n >>> 1) - 1; i >= 0; i--) sift(a, i, n - 1);
        // Repeatedly move root to end and shrink heap
        for (int end = n - 1; end > 0; end--) {
            int t = a[0]; a[0] = a[end]; a[end] = t;
            sift(a, 0, end - 1);
        }
    }

    private static void sift(int[] a, int root, int end) {
        while (true) {
            int left = 2 * root + 1;
            if (left > end) break;
            int right = left + 1;
            int largest = (right <= end && a[right] > a[left]) ? right : left;
            if (a[root] >= a[largest]) break;
            int t = a[root]; a[root] = a[largest]; a[largest] = t;
            root = largest;
        }
    }

    /** Fisher-Yates shuffle (Knuth). Returns the mutated array. */
    public static void fisherYatesShuffle(int[] a, Random rng) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }
}
