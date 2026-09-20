package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W569 — Tests for Liquid Node Roles.
 */
class LiquidNodeRoleTest {

    @Test
    void testNodeRoleHierarchy() {
        assertTrue(NodeRole.INFANT.ordinal() < NodeRole.LEARNER.ordinal());
        assertTrue(NodeRole.LEARNER.ordinal() < NodeRole.ADULT.ordinal());
        assertTrue(NodeRole.ADULT.ordinal() < NodeRole.SPECIALIST.ordinal());
        assertTrue(NodeRole.SPECIALIST.ordinal() < NodeRole.GUARDIAN.ordinal());
    }

    @Test
    void testVotingPermissions() {
        assertFalse(NodeRole.INFANT.canVote());
        assertFalse(NodeRole.LEARNER.canVote());
        assertTrue(NodeRole.ADULT.canVote());
        assertTrue(NodeRole.SPECIALIST.canVote());
        assertTrue(NodeRole.GUARDIAN.canVote());
    }

    @Test
    void testVetoPower() {
        assertFalse(NodeRole.INFANT.hasVetoPower());
        assertFalse(NodeRole.LEARNER.hasVetoPower());
        assertFalse(NodeRole.ADULT.hasVetoPower());
        assertFalse(NodeRole.SPECIALIST.hasVetoPower());
        assertTrue(NodeRole.GUARDIAN.hasVetoPower());
    }

    @Test
    void testProposalPermissions() {
        assertFalse(NodeRole.INFANT.canPropose());
        assertTrue(NodeRole.LEARNER.canPropose());
        assertTrue(NodeRole.ADULT.canPropose());
        assertTrue(NodeRole.SPECIALIST.canPropose());
        assertTrue(NodeRole.GUARDIAN.canPropose());
    }

    @Test
    void testRegistryModificationPermissions() {
        assertFalse(NodeRole.INFANT.canModifyRegistry());
        assertFalse(NodeRole.LEARNER.canModifyRegistry());
        assertTrue(NodeRole.ADULT.canModifyRegistry());
        assertTrue(NodeRole.SPECIALIST.canModifyRegistry());
        assertTrue(NodeRole.GUARDIAN.canModifyRegistry());
    }

    @Test
    void testEvaluateRole() {
        // Low metrics → INFANT
        assertEquals(NodeRole.INFANT, NodeRole.evaluate(0, 0.0, 0.0, 0));
        assertEquals(NodeRole.INFANT, NodeRole.evaluate(2, 0.1, 0.1, 0));

        // Medium metrics → LEARNER
        assertEquals(NodeRole.LEARNER, NodeRole.evaluate(3, 0.3, 0.5, 10));
        assertEquals(NodeRole.LEARNER, NodeRole.evaluate(4, 0.5, 0.6, 20));

        // High metrics → ADULT
        assertEquals(NodeRole.ADULT, NodeRole.evaluate(5, 0.6, 0.7, 50));

        // Very high metrics → SPECIALIST
        assertEquals(NodeRole.SPECIALIST, NodeRole.evaluate(6, 0.8, 0.85, 100));

        // Max metrics → GUARDIAN
        assertEquals(NodeRole.GUARDIAN, NodeRole.evaluate(7, 0.9, 0.95, 200));
    }

    @Test
    void testQualifiesFor() {
        // Guardian requires L7, 90% uptime, 95% accuracy, 200 contributions
        assertTrue(NodeRole.GUARDIAN.qualifiesFor(7, 0.9, 0.95, 200));
        assertFalse(NodeRole.GUARDIAN.qualifiesFor(7, 0.8, 0.95, 200)); // uptime too low
        assertFalse(NodeRole.GUARDIAN.qualifiesFor(7, 0.9, 0.9, 200));  // accuracy too low
        assertFalse(NodeRole.GUARDIAN.qualifiesFor(7, 0.9, 0.95, 100)); // contributions too low
        assertFalse(NodeRole.GUARDIAN.qualifiesFor(6, 0.9, 0.95, 200)); // level too low
    }

    @Test
    void testShouldDemote() {
        // Guardian should demote if metrics drop below 80% of thresholds
        assertFalse(NodeRole.GUARDIAN.shouldDemote(0.9, 0.95, 200));  // good
        assertTrue(NodeRole.GUARDIAN.shouldDemote(0.6, 0.95, 200));   // uptime dropped
        assertTrue(NodeRole.GUARDIAN.shouldDemote(0.9, 0.7, 200));    // accuracy dropped
        assertTrue(NodeRole.GUARDIAN.shouldDemote(0.9, 0.95, 80));    // contributions dropped
    }

