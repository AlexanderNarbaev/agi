package io.matrix.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HuggingFaceFetcher} — RUN 54 HF integration.
 */
class HuggingFaceFetcherTest {

    @TempDir
    Path tmp;

    private HuggingFaceFetcher fetcher;

    @BeforeEach
    void setUp() throws IOException {
        fetcher = new HuggingFaceFetcher("Qwen/Qwen2.5-0.5B-Instruct", tmp);
    }

    @Test
    void modelIdAndCacheDirAreExposed() {
        assertThat(fetcher.modelId()).isEqualTo("Qwen/Qwen2.5-0.5B-Instruct");
        assertThat(fetcher.cacheDir()).isEqualTo(tmp);
    }

    @Test
    void isAvailableReturnsFalseForEmptyCache() {
        assertThat(fetcher.isAvailable()).isFalse();
    }

    @Test
    void isAvailableReturnsTrueWhenAllArtifactsPresent() throws IOException {
        Files.writeString(tmp.resolve("config.json"), "{}");
        Files.writeString(tmp.resolve("model.safetensors"), "fake weights");
        // Re-check (cache state was set to false on init).
        HuggingFaceFetcher fresh = new HuggingFaceFetcher("test/model", tmp);
        assertThat(fresh.isAvailable()).isTrue();
    }

    @Test
    void isAvailableTrueWithBinInsteadOfSafetensors() throws IOException {
        Files.writeString(tmp.resolve("config.json"), "{}");
        Files.writeString(tmp.resolve("model.bin"), "fake weights");
        HuggingFaceFetcher fresh = new HuggingFaceFetcher("test/model", tmp);
        assertThat(fresh.isAvailable()).isTrue();
    }

    @Test
    void isAvailableFalseWithoutConfig() throws IOException {
        Files.writeString(tmp.resolve("model.safetensors"), "fake weights");
        HuggingFaceFetcher fresh = new HuggingFaceFetcher("test/model", tmp);
        assertThat(fresh.isAvailable()).isFalse();
    }

    @Test
    void fetchFailureCountStartsAtZero() {
        assertThat(fetcher.fetchFailureCount()).isZero();
    }

    @Test
    void downloadCountStartsAtZero() {
        assertThat(fetcher.downloadCount()).isZero();
    }

    @Test
    void fetchReportsLastOutputOnFailure() {
        boolean result = fetcher.fetch();  // Will likely fail if hf CLI isn't fully usable in tests.
        // Even on failure, lastOutput should be set.
        assertThat(fetcher.lastOutput()).isNotNull();
        // The result may be true or false depending on environment.
        // Just verify the counter consistency.
        if (result) {
            assertThat(fetcher.downloadCount()).isEqualTo(1);
        } else {
            assertThat(fetcher.fetchFailureCount()).isEqualTo(1);
        }
    }

    @org.junit.jupiter.api.io.TempDir
    Path tmp2;

    @Test
    void isAvailableDetectsAlreadyDownloadedModel() throws IOException {
        // Simulate the actual download scenario: config + safetensors present.
        Files.createDirectories(tmp);
        Files.writeString(tmp.resolve("config.json"), "{\"model_type\":\"qwen2\"}");
        Files.writeString(tmp.resolve("model.safetensors"), "fake binary weights");
        HuggingFaceFetcher fresh = new HuggingFaceFetcher("Qwen/Qwen2.5-0.5B-Instruct", tmp);
        assertThat(fresh.isAvailable())
                .as("when both config.json and model.safetensors present, isAvailable=true")
                .isTrue();
    }
}
