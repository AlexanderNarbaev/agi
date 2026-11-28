package io.matrix.knowledge;

import java.util.List;

/**
 * CRJ-01 / EPISTEMIC-NECESSITY — a bounded, typed request for the evidence a task is missing.
 *
 * <p><b>Why a request and not a refusal.</b> A refusal that says "the evidence is missing" and
 * stops there leaves the caller with nothing to do: the workspace does not know what it does
 * not know, and only the caller can decide whether to go and get it. A request names
 * <b>what</b> is missing, <b>through which authorised channel</b> it may be obtained, and
 * <b>what the request authorises</b> — and it is bounded: it names a finite set of actions, it
 * carries no endpoint, it opens no permission and it makes no call to a model.</p>
 *
 * <p><b>What the two kinds mean.</b></p>
 * <ul>
 *   <li>{@link Kind#EVIDENCE} — the workspace <b>holds</b> the evidence and the competence's
 *       scope does not carry it. The channel is the <b>learning</b> one: a wider scope has to be
 *       learned and validated before it may be served, and a scope that was simply widened
 *       would be a competence nobody checked.</li>
 *   <li>{@link Kind#OBSERVATION} — the workspace holds <b>nothing</b> about part of the
 *       question: the terms the question asks about that no claim covers. The channel is the
 *       <b>receipt/import</b> one, and it is the only way in: a document arrives through the
 *       existing authorised import path and the workspace learns it.</li>
 * </ul>
 *
 * <p><b>What the request is NOT.</b> It is not an authority: it authorises nothing by itself —
 * it RECORDS what the caller is allowed to do, and a fulfilment through a channel the request
 * does not name is refused. It is not a plan for the whole workspace: it names one task and the
 * evidence that task is missing. And it is not a substitute for the adjudication of a
 * conflict: evidence that contradicts an established claim is refused until the conflict is
 * settled, because a request that quietly overwrote a settled fact would be a second rule of
 * evidence hidden inside a request for more of it.</p>
 *
 * @param requestId      the request's identity
 * @param workspaceId   the workspace the task belongs to
 * @param procedureId   the competence that could not serve the task, or null when the task was
 *                       not a competence's to serve
 * @param question       the task
 * @param level         the level the task is asked at
 * @param kind          what kind of gap this is
 * @param missing       the requirements the scope does not carry
 * @param uncoveredTerms the terms of the task that nothing in the workspace covers
 * @param channel       the authorised channel the evidence may arrive through
 * @param reason        why the evidence is needed, in words a reader can act on
 * @param allowedActions the channels this request authorises
 */
public record EvidenceAcquisitionRequest(
        String requestId,
        String workspaceId,
        String procedureId,
        String question,
        String level,
        Kind kind,
        List<EvidenceRequirement> missing,
        List<String> uncoveredTerms,
        String channel,
        String reason,
        List<String> allowedActions) {

    /** What kind of gap the request is for. */
    public enum Kind {
        /** The workspace holds the evidence and the scope does not carry it. */
        EVIDENCE,
        /** The workspace holds nothing about part of the task. */
        OBSERVATION
    }

    /** The channel a request for evidence the workspace does not hold is fulfilled through. */
    public static final String CHANNEL_RECEIPT_IMPORT = "receipt.import";

    /** The channel a request for evidence the scope does not carry is fulfilled through. */
    public static final String CHANNEL_LEARNING = "learning.scope_revision";

    public EvidenceAcquisitionRequest {
        missing = List.copyOf(missing == null ? List.of() : missing);
        uncoveredTerms = List.copyOf(uncoveredTerms == null ? List.of() : uncoveredTerms);
        allowedActions = List.copyOf(allowedActions == null ? List.of() : allowedActions);
        reason = reason == null ? "" : reason;
    }

    /**
     * The request's summary, in one sentence a reader can act on.
     *
     * @return the summary
     */
    public String summary() {
        if (kind == Kind.OBSERVATION) {
            return "the evidence this task needs is not in the workspace at all: the terms "
                    + String.join(", ", uncoveredTerms) + " are covered by no claim of it. The "
                    + "authorised channel is " + channel;
        }
        List<String> propositions = new java.util.ArrayList<>();
        for (EvidenceRequirement requirement : missing) {
            propositions.add(requirement.proposition());
        }
        return "the scope of " + (procedureId == null ? "this competence" : procedureId)
                + " does not carry the evidence this task needs: " + String.join("; ", propositions)
                + ". The authorised channel is " + channel;
    }
}
