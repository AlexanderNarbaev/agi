package io.matrix.contract;

/**
 * W51 — a single named property of an entity, with its own epistemic state.
 *
 * <p><b>Why this is separate from an entity's attribute map.</b> An attribute says what the
 * system believes about an entity. A property is a claim ABOUT one attribute, and it can be
 * wrong, disputed, revised or refuted. The moment a fact enters the attribute map directly, the
 * system loses the ability to say "that was believed, it was wrong, and here is the evidence
 * that showed it" — which is precisely the failure the W50 audit recorded when a candidate claim
 * entered an answer without status filtering.</p>
 *
 * <p><b>A property is a belief about one attribute of one entity.</b> It therefore carries a
 * {@link BeliefStatus}, a {@link Confidence} and a {@link Provenance}. When the property is
 * established, the entity's attribute map MAY be updated to reflect it; when it is not, the
 * attribute map keeps whatever it had and the disagreement stays inspectable.</p>
 *
 * @param id         the identity of the property record
 * @param entityId   the entity the property is about
 * @param name       the name of the property
 * @param value      the value the property asserts
 * @param status     the epistemic state of the assertion
 * @param confidence how much the system believes it
 * @param provenance where the assertion came from
 * @param validity   the interval during which the assertion is the current account
 */
public record Property(
        String id,
        String entityId,
        String name,
        Object value,
        BeliefStatus status,
        Confidence confidence,
        Provenance provenance,
        ValidityInterval validity) {

    public Property {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("property id must not be blank");
        }
        if (entityId == null || entityId.isBlank()) {
            throw new IllegalArgumentException("property entity id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("property name must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("property status must not be null");
        }
        if (confidence == null) {
            confidence = Confidence.UNESTABLISHED;
        }
        if (validity == null) {
            validity = ValidityInterval.UNBOUNDED;
        }
    }

    /** True when the assertion may be used as the current account of the attribute. */
    public boolean established() {
        return status.established();
    }

    @Override
    public String toString() {
        return "Property[" + entityId + "." + name + "=" + value + " " + status + "]";
    }
}
