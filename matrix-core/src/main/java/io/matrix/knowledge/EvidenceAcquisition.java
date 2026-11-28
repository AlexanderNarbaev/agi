package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRJ-01 / EPISTEMIC-NECESSITY — the fulfilment of an acquisition request, and its refusals.
 *
 * <p><b>What this decides.</b> Whether evidence that arrived may be counted against a request.
 * Three refusals exist, and each of them is a different failure with a different repair:</p>
 *
 * <ul>
 *   <li><b>UNAUTHORIZED</b> — the evidence arrived through a channel the request does not
 *       authorise. The repair is a decision, not a mechanism: the caller asked for something
 *       the request did not allow.</li>
 *   <li><b>STALE</b> — the evidence is a revision the workspace's current view no longer
 *       holds. The repair is to obtain the current revision; counting a withdrawn revision
 *       would put a fact back into a workspace that took it back.</li>
 *   <li><b>CONFLICTING</b> — the evidence contradicts an established claim of the workspace.
 *       The repair is the adjudication of the conflict, which is a separate door: a request
 *       for more evidence must not silently overwrite what the workspace has settled.</li>
 * </ul>
 *
 * <p><b>Why the conflict check uses the production detector.</b> The check is
 * {@link ClaimConflictDetector} — the same detector the workspace uses — and not a comparison
 * of words written here, so that the refusal and the workspace's own notion of a conflict
 * cannot drift apart.</p>
 *
 * <p><b>What this does not do.</b> It does not import anything, it does not adjudicate a
 * conflict, it does not widen a scope and it does not decide that the evidence is <i>true</i>.
 * It decides one thing: whether the offered evidence may be counted against this request.</p>
 */
public final class EvidenceAcquisition {

    /** The evidence satisfied the request. */
    public static final String FULFILLED = "FULFILLED";

    /** The evidence arrived through a channel the request does not authorise. */
    public static final String REFUSED_UNAUTHORIZED = "REFUSED_UNAUTHORIZED";

    /** The evidence is a revision the workspace's current view no longer holds. */
    public static final String REFUSED_STALE = "REFUSED_STALE";

    /** The evidence contradicts an established claim of the workspace. */
    public static final String REFUSED_CONFLICTING = "REFUSED_CONFLICTING";

    /** The evidence is offered against a request for evidence the workspace HOLDS, and the
     * evidence offered is not the workspace's own. */
    public static final String REFUSED_UNVERIFIED = "REFUSED_UNVERIFIED";

    /**
     * The outcome of an offer of evidence against a request.
     *
     * @param disposition       what the offer was decided to be
     * @param reason            why, in words a reader can act on
     * @param carriedClaimIds   the claims that satisfy the request
     * @param conflictingClaimIds the claims the evidence conflicts with
     */
    public record Fulfilment(String disposition, String reason, List<String> carriedClaimIds,
            List<String> conflictingClaimIds) {

        public Fulfilment {
            carriedClaimIds = List.copyOf(carriedClaimIds == null ? List.of() : carriedClaimIds);
            conflictingClaimIds =
                    List.copyOf(conflictingClaimIds == null ? List.of() : conflictingClaimIds);
            reason = reason == null ? "" : reason;
        }

        /** Whether the evidence satisfied the request. */
        public boolean fulfilled() {
            return FULFILLED.equals(disposition);
        }
    }

    private EvidenceAcquisition() {
    }

