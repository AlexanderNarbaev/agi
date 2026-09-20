package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W591 — End-to-End Scenario: Child Growth.
 */
class ChildGrowthScenarioTest {

    @Test
    void testInfantToAdultProgression() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();

        long nodeId = 1;
        assigner.registerNode(nodeId);
        assertEquals(NodeRole.INFANT, assigner.getRole(nodeId));

        // Force transitions to simulate growth
        assigner.forceTransition(nodeId, NodeRole.LEARNER, "learning phase");
        assertEquals(NodeRole.LEARNER, assigner.getRole(nodeId));

        assigner.forceTransition(nodeId, NodeRole.ADULT, "adult phase");
        assertEquals(NodeRole.ADULT, assigner.getRole(nodeId));

        // Verify transition log
        List<LiquidNodeRoleAssigner.RoleTransition> log = assigner.getTransitionLog();
        assertTrue(log.size() >= 2);
    }

    @Test
    void testRoleCapabilities() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();

        long nodeId = 1;
        assigner.registerNode(nodeId);

        // Infant
        assertFalse(assigner.getRole(nodeId).canVote());
        assertFalse(assigner.getRole(nodeId).canPropose());

        // LEARNER
        assigner.forceTransition(nodeId, NodeRole.LEARNER, "test");
        assertFalse(assigner.getRole(nodeId).canVote());
        assertTrue(assigner.getRole(nodeId).canPropose());

        // ADULT
        assigner.forceTransition(nodeId, NodeRole.ADULT, "test");
        assertTrue(assigner.getRole(nodeId).canVote());
        assertTrue(assigner.getRole(nodeId).canModifyRegistry());

        // GUARDIAN
        assigner.forceTransition(nodeId, NodeRole.GUARDIAN, "test");
        assertTrue(assigner.getRole(nodeId).hasVetoPower());
    }

    @Test
    void testDemotionOnPoorPerformance() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();

        long nodeId = 1;
        assigner.registerNode(nodeId);
        assigner.forceTransition(nodeId, NodeRole.ADULT, "test");

        // Poor performance
        for (int i = 0; i < 100; i++) {
            assigner.recordVote(nodeId, i < 30);
        }

        var transition = assigner.evaluateRole(nodeId, 5);
        assertNotNull(transition);
        assertTrue(transition.toRole().ordinal() < NodeRole.ADULT.ordinal());
    }

    @Test
    void testTransitionLog() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        assigner.registerNode(1);

        assigner.forceTransition(1, NodeRole.LEARNER, "step1");
        assigner.forceTransition(1, NodeRole.ADULT, "step2");
        assigner.forceTransition(1, NodeRole.SPECIALIST, "step3");

        var log = assigner.getTransitionLog();
        assertEquals(3, log.size());
        assertEquals(NodeRole.INFANT, log.get(0).fromRole());
        assertEquals(NodeRole.LEARNER, log.get(1).fromRole());
        assertEquals(NodeRole.ADULT, log.get(2).fromRole());
    }
}
