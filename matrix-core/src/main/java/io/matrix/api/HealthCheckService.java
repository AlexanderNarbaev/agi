package io.matrix.api;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RUN 129 — HealthCheck service for periodic bridge monitoring.
 *
 * <p>Runs a background thread that periodically calls
 * bridge.isLoaded() and reports failures via a callback.
 */
public final class HealthCheckService {

    private final QwenOnnxBridge bridge;
    private final long intervalMs;
    private final java.util.function.Consumer<HealthStatus> callback;
    private final ScheduledExecutorService executor;
    private final AtomicLong totalChecks = new AtomicLong();
    private final AtomicLong failedChecks = new AtomicLong();
    private final AtomicLong lastCheckNanos = new AtomicLong();
    private volatile HealthStatus lastStatus = HealthStatus.UNKNOWN;

    public enum HealthStatus { HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN }

    public HealthCheckService(QwenOnnxBridge bridge, long intervalMs,
                              java.util.function.Consumer<HealthStatus> callback) {
        this.bridge = bridge;
        this.intervalMs = intervalMs;
        this.callback = callback;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-check");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        executor.scheduleAtFixedRate(this::check, 0, intervalMs,
                TimeUnit.MILLISECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    public HealthStatus lastStatus() { return lastStatus; }
    public long totalChecks() { return totalChecks.get(); }
    public long failedChecks() { return failedChecks.get(); }
    public long lastCheckMs() {
        long n = lastCheckNanos.get();
        return n == 0 ? 0 : (System.nanoTime() - n) / 1_000_000L;
    }

    public double failureRate() {
        long total = totalChecks.get();
        return total == 0 ? 0.0 : (double) failedChecks.get() / total;
    }

    private void check() {
        totalChecks.incrementAndGet();
        try {
            boolean healthy = bridge != null && bridge.isLoaded();
            lastStatus = healthy ? HealthStatus.HEALTHY : HealthStatus.DEGRADED;
            lastCheckNanos.set(System.nanoTime());
            if (callback != null) callback.accept(lastStatus);
        } catch (Exception e) {
            failedChecks.incrementAndGet();
            lastStatus = HealthStatus.UNHEALTHY;
            if (callback != null) callback.accept(lastStatus);
        }
    }
}
