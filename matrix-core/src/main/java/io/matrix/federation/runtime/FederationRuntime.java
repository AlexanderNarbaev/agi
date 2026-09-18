package io.matrix.federation.runtime;

import io.matrix.federation.consensus.LocalConsensusEngine;
import io.matrix.federation.consensus.LocalConsensusEngine.ConsensusResult;
import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.ConsensusProposal;
import io.matrix.federation.proto.ConsensusProposalOrBuilder;
import io.matrix.federation.proto.ConsensusStatus;
import io.matrix.federation.proto.ConsensusVote;
import io.matrix.federation.proto.ModulatorDefinition;
import io.matrix.federation.proto.ModulatorRegistry;
import io.matrix.federation.proto.ProposalType;
import io.matrix.federation.proto.VoteDecision;
import io.matrix.federation.registry.ModulatorRegistryStore;

import java.util.Optional;
import java.util.Random;

/**
 * W360 — End-to-end federation runtime.
 * 
 * Coordinates ModulatorRegistry + ConsensusEngine to provide a unified
 * interface for federation operations on a single node.
 */
public final class FederationRuntime {
    
    private final ModulatorRegistryStore registry;
    private final LocalConsensusEngine consensus;
    private final long nodeId;
    private final CapabilityLevel nodeCapability;
    private final Random rng;
    
    public FederationRuntime(long nodeId, CapabilityLevel nodeCapability, long seed) {
        this.nodeId = nodeId;
        this.nodeCapability = nodeCapability;
        this.registry = new ModulatorRegistryStore(seed);
        this.consensus = new LocalConsensusEngine(seed);
        this.rng = new Random(seed);
    }
    
    /**
     * Propose adding a modulator. Returns the proposal if accepted by consensus.
     */
    public Optional<ModulatorDefinition> proposeAdd(ModulatorDefinition def, 
                                                      long proposerCapabilityOrdinal) {
        // Check proposer can mutate (must be L2+)
        if (!registry.canMutate(CapabilityLevel.forNumber(Math.toIntExact(proposerCapabilityOrdinal)))) {
            return Optional.empty();
        }
        
        // Create proposal
        ConsensusProposal proposal = ConsensusProposal.newBuilder()
            .setProposalId("add-" + def.getId() + "-" + rng.nextLong())
            .setType(ProposalType.PROPOSAL_NEW_MODULATOR)
            .setProposerNodeId(String.valueOf(nodeId))
            .setProposedAtNs(System.nanoTime())
            .setDeadlineNs(System.nanoTime() + 5_000_000_000L)  // 5s deadline
            .setStatus(ConsensusStatus.CONSENSUS_PENDING)
            .build();
        
        // Auto-vote (single-node consensus: proposer votes YES)
        ConsensusVote selfVote = ConsensusVote.newBuilder()
            .setProposalId(proposal.getProposalId())
            .setDecision(VoteDecision.VOTE_YES)
            .setConfidence(1.0f)
            .setReputationWeight((float) LocalConsensusEngine.getVotingWeight(nodeCapability))
            .setVotedAtNs(System.nanoTime())
            .build();
        consensus.addVote(selfVote);
        
        ConsensusResult result = consensus.evaluate(proposal);
        if (result.approved()) {
            registry.add(def);
            return Optional.of(def);
        }
        return Optional.empty();
    }
    
    /**
     * Propose updating a modulator.
     */
    public Optional<ModulatorDefinition> proposeUpdate(ModulatorDefinition def,
                                                          long proposerCapabilityOrdinal) {
        if (!registry.canMutate(CapabilityLevel.forNumber(Math.toIntExact(proposerCapabilityOrdinal)))) {
            return Optional.empty();
        }
        
        Optional<ModulatorDefinition> existing = registry.get(def.getId());
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        if (existing.get().getSafety().getFrozen()) {
            return Optional.empty();  // FROZEN: cannot update
        }
        
        // Same consensus flow as add
        ConsensusProposal proposal = ConsensusProposal.newBuilder()
            .setProposalId("update-" + def.getId() + "-" + rng.nextLong())
            .setType(ProposalType.PROPOSAL_UPDATE_MODULATOR)
            .setProposerNodeId(String.valueOf(nodeId))
            .setProposedAtNs(System.nanoTime())
            .build();
        
        ConsensusVote selfVote = ConsensusVote.newBuilder()
            .setProposalId(proposal.getProposalId())
            .setDecision(VoteDecision.VOTE_YES)
            .setConfidence(1.0f)
            .setReputationWeight((float) LocalConsensusEngine.getVotingWeight(nodeCapability))
            .setVotedAtNs(System.nanoTime())
            .build();
        consensus.addVote(selfVote);
        
        ConsensusResult result = consensus.evaluate(proposal);
        if (result.approved()) {
            registry.update(def);
            return Optional.of(def);
        }
        return Optional.empty();
    }
    
    /**
     * Propose removing a modulator.
     */
    public boolean proposeRemove(String id, long proposerCapabilityOrdinal) {
        if (!registry.canMutate(CapabilityLevel.forNumber(Math.toIntExact(proposerCapabilityOrdinal)))) {
            return false;
        }
        
        Optional<ModulatorDefinition> existing = registry.get(id);
        if (existing.isEmpty() || existing.get().getSafety().getFrozen()) {
            return false;
        }
        
        ConsensusProposal proposal = ConsensusProposal.newBuilder()
            .setProposalId("remove-" + id + "-" + rng.nextLong())
            .setType(ProposalType.PROPOSAL_DELETE_MODULATOR)
            .setProposerNodeId(String.valueOf(nodeId))
            .setProposedAtNs(System.nanoTime())
            .build();
        
        ConsensusVote selfVote = ConsensusVote.newBuilder()
            .setProposalId(proposal.getProposalId())
            .setDecision(VoteDecision.VOTE_YES)
            .setConfidence(1.0f)
            .setReputationWeight((float) LocalConsensusEngine.getVotingWeight(nodeCapability))
            .setVotedAtNs(System.nanoTime())
            .build();
        consensus.addVote(selfVote);
        
        ConsensusResult result = consensus.evaluate(proposal);
        if (result.approved()) {
            registry.remove(id);
            return true;
        }
        return false;
    }
    
    /**
     * Get a modulator (no consensus needed for read).
     */
    public Optional<ModulatorDefinition> get(String id) {
        return registry.get(id);
    }
    
    /**
     * Get current registry state as ProtoBuf.
     */
    public ModulatorRegistry exportRegistry() {
        return registry.exportProto();
    }
    
    public ModulatorRegistryStore getRegistryStore() {
        return registry;
    }
    
    public LocalConsensusEngine getConsensusEngine() {
        return consensus;
    }
    
    public long getNodeId() {
        return nodeId;
    }
    
    public CapabilityLevel getNodeCapability() {
        return nodeCapability;
    }
}
