package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 277 — CycleCombiner (combine multiple inputs into one cycle).
 *
 * <p>Merges multiple related inputs into a single combined input
 * for more efficient processing.
 */
public final class CycleCombiner {

    public record CombinedInput(List<String> originals, String combined) {}

    public static CombinedInput combine(List<String> inputs, String separator) {
        if (inputs.isEmpty()) return new CombinedInput(inputs, "");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inputs.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(inputs.get(i));
        }
        return new CombinedInput(inputs, sb.toString());
    }

    public static CombinedInput combine(List<String> inputs) {
        return combine(inputs, " ");
    }
}
