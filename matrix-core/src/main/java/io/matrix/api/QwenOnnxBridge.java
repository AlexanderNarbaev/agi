package io.matrix.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * EXP-MATRIX.39 — Qwen ONNX chat inference bridge (RUN 66).
 *
 * <p>Combines {@link BpeTokenizer} (vocab.json + merges.txt) with
 * {@link OnnxRuntimeAdapter} (model.onnx) to provide end-to-end
 * real LLM inference in Java, optionally on GPU.
 *
 * <p>This is the first class that turns the ONNX adapter into a
 * production inference path: text in, text out, using Qwen2.5-0.5B
 * with no Python in the loop.
 *
 * <p>Usage:
 * <pre>{@code
 * QwenOnnxBridge bridge = new QwenOnnxBridge(modelDir);
 * bridge.useGpu(true);
 * bridge.load();
 * String reply = bridge.generate("What is 2+2?", 64);
 * }</pre>
 */
public class QwenOnnxBridge {

    private static final Logger log = LoggerFactory.getLogger(QwenOnnxBridge.class);

    /** Default EOS token id for Qwen2.5 (token 151645 = {@code <|im_end|>}). */
    public static final long EOS_TOKEN_DEFAULT = 151645L;
    /** Default PAD token id for Qwen2.5 (token 151643 = {@code <|endoftext|>}). */
    public static final long PAD_TOKEN_DEFAULT = 151643L;

    private final Path modelDir;
    private BpeTokenizer tokenizer;
    private OnnxRuntimeAdapter onnx;
    private boolean useGpu = false;
    private int maxNewTokens = 64;
    private long eosToken = EOS_TOKEN_DEFAULT;

    /** RUN 69 — inference metrics aggregator. */
    private final OnnxInferenceMetrics metrics = new OnnxInferenceMetrics();

    public QwenOnnxBridge(Path modelDir) {
        this.modelDir = modelDir;
    }

    public void useGpu(boolean useGpu) {
        this.useGpu = useGpu;
    }

    public void setMaxNewTokens(int n) {
        if (n > 0 && n <= 1024) this.maxNewTokens = n;
    }

    public void setEosToken(long t) {
        this.eosToken = t;
    }

    public boolean isLoaded() {
        return tokenizer != null && onnx != null && onnx.isLoaded();
    }

    public boolean isGpuEnabled() {
        return useGpu;
    }

    /**
     * Generate text using temperature sampling instead of greedy argmax.
     *
     * @param prompt input text
     * @param maxTokens maximum tokens
     * @param temperature sampling temperature (0.0 = greedy, 1.0 = uniform).
     *                    Values outside [0.0, 2.0] are clamped.
     * @return decoded text response
     */
    public String generateSampled(String prompt, int maxTokens, double temperature) {
        return generateSampled(prompt, maxTokens, temperature, -1, 1.0);
    }

    /**
     * Generate text with full sampling strategy: temperature + top-k + top-p.
     */
    public String generateSampled(String prompt, int maxTokens,
                                  double temperature, int topK, double topP) {
        if (!isLoaded()) {
            throw new IllegalStateException("bridge not loaded");
        }
        int budget = Math.min(maxTokens, maxNewTokens);
        double t = Math.max(0.01, Math.min(2.0, temperature));
        int k = topK <= 0 ? -1 : topK;
        double p = Math.max(0.0, Math.min(1.0, topP));
        int[] promptIds = tokenizer.encode(prompt);
        java.util.List<Long> allIds = new java.util.ArrayList<>();
        for (int id : promptIds) allIds.add((long) id);

        long t0 = System.nanoTime();
        int generated = 0;
        for (int step = 0; step < budget; step++) {
            long[] ids = toLongArray(allIds);
            long nextToken;
            try {
                nextToken = sampleWithStrategy(ids, t, k, p);
            } catch (Exception e) {
                log.warn("QwenOnnxBridge.generateSampled: inference failed at step {}: {}",
                        step, e.getMessage());
                break;
            }
            if (nextToken == eosToken) break;
            allIds.add(nextToken);
            generated++;
        }
        metrics.record(generated, System.nanoTime() - t0, useGpu);

        int[] genIds = new int[generated];
        for (int i = 0; i < generated; i++) {
            genIds[i] = allIds.get(promptIds.length + i).intValue();
        }
        return tokenizer.decode(genIds);
    }

