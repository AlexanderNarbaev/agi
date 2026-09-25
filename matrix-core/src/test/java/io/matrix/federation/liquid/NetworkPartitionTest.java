package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W592 — Stress Test: Network Partition.
 */
class NetworkPartitionTest {

    @Test
    void testPartitionAndRecovery() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        CapabilityConsensusEngine consensus = new CapabilityConsensusEngine(assigner);

        for (long i = 1; i <= 5; i++) {
            assigner.registerNode(i);
            assigner.forceTransition(i, NodeRole.ADULT, "test");
            assigner.recordVote(i, true);
        }

        // Partition: 3 nodes vote YES, 2 abstain
        String proposalId = consensus.createProposal(System.nanoTime() + 1_000_000_000L);
        for (long nodeId = 1; nodeId <= 3; nodeId++) {
            consensus.vote(proposalId, nodeId, CapabilityConsensusEngine.VoteDecision.YES, 0.9, "test");
        }
        // Nodes 4-5 abstain (no vote)

        var result = consensus.evaluate(proposalId);
        // 3 ADULTs all voted YES → threshold is 67% of participants
        // 3/3 = 100% > 67% → APPROVED
        assertEquals(CapabilityConsensusEngine.ConsensusStatus.APPROVED, result.status());

        // Recovery: all 5 nodes vote on new proposal
        String proposalId2 = consensus.createProposal(System.nanoTime() + 1_000_000_000L);
        for (long nodeId = 1; nodeId <= 5; nodeId++) {
            consensus.vote(proposalId2, nodeId, CapabilityConsensusEngine.VoteDecision.YES, 0.9, "test");
        }

        result = consensus.evaluate(proposalId2);
        assertEquals(CapabilityConsensusEngine.ConsensusStatus.APPROVED, result.status());
    }

    @Test
    void testSplitVote() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        CapabilityConsensusEngine consensus = new CapabilityConsensusEngine(assigner);

        for (long i = 1; i <= 5; i++) {
            assigner.registerNode(i);
            assigner.forceTransition(i, NodeRole.ADULT, "test");
            assigner.recordVote(i, true);
        }

        // Split vote: 2 YES, 2 NO, 1 abstains
        String proposalId = consensus.createProposal(System.nanoTime() + 1_000_000_000L);
        consensus.vote(proposalId, 1, CapabilityConsensusEngine.VoteDecision.YES, 0.9, "test");
        consensus.vote(proposalId, 2, CapabilityConsensusEngine.VoteDecision.YES, 0.9, "test");
        consensus.vote(proposalId, 3, CapabilityConsensusEngine.VoteDecision.NO, 0.9, "test");
        consensus.vote(proposalId, 4, CapabilityConsensusEngine.VoteDecision.NO, 0.9, "test");

        var result = consensus.evaluate(proposalId);
        // 2 YES vs 2 NO → 50% < 67% → REJECTED
        assertEquals(CapabilityConsensusEngine.ConsensusStatus.REJECTED, result.status());
    }

    @Test
    void testVetoOverridesAll() {
        LiquidNodeRoleAssigner assigner = new LiquidNodeRoleAssigner();
        CapabilityConsensusEngine consensus = new CapabilityConsensusEngine(assigner);

        assigner.registerNode(1);
        assigner.forceTransition(1, NodeRole.GUARDIAN, "test");
        assigner.recordVote(1, true);

        for (long i = 2; i <= 5; i++) {
            assigner.registerNode(i);
            assigner.forceTransition(i, NodeRole.ADULT, "test");
            assigner.recordVote(i, true);
        }

        String proposalId = consensus.createProposal(System.nanoTime() + 1_000_000_000L);
        for (long nodeId = 2; nodeId <= 5; nodeId++) {
            consensus.vote(proposalId, nodeId, CapabilityConsensusEngine.VoteDecision.YES, 0.9, "test");
        }
        consensus.vote(proposalId, 1, CapabilityConsensusEngine.VoteDecision.VETO, 1.0, "ethics");

        var result = consensus.evaluate(proposalId);
        assertEquals(CapabilityConsensusEngine.ConsensusStatus.VETOED, result.status());
    }
}
