package io.matrix.noosphere;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Two-Phase Set (2P-Set) — a CRDT with additions and permanent tombstones.
 *
 * <p>RECON-W39: this class used to be documented and understood as a
 * Grow-Only Set, but it carries tombstones and removes tombstoned keys on
 * every merge. That is the definition of a two-phase set, and the
 * distinction matters: a G-Set never removes anything, while this class
 * removes a key FOREVER once it is tombstoned — no later insert, at any
 * epoch, can bring it back. The name is kept (it is public API and renaming
 * it would break imports) but the semantics are now stated correctly.
 *
 * <p><b>What this class is NOT.</b> It is not the epoch-aware causal CRDT
 * that {@code formal/MemoryM4Causal.tla} models: that model has tombstones
 * carrying an epoch and a per-key vector clock, and it permits an insert
 * NEWER than a tombstone to resurrect the key. This class has no epoch
 * ordering at all — {@link #tombstoneAt(String, long)} accepts the epoch for
 * interface symmetry and discards it. The epoch-aware counterpart is
 * {@link EpochAwareCausalSet}; the decision is recorded in
 * {@code docs-v2/architecture/ADR-2026-10-30-004-noosphere-crdt-semantics.md}.
 *
 * <p>Merge is set union minus the tombstoned keys, which is commutative,
 * associative and idempotent.
 *
 * <p>Thread-safe: immutable state wrapped in an unmodifiable view.
 *
 * <p>Ref: DESIGN-08 §Federation, Phase 5 Noosphere ROADMAP,
 * Shapiro et al. "Conflict-Free Replicated Data Types" (2011),
 * Almeida et al. "A scalable, portable, and memory-efficient CRDT" (2015)
 */
public final class GrowOnlySet implements Crdt<GrowOnlySet> {

    /** Current wire-format version. */
    public static final int SCHEMA_VERSION = 2;

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
        // 2P-Set: tombstone = remove the element AND record the key so no
        // future merge can re-introduce it. Tombstones are stored as bare
        // keys — multiple epochs collapse to one. The epoch argument is
        // accepted for API symmetry with richer CRDTs and is NOT enforced
        // here: this class has no epoch ordering, so a tombstone is
        // permanent. EpochAwareCausalSet implements the epoch-ordered
        // semantics the M4 model describes.
        Set<String> newElems = new HashSet<>(this.elements);
        newElems.remove(key);
        Set<String> newTombstones = new HashSet<>(this.tombstones);
        newTombstones.add(key);
        return new GrowOnlySet(newElems, newTombstones);
    }

    /**
     * Serializes to a versioned JSON document.
     *
     * <p>RECON-W39: this used to write a bare JSON array of the ELEMENTS
     * only. The tombstones were dropped, so a key deleted before a restart
     * became eligible for re-introduction after it — the deletion history did
     * not survive persistence. The format is now versioned and carries the
     * tombstones:
     *
     * <pre>{@code {"schemaVersion":2,"elements":["a"],"tombstones":["b"]}}</pre>
     *
     * <p>The output is deterministic (the sets are sorted), so it can be
     * hashed or compared.
     *
     * @return the JSON representation of this set
     */
    @Override
    public String toJson() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        ArrayNode elementsNode = root.putArray("elements");
        for (String key : new TreeSet<>(elements)) {
            elementsNode.add(key);
        }
        ArrayNode tombstonesNode = root.putArray("tombstones");
        for (String key : new TreeSet<>(tombstones)) {
            tombstonesNode.add(key);
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize GrowOnlySet", e);
        }
    }

    /**
     * Deserializes from the versioned JSON document.
     *
     * <p>The legacy format — a bare JSON array of elements, written before
     * RECON-W39 — is still READABLE, and is read as version 1: it carries no
     * tombstones, which is exactly what it did not contain. An unknown
     * version is REJECTED rather than silently reinterpreted, and a
     * contradictory payload (a key that is both an element and a tombstone)
     * is rejected too.
     *
     * @param json the JSON payload
     * @return a new GrowOnlySet with the deserialized elements and tombstones
     * @throws IllegalArgumentException when the payload is malformed, carries
     *         an unknown schema version, or contradicts itself
     */
    public static GrowOnlySet fromJson(String json) {
        Objects.requireNonNull(json, "json");
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed GrowOnlySet payload", e);
        }
        if (root == null || root.isNull()) {
            throw new IllegalArgumentException("Empty GrowOnlySet payload");
        }
        // Legacy format: a bare array of element names.
        if (root.isArray()) {
            Set<String> elements = new HashSet<>();
            for (JsonNode node : root) {
                elements.add(node.asText());
            }
            return new GrowOnlySet(elements, Set.of());
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("GrowOnlySet payload must be an object or an array");
        }
        JsonNode versionNode = root.get("schemaVersion");
        if (versionNode == null) {
            throw new IllegalArgumentException("GrowOnlySet payload has no schemaVersion");
        }
        int version = versionNode.asInt(-1);
        if (version != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported GrowOnlySet schemaVersion: " + version);
        }
        Set<String> elements = readStringSet(root.get("elements"), "elements");
        Set<String> tombstones = readStringSet(root.get("tombstones"), "tombstones");
        Set<String> contradiction = new HashSet<>(elements);
        contradiction.retainAll(tombstones);
        if (!contradiction.isEmpty()) {
            throw new IllegalArgumentException(
                    "Contradictory GrowOnlySet state: " + contradiction
                            + " is both an element and a tombstone");
        }
        return new GrowOnlySet(elements, tombstones);
    }

    private static Set<String> readStringSet(JsonNode node, String field) {
        Set<String> values = new HashSet<>();
        if (node == null) {
            return values;
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("'" + field + "' must be an array");
        }
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return values;
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
