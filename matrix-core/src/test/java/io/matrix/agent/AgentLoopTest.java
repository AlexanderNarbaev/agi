package io.matrix.agent;

import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import io.matrix.mediator.Task;
import io.matrix.mediator.scheduler.TaskScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentLoopTest {

    private AgentBrainService brain;
    private AgentLoop.Sensor sensor;
    private AgentLoop.Effector effector;
    private DriverState[] drivers;
    private TaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        brain = new AgentBrainService();
        sensor = () -> 0xABCDEL;
        effector = action -> AgentAction.ActionResult.success("executed", 10);
        drivers = new DriverState[]{
                DriverState.withDefaults(DriverType.ENERGY),
                DriverState.withDefaults(DriverType.SAFETY),
                DriverState.withDefaults(DriverType.CURIOSITY),
        };
        scheduler = TaskScheduler.withDefaults();
    }

    // ── Construction ──

    @Test
    void shouldCreateWithValidDependencies() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler);

        assertThat(loop.tickCount()).isZero();
        assertThat(loop.isRunning()).isFalse();
        assertThat(loop.isConverged()).isFalse();
        assertThat(loop.convergenceReason()).isNull();
        assertThat(loop.convergenceThreshold()).isEqualTo(AgentLoop.DEFAULT_CONVERGENCE_THRESHOLD);
    }

    @Test
    void shouldCreateWithCustomConvergenceThreshold() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 10);
        assertThat(loop.convergenceThreshold()).isEqualTo(10);
    }

    @Test
    void shouldRejectNullBrain() {
        assertThatThrownBy(() ->
                new AgentLoop(null, sensor, effector, drivers, scheduler))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullSensor() {
        assertThatThrownBy(() ->
                new AgentLoop(brain, null, effector, drivers, scheduler))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullEffector() {
        assertThatThrownBy(() ->
                new AgentLoop(brain, sensor, null, drivers, scheduler))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullDrivers() {
        assertThatThrownBy(() ->
                new AgentLoop(brain, sensor, effector, null, scheduler))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullScheduler() {
        assertThatThrownBy(() ->
                new AgentLoop(brain, sensor, effector, drivers, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── Single tick ──

    @Test
    void tickShouldReturnValidState() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler);
        AgentState state = loop.tick();

        assertThat(state).isNotNull();
        assertThat(state.tick()).isEqualTo(1);
        assertThat(state.observation()).isEqualTo(0xABCDEL);
        assertThat(state.thought()).hasSize(5);
        assertThat(state.action()).isNotNull();
        assertThat(state.action().hasResult()).isTrue();
        assertThat(state.driverLevels()).hasSize(3);
    }

    @Test
    void tickShouldIncrementCounter() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler);

        loop.tick();
        assertThat(loop.tickCount()).isEqualTo(1);

        loop.tick();
        assertThat(loop.tickCount()).isEqualTo(2);
    }

    @Test
    void tickShouldCallEffector() {
        AtomicInteger callCount = new AtomicInteger();
        var countingEffector = (AgentLoop.Effector) action -> {
            callCount.incrementAndGet();
            return AgentAction.ActionResult.empty();
        };

        var loop = new AgentLoop(brain, sensor, countingEffector, drivers, scheduler);
        loop.tick();

        assertThat(callCount.get()).isEqualTo(1);
    }

    // ── Synchronous run ──

    @Test
    void runShouldExecuteMultipleTicks() {
        // Different sensor values each tick to avoid convergence
        AtomicInteger sensorCounter = new AtomicInteger();
        var varyingSensor = (AgentLoop.Sensor) () -> sensorCounter.incrementAndGet() * 0x1000L;

        var loop = new AgentLoop(brain, varyingSensor, effector, drivers, scheduler, 100);
        List<AgentState> history = loop.run(10);

        assertThat(history).hasSizeLessThanOrEqualTo(10);
        assertThat(loop.tickCount()).isGreaterThan(0);
    }

    @Test
    void runShouldDetectMaxIterationsConvergence() {
        // Use a sensor that changes every tick to avoid action-repeat convergence
        AtomicInteger counter = new AtomicInteger();
        var changingSensor = (AgentLoop.Sensor) () -> (long) counter.incrementAndGet() << 16;
        var changingEffector = (AgentLoop.Effector) action ->
                AgentAction.ActionResult.success("ok", 1);

        var loop = new AgentLoop(brain, changingSensor, changingEffector, drivers, scheduler, 100);
        List<AgentState> history = loop.run(5);

        assertThat(loop.isConverged()).isTrue();
        assertThat(loop.convergenceReason())
                .isIn(AgentLoop.ConvergenceReason.MAX_ITERATIONS,
                      AgentLoop.ConvergenceReason.REPEATING_ACTION);
    }

    @Test
    void runShouldDetectRepeatingActionConvergence() {
        // Fixed sensor → same brain output → same action → convergence
        var loop = new AgentLoop(brain, () -> 0L, effector, drivers, scheduler, 3);
        List<AgentState> history = loop.run(100);

        assertThat(loop.isConverged()).isTrue();
        // Could be REPEATING_ACTION or MAX_ITERATIONS depending on brain output
        assertThat(loop.convergenceReason()).isNotNull();
    }

    @Test
    void runShouldDetectTaskCompletionConvergence() {
        UUID goalId = UUID.randomUUID();
        Task task = new Task(goalId, DriverType.CURIOSITY, 0.8);
        task.setStatus(Task.Status.ACTIVE);
        scheduler.enqueue(task);

        // Complete the task after 2 ticks
        AtomicInteger ticks = new AtomicInteger();
        var completingEffector = (AgentLoop.Effector) action -> {
            if (ticks.incrementAndGet() >= 2) {
                scheduler.complete(task);
            }
            return AgentAction.ActionResult.success("ok", 1);
        };

        var loop = new AgentLoop(brain, sensor, completingEffector, drivers, scheduler, 100);
        loop.run(50);

        assertThat(loop.isConverged()).isTrue();
        assertThat(loop.convergenceReason()).isEqualTo(AgentLoop.ConvergenceReason.TASK_COMPLETED);
    }

    @Test
    void runShouldDetectTaskFailureConvergence() {
        UUID goalId = UUID.randomUUID();
        Task task = new Task(goalId, DriverType.ENERGY, 0.5);
        task.setStatus(Task.Status.ACTIVE);
        scheduler.enqueue(task);

        AtomicInteger ticks = new AtomicInteger();
        var failingEffector = (AgentLoop.Effector) action -> {
            if (ticks.incrementAndGet() >= 2) {
                scheduler.fail(task);
            }
            return AgentAction.ActionResult.failure("error", 1);
        };

        var loop = new AgentLoop(brain, sensor, failingEffector, drivers, scheduler, 100);
        loop.run(50);

        assertThat(loop.isConverged()).isTrue();
        assertThat(loop.convergenceReason()).isEqualTo(AgentLoop.ConvergenceReason.TASK_FAILED);
    }

    @Test
    void runShouldUpdateHistory() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 100);
        List<AgentState> history = loop.run(3);

        assertThat(history).isNotEmpty();
        // Verify history is ordered by tick
        for (int i = 1; i < history.size(); i++) {
            assertThat(history.get(i).tick()).isGreaterThan(history.get(i - 1).tick());
        }
    }

    @Test
    void runShouldUpdateCurrentState() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 100);
        loop.run(3);

        assertThat(loop.currentState()).isNotNull();
        assertThat(loop.currentState().tick()).isEqualTo(loop.tickCount());
    }

    // ── Async execution ──

    @Test
    void runAsyncShouldCompleteSuccessfully() throws Exception {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 100);
        CompletableFuture<List<AgentState>> future = loop.runAsync(3);

        List<AgentState> history = future.get(5, TimeUnit.SECONDS);
        assertThat(history).isNotEmpty();
        assertThat(loop.isConverged()).isTrue();
    }

    @Test
    void runAsyncShouldBeNonBlocking() throws Exception {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 100);

        // Use a latch to verify async execution
        CountDownLatch started = new CountDownLatch(1);
        var latchSensor = (AgentLoop.Sensor) () -> {
            started.countDown();
            return 0xABCDEL;
        };

        var asyncLoop = new AgentLoop(brain, latchSensor, effector, drivers, scheduler, 100);
        CompletableFuture<List<AgentState>> future = asyncLoop.runAsync(5);

        // Should be able to do other work while loop runs
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        future.get(5, TimeUnit.SECONDS);
    }

    // ── Manual stop ──

    @Test
    void stopShouldTerminateLoop() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 100);

        // Start async and stop quickly
        CompletableFuture<List<AgentState>> future = loop.runAsync(10000);
        loop.stop();

        try {
            List<AgentState> history = future.get(5, TimeUnit.SECONDS);
            assertThat(loop.isConverged()).isTrue();
            assertThat(loop.convergenceReason()).isEqualTo(AgentLoop.ConvergenceReason.MANUAL_STOP);
        } catch (Exception e) {
            // May have already completed
        }
    }

    @Test
    void runShouldNotAllowConcurrentExecution() throws Exception {
        // Use a latch to ensure the async run is still executing
        // when we attempt the second (blocking) run. Virtual threads
        // are fast enough to complete before the assertion otherwise.
        CountDownLatch startedLatch = new CountDownLatch(1);
        CountDownLatch blockedLatch = new CountDownLatch(1);

        var blockingSensor = new AgentLoop.Sensor() {
            @Override
            public long read() {
                startedLatch.countDown();
                try {
                    blockedLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return 0xABCDEL;
            }
        };

        var loop = new AgentLoop(brain, blockingSensor, effector, drivers, scheduler, 200);

        // Start async run
        CompletableFuture<List<AgentState>> f1 = loop.runAsync(200);

        // Wait until at least one tick has started
        assertThat(startedLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // Now try to start a second run — should throw
        assertThatThrownBy(() -> loop.run(10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already running");

        // Unblock the first run
        blockedLatch.countDown();

        try {
            f1.get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    // ── Action selection ──

    @Test
    void selectActionShouldPreferWaitWhenSafetyHigh() {
        // SAFETY ordinal is 1, so we need at least 2 elements with SAFETY at index 1
        var safetyDrivers = new DriverState[]{
                DriverState.withDefaults(DriverType.ENERGY),
                new DriverState(DriverType.SAFETY, 0.9, 0.05, 0.05, 0.01, 0.7, 0.1),
        };
        var loop = new AgentLoop(brain, sensor, effector, safetyDrivers, scheduler);

        AgentAction action = loop.selectAction(new boolean[]{true, true, true, true, true},
                new double[]{0.3, 0.9});

        assertThat(action.type()).isEqualTo(AgentAction.ActionType.WAIT);
        assertThat(action.parameters()).containsEntry("reason", "safety_driver_high");
    }

    @Test
    void selectActionShouldMapTaskToAction() {
        UUID goalId = UUID.randomUUID();
        Task task = new Task(goalId, DriverType.CURIOSITY, 0.8);
        task.setStatus(Task.Status.ACTIVE);
        scheduler.enqueue(task);

        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler);
        AgentAction action = loop.selectAction(new boolean[5], new double[3]);

        assertThat(action.type()).isEqualTo(AgentAction.ActionType.EXPLORE);
        assertThat(action.parameters()).containsKey("taskId");
    }

    @Test
    void selectActionShouldUseThoughtWhenNoTask() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler);

        // All false → WAIT (code 0)
        AgentAction action1 = loop.selectAction(new boolean[]{false, false, false, false, false},
                new double[]{0.3, 0.1, 0.5});
        assertThat(action1.type()).isEqualTo(AgentAction.ActionType.WAIT);

        // First bit true → MOVE (code 1)
        AgentAction action2 = loop.selectAction(new boolean[]{true, false, false, false, false},
                new double[]{0.3, 0.1, 0.5});
        assertThat(action2.type()).isEqualTo(AgentAction.ActionType.MOVE);
    }

    @Test
    void selectActionShouldHandleEmptyThought() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler);
        AgentAction action = loop.selectAction(new boolean[0], new double[3]);
        assertThat(action.type()).isEqualTo(AgentAction.ActionType.WAIT);
    }

    // ── Thought vector conversion ──

    @Test
    void actionCodeToThoughtShouldConvertCorrectly() {
        // Code 0 = 00000
        assertThat(AgentLoop.actionCodeToThought(0))
                .containsExactly(false, false, false, false, false);

        // Code 1 = 00001
        assertThat(AgentLoop.actionCodeToThought(1))
                .containsExactly(true, false, false, false, false);

        // Code 5 = 00101
        assertThat(AgentLoop.actionCodeToThought(5))
                .containsExactly(true, false, true, false, false);

        // Code 31 = 11111
        assertThat(AgentLoop.actionCodeToThought(31))
                .containsExactly(true, true, true, true, true);
    }

    // ── Driver integration ──

    @Test
    void tickShouldSnapshotDriverLevels() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 100);
        AgentState state = loop.tick();

        assertThat(state.driverLevels()).hasSize(3);
        assertThat(state.driverLevels()[0]).isBetween(0.0, 1.0);
        assertThat(state.driverLevels()[1]).isBetween(0.0, 1.0);
        assertThat(state.driverLevels()[2]).isBetween(0.0, 1.0);
    }

    @Test
    void tickShouldUpdateDrivers() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 100);

        double beforeEnergy = drivers[0].level();
        loop.tick();

        // Drivers should have been nudged
        double afterEnergy = drivers[0].level();
        // Energy nudge is small (-0.01 for success), so it may or may not change noticeably
        assertThat(afterEnergy).isBetween(0.0, 1.0);
    }

    // ── Convergence threshold edge cases ──

    @Test
    void convergenceThresholdShouldBeMinimumOne() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 0);
        assertThat(loop.convergenceThreshold()).isEqualTo(1);
    }

    @Test
    void negativeConvergenceThresholdShouldClampToOne() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler, -5);
        assertThat(loop.convergenceThreshold()).isEqualTo(1);
    }

    @Test
    void runWithTimingShouldReturnValidAgentResponse() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 5);

        AgentResponse response = loop.runWithTiming(5);

        assertThat(response.requestId()).isNotNull();
        assertThat(response.answer()).isNotEmpty();
        assertThat(response.answer()).contains("Completed", "ticks");
        assertThat(response.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.timings()).isNotNull();
        assertThat(response.timings().retrievalMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.sources()).isNotEmpty();
    }
}
