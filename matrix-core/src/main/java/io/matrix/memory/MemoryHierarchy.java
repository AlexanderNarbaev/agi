package io.matrix.memory;

import io.matrix.cluster.FNLMetadata;
import io.matrix.cluster.NeuronInstance;
import io.matrix.cluster.Signal;
import io.matrix.events.ClusterEvent;
import io.matrix.events.ClusterEventType;
import io.matrix.events.EventJournal;
import io.matrix.events.InMemoryEventJournal;
import io.matrix.noosphere.FnlPackage;
import io.matrix.noosphere.NoosphereRegistry;
import io.matrix.snapshot.ClusterSnapshot;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 5-level memory hierarchy as specified in L6 §2.
 *
 * <ul>
 *   <li><b>L1 — Operating (signals):</b> in-memory ring buffer, milliseconds</li>
 *   <li><b>L2 — Short-term (WAL):</b> event journal (Kafka topic in production),
 *       hours</li>
 *   <li><b>L3 — Long-term (knowledge):</b> snapshots (local RocksDB + S3/MinIO
 *       in production), days–years</li>
 *   <li><b>L4 — Structural (instincts):</b> FROZEN neurons, permanent</li>
 *   <li><b>L5 — Collective unconscious (Noosphere):</b> distributed registry,
 *       infinite</li>
 * </ul>
 *
 * <p>This Phase-1 implementation uses in-memory storage for L1–L4 and the
 * existing {@link NoosphereRegistry} for L5. Later phases replace the backing
 * stores with Kafka, RocksDB/S3, and the distributed Noosphere protocol.
 */
@ApplicationScoped
public class MemoryHierarchy {

    private static final int DEFAULT_L1_CAPACITY = 1024;

    private final RingBuffer<Signal> l1Operating;
    private final EventJournal l2Wal;
    private final Map<UUID, ClusterSnapshot> l3LongTerm;
    private final Map<UUID, NeuronInstance> l4Frozen;
    private final NoosphereRegistry l5Noosphere;
    private final String instanceId;

    /**
     * CDI constructor — initialises all levels with Phase-1 in-memory defaults.
     */
    public MemoryHierarchy() {
        this(new InMemoryEventJournal(), new NoosphereRegistry(),
                DEFAULT_L1_CAPACITY, "default-instance");
    }

    /**
     * Test/assembly constructor allowing the caller to supply the journal and
     * noosphere registry.
     */
    public MemoryHierarchy(EventJournal journal, NoosphereRegistry noosphere,
                           int l1Capacity, String instanceId) {
        this.l1Operating = new RingBuffer<>(l1Capacity);
        this.l2Wal = Objects.requireNonNull(journal);
        this.l3LongTerm = new HashMap<>();
        this.l4Frozen = new HashMap<>();
        this.l5Noosphere = Objects.requireNonNull(noosphere);
        this.instanceId = Objects.requireNonNull(instanceId);
    }

    // ─── L1: Operating (signals) ──────────────────────────────────────────

    /**
     * Stores a signal in the L1 operating ring buffer. When the buffer is
     * full the oldest signal is evicted.
     */
    public void storeL1(Signal signal) {
        l1Operating.put(Objects.requireNonNull(signal));
    }

    /**
     * Recalls up to {@code count} most recent L1 signals (newest first).
     */
    public List<Signal> recallL1(int count) {
        if (count <= 0) {
            return List.of();
        }
        List<Signal> all = l1Operating.toListNewestFirst();
        return all.size() <= count ? all : all.subList(0, count);
    }

    /**
     * Returns the number of signals currently held in L1.
     */
    public int l1Size() {
        return l1Operating.size();
    }

    // ─── L2: Short-term (WAL) ─────────────────────────────────────────────

    /**
     * Checkpoints L1 → L2: drains the operating ring buffer and appends one
     * {@link ClusterEventType#SIGNAL_EMITTED} event per signal to the WAL.
     *
     * @return the number of signals flushed
     */
    public int checkpointL2() {
        List<Signal> drained = l1Operating.drainAll();
        for (Signal s : drained) {
            ClusterEvent event = ClusterEvent.of(
                    ClusterEventType.SIGNAL_EMITTED,
                    instanceId,
                    s.sourceId() != null ? toNeuronId(s) : null,
                    formatPayload(s));
            l2Wal.append(event);
        }
        return drained.size();
    }

    /**
     * Replays L2 events from the given offset (inclusive).
     */
    public List<ClusterEvent> replayL2(long fromOffset) {
        return l2Wal.replayFrom(fromOffset);
    }

    /**
     * Returns the total number of events persisted in L2.
     */
    public long l2Size() {
        return l2Wal.size();
    }

    // ─── L3: Long-term (knowledge) ────────────────────────────────────────

    /**
     * Consolidates L2 → L3: builds a {@link ClusterSnapshot} from the frozen
     * L4 neurons and the current L2 event index, then stores it keyed by a
     * fresh snapshot id.
     *
     * @return the id of the newly created snapshot
     */
    public UUID consolidateL3() {
        long eventIndex = Math.max(0L, l2Wal.size() - 1);
        List<ClusterSnapshot.NeuronRecord> records = new ArrayList<>();
        for (NeuronInstance neuron : l4Frozen.values()) {
            records.add(ClusterSnapshot.NeuronRecord.from(neuron));
        }
        UUID snapshotId = UUID.randomUUID();
        ClusterSnapshot snapshot = new ClusterSnapshot(
                snapshotId.toString(),
                instanceId,
                System.currentTimeMillis(),
                eventIndex,
                records.size(),
                records);
        l3LongTerm.put(snapshotId, snapshot);
        return snapshotId;
    }