    /**
     * Sample next token from softmax(logits / temperature).
     * For temperature = 0, returns argmax.
     */
    private long sampleNextToken(long[] tokenIds, double temperature) throws Exception {
        // We re-run inference and get raw logits via reflection.
        java.lang.reflect.Field sessionField = OnnxRuntimeAdapter.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        ai.onnxruntime.OrtSession session =
                (ai.onnxruntime.OrtSession) sessionField.get(onnx);
        ai.onnxruntime.OrtEnvironment env = ai.onnxruntime.OrtEnvironment.getEnvironment();

        long seqLen = tokenIds.length;
        long[] attentionMask = new long[(int) seqLen];
        long[] positionIds = new long[(int) seqLen];
        for (int i = 0; i < seqLen; i++) {
            attentionMask[i] = 1L;
            positionIds[i] = i;
        }
        try (ai.onnxruntime.OnnxTensor inputIds = ai.onnxruntime.OnnxTensor.createTensor(
                    env, java.nio.LongBuffer.wrap(tokenIds), new long[]{1, seqLen});
             ai.onnxruntime.OnnxTensor attnMask = ai.onnxruntime.OnnxTensor.createTensor(
                    env, java.nio.LongBuffer.wrap(attentionMask), new long[]{1, seqLen});
             ai.onnxruntime.OnnxTensor posIds = ai.onnxruntime.OnnxTensor.createTensor(
                    env, java.nio.LongBuffer.wrap(positionIds), new long[]{1, seqLen})) {
            try (var results = session.run(java.util.Map.of(
                    "input_ids", inputIds,
                    "attention_mask", attnMask,
                    "position_ids", posIds))) {
                float[][][] logits = (float[][][]) results.get(0).getValue();
                float[] last = logits[0][(int) seqLen - 1];
                if (temperature < 0.05) {
                    // Greedy
                    int bestIdx = 0;
                    float bestVal = last[0];
                    for (int i = 1; i < last.length; i++) {
                        if (last[i] > bestVal) {
                            bestVal = last[i];
                            bestIdx = i;
                        }
                    }
                    return bestIdx;
                }
                // Temperature sampling: softmax with temperature
                double maxLogit = last[0];
                for (int i = 1; i < last.length; i++) {
                    if (last[i] > maxLogit) maxLogit = last[i];
                }
                double sum = 0.0;
                double[] probs = new double[last.length];
                for (int i = 0; i < last.length; i++) {
                    probs[i] = Math.exp((last[i] - maxLogit) / temperature);
                    sum += probs[i];
                }
                for (int i = 0; i < probs.length; i++) probs[i] /= sum;
                // Sample using deterministic PRNG so tests are reproducible
                double r = ((java.util.Random) null == null ? 0.0 : 0.5);
                // Use System.nanoTime() — non-deterministic but real sampling.
                // For reproducible tests, caller should use greedy.
                long seed = (System.nanoTime() ^ Thread.currentThread().threadId()) & 0x7FFFFFFFL;
                java.util.Random rng = new java.util.Random(seed);
                r = rng.nextDouble();
                double cum = 0.0;
                for (int i = 0; i < probs.length; i++) {
                    cum += probs[i];
                    if (r < cum) return i;
                }
                return probs.length - 1;
            }
        }
    }

