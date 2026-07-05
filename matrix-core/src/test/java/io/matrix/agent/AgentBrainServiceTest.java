package io.matrix.agent;

import io.matrix.neuron.DecisionTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AgentBrainServiceTest {

    private static final Set<String> VALID_ACTIONS = Set.of(
            "MOVE_N", "MOVE_S", "MOVE_W", "MOVE_E", "STAY",
            "MINE", "CRAFT", "EAT", "TOOL_UP"
    );

    private AgentBrainService service;

    @BeforeEach
    void setUp() {
        service = new AgentBrainService();
    }

    @Test
    void testInitialBrainHasFiveTrees() {
        assertThat(service.moveTree()).isNotNull();
        assertThat(service.mineTree()).isNotNull();
        assertThat(service.craftTree()).isNotNull();
        assertThat(service.eatTree()).isNotNull();
        assertThat(service.toolUpTree()).isNotNull();
    }

    @Test
    void testActReturnsValidAction() {
        String action = service.act(0);
        assertThat(action).isNotNull().isNotEmpty();
        assertThat(VALID_ACTIONS).contains(action);
    }

    @Test
    void testActIsDeterministic() {
        long sensors = 0xAAAAA;
        String action1 = service.act(sensors);
        String action2 = service.act(sensors);
        assertThat(action1).isEqualTo(action2);
    }

    @Test
    void testActWithAllOnes() {
        String action = service.act(0xFFFFFL);
        assertThat(VALID_ACTIONS).contains(action);
    }

    @Test
    void testTrainDoesNotThrow() {
        assertThatCode(() -> service.train(5, 10, 4))
                .doesNotThrowAnyException();
    }

    @Test
    void testTrainImprovesFitness() {
        AgentBrainService.EvolutionResult result = service.train(20, 30, 8);
        assertThat(result).isNotNull();
        assertThat(result.bestFitness()).isGreaterThan(0);
        assertThat(result.generations()).isGreaterThan(0);
        assertThat(result.history()).isNotEmpty();
    }

    @Test
    void testActWithRandomSensors() {
        var rng = new java.util.Random(42);
        for (int i = 0; i < 100; i++) {
            long sensors = rng.nextLong() & 0xFFFFFL;
            String action = service.act(sensors);
            assertThat(VALID_ACTIONS).contains(action);
        }
    }

    @Test
    void testSaveLoadRoundtrip(@TempDir Path tempDir) throws Exception {
        long sensors = 0xAAAAA;

        Path savePath = tempDir.resolve("brain.json");
        service.save(savePath.toString());

        AgentBrainService reloaded = new AgentBrainService();
        reloaded.load(savePath.toString());
        assertThat(reloaded.act(sensors)).isEqualTo(service.act(sensors));
    }

    @Test
    void testNullSafety() {
        AgentBrainService svc = new AgentBrainService();
        assertThatCode(svc::initializeRandom).doesNotThrowAnyException();
        assertThatCode(() -> svc.act(0)).doesNotThrowAnyException();
        assertThatCode(() -> svc.act(Long.MAX_VALUE)).doesNotThrowAnyException();
    }

    @Test
    void testDecisionTreeFromTruthTable() {
        var rng = new java.util.Random(42);
        var tt = io.matrix.neuron.TruthTable.random(6, rng);
        DecisionTree tree = AgentBrainService.decisionTreeFromTruthTable(tt);
        assertThat(tree).isNotNull();

        for (int i = 0; i < (1 << 6); i++) {
            var bs = java.util.BitSet.valueOf(new long[]{i});
            assertThat(tree.evaluate(bs)).isEqualTo(tt.evaluate(i));
        }
    }
}
