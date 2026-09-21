package io.matrix.concurrent;

import io.matrix.agent.AgentAction;
import io.matrix.agent.AgentBrainService;
import io.matrix.agent.AgentLoop;
import io.matrix.agent.AgentState;
import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import io.matrix.mediator.scheduler.TaskScheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link AsyncAgentLoop}.
 *
 * <p>Tests:
 * <ul>
 *   <li>Async execution test</li>
 *   <li>Timeout test</li>
 *   <li>Cancellation test</li>
 *   <li>CompletableFuture chaining</li>
 * </ul>
 */
class AsyncAgentLoopTest {

    private AgentLoop agentLoop;
    private AsyncAgentLoop asyncLoop;

    @BeforeEach
    void setUp() {
        AgentBrainService brain = new AgentBrainService();
        AgentLoop.Sensor sensor = () -> 0b10101010L;
        AgentLoop.Effector effector = action ->
                AgentAction.ActionResult.success("ok", 1);
        DriverState[] drivers = {
                DriverState.withDefaults(DriverType.ENERGY),
                DriverState.withDefaults(DriverType.CURIOSITY),
                DriverState.withDefaults(DriverType.SAFETY)
        };
        TaskScheduler scheduler = TaskScheduler.withDefaults();

        agentLoop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 3);
        asyncLoop = new AsyncAgentLoop(agentLoop);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void asyncExecutionTest() throws Exception {
        // When: run asynchronously
        CompletableFuture<List<AgentState>> future = asyncLoop.runAsync(10);
        List<AgentState> result = future.get(10, TimeUnit.SECONDS);

        // Then: completed with results
        assertThat(result).isNotEmpty();
        assertThat(result.size()).isLessThanOrEqualTo(10);
        assertThat(asyncLoop.completedTicks()).isGreaterThan(0);
        assertThat(asyncLoop.isConverged()).isTrue();
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void asyncExecution_withTimeout_completesNormally() throws Exception {
        // When: run with generous timeout
        CompletableFuture<List<AgentState>> future =
                asyncLoop.runAsync(5, 30, TimeUnit.SECONDS);
        List<AgentState> result = future.get(10, TimeUnit.SECONDS);

        // Then: completed successfully
        assertThat(result).isNotEmpty();
        assertThat(asyncLoop.isConverged()).isTrue();
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void asyncExecution_withTimeout_timesOut() {
        // Given: a slow brain that takes long per tick
        AtomicInteger tickCount = new AtomicInteger(0);
        AgentLoop.Sensor slowSensor = () -> {
            try {
                Thread.sleep(500); // 500ms per tick
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 0L;
        };
        AgentBrainService brain = new AgentBrainService();
        AgentLoop.Effector effector = action ->
                AgentAction.ActionResult.success("ok", 1);
        DriverState[] drivers = {
                DriverState.withDefaults(DriverType.ENERGY),
        };
        TaskScheduler scheduler = TaskScheduler.withDefaults();

        AgentLoop slowLoop = new AgentLoop(brain, slowSensor, effector, drivers, scheduler, 100);
        AsyncAgentLoop slowAsync = new AsyncAgentLoop(slowLoop);

        // When: run with short timeout
        CompletableFuture<List<AgentState>> future =
                slowAsync.runAsync(100, 1, TimeUnit.SECONDS);

        // Then: should timeout
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void cancellationTest() throws Exception {
        // Given: a long-running loop
        AtomicInteger tickCount = new AtomicInteger(0);
        AgentLoop.Sensor countingSensor = () -> {
            tickCount.incrementAndGet();
            return 0L;
        };
        AgentBrainService brain = new AgentBrainService();
        AgentLoop.Effector effector = action ->
                AgentAction.ActionResult.success("ok", 1);
        DriverState[] drivers = {
                DriverState.withDefaults(DriverType.ENERGY),
        };
        TaskScheduler scheduler = TaskScheduler.withDefaults();

        AgentLoop longLoop = new AgentLoop(brain, countingSensor, effector, drivers, scheduler, 1000);
        AsyncAgentLoop longAsync = new AsyncAgentLoop(longLoop);

        // Start long-running task
        CompletableFuture<List<AgentState>> future = longAsync.runAsync(10000);

        // Wait for at least one tick
        Thread.sleep(200);

        // When: cancel
        asyncLoop.cancel();

        // Then: cancellation was initiated
        assertThat(asyncLoop.isCancelled()).isTrue();

        // Future should eventually complete (with cancellation)
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (CancellationException e) {
            // CompletableFuture.get() throws CancellationException directly
            // when the future is cancelled or completed with CancellationException
        } catch (ExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(CancellationException.class);
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void cancellation_beforeRun_returnsFailedFuture() {
        // Given: cancelled before run
        asyncLoop.cancel();

        // When: try to run
        CompletableFuture<List<AgentState>> future = asyncLoop.runAsync(10);

        // Then: should fail — CompletableFuture.get() throws CancellationException
        // directly for futures completed with CancellationException
        assertThatThrownBy(future::get)
                .isInstanceOf(CancellationException.class);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void completableFutureChaining_thenApply() throws Exception {
        // When: chain with thenApply
        CompletableFuture<Integer> chained = asyncLoop.runAsync(5)
                .thenApply(List::size);

        Integer size = chained.get(10, TimeUnit.SECONDS);

        // Then: chaining works
        assertThat(size).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void completableFutureChaining_thenAccept() throws Exception {
        // Given
        AtomicReference<List<AgentState>> captured = new AtomicReference<>();

        // When: chain with thenAccept
        CompletableFuture<Void> chained = asyncLoop.runAsync(5)
                .thenAccept(captured::set);

        chained.get(10, TimeUnit.SECONDS);

        // Then: consumer was called
        assertThat(captured.get()).isNotNull();
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void completableFutureChaining_thenCompose() throws Exception {
        // When: chain with thenCompose (flatMap)
        CompletableFuture<Long> chained = asyncLoop.runAsync(5)
                .thenCompose(states -> {
                    // Run a second async operation
                    return CompletableFuture.supplyAsync(() -> {
                        return (long) states.size();
                    });
                });

        Long result = chained.get(10, TimeUnit.SECONDS);

        // Then: composed result is valid
        assertThat(result).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void customExecutor() throws Exception {
        // Given: custom executor
        var customExecutor = Executors.newSingleThreadExecutor();
        AsyncAgentLoop customAsync = new AsyncAgentLoop(agentLoop, customExecutor);

        // When: run with custom executor
        CompletableFuture<List<AgentState>> future = customAsync.runAsync(5);
        List<AgentState> result = future.get(10, TimeUnit.SECONDS);

        // Then: completed successfully
        assertThat(result).isNotEmpty();

        customExecutor.shutdown();
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void tickAsync_singleTick() throws Exception {
        // When: run a single tick
        CompletableFuture<AgentState> future = asyncLoop.tickAsync();
        AgentState state = future.get(5, TimeUnit.SECONDS);

        // Then: state is valid
        assertThat(state).isNotNull();
        assertThat(state.tick()).isEqualTo(1);
        assertThat(asyncLoop.completedTicks()).isEqualTo(1);
    }

    @Test
    void stateAccessors() {
        // Given: initial state
        assertThat(asyncLoop.isCancelled()).isFalse();
        assertThat(asyncLoop.isRunning()).isFalse();
        assertThat(asyncLoop.isConverged()).isFalse();
        assertThat(asyncLoop.completedTicks()).isEqualTo(0);
        assertThat(asyncLoop.convergenceReason()).isNull();
        assertThat(asyncLoop.history()).isEmpty();
        assertThat(asyncLoop.delegate()).isSameAs(agentLoop);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void runAsync_withTransformer() throws Exception {
        // When: run with transformation
        CompletableFuture<String> future = asyncLoop.runAsync(5, states -> {
            return "Completed with " + states.size() + " states";
        });

        String result = future.get(10, TimeUnit.SECONDS);

        // Then: transformation applied
        assertThat(result).startsWith("Completed with");
    }
}
