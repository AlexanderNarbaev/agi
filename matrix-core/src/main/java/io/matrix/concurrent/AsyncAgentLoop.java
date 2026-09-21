package io.matrix.concurrent;

import io.matrix.agent.AgentAction;
import io.matrix.agent.AgentBrainService;
import io.matrix.agent.AgentLoop;
import io.matrix.agent.AgentState;
import io.matrix.mediator.DriverState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Async version of {@link AgentLoop} using {@link CompletableFuture}
 * backed by virtual threads (JEP 444).
 *
 * <p>Provides a non-blocking Observe → Think → Act cycle with:
 * <ul>
 *   <li>Non-blocking execution via CompletableFuture on virtual threads</li>
 *   <li>Configurable timeout per tick and total execution</li>
 *   <li>Cancellation support with graceful shutdown</li>
 *   <li>CompletionStage chaining for reactive pipelines</li>
 * </ul>
 *
 * <p>Ref: Phase8 — Multithreading & Concurrency (virtual threads)
 */
public final class AsyncAgentLoop {

    private final AgentLoop delegate;
    private final Executor executor;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<CompletableFuture<List<AgentState>>> runningTask =
            new AtomicReference<>(null);
    private final AtomicLong completedTicks = new AtomicLong(0);

    /**
     * Creates an async agent loop with a virtual-thread-per-task executor.
     *
     * @param delegate the underlying AgentLoop
     */
    public AsyncAgentLoop(AgentLoop delegate) {
        this(delegate, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Creates an async agent loop with a custom executor.
     *
     * @param delegate the underlying AgentLoop
     * @param executor executor for async execution
     */
    public AsyncAgentLoop(AgentLoop delegate, Executor executor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Runs the agent loop asynchronously.
     *
     * @param maxIterations maximum number of iterations
     * @return CompletableFuture with the state history
     */
    public CompletableFuture<List<AgentState>> runAsync(int maxIterations) {
        if (!cancelled.compareAndSet(false, false)) {
            return CompletableFuture.failedFuture(
                    new CancellationException("AsyncAgentLoop has been cancelled"));
        }

        CompletableFuture<List<AgentState>> future = CompletableFuture.supplyAsync(() -> {
            List<AgentState> result = delegate.run(maxIterations);
            completedTicks.set(delegate.tickCount());
            return result;
        }, executor);

        runningTask.set(future);
        return future;
    }

    /**
     * Runs the agent loop asynchronously with a timeout.
     *
     * @param maxIterations maximum number of iterations
     * @param timeout       timeout value
     * @param unit          timeout unit
     * @return CompletableFuture with the state history
     */
    public CompletableFuture<List<AgentState>> runAsync(int maxIterations,
                                                         long timeout, TimeUnit unit) {
        if (!cancelled.compareAndSet(false, false)) {
            return CompletableFuture.failedFuture(
                    new CancellationException("AsyncAgentLoop has been cancelled"));
        }

        CompletableFuture<List<AgentState>> future = CompletableFuture.supplyAsync(() -> {
            if (cancelled.get()) {
                throw new CancellationException("Cancelled before execution");
            }
            List<AgentState> result = delegate.run(maxIterations);
            completedTicks.set(delegate.tickCount());
            return result;
        }, executor);

        // Apply timeout
        CompletableFuture<List<AgentState>> withTimeout = future.orTimeout(timeout, unit);
        runningTask.set(withTimeout);
        return withTimeout;
    }

    /**
     * Runs a single tick asynchronously.
     *
     * @return CompletableFuture with the resulting state
     */
    public CompletableFuture<AgentState> tickAsync() {
        if (cancelled.get()) {
            return CompletableFuture.failedFuture(
                    new CancellationException("AsyncAgentLoop has been cancelled"));
        }

        return CompletableFuture.supplyAsync(() -> {
            AgentState state = delegate.tick();
            completedTicks.incrementAndGet();
            return state;
        }, executor);
    }

    /**
     * Chains a transformation on the async result.
     *
     * @param maxIterations maximum iterations
     * @param transformer   function to transform the result
     * @return transformed CompletableFuture
     */
    public <T> CompletableFuture<T> runAsync(int maxIterations,
                                              Function<List<AgentState>, T> transformer) {
        return runAsync(maxIterations).thenApplyAsync(transformer, executor);
    }

    /**
     * Cancels the running task gracefully.
     *
     * @return true if cancellation was initiated
     */
    public boolean cancel() {
        cancelled.set(true);
        delegate.stop();

        CompletableFuture<List<AgentState>> task = runningTask.get();
        if (task != null && !task.isDone()) {
            task.cancel(true);
            return true;
        }
        return false;
    }

    /**
     * Returns true if this loop has been cancelled.
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Returns true if the loop is currently running.
     */
    public boolean isRunning() {
        return delegate.isRunning();
    }

    /**
     * Returns true if the loop has converged.
     */
    public boolean isConverged() {
        return delegate.isConverged();
    }

    /**
     * Returns the number of completed ticks.
     */
    public long completedTicks() {
        return completedTicks.get();
    }

    /**
     * Returns the convergence reason, or null if not converged.
     */
    public AgentLoop.ConvergenceReason convergenceReason() {
        return delegate.convergenceReason();
    }

    /**
     * Returns the state history.
     */
    public List<AgentState> history() {
        return delegate.history();
    }

    /**
     * Returns the underlying delegate loop.
     */
    public AgentLoop delegate() {
        return delegate;
    }
}
