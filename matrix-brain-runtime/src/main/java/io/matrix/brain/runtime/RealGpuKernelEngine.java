package io.matrix.brain.runtime;

import io.matrix.federation.gpu.GpuTaskExecutor;
import io.matrix.federation.proto.GpuTask;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TRUE-W6 — GPU acceleration wrapper around real {@link GpuTaskExecutor}.
 *
 * <p>Auto-detects GPU availability. Falls back to CPU deterministically.
 * The same {@link GpuTaskExecutor} is used either way; the
 * {@code gpuEnabled} flag controls whether tasks actually offload.</p>
 */
public final class RealGpuKernelEngine {

    private final GpuTaskExecutor executor;

    /** Construct with auto-detection. */
    public RealGpuKernelEngine() {
        this(true);  // attempt GPU; falls back internally
    }

    /** Explicit gpuEnabled flag. */
    public RealGpuKernelEngine(boolean gpuEnabled) {
        this.executor = new GpuTaskExecutor(42L, gpuEnabled);
    }

    /** Run a kernel; returns result + latency in nanoseconds. */
    public KernelResult runKernel(GpuTask task) {
        long t0 = System.nanoTime();
        try {
            var result = executor.execute(task);
            long ns = System.nanoTime() - t0;
            return new KernelResult(result, ns, executor.isGpuEnabled());
        } catch (Throwable t) {
            long ns = System.nanoTime() - t0;
            return new KernelResult(null, ns, false);
        }
    }

    /** Get executor stats for /v1/status rendering + Prometheus exporter. */
    public GpuTaskExecutor.GpuStats stats() {
        return executor.getStats();
    }

    /** Snapshot for /v1/status. */
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        var s = stats();
        m.put("backend", executor.isGpuEnabled() ? "GPU" : "CPU");
        m.put("tasks_executed", executor.getTotalTaskCount());
        m.put("total_tasks", s.totalTasks());
        m.put("success_count", s.successCount());
        m.put("timeout_count", s.timeoutCount());
        m.put("oom_count", s.oomCount());
        return m;
    }

    public record KernelResult(Object payload, long latencyNs, boolean gpuUsed) {}
}
