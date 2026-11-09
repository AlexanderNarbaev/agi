package io.matrix.contract;

import java.util.List;

/**
 * W51 — a proposition the system holds, with its epistemic state.
 *
 * <p><b>What this replaces.</b> The workbench's {@code io.matrix.knowledge.Claim} already
 * carries a status, a confidence and a version, and it is the workbench's own record — it is
 * scoped to a concept, it lives in the workbench's store, and its fields are the workbench's.
 * This record is the <i>canonical</i> form of the same idea, and it is what crosses a process
 * boundary, what a plugin declares, and what the cognitive runtime reasons over. The
 * workbench's claim is adapted into this form; it is not replaced by it.</p>
 *
 * <p><b>The rule this record exists to enforce.</b> A candidate belief does not become an
 * established answer because a retrieval score was high. The status is not derived from the
 * confidence, and the confidence is not derived from the status. A proposition with a
 * confidence of {@code 0.9} and no supporting evidence is still a {@link BeliefStatus#CANDIDATE},
 * and a proposition with a confidence of {@code 0.1} and three supporting pieces of evidence is
 * still {@link BeliefStatus#SUPPORTED}. The two are independent, and the W50 defect
 * "candidate claims could enter answers without adequate status filtering" is what happens when
 * they are not.</p>
 *
 * <p><b>Revision history is part of the record.</b> {@link #version()} and
 * {@link #supersedes()} say what this belief replaced and what replaced it. The earlier belief
 * is not edited and not deleted — it is superseded, and it stays readable with its own
 * evidence.</p>
 *
 * @param id                    the identity of the belief
 * @param proposition            the proposition the system holds
 * @param status                the epistemic state of the holding
 * @param confidence            how much the system believes it, and on what basis
 * @param evidenceIds           the evidence that bears on it
 * @param contradictingEvidenceIds the evidence that contradicts it
 * @param provenance            where the belief came from
 * @param validity              the interval during which it is the current account
 * @param version               the revision number of this belief
 * @param supersedes            the belief this one replaced, or {@code null}
 * @param supersededBy          the belief that replaced this one, or {@code null}
 * @param rationale             why the system's stance changed, when it changed
 */
public record Belief(
        String id,
        String proposition,
        BeliefStatus status,
        Confidence confidence,
        List<String> evidenceIds,
        List<String> contradictingEvidenceIds,
        Provenance provenance,
        ValidityInterval validity,
        int version,
        String supersedes,
        String supersededBy,
        String rationale) {

    public Belief {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("belief id must not be blank");
        }
        if (proposition == null || proposition.isBlank()) {
            throw new IllegalArgumentException("belief proposition must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("belief status must not be null");
        }
        if (confidence == null) {
            confidence = Confidence.UNESTABLISHED;
        }
        if (validity == null) {
            validity = ValidityInterval.UNBOUNDED;
        }
        if (version < 1) {
            throw new IllegalArgumentException("belief version must be at least one");
        }
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        contradictingEvidenceIds =
                contradictingEvidenceIds == null ? List.of() : List.copyOf(contradictingEvidenceIds);
    }

    /**
     * True when the belief may be presented as an established fact.
     *
     * <p>This is the single predicate the answering path must consult. It is deliberately not
     * "confidence is high": it is the status.</p>
     */
    public boolean established() {
        return status.established();
    }

    /** True when the belief may be presented as an answer at all. */
    public boolean answerable() {
        return status.answerable();
    }

    /** True when some known evidence contradicts the belief. */
    public boolean contested() {
        return status == BeliefStatus.CONTESTED || confidence.contested()
                || !contradictingEvidenceIds.isEmpty();
    }

    /** True when the belief has been replaced by a later one. */
    public boolean superseded() {
        return status == BeliefStatus.SUPERSEDED || supersededBy != null;
    }

    /**
     * The belief that replaced this one, with the rationale.
     *
     * @param newId        the identifier of the belief that replaces this one
     * @param reason       why the replacement happened
     * @param newVersion   the version of the replacing belief
     * @return the superseded form of this belief
     */
    public Belief supersededBy(String newId, String reason, int newVersion) {
        return new Belief(id, proposition, BeliefStatus.SUPERSEDED, confidence, evidenceIds,
                contradictingEvidenceIds, provenance, validity, version, supersedes, newId, reason);
    }

    @Override
    public String toString() {
        return "Belief[" + id + " v" + version + " " + status + " " + proposition + "]";
    }
}
