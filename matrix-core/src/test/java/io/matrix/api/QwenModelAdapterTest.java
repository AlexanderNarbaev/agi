package io.matrix.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link QwenModelAdapter} — RUN 57 Qwen2.5-0.5B integration.
 */
class QwenModelAdapterTest {

    @TempDir
    Path tmp;

    @Test
    void missingConfigReportsUnavailable() {
        QwenModelAdapter adapter = new QwenModelAdapter(tmp);
        assertThat(adapter.available).isFalse();
        assertThat(adapter.architecture).isNull();
        assertThat(adapter.hiddenSize).isZero();
        assertThat(adapter.summary()).contains("unavailable");
    }

    @Test
    void readsQwenConfigCorrectly() throws IOException {
        Files.writeString(tmp.resolve("config.json"), """
                {
                  "architectures": ["Qwen2ForCausalLM"],
                  "hidden_size": 896,
                  "num_hidden_layers": 24,
                  "num_attention_heads": 14,
                  "vocab_size": 151936,
                  "max_position_embeddings": 32768,
                  "torch_dtype": "bfloat16"
                }
                """);
        QwenModelAdapter adapter = new QwenModelAdapter(tmp);
        assertThat(adapter.available).isTrue();
        assertThat(adapter.architecture).isEqualTo("Qwen2ForCausalLM");
        assertThat(adapter.hiddenSize).isEqualTo(896);
        assertThat(adapter.numHiddenLayers).isEqualTo(24);
        assertThat(adapter.numAttentionHeads).isEqualTo(14);
        assertThat(adapter.vocabSize).isEqualTo(151_936);
        assertThat(adapter.maxPositionEmbeddings).isEqualTo(32_768);
        assertThat(adapter.torchDtype).isEqualTo("bfloat16");
    }

    @Test
    void readsRealDownloadedModel() {
        // Run against the actual downloaded Qwen2.5-0.5B if present.
        Path model = Path.of("models/hf_cache/qwen05b");
        if (!Files.isRegularFile(model.resolve("config.json"))) return;  // skip
        QwenModelAdapter adapter = new QwenModelAdapter(model);
        assertThat(adapter.available).isTrue();
        assertThat(adapter.architecture).isEqualTo("Qwen2ForCausalLM");
        assertThat(adapter.hiddenSize).isEqualTo(896);
        assertThat(adapter.numHiddenLayers).isEqualTo(24);
        assertThat(adapter.vocabSize).isEqualTo(151_936);
        assertThat(adapter.summary()).contains("hidden=896");
    }

    @Test
    void handlesMissingFieldsGracefully() throws IOException {
        Files.writeString(tmp.resolve("config.json"), "{}");
        QwenModelAdapter adapter = new QwenModelAdapter(tmp);
        assertThat(adapter.available).isTrue();
        assertThat(adapter.architecture).isNull();
        assertThat(adapter.hiddenSize).isZero();
    }

    @Test
    void summaryIncludesAllFields() throws IOException {
        Files.writeString(tmp.resolve("config.json"), """
                {
                  "architectures": ["Qwen2ForCausalLM"],
                  "hidden_size": 896,
                  "num_hidden_layers": 24,
                  "vocab_size": 151936
                }
                """);
        QwenModelAdapter adapter = new QwenModelAdapter(tmp);
        String s = adapter.summary();
        assertThat(s).contains("Qwen2ForCausalLM");
        assertThat(s).contains("hidden=896");
        assertThat(s).contains("vocab=151936");
    }

    @Test
    void zeroValuesForMissingOptionalFields() throws IOException {
        Files.writeString(tmp.resolve("config.json"), """
                {"architectures": ["Qwen2ForCausalLM"]}
                """);
        QwenModelAdapter adapter = new QwenModelAdapter(tmp);
        assertThat(adapter.architecture).isEqualTo("Qwen2ForCausalLM");
        assertThat(adapter.hiddenSize).isZero();
        assertThat(adapter.vocabSize).isZero();
    }
}
