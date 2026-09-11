package io.matrix.noosphere;

import io.matrix.neuron.EnrichedNeuron;
import io.matrix.neuron.Neurotransmitter;
import io.matrix.neuron.TruthTable;

import java.util.List;
import java.util.UUID;

/**
 * DESIGN-22 — FnlEntry: a single neuron in the unified FnlRegistry pool.
 * INV-FNL-ONE enforced: provenance must be set.
 *
 * <p>Carries lineage (parents) so merge operations preserve audit
 * trail (CONSTITUTION VII).
 */
public record FnlEntry(
        UUID id,
        TruthTable table,
        double magnitude,
        double[] chemicalVector,
        Neurotransmitter tag,
        String provenance,         // "Qwen2.5-0.5B", "Llama-3.2-1B", etc.
        List<UUID> parents,        // non-empty if merged
        long createdTimestamp,
        int generation
) {
    public FnlEntry {
        if (id == null) throw new IllegalArgumentException("id");
        if (table == null) throw new IllegalArgumentException("table");
        if (provenance == null || provenance.isBlank()) {
            throw new IllegalArgumentException(
                    "INV-FNL-ONE: provenance must be set");
        }
        if (magnitude < 0.0 || magnitude > 1.0) {
            throw new IllegalArgumentException("magnitude out of range: " + magnitude);
        }
        if (chemicalVector == null || chemicalVector.length != EnrichedNeuron.CHEMICAL_DIM) {
            throw new IllegalArgumentException("chemicalVector must have "
                    + EnrichedNeuron.CHEMICAL_DIM + " dims");
        }
        if (tag == null) throw new IllegalArgumentException("tag");
        parents = parents == null ? List.of() : List.copyOf(parents);
    }

    /** Factory: build entry from an EnrichedNeuron + provenance + id. */
    public static FnlEntry fromEnriched(UUID id, EnrichedNeuron neuron,
                                       String provenance, long timestamp) {
        return new FnlEntry(
                id,
                neuron.table(),
                neuron.magnitude(),
                neuron.chemicalVector(),
                neuron.tag(),
                provenance,
                List.of(),
                timestamp,
                0
        );
    }

    /** Builder: with new id (used by FnlRegistry.append). */
    public FnlEntry withId(UUID newId) {
        return new FnlEntry(newId, table, magnitude, chemicalVector, tag,
                provenance, parents, createdTimestamp, generation);
    }

    /** Builder: with parents (for merged entries). */
    public FnlEntry withParents(List<UUID> newParents, long newTimestamp, int newGen) {
        return new FnlEntry(id, table, magnitude, chemicalVector, tag,
                provenance, newParents, newTimestamp, newGen);
    }
}