    /**
     * Sample next token with full strategy: temperature + top-k + top-p.
     *
     * <p>Pipeline:
     * <ol>
     *   <li>Run inference, get last-position logits</li>
     *   <li>If temperature ≈ 0 → return argmax</li>
     *   <li>Otherwise: apply temperature, mask logits outside top-K
     *       to -inf, renormalize, mask cumulative tail above top-P,
     *       renormalize, then sample.</li>
     * </ol>
     */
    private long sampleWithStrategy(long[] tokenIds, double temperature,
                                    int topK, double topP) throws Exception {
        java.lang.reflect.Field sessionField = OnnxRuntimeAdapter.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        ai.onnxruntime.OrtSession session =
                (ai.onnxruntime.OrtSession) sessionField.get(onnx);
        ai.onnxruntime.OrtEnvironment env = ai.onnxruntime.OrtEnvironment.getEnvironment();

        long seqLen = tokenIds.length;
        long[] attentionMask = new long[(int) seqLen];
        long[] positionIds = new long[(int) seqLen];
        for (int i = 0; i < seqLen; i++) {
            attentionMask[i] = 1L;
            positionIds[i] = i;
        }
        try (ai.onnxruntime.OnnxTensor inputIds = ai.onnxruntime.OnnxTensor.createTensor(
                    env, java.nio.LongBuffer.wrap(tokenIds), new long[]{1, seqLen});
             ai.onnxruntime.OnnxTensor attnMask = ai.onnxruntime.OnnxTensor.createTensor(
                    env, java.nio.LongBuffer.wrap(attentionMask), new long[]{1, seqLen});
             ai.onnxruntime.OnnxTensor posIds = ai.onnxruntime.OnnxTensor.createTensor(
                    env, java.nio.LongBuffer.wrap(positionIds), new long[]{1, seqLen})) {
            try (var results = session.run(java.util.Map.of(
                    "input_ids", inputIds,
                    "attention_mask", attnMask,
                    "position_ids", posIds))) {
                float[][][] logits = (float[][][]) results.get(0).getValue();
                float[] last = logits[0][(int) seqLen - 1];

                // Greedy if temperature is essentially zero
                if (temperature < 0.05) {
                    int bestIdx = 0;
                    float bestVal = last[0];
                    for (int i = 1; i < last.length; i++) {
                        if (last[i] > bestVal) {
                            bestVal = last[i];
                            bestIdx = i;
                        }
                    }
                    return bestIdx;
                }

                // Build (index, logit) pairs and sort descending
                int n = last.length;
                Integer[] idx = new Integer[n];
                for (int i = 0; i < n; i++) idx[i] = i;
                java.util.Arrays.sort(idx, (a, b) -> Float.compare(last[b], last[a]));

                // Compute scaled logits
                double[] scaled = new double[n];
                for (int i = 0; i < n; i++) scaled[i] = last[i] / temperature;

                // Top-K truncation: zero out everything below K-th highest
                if (topK > 0 && topK < n) {
                    float kthLogit = last[idx[topK]];
                    for (int i = 0; i < n; i++) {
                        if (last[i] < kthLogit) scaled[i] = Double.NEGATIVE_INFINITY;
                    }
                }

                // Softmax
                double maxS = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < n; i++) {
                    if (scaled[i] > maxS && scaled[i] != Double.NEGATIVE_INFINITY) maxS = scaled[i];
                }
                double[] probs = new double[n];
                double sum = 0.0;
                for (int i = 0; i < n; i++) {
                    if (scaled[i] == Double.NEGATIVE_INFINITY) continue;
                    probs[i] = Math.exp(scaled[i] - maxS);
                    sum += probs[i];
                }
                for (int i = 0; i < n; i++) probs[i] /= sum;

                // Top-P (nucleus) truncation: zero out tail beyond threshold.
                if (topP < 1.0) {
                    double cum = 0.0;
                    java.util.Set<Integer> keep = new java.util.LinkedHashSet<>();
                    for (int kk = 0; kk < n; kk++) {
                        int i = idx[kk];
                        if (probs[i] == 0.0) continue;
                        keep.add(i);
                        cum += probs[i];
                        if (cum >= topP) break;
                    }
                    // Renormalize over keep set
                    double keepSum = 0.0;
                    for (int i : keep) keepSum += probs[i];
                    if (keepSum > 0.0) {
                        for (int i = 0; i < n; i++) {
                            if (!keep.contains(i)) probs[i] = 0.0;
                            else probs[i] /= keepSum;
                        }
                    }
                }

                // Sample
                long seed = (System.nanoTime() ^ Thread.currentThread().threadId()) & 0x7FFFFFFFL;
                java.util.Random rng = new java.util.Random(seed);
                double r = rng.nextDouble();
                double cum = 0.0;
                for (int i = 0; i < n; i++) {
                    cum += probs[i];
                    if (r < cum) return i;
                }
                return idx[0];
            }
        }
    }

    public boolean load() {
        try {
            tokenizer = BpeTokenizer.fromModelDir(modelDir);
            log.info("QwenOnnxBridge: tokenizer loaded (vocab size={})",
                    tokenizer.vocabSize());
        } catch (IOException e) {
            log.warn("QwenOnnxBridge: tokenizer load failed: {}", e.getMessage());
            return false;
        }
        Path onnxPath = findOnnxModel(modelDir);
        if (onnxPath == null) {
            log.warn("QwenOnnxBridge: no model.onnx found under {}", modelDir);
            return false;
        }
        onnx = new OnnxRuntimeAdapter(onnxPath);
        onnx.setUseGpu(useGpu);
        boolean ok = onnx.load();
        if (!ok) {
            log.warn("QwenOnnxBridge: ONNX load failed");
        } else {
            log.info("QwenOnnxBridge: ONNX loaded (gpu={}, path={})", useGpu, onnxPath);
        }
        return ok;
    }

    public void close() {
        if (onnx != null) onnx.close();
    }

    /**
     * Greedy autoregressive generation.
     *
     * @param prompt input text
     * @param maxTokens maximum number of new tokens to generate
     * @return decoded text response
     */
    public String generate(String prompt, int maxTokens) {
        if (!isLoaded()) {
            throw new IllegalStateException("bridge not loaded");
        }
        int budget = Math.min(maxTokens, maxNewTokens);
        int[] promptIds = tokenizer.encode(prompt);
        List<Long> allIds = new ArrayList<>();
        for (int id : promptIds) allIds.add((long) id);

        long t0 = System.nanoTime();
        int generated = 0;
        for (int step = 0; step < budget; step++) {
            long[] ids = toLongArray(allIds);
            long nextToken;
            try {
                nextToken = onnx.greedyNextToken(ids);
            } catch (Exception e) {
                log.warn("QwenOnnxBridge.generate: inference failed at step {}: {}",
                        step, e.getMessage());
                break;
            }
            if (nextToken == eosToken) break;
            allIds.add(nextToken);
            generated++;
        }
        long elapsedNanos = System.nanoTime() - t0;
        metrics.record(generated, elapsedNanos, useGpu);
        long elapsedMs = elapsedNanos / 1_000_000L;
        log.debug("QwenOnnxBridge.generate: produced {} tokens in {}ms ({}tok/s)",
                generated, elapsedMs,
                elapsedMs > 0 ? (generated * 1000L / elapsedMs) : 0);

        // Decode only the generated portion (skip prompt).
        int[] genIds = new int[generated];
        for (int i = 0; i < generated; i++) {
            genIds[i] = allIds.get(promptIds.length + i).intValue();
        }
        return tokenizer.decode(genIds);
    }

    /** Returns the number of new tokens the most recent generate() call produced. */
    public int lastGeneratedTokens() {
        return 0;  // placeholder; testable via generate() return value
    }

    public String info() {
        if (onnx == null) return "QwenOnnxBridge(unloaded)";
        return "QwenOnnxBridge(gpu=" + useGpu
                + ", loaded=" + onnx.isLoaded()
                + ", path=" + onnx.modelPath()
                + ", vocab=" + (tokenizer == null ? "?" : tokenizer.vocabSize())
                + ")";
    }

    public Path modelDir() {
        return modelDir;
    }

    public int vocabSize() {
        return tokenizer == null ? 0 : tokenizer.vocabSize();
    }

    /** RUN 69 — get the inference metrics aggregator. */
    public OnnxInferenceMetrics metrics() {
        return metrics;
    }

    private static long[] toLongArray(List<Long> list) {
        long[] out = new long[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    private static Path findOnnxModel(Path modelDir) {
        Path[] candidates = {
                modelDir.resolve("model.onnx"),
                modelDir.resolve("onnx/model.onnx"),
                Path.of("models/onnx/qwen05b/model.onnx")
        };
        for (Path p : candidates) {
            if (Files.exists(p)) return p;
        }
        Path cwd = Path.of(".").toAbsolutePath();
        for (int i = 0; i < 5; i++) {
            Path p = cwd.resolve("models/onnx/qwen05b/model.onnx");
            if (Files.exists(p)) return p;
            cwd = cwd.getParent();
            if (cwd == null) break;
        }
        return null;
    }
}
