package io.matrix.noosphere;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Epoch-aware causal CRDT for the M4 memory layer.
 *
 * <p>This is the Java counterpart of {@code formal/MemoryM4Causal.tla}. It
 * exists because {@link GrowOnlySet} — the class the specification originally
 * named — cannot express the specification's semantics: it stores tombstone
 * keys with no epoch and filters them unconditionally, which is a two-phase
 * set, not the epoch-ordered causal set the model describes. The decision is
 * recorded in {@code docs-v2/architecture/ADR-2026-10-30-004-noosphere-crdt-semantics.md}.
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li>Every key carries an <b>insert epoch</b> — the logical clock at which
 *       it was last inserted. A stale insert (an epoch below the recorded
 *       one) is ignored.</li>
 *   <li>Every tombstoned key carries a <b>tombstone epoch</b>. A tombstone
 *       covers every insert with an epoch at or below its own, so the key
 *       cannot be resurrected by an old message. An insert with a
 *       <i>newer</i> epoch does resurrect it — this is the property the model
 *       calls F2.</li>
 *   <li>A key is <b>live</b> iff it has an insert epoch and that epoch is
 *       strictly greater than any tombstone epoch for the same key.</li>
 *   <li><b>Frozen keys</b> may never be inserted at all.</li>
 *   <li><b>Merge</b> is commutative, associative and idempotent: for each key
 *       the greater insert epoch and the greater tombstone epoch win.</li>
 * </ul>
 *
 * <p>Pure functions (CONSTITUTION I): no randomness, no wall-clock. Every
 * operation returns a new instance; the state is immutable.
 *
 * <p>Serialisation is versioned and lossless: tombstones survive a
 * round-trip, and a legacy payload without a version is rejected rather than
 * silently reinterpreted.
 */
public final class EpochAwareCausalSet implements Crdt<EpochAwareCausalSet> {

    /** Current wire-format version. */
    public static final int SCHEMA_VERSION = 2;

    /** Sentinel for "no tombstone recorded" — smaller than any legal epoch. */
    private static final long NO_EPOCH = Long.MIN_VALUE;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Long> insertEpochs;
    private final Map<String, Long> tombstoneEpochs;
    private final Set<String> frozenKeys;

    public EpochAwareCausalSet() {
        this(Set.of(), Map.of(), Map.of());
    }

    public EpochAwareCausalSet(Set<String> frozenKeys,
                               Map<String, Long> insertEpochs,
                               Map<String, Long> tombstoneEpochs) {
        this.frozenKeys = Collections.unmodifiableSet(new TreeSet<>(frozenKeys));
        this.insertEpochs = Collections.unmodifiableMap(new TreeMap<>(insertEpochs));
        this.tombstoneEpochs = Collections.unmodifiableMap(new TreeMap<>(tombstoneEpochs));
    }

    // ── reads ──────────────────────────────────────────────────────────────────

    /** The live keys — those not covered by a tombstone. */
    public Set<String> keys() {
        Set<String> live = new TreeSet<>();
        for (Map.Entry<String, Long> e : insertEpochs.entrySet()) {
            if (isLive(e.getKey(), e.getValue())) {
                live.add(e.getKey());
            }
        }
        return Collections.unmodifiableSet(live);
    }

    /** All keys with an insert epoch, including tombstoned ones. */
    public Set<String> insertedKeys() {
        return Collections.unmodifiableSet(new TreeSet<>(insertEpochs.keySet()));
    }

    /** The keys carrying a tombstone. */
    public Set<String> tombstonedKeys() {
        return Collections.unmodifiableSet(new TreeSet<>(tombstoneEpochs.keySet()));
    }

    /** The insert epoch of a key, or {@code NO_EPOCH} when it was never inserted. */
    public long insertEpoch(String key) {
        return insertEpochs.getOrDefault(key, NO_EPOCH);
    }

    /** The tombstone epoch of a key, or {@code NO_EPOCH} when it has none. */
    public long tombstoneEpoch(String key) {
        return tombstoneEpochs.getOrDefault(key, NO_EPOCH);
    }

    public boolean isTombstoned(String key) {
        return tombstoneEpochs.containsKey(key);
    }

