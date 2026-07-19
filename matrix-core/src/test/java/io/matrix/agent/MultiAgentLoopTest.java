package io.matrix.agent;

import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import io.matrix.mediator.scheduler.TaskScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MultiAgentLoopTest {

    private AgentBrainService brain;
    private DriverState[] drivers;
    private TaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        brain = new AgentBrainService();
        drivers = new DriverState[] {
                DriverState.withDefaults(DriverType.ENERGY),
                DriverState.withDefaults(DriverType.SAFETY),
                DriverState.withDefaults(DriverType.CURIOSITY),
        };
        scheduler = TaskScheduler.withDefaults();
    }

    // ── Helpers ──

    /** Creates an AgentLoop with a fixed sensor and successful effector. */
    private AgentLoop createAgent(long sensorBits) {
        AgentLoop.Sensor sensor = () -> sensorBits;
        AgentLoop.Effector effector =
                action -> AgentAction.ActionResult.success("ok", 1);
        return new AgentLoop(brain, sensor, effector, drivers, scheduler);
    }

    /** Creates 3 agents with different sensor bits → different actions. */
    private List<AgentLoop> createThreeAgents() {
        // Different sensor bits produce different brain outputs → different actions
        List<AgentLoop> agents = new ArrayList<>();
        agents.add(createAgent(0xA0L));    // Agent 0
        agents.add(createAgent(0xB1L));    // Agent 1
        agents.add(createAgent(0xC2L));    // Agent 2
        return agents;
    }

    /** Creates 3 agents with identical sensor bits → same action. */
    private List<AgentLoop> createUnanimousAgents() {
        List<AgentLoop> agents = new ArrayList<>();
        agents.add(createAgent(0xABCDEL));
        agents.add(createAgent(0xABCDEL));
        agents.add(createAgent(0xABCDEL));
        return agents;
    }

    // ── Construction ──

    @Test
    void shouldCreateWithValidDependencies() {
        List<AgentLoop> agents = createThreeAgents();
        var loop = new MultiAgentLoop(agents,
                MultiAgentLoop.ConsensusMode.BYZANTINE, 2);

        assertThat(loop.agents()).hasSize(3);
        assertThat(loop.mode()).isEqualTo(MultiAgentLoop.ConsensusMode.BYZANTINE);
        assertThat(loop.quorum()).isEqualTo(2);
        assertThat(loop.tickCount()).isZero();
        assertThat(loop.history()).isEmpty();
    }

    @Test
    void shouldRejectNullAgents() {
        assertThatNullPointerException().isThrownBy(() ->
                new MultiAgentLoop(null,
                        MultiAgentLoop.ConsensusMode.BYZANTINE, 2));
    }

    @Test
    void shouldRejectNullMode() {
        assertThatNullPointerException().isThrownBy(() ->
                new MultiAgentLoop(createThreeAgents(), null, 2));
    }

    @Test
    void shouldClampNegativeQuorum() {
        var loop = new MultiAgentLoop(createThreeAgents(),
                MultiAgentLoop.ConsensusMode.BYZANTINE, -5);
        assertThat(loop.quorum()).isEqualTo(1);
    }

    // ── All consensus modes ──

    @ParameterizedTest
    @EnumSource(MultiAgentLoop.ConsensusMode.class)
    void consensusTickShouldReturnValidState(MultiAgentLoop.ConsensusMode mode) {
        var loop = new MultiAgentLoop(createThreeAgents(), mode, 2);

        MultiAgentLoop.MultiAgentState state = loop.consensusTick();

        assertThat(state).isNotNull();
        assertThat(state.tick()).isEqualTo(1);
        assertThat(state.agentStates()).hasSize(3);
        assertThat(state.consensusAction()).isNotNull();
        assertThat(state.consensusAction().type()).isNotNull();
        // chosenAgentId may be null when quorum isn't met, but action is always present
    }

    @Test
    void allAgentsShouldProduceStates() {
        var loop = new MultiAgentLoop(createThreeAgents(),
                MultiAgentLoop.ConsensusMode.BYZANTINE, 2);

        MultiAgentLoop.MultiAgentState state = loop.consensusTick();

        assertThat(state.agentStates()).hasSize(3);
        for (int i = 0; i < 3; i++) {
            AgentState agentState = state.agentStates().get(i);
            assertThat(agentState).isNotNull();
            assertThat(agentState.tick()).isPositive();
            assertThat(agentState.action()).isNotNull();
            assertThat(agentState.thought()).hasSize(AgentLoop.THOUGHT_BITS);
        }
    }

    // ── 3 agents reach consensus ──

    @Test
    void threeAgentsShouldReachConsensus() {
        var loop = new MultiAgentLoop(createThreeAgents(),
                MultiAgentLoop.ConsensusMode.BYZANTINE, 2);

        MultiAgentLoop.MultiAgentState state = loop.consensusTick();

        assertThat(state.consensusAction()).isNotNull();
        assertThat(state.consensusAction().type()).isIn(
                (Object[]) AgentAction.ActionType.values());
    }

    // ── Byzantine quorum (2/3 majority) ──

    @Test
    void byzantineQuorumShouldWorkWithTwoThirdsMajority() {
        var loop = new MultiAgentLoop(createThreeAgents(),
                MultiAgentLoop.ConsensusMode.BYZANTINE, 2);

        MultiAgentLoop.MultiAgentState state = loop.consensusTick();

        assertThat(state.consensusAction()).isNotNull();
        // The consensus action should have parameters indicating vote count
        var params = state.consensusAction().parameters();
        assertThat(params).containsKey("voteCount");
        assertThat(params).containsKey("totalAgents");
    }

    // ── Consensus action is from one of the agents ──

    @Test
    void consensusActionShouldBeFromOneOfTheAgents() {
        var loop = new MultiAgentLoop(createThreeAgents(),
                MultiAgentLoop.ConsensusMode.BYZANTINE, 2);

        MultiAgentLoop.MultiAgentState state = loop.consensusTick();

        AgentAction.ActionType consensusType = state.consensusAction().type();

        // At least one agent must have proposed this action
        boolean found = false;
        var agentStates = state.agentStates();
        for (AgentState agentState : agentStates) {
            if (agentState.actionType() == consensusType) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    // ── Run completes with non-empty history ──

    @Test
    void runShouldCompleteWithNonEmptyHistory() {
        var loop = new MultiAgentLoop(createThreeAgents(),
                MultiAgentLoop.ConsensusMode.BYZANTINE, 2);

        List<MultiAgentLoop.MultiAgentState> history = loop.run(5);

        assertThat(history).isNotEmpty();
        assertThat(history).hasSize(5);
        assertThat(loop.tickCount()).isEqualTo(5);

        // Verify each entry has all required fields
        for (MultiAgentLoop.MultiAgentState s : history) {
            assertThat(s.tick()).isPositive();
            assertThat(s.agentStates()).isNotEmpty();
            assertThat(s.consensusAction()).isNotNull();
        }
    }

    // ── Unanimous agents produce consistent consensus ──

    @Test
    void unanimousAgentsShouldAlwaysReachConsensus() {
        List<AgentLoop> agents = createUnanimousAgents();
        var loop = new MultiAgentLoop(agents,
                MultiAgentLoop.ConsensusMode.BYZANTINE, 2);

        List<MultiAgentLoop.MultiAgentState> history = loop.run(3);

        assertThat(history).hasSize(3);
        // With identical sensors, all agents should have the same action type
        for (MultiAgentLoop.MultiAgentState s : history) {
            AgentAction.ActionType expectedType =
                    s.agentStates().get(0).actionType();
            for (AgentState agentState : s.agentStates()) {
                assertThat(agentState.actionType()).isEqualTo(expectedType);
            }
        }
    }

    // ── Weighted consensus mode ──

    @Test
    void weightedConsensusShouldProduceResult() {
        var loop = new MultiAgentLoop(createThreeAgents(),
                MultiAgentLoop.ConsensusMode.WEIGHTED, 2);

        MultiAgentLoop.MultiAgentState state = loop.consensusTick();

        assertThat(state.consensusAction()).isNotNull();
        assertThat(state.consensusAction().parameters()).containsKey("weighted");
    }

    // ── Debate consensus mode ──

    @Test
    void debateConsensusShouldProduceResult() {
        var loop = new MultiAgentLoop(createThreeAgents(),
                MultiAgentLoop.ConsensusMode.DEBATE, 2);

        MultiAgentLoop.MultiAgentState state = loop.consensusTick();

        assertThat(state.consensusAction()).isNotNull();
        assertThat(state.consensusAction().parameters()).containsKey("debate");
    }

    // ── Run across all modes ──

    @ParameterizedTest
    @EnumSource(MultiAgentLoop.ConsensusMode.class)
    void runShouldWorkForAllModes(MultiAgentLoop.ConsensusMode mode) {
        var loop = new MultiAgentLoop(createThreeAgents(), mode, 2);

        List<MultiAgentLoop.MultiAgentState> history = loop.run(3);

        assertThat(history).hasSize(3);
        assertThat(loop.tickCount()).isEqualTo(3);
    }

    // ── Tick counter increments ──

    @Test
    void tickCounterShouldIncrement() {
        var loop = new MultiAgentLoop(createThreeAgents(),
                MultiAgentLoop.ConsensusMode.BYZANTINE, 2);

        assertThat(loop.tickCount()).isZero();
        loop.consensusTick();
        assertThat(loop.tickCount()).isEqualTo(1);
        loop.consensusTick();
        assertThat(loop.tickCount()).isEqualTo(2);
    }

    // ── History is ordered by tick ──

    @Test
    void historyShouldBeOrderedByTick() {
        var loop = new MultiAgentLoop(createThreeAgents(),
                MultiAgentLoop.ConsensusMode.BYZANTINE, 2);

        List<MultiAgentLoop.MultiAgentState> history = loop.run(5);

        for (int i = 1; i < history.size(); i++) {
            assertThat(history.get(i).tick())
                    .isGreaterThan(history.get(i - 1).tick());
        }
    }
}
