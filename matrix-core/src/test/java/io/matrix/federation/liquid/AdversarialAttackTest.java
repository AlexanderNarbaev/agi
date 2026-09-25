package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W593 — Stress Test: Adversarial Attack.
 *
 * Inject "poisoned" nodes, verify Guardian veto and Sybil resistance.
 */
class AdversarialAttackTest {

    @Test
    void testSybilAttackResistance() {
        SybilResistance sybil = new SybilResistance();
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();

        // Register legitimate nodes
        for (long i = 1; i <= 5; i++) {
            sybil.registerNode(i, 5);
            assigner.registerNode(i);
            assigner.forceTransition(i, NodeRole.ADULT, "test");
        }

        // Register Sybil attacker
        long attackerId = 100;
        sybil.registerNode(attackerId, 0); // Low capability

        // Attacker tries to vote but is Infant
        assertFalse(assigner.getRole(attackerId).canVote());
    }

    @Test
    void testBlacklistedNodeCannotVote() {
        SybilResistance sybil = new SybilResistance();
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();

        long nodeId = 1;
        sybil.registerNode(nodeId, 5);
        assigner.registerNode(nodeId);
        assigner.forceTransition(nodeId, NodeRole.ADULT, "test");

        // Blacklist the node
        sybil.blacklistNode(nodeId, "malicious behavior");

        // Cannot issue challenges
        var result = sybil.issueChallenge(nodeId, SybilResistance.ChallengeType.PROOF_OF_CAPABILITY);
        assertFalse(result.passed());
    }

    @Test
    void testGuardianVetoAgainstPoison() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        CapabilityConsensusEngine consensus = new CapabilityConsensusEngine(assigner);

        // Guardian node
        assigner.registerNode(1);
        assigner.forceTransition(1, NodeRole.GUARDIAN, "test");
        assigner.recordVote(1, true);

        // Poisoned nodes (trying to approve malicious proposal)
        for (long i = 2; i <= 10; i++) {
            assigner.registerNode(i);
            assigner.forceTransition(i, NodeRole.ADULT, "test");
            assigner.recordVote(i, true);
        }

        // All poisoned nodes vote YES
        String proposalId = consensus.createProposal(System.nanoTime() + 1_000_000_000L);
        for (long nodeId = 2; nodeId <= 10; nodeId++) {
            consensus.vote(proposalId, nodeId, CapabilityConsensusEngine.VoteDecision.YES, 0.9, "test");
        }

        // Guardian VETOs
        consensus.vote(proposalId, 1, CapabilityConsensusEngine.VoteDecision.VETO, 1.0, "ethics");

        var result = consensus.evaluate(proposalId);
        assertEquals(CapabilityConsensusEngine.ConsensusStatus.VETOED, result.status());
    }

    @Test
    void testRateLimiting() {
        SybilResistance sybil = new SybilResistance();
        sybil.registerNode(1, 0); // Infant: 5 actions/sec

        // Rate limit kicks in after 5 actions
        for (int i = 0; i < 5; i++) {
            assertTrue(sybil.checkPermission(1, "test"));
        }
        assertFalse(sybil.checkPermission(1, "test")); // 6th action blocked
    }

    @Test
    void testReputationDecay() {
        SybilResistance sybil = new SybilResistance();
        sybil.registerNode(1, 5);

        double initialRep = sybil.getProfile(1).reputationScore();

        // Negative reputation update
        sybil.updateReputation(1, -0.3);
        assertTrue(sybil.getProfile(1).reputationScore() < initialRep);
    }
}
