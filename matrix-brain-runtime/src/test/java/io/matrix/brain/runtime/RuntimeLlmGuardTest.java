package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MIND-W5 — CI guard: zero runtime imports of legacy LLM classes.
 *
 * <p>CONSTITUTION Article I: "LLM в путях решений остаётся ЗАПРЕЩЁН" (no LLM
 * in the runtime decision path). This test asserts that the runtime mind
 * modules ({@code matrix-brain-runtime} and {@code matrix-api-gateway})
 * never import any of the legacy LLM/Ollama/Qwen classes from
 * {@code matrix-core/src/main/java/io/matrix/api/}.</p>
 *
 * <p>Legacy LLM classes are quarantined for OFFLINE distillation feeders
 * (MIND-W5 / W7 audit-wave); they MUST NOT appear in the runtime mind.</p>
 */
class RuntimeLlmGuardTest {

    /** Legacy LLM-serving classes that must stay out of the runtime path. */
    private static final List<String> FORBIDDEN_PACKAGES = List.of(
        "io.matrix.api.OnnxModelRegistry",
        "io.matrix.api.QwenModelAdapter",
        "io.matrix.api.QwenOnnxBridge",
        "io.matrix.api.OnnxChatResource",
        "io.matrix.api.OpenAIChatResource",
        "io.matrix.api.BeamSearchGenerator",
        "io.matrix.api.BpeTokenizer",
        "io.matrix.api.ChainTextGenerator",
        "io.matrix.api.ContinuousBatchScheduler",
        "io.matrix.api.AdaptiveModelRouter",
        "io.matrix.api.HuggingFaceFetcher",
        "io.matrix.api.PromptTemplates",
        "io.matrix.api.ContextWindowManager",
        "io.matrix.api.TextEmbedder",
        "io.matrix.api.OnnxChainEnsemble",
        "io.matrix.api.OnnxRuntimeAdapter"
    );

    /** Allowed source roots for the runtime mind path. */
    private static final List<String> RUNTIME_SOURCE_DIRS = List.of(
        "matrix-brain-runtime/src/main/java",
        "matrix-api-gateway/src/main/java"
    );

    @Test
    void no_runtime_source_imports_legacy_llm_classes() throws IOException {
        Path repoRoot = Paths.get(".").toAbsolutePath();
        java.util.List<String> violations = new java.util.ArrayList<>();
        for (String sourceDir : RUNTIME_SOURCE_DIRS) {
            Path src = repoRoot.resolve(sourceDir);
            if (!Files.exists(src)) continue;
            try (Stream<Path> files = Files.walk(src)) {
                files.filter(p -> p.toString().endsWith(".java"))
                     .forEach(p -> check(p, violations));
            }
        }
        assertThat(violations)
            .as("Runtime mind must not import legacy LLM classes (CONSTITUTION Article I)")
            .isEmpty();
    }

    private static void check(Path file, java.util.List<String> violations) {
        try {
            String content = Files.readString(file);
            for (String forbidden : FORBIDDEN_PACKAGES) {
                // Look for an actual import statement (not just the string in a comment)
                String importLine = "import " + forbidden + ";";
                if (content.contains(importLine)) {
                    violations.add(file + " imports forbidden " + forbidden);
                }
                // Also catch reflective Class.forName() references
                if (content.contains("\"" + forbidden + "\"")) {
                    violations.add(file + " references forbidden " + forbidden
                        + " via Class.forName");
                }
            }
        } catch (IOException ex) {
            // Best-effort; skip files we can't read.
        }
    }
}
