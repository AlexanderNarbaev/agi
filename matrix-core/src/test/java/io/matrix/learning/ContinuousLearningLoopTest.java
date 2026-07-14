package io.matrix.learning;

import io.matrix.agent.AgentAction;
import io.matrix.agent.AgentBrainService;
import io.matrix.agent.AgentLoop;
import io.matrix.agent.AgentState;
import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import io.matrix.mediator.scheduler.TaskScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContinuousLearningLoopTest {

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
    void shouldRejectNullLoop() {
        assertThatThrownBy(() ->
                new ContinuousLearningLoop(null, brain, 5, 3))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullBrain() {
        var loop = agentLoop();
        assertThatThrownBy(() ->
                new ContinuousLearningLoop(loop, null, 5, 3))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldDefaultBatchSizeToOne() {
        var loop = agentLoop();
        var cll = new ContinuousLearningLoop(loop, brain, 0, 3);
        assertThat(cll.feedbackBatchSize()).isEqualTo(1);
    }

    @Test
    void shouldDefaultIterationsToOne() {
        var loop = agentLoop();
        var cll = new ContinuousLearningLoop(loop, brain, 5, 0);
        assertThat(cll.onlineTrainIterations()).isEqualTo(1);
    }

    // ── Tick ──

    @Test
    void tickShouldReturnAgentState() {
        var loop = agentLoop();
        var cll = new ContinuousLearningLoop(loop, brain, 10, 3);

        AgentState state = cll.tick();

        assertThat(state).isNotNull();
        assertThat(state.observation()).isEqualTo(0xABCDEL);
        assertThat(cll.tickCount()).isEqualTo(1);
    }

    @Test
    void tickShouldAccumulateCount() {
        var loop = agentLoop();
        var cll = new ContinuousLearningLoop(loop, brain, 10, 3);

        cll.tick();
        cll.tick();
        cll.tick();

        assertThat(cll.tickCount()).isEqualTo(3);
    }

    // ── Retrain at batch boundary ──

    @Test
    void shouldTriggerRetrainAtBatchBoundary() {
        var loop = agentLoop();
        int batchSize = 3;
        var cll = new ContinuousLearningLoop(loop, brain, batchSize, 2);

        // Before batch boundary — no retrain
        cll.tick();
        cll.tick();
        assertThat(cll.retrainCount()).isZero();

        // Third tick hits batch boundary
        cll.tick();
        assertThat(cll.retrainCount()).isEqualTo(1);
        assertThat(cll.fitnessHistory()).hasSize(1);
    }

    @Test
    void shouldTriggerMultipleRetrains() {
        var loop = agentLoop();
        int batchSize = 2;
        var cll = new ContinuousLearningLoop(loop, brain, batchSize, 2);

        for (int i = 0; i < 6; i++) {
            cll.tick();
        }

        assertThat(cll.retrainCount()).isEqualTo(3);
        assertThat(cll.fitnessHistory()).hasSize(3);
    }

    @Test
    void shouldTriggerEveryTickWithBatchSizeOne() {
        var loop = agentLoop();
        var cll = new ContinuousLearningLoop(loop, brain, 1, 2);

        for (int i = 0; i < 4; i++) {
            cll.tick();
        }

        assertThat(cll.retrainCount()).isEqualTo(4);
        assertThat(cll.fitnessHistory()).hasSize(4);
    }

    // ── Fitness history ──

    @Test
    void fitnessHistoryShouldContainSuccessRate() {
        var loop = agentLoop();
        var cll = new ContinuousLearningLoop(loop, brain, 3, 2);

        // All actions succeed (effector always returns success)
        cll.tick();
        cll.tick();
        cll.tick();

        List<Double> history = cll.fitnessHistory();
        assertThat(history).hasSize(1);
        assertThat(history.get(0)).isEqualTo(1.0);
    }

    @Test
    void fitnessHistoryShouldTrackMixedSuccess() {
        // Use mixed effector: success, fail, success, fail
        AgentLoop.Sensor mixedSensor = () -> 0xABCDEL;
        AgentLoop.Effector mixedEffector = new AgentLoop.Effector() {
            private int callCount = 0;

            @Override
            public AgentAction.ActionResult execute(AgentAction action) {
                callCount++;
                boolean success = callCount % 2 == 1; // odd → success, even → fail
                return success
                        ? AgentAction.ActionResult.success("ok", 10)
                        : AgentAction.ActionResult.failure("fail", 10);
            }
        };
        var mixedLoop = new AgentLoop(brain, mixedSensor, mixedEffector, drivers, scheduler, 100);

        var cll = new ContinuousLearningLoop(mixedLoop, brain, 4, 2);

        cll.tick(); // success (1)
        cll.tick(); // fail (2)
        cll.tick(); // success (3)
        cll.tick(); // fail (4)

        List<Double> history = cll.fitnessHistory();
        assertThat(history).hasSize(1);
        assertThat(history.get(0)).isEqualTo(0.5);
    }

    // ── Run ──

    @Test
    void runShouldComplete() {
        var loop = agentLoop();
        var cll = new ContinuousLearningLoop(loop, brain, 3, 2);

        List<AgentState> states = cll.run(6);

        assertThat(states).hasSize(6);
        assertThat(cll.retrainCount()).isEqualTo(2);
        assertThat(cll.tickCount()).isEqualTo(6);
    }

    @Test
    void runShouldHandleZeroMaxTicks() {
        var loop = agentLoop();
        var cll = new ContinuousLearningLoop(loop, brain, 3, 2);

        List<AgentState> states = cll.run(0);

        assertThat(states).isEmpty();
        assertThat(cll.tickCount()).isZero();
    }

    @Test
    void runShouldHandleNegativeMaxTicks() {
        var loop = agentLoop();
        var cll = new ContinuousLearningLoop(loop, brain, 3, 2);

        List<AgentState> states = cll.run(-1);

        assertThat(states).isEmpty();
        assertThat(cll.tickCount()).isZero();
    }

    // ── Accessors ──

    @Test
    void shouldExposeConfiguration() {
        var loop = agentLoop();
        var cll = new ContinuousLearningLoop(loop, brain, 7, 13);

        assertThat(cll.feedbackBatchSize()).isEqualTo(7);
        assertThat(cll.onlineTrainIterations()).isEqualTo(13);
    }

    // ── Integration: full run with improvement tracking ──

    @Test
    void runShouldTrackFitnessAcrossBatches() {
        var loop = agentLoop();
        int batchSize = 2;
        int maxTicks = 8; // 4 batches
        var learningLoop = new ContinuousLearningLoop(loop, brain, batchSize, 2);

        List<AgentState> states = learningLoop.run(maxTicks);

        assertThat(states).hasSize(maxTicks);
        assertThat(learningLoop.retrainCount()).isEqualTo(4);
        List<Double> fitness = learningLoop.fitnessHistory();
        assertThat(fitness).hasSize(4);
        // With always-success effector, every batch should have 1.0 fitness
        assertThat(fitness).allMatch(f -> f == 1.0);
    }

    // ── Helpers ──

    private AgentLoop agentLoop() {
        return new AgentLoop(brain, sensor, effector, drivers, scheduler, 100);
    }
}
