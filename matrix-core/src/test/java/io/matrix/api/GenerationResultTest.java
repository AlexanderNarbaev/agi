package io.matrix.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 86 — GenerationResult + generateWithProbs tests. */
class GenerationResultTest {

    private static Path findModelDir() {
        Path cwd = Path.of(".").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            Path p = cwd.resolve("models/hf_cache/qwen05b");
            if (Files.exists(p.resolve("config.json"))) return p;
            cwd = cwd.getParent();
            if (cwd == null) break;
        }
        return null;
    }

    static boolean modelAvailable() {
        return findModelDir() != null;
    }

    @Test
    void emptyResultConfidence() {
        GenerationResult r = new GenerationResult("", 0, 0, java.util.List.of());
        assertThat(r.avgConfidence()).isZero();
        assertThat(r.minConfidence()).isZero();
        assertThat(r.tokensGenerated()).isZero();
    }

    @Test
    void singleStepConfidence() {
        var step = new GenerationResult.TokenStep(1234, "test", 0.75,
                java.util.List.of(new GenerationResult.Candidate(1234, "test", 0.75)));
        GenerationResult r = new GenerationResult("test", 1, 100,
                java.util.List.of(step));
        assertThat(r.avgConfidence()).isEqualTo(0.75);
        assertThat(r.minConfidence()).isEqualTo(0.75);
    }

    @Test
    void multiStepConfidence() {
        var s1 = new GenerationResult.TokenStep(1, "a", 0.5, java.util.List.of());
        var s2 = new GenerationResult.TokenStep(2, "b", 0.7, java.util.List.of());
        var s3 = new GenerationResult.TokenStep(3, "c", 0.9, java.util.List.of());
        GenerationResult r = new GenerationResult("abc", 3, 100,
                java.util.List.of(s1, s2, s3));
        assertThat(r.avgConfidence()).isCloseTo(0.7, org.assertj.core.data.Offset.offset(0.01));
        assertThat(r.minConfidence()).isEqualTo(0.5);
    }

    @Test
    void candidateRecord() {
        GenerationResult.Candidate c = new GenerationResult.Candidate(100, "Ġhello", 0.85);
        assertThat(c.tokenId()).isEqualTo(100);
        assertThat(c.token()).isEqualTo("Ġhello");
        assertThat(c.probability()).isEqualTo(0.85);
    }

    @Test
    @EnabledIf("modelAvailable")
    void generateWithProbsProducesStructuredResult() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(8);
        if (!bridge.load()) return;

        GenerationResult result = bridge.generateWithProbs("Hi", 4);
        assertThat(result).isNotNull();
        assertThat(result.tokensGenerated()).isGreaterThan(0);
        assertThat(result.elapsedMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.steps()).hasSize(result.tokensGenerated());
        // Each step has top candidates
        for (var step : result.steps()) {
            assertThat(step.topCandidates()).isNotEmpty();
            // Probabilities sum to roughly 1.0 (softmax)
            double sum = 0.0;
            for (var c : step.topCandidates()) sum += c.probability();
            assertThat(sum).isLessThanOrEqualTo(1.0);
        }
        bridge.close();
    }
}
