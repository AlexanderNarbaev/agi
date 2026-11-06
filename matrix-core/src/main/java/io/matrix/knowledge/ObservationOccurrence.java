package io.matrix.knowledge;

import io.matrix.cognitive.microworld.ObservationDisposition;

/**
 * W45.1 — ONE act of observing a canonical case.
 *
 * <p><b>Why a canonical case is not an observation.</b> The micro-world derives a case id from
 * the conditions AND the observed outcome, so the same fact — "the lamp was on under
 * power=1,switch=1" — comes back as the SAME case in every later episode. That is what makes
 * a case a canonical identity: the fact, not the act. The act is something else, and it has
 * its own record here: an occurrence says that at this instant, in this episode, under this
 * signal, this source and this evidence, the fact was observed again.</p>
 *
 * <p><b>Why the distinction has to be durable.</b> Before this record existed, the only place
 * an observation's disposition lived was the case's own row, and a re-observation overwrote
 * it. A review that accepted a contradiction was undone by the next cycle that saw the same
 * lamp in the same state. With the occurrence written separately, the case's row can hold the
 * DECISION and the occurrence can hold the ACT, and the two stop fighting over one row.</p>
 *
 * <p><b>What the disposition of an occurrence means.</b> It is the disposition the SESSION
 * decided at that moment — the decision the session made when it saw the fact, which may be
 * based on a model that knows nothing about a later adjudication. It is NOT the case's current
 * disposition, which is the projection of the adjudication history. The two can differ, and
 * when they do the difference is exactly what the two identities are for: the session decided
 * ELIGIBLE because it had never heard of the review, and the review's decision still stands.
 * The reader who wants to know what may be believed NOW asks the case's disposition; the
 * reader who wants to know what the session thought THEN asks the occurrence.</p>
 *
 * <p><b>Why the identifiers are the caller's.</b> The occurrence id is derived from the
 * evidence id, because the evidence link is the one thing that is minted per act of
 * observing. Nothing here is read from a clock and nothing is random: the store never
 * invents an identity for a fact it was given (CONSTITUTION Article I).</p>
 *
 * @param id           the identifier of this occurrence; unique
 * @param workspaceId  the workspace
 * @param caseId       the canonical case that was observed
 * @param episodeId    the episode the observation belongs to, when it is known
 * @param signalId     the signal that produced the observation, when it is known
 * @param sourceId     the source of the observation
 * @param evidenceId   the evidence link the observation produced
 * @param observedAt   when the observation was made
 * @param disposition  the disposition the session decided at that moment
 * @param reason       why the session decided that
 */
public record ObservationOccurrence(
        String id,
        String workspaceId,
        String caseId,
        String episodeId,
        String signalId,
        String sourceId,
        String evidenceId,
        String observedAt,
        ObservationDisposition disposition,
        String reason) {

    public ObservationOccurrence {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("an occurrence id must not be blank");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("an occurrence workspace id must not be blank");
        }
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("an occurrence must name the case it observed");
        }
        if (observedAt == null || observedAt.isBlank()) {
            throw new IllegalArgumentException("an occurrence must say when it was made");
        }
        if (disposition == null) {
            throw new IllegalArgumentException("an occurrence must carry a disposition");
        }
    }

    /** The prefix of the identifiers this store derives for occurrences. */
    public static final String ID_PREFIX = "occ-";

    /**
     * The identifier of the occurrence that an evidence link records.
     *
     * <p>Derived, not invented: the evidence link is minted once per act of observing, so its
     * id already says which act this is. Replaying the same request with the same evidence
     * therefore produces the same occurrence id, and the occurrence is written once.</p>
     *
     * @param evidenceId the evidence link of the observation
     * @return the identifier of the occurrence
     */
    public static String idForEvidence(String evidenceId) {
        if (evidenceId == null || evidenceId.isBlank()) {
            throw new IllegalArgumentException("an occurrence needs an evidence link");
        }
        return ID_PREFIX + evidenceId;
    }
}
