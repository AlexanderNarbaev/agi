package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 278 — CycleSplitter (split input into sub-cycles).
 *
 * <p>Splits a long input into manageable chunks for processing.
 * Each chunk is a separate cycle.
 */
public final class CycleSplitter {

    public record Split(List<String> chunks, int originalLength) {}

    public static Split split(String input, int maxChunkSize) {
        if (input == null || input.isEmpty()) return new Split(List.of(), 0);
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < input.length(); i += maxChunkSize) {
            int end = Math.min(i + maxChunkSize, input.length());
            chunks.add(input.substring(i, end));
        }
        return new Split(chunks, input.length());
    }

    public static Split split(String input) {
        return split(input, 256);
    }
}
