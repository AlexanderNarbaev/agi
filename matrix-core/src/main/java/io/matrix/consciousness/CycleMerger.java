package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 244 — CycleMerger (batch similar inputs).
 *
 * <p>Identifies inputs that should be merged into a single
 * cycle (e.g., duplicate text). Returns deduplicated list.
 *
 * <p>Deterministic given input order.
 */
public final class CycleMerger {

    public record MergeResult(List<String> merged, int duplicates) {}

    public static MergeResult merge(List<String> inputs) {
        List<String> out = new ArrayList<>();
        int duplicates = 0;
        for (String in : inputs) {
            if (out.contains(in)) {
                duplicates++;
            } else {
                out.add(in);
            }
        }
        return new MergeResult(out, duplicates);
    }

    /** Count cycles saved by deduplication. */
    public static int savedCycles(MergeResult result) {
        return result.duplicates();
    }
}
