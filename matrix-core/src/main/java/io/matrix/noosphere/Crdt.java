package io.matrix.noosphere;

/**
 * Conflict-free Replicated Data Type (CRDT) — a distributed data structure
 * that guarantees strong eventual consistency without coordination.
 *
 * <p>A CRDT must satisfy three algebraic properties:
 * <ol>
 *   <li><b>Commutativity:</b> {@code a.merge(b) ≡ b.merge(a)}</li>
 *   <li><b>Associativity:</b> {@code (a.merge(b)).merge(c) ≡ a.merge(b.merge(c))}</li>
 *   <li><b>Idempotence:</b> {@code a.merge(a) ≡ a}</li>
 * </ol>
 *
 * <p>These properties ensure that replicas converge to the same state
 * regardless of message delivery order in the Noosphere mesh.
 *
 * <p>Ref: DESIGN-08 §Federation, Phase 5 Noosphere ROADMAP
 *
 * @param <T> the state type carried by this CRDT
 */
public interface Crdt<T> {

    /**
     * Merges this CRDT with another replica, returning a new instance
     * that reflects the combined state.
     *
     * <p>Must be commutative, associative, and idempotent.
     *
     * @param other the other replica to merge with
     * @return a new CRDT containing the merged state
     */
    T merge(T other);

    /**
     * Serializes this CRDT to a JSON string for wire transfer.
     *
     * @return JSON representation of the current state
     */
    String toJson();

    /**
     * Deserializes a CRDT from a JSON string.
     *
     * @param json the JSON representation
     * @return a new CRDT with the deserialized state
     */
    static <T extends Crdt<T>> T fromJson(String json) {
        throw new UnsupportedOperationException("fromJson must be implemented by concrete types");
    }
}
