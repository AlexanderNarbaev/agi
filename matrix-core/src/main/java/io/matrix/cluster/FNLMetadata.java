package io.matrix.cluster;

import java.util.UUID;

/**
 * Metadata for a loaded FNL (Functional Neural Layer).
 *
 * <p>Ref: L3_Neurocluster_Arch.md §4.2, L6_Memory.md §5
 *
 * @param fnlId            unique identifier
 * @param name             functional name ("vision", "motor", "language")
 * @param neuronCount      number of neurons in this FNL
 * @param generation       evolution generation
 * @param accuracy         measured accuracy [0.0, 1.0]
 * @param createdTimestamp creation epoch millis
 */
public record FNLMetadata(
        UUID fnlId,
        String name,
        int neuronCount,
        int generation,
        double accuracy,
        long createdTimestamp
) {
    public FNLMetadata {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("FNL name must not be blank");
        }
        if (neuronCount < 0) {
            throw new IllegalArgumentException("neuronCount must be >= 0");
        }
        if (accuracy < 0.0 || accuracy > 1.0) {
            throw new IllegalArgumentException("accuracy must be in [0.0, 1.0]");
        }
    }
}
