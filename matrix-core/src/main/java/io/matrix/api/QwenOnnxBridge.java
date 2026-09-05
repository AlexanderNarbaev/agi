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
public final class QwenOnnxBridge {

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
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
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
