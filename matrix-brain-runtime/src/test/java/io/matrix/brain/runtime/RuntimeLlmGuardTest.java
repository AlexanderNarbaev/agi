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


    /**
     * RECON-W21 — locate the repository root from wherever the test JVM started.
     *
     * <p>Gradle runs a module's tests with the MODULE directory as the working
     * directory, so {@code Paths.get("matrix-core/src/main/java")} does not
     * resolve. The pre-existing guard silently took the
     * {@code if (!Files.exists(src)) continue;} branch and passed VACUOUSLY —
     * it was scanning nothing. Walking up to the directory that contains
     * settings.gradle fixes that and makes the guard real.</p>
     */
    private static Path repoRoot() {
        Path p = Paths.get(".").toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++) {
            if (Files.exists(p.resolve("settings.gradle"))
                || Files.exists(p.resolve("settings.gradle.kts"))) {
                return p;
            }
            p = p.getParent();
        }
        return Paths.get(".").toAbsolutePath();
    }

    @Test
    void no_runtime_source_imports_legacy_llm_classes() throws IOException {
        Path repoRoot = repoRoot();
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

    /**
     * RECON-W21 — the ONNX activation-capture sidecar must stay OFFLINE.
     *
     * <p>Article I: "Offline tools (activation capture) may use ONNX/network but
     * must NEVER be reachable from analyze/chat/think paths." W21 moves ONNX
     * Runtime out of the JVM entirely ({@code scripts/capture_activations.py}
     * runs it in a separate process), so the guarantee is now mechanically
     * checkable rather than a matter of discipline:</p>
     * <ul>
     *   <li>no runtime module source may reference the Python sidecar, and</li>
     *   <li>no runtime module source may import an ONNX activation-capture type
     *       outside the explicitly-quarantined list.</li>
     * </ul>
     * <p>Extends the guard rather than replacing it: the quarantine list above is
     * still enforced unchanged.</p>
     */
    @Test
    void activation_capture_sidecar_is_unreachable_from_runtime_sources() throws IOException {
        Path repoRoot = repoRoot();
        java.util.List<String> violations = new java.util.ArrayList<>();
        String[] forbiddenRefs = {
            "capture_activations",     // the python sidecar
            "onnxruntime",             // the python package
            "layer_activations",       // the NDJSON activation schema
        };
        for (String sourceDir : RUNTIME_SOURCE_DIRS) {
            Path src = repoRoot.resolve(sourceDir);
            if (!Files.exists(src)) continue;
            try (Stream<Path> files = Files.walk(src)) {
                files.filter(p -> p.toString().endsWith(".java"))
                     .filter(p -> !p.toString().contains("ActivationRecord.java"))
                     .forEach(p -> {
                         try {
                             // Comments legitimately NAME the offline tool (that is
                             // how a reader learns it exists); only executable
                             // content is forbidden.
                             String c = stripComments(Files.readString(p));
                             for (String bad : forbiddenRefs) {
                                 if (c.contains("\"" + bad) || c.contains("import " + bad)) {
                                     violations.add(p + " references offline sidecar token: " + bad);
                                 }
                             }
                         } catch (IOException ignore) { }
                     });
            }
        }
        assertThat(violations)
            .as("ONNX activation capture must remain an offline sidecar (Article I)")
            .isEmpty();
    }

    /**
     * RECON-W21 — the runtime's own activation reader must be pure data.
     * {@code ActivationRecord} may not load a model or touch a native library;
     * it is the only runtime-side class permitted to speak the capture schema.
     */
    @Test
    void activation_record_is_pure_data_with_no_model_loading() throws IOException {
        Path file = repoRoot().resolve("matrix-core/src/main/java/io/matrix/distill/ActivationRecord.java");
        assertThat(Files.exists(file)).as("ActivationRecord must exist").isTrue();
        String content = stripComments(Files.readString(file));
        for (String forbidden : new String[]{
                "onnxruntime", "InferenceSession", "OrtSession", "System.load",
                "Runtime.getRuntime", "ProcessBuilder", "HttpClient", "URLConnection"}) {
            assertThat(content)
                .as("ActivationRecord must not contain %s (Article I offline purity)", forbidden)
                .doesNotContain(forbidden);
        }
    }

    /** Remove // and /* *&#47; comments so prose about a forbidden name is not
     *  mistaken for a use of it. String literals are left intact. */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inBlock = false;
        boolean inLine = false;
        boolean inStr = false;
        boolean inChar = false;
        boolean esc = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char n = (i + 1 < src.length()) ? src.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') { inLine = false; out.append(c); }
                continue;
            }
            if (inBlock) {
                if (c == '*' && n == '/') { inBlock = false; i++; }
                else if (c == '\n') out.append(c);
                continue;
            }
            if (inStr) {
                out.append(c);
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') inStr = false;
                continue;
            }
            if (inChar) {
                out.append(c);
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '\'') inChar = false;
                continue;
            }
            if (c == '/' && n == '/') { inLine = true; i++; continue; }
            if (c == '/' && n == '*') { inBlock = true; i++; continue; }
            if (c == '"') { inStr = true; out.append(c); continue; }
            if (c == '\'') { inChar = true; out.append(c); continue; }
            out.append(c);
        }
        return out.toString();
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
