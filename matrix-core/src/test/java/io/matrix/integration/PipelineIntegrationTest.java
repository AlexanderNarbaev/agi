package io.matrix.integration;

import io.matrix.agent.AgentLoop;
import io.matrix.agent.AgentBrainService;
import io.matrix.agent.AgentResponse;
import io.matrix.agent.AgentTrajectoryRecorder;
import io.matrix.agent.AgentTrajectoryRecorder.AgentTrajectory;
import io.matrix.agent.AgentAction;
import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import io.matrix.mediator.scheduler.TaskScheduler;
import io.matrix.rag.BooleanIndex;
import io.matrix.rag.BooleanRag;
import io.matrix.rag.HybridBooleanRag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full pipeline integration: Guarded RAG → AgentLoop → AgentResponse → TrajectoryRecorder.
 */
class PipelineIntegrationTest {

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
    void fullPipelineRagToAgentResponseToTrajectory() {
        // Step 1: RAG retrieval
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        index.add("navigate", new long[]{0x01L});
        index.add("mine", new long[]{0x02L});
        index.add("craft", new long[]{0x04L});
        index.add("eat", new long[]{0x08L});

        BooleanRag rag = BooleanRag.builder().index(index).topK(3).build();
        BooleanRag.RagResult ragResult = rag.query(new long[]{0x01L});

        assertThat(ragResult).isNotNull();
        assertThat(ragResult.knowledgeHits()).isNotEmpty();

        // Step 2: Agent loop with observable timing
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 3);
        AgentResponse response = loop.runWithTiming(3);

        assertThat(response.requestId()).isNotNull();
        assertThat(response.answer()).contains("Completed", "ticks");
        assertThat(response.sources()).isNotEmpty();
        assertThat(response.timings().retrievalMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.timings().totalMs()).isGreaterThanOrEqualTo(
                response.timings().retrievalMs() + response.timings().filteringMs());
        assertThat(response.durationMs()).isEqualTo(response.timings().totalMs());

        // Step 3: Trajectory recording via static ThreadLocal API
        var loop2 = new AgentLoop(brain, sensor, effector, drivers, scheduler, 2);
        var history = loop2.run(2);

        for (var state : history) {
            AgentTrajectoryRecorder.recordStep(
                    (int) state.tick(), state.actionType().name(),
                    state.observation(), 0, true);
        }

        var requestId = UUID.randomUUID();
        var trajectory = AgentTrajectoryRecorder.finishSession(requestId, "test-agent");

        assertThat(trajectory).isNotNull();
        assertThat(trajectory.steps()).hasSize(history.size());
        assertThat(trajectory.requestId()).isEqualTo(requestId);
    }

    @Test
    void guardedRagBlocksGenerationOnMissingTechnicalTerms() {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        index.add("api_auth_controller", new long[]{0x01L});
        index.add("database_schema", new long[]{0x02L});

        HybridBooleanRag rag = HybridBooleanRag.builder()
                .index(index)
                .topK(5)
                .adaptiveContext(true)
                .build();

        var result = rag.guardedQuery(
                new long[]{0x01L},
                "How does the PaymentGatewayController handle REFUND_TYPE?"
        );

        assertThat(result).isNotNull();
        assertThat(result.generationAllowed()).isFalse();
        assertThat(result.guardResult().missingTerms()).isNotEmpty();
        assertThat(result.guardResult().missingTerms())
                .contains("PaymentGatewayController");
    }

    @Test
    void guardedRagAllowsGenerationWhenTermsMatch() {
        BooleanIndex index = BooleanIndex.builder().dimensions(64).build();
        index.add("UserService", new long[]{0x01L});
        index.add("TokenValidator", new long[]{0x02L});

        HybridBooleanRag rag = HybridBooleanRag.builder()
                .index(index)
                .topK(5)
                .build();

        var result = rag.guardedQuery(
                new long[]{0x01L},
                "How does UserService interact with TokenValidator?"
        );

        assertThat(result).isNotNull();
        assertThat(result.generationAllowed()).isTrue();
        assertThat(result.guardResult().missingTerms()).isEmpty();
    }

    @Test
    void trajectoryRecorderReplayDetectsDivergence() {
        // Record reference trajectory
        var loop1 = new AgentLoop(brain, sensor, effector, drivers, scheduler, 2);
        var history1 = loop1.run(2);

        for (var state : history1) {
            AgentTrajectoryRecorder.recordStep(
                    (int) state.tick(), "MOVE", state.observation(), 0, true);
        }
        var reference = AgentTrajectoryRecorder.finishSession(
                UUID.randomUUID(), "agent-ref");

        // Record identical trajectory
        for (var state : history1) {
            AgentTrajectoryRecorder.recordStep(
                    (int) state.tick(), "MOVE", state.observation(), 0, true);
        }
        var matching = AgentTrajectoryRecorder.finishSession(
                UUID.randomUUID(), "agent-match");

        var result = AgentTrajectoryRecorder.replay(reference, matching);
        assertThat(result.matches()).isTrue();
        assertThat(result.stepMatchRate()).isEqualTo(1.0);
        assertThat(result.divergentSteps()).isEmpty();

        // Record divergent trajectory (first step differs)
        for (int i = 0; i < history1.size(); i++) {
            AgentTrajectoryRecorder.recordStep(
                    i, i == 0 ? "JUMP" : "MOVE", 0L, 0, true);
        }
        var divergent = AgentTrajectoryRecorder.finishSession(
                UUID.randomUUID(), "agent-div");

        var divResult = AgentTrajectoryRecorder.replay(reference, divergent);
        assertThat(divResult.matches()).isFalse();
        assertThat(divResult.divergentSteps()).isNotEmpty();
    }

    @Test
    void agentResponseTimingConsistency() {
        var loop = new AgentLoop(brain, sensor, effector, drivers, scheduler, 5);

        AgentResponse r1 = loop.runWithTiming(3);
        AgentResponse r2 = loop.runWithTiming(3);

        assertThat(r1.requestId()).isNotEqualTo(r2.requestId());
        assertThat(r1.timings().totalMs()).isGreaterThanOrEqualTo(0);
        assertThat(r2.timings().totalMs()).isGreaterThanOrEqualTo(0);

        long phaseSum1 = r1.timings().retrievalMs() + r1.timings().filteringMs();
        assertThat(r1.timings().totalMs()).isGreaterThanOrEqualTo(phaseSum1);

        String json = r1.toJson();
        AgentResponse parsed = AgentResponse.fromJson(json);
        assertThat(parsed.requestId()).isEqualTo(r1.requestId());
        assertThat(parsed.answer()).isEqualTo(r1.answer());
        assertThat(parsed.sources()).hasSize(r1.sources().size());
    }
}
