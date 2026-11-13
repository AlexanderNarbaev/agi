package io.matrix.cognitive;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.EpistemicPolicy;
import io.matrix.knowledge.Evidence;
import io.matrix.knowledge.EvidenceRelation;
import io.matrix.knowledge.KnowledgeStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * W53 — chain A's own evidence, read from chain A's own knowledge store.
 *
 * <p><b>Why this class exists.</b> The adjudicator asks two questions about the peer's
 * report: does chain A's own knowledge <i>support</i> the proposition, and does it
 * <i>contradict</i> it? Until now the only implementation of that question was
 * {@code LocalEvidence.NONE} — the answers were always zero, so the adjudicator could
 * never support a proposition and never reject one. A remote result that is not checked
 * against what this process already knows is not evaluated, it is merely received.</p>
 *
 * <p><b>What counts as support.</b> A claim of the same proposition whose status may be
 * cited ({@link EpistemicPolicy#CITABLE_STATUSES}) and which carries at least one
 * supporting evidence record. A claim whose evidence contradicts the proposition counts
 * as a contradiction. A candidate — an unvalidated claim, which is what a remote report
 * itself becomes — is not citable and therefore supports nothing: the rule that keeps a
 * candidate from answering as an established fact applies to chain A's own records too.</p>
 *
 * <p><b>What it does not do.</b> It does not decide whether the report is true. It counts
 * what chain A holds; the adjudicator weighs that count, and the report's own confidence
 * never enters the count.</p>
 */
public final class KnowledgeStoreEvidence implements RemoteReportAssessor.LocalEvidence {

    private final KnowledgeStore store;
    private final String workspaceId;

    /**
     * The evidence of a workspace.
     *
     * @param store       chain A's knowledge store
     * @param workspaceId the workspace the claims live in
     */
    public KnowledgeStoreEvidence(KnowledgeStore store, String workspaceId) {
        if (store == null) {
            throw new IllegalArgumentException("the evidence needs a knowledge store");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("the evidence needs a workspace");
        }
        this.store = store;
        this.workspaceId = workspaceId;
    }

    @Override
    public int supporting(String proposition) {
        int count = 0;
        for (Claim claim : claimsOf(proposition)) {
            if (EpistemicPolicy.isCitable(claim.status())
                    && relationsOf(claim.id()).contains(EvidenceRelation.SUPPORTS)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int contradicting(String proposition) {
        int count = 0;
        for (Claim claim : claimsOf(proposition)) {
            if (relationsOf(claim.id()).contains(EvidenceRelation.CONTRADICTS)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public List<String> propositionsOf(List<String> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return List.of();
        }
        Map<String, String> byId = new LinkedHashMap<>();
        for (Claim claim : store.claims(workspaceId)) {
            byId.put(claim.id(), claim.proposition());
        }
        List<String> propositions = new ArrayList<>();
        for (String id : recordIds) {
            String proposition = byId.get(id);
            if (proposition != null && !proposition.isBlank()) {
                propositions.add(proposition);
            }
        }
        return List.copyOf(propositions);
    }

    /**
     * The claims of this workspace that state this proposition.
     *
     * @param proposition the proposition
     * @return the claims, in the store's own order
     */
    private List<Claim> claimsOf(String proposition) {
        if (proposition == null || proposition.isBlank()) {
            return List.of();
        }
        String wanted = normalize(proposition);
        List<Claim> found = new ArrayList<>();
        for (Claim claim : store.claims(workspaceId)) {
            if (wanted.equals(normalize(claim.proposition()))) {
                found.add(claim);
            }
        }
        return found;
    }

    /**
     * The relations the evidence of a claim carries.
     *
     * @param claimId the claim
     * @return the relations
     */
    private List<EvidenceRelation> relationsOf(String claimId) {
        List<EvidenceRelation> relations = new ArrayList<>();
        for (Evidence evidence : store.evidence(workspaceId)) {
            if (evidence.claimId().equals(claimId)) {
                relations.add(evidence.relation());
            }
        }
        return relations;
    }

    /**
     * Two propositions that differ only in case, spacing and a trailing full stop are
     * the same proposition for this purpose.
     *
     * @param proposition the proposition
     * @return its comparable form
     */
    private static String normalize(String proposition) {
        String normalized = proposition == null ? "" : proposition.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("\\s+", " ");
        while (normalized.endsWith(".") || normalized.endsWith("!")
                || normalized.endsWith("?")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }
}
