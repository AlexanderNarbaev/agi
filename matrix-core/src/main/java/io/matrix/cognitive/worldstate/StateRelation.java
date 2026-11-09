package io.matrix.cognitive.worldstate;

/**
 * W52 §3 — the connection a fact asserts, besides the properties it asserts.
 *
 * <p><b>What this is.</b> The world-state memory holds entities and their properties, and a
 * fact that says "the lamp was on under this assignment" says nothing about how the
 * assignment and the lamp are connected. The domain knows that connection — in the
 * micro-world, the assignment <em>determines</em> the lamp — and this record is how the
 * domain tells the memory, without the memory having to know anything about lamps.</p>
 *
 * <p><b>What the record does NOT carry.</b> It does not carry an identity, a status, a
 * confidence, an observation count or an association strength: those are the MEMORY's
 * bookkeeping, and the memory fills them as the facts arrive. A template that carried them
 * would let a domain assert its own certainty, and a domain's certainty about a rule is not
 * the system's evidence for it.</p>
 *
 * <p><b>The causal flag is a claim, and it is explicit.</b> {@link #causallyRelevant()} says
 * that the connection asserts a causal role. The memory records the claim and, separately,
 * the evidence for it — which is exactly what the canonical
 * {@link io.matrix.contract.Relation} requires: <i>association strength, frequency, causal
 * relevance, confidence and evidential support are five different quantities</i>.</p>
 *
 * @param type               the kind of the connection
 * @param targetEntityId     the entity the connection points at
 * @param targetEntityType   the type of that entity
 * @param causallyRelevant   whether the connection asserts a causal role
 */
public record StateRelation(String type, String targetEntityId, String targetEntityType,
                            boolean causallyRelevant) {

    public StateRelation {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("a relation must name its type");
        }
        if (targetEntityId == null || targetEntityId.isBlank()) {
            throw new IllegalArgumentException("a relation must name the entity it points at");
        }
        if (targetEntityType == null || targetEntityType.isBlank()) {
            throw new IllegalArgumentException("a relation must name the type of that entity");
        }
    }
}
