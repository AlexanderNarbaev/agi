package io.matrix.integration;

import io.matrix.agent.AgentBrainService;
import io.matrix.agent.AgentLoop;
import io.matrix.agent.AgentAction;
import io.matrix.agent.MultiAgentLoop;
import io.matrix.compression.SimdBooleanCompressor;
import io.matrix.knowledge.KnowledgeGraphStore;
import io.matrix.learning.ContinuousLearningLoop;
import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import io.matrix.mediator.scheduler.TaskScheduler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: validates L.1-L.5 components working together.
 */
class L1toL5IntegrationTest {

    @Test
    void knowledgeGraphToExplainabilityToCompressionPipeline() {
        // L.1: Knowledge Graph
        var graph = KnowledgeGraphStore.builder()
                .entity(new KnowledgeGraphStore.Entity("sensor_north", "sensor", java.util.Map.of()))
                .entity(new KnowledgeGraphStore.Entity("action_move", "action", java.util.Map.of()))
                .relation(new KnowledgeGraphStore.Relation("sensor_north", "action_move", "triggers", 0.9))
                .build();

        assertThat(graph.entityCount()).isEqualTo(2);
        assertThat(graph.relationCount()).isEqualTo(1);

        // L.4: Explainability via brain layer
        AgentBrainService brain = new AgentBrainService();
        var featureLayer = brain.brain().featureLayer();
        assertThat(featureLayer).isNotNull();
        assertThat(featureLayer.neurons()).isNotEmpty();

        // L.5: SIMD compression roundtrip
        boolean[] bits = new boolean[64];
        bits[0] = true; bits[10] = true; bits[63] = true;
        long[] packed = SimdBooleanCompressor.packSIMD(bits);
        assertThat(packed).hasSize(1);
        boolean[] unpacked = SimdBooleanCompressor.unpackSIMD(packed, 64);
        assertThat(unpacked).hasSize(64);
        assertThat(unpacked[0]).isTrue();
        assertThat(unpacked[63]).isTrue();
    }

    @Test
    void multiAgentContinuousLearningIntegration() {
        // L.2: Multi-agent setup
        AgentBrainService brain1 = new AgentBrainService();
        AgentBrainService brain2 = new AgentBrainService();
        AgentBrainService brain3 = new AgentBrainService();

        AgentLoop.Sensor sensor = () -> 0xABCDEL;
        AgentLoop.Effector effector = action ->
                AgentAction.ActionResult.success("ok", 10);

        DriverState[] drivers = {
                DriverState.withDefaults(DriverType.ENERGY),
                DriverState.withDefaults(DriverType.SAFETY),
        };
        TaskScheduler scheduler = TaskScheduler.withDefaults();

        var loop1 = new AgentLoop(brain1, sensor, effector, drivers, scheduler, 3);
        var loop2 = new AgentLoop(brain2, sensor, effector, drivers, scheduler, 3);
        var loop3 = new AgentLoop(brain3, sensor, effector, drivers, scheduler, 3);

        var multiLoop = new MultiAgentLoop(
                List.of(loop1, loop2, loop3),
                MultiAgentLoop.ConsensusMode.WEIGHTED, 2);

        var states = multiLoop.run(3);
        assertThat(states).isNotEmpty();

        // L.3: Continuous learning on first agent
        var clLoop = new ContinuousLearningLoop(
                new AgentLoop(brain1, sensor, effector, drivers, scheduler, 2),
                brain1, 2, 3);

        var clStates = clLoop.run(4);
        assertThat(clStates).isNotEmpty();
        // At least one retrain should have happened (batch size 2, 4 ticks)
        assertThat(clLoop.retrainCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void simdLaneCountAvailable() {
        assertThat(SimdBooleanCompressor.laneCount()).isGreaterThan(0);
    }
}