    @Test
    void testNextPrevious() {
        assertEquals(NodeRole.LEARNER, NodeRole.INFANT.next());
        assertEquals(NodeRole.ADULT, NodeRole.LEARNER.next());
        assertEquals(NodeRole.SPECIALIST, NodeRole.ADULT.next());
        assertEquals(NodeRole.GUARDIAN, NodeRole.SPECIALIST.next());
        assertNull(NodeRole.GUARDIAN.next());

        assertNull(NodeRole.INFANT.previous());
        assertEquals(NodeRole.INFANT, NodeRole.LEARNER.previous());
        assertEquals(NodeRole.LEARNER, NodeRole.ADULT.previous());
    }

    @Test
    void testFromName() {
        assertEquals(NodeRole.INFANT, NodeRole.fromName("infant"));
        assertEquals(NodeRole.LEARNER, NodeRole.fromName("LEARNER"));
        assertEquals(NodeRole.ADULT, NodeRole.fromName("Adult"));
        assertEquals(NodeRole.GUARDIAN, NodeRole.fromName("guardian"));
        assertEquals(NodeRole.INFANT, NodeRole.fromName("unknown"));
        assertEquals(NodeRole.INFANT, NodeRole.fromName(null));
    }

    @Test
    void testVotingWeight() {
        assertTrue(NodeRole.INFANT.votingWeight < NodeRole.LEARNER.votingWeight);
        assertTrue(NodeRole.LEARNER.votingWeight < NodeRole.ADULT.votingWeight);
        assertTrue(NodeRole.ADULT.votingWeight < NodeRole.SPECIALIST.votingWeight);
        assertTrue(NodeRole.SPECIALIST.votingWeight < NodeRole.GUARDIAN.votingWeight);
    }

    // === LiquidNodeRoleAssigner tests ===

    @Test
    void testAssignerRegisterAndGetRole() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        assigner.registerNode(1);
        assertEquals(NodeRole.INFANT, assigner.getRole(1));
        assertEquals(1, assigner.getNodeCount());
    }

    @Test
    void testAssignerRecordVote() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        assigner.registerNode(1);

        // Record some votes
        assigner.recordVote(1, true);
        assigner.recordVote(1, true);
        assigner.recordVote(1, false);

        LiquidNodeRoleAssigner.NodeMetrics m = assigner.getMetrics(1);
        assertNotNull(m);
        assertEquals(2.0 / 3.0, m.getAccuracy(), 0.01);
    }

    @Test
    void testAssignerForceTransition() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        assigner.registerNode(1);

        var t = assigner.forceTransition(1, NodeRole.ADULT, "test");
        assertEquals(NodeRole.INFANT, t.fromRole());
        assertEquals(NodeRole.ADULT, t.toRole());
        assertEquals(NodeRole.ADULT, assigner.getRole(1));
    }

    @Test
    void testAssignerVotingWeight() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        assigner.registerNode(1);
        assigner.forceTransition(1, NodeRole.ADULT, "test");

        // No votes yet → weight is 0
        assertEquals(0.0, assigner.getVotingWeight(1));

        // Add votes
        assigner.recordVote(1, true);
        assigner.recordVote(1, true);
        assigner.recordVote(1, true);
        assigner.recordVote(1, false);

        double weight = assigner.getVotingWeight(1);
        assertTrue(weight > 0);
        assertTrue(weight <= NodeRole.ADULT.votingWeight);
    }

    @Test
    void testAssignerGetNodesByRole() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        assigner.registerNode(1);
        assigner.registerNode(2);
        assigner.registerNode(3);

        assigner.forceTransition(2, NodeRole.ADULT, "test");
        assigner.forceTransition(3, NodeRole.ADULT, "test");

        assertEquals(2, assigner.getNodesByRole(NodeRole.ADULT).size());
        assertEquals(1, assigner.getNodesByRole(NodeRole.INFANT).size());
        assertEquals(0, assigner.getNodesByRole(NodeRole.GUARDIAN).size());
    }

    @Test
    void testAssignerTransitionLog() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        assigner.registerNode(1);

        assigner.forceTransition(1, NodeRole.LEARNER, "step1");
        assigner.forceTransition(1, NodeRole.ADULT, "step2");

        var log = assigner.getTransitionLog();
        assertEquals(2, log.size());
        assertEquals(NodeRole.INFANT, log.get(0).fromRole());
        assertEquals(NodeRole.LEARNER, log.get(0).toRole());
        assertEquals(NodeRole.LEARNER, log.get(1).fromRole());
        assertEquals(NodeRole.ADULT, log.get(1).toRole());
    }

    @Test
    void testAssignerVetoPower() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        assigner.registerNode(1);

        assertFalse(assigner.hasVetoPower(1));

        assigner.forceTransition(1, NodeRole.GUARDIAN, "test");
        assertTrue(assigner.hasVetoPower(1));
    }
}
