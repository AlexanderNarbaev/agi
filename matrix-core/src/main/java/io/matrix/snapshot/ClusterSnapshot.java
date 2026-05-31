package io.matrix.snapshot;

import io.matrix.cluster.NeuronInstance;

import java.util.BitSet;
import java.util.List;
import java.util.UUID;

/**
 * Serializable snapshot of a neuron cluster state.
 *
 * <p>Contains all neurons with their truth tables, enabling full
 * state restoration. Stored as {@code .ldn} JSON files.
 *
 * <p>Ref: L6_Memory.md §4.2
 */
public record ClusterSnapshot(
        String snapshotId,
        String instanceId,
        long createdAt,
        long eventIndex,
        int neuronCount,
        List<NeuronRecord> neurons
) {
    public ClusterSnapshot() {
        this("", "", 0L, 0L, 0, List.of());
    }

    /**
     * Lightweight neuron representation for snapshot serialization.
     */
    public record NeuronRecord(
            String neuronId,
            long generation,
            int k,
            long[] truthTableBits,
            String state
    ) {
        public NeuronRecord() {
            this("", 0L, 0, new long[0], "");
        }

        public static NeuronRecord from(NeuronInstance neuron) {
            BitSet bits = neuron.truthTable().table();
            return new NeuronRecord(
                    neuron.id().uuid().toString(),
                    neuron.id().generation(),
                    neuron.k(),
                    bits.toLongArray(),
                    neuron.state().name());
        }
    }

    /**
     * Timestamp as ISO-8601 string for filenames.
     */
    public String createdAtIso() {
        return java.time.Instant.ofEpochMilli(createdAt).toString()
                .replace(":", "-");
    }
}
