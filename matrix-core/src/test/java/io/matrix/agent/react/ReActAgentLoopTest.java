package io.matrix.agent.react;

import io.matrix.agent.AgentAction;
import io.matrix.agent.AgentBrainService;
import io.matrix.agent.AgentLoop;
import io.matrix.agent.AgentState;
import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import io.matrix.mediator.Task;
import io.matrix.mediator.scheduler.TaskScheduler;
import io.matrix.memory.HierarchicalMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReActAgentLoopTest {

    private AgentBrainService brain;
    private AgentLoop.Sensor sensor;
    private AgentLoop.Effector effector;
    private DriverState[] drivers;
    private TaskScheduler scheduler;
    private HierarchicalMemory longTermMemory;
    private ReflexionMemory reflexionMemory;

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
        longTermMemory = new HierarchicalMemory(100);
        reflexionMemory = new ReflexionMemory(longTermMemory, 50);
    }

    // ── Construction ──

    @Test
    void shouldCreateWithValidDependencies() {
        var loop = new ReActAgentLoop(brain, sensor, effector, drivers, scheduler, reflexionMemory);

        assertThat(loop.tickCount()).isZero();
        assertThat(loop.isRunning()).isFalse();
        assertThat(loop.isConverged()).isFalse();
        assertThat(loop.convergenceReason()).isNull();
        assertThat(loop.reflexionMemory()).isSameAs(reflexionMemory);
        assertThat(loop.evaluator()).isNotNull();
    }

    @Test
    void shouldRejectNullBrain() {
        assertThatThrownBy(() ->
                new ReActAgentLoop(null, sensor, effector, drivers, scheduler, reflexionMemory))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullReflexionMemory() {
        assertThatThrownBy(() ->
                new ReActAgentLoop(brain, sensor, effector, drivers, scheduler, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── Single Tick (ReAct Cycle) ──

    @Test
    void tickShouldReturnValidState() {
        var loop = new ReActAgentLoop(brain, sensor, effector, drivers, scheduler, reflexionMemory);

        AgentState state = loop.tick();

        assertThat(state).isNotNull();
        assertThat(state.tick()).isEqualTo(1);
        assertThat(state.observation()).isEqualTo(0xABCDEL);
        assertThat(state.thought()).hasSize(5);
        assertThat(state.action()).isNotNull();
        assertThat(state.action().hasResult()).isTrue();
    }

    @Test
    void tickShouldProduceReasoningTrace() {
        var loop = new ReActAgentLoop(brain, sensor, effector, drivers, scheduler, reflexionMemory);

        loop.tick();

        ReasoningTrace trace = loop.currentTrace();
        assertThat(trace).isNotNull();
        assertThat(trace.tick()).isEqualTo(1);
        assertThat(trace.thought()).isNotEmpty();
        assertThat(trace.reasoningChain()).isNotEmpty();
        // Trace should also be stored in memory
        assertThat(reflexionMemory.size()).isEqualTo(1);
    }

    @Test
    void tickShouldStoreTraceInHistory() {
        var loop = new ReActAgentLoop(brain, sensor, effector, drivers, scheduler, reflexionMemory);

        loop.tick();
        loop.tick();

        assertThat(loop.traceHistory()).hasSize(2);
        assertThat(loop.traceHistory().get(0).tick()).isEqualTo(1);
        assertThat(loop.traceHistory().get(1).tick()).isEqualTo(2);
    }

    @Test
    void tickShouldIncrementCounter() {
        var loop = new ReActAgentLoop(brain, sensor, effector, drivers, scheduler, reflexionMemory);

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

        var loop = new ReActAgentLoop(brain, sensor, countingEffector, drivers, scheduler, reflexionMemory);
        loop.tick();

        assertThat(callCount.get()).isEqualTo(1);
    }

    // ── Synchronous Run ──

    @Test
    void runShouldExecuteMultipleTicks() {
        AtomicInteger sensorCounter = new AtomicInteger();
        var varyingSensor = (AgentLoop.Sensor) () -> sensorCounter.incrementAndGet() * 0x1000L;

        var loop = new ReActAgentLoop(brain, varyingSensor, effector, drivers, scheduler,
                reflexionMemory, 100, 10);
        List<AgentState> history = loop.run(10);

        assertThat(history).hasSizeLessThanOrEqualTo(10);
        assertThat(loop.tickCount()).isGreaterThan(0);
    }

    @Test
    void runShouldDetectMaxIterationsConvergence() {
        AtomicInteger counter = new AtomicInteger();
        var changingSensor = (AgentLoop.Sensor) () -> (long) counter.incrementAndGet() << 16;

        var loop = new ReActAgentLoop(brain, changingSensor, effector, drivers, scheduler,
                reflexionMemory, 100, 10);
        loop.run(5);

        assertThat(loop.isConverged()).isTrue();
        assertThat(loop.convergenceReason())
                .isIn(AgentLoop.ConvergenceReason.MAX_ITERATIONS,
                        AgentLoop.ConvergenceReason.REPEATING_ACTION);
    }

    @Test
    void runShouldStoreTracesForAllTicks() {
        AtomicInteger counter = new AtomicInteger();
        var changingSensor = (AgentLoop.Sensor) () -> (long) counter.incrementAndGet() << 16;

        var loop = new ReActAgentLoop(brain, changingSensor, effector, drivers, scheduler,
                reflexionMemory, 100, 10);
        loop.run(5);

        assertThat(loop.traceHistory()).hasSizeLessThanOrEqualTo(5);
        // Each trace should be stored in reflexion memory
        assertThat(reflexionMemory.totalEpisodesStored()).isGreaterThan(0);
    }

    @Test
    void runShouldDetectTaskCompletionConvergence() {
        UUID goalId = UUID.randomUUID();
        Task task = new Task(goalId, DriverType.CURIOSITY, 0.8);
        task.setStatus(Task.Status.ACTIVE);
        scheduler.enqueue(task);

        AtomicInteger ticks = new AtomicInteger();
        var completingEffector = (AgentLoop.Effector) action -> {
            if (ticks.incrementAndGet() >= 2) {
                scheduler.complete(task);
            }
            return AgentAction.ActionResult.success("ok", 1);
        };

        var loop = new ReActAgentLoop(brain, sensor, completingEffector, drivers, scheduler,
                reflexionMemory, 100, 10);
        loop.run(50);

        assertThat(loop.isConverged()).isTrue();
        assertThat(loop.convergenceReason()).isEqualTo(AgentLoop.ConvergenceReason.TASK_COMPLETED);
    }

    // ── Async Execution ──

    @Test
    void runAsyncShouldCompleteSuccessfully() throws Exception {
        var loop = new ReActAgentLoop(brain, sensor, effector, drivers, scheduler,
                reflexionMemory, 100, 10);
        CompletableFuture<List<AgentState>> future = loop.runAsync(3);

        List<AgentState> history = future.get(5, TimeUnit.SECONDS);
        assertThat(history).isNotEmpty();
        assertThat(loop.isConverged()).isTrue();
    }

    // ── Manual Stop ──

    @Test
    void stopShouldTerminateLoop() {
        var loop = new ReActAgentLoop(brain, sensor, effector, drivers, scheduler,
                reflexionMemory, 100, 10);

        CompletableFuture<List<AgentState>> future = loop.runAsync(10000);
        loop.stop();

        try {
            future.get(5, TimeUnit.SECONDS);
            assertThat(loop.isConverged()).isTrue();
            assertThat(loop.convergenceReason()).isEqualTo(AgentLoop.ConvergenceReason.MANUAL_STOP);
        } catch (Exception e) {
            // May have already completed
        }
    }

    @Test
    void runShouldNotAllowConcurrentExecution() {
        var loop = new ReActAgentLoop(brain, sensor, effector, drivers, scheduler,
                reflexionMemory, 100, 10);

        CompletableFuture<List<AgentState>> f1 = loop.runAsync(100);

        assertThatThrownBy(() -> loop.run(10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already running");

        try {
            f1.get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    // ── Reflexion Integration ──

    @Test
    void failuresShouldBeStoredInReflexionMemory() {
        var failingEffector = (AgentLoop.Effector) action ->
                AgentAction.ActionResult.failure("error: " + action.type(), 5);

        var loop = new ReActAgentLoop(brain, sensor, failingEffector, drivers, scheduler,
                reflexionMemory, 100, 10);
        loop.run(5);

        List<ReasoningTrace> failures = reflexionMemory.failures();
        assertThat(failures).isNotEmpty();
    }

    @Test
    void shouldProduceReadableTraceJson() {
        var loop = new ReActAgentLoop(brain, sensor, effector, drivers, scheduler, reflexionMemory);

        loop.tick();

        String json = loop.currentTrace().toJson();
        assertThat(json).contains("tick");
        assertThat(json).contains("thought");
        assertThat(json).contains("reasoningChain");
    }

    // ── Driver Integration ──

    @Test
    void tickShouldSnapshotDriverLevels() {
        var loop = new ReActAgentLoop(brain, sensor, effector, drivers, scheduler, reflexionMemory);

        AgentState state = loop.tick();

        assertThat(state.driverLevels()).hasSize(3);
        assertThat(state.driverLevels()[0]).isBetween(0.0, 1.0);
    }

    // ── AgentLoop Factory ──

    @Test
    void agentLoopToReActLoopShouldPreserveSettings() {
        var classicLoop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 7);
        var reactLoop = classicLoop.toReActLoop();

        assertThat(reactLoop).isNotNull();
        assertThat(reactLoop.convergenceThreshold()).isEqualTo(7);
        assertThat(reactLoop.reflexionMemory()).isNotNull();
    }

    @Test
    void agentLoopCreateShouldReturnClassicOrReact() {
        Object classic = AgentLoop.create(AgentLoop.LoopMode.CLASSIC, brain, sensor,
                effector, drivers, scheduler, 5);
        Object react = AgentLoop.create(AgentLoop.LoopMode.REACT, brain, sensor,
                effector, drivers, scheduler, 5);

        assertThat(classic).isInstanceOf(AgentLoop.class);
        assertThat(react).isInstanceOf(ReActAgentLoop.class);
    }
}