    /**
     * Offers evidence against a request.
     *
     * @param request     the request the evidence is offered against
     * @param channel     the channel the evidence arrived through
     * @param offered     the claims the evidence carries
     * @param view        the workspace as it is now — its current sources and its established
     *                    claims
     * @return the outcome
     */
    public static Fulfilment offer(EvidenceAcquisitionRequest request, String channel,
            List<Claim> offered, KnowledgeSnapshot view) {
        if (request == null) {
            return new Fulfilment(REFUSED_UNAUTHORIZED,
                    "there is no request to offer the evidence against", List.of(), List.of());
        }
        // ── THE AUTHORISATION ──────────────────────────────────────────────────────────
        if (channel == null || !request.allowedActions().contains(channel)) {
            return new Fulfilment(REFUSED_UNAUTHORIZED,
                    "the request authorises " + String.join(", ", request.allowedActions())
                            + " and the evidence arrived through " + channel
                            + "; an offer outside the request's allow-list is refused", List.of(),
                    List.of());
        }
        List<Claim> evidence = offered == null ? List.of() : offered;
        if (evidence.isEmpty()) {
            return new Fulfilment(REFUSED_UNAUTHORIZED,
                    "the offer carries no claim, so there is nothing to count against the request",
                    List.of(), List.of());
        }
        Set<String> currentSources = new LinkedHashSet<>();
        Map<String, Source> sourcesById = new LinkedHashMap<>();
        Set<String> heldClaimIds = new LinkedHashSet<>();
        if (view != null) {
            for (Source source : view.sources()) {
                currentSources.add(source.id());
                sourcesById.put(source.id(), source);
            }
            for (Claim claim : view.claims()) {
                heldClaimIds.add(claim.id());
            }
        }
        // ── THE PROVENANCE ─────────────────────────────────────────────────────────────
        // A request for evidence the workspace HOLDS is answered by the workspace's own
        // claims, and a claim the workspace does not hold cannot answer it: it is not
        // evidence of what this workspace knows, whatever source it names. A request for
        // evidence the workspace does NOT hold (an observation) is the opposite case, and
        // new evidence is exactly what it is for.
        //
        // The first version of this check nothing of the kind, and a fabricated claim with
        // an existing source's id was counted as a fulfilment. The independent reviewer
        // found it.
        if (request.kind() == EvidenceAcquisitionRequest.Kind.EVIDENCE) {
            List<String> unverified = new ArrayList<>();
            for (Claim claim : evidence) {
                if (!heldClaimIds.contains(claim.id())) {
                    unverified.add(claim.id());
                }
            }
            if (!unverified.isEmpty()) {
                return new Fulfilment(REFUSED_UNVERIFIED,
                        "the request is for evidence the workspace HOLDS, and "
                                + unverified.size() + " of the claim(s) offered are not claims of "
                                + "this workspace; they cannot stand as evidence of what the "
                                + "workspace knows", List.of(), unverified);
            }
        }
        // ── THE STALENESS ──────────────────────────────────────────────────────────────
        // A claim the workspace has TAKEN BACK is not evidence: the production policy says
        // so, and this refusal uses the production policy's own set rather than a rule
        // written here. A claim whose source is not in the workspace's current view is stale
        // for the same reason — the revision it came from is no longer the one in force.
        List<String> stale = new ArrayList<>();
        for (Claim claim : evidence) {
            String sourceId = claim.introducedBySourceId();
            if (sourceId != null && !currentSources.contains(sourceId)) {
                stale.add(claim.id());
                continue;
            }
            if (EpistemicPolicy.WITHDRAWN_STATUSES.contains(claim.status())) {
                stale.add(claim.id());
            }
        }
        if (!stale.isEmpty()) {
            return new Fulfilment(REFUSED_STALE,
                    "the evidence names " + stale.size() + " claim(s) the workspace has taken "
                            + "back — a withdrawn claim is never evidence, and a revision that "
                            + "is no longer in force is not the one the workspace holds",
                    List.of(), stale);
        }
        // ── THE CONFLICT ───────────────────────────────────────────────────────────────
        // TWO production detectors are asked, because the workspace has TWO notions of a
        // conflict and the refusal must not be narrower than either: the corpus-level
        // detector, which compares two accounts of the same subject, and the
        // contradiction detector, which compares two claims of the same concept with
        // opposite polarity.
        List<Claim> together = new ArrayList<>();
        if (view != null) {
            together.addAll(view.claims());
        }
        together.addAll(evidence);
        Set<String> offeredIds = new LinkedHashSet<>();
        for (Claim claim : evidence) {
            offeredIds.add(claim.id());
        }
        List<String> conflicting = new ArrayList<>();
        for (ClaimConflictDetector.Conflict conflict : ClaimConflictDetector.detect(together,
                sourcesById, view)) {
            for (ClaimConflictDetector.Side side : conflict.sides()) {
                if (offeredIds.contains(side.claimId()) && !conflicting.contains(side.claimId())) {
                    conflicting.add(side.claimId());
                }
            }
        }
        if (view != null) {
            for (Contradiction contradiction : new ContradictionDetector().detect(view)) {
                if (offeredIds.contains(contradiction.claimAId())
                        && !conflicting.contains(contradiction.claimAId())) {
                    conflicting.add(contradiction.claimAId());
                }
                if (offeredIds.contains(contradiction.claimBId())
                        && !conflicting.contains(contradiction.claimBId())) {
                    conflicting.add(contradiction.claimBId());
                }
            }
            // AND THE PAIRS THE OFFER ITSELF MAKES — an offer that contradicts a claim of
            // the workspace must be refused even when the pair is not inside the view.
            ContradictionDetector detector = new ContradictionDetector();
            for (Claim claim : evidence) {
                for (Claim established : view.claims()) {
                    if (established.id().equals(claim.id())) {
                        continue;
                    }
                    if (detector.compare(claim, established) != null
                            && !conflicting.contains(claim.id())) {
                        conflicting.add(claim.id());
                    }
                }
            }
        }
        if (!conflicting.isEmpty()) {
            return new Fulfilment(REFUSED_CONFLICTING,
                    "the evidence conflicts with an established claim of the workspace ("
                            + conflicting.size() + " claim(s)); a request for more evidence must "
                            + "not overwrite what the workspace has settled", List.of(),
                    conflicting);
        }
        // ── THE FULFILMENT — the evidence must actually answer the request ──────────────
        List<String> carried = new ArrayList<>();
        Set<String> propositions = new LinkedHashSet<>();
        for (Claim claim : evidence) {
            carried.add(claim.id());
            propositions.add(claim.proposition());
        }
        List<String> unanswered = new ArrayList<>();
        for (EvidenceRequirement requirement : request.missing()) {
            if (!carried.contains(requirement.claimId())
                    && !propositions.contains(requirement.proposition())) {
                unanswered.add(requirement.claimId());
            }
        }
        if (!unanswered.isEmpty()) {
            return new Fulfilment(REFUSED_UNAUTHORIZED,
                    "the evidence carries none of the requirement(s) the request asked for ("
                            + unanswered.size() + " unanswered); an offer that does not answer "
                            + "the request is not a fulfilment of it", carried, unanswered);
        }
        return new Fulfilment(FULFILLED,
                "the evidence arrived through the authorised channel " + channel + " and carries "
                        + carried.size() + " claim(s) that the request asked for", carried,
                List.of());
    }

