package io.matrix.api;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * RUN 57 — Qwen2.5-0.5B model adapter.
 *
 * <p>Reads the local Qwen2.5-0.5B model artifacts (config.json,
 * tokenizer.json, model.safetensors) and exposes metadata about
 * the model for the chain runner.
 *
 * <p>Honest caveats:
 * <ul>
 *   <li>This class reads METADATA only. Actual inference is done by
 *       the boolean chain (which is distilled from Qwen2.5-0.5B per
 *       the CONSTITUTION principle).</li>
 *   <li>For ONNX-based inference, see OnnxRuntimeAdapter (separate
 *       component, not implemented in this RUN).</li>
 *   <li>The chain distillation process uses these metadata fields
 *       to know the vocabulary size, hidden dimensions, etc.</li>
 * </ul>
 */
@ApplicationScoped
public class QwenModelAdapter {

    @ConfigProperty(name = "matrix.qwen.model-path", defaultValue = "models/hf_cache/qwen05b")
    String configuredPath;

    /** CDI constructor. */
    public QwenModelAdapter() {
        // Constructor invoked by CDI; the configuredPath field is
        // injected separately. Initialize fields defensively.
        this.modelPath = null;  // Set in onStart
        this.architecture = null;
        this.hiddenSize = 0;
        this.numHiddenLayers = 0;
        this.numAttentionHeads = 0;
        this.vocabSize = 0;
        this.maxPositionEmbeddings = 0;
        this.torchDtype = null;
        this.available = false;
    }

    private static final Logger log = LoggerFactory.getLogger(QwenModelAdapter.class);

    /** Architecture identifier (e.g., "Qwen2ForCausalLM"). */
    public String architecture;

    /** Hidden dimension (e.g., 896 for Qwen2.5-0.5B). */
    public int hiddenSize;

    /** Number of hidden layers (e.g., 24 for Qwen2.5-0.5B). */
    public int numHiddenLayers;

    /** Number of attention heads (e.g., 14 for Qwen2.5-0.5B). */
    public int numAttentionHeads;

    /** Vocabulary size (e.g., 151,936 for Qwen2.5-0.5B). */
    public int vocabSize;

    /** Maximum sequence length (e.g., 32768 for Qwen2.5-0.5B). */
    public int maxPositionEmbeddings;

    /** Model data type (e.g., "bfloat16"). */
    public String torchDtype;

    /** Path to the model artifacts directory. */
    public Path modelPath;

    /** True if the model artifacts are present. */
    public boolean available;

    public QwenModelAdapter(Path modelPath) {
        this.modelPath = modelPath;
        // Initialize fields based on whether config exists.
        if (!Files.isRegularFile(modelPath.resolve("config.json"))) {
            this.architecture = null;
            this.hiddenSize = 0;
            this.numHiddenLayers = 0;
            this.numAttentionHeads = 0;
            this.vocabSize = 0;
            this.maxPositionEmbeddings = 0;
            this.torchDtype = null;
            this.available = false;
            return;
        }
        // Try to load metadata.
        String arch = null, dtype = null;
        int hidden = 0, layers = 0, heads = 0, vocab = 0, maxPos = 0;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(modelPath.resolve("config.json").toFile());
            arch = root.has("architectures")
                    ? root.get("architectures").get(0).asText() : null;
            hidden = root.has("hidden_size") ? root.get("hidden_size").asInt() : 0;
            layers = root.has("num_hidden_layers") ? root.get("num_hidden_layers").asInt() : 0;
            heads = root.has("num_attention_heads") ? root.get("num_attention_heads").asInt() : 0;
            vocab = root.has("vocab_size") ? root.get("vocab_size").asInt() : 0;
            maxPos = root.has("max_position_embeddings")
                    ? root.get("max_position_embeddings").asInt() : 0;
            dtype = root.has("torch_dtype") ? root.get("torch_dtype").asText() : null;
        } catch (IOException e) {
            log.warn("QwenModelAdapter: failed to read config: {}", e.getMessage());
        }
        this.architecture = arch;
        this.hiddenSize = hidden;
        this.numHiddenLayers = layers;
        this.numAttentionHeads = heads;
        this.vocabSize = vocab;
        this.maxPositionEmbeddings = maxPos;
        this.torchDtype = dtype;
        this.available = true;
    }

    /** Initialize from configured path on startup. */
    void onStart(@Observes StartupEvent ev) {
        // Lazy: defer until first call to modelPath() to avoid touching
        // filesystem during build-time analysis.
        if (this.modelPath == null && configuredPath != null) {
            // Re-init from configuredPath
            QwenModelAdapter tmp = new QwenModelAdapter(Path.of(configuredPath));
            copyFrom(tmp);
        }
    }

    private void copyFrom(QwenModelAdapter other) {
        this.modelPath = other.modelPath;
        this.architecture = other.architecture;
        this.hiddenSize = other.hiddenSize;
        this.numHiddenLayers = other.numHiddenLayers;
        this.numAttentionHeads = other.numAttentionHeads;
        this.vocabSize = other.vocabSize;
        this.maxPositionEmbeddings = other.maxPositionEmbeddings;
        this.torchDtype = other.torchDtype;
        this.available = other.available;
    }

    /** Compact summary for logging. */
    public String summary() {
        if (!available) return "QwenModelAdapter(unavailable, path=" + modelPath + ")";
        return String.format(
                "QwenModelAdapter(arch=%s, hidden=%d, layers=%d, heads=%d, vocab=%d, maxPos=%d, dtype=%s)",
                architecture, hiddenSize, numHiddenLayers, numAttentionHeads,
                vocabSize, maxPositionEmbeddings, torchDtype);
    }
}
