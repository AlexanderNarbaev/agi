package io.matrix.api;

import java.util.concurrent.atomic.AtomicLong;

/**
 * RUN 81 — OnnxChainEnsemble: combine boolean chain scores with ONNX
 * logits for hybrid inference.
 *
 * <p>Idea: rather than choosing between the chain and Qwen, blend them:
 * <ul>
 *   <li>Compute chain "logits" (per-token scores from chain neurons)</li>
 *   <li>Compute Qwen logits (real ONNX forward pass)</li>
 *   <li>Combine with weights: {@code alpha * Qwen + (1-alpha) * Chain}</li>
 *   <li>Sample/argmax from the combined distribution</li>
 * </ul>
 *
 * <p>This is a RESEARCH-grade path. Production should still use one or
 * the other. The ensemble is mostly useful for ablation experiments.
 */
public final class OnnxChainEnsemble {

    private final QwenOnnxBridge onnx;
    private final double alpha;  // 0.0 = pure chain, 1.0 = pure ONNX
    private final int vocabSize;
    private final AtomicLong chainCalls = new AtomicLong();
    private final AtomicLong onnxCalls = new AtomicLong();

    public OnnxChainEnsemble(QwenOnnxBridge onnx, double alpha, int vocabSize) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("alpha must be in [0.0, 1.0]");
        }
        this.onnx = onnx;
        this.alpha = alpha;
        this.vocabSize = vocabSize;
    }

    public double alpha() { return alpha; }
    public long chainCalls() { return chainCalls.get(); }
    public long onnxCalls() { return onnxCalls.get(); }
    public int vocabSize() { return vocabSize; }

    /**
     * Combine Qwen logits with chain-derived "logits" and return the
     * argmax over the combined distribution.
     *
     * <p>The chain contribution is a fixed uniform bias when no chain
     * is configured; in production you'd wire a real ChainScorer here.
     *
     * @param onnxLogits last-position logits from Qwen
     * @return argmax of the combined distribution
     */
    public int combineAndArgmax(float[] onnxLogits) {
        onnxCalls.incrementAndGet();
        if (onnxLogits.length != vocabSize) {
            throw new IllegalArgumentException(
                    "logits length " + onnxLogits.length
                            + " != vocabSize " + vocabSize);
        }
        if (alpha >= 1.0) {
            // pure ONNX
            return argmax(onnxLogits);
        }
        if (alpha <= 0.0) {
            // pure chain - just return a token id (uniform random not deterministic)
            chainCalls.incrementAndGet();
            return 0;  // default to token 0 (placeholder)
        }
        // Mix: alpha * onnxLogits + (1-alpha) * chainBias
        float[] chainBias = new float[vocabSize];
        // In real production, this would query chain neurons.
        // For now, leave at 0 and rely on the onnx logit.
        // The bias keeps the math symmetric.
        chainCalls.incrementAndGet();

        float[] combined = new float[vocabSize];
        for (int i = 0; i < vocabSize; i++) {
            combined[i] = (float) (alpha * onnxLogits[i]
                    + (1.0 - alpha) * chainBias[i]);
        }
        return argmax(combined);
    }

    private static int argmax(float[] arr) {
        int bestIdx = 0;
        float bestVal = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > bestVal) {
                bestVal = arr[i];
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /** Reset call counters (for testing or session rollover). */
    public void resetCounters() {
        chainCalls.set(0);
        onnxCalls.set(0);
    }
}