    /**
     * A request for the evidence a scope does not carry.
     *
     * @param workspaceId the workspace the task belongs to
     * @param procedureId the competence that could not serve the task
     * @param question    the task
     * @param level       the level the task is asked at
     * @param missing     the requirements the scope does not carry
     * @return the request
     */
    public static EvidenceAcquisitionRequest forMissingEvidence(String workspaceId,
            String procedureId, String question, String level,
            List<EvidenceRequirement> missing) {
        List<EvidenceRequirement> requirements = List.copyOf(
                missing == null ? List.of() : missing);
        String reason = "the scope of " + procedureId + " does not carry "
                + requirements.size() + " requirement(s) the task cannot be done without; the "
                + "evidence is in the workspace, and a wider scope has to be LEARNED and "
                + "VALIDATED before it may be served";
        return new EvidenceAcquisitionRequest(
                "acquisition." + Integer.toHexString((workspaceId + "|" + procedureId + "|"
                        + question).hashCode()),
                workspaceId, procedureId, question, level,
                EvidenceAcquisitionRequest.Kind.EVIDENCE, requirements, List.of(),
                EvidenceAcquisitionRequest.CHANNEL_LEARNING, reason,
                List.of(EvidenceAcquisitionRequest.CHANNEL_LEARNING));
    }

    /**
     * A request for the evidence the workspace does not hold at all.
     *
     * @param workspaceId    the workspace the task belongs to
     * @param procedureId    the competence that could not serve the task, or null
     * @param question       the task
     * @param level          the level the task is asked at
     * @param uncoveredTerms the terms of the task that nothing covers
     * @return the request
     */
    public static EvidenceAcquisitionRequest forUncoveredTerms(String workspaceId,
            String procedureId, String question, String level, List<String> uncoveredTerms) {
        List<String> terms = List.copyOf(uncoveredTerms == null ? List.of() : uncoveredTerms);
        String reason = "the workspace covers no claim of " + terms.size() + " term(s) of this "
                + "task (" + String.join(", ", terms) + "); the evidence has to ARRIVE through "
                + "the authorised receipt/import path, and the task cannot be answered before "
                + "it does";
        return new EvidenceAcquisitionRequest(
                "acquisition." + Integer.toHexString((workspaceId + "|" + question).hashCode()),
                workspaceId, procedureId, question, level,
                EvidenceAcquisitionRequest.Kind.OBSERVATION, List.of(), terms,
                EvidenceAcquisitionRequest.CHANNEL_RECEIPT_IMPORT, reason,
                List.of(EvidenceAcquisitionRequest.CHANNEL_RECEIPT_IMPORT));
    }
}