    public boolean isFrozen(String key) {
        return frozenKeys.contains(key);
    }

    public Set<String> frozenKeys() {
        return frozenKeys;
    }

    /**
     * Whether an insert of {@code key} at {@code epoch} would be accepted.
     *
     * <p>An insert is rejected when the key is frozen, when it is stale
     * (its epoch is below the recorded insert epoch), or when a tombstone
     * covers that epoch.
     */
    public boolean canInsert(String key, long epoch) {
        if (key == null) {
            return false;
        }
        if (frozenKeys.contains(key)) {
            return false;
        }
        if (epoch < insertEpochs.getOrDefault(key, Long.MIN_VALUE)) {
            return false;
        }
        return tombstoneEpochs.getOrDefault(key, NO_EPOCH) < epoch;
    }

    private boolean isLive(String key, long insertEpoch) {
        return tombstoneEpochs.getOrDefault(key, NO_EPOCH) < insertEpoch;
    }

    // ── writes ─────────────────────────────────────────────────────────────────

    /**
     * Insert a key at a logical epoch.
     *
     * <p>A rejected insert returns this instance unchanged — the CRDT way:
     * a stale or covered message is dropped, not an error. Use
     * {@link #canInsert(String, long)} to distinguish the two.
     */
    public EpochAwareCausalSet insert(String key, long epoch) {
        Objects.requireNonNull(key, "key");
        if (!canInsert(key, epoch)) {
            return this;
        }
        Map<String, Long> inserts = new TreeMap<>(insertEpochs);
        inserts.put(key, epoch);
        return new EpochAwareCausalSet(frozenKeys, inserts, tombstoneEpochs);
    }

    /**
     * Tombstone a key at a logical epoch.
     *
     * <p>The tombstone removes the key from the live set and is recorded with
     * its epoch. A tombstone older than one already recorded is ignored — the
     * newer tombstone wins, which is what makes merge commutative.
     */
    @Override
    public EpochAwareCausalSet tombstoneAt(String key, long epoch) {
        Objects.requireNonNull(key, "key");
        long current = tombstoneEpochs.getOrDefault(key, NO_EPOCH);
        if (epoch < current) {
            return this;
        }
        Map<String, Long> tombstones = new TreeMap<>(tombstoneEpochs);
        tombstones.put(key, epoch);
        return new EpochAwareCausalSet(frozenKeys, insertEpochs, tombstones);
    }

    /**
     * Merge with another replica.
     *
     * <p>For each key the greater insert epoch and the greater tombstone
     * epoch win. The result is the same regardless of the order of the
     * arguments, and merging a replica with itself changes nothing.
     */
    @Override
    public EpochAwareCausalSet merge(EpochAwareCausalSet other) {
        Objects.requireNonNull(other, "other");
        Map<String, Long> inserts = new TreeMap<>(insertEpochs);
        Map<String, Long> tombstones = new TreeMap<>(tombstoneEpochs);

        for (Map.Entry<String, Long> e : other.insertEpochs.entrySet()) {
            inserts.merge(e.getKey(), e.getValue(), Math::max);
        }
        for (Map.Entry<String, Long> e : other.tombstoneEpochs.entrySet()) {
            tombstones.merge(e.getKey(), e.getValue(), Math::max);
        }
        Set<String> frozen = new TreeSet<>(frozenKeys);
        frozen.addAll(other.frozenKeys);
        return new EpochAwareCausalSet(frozen, inserts, tombstones);
    }

    /**
     * Causal merge. The epoch ordering IS this structure's merge rule, so
     * this is the same operation as {@link #merge(Object)} — unlike a G-Set,
     * where the causal variant is a lossy fallback.
     */
    @Override
    public EpochAwareCausalSet mergeCausal(EpochAwareCausalSet other) {
        return merge(other);
    }

    // ── persistence ────────────────────────────────────────────────────────────

