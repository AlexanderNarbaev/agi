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
     * Causal merge: merges while honouring per-key vector clocks and
     * tombstone history. The base {@link #merge(Object)} is the lossy
     * last-resort fallback; {@code mergeCausal} is the safe path used by
     * the M4 Noosphere mesh.
     *
     * <p>Default delegates to {@link #merge(Object)} for CRDTs that don't
     * track per-key causality (e.g. pure G-Set). CRDTs that do (e.g. M4
     * causal variants) MUST override.
     *
     * @param other the other replica to merge with
     * @return a new CRDT with causally-consistent merged state
     */
    default T mergeCausal(T other) {
        return merge(other);
    }

    /**
     * Mark a key as tombstoned at the given epoch. The tombstone is
     * irreversible: any subsequent {@link #merge(Object)} or
     * {@link #mergeCausal(Object)} that would re-introduce the key with
     * epoch ≤ {@code epoch} is rejected.
     *
     * <p>Default throws {@link UnsupportedOperationException} for CRDTs
     * that do not support deletion (e.g. G-Set). Concrete deletion-aware
     * CRDTs MUST override.
     *
     * @param key   the key to tombstone
     * @param epoch the logical clock value at which the tombstone is created
     * @return a new CRDT with the tombstone applied
     */
    default T tombstoneAt(String key, long epoch) {
        throw new UnsupportedOperationException(
                "tombstoneAt not supported by " + getClass().getSimpleName());
    }

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
