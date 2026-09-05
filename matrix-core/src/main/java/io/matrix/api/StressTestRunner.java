package io.matrix.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * RUN 133 — StressTest runner for concurrent generation.
 *
 * <p>Drives N concurrent generations on a thread pool and reports
 * throughput and error counts.
 */
public final class StressTestRunner {

    public record StressResult(int totalRequests, int successful,
                               int failed, long elapsedMs,
                               double requestsPerSecond) {}

    public StressResult run(QwenOnnxBridge bridge, int numRequests,
                            int maxConcurrent, String prompt, int maxTokens)
            throws Exception {
        if (!bridge.isLoaded()) {
            throw new IllegalStateException("bridge not loaded");
        }
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrent);
        List<CompletableFuture<String>> futures = new ArrayList<>();
        long t0 = System.nanoTime();
        for (int i = 0; i < numRequests; i++) {
            final int idx = i;
            futures.add(CompletableFuture.supplyAsync(() ->
                    bridge.generate(prompt + " " + idx, maxTokens), executor));
        }
        // Wait for all (collect results manually to avoid allOf propagating errors)
        for (var f : futures) {
            try {
                f.get(60, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // tracked below
            }
        }
        executor.shutdown();
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;

        int successful = 0;
        int failed = 0;
        for (var f : futures) {
            try {
                String r = f.getNow(null);
                if (r != null && !r.isBlank()) successful++;
                else failed++;
            } catch (Exception e) {
                failed++;
            }
        }
        double rps = numRequests / (elapsedMs / 1000.0);
        return new StressResult(numRequests, successful, failed,
                elapsedMs, rps);
    }
}
