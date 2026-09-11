package io.matrix.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase Z (RUN 407) — LongRunning autonomy framework.
 * Schedules periodic tasks (autonomy impulses, health checks,
 * WAL snapshots, model refreshes) on a single-threaded scheduler.
 *
 * <p>Pure data class for scheduling, side-effect-free task execution.
 * The framework tracks tick count + last-tick time + completed
 * tasks; consumers query these for telemetry.
 */
public final class LongRunningFramework {

    public record TaskSpec(
            String name,
            Runnable action,
            Duration interval
    ) {}

    public record TickReport(
            long tickCount,
            Instant startedAt,
            Instant lastTickAt,
            long tasksRun,
            String lastError
    ) {}

    private final ScheduledExecutorService executor;
    private final List<TaskSpec> tasks = new ArrayList<>();
    private final AtomicLong tickCount = new AtomicLong();
    private final AtomicLong tasksRun = new AtomicLong();
    private volatile Instant startedAt;
    private volatile Instant lastTickAt;
    private volatile String lastError;

    public LongRunningFramework() {
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "matrix-long-running");
            t.setDaemon(true);
            return t;
        });
    }

    public void register(TaskSpec task) {
        if (task == null || task.name() == null || task.action() == null) {
            throw new IllegalArgumentException("invalid task");
        }
        if (task.interval() == null || task.interval().isNegative() || task.interval().isZero()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        tasks.add(task);
    }

    public void start() {
        startedAt = Instant.now();
        for (TaskSpec spec : tasks) {
            long periodMs = spec.interval().toMillis();
            executor.scheduleAtFixedRate(
                    () -> runTaskSafely(spec),
                    periodMs, periodMs, TimeUnit.MILLISECONDS);
        }
    }

    public void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public TickReport report() {
        return new TickReport(
                tickCount.get(),
                startedAt,
                lastTickAt,
                tasksRun.get(),
                lastError);
    }

    private void runTaskSafely(TaskSpec spec) {
        try {
            spec.action().run();
            tasksRun.incrementAndGet();
            lastError = null;
        } catch (Throwable t) {
            lastError = spec.name() + ": " + t.getClass().getSimpleName()
                    + ": " + t.getMessage();
        } finally {
            tickCount.incrementAndGet();
            lastTickAt = Instant.now();
        }
    }

    /** Synchronous test helper: run all tasks once. */
    public void runOnce() {
        for (TaskSpec spec : tasks) {
            runTaskSafely(spec);
        }
    }

    public int taskCount() { return tasks.size(); }
}