    /**
     * Loads an L3 snapshot by id.
     */
    public Optional<ClusterSnapshot> loadL3(UUID snapshotId) {
        return Optional.ofNullable(l3LongTerm.get(snapshotId));
    }

    /**
     * Returns the number of snapshots held in L3.
     */
    public int l3Size() {
        return l3LongTerm.size();
    }

    // ─── L4: Structural (instincts) ──────────────────────────────────────

    /**
     * Registers a FROZEN neuron in L4. Only frozen neurons are accepted.
     */
    public void registerFrozenL4(NeuronInstance neuron) {
        Objects.requireNonNull(neuron, "neuron");
        if (!neuron.isFrozen()) {
            throw new IllegalArgumentException(
                    "Only FROZEN neurons may be registered in L4: " + neuron.id());
        }
        l4Frozen.put(neuron.id().uuid(), neuron);
    }

    /**
     * Retrieves a frozen L4 neuron by its id.
     */
    public Optional<NeuronInstance> getFrozenL4(UUID neuronId) {
        return Optional.ofNullable(l4Frozen.get(neuronId));
    }

    /**
     * Returns the number of frozen neurons held in L4.
     */
    public int l4Size() {
        return l4Frozen.size();
    }

    // ─── L5: Collective unconscious (Noosphere) ──────────────────────────

    /**
     * Publishes an FNL to the Noosphere L5 registry.
     *
     * @param fnl metadata describing the FNL
     * @param data serialised FNL payload (used to derive a snapshot hash)
     * @return the registry entry id assigned to the published FNL
     */
    public UUID publishToNoosphereL5(FNLMetadata fnl, byte[] data) {
        Objects.requireNonNull(fnl, "fnl");
        String hash = (data == null || data.length == 0)
                ? "" : Integer.toHexString(java.util.Arrays.hashCode(data));
        FnlPackage pkg = FnlPackage.builder()
                .name(fnl.name())
                .type("FNL")
                .accuracy(fnl.accuracy())
                .generation(fnl.generation())
                .description("Published via MemoryHierarchy")
                .tags(fnl.name())
                .snapshotHash(hash)
                .authorInstanceId(instanceId)
                .build();
        NoosphereRegistry.PublishResult result = l5Noosphere.publish(pkg);
        if (!result.success()) {
            throw new IllegalStateException("Noosphere publish rejected: " + result.message());
        }
        return result.entryId();
    }

    /**
     * Searches the L5 Noosphere registry for entries tagged with the given
     * tag (matched against FNL name or tags).
     */
    public List<FNLMetadata> searchNoosphereL5(String tag) {
        Objects.requireNonNull(tag, "tag");
        String lowered = tag.toLowerCase();
        List<FNLMetadata> results = new ArrayList<>();
        for (NoosphereRegistry.RegistryEntry entry : l5Noosphere.activeEntries()) {
            FnlPackage pkg = entry.fnlPackage();
            if (matchesTag(pkg, lowered)) {
                results.add(toMetadata(pkg, entry.entryId()));
            }
        }
        return results;
    }

    /**
     * Returns the total number of entries in the L5 registry.
     */
    public int l5Size() {
        return l5Noosphere.size();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private static boolean matchesTag(FnlPackage pkg, String loweredTag) {
        if (pkg.name() != null && pkg.name().toLowerCase().contains(loweredTag)) {
            return true;
        }
        if (pkg.tags() != null) {
            for (String t : pkg.tags()) {
                if (t != null && t.toLowerCase().contains(loweredTag)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static FNLMetadata toMetadata(FnlPackage pkg, UUID entryId) {
        return new FNLMetadata(
                entryId,
                pkg.name() != null ? pkg.name() : "unnamed",
                0,
                pkg.generation(),
                pkg.accuracy(),
                pkg.publishedAt());
    }

    private static io.matrix.cluster.NeuronId toNeuronId(Signal s) {
        return s.sourceId();
    }

    private static String formatPayload(Signal s) {
        return "signal{src=" + s.sourceId() + ",tgt=" + s.targetId()
                + ",v=" + s.value() + ",ts=" + s.timestamp() + "}";
    }

    /**
     * Minimal bounded ring buffer used by L1 operating memory.
     */
    static final class RingBuffer<T> {
        private final int capacity;
        private final Deque<T> deque;

        RingBuffer(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be > 0, got: " + capacity);
            }
            this.capacity = capacity;
            this.deque = new ArrayDeque<>(capacity);
        }

        void put(T item) {
            Objects.requireNonNull(item);
            if (deque.size() >= capacity) {
                deque.pollFirst();
            }
            deque.addLast(item);
        }

        List<T> drainAll() {
            List<T> copy = new ArrayList<>(deque);
            deque.clear();
            return copy;
        }

        @SuppressWarnings("unchecked")
        List<T> toListNewestFirst() {
            Object[] arr = deque.toArray();
            List<T> result = new ArrayList<>(arr.length);
            for (int i = arr.length - 1; i >= 0; i--) {
                result.add((T) arr[i]);
            }
            return result;
        }

        int size() {
            return deque.size();
        }

        int capacity() {
            return capacity;
        }
    }
}
