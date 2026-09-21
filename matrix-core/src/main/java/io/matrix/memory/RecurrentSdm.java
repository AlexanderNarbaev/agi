package io.matrix.memory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase S (RUN 413) — Recurrent Kanerva SDM.
 *
 * <p>SDM (Sparse Distributed Memory) with 1-step context (recurrent).
 * Each address is a high-dim binary vector. Write stores pattern at
 * the address and at Hamming neighbors. Read returns sum of patterns
 * from all locations close to the cue.
 *
 * <p>Pure function (CONSTITUTION I).
 */
public final class RecurrentSdm {

    public record Address(long bits) {}
    public record Pattern(double[] vector) {}

    public record Sdm(
            Map<Long, double[]> memory,
            int dimension,
            int contextWindow
    ) {}

    private RecurrentSdm() {}

    /** Create empty SDM. */
    public static Sdm empty(int dimension, int contextWindow) {
        return new Sdm(new HashMap<>(), dimension, Math.max(1, contextWindow));
    }

    /** Hamming distance between two addresses. */
    public static int hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    /** Write pattern at address (and within access radius). */
    public static Sdm write(Sdm sdm, Address address, Pattern pattern) {
        Map<Long, double[]> memory = new HashMap<>(sdm.memory());
        int accessRadius = sdm.dimension() / 8;  // typical SDM radius
        // Write to all locations within access radius
        for (Map.Entry<Long, double[]> entry : memory.entrySet()) {
            if (hammingDistance(address.bits(), entry.getKey()) <= accessRadius) {
                double[] blended = blend(entry.getValue(), pattern.vector());
                memory.put(entry.getKey(), blended);
            }
        }
        // Also store at the address itself
        memory.merge(address.bits(), pattern.vector(), (old, neu) -> blend(old, neu));
        return new Sdm(memory, sdm.dimension(), sdm.contextWindow());
    }

    /** Read pattern at address — sum of nearby patterns. */
    public static Pattern read(Sdm sdm, Address address) {
        int accessRadius = sdm.dimension() / 8;
        double[] sum = new double[0];  // assumes all vectors same length
        int count = 0;
        for (Map.Entry<Long, double[]> entry : sdm.memory().entrySet()) {
            if (hammingDistance(address.bits(), entry.getKey()) <= accessRadius) {
                double[] v = entry.getValue();
                if (sum.length == 0) sum = new double[v.length];
                for (int i = 0; i < v.length; i++) sum[i] += v[i];
                count++;
            }
        }
        if (count == 0) return new Pattern(new double[0]);
        for (int i = 0; i < sum.length; i++) sum[i] /= count;
        return new Pattern(sum);
    }

    /** Recurrent read: combines current cue with previous read result. */
    public static Pattern recurrentRead(Sdm sdm, Address address,
                                        Pattern previous) {
        Pattern current = read(sdm, address);
        if (previous == null || previous.vector().length == 0) {
            return current;
        }
        // Mix current with previous (carry over)
        double[] mixed = new double[current.vector().length];
        for (int i = 0; i < current.vector().length; i++) {
            double prev = i < previous.vector().length ? previous.vector()[i] : 0;
            mixed[i] = 0.7 * current.vector()[i] + 0.3 * prev;
        }
        return new Pattern(mixed);
    }

    private static double[] blend(double[] a, double[] b) {
        int n = Math.max(a.length, b.length);
        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            double av = i < a.length ? a[i] : 0;
            double bv = i < b.length ? b[i] : 0;
            result[i] = av + bv;  // accumulator
        }
        return result;
    }
}
