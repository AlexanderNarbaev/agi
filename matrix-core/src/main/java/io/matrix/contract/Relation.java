package io.matrix.contract;

import java.util.List;

/**
 * W51 — a stated connection between two entities.
 *
 * <p><b>The rule this record exists to enforce.</b> <i>A graph association is not proof of
 * truth.</i> The repository stores edges in several graphs — the workbench's
 * {@code KnowledgeGraphStore}, the connectome, the noosphere — and a reader of a graph sees a
 * fact where there is only a link. A relation therefore carries a {@link BeliefStatus} and a
 * {@link Confidence} of its own, exactly like a proposition.</p>
 *
 * <p><b>Association strength, frequency, causal relevance and evidential support are four
 * different quantities.</b> This record keeps them apart:</p>
 * <ul>
 *   <li>{@link #associationStrength()} — how strongly the two entities are associated in the
 *       system's data. It is a property of the data, not a property of the world;</li>
 *   <li>{@link #observationCount()} — how many times the connection was seen;</li>
 *   <li>{@link #causallyRelevant()} — whether the relation claims a causal role, which is a
 *       claim ABOUT the world and therefore carries its own status;</li>
 *   <li>{@link #confidence()} — the system's epistemic stance, with its basis and its
 *       supporting and contradicting counts.</li>
 * </ul>
 *
 * <p>Collapsing any two of those into one number is what lets a retrieval score masquerade as
 * truth. The W51 requirement — "association strength, frequency, causal relevance, confidence
 * and evidential support must not be silently conflated" — is this record's reason to
 * exist.</p>
 *
 * @param id                 the identity of the relation
 * @param type               the type of the connection
 * @param sourceEntityId     the entity the connection starts from
 * @param targetEntityId     the entity the connection points to
 * @param status             the epistemic state of the assertion
 * @param confidence         the system's stance on the assertion
 * @param associationStrength how strongly the two are associated in the system's data
 * @param observationCount   how many times the connection was seen
 * @param causallyRelevant   whether the relation asserts a causal role
 * @param evidenceIds        the identifiers of the evidence that bears on the relation
 * @param provenance         where the assertion came from
 */
public record Relation(
        String id,
        String type,
        String sourceEntityId,
        String targetEntityId,
        BeliefStatus status,
        Confidence confidence,
        double associationStrength,
        int observationCount,
        boolean causallyRelevant,
        List<String> evidenceIds,
        Provenance provenance) {

    public Relation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("relation id must not be blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("relation type must not be blank");
        }
        if (sourceEntityId == null || sourceEntityId.isBlank()) {
            throw new IllegalArgumentException("relation source entity id must not be blank");
        }
        if (targetEntityId == null || targetEntityId.isBlank()) {
            throw new IllegalArgumentException("relation target entity id must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("relation status must not be null");
        }
        if (confidence == null) {
            confidence = Confidence.UNESTABLISHED;
        }
        if (Double.isNaN(associationStrength) || associationStrength < 0.0) {
            throw new IllegalArgumentException("association strength must not be negative");
        }
        if (observationCount < 0) {
            throw new IllegalArgumentException("observation count must not be negative");
        }
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }

    /**
     * True when the relation may be used as the current account of the connection.
     *
     * <p>An association of any strength is not enough. Only a supported relation qualifies,
     * and a relation that asserts a causal role needs the same treatment as any other
     * proposition.</p>
     */
    public boolean established() {
        return status.established();
    }

    /** True when some evidence contradicts the relation. */
    public boolean contested() {
        return status == BeliefStatus.CONTESTED || confidence.contested();
    }

    @Override
    public String toString() {
        return "Relation[" + sourceEntityId + " -" + type + "-> " + targetEntityId
                + " " + status + " strength=" + associationStrength + "]";
    }
}
