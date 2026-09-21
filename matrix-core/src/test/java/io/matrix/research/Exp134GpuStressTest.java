package io.matrix.research;

import io.matrix.api.QwenOnnxBridge;
import io.matrix.api.StressTestRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.60 — GPU stress test on real Qwen (RUN 134).
 *
 * <p>Runs 10 concurrent generations with 4 threads and measures
 * throughput.
 */
class Exp134GpuStressTest {

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
    void tenConcurrent4Threads() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(4);
        if (!bridge.load()) return;

        StressTestRunner runner = new StressTestRunner();
        var result = runner.run(bridge, 10, 4, "Hi", 4);
        System.out.printf("[STRESS-10x4] total=%d ok=%d fail=%d ms=%d rps=%.2f%n",
                result.totalRequests(), result.successful(), result.failed(),
                result.elapsedMs(), result.requestsPerSecond());

        assertThat(result.totalRequests()).isEqualTo(10);
        assertThat(result.successful()).isGreaterThanOrEqualTo(8);
        bridge.close();
    }
}
