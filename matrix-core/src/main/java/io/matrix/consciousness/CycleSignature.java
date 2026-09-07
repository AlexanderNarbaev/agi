package io.matrix.consciousness;

import io.matrix.auditor.MatrixTrace;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * RUN 251 — CycleSignature (stable signature for a cycle).
 *
 * <p>Computes a stable signature for one cycle: takes the first/last
 * hash from the trace and combines them. Used for indexing, caching,
 * deduplication.
 */
public final class CycleSignature {

    public record Signature(String cycleId, String input,
                            String headHash, String tailHash,
                            String combinedHex) {}

    public static Signature compute(int cycleNumber, String input,
                                    MatrixTrace trace) {
        var steps = trace.steps();
        String head = steps.isEmpty() ? "" : steps.get(0).hash;
        String tail = steps.isEmpty() ? "" : steps.get(steps.size() - 1).hash;
        // Combine head + tail into a short hex (first 16 chars each)
        String combined = (head + tail);
        String combinedHex = combined.length() > 16
                ? combined.substring(0, 16) : combined;
        return new Signature("c" + cycleNumber, input, head, tail, combinedHex);
    }

    public static String shortHash(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.length() > 8 ? s.substring(0, 8) : s;
    }
}
