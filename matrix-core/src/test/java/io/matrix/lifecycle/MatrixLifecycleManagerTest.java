package io.matrix.lifecycle;

import io.matrix.agent.AgentAction;
import io.matrix.agent.AgentBrainService;
import io.matrix.agent.AgentLoop;
import io.matrix.knowledge.KnowledgeGraphStore;
import io.matrix.learning.ContinuousLearningLoop;
import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import io.matrix.mediator.scheduler.TaskScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatrixLifecycleManagerTest {

    private static final int HIGH_CONVERGENCE = 1000;

    private AgentBrainService brain;
    private AgentLoop agentLoop;
    private ContinuousLearningLoop learningLoop;
    private KnowledgeGraphStore knowledgeGraph;

    @BeforeEach
    void setUp() {
        brain = new AgentBrainService();
        brain.initializeRandom();

        AgentLoop.Sensor sensor = () -> 0xABCDEL;
        AgentLoop.Effector effector =
                action -> AgentAction.ActionResult.success("executed", 10);
        DriverState[] drivers = {
                DriverState.withDefaults(DriverType.ENERGY),
                DriverState.withDefaults(DriverType.SAFETY),
                DriverState.withDefaults(DriverType.CURIOSITY),
        };
        TaskScheduler scheduler = TaskScheduler.withDefaults();
        agentLoop = new AgentLoop(brain, sensor, effector, drivers, scheduler,
                HIGH_CONVERGENCE);
        learningLoop = new ContinuousLearningLoop(agentLoop, brain, 5, 3);
        knowledgeGraph = new KnowledgeGraphStore();
    }

    private MatrixLifecycleManager createManager() {
        return new MatrixLifecycleManager(brain, agentLoop, learningLoop, knowledgeGraph);
    }

    // ── Test 1: Initialize creates valid state ──

    @Test
    void shouldInitializeCreatesValidState() {
        var manager = createManager();

        var state = manager.initialize();

        assertThat(state.phase()).isEqualTo(MatrixLifecycleManager.LifecyclePhase.INIT);
        assertThat(state.epoch()).isZero();
        assertThat(state.avgFitness()).isZero();
        assertThat(state.testCount()).isZero();
        assertThat(state.uptimeMs()).isPositive();

        assertThat(manager.brain()).isNotNull();
        assertThat(manager.brain().brain()).isNotNull();
        assertThat(manager.knowledgeGraph().entityCount()).isGreaterThanOrEqualTo(2);
        assertThat(manager.history()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldInitializeIsIdempotent() {
        var manager = createManager();

        var first = manager.initialize();
        var second = manager.initialize();

        assertThat(first.phase()).isEqualTo(MatrixLifecycleManager.LifecyclePhase.INIT);
        assertThat(second.phase()).isEqualTo(MatrixLifecycleManager.LifecyclePhase.INIT);
        // Second initialize should not add duplicate entities
        assertThat(manager.knowledgeGraph().entityCount()).isEqualTo(2);
    }

    // ── Test 2: Train produces improved brain ──

    @Test
    void shouldTrainProducesDeployedPhase() {
        var manager = createManager();
        manager.initialize();

        var state = manager.train(2, 10, 20);

        assertThat(state.phase()).isEqualTo(MatrixLifecycleManager.LifecyclePhase.DEPLOYED);
        assertThat(state.epoch()).isPositive();
        assertThat(state.avgFitness()).isGreaterThanOrEqualTo(0.0);
        assertThat(state.testCount()).isPositive();
        assertThat(state.uptimeMs()).isPositive();

        assertThat(manager.brain().brain()).isNotNull();
    }

    @Test
    void shouldTrainIncrementsHistory() {
        var manager = createManager();
        manager.initialize();

        int historySizeBefore = manager.history().size();
        manager.train(1, 5, 20);
        int historySizeAfter = manager.history().size();

        // train records TRAINING state + DEPLOYED state = 2 new entries
        assertThat(historySizeAfter - historySizeBefore).isEqualTo(2);

        // History should contain TRAINING phase followed by DEPLOYED
        List<MatrixLifecycleManager.LifecycleState> history = manager.history();
        var phases = history.stream()
                .map(MatrixLifecycleManager.LifecycleState::phase)
                .toList();
        assertThat(phases).contains(
                MatrixLifecycleManager.LifecyclePhase.TRAINING,
                MatrixLifecycleManager.LifecyclePhase.DEPLOYED);
    }

    // ── Test 3: Deploy runs agent ──

    @Test
    void shouldDeployRunsAgent() {
        var manager = createManager();
        manager.initialize();
        manager.train(1, 5, 20);

        var state = manager.deploy(5);

        assertThat(state.phase()).isEqualTo(MatrixLifecycleManager.LifecyclePhase.DEPLOYED);
        assertThat(state.testCount()).isPositive();
        assertThat(state.uptimeMs()).isPositive();
    }

    @Test
    void shouldDeployReturnsDeployedPhase() {
        var manager = createManager();
        manager.initialize();
        manager.train(1, 5, 20);

        var state = manager.deploy(3);

        assertThat(state.phase()).isEqualTo(MatrixLifecycleManager.LifecyclePhase.DEPLOYED);
    }

    // ── Test 4: Monitor collects feedback ──

    @Test
    void shouldMonitorCollectsFeedback() {
        var manager = createManager();
        manager.initialize();
        manager.train(1, 5, 20);

        // Run monitor directly (skips deploy to avoid convergence flag issue)
        var state = manager.monitor(10);

        assertThat(state.phase()).isEqualTo(MatrixLifecycleManager.LifecyclePhase.DEPLOYED);
        assertThat(state.testCount()).isEqualTo(10);
        assertThat(learningLoop.retrainCount()).isGreaterThanOrEqualTo(1);
        assertThat(learningLoop.fitnessHistory()).isNotEmpty();
    }

    @Test
    void shouldMonitorRecordsAvgFitness() {
        var manager = createManager();
        manager.initialize();
        manager.train(1, 5, 20);

        var state = manager.monitor(15);

        assertThat(state.avgFitness()).isGreaterThanOrEqualTo(0.0);
        assertThat(state.avgFitness()).isLessThanOrEqualTo(1.0);
    }

    // ── Test 5: Full lifecycle completes ──

    @Test
    void shouldCompleteFullLifecycle() {
        var manager = createManager();

        var states = manager.runFullLifecycle(1, 5, 20, 3, 2);

        // runFullLifecycle returns: initialize, train, deploy, monitor, retrain
        assertThat(states).hasSize(5);

        // States should cover the expected phases
        var phases = states.stream()
                .map(MatrixLifecycleManager.LifecycleState::phase)
                .toList();
        assertThat(phases).contains(
                MatrixLifecycleManager.LifecyclePhase.INIT,
                MatrixLifecycleManager.LifecyclePhase.DEPLOYED);
    }

    @Test
    void shouldFullLifecycleProduceValidStates() {
        var manager = createManager();

        var states = manager.runFullLifecycle(1, 5, 20, 3, 2);

        for (var state : states) {
            assertThat(state.uptimeMs()).isPositive();
            assertThat(state.avgFitness()).isBetween(0.0, 1.0);
        }
    }

    // ── Test 6: State transitions are sequential ──

    @Test
    void shouldTransitionSequentially() {
        var manager = createManager();
        manager.initialize();
        manager.train(1, 5, 20);

        List<MatrixLifecycleManager.LifecycleState> history = manager.history();

        // Verify phases appear in valid order
        // Expected: INIT → TRAINING → DEPLOYED
        var phases = history.stream()
                .map(MatrixLifecycleManager.LifecycleState::phase)
                .toList();

        int initIdx = phases.indexOf(MatrixLifecycleManager.LifecyclePhase.INIT);
        int trainingIdx = phases.indexOf(MatrixLifecycleManager.LifecyclePhase.TRAINING);
        int deployedIdx = phases.indexOf(MatrixLifecycleManager.LifecyclePhase.DEPLOYED);

        assertThat(initIdx).isLessThan(trainingIdx);
        assertThat(trainingIdx).isLessThan(deployedIdx);
    }

    @Test
    void shouldTransitionFullSequence() {
        var manager = createManager();
        manager.runFullLifecycle(1, 5, 20, 3, 2);

        List<MatrixLifecycleManager.LifecycleState> history = manager.history();

        var phases = history.stream()
                .map(MatrixLifecycleManager.LifecycleState::phase)
                .toList();

        // Verify INIT appears before TRAINING appears before first DEPLOYED
        int firstInit = phases.indexOf(MatrixLifecycleManager.LifecyclePhase.INIT);
        int firstTraining = phases.indexOf(MatrixLifecycleManager.LifecyclePhase.TRAINING);
        int lastDeployed = phases.lastIndexOf(MatrixLifecycleManager.LifecyclePhase.DEPLOYED);

        assertThat(firstInit).isLessThan(firstTraining);
        assertThat(firstTraining).isLessThan(lastDeployed);
    }

    // ── Test 7: History grows correctly ──

    @Test
    void shouldGrowHistoryCorrectly() {
        var manager = createManager();
        int initial = manager.history().size();
        assertThat(initial).isEqualTo(1); // INITIAL state

        manager.initialize();
        assertThat(manager.history().size()).isEqualTo(initial + 1); // INIT

        manager.train(1, 5, 20);
        // train adds TRAINING + DEPLOYED
        assertThat(manager.history().size()).isEqualTo(initial + 1 + 2);

        // Total: INITIAL + INIT + TRAINING + DEPLOYED = 4
        assertThat(manager.history().size()).isEqualTo(4);
    }

    @Test
    void shouldHistoryContainAllPhases() {
        var manager = createManager();
        manager.runFullLifecycle(1, 5, 20, 3, 2);

        List<MatrixLifecycleManager.LifecycleState> history = manager.history();

        var allPhases = history.stream()
                .map(MatrixLifecycleManager.LifecycleState::phase)
                .distinct()
                .toList();

        assertThat(allPhases).contains(
                MatrixLifecycleManager.LifecyclePhase.INIT,
                MatrixLifecycleManager.LifecyclePhase.TRAINING,
                MatrixLifecycleManager.LifecyclePhase.DEPLOYED,
                MatrixLifecycleManager.LifecyclePhase.RETRAINING);
    }

    // ── Test 8: SHUTDOWN finalizes ──

    @Test
    void shouldShutdownIsTerminal() {
        var manager = createManager();
        manager.initialize();

        var shutdownState = manager.shutdown();

        assertThat(shutdownState.phase()).isEqualTo(MatrixLifecycleManager.LifecyclePhase.SHUTDOWN);

        // Subsequent operations should throw
        assertThatThrownBy(() -> manager.train(1, 5, 20))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldShutdownIdempotent() {
        var manager = createManager();
        manager.initialize();

        var first = manager.shutdown();
        var second = manager.shutdown();

        assertThat(first.phase()).isEqualTo(MatrixLifecycleManager.LifecyclePhase.SHUTDOWN);
        assertThat(second.phase()).isEqualTo(MatrixLifecycleManager.LifecyclePhase.SHUTDOWN);
        // History should not grow on second shutdown
        int shutdownCount = (int) manager.history().stream()
                .filter(s -> s.phase() == MatrixLifecycleManager.LifecyclePhase.SHUTDOWN)
                .count();
        assertThat(shutdownCount).isEqualTo(1);
    }

    // ── Test: Retrain transitions ──

    @Test
    void shouldRetrainBackToDeployed() {
        var manager = createManager();
        manager.initialize();
        manager.train(1, 5, 20);

        var state = manager.retrain(2);

        assertThat(state.phase()).isEqualTo(MatrixLifecycleManager.LifecyclePhase.DEPLOYED);
        assertThat(state.epoch()).isGreaterThan(0);
    }

    // ── Test: Accessors ──

    @Test
    void shouldReturnCorrectAccessors() {
        var manager = createManager();

        assertThat(manager.currentState().phase()).isEqualTo(MatrixLifecycleManager.LifecyclePhase.INIT);
        assertThat(manager.brain()).isNotNull();
        assertThat(manager.knowledgeGraph()).isNotNull();
        assertThat(manager.history()).isInstanceOf(List.class);
    }
}
