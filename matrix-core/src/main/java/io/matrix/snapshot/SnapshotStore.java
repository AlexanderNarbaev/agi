package io.matrix.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.neuron.TruthTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Saves and loads {@link ClusterSnapshot} as {@code .ldn} JSON files.
 *
 * <p>Phase 1: simple JSON via Jackson. Later phases will use Avro binary
 * and tar archives with cryptographic signatures.
 *
 * <p>Ref: L6_Memory.md §4.2, §4.3
 */
public final class SnapshotStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path storeDir;
    private final String instanceId;

    public SnapshotStore(Path storeDir, String instanceId) {
        this.storeDir = storeDir;
        this.instanceId = instanceId;
    }

    /**
     * Creates a snapshot from current cluster state.
     */
    public ClusterSnapshot createSnapshot(Map<NeuronId, NeuronInstance> neurons,
                                           long eventIndex) {
        List<ClusterSnapshot.NeuronRecord> records = new ArrayList<>();
        for (var entry : neurons.entrySet()) {
            records.add(ClusterSnapshot.NeuronRecord.from(entry.getValue()));
        }

        return new ClusterSnapshot(
                UUID.randomUUID().toString(),
                instanceId,
                System.currentTimeMillis(),
                eventIndex,
                records.size(),
                records);
    }

    /**
     * Saves a snapshot to a {@code .ldn} JSON file.
     *
     * @return the file path of the saved snapshot
     */
    public Path save(ClusterSnapshot snapshot) throws IOException {
        Files.createDirectories(storeDir);

        String filename = "snapshot_" + snapshot.createdAtIso() + "_"
                + instanceId + ".ldn";
        Path filePath = storeDir.resolve(filename);

        String json = MAPPER.writeValueAsString(snapshot);
        Files.writeString(filePath, json);

        return filePath;
    }

    /**
     * Loads the most recent snapshot from the store directory.
     *
     * @return the snapshot, or {@code null} if none found
     */
    public ClusterSnapshot loadLatest() throws IOException {
        if (!Files.exists(storeDir)) {
            return null;
        }

        try (var stream = Files.list(storeDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".ldn"))
                    .sorted((a, b) -> b.compareTo(a))
                    .findFirst()
                    .map(this::load)
                    .orElse(null);
        }
    }

    /**
     * Loads a snapshot from a specific file.
     */
    public ClusterSnapshot load(Path filePath) {
        try {
            String json = Files.readString(filePath);
            return MAPPER.readValue(json, ClusterSnapshot.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load snapshot: " + filePath, e);
        }
    }

    /**
     * Restores neurons from a snapshot.
     */
    public List<NeuronInstance> restoreNeurons(ClusterSnapshot snapshot) {
        List<NeuronInstance> neurons = new ArrayList<>();
        for (var record : snapshot.neurons()) {
            NeuronId id = new NeuronId(
                    UUID.fromString(record.neuronId()),
                    record.generation());
            BitSet bits = BitSet.valueOf(record.truthTableBits());
            TruthTable table = TruthTable.of(record.k(), bits);
            NeuronInstance.State state = NeuronInstance.State.valueOf(record.state());
            neurons.add(new NeuronInstance(id, table, state));
        }
        return neurons;
    }

    /**
     * Lists all .ldn files in the store directory.
     */
    public List<Path> listSnapshots() throws IOException {
        if (!Files.exists(storeDir)) {
            return List.of();
        }
        try (var stream = Files.list(storeDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".ldn"))
                    .sorted()
                    .toList();
        }
    }
}
