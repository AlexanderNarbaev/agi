package io.matrix.api;

import org.slf4j.Logger;
import io.matrix.api.QwenOnnxBridge;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RUN 88 — Continuous batching for ONNX inference.
 *
 * <p>For a chat service with many concurrent users, batching multiple
 * prompts together can dramatically improve GPU utilization. This
 * class implements a simple batch scheduler:
 * <ul>
 *   <li>Requests arrive via {@link #submit}</li>
 *   <li>They accumulate in a queue</li>
 *   <li>A worker drains the queue, processes up to {@code maxBatchSize}
 *       requests together</li>
 *   <li>Each request's future completes with its own response</li>
 * </ul>
 *
 * <p>This is a simplified version that processes requests serially per
 * batch but groups them in time windows. Real implementations would
 * use TensorRT-LLM or vLLM-style continuous batching, but this is a
 * starting point.
 */
public final class ContinuousBatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContinuousBatchScheduler.class);

    private final QwenOnnxBridge bridge;
    private final int maxBatchSize;
    private final long maxWaitMs;
    private final Deque<Request> queue = new ArrayDeque<>();
    private final ExecutorService executor;
    private final AtomicLong totalProcessed = new AtomicLong();
    private final AtomicLong totalBatches = new AtomicLong();

    public ContinuousBatchScheduler(QwenOnnxBridge bridge, int maxBatchSize, long maxWaitMs) {
        this.bridge = bridge;
        this.maxBatchSize = maxBatchSize;
        this.maxWaitMs = maxWaitMs;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "continuous-batch-worker");
            t.setDaemon(true);
            return t;
        });
        // Start the worker
        this.executor.submit(this::runWorker);
    }

    /**
     * Submit a generation request. Returns a CompletableFuture that
     * completes with the response text.
     */
    public CompletableFuture<String> submit(String prompt, int maxTokens) {
        Request req = new Request(prompt, maxTokens);
        queue.add(req);
        return req.future;
    }

    public long totalProcessed() { return totalProcessed.get(); }
    public long totalBatches() { return totalBatches.get(); }
    public int queueDepth() { return queue.size(); }

    /** Stop the worker. */
    public void shutdown() {
        executor.shutdownNow();
    }

    private void runWorker() {
        log.info("ContinuousBatchScheduler: worker started (maxBatch={}, maxWait={}ms)",
                maxBatchSize, maxWaitMs);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Wait for at least one request
                Request first = null;
                while (first == null && !Thread.currentThread().isInterrupted()) {
                    synchronized (queue) {
                        first = queue.pollFirst();
                    }
                    if (first == null) {
                        Thread.sleep(1);
                    }
                }
                if (first == null) break;

                // Drain up to maxBatchSize requests (or until maxWait)
                List<Request> batch = new ArrayList<>();
                batch.add(first);
                long deadline = System.nanoTime() + maxWaitMs * 1_000_000L;
                while (batch.size() < maxBatchSize
                        && System.nanoTime() < deadline) {
                    Request next;
                    synchronized (queue) {
                        next = queue.pollFirst();
                    }
                    if (next == null) {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    batch.add(next);
                }

                totalBatches.incrementAndGet();
                processBatch(batch);
                totalProcessed.addAndGet(batch.size());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("ContinuousBatchScheduler: error: {}", e.getMessage());
            }
        }
        log.info("ContinuousBatchScheduler: worker exiting");
    }

    private void processBatch(List<Request> batch) {
        for (Request req : batch) {
            try {
                String response = bridge.generate(req.prompt, req.maxTokens);
                req.future.complete(response);
            } catch (Exception e) {
                req.future.completeExceptionally(e);
            }
        }
    }

    private static final class Request {
        final String prompt;
        final int maxTokens;
        final CompletableFuture<String> future = new CompletableFuture<>();
        Request(String prompt, int maxTokens) {
            this.prompt = prompt;
            this.maxTokens = maxTokens;
        }
    }
}
