package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * W229 — Cognitive Continuous Batching.
 *
 * <p>Inspired by continuous batching in vLLM (Kwon et al. 2023) and
 * Orca (Yu et al. 2022). Process multiple cognitive profiles in
 * parallel batches to maximize throughput.
 *
 * <p>Strategy:
 * - Queue incoming profiles
 * - Batch up to maxBatchSize profiles
 * - Process batch together (parallel-style via batch operations)
 * - Free slots as batches complete
 *
 * <p>Use cases:
 * - High-throughput cognitive processing
 * - Real-time cognitive monitoring
 * - Server-side cognitive analysis
 *
 * <p>CONSTITUTION VI compliance: batched cognitive processing, not
 * phenomenal consciousness claim.
 */
public final class CognitiveContinuousBatching {

    /** A batch of profiles to process together. */
    public record Batch(List<CognitiveGenesisProfile> profiles, long batchId) {}

    /** Result of processing a batch. */
    public record BatchResult(
        long batchId,
        int profilesProcessed,
        double[] batchMeanEmbedding,
        long elapsedOps
    ) {}

    private final int maxBatchSize;
    private final List<CognitiveGenesisProfile> queue;
    private long nextBatchId = 0;

    public CognitiveContinuousBatching(int maxBatchSize) {
        if (maxBatchSize < 1) {
            throw new IllegalArgumentException("maxBatchSize must be >= 1");
        }
        this.maxBatchSize = maxBatchSize;
        this.queue = new ArrayList<>();
    }

    /**
     * Enqueue a profile for batched processing.
     */
    public void enqueue(CognitiveGenesisProfile profile) {
        if (profile != null) queue.add(profile);
    }

    /**
     * Form a batch from the queue (up to maxBatchSize).
     *
     * @return null if queue is empty, else batch
     */
    public Batch formBatch() {
        if (queue.isEmpty()) return null;
        int batchSize = Math.min(maxBatchSize, queue.size());
        List<CognitiveGenesisProfile> batchProfiles = new ArrayList<>(queue.subList(0, batchSize));
        for (int i = 0; i < batchSize; i++) queue.remove(0);
        return new Batch(batchProfiles, nextBatchId++);
    }

    /**
     * Process a batch: compute mean embedding (proxy for batch processing).
     */
    public BatchResult process(Batch batch) {
        if (batch == null || batch.profiles().isEmpty()) {
            return new BatchResult(0, 0, new double[0], 0);
        }
        int n = batch.profiles().size();
        CognitiveEmbedding embedder = new CognitiveEmbedding(64, batch.batchId());
        double[] mean = new double[64];
        long ops = 0;
        for (CognitiveGenesisProfile p : batch.profiles()) {
            double[] v = embedder.embed(p);
            for (int d = 0; d < 64; d++) mean[d] += v[d];
            ops += 64 * 13;
        }
        for (int d = 0; d < 64; d++) mean[d] /= n;
        return new BatchResult(batch.batchId(), n, mean, ops);
    }

    /**
     * Convenience: form a batch and process it.
     */
    public BatchResult formAndProcess() {
        Batch batch = formBatch();
        if (batch == null) return new BatchResult(0, 0, new double[0], 0);
        return process(batch);
    }

    /** Current queue size. */
    public int queueSize() { return queue.size(); }

    /** Max batch size. */
    public int maxBatchSize() { return maxBatchSize; }
}
