package io.matrix.federated;

/**
 * Local training update from a federated participant.
 */
public record LocalUpdate(
        String nodeId,
        boolean[] update,
        int dataSize,
        double loss
) {
    /**
     * Compute weight for aggregation based on data size and loss.
     */
    public double weight() {
        return dataSize / (1.0 + loss);
    }
}
