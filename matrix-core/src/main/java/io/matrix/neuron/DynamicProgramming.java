package io.matrix.neuron;

import java.util.Arrays;

/**
 * RUN 434 — Classic DP & greedy algorithms.
 * <ul>
 *   <li>{@link #longestIncreasingSubsequence} — O(n log n) patience sort method</li>
 *   <li>{@link #knapsack} — 0/1 knapsack DP, O(n·W) time + O(W) space (1D)</li>
 *   <li>{@link #subsetSum} — variant for boolean reachability</li>
 *   <li>{@link #activitySelection} — earliest-finishing-time greedy, O(n log n)</li>
 *   <li>{@link #intervalSchedulingMax} — alternative interval-scheduling DP</li>
 * </ul>
 * CONSTITUTION I-safe: no Random, no wall-clock.
 */
public final class DynamicProgramming {

    private DynamicProgramming() {}

    /** Longest strictly-increasing subsequence length. */
    public static int longestIncreasingSubsequence(int[] a) {
        int[] tails = new int[a.length];
        int size = 0;
        for (int x : a) {
            int lo = 0, hi = size;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (tails[mid] < x) lo = mid + 1;
                else hi = mid;
            }
            tails[lo] = x;
            if (lo == size) size++;
        }
        return size;
    }

    /**
     * 0/1 Knapsack with weight and value arrays; capacity W. Maximises total value.
     * Returns the max total value, not the subset itself.
     */
    public static int knapsack(int[] weights, int[] values, int capacity) {
        int n = weights.length;
        int[] dp = new int[capacity + 1];
        for (int i = 0; i < n; i++) {
            for (int w = capacity; w >= weights[i]; w--) {
                dp[w] = Math.max(dp[w], dp[w - weights[i]] + values[i]);
            }
        }
        int max = 0;
        for (int v : dp) if (v > max) max = v;
        return max;
    }

    /** Subset sum: can any subset sum to target? Returns true if reachable. */
    public static boolean subsetSum(int[] nums, int target) {
        boolean[] dp = new boolean[target + 1];
        dp[0] = true;
        for (int x : nums) {
            for (int t = target; t >= x; t--) {
                if (dp[t - x]) dp[t] = true;
            }
        }
        return dp[target];
    }

    /** Activity selection: max number of non-overlapping intervals. */
    public record Interval(int start, int end) {}

    public static int activitySelection(Interval[] intervals) {
        Arrays.sort(intervals, (a, b) -> Integer.compare(a.end, b.end));
        int count = 0;
        int lastEnd = Integer.MIN_VALUE;
        for (Interval i : intervals) {
            if (i.start >= lastEnd) { count++; lastEnd = i.end; }
        }
        return count;
    }

    /** Interval scheduling via DP (Bellman-Ford-style over intervals). */
    public static int intervalSchedulingMax(Interval[] intervals) {
        Arrays.sort(intervals, (a, b) -> Integer.compare(a.end, b.end));
        int n = intervals.length;
        int[] dp = new int[n];
        for (int i = 0; i < n; i++) dp[i] = 1;
        for (int i = 1; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (intervals[j].end <= intervals[i].start) {
                    dp[i] = Math.max(dp[i], dp[j] + 1);
                }
            }
        }
        int max = 0;
        for (int v : dp) if (v > max) max = v;
        return max;
    }
}
