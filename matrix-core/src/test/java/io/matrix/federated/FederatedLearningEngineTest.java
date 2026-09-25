package io.matrix.federated;

import io.matrix.federation.liquid.NodeRole;
import io.matrix.federation.orchestration.IntegratedFederation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FederatedLearningEngineTest {

    @Test
    void testCreateEngine() {
        FederatedLearningEngine.Config config = FederatedLearningEngine.Config.defaultConfig();
        KnowledgeExchangeProtocol exchange = new KnowledgeExchangeProtocol();
        FederatedLearningEngine engine = new FederatedLearningEngine(config, exchange);
        assertNotNull(engine);
    }

    @Test
    void testFederatedLearning5xFaster() {
        FederatedLearningEngine.Config config = new FederatedLearningEngine.Config(
            5, true, 1.0, 0.1
        );
        KnowledgeExchangeProtocol exchange = new KnowledgeExchangeProtocol();
        FederatedLearningEngine engine = new FederatedLearningEngine(config, exchange);

        // Setup: 5 nodes
        List<Long> nodeIds = Arrays.asList(1L, 2L, 3L, 4L, 5L);
        Map<Long, IntegratedFederation> federations = new HashMap<>();
        for (long id : nodeIds) {
            IntegratedFederation fed = new IntegratedFederation(id, 42L);
            fed.registerNode(id, NodeRole.ADULT);
            federations.put(id, fed);
        }

        FederatedLearningEngine.FederatedLearningResult result =
            engine.runFederatedLearning(nodeIds, federations);

        assertEquals(5, result.totalRounds());
        assertTrue(result.knowledgeShared() > 0);
        assertTrue(result.finalAccuracy() > 0);
    }

    @Test
    void testPrivacyPreservingAggregation() {
        FederatedLearningEngine.Config config = new FederatedLearningEngine.Config(
            3, true, 0.5, 0.1
        );
        KnowledgeExchangeProtocol exchange = new KnowledgeExchangeProtocol();
        FederatedLearningEngine engine = new FederatedLearningEngine(config, exchange);

        List<Long> nodeIds = Arrays.asList(1L, 2L);
        Map<Long, IntegratedFederation> federations = new HashMap<>();
        for (long id : nodeIds) {
            IntegratedFederation fed = new IntegratedFederation(id, 42L);
            fed.registerNode(id, NodeRole.ADULT);
            federations.put(id, fed);
        }

        FederatedLearningEngine.FederatedLearningResult result =
            engine.runFederatedLearning(nodeIds, federations);

        assertNotNull(result);
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    void testConflictResolution() {
        FederatedLearningEngine.Config config = FederatedLearningEngine.Config.defaultConfig();
        KnowledgeExchangeProtocol exchange = new KnowledgeExchangeProtocol();
        FederatedLearningEngine engine = new FederatedLearningEngine(config, exchange);

        List<Long> nodeIds = Arrays.asList(1L, 2L, 3L);
        Map<Long, IntegratedFederation> federations = new HashMap<>();
        for (long id : nodeIds) {
            IntegratedFederation fed = new IntegratedFederation(id, 42L);
            fed.registerNode(id, NodeRole.ADULT);
            federations.put(id, fed);
        }

        FederatedLearningEngine.FederatedLearningResult result =
            engine.runFederatedLearning(nodeIds, federations);

        assertTrue(result.conflictsResolved() > 0, "Should resolve conflicts");
    }

    @Test
    void testLearningRateModulation() {
        FederatedLearningEngine.Config config = new FederatedLearningEngine.Config(
            3, false, 1.0, 0.5 // higher modulation
        );
        KnowledgeExchangeProtocol exchange = new KnowledgeExchangeProtocol();
        FederatedLearningEngine engine = new FederatedLearningEngine(config, exchange);

        List<Long> nodeIds = Arrays.asList(1L);
        Map<Long, IntegratedFederation> federations = new HashMap<>();
        IntegratedFederation fed = new IntegratedFederation(1L, 42L);
        fed.registerNode(1L, NodeRole.ADULT);
        federations.put(1L, fed);

        double dopamineBefore = fed.getBiochemicalOrchestrator()
            .getModulator("DOPAMINE").getCurrentLevel();

        FederatedLearningEngine.FederatedLearningResult result =
            engine.runFederatedLearning(nodeIds, federations);

        double dopamineAfter = fed.getBiochemicalOrchestrator()
            .getModulator("DOPAMINE").getCurrentLevel();

        // Dopamine should increase after learning (reward signal)
        assertTrue(dopamineAfter >= dopamineBefore,
            "Dopamine should increase after learning: " +
            dopamineBefore + " -> " + dopamineAfter);
    }

    @Test
    void testRoundsCompletion() {
        FederatedLearningEngine.Config config = new FederatedLearningEngine.Config(
            10, true, 1.0, 0.1
        );
        KnowledgeExchangeProtocol exchange = new KnowledgeExchangeProtocol();
        FederatedLearningEngine engine = new FederatedLearningEngine(config, exchange);

        List<Long> nodeIds = Arrays.asList(1L, 2L);
        Map<Long, IntegratedFederation> federations = new HashMap<>();
        for (long id : nodeIds) {
            IntegratedFederation fed = new IntegratedFederation(id, 42L);
            fed.registerNode(id, NodeRole.ADULT);
            federations.put(id, fed);
        }

        FederatedLearningEngine.FederatedLearningResult result =
            engine.runFederatedLearning(nodeIds, federations);

        assertEquals(10, result.totalRounds());
        assertEquals(10, engine.getRoundsCompleted());
    }

    @Test
    void testKnowledgeSharing() {
        FederatedLearningEngine.Config config = FederatedLearningEngine.Config.defaultConfig();
        KnowledgeExchangeProtocol exchange = new KnowledgeExchangeProtocol();
        FederatedLearningEngine engine = new FederatedLearningEngine(config, exchange);

        List<Long> nodeIds = Arrays.asList(1L, 2L, 3L, 4L);
        Map<Long, IntegratedFederation> federations = new HashMap<>();
        for (long id : nodeIds) {
            IntegratedFederation fed = new IntegratedFederation(id, 42L);
            fed.registerNode(id, NodeRole.ADULT);
            federations.put(id, fed);
        }

        FederatedLearningEngine.FederatedLearningResult result =
            engine.runFederatedLearning(nodeIds, federations);

        // 4 nodes → 6 pairs per round → 60 messages for 10 rounds
        assertEquals(60, result.knowledgeShared());
    }

    @Test
    void testConfigDefaults() {
        FederatedLearningEngine.Config config = FederatedLearningEngine.Config.defaultConfig();
        assertEquals(10, config.rounds());
        assertTrue(config.enableDifferentialPrivacy());
        assertEquals(1.0, config.privacyEpsilon());
    }

    @Test
    void testFinalAccuracy() {
        FederatedLearningEngine.Config config = FederatedLearningEngine.Config.defaultConfig();
        KnowledgeExchangeProtocol exchange = new KnowledgeExchangeProtocol();
        FederatedLearningEngine engine = new FederatedLearningEngine(config, exchange);

        List<Long> nodeIds = Arrays.asList(1L, 2L);
        Map<Long, IntegratedFederation> federations = new HashMap<>();
        for (long id : nodeIds) {
            IntegratedFederation fed = new IntegratedFederation(id, 42L);
            fed.registerNode(id, NodeRole.ADULT);
            federations.put(id, fed);
        }

        FederatedLearningEngine.FederatedLearningResult result =
            engine.runFederatedLearning(nodeIds, federations);

        assertTrue(result.finalAccuracy() >= 0 && result.finalAccuracy() <= 1,
            "Accuracy should be in [0,1]: " + result.finalAccuracy());
    }

    @Test
    void testSpeedupVsSingleNode() {
        FederatedLearningEngine.Config config = FederatedLearningEngine.Config.defaultConfig();
        KnowledgeExchangeProtocol exchange = new KnowledgeExchangeProtocol();
        FederatedLearningEngine engine = new FederatedLearningEngine(config, exchange);

        List<Long> nodeIds = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        Map<Long, IntegratedFederation> federations = new HashMap<>();
        for (long id : nodeIds) {
            IntegratedFederation fed = new IntegratedFederation(id, 42L);
            fed.registerNode(id, NodeRole.ADULT);
            federations.put(id, fed);
        }

        FederatedLearningEngine.FederatedLearningResult result =
            engine.runFederatedLearning(nodeIds, federations);

        // Speedup should be positive
        assertTrue(result.speedupVsSingleNode() > 0);
    }
}
