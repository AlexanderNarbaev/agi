package io.matrix.noosphere;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Grow-Only Set (G-Set) — a CRDT that only supports additions.
 *
 * <p>Merge is set union, which is trivially commutative, associative, and
 * idempotent. Used for the Distributed Neuron Identity Ledger in the
 * Noosphere mesh: once a neuron identity is added to the set, it is never
 * removed.
 *
 * <p>Thread-safe: immutable state wrapped in an unmodifiable view.
 *
 * <p>Ref: DESIGN-08 §Federation, Phase 5 Noosphere ROADMAP,
 * Shapiro et al. "Conflict-Free Replicated Data Types" (2011)
 */
public final class GrowOnlySet implements Crdt<GrowOnlySet> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Set<String> elements;
    private final Set<String> tombstones;

    /**
     * Creates a new GrowOnlySet with the given initial elements.
     *
     * @param elements initial elements (defensively copied)
     */
    public GrowOnlySet(Set<String> elements) {
        this(elements, java.util.Set.of());
    }

    /**
     * Creates a new GrowOnlySet with both initial elements and a tombstone
     * set (W-C, M4 causal extension).
     */
    public GrowOnlySet(Set<String> elements, Set<String> tombstones) {
        this.elements = Collections.unmodifiableSet(new HashSet<>(elements));
        this.tombstones = Collections.unmodifiableSet(new HashSet<>(tombstones));
    }

    /**
     * Returns the current elements as an unmodifiable set.
     */
    public Set<String> elements() {
        return elements;
    }

    /** Tombstoned keys — never re-introduced by merge or mergeCausal. */
    public Set<String> tombstones() {
        return tombstones;
    }

    /**
     * Merges this set with another via set union.
     *
     * <p>Satisfies CRDT properties:
     * <ul>
     *   <li>Commutative: union(a, b) ≡ union(b, a)</li>
     *   <li>Associative: union(union(a, b), c) ≡ union(a, union(b, c))</li>
     *   <li>Idempotent: union(a, a) ≡ a</li>
     * </ul>
     *
     * @param other the other GrowOnlySet to merge with
     * @return a new GrowOnlySet containing the union of both sets
     */
    @Override
    public GrowOnlySet merge(GrowOnlySet other) {
        Set<String> merged = new HashSet<>(this.elements);
        merged.addAll(other.elements);
        // tombstone irreversibility: filter out any key that is tombstoned
        // on either side (irreversibility invariant F2 in TLA+ spec)
        merged.removeAll(this.tombstones);
        merged.removeAll(other.tombstones);
        Set<String> mergedTombstones = new HashSet<>(this.tombstones);
        mergedTombstones.addAll(other.tombstones);
        return new GrowOnlySet(merged, mergedTombstones);
    }

    @Override
    public GrowOnlySet mergeCausal(GrowOnlySet other) {
        // Causal merge is the same as merge for G-Set (no partial ordering
        // between inserts); the tombstone filter is what makes this safer
        // than a blind union. Delegated to merge() which now applies the
        // tombstone filter.
        return merge(other);
    }

    @Override
    public GrowOnlySet tombstoneAt(String key, long epoch) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        // G-Set: tombstone = remove element AND record so future merges
        // cannot re-introduce it. Tombstones are stored as bare keys
        // (multiple epochs collapse to one — the epoch argument is
        // accepted for API symmetry with richer CRDTs but not enforced
        // at the G-Set layer; this matches the minimal-implementation
        // direction in Design-DRAFT-MemoryM4.md).
        Set<String> newElems = new HashSet<>(this.elements);
        newElems.remove(key);
        Set<String> newTombstones = new HashSet<>(this.tombstones);
        newTombstones.add(key);
        return new GrowOnlySet(newElems, newTombstones);
    }

    /**
     * Serializes to JSON array format.
     *
     * @return JSON representation, e.g. {@code ["a","b","c"]}
     */
    @Override
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(new ArrayList<>(elements));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize GrowOnlySet", e);
        }
    }

    /**
     * Deserializes from JSON array format.
     *
     * @param json JSON array string, e.g. {@code ["a","b","c"]}
     * @return a new GrowOnlySet with the deserialized elements
     */
    public static GrowOnlySet fromJson(String json) {
        try {
            List<String> list = MAPPER.readValue(json, new TypeReference<List<String>>() {});
            return new GrowOnlySet(new HashSet<>(list));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize GrowOnlySet", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GrowOnlySet that)) return false;
        return elements.equals(that.elements) && tombstones.equals(that.tombstones);
    }

    @Override
    public int hashCode() {
        return elements.hashCode() * 31 + tombstones.hashCode();
    }

    @Override
    public String toString() {
        return "GrowOnlySet" + elements + (tombstones.isEmpty() ? "" : " / tombstones=" + tombstones);
    }
}
