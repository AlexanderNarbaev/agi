package io.matrix.consciousness;

import java.util.List;

/**
 * RUN 279 — CycleAssembler (assemble from chunks).
 *
 * <p>Reassembles split chunks back into a single string.
 */
public final class CycleAssembler {

    public static String assemble(List<String> chunks) {
        if (chunks == null || chunks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String chunk : chunks) sb.append(chunk);
        return sb.toString();
    }

    public static String assemble(List<String> chunks, String separator) {
        if (chunks == null || chunks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(chunks.get(i));
        }
        return sb.toString();
    }
}
