package io.matrix.api;

import java.util.concurrent.atomic.AtomicLong;

/**
 * RUN 69 — ONNX inference metrics aggregator.
 *
 * <p>Tracks per-bridge inference statistics: call count, total
 * tokens generated, wall-clock latency, tokens/sec. Used by the
 * /v1/onnx/metrics endpoint and by EXP-MATRIX.40 verification.
 *
 * <p>This is intentionally allocation-free in the hot path so it
 * can be called from autoregressive generation without GC pressure.
 */
public final class OnnxInferenceMetrics {

    private final AtomicLong inferenceCount = new AtomicLong();
    private final AtomicLong totalTokens = new AtomicLong();
    private final AtomicLong totalLatencyNanos = new AtomicLong();
    private final AtomicLong maxLatencyNanos = new AtomicLong();
    private final AtomicLong totalGpuCalls = new AtomicLong();
    private final AtomicLong totalCpuCalls = new AtomicLong();

    private volatile long firstInferenceNanos = 0;

    /** Record a single inference call. */
    public void record(long tokensGenerated, long latencyNanos, boolean gpu) {
        inferenceCount.incrementAndGet();
        totalTokens.addAndGet(tokensGenerated);
        totalLatencyNanos.addAndGet(latencyNanos);
        updateMax(maxLatencyNanos, latencyNanos);
        if (gpu) totalGpuCalls.incrementAndGet();
        else totalCpuCalls.incrementAndGet();
        if (firstInferenceNanos == 0) {
            firstInferenceNanos = System.nanoTime();
        }
    }

    private static void updateMax(AtomicLong holder, long value) {
        long prev;
        do {
            prev = holder.get();
            if (value <= prev) return;
        } while (!holder.compareAndSet(prev, value));
    }

    public long inferenceCount() { return inferenceCount.get(); }
    public long totalTokens() { return totalTokens.get(); }
    public long avgLatencyNanos() {
        long n = inferenceCount.get();
        return n == 0 ? 0 : totalLatencyNanos.get() / n;
    }
    public long maxLatencyNanos() { return maxLatencyNanos.get(); }
    public long totalGpuCalls() { return totalGpuCalls.get(); }
    public long totalCpuCalls() { return totalCpuCalls.get(); }
    public long uptimeMs() {
        return firstInferenceNanos == 0
                ? 0 : (System.nanoTime() - firstInferenceNanos) / 1_000_000L;
    }

    public double tokensPerSecond() {
        long ms = uptimeMs();
        return ms == 0 ? 0.0 : (totalTokens.get() * 1000.0) / ms;
    }

    public double gpuRatio() {
        long total = inferenceCount.get();
        return total == 0 ? 0.0 : (double) totalGpuCalls.get() / total;
    }

    /** Reset all counters (for testing or session rollover). */
    public void reset() {
        inferenceCount.set(0);
        totalTokens.set(0);
        totalLatencyNanos.set(0);
        maxLatencyNanos.set(0);
        totalGpuCalls.set(0);
        totalCpuCalls.set(0);
        firstInferenceNanos = 0;
    }

    /** JSON snapshot for the metrics endpoint. */
    public String toJson() {
        return "{"
                + "\"inferenceCount\":" + inferenceCount.get()
                + ",\"totalTokens\":" + totalTokens.get()
                + ",\"avgLatencyMs\":" + (avgLatencyNanos() / 1_000_000.0)
                + ",\"maxLatencyMs\":" + (maxLatencyNanos() / 1_000_000.0)
                + ",\"totalGpuCalls\":" + totalGpuCalls.get()
                + ",\"totalCpuCalls\":" + totalCpuCalls.get()
                + ",\"tokensPerSecond\":" + tokensPerSecond()
                + ",\"gpuRatio\":" + gpuRatio()
                + ",\"uptimeMs\":" + uptimeMs()
                + "}";
    }
}
