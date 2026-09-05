package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 88 — ContinuousBatchScheduler unit tests. */
class ContinuousBatchSchedulerTest {

    @Test
    void emptySchedulerHasZeroDepth() {
        QwenOnnxBridge bridge = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return true; }
            @Override
            public String generate(String prompt, int maxTokens) {
                return "reply:" + prompt;
            }
        };
        ContinuousBatchScheduler sched = new ContinuousBatchScheduler(bridge, 8, 50);
        assertThat(sched.queueDepth()).isZero();
        sched.shutdown();
    }

    @Test
    void submitReturnsCompletableFuture() {
        QwenOnnxBridge bridge = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return true; }
            @Override
            public String generate(String prompt, int maxTokens) {
                return "echo:" + prompt;
            }
        };
        ContinuousBatchScheduler sched = new ContinuousBatchScheduler(bridge, 8, 50);
        CompletableFuture<String> f = sched.submit("hello", 8);
        assertThat(f).isNotNull();
        try {
            String result = f.get(2, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("echo:hello");
        } catch (Exception e) {
            assertThat(false).as("Future should complete: " + e).isTrue();
        }
        sched.shutdown();
    }

    @Test
    void processesMultipleConcurrentSubmissions() throws Exception {
        QwenOnnxBridge bridge = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return true; }
            @Override
            public String generate(String prompt, int maxTokens) {
                return "r:" + prompt;
            }
        };
        ContinuousBatchScheduler sched = new ContinuousBatchScheduler(bridge, 4, 100);
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(sched.submit("p" + i, 4));
        }
        for (int i = 0; i < 5; i++) {
            String result = futures.get(i).get(5, TimeUnit.SECONDS);
            assertThat(result).isEqualTo("r:p" + i);
        }
        assertThat(sched.totalProcessed()).isGreaterThanOrEqualTo(5);
        sched.shutdown();
    }

    @Test
    void multipleBatchesAccumulate() throws Exception {
        QwenOnnxBridge bridge = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return true; }
            @Override
            public String generate(String prompt, int maxTokens) {
                return "ok";
            }
        };
        // Force small batches with maxBatchSize=2, maxWait=20ms
        ContinuousBatchScheduler sched = new ContinuousBatchScheduler(bridge, 2, 20);
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(sched.submit("x" + i, 2));
        }
        for (var f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        // With maxBatch=2 and 6 requests, expect at least 3 batches
        assertThat(sched.totalBatches()).isGreaterThanOrEqualTo(3);
        assertThat(sched.totalProcessed()).isGreaterThanOrEqualTo(6);
        sched.shutdown();
    }
}
