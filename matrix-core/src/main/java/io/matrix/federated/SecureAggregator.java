package io.matrix.federated;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Secure aggregation with Byzantine-resilient outlier removal.
 */
@ApplicationScoped
public class SecureAggregator {

    private static final Logger log = LoggerFactory.getLogger(SecureAggregator.class);

    /**
     * Aggregate updates with outlier removal.
     */
    public boolean[] aggregate(List<LocalUpdate> updates) {
        if (updates.isEmpty()) return new boolean[0];

        // Remove outliers
        List<LocalUpdate> filtered = removeOutliers(updates);
        log.debug("Filtered {} -> {} updates", updates.size(), filtered.size());

        // XOR aggregation
        int maxLen = filtered.stream()
                .mapToInt(u -> u.update().length)
                .max().orElse(0);

        boolean[] result = new boolean[maxLen];
        for (LocalUpdate update : filtered) {
            for (int i = 0; i < update.update().length; i++) {
                result[i] ^= update.update()[i];
            }
        }
        return result;
    }

    /**
     * Remove outlier updates using median-based detection.
     */
    private List<LocalUpdate> removeOutliers(List<LocalUpdate> updates) {
        if (updates.size() <= 2) return new ArrayList<>(updates);

        double[] losses = updates.stream()
                .mapToDouble(LocalUpdate::loss)
                .sorted()
                .toArray();

        double median = losses[losses.length / 2];
        double threshold = median * 2.0;

        List<LocalUpdate> filtered = new ArrayList<>();
        for (LocalUpdate update : updates) {
            if (update.loss() <= threshold) {
                filtered.add(update);
            } else {
                log.warn("Outlier removed: node={} loss={} threshold={}",
                        update.nodeId(), update.loss(), threshold);
            }
        }

        return filtered;
    }
}
