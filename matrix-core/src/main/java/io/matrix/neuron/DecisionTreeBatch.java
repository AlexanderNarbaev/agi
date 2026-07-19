package io.matrix.neuron;

import java.util.BitSet;

/**
 * Batch evaluator for {@link DecisionTree} — processes many inputs at once,
 * avoiding method-call overhead per evaluation.
 *
 * <p>This is the SIMD-friendly counterpart to {@link DecisionTree#evaluate(BitSet)}:
 * instead of doing one tree-walk per input, we do many tree-walks in a tight
 * loop, allowing the JIT to auto-vectorise.
 *
 * <p>Ref: L1 §5.
 */
public final class DecisionTreeBatch {

    private DecisionTreeBatch() {}

    /**
     * Evaluates {@code tree} against every input in {@code inputs}.
     *
     * @return packed 64-bit result: bit {@code i} = {@code tree.evaluate(inputs[i])}
     * @throws IllegalArgumentException if {@code inputs.length > 64}
     */
    public static long evaluateAll64(DecisionTree tree, BitSet[] inputs) {
        if (tree == null) throw new IllegalArgumentException("tree required");
        if (inputs == null) throw new IllegalArgumentException("inputs required");
        if (inputs.length > 64) {
            throw new IllegalArgumentException("evaluateAll64 supports up to 64 inputs per call");
        }
        long result = 0L;
        for (int i = 0; i < inputs.length; i++) {
            if (tree.evaluate(inputs[i])) result |= (1L << i);
        }
        return result;
    }

    /**
     * Variant: input bits packed into a {@code long[]} of length {@code k}.
     * Bit {@code j} of {@code inputWords[i]} is the {@code j}-th feature
     * for the {@code i}-th input.
     */
    public static long evaluateAll64FromLongs(DecisionTree tree, long[][] inputWords, int k) {
        if (k < 1 || k > 63) {
            throw new IllegalArgumentException("k must be in [1, 63], got " + k);
        }
        if (inputWords.length > 64) {
            throw new IllegalArgumentException("supports up to 64 inputs per call");
        }
        long result = 0L;
        for (int i = 0; i < inputWords.length; i++) {
            BitSet bs = new BitSet(k);
            for (int j = 0; j < k; j++) {
                if ((inputWords[i][j >>> 6] & (1L << (j & 63))) != 0) bs.set(j);
            }
            if (tree.evaluate(bs)) result |= (1L << i);
        }
        return result;
    }

    /**
     * Count the {@code true} outputs over a batch — useful for sampling-based
     * evaluations.
     */
    public static int trueCount(DecisionTree tree, BitSet[] inputs) {
        return Long.bitCount(evaluateAll64(tree, inputs));
    }
}