    /**
     * Serialise to a versioned JSON document.
     *
     * <p>The output is deterministic — the maps and sets are sorted — so it
     * can be hashed or compared. Tombstones are included: a key deleted
     * before serialisation must not be resurrectable after it.
     */
    @Override
    public String toJson() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", SCHEMA_VERSION);
        ArrayNode elements = root.putArray("elements");
        for (String key : keys()) {
            elements.add(key);
        }
        ObjectNode tombstoneEpochsNode = root.putObject("tombstoneEpochs");
        for (Map.Entry<String, Long> e : tombstoneEpochs.entrySet()) {
            tombstoneEpochsNode.put(e.getKey(), e.getValue());
        }
        ObjectNode insertEpochsNode = root.putObject("insertEpochs");
        for (Map.Entry<String, Long> e : insertEpochs.entrySet()) {
            insertEpochsNode.put(e.getKey(), e.getValue());
        }
        ArrayNode frozen = root.putArray("frozenKeys");
        for (String key : frozenKeys) {
            frozen.add(key);
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize EpochAwareCausalSet", e);
        }
    }

    /**
     * Deserialise from the versioned JSON document.
     *
     * @throws IllegalArgumentException when the payload is malformed, carries
     *         an unknown schema version, or contradicts itself
     */
    public static EpochAwareCausalSet fromJson(String json) {
        Objects.requireNonNull(json, "json");
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed EpochAwareCausalSet payload", e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("EpochAwareCausalSet payload must be a JSON object");
        }
        JsonNode versionNode = root.get("schemaVersion");
        if (versionNode == null) {
            throw new IllegalArgumentException("EpochAwareCausalSet payload has no schemaVersion");
        }
        int version = versionNode.asInt(-1);
        if (version != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported EpochAwareCausalSet schemaVersion: " + version);
        }

        Set<String> elements = new HashSet<>();
        JsonNode elementsNode = root.get("elements");
        if (elementsNode != null) {
            if (!elementsNode.isArray()) {
                throw new IllegalArgumentException("'elements' must be an array");
            }
            for (JsonNode node : elementsNode) {
                elements.add(node.asText());
            }
        }

        Map<String, Long> insertEpochs = readEpochMap(root.get("insertEpochs"), "insertEpochs");
        Map<String, Long> tombstoneEpochs = readEpochMap(root.get("tombstoneEpochs"), "tombstoneEpochs");

        // The live elements must agree with the recorded epochs: an element
        // whose insert epoch is covered by a tombstone would be a state the
        // structure can never produce.
        for (Map.Entry<String, Long> e : insertEpochs.entrySet()) {
            long tombstone = tombstoneEpochs.getOrDefault(e.getKey(), NO_EPOCH);
            boolean live = tombstone < e.getValue();
            if (live != elements.contains(e.getKey())) {
                throw new IllegalArgumentException(
                        "Contradictory state for key '" + e.getKey()
                                + "': elements and epochs disagree");
            }
        }
        for (String key : elements) {
            if (!insertEpochs.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Element '" + key + "' has no insert epoch");
            }
        }

        Set<String> frozen = new TreeSet<>();
        JsonNode frozenNode = root.get("frozenKeys");
        if (frozenNode != null) {
            if (!frozenNode.isArray()) {
                throw new IllegalArgumentException("'frozenKeys' must be an array");
            }
            for (JsonNode node : frozenNode) {
                frozen.add(node.asText());
            }
        }
        return new EpochAwareCausalSet(frozen, insertEpochs, tombstoneEpochs);
    }

    private static Map<String, Long> readEpochMap(JsonNode node, String field) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (node == null) {
            return map;
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("'" + field + "' must be an object");
        }
        node.fields().forEachRemaining(entry -> {
            if (!entry.getValue().canConvertToLong()) {
                throw new IllegalArgumentException(
                        "'" + field + "' entry '" + entry.getKey() + "' is not an integer");
            }
            map.put(entry.getKey(), entry.getValue().asLong());
        });
        return map;
    }

    // ── value semantics ────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EpochAwareCausalSet that)) {
            return false;
        }
        return insertEpochs.equals(that.insertEpochs)
                && tombstoneEpochs.equals(that.tombstoneEpochs)
                && frozenKeys.equals(that.frozenKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(insertEpochs, tombstoneEpochs, frozenKeys);
    }

    @Override
    public String toString() {
        return "EpochAwareCausalSet[keys=" + keys()
                + ", tombstones=" + tombstoneEpochs
                + (frozenKeys.isEmpty() ? "" : ", frozen=" + frozenKeys) + "]";
    }
}
