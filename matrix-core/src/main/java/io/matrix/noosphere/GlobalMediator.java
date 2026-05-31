package io.matrix.noosphere;

import io.matrix.consensus.ConsensusEngine;
import io.matrix.consensus.ConsensusLevel;
import io.matrix.consensus.Proposal;
import io.matrix.consensus.Vote;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Global Mediator — distributed council for Noosphere governance.
 *
 * <p>Composed of top-reputation instances. Governs:
 * <ul>
 * <li>FNL publication approval</li>
 * <li>Global axiom updates (FROZEN rules)</li>
 * <li>Credit model parameters</li>
 * <li>Deprecation and revocation decisions</li>
 * </ul>
 *
 * <p>Ref: L6_Memory.md §6.1, L4_Mediator.md §2.1
 */
public class GlobalMediator {

    private final NoosphereRegistry registry;
    private final CreditModel creditModel;
    private final ConsensusEngine consensus;
    private final List<String> councilMembers;
    private final List<String> actionLog = new ArrayList<>();

    public GlobalMediator(NoosphereRegistry registry, CreditModel creditModel) {
        this.registry = registry;
        this.creditModel = creditModel;
        this.consensus = new ConsensusEngine();
        this.councilMembers = new ArrayList<>();
    }

    /**
     * Elects council members based on reputation.
     */
    public void electCouncil(int size) {
        var top = creditModel.topReputation(size);
        councilMembers.clear();
        for (var member : top) {
            councilMembers.add(member.instanceId());
        }
        actionLog.add("COUNCIL_ELECTED:" + councilMembers.size() + " members");
    }

    /**
     * Proposes FNL publication to the council.
     */
    public UUID proposePublication(FnlPackage fnl, String proposerId) {
        Proposal proposal = Proposal.create(ConsensusLevel.LEVEL_3,
                proposerId, "PUBLISH_FNL",
                fnl.name() + ":" + fnl.type() + ":" + fnl.accuracy());
        UUID propId = consensus.propose(proposal);
        actionLog.add("PROPOSE_PUBLISH:" + fnl.name() + " by " + proposerId);
        return propId;
    }

    /**
     * Council member votes on a proposal.
     */
    public void councilVote(UUID proposalId, String voterId, boolean approve) {
        double weight = creditModel.getCredits(voterId).reputation();
        Vote vote = approve
                ? Vote.approve(proposalId, voterId, Math.max(weight, 0.1))
                : Vote.reject(proposalId, voterId, Math.max(weight, 0.1));
        consensus.castVote(vote);
    }

    /**
     * Evaluates a proposal and executes the decision.
     */
    public String decide(UUID proposalId) {
        var decision = consensus.evaluate(proposalId);
        var prop = consensus.getProposal(proposalId);

        if (decision == ConsensusEngine.Decision.APPROVED && prop != null
                && prop.action().equals("PUBLISH_FNL")) {

            String[] parts = prop.payload().split(":");
            if (parts.length >= 3) {
                actionLog.add("DECIDE:APPROVED " + prop.payload());
                return "APPROVED";
            }
        }

        actionLog.add("DECIDE:" + decision + " " + (prop != null ? prop.payload() : ""));
        return decision.name();
    }

    public List<String> councilMembers() { return List.copyOf(councilMembers); }

    public List<String> actionLog() { return List.copyOf(actionLog); }

    public ConsensusEngine consensus() { return consensus; }

    public NoosphereRegistry registry() { return registry; }

    public CreditModel creditModel() { return creditModel; }
}
