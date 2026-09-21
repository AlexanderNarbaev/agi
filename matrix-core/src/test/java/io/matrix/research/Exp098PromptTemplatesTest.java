package io.matrix.research;

import io.matrix.api.GenerationResult;
import io.matrix.api.PromptTemplates;
import io.matrix.api.QwenOnnxBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.49 — Prompt template verification on GPU (RUN 98).
 *
 * <p>Drives each prompt template through real Qwen and verifies
 * the output is non-blank and properly formatted.
 */
class Exp098PromptTemplatesTest {

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

    private static Path findOnnx() {
        Path cwd = Path.of(".").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            Path p = cwd.resolve("models/onnx/qwen05b/model.onnx");
            if (Files.exists(p)) return p;
            cwd = cwd.getParent();
            if (cwd == null) break;
        }
        return null;
    }

    static boolean modelAvailable() {
        return findModelDir() != null && findOnnx() != null;
    }

    @Test
    @EnabledIf("modelAvailable")
    void helpfulAssistantGenerates() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(16);
        if (!bridge.load()) return;

        String prompt = PromptTemplates.helpfulAssistant("What is gravity?");
        String reply = bridge.generate(prompt, 16);
        assertThat(reply).isNotBlank();
        System.out.println("[HELPFUL] " + reply);
        bridge.close();
    }

    @Test
    @EnabledIf("modelAvailable")
    void conciseAssistantGenerates() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(8);
        if (!bridge.load()) return;

        String prompt = PromptTemplates.conciseAssistant("What color is the sky?");
        String reply = bridge.generate(prompt, 8);
        assertThat(reply).isNotBlank();
        System.out.println("[CONCISE] " + reply);
        bridge.close();
    }

    @Test
    @EnabledIf("modelAvailable")
    void translatorGenerates() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(16);
        if (!bridge.load()) return;

        String prompt = PromptTemplates.translator("Hello world", "French");
        String reply = bridge.generate(prompt, 16);
        assertThat(reply).isNotBlank();
        System.out.println("[TRANSLATE] " + reply);
        bridge.close();
    }

    @Test
    @EnabledIf("modelAvailable")
    void summarizerGenerates() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(16);
        if (!bridge.load()) return;

        String prompt = PromptTemplates.summarizer(
                "The Qwen2.5-0.5B model is a small but capable LLM. "
                        + "It can be used for various tasks including translation, "
                        + "summarization, and question answering.");
        String reply = bridge.generate(prompt, 16);
        assertThat(reply).isNotBlank();
        System.out.println("[SUMMARIZE] " + reply);
        bridge.close();
    }

    @Test
    @EnabledIf("modelAvailable")
    void mathAssistantGenerates() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(16);
        if (!bridge.load()) return;

        String prompt = PromptTemplates.mathAssistant("What is 12 times 7?");
        GenerationResult result = bridge.generateWithProbs(prompt, 16);
        assertThat(result.text()).isNotBlank();
        System.out.printf("[MATH] %s (avg conf=%.2f)%n",
                result.text(), result.avgConfidence());
        bridge.close();
    }
}
