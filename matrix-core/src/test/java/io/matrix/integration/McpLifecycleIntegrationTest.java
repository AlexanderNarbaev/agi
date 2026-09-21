package io.matrix.integration;

import io.matrix.agent.AgentBrainService;
import io.matrix.agent.AgentLoop;
import io.matrix.agent.AgentAction;
import io.matrix.learning.ContinuousLearningLoop;
import io.matrix.lifecycle.MatrixLifecycleManager;
import io.matrix.mcp.MatrixMcpServer;
import io.matrix.rag.BooleanIndex;
import io.matrix.rag.BooleanRag;
import io.matrix.knowledge.KnowledgeGraphStore;
import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import io.matrix.mediator.scheduler.TaskScheduler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpLifecycleIntegrationTest {

    private static AgentLoop createLoop(AgentBrainService brain) {
        AgentLoop.Sensor sensor = () -> 0xABCDEL;
        AgentLoop.Effector effector = action -> AgentAction.ActionResult.success("ok", 10);
        DriverState[] drivers = {
                DriverState.withDefaults(DriverType.ENERGY),
                DriverState.withDefaults(DriverType.SAFETY),
        };
        TaskScheduler scheduler = TaskScheduler.withDefaults();
        return new AgentLoop(brain, sensor, effector, drivers, scheduler, 5);
    }

    @Test
    void mcpServerExposesLifecycleCapabilities() {
        var brain = new AgentBrainService();
        var loop = createLoop(brain);
        var clLoop = new ContinuousLearningLoop(loop, brain, 2, 3);
        var index = BooleanIndex.builder().dimensions(64).build();
        var rag = BooleanRag.builder().index(index).topK(3).build();
        var kg = KnowledgeGraphStore.builder()
                .entity(new KnowledgeGraphStore.Entity("agent_main", "agent", Map.of("role", "primary")))
                .build();

        var lifecycle = new MatrixLifecycleManager(brain, loop, clLoop, kg);
        lifecycle.initialize();
        lifecycle.train(3, 8, 6);
        lifecycle.deploy(5);

        var mcp = MatrixMcpServer.createDefault(brain, rag, kg);

        var tools = mcp.listTools();
        assertThat(tools).isNotEmpty();
        assertThat(tools.stream().map(t -> t.get("name"))).contains(
                "matrix_query", "matrix_act", "matrix_explain", "matrix_traverse");

        String brainState = mcp.readResource("matrix://brain/state");
        assertThat(brainState).contains("sensorLayer", "featureLayer", "actionLayer");

        String entities = mcp.readResource("matrix://knowledge/entities");
        assertThat(entities).contains("agent_main");

        var actResult = mcp.callTool("matrix_act", Map.of("sensorBits", "0xABCDE"));
        assertThat(actResult).containsKey("action");

        String toolsListJson = """
                {"method": "tools/list", "params": {}}""";
        String response = mcp.handleRequest(toolsListJson);
        assertThat(response).contains("tools");
    }

    @Test
    void mcpToolTraverseReturnsConnectedEntities() {
        var kg = KnowledgeGraphStore.builder()
                .entity(new KnowledgeGraphStore.Entity("start", "hub", Map.of()))
                .entity(new KnowledgeGraphStore.Entity("hop1", "node", Map.of()))
                .entity(new KnowledgeGraphStore.Entity("hop2", "node", Map.of()))
                .relation(new KnowledgeGraphStore.Relation("start", "hop1", "links", 0.8))
                .relation(new KnowledgeGraphStore.Relation("hop1", "hop2", "links", 0.6))
                .build();

        var brain = new AgentBrainService();
        var index = BooleanIndex.builder().dimensions(64).build();
        var rag = BooleanRag.builder().index(index).topK(3).build();
        var mcp = MatrixMcpServer.createDefault(brain, rag, kg);

        var result = mcp.callTool("matrix_traverse", Map.of(
                "startId", "start", "maxDepth", "2"));
        assertThat(result).containsKey("paths");
    }

    @Test
    void mcpServerHandlesInvalidRequestsGracefully() {
        var brain = new AgentBrainService();
        var index = BooleanIndex.builder().dimensions(64).build();
        var rag = BooleanRag.builder().index(index).topK(3).build();
        var kg = KnowledgeGraphStore.builder().build();

        var mcp = MatrixMcpServer.createDefault(brain, rag, kg);

        String r1 = mcp.handleRequest("");
        assertThat(r1).contains("error");

        String r2 = mcp.handleRequest("""
                {"method": "unknown/method", "params": {}}""");
        assertThat(r2).contains("error");

        // Unknown tool — may throw IllegalArgumentException
        assertThatThrownBy(() -> mcp.callTool("unknown_tool", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lifecycleManagerCompletesFullCycle() {
        var brain = new AgentBrainService();
        var loop = createLoop(brain);
        var clLoop = new ContinuousLearningLoop(loop, brain, 2, 3);
        var kg = KnowledgeGraphStore.builder()
                .entity(new KnowledgeGraphStore.Entity("agent", "agent", Map.of()))
                .build();

        var lifecycle = new MatrixLifecycleManager(brain, loop, clLoop, kg);

        var history = lifecycle.runFullLifecycle(2, 4, 6, 3, 5);
        assertThat(history).isNotEmpty();
        assertThat(history.size()).isGreaterThanOrEqualTo(4);

        // First phase is always INIT
        assertThat(history.get(0).phase()).isEqualTo(
                MatrixLifecycleManager.LifecyclePhase.INIT);

        // State is valid after full cycle
        var state = lifecycle.currentState();
        assertThat(state.avgFitness()).isBetween(0.0, 1.0);
    }
}
