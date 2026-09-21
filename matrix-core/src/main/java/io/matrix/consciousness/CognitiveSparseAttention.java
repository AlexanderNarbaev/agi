package io.matrix.consciousness;

import java.util.List;
import java.util.Random;

/**
 * W245 — Cognitive Sparse Attention (BigBird/Longformer style).
 *
 * <p>Inspired by BigBird (Zaheer et al. 2020) and Longformer (Beltagy
 * et al. 2020). Sparse attention patterns:
 * - Local window attention (neighbors)
 * - Random global attention (sparse)
 * - All-global attention (sinks)
 *
 * <p>Reduces complexity from O(N²) to O(N).
 *
 * <p>CONSTITUTION VI compliance: sparse cognitive attention, not
 * phenomenal consciousness claim.
 *
 * <p>CONSTITUTION I v3 compliance: all Random seeded.
 */
public final class CognitiveSparseAttention {

    private final int windowSize;
    private final int numGlobalTokens;
    private final int numRandomTokens;
    private final long seed;

    public CognitiveSparseAttention(int windowSize, int numGlobalTokens,
                                       int numRandomTokens, long seed) {
        if (windowSize < 1) throw new IllegalArgumentException("windowSize must be >= 1");
        if (numGlobalTokens < 0) throw new IllegalArgumentException("numGlobalTokens must be >= 0");
        if (numRandomTokens < 0) throw new IllegalArgumentException("numRandomTokens must be >= 0");
        this.windowSize = windowSize;
        this.numGlobalTokens = numGlobalTokens;
        this.numRandomTokens = numRandomTokens;
        this.seed = seed;
    }

    /**
     * Compute sparse attention mask for token at position i.
     * Returns positions that token i attends to.
     */
    public int[] attentionMask(int position, int sequenceLength) {
        if (position < 0 || position >= sequenceLength) return new int[0];
        java.util.List<Integer> positions = new java.util.ArrayList<>();
        // Local window
        int start = Math.max(0, position - windowSize / 2);
        int end = Math.min(sequenceLength, position + windowSize / 2 + 1);
        for (int i = start; i < end; i++) positions.add(i);
        // Global tokens (first numGlobalTokens)
        for (int i = 0; i < numGlobalTokens && i < sequenceLength; i++) {
            if (!positions.contains(i)) positions.add(i);
        }
        // Random tokens
        Random rng = new Random(seed + position);
        java.util.Set<Integer> seen = new java.util.HashSet<>(positions);
        int added = 0;
        while (added < numRandomTokens && positions.size() < sequenceLength) {
            int idx = rng.nextInt(sequenceLength);
            if (seen.add(idx)) {
                positions.add(idx);
                added++;
            }
        }
        int[] result = new int[positions.size()];
        for (int i = 0; i < positions.size(); i++) result[i] = positions.get(i);
        return result;
    }

    /**
     * Compute total attention operations (sum of mask sizes).
     */
    public int totalOps(int sequenceLength) {
        int total = 0;
        for (int i = 0; i < sequenceLength; i++) {
            total += attentionMask(i, sequenceLength).length;
        }
        return total;
    }

    /**
     * Compare to dense O(N²) complexity.
     */
    public double sparsityRatio(int sequenceLength) {
        if (sequenceLength <= 0) return 0;
        int dense = sequenceLength * sequenceLength;
        int sparse = totalOps(sequenceLength);
        return (double) sparse / dense;
    }

    public int windowSize() { return windowSize; }
    public int numGlobalTokens() { return numGlobalTokens; }
    public int numRandomTokens() { return numRandomTokens; }
    public long seed() { return seed; }
}
