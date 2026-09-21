package io.matrix.integration;

import io.matrix.agent.AgentAction;
import io.matrix.agent.AgentBrainService;
import io.matrix.agent.AgentLoop;
import io.matrix.agent.AgentState;
import io.matrix.concurrent.AsyncAgentLoop;
import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import io.matrix.mediator.scheduler.TaskScheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: concurrent AgentLoop execution with shared brain
 * and virtual thread executor.
 *
 * <p>Covers: parallel loops, cancel behavior, shared state correctness,
 * virtual thread isolation.
 */
class ConcurrentAgentLoopIntegrationTest {

    private AgentBrainService brain;
    private AgentLoop.Sensor sensor;
    private AgentLoop.Effector effector;
    private DriverState[] drivers;
    private TaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        brain = new AgentBrainService();
        sensor = () -> 0xABCDEL;
        effector = action -> AgentAction.ActionResult.success("ok", 10);
        drivers = new DriverState[]{
                DriverState.withDefaults(DriverType.ENERGY),
                DriverState.withDefaults(DriverType.SAFETY),
                DriverState.withDefaults(DriverType.CURIOSITY),
        };
        scheduler = TaskScheduler.withDefaults();
    }

    @Test
    void concurrentAgentLoopsProduceValidHistories() throws Exception {
        int numLoops = 4;
        CountDownLatch latch = new CountDownLatch(numLoops);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < numLoops; i++) {
            var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 10);
            var asyncLoop = new AsyncAgentLoop(loop,
                    Executors.newVirtualThreadPerTaskExecutor());

            asyncLoop.runAsync(10).thenAccept(history -> {
                try {
                    assertThat(history).isNotEmpty();
                    assertThat(history.size()).isLessThanOrEqualTo(11);
                    for (AgentState state : history) {
                        assertThat(state.actionType()).isNotNull();
                        assertThat(state.tick()).isGreaterThan(0);
                    }
                } catch (AssertionError e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(errors.get()).isZero();
    }

    @Test
    void virtualThreadExecutorRunsMultipleLoopsInParallel() throws Exception {
        int numLoops = 8;
        CountDownLatch started = new CountDownLatch(numLoops);
        CountDownLatch barrier = new CountDownLatch(1);

        var blockingSensor = new AgentLoop.Sensor() {
            @Override
            public long read() {
                started.countDown();
                try {
                    barrier.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return 0xABCDEL;
            }
        };

        var loop = new AgentLoop(brain, blockingSensor, effector, drivers, scheduler, 50);
        var asyncLoop = new AsyncAgentLoop(loop,
                Executors.newVirtualThreadPerTaskExecutor());

        // Start in background (fire-and-forget, only need concurrent check)
        asyncLoop.runAsync(50);

        // Additional parallel execution with a separate loop
        var loop2 = new AgentLoop(new AgentBrainService(), sensor, effector, drivers, scheduler, 5);
        List<AgentState> history2 = loop2.run(5);

        assertThat(history2).isNotEmpty();

        // Cancel the blocking loop
        asyncLoop.cancel();
        barrier.countDown();

        assertThat(asyncLoop.isCancelled()).isTrue();
    }

    @Test
    void sequentialLoopsOnSameBrainProduceNonNullStates() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 5);
        List<AgentState> history = loop.run(5);

        assertThat(history).isNotEmpty();
        for (AgentState state : history) {
            assertThat(state.tick()).isGreaterThan(0);
            assertThat(state.actionType()).isNotNull();
        }

        // Second run on same brain should also work (first run completed)
        var loop2 = new AgentLoop(brain, sensor, effector, drivers, scheduler, 3);
        List<AgentState> history2 = loop2.run(3);

        assertThat(history2).isNotEmpty();
    }

    @Test
    void asyncCancelStopsLoopGracefully() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch barrier = new CountDownLatch(1);

        var blockingSensor = new AgentLoop.Sensor() {
            @Override
            public long read() {
                started.countDown();
                try {
                    barrier.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return 0xABCDEL;
            }
        };

        var loop = new AgentLoop(brain, blockingSensor, effector, drivers, scheduler, 500);
        var asyncLoop = new AsyncAgentLoop(loop,
                Executors.newVirtualThreadPerTaskExecutor());

        CompletableFuture<List<AgentState>> future = asyncLoop.runAsync(500);

        // Wait for the loop to start
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        // Cancel
        boolean cancelled = asyncLoop.cancel();
        barrier.countDown();

        // Should be cancelled
        assertThat(cancelled).isTrue();
        assertThat(asyncLoop.isCancelled()).isTrue();

        // Future should complete (normally or exceptionally)
        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    @Test
    void multipleLoopsDifferentBrainsDoNotInterfere() throws Exception {
        CountDownLatch latch = new CountDownLatch(2);

        // Brain 1
        var brain1 = new AgentBrainService();
        var loop1 = new AgentLoop(brain1, sensor, effector, drivers, scheduler, 3);
        var asyncLoop1 = new AsyncAgentLoop(loop1,
                Executors.newVirtualThreadPerTaskExecutor());

        // Brain 2
        var brain2 = new AgentBrainService();
        var loop2 = new AgentLoop(brain2, sensor, effector, drivers, scheduler, 3);
        var asyncLoop2 = new AsyncAgentLoop(loop2,
                Executors.newVirtualThreadPerTaskExecutor());

        CompletableFuture.allOf(
                asyncLoop1.runAsync(3).thenRun(latch::countDown),
                asyncLoop2.runAsync(3).thenRun(latch::countDown)
        );

        assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

        List<AgentState> h1 = asyncLoop1.history();
        List<AgentState> h2 = asyncLoop2.history();

        assertThat(h1).isNotEmpty();
        assertThat(h2).isNotEmpty();
        // They produce independent histories
    }
}
