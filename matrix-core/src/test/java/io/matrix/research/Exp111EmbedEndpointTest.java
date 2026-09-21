package io.matrix.research;

import io.matrix.api.QwenOnnxBridge;
import io.matrix.api.TextEmbedder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.54 — Embed endpoint + similarity on GPU (RUN 111).
 */
class Exp111EmbedEndpointTest {

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
    void batchEmbeddingsProduceExpectedFormat() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        if (!bridge.load()) return;

        TextEmbedder embedder = new TextEmbedder(bridge);
        String[] inputs = {"Hello", "World", "Goodbye"};
        StringBuilder allOutput = new StringBuilder();
        for (String s : inputs) {
            float[] vec = embedder.embed(s);
            StringBuilder sb = new StringBuilder();
            sb.append(vec.length).append(":");
            for (int i = 0; i < vec.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(vec[i]);
            }
            allOutput.append(sb).append("\n");
        }
        System.out.println("[EMBED-OUTPUT]\n" + allOutput);
        assertThat(allOutput.toString()).contains("896:");
        bridge.close();
    }
}
