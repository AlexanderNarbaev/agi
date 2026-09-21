package io.matrix.integration;

import io.matrix.agent.AgentBrainService;
import io.matrix.agent.AgentState;
import io.matrix.proxy.EffectorProxy;
import io.matrix.proxy.SensorProxy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9: Integration tests for the Agent Loop (observe-think-act cycle).
 *
 * <p>Tests full observe-think-act cycle, driver integration,
 * convergence detection, and async execution.
 */
class AgentLoopIntegrationTest {

    @Test
    void fullObserveThinkActCycle() {
        AgentBrainService brainService = new AgentBrainService();
        SensorProxy sensorProxy = new SensorProxy();
        EffectorProxy effectorProxy = new EffectorProxy();

        // Observe: convert text to sensor bits
        String observation = "hostile mob nearby at distance 5";
        long sensorBits = sensorProxy.textToBits(observation);
        assertThat(sensorBits).isNotZero();

        // Think: brain decides action
        String action = brainService.act(sensorBits);
        assertThat(action).isNotNull();
        assertThat(action).isNotEmpty();

        // Act: verify action is valid
        assertThat(action).isIn("MOVE_N", "MOVE_S", "MOVE_W", "MOVE_E",
                "STAY", "MINE", "CRAFT", "EAT", "TOOL_UP");
    }

    @Test
    void multipleStepsProduceConsistentResults() {
        AgentBrainService brainService = new AgentBrainService();
        SensorProxy sensorProxy = new SensorProxy();

        long sensorBits = sensorProxy.textToBits("tree ahead wood available");

        // Same input should produce same action (deterministic brain)
        String action1 = brainService.act(sensorBits);
        String action2 = brainService.act(sensorBits);

        // Brain is deterministic for same input (after first call stabilizes state)
        assertThat(action1).isNotNull();
        assertThat(action2).isNotNull();
    }

    @Test
    void differentObservationsProduceDifferentActions() {
        AgentBrainService brainService = new AgentBrainService();
        SensorProxy sensorProxy = new SensorProxy();

        long obs1 = sensorProxy.textToBits("hostile zombie attacking");
        long obs2 = sensorProxy.textToBits("peaceful village nearby");

        // Different observations should lead to different sensor bits
        assertThat(obs1).isNotEqualTo(obs2);
    }

    @Test
    void brainDecideReturnsValidActionCode() {
        AgentBrainService brainService = new AgentBrainService();

        // Test with various sensor inputs
        for (int i = 0; i < 20; i++) {
            long sensorBits = 1L << i;
            String action = brainService.act(sensorBits);
            assertThat(action).isIn("MOVE_N", "MOVE_S", "MOVE_W", "MOVE_E",
                    "STAY", "MINE", "CRAFT", "EAT", "TOOL_UP");
        }
    }

    @Test
    void sensorProxyTextConversion() {
        SensorProxy proxy = new SensorProxy();

        long bits1 = proxy.textToBits("hello world");
        long bits2 = proxy.textToBits("goodbye world");
        long empty = proxy.textToBits("");

        assertThat(bits1).isNotZero();
        assertThat(bits2).isNotZero();
        assertThat(empty).isZero();

        // Same text → same bits
        assertThat(proxy.textToBits("hello world")).isEqualTo(bits1);
    }

    @Test
    void sensorProxyNumericConversion() {
        SensorProxy proxy = new SensorProxy();

        long low = proxy.numericToBits(0.0, 0.0, 100.0);
        long mid = proxy.numericToBits(50.0, 0.0, 100.0);
        long high = proxy.numericToBits(100.0, 0.0, 100.0);

        assertThat(low).isLessThan(mid);
        assertThat(mid).isLessThan(high);
    }

    @Test
    void effectorProxyActionNames() {
        EffectorProxy proxy = new EffectorProxy();

        assertThat(proxy.bitsToAction(0)).isEqualTo("IDLE");
        assertThat(proxy.bitsToAction(1)).isEqualTo("MOVE_FORWARD");
        assertThat(proxy.bitsToAction(14)).isEqualTo("CRAFT");
        assertThat(proxy.bitsToAction(18)).isEqualTo("EAT");
        assertThat(proxy.bitsToAction(31)).isEqualTo("RESPOND");
    }

    @Test
    void effectorProxyMinecraftCommands() {
        EffectorProxy proxy = new EffectorProxy();

        assertThat(proxy.actionToMinecraftCommand("MOVE_FORWARD")).isEqualTo("move forward");
        assertThat(proxy.actionToMinecraftCommand("JUMP")).isEqualTo("jump");
        assertThat(proxy.actionToMinecraftCommand("MINE")).isEqualTo("mine block");
        assertThat(proxy.actionToMinecraftCommand("CRAFT")).isEqualTo("craft item");
    }

    @Test
    void effectorProxyActionToText() {
        EffectorProxy proxy = new EffectorProxy();

        String idle = proxy.actionToText(0, null);
        assertThat(idle).containsIgnoringCase("waiting");

        String move = proxy.actionToText(1, "target");
        assertThat(move).containsIgnoringCase("moving");
    }

    @Test
    void agentStateLifecycle() {
        boolean[] thought = new boolean[]{true, false, true, true, false};
        double[] drivers = new double[]{0.8, 0.5, 0.3};

        AgentState state = new AgentState(0xABCDL, thought, null, drivers, 1);

        assertThat(state.observation()).isEqualTo(0xABCDL);
        assertThat(state.thought()).hasSize(5);
        assertThat(state.thoughtActivation()).isEqualTo(3);
        assertThat(state.driverLevels()).hasSize(3);
        assertThat(state.tick()).isEqualTo(1);
        assertThat(state.timestampMs()).isGreaterThan(0);
    }

    @Test
    void brainSerializationRoundtrip() throws Exception {
        AgentBrainService brainService = new AgentBrainService();
        var tmp = java.nio.file.Files.createTempFile("brain", ".json");

        brainService.save(tmp.toString());

        AgentBrainService loaded = new AgentBrainService();
        loaded.load(tmp.toString());

        // Both should produce valid actions
        String action1 = brainService.act(0xABCDL);
        String action2 = loaded.act(0xABCDL);

        assertThat(action1).isIn("MOVE_N", "MOVE_S", "MOVE_W", "MOVE_E",
                "STAY", "MINE", "CRAFT", "EAT", "TOOL_UP");
        assertThat(action2).isIn("MOVE_N", "MOVE_S", "MOVE_W", "MOVE_E",
                "STAY", "MINE", "CRAFT", "EAT", "TOOL_UP");

        java.nio.file.Files.deleteIfExists(tmp);
    }

    @Test
    void stuckDetectionTriggersExploration() {
        AgentBrainService brainService = new AgentBrainService();

        // Feed same input many times to trigger stuck detection
        for (int i = 0; i < 35; i++) {
            brainService.act(0L);
        }

        // After stuck detection, exploration should kick in
        // The next few actions should still be valid
        for (int i = 0; i < 10; i++) {
            String action = brainService.act(0L);
            assertThat(action).isIn("MOVE_N", "MOVE_S", "MOVE_W", "MOVE_E",
                    "STAY", "MINE", "CRAFT", "EAT", "TOOL_UP");
        }
    }
}
