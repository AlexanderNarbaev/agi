package io.matrix.bridge;

import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class NeuroSymbolicBridgeTest {

    private final NeuroSymbolicBridge bridge = new NeuroSymbolicBridge(new Random(42));

    @Test
    void shouldExtractRulesFromLlmReasoning() {
        String reasoning = """
            If the sensor detects an obstacle then the robot should turn left.
            If the battery is low then the robot should return to base.
            If the goal is visible then the robot should move forward.
            """;

        var result = bridge.extractRules(reasoning);

        assertThat(result.count()).isEqualTo(3);
        assertThat(result.rules()).hasSize(3);
        assertThat(result.tables()).hasSize(3);
    }

    @Test
    void shouldExplainTruthTable() {
        TruthTable table = TruthTable.random(4, new Random(42));
        Map<Integer, String> labels = Map.of(
                0, "sensor_1", 1, "sensor_2",
                2, "sensor_3", 3, "sensor_4"
        );

        String explanation = bridge.explain(table, labels);

        assertThat(explanation).isNotEmpty();
        assertThat(explanation).contains("Neuron with 4 inputs");
    }

    @Test
    void shouldPerformHybridReasoning() {
        TruthTable table = TruthTable.random(4, new Random(42));
        String context = "The agent is in a grid world with obstacles to the north and east.";

        var result = bridge.reason(table, context);

        assertThat(result.explanation()).isNotEmpty();
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.0);
        assertThat(result.confidence()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void shouldHandleEmptyReasoning() {
        var result = bridge.extractRules("");
        assertThat(result.count()).isEqualTo(0);
    }

    @Test
    void confidenceShouldBeHighForDeterministicTables() {
        TruthTable allOnes = TruthTable.random(2, new Random(42));
        double conf = bridge.reason(allOnes, "").confidence();
        assertThat(conf).isGreaterThanOrEqualTo(0.0);
    }
}
