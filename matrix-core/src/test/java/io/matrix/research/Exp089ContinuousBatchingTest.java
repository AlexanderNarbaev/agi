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
        // RECON-W34.2: this was `if (!bridge.load()) return;` -- an early return that JUnit
        // records as a PASS. A bridge that loaded nothing and produced nothing therefore
        // produced a green test. Model absence is already declared by @EnabledIf(modelAvailable)
        // above, so reaching here means a model WAS found; if it then fails to load, that is a
        // genuine failure and must be reported as one.
        assertThat(bridge.load())
                .as("a model directory was located and @EnabledIf passed, so load() must succeed. "
                        + "An early return here would report PASS for a bridge that did nothing")
                .isTrue();

        ContinuousBatchScheduler sched =
                new ContinuousBatchScheduler(bridge, 4, 50);

        long t0 = System.nanoTime();
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(sched.submit("Hi " + i, 4));
        }
        // RECON-W34.2: this was f.get(60, TimeUnit.SECONDS), a wall-clock deadline that made
        // the verdict depend on machine speed. Measured evidence: this test FAILED in the
        // 26-minute full suite, failed 2 of 3 back-to-back runs, and passed 8 of 8 isolated.
        // Under suite contention ten concurrent inferences contend for a saturated CPU -- and
        // ONNX Runtime falls back to CPU here -- so correct code could exceed 60s on a machine
        // doing nothing wrong. Unbounded get() waits for the real result; the assertions below
        // are what constitute the correctness claim, not how long it took.
        for (var f : futures) {
            f.get();
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
        // RECON-W34.2: same vacuous pass as tenConcurrentRequests -- an early return recorded
        // as PASS. Absence is already declared by @EnabledIf; a load failure here is real.
        assertThat(bridge.load())
                .as("@EnabledIf passed, so a model was located and load() must succeed")
                .isTrue();

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
