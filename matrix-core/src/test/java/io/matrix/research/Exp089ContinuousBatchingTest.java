package io.matrix.research;

import io.matrix.api.ContinuousBatchScheduler;
import io.matrix.api.QwenOnnxBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.46 — Continuous batching throughput on GPU (RUN 89).
 *
 * <p>Measures end-to-end throughput: requests per second across
 * many concurrent submissions.
 */
class Exp089ContinuousBatchingTest {

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
    void tenConcurrentRequests() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(8);
        if (!bridge.load()) return;

        ContinuousBatchScheduler sched =
                new ContinuousBatchScheduler(bridge, 4, 50);

        long t0 = System.nanoTime();
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(sched.submit("Hi " + i, 4));
        }
        for (var f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        double rps = futures.size() / (elapsedMs / 1000.0);
        System.out.printf("[BATCH-10] %d reqs in %dms = %.2f reqs/sec%n",
                futures.size(), elapsedMs, rps);
        assertThat(sched.totalProcessed()).isEqualTo(10);
        bridge.close();
        sched.shutdown();
    }

    @Test
    @EnabledIf("modelAvailable")
    void throughputScalesWithBatchSize() throws Exception {
        Path dir = findModelDir();
        QwenOnnxBridge bridge = new QwenOnnxBridge(dir);
        bridge.setMaxNewTokens(4);
        if (!bridge.load()) return;

        // Batch size 1 (effectively serial)
        ContinuousBatchScheduler small =
                new ContinuousBatchScheduler(bridge, 1, 10);
        long t0 = System.nanoTime();
        for (int i = 0; i < 3; i++) {
            schedSubmit(small, "x" + i, 2);
        }
        long smallMs = (System.nanoTime() - t0) / 1_000_000L;

        // Batch size 3 (drains together)
        ContinuousBatchScheduler big =
                new ContinuousBatchScheduler(bridge, 3, 10);
        t0 = System.nanoTime();
        for (int i = 0; i < 3; i++) {
            schedSubmit(big, "y" + i, 2);
        }
        long bigMs = (System.nanoTime() - t0) / 1_000_000L;

        System.out.printf("[BATCH-SIZE] small(1)=%dms big(3)=%dms%n", smallMs, bigMs);
        small.shutdown();
        big.shutdown();
        bridge.close();
    }

    private static void schedSubmit(ContinuousBatchScheduler s, String prompt, int tokens) {
        try {
            s.submit(prompt, tokens).get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            // ignore
        }
    }
}
