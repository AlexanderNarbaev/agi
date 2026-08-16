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

    /**
     * Creates a new GrowOnlySet with the given initial elements.
     *
     * @param elements initial elements (defensively copied)
     */
    public GrowOnlySet(Set<String> elements) {
        this.elements = Collections.unmodifiableSet(new HashSet<>(elements));
    }

    /**
     * Returns the current elements as an unmodifiable set.
     */
    public Set<String> elements() {
        return elements;
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
        return new GrowOnlySet(merged);
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
        return elements.equals(that.elements);
    }

    @Override
    public int hashCode() {
        return elements.hashCode();
    }

    @Override
    public String toString() {
        return "GrowOnlySet" + elements;
    }
}
