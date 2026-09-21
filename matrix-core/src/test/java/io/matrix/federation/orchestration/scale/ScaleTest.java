package io.matrix.federation.orchestration.scale;

import io.matrix.federation.liquid.NodeRole;
import io.matrix.federation.orchestration.IntegratedFederation;
import io.matrix.federation.liquid.biochemistry.StigmergyProtocol;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W711 — Scale Tests.
 *
 * Tests federation with 1000 nodes to verify performance.
 */
class ScaleTest {

    @Test
    void test1000NodeFederation() {
        long start = System.currentTimeMillis();
        IntegratedFederation fed = new IntegratedFederation(1L, 42L);

        // Register 1000 nodes
        for (long i = 1; i <= 1000; i++) {
            NodeRole role = switch ((int) (i % 5)) {
                case 0 -> NodeRole.INFANT;
                case 1 -> NodeRole.LEARNER;
                case 2 -> NodeRole.ADULT;
                case 3 -> NodeRole.SPECIALIST;
                default -> NodeRole.GUARDIAN;
            };
            fed.registerNode(i, role);
        }

        long afterRegister = System.currentTimeMillis();

        // Run 10 ticks
        long tickStart = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            fed.tick(1.0);
        }
        long afterTicks = System.currentTimeMillis();

        // Deposit pheromones from random nodes
        Random rng = new Random(42);
        for (int i = 0; i < 100; i++) {
            long nodeId = rng.nextInt(1000) + 1;
            String topic = "topic-" + (i % 5);
            fed.depositPheromone(nodeId, topic,
                StigmergyProtocol.PheromoneType.EXPLORATION, 0.5 + rng.nextDouble() * 0.5);
        }

        // Verify hot topics
        List<String> hotTopics = fed.getHotTopics(5);
        assertFalse(hotTopics.isEmpty());

        long total = System.currentTimeMillis() - start;
        long registerTime = afterRegister - start;
        long tickTime = afterTicks - afterRegister;

        System.out.println("Scale test: 1000 nodes, 10 ticks");
        System.out.println("  Registration: " + registerTime + "ms");
        System.out.println("  10 ticks: " + tickTime + "ms");
        System.out.println("  Total: " + total + "ms");

        // Verify all nodes are registered
        for (long i = 1; i <= 1000; i++) {
            assertNotNull(fed.getNodeRole(i), "Node " + i + " not registered");
        }

        // Performance assertion: 10 ticks should complete in reasonable time
        assertTrue(tickTime < 5000, "10 ticks took too long: " + tickTime + "ms");
    }

    @Test
    void testStressCascade1000Nodes() {
        IntegratedFederation fed = new IntegratedFederation(1L, 42L);

        for (long i = 1; i <= 1000; i++) {
            fed.registerNode(i, NodeRole.ADULT);
        }

        // Inject high stress
        fed.injectStimulus("CORTISOL", 0.9);

        long start = System.currentTimeMillis();
        for (int i = 0; i < 50; i++) {
            fed.tick(1.0);
        }
        long duration = System.currentTimeMillis() - start;

        // Verify dopamine decreased
        double dopamine = fed.getBiochemicalOrchestrator()
            .getModulator("DOPAMINE").getCurrentLevel();
        assertTrue(dopamine < 0.5, "Dopamine should be reduced under stress: " + dopamine);

        System.out.println("Stress cascade (1000 nodes, 50 ticks): " + duration + "ms");
        assertTrue(duration < 10000, "50 ticks should complete in under 10s: " + duration + "ms");
    }
}
