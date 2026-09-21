package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * W117 — Kolmogorov complexity recorder (wraps ConsciousBrain).
 *
 * <p>Provides a non-invasive way to record Kolmogorov complexity snapshots
 * alongside ConsciousBrain cycles. The recorder owns its own ring buffer
 * (independent of ConsciousBrain's internal state) and exposes methods to
 * capture and retrieve snapshots.
 *
 * <p>This decouples W116 snapshot from the ConsciousBrain constructor
 * signature, allowing opt-in usage without modifying the core class.
 *
 * <p>CONSTITUTION VI compliance: passive measurement of complexity,
 * not a phenomenal consciousness claim.
 */
public final class KolmogorovComplexityRecorder {

    private final KolmogorovComplexitySnapshot[] buffer;
    private int nextIdx = 0;
    private int count = 0;
    private final int capacity;

    public KolmogorovComplexityRecorder(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be ≥ 1");
        this.capacity = capacity;
        this.buffer = new KolmogorovComplexitySnapshot[capacity];
    }

    /** Default capacity matching ConsciousBrain's INTER_AGENT_SNAPSHOT_CAPACITY. */
    public KolmogorovComplexityRecorder() {
        this(32);
    }

    /**
     * Capture a snapshot from the given trajectory at the current cycle.
     */
    public void capture(int cycleNumber, long[] trajectory) {
        if (trajectory == null || trajectory.length == 0) {
            buffer[nextIdx % capacity] = new KolmogorovComplexitySnapshot(
                cycleNumber, nextIdx % capacity, new long[0], 0.0, 0L);
        } else {
            double k = KolmogorovComplexity.estimate(trajectory);
            long hash = computeHash(trajectory);
            buffer[nextIdx % capacity] = new KolmogorovComplexitySnapshot(
                cycleNumber, nextIdx % capacity, trajectory.clone(), k, hash);
        }
        nextIdx++;
        if (count < capacity) count++;
    }

    /**
     * Compute a deterministic hash for the trajectory (simple sum + count).
     */
    private static long computeHash(long[] trajectory) {
        long h = -3750763034362895579L; // FNV offset basis // FNV offset basis
        for (long v : trajectory) {
            h = (h ^ v) * 1099511628211L;
        }
        return h;
    }

    /** Total snapshots captured so far (capped at capacity). */
    public int snapshotCount() {
        return count;
    }

    /** Current ring buffer capacity. */
    public int capacity() {
        return capacity;
    }

    /**
     * Get the i-th snapshot in chronological order (0 = oldest, count-1 = newest).
     */
    public KolmogorovComplexitySnapshot get(int i) {
        if (i < 0 || i >= count) {
            throw new IndexOutOfBoundsException("index " + i + " out of [0, " + count + ")");
        }
        int start = (count < capacity) ? 0 : nextIdx % capacity;
        return buffer[(start + i) % capacity];
    }

    /**
     * Get all snapshots in chronological order.
     */
    public List<KolmogorovComplexitySnapshot> allSnapshots() {
        List<KolmogorovComplexitySnapshot> out = new ArrayList<>(count);
        int start = (count < capacity) ? 0 : nextIdx % capacity;
        for (int i = 0; i < count; i++) {
            out.add(buffer[(start + i) % capacity]);
        }
        return out;
    }

    /**
     * Get the most recent snapshot, or null if no snapshots captured.
     */
    public KolmogorovComplexitySnapshot latest() {
        if (count == 0) return null;
        int idx = (nextIdx - 1) % capacity;
        if (idx < 0) idx += capacity;
        return buffer[idx];
    }

    /**
     * Compute the mean K across all snapshots. Returns 0.0 if no snapshots.
     */
    public double meanK() {
        if (count == 0) return 0.0;
        double sum = 0;
        for (int i = 0; i < count; i++) sum += get(i).kolmogorovK();
        return sum / count;
    }

    /**
     * Wire to a ConsciousBrain: returns a Consumer that captures the
     * current discrete trajectory (from recent bit states) at each cycle.
     * The wiring must be applied externally — this is a passive recorder.
     */

    /** Clear all snapshots. */
    public void clear() {
        nextIdx = 0;
        count = 0;
        for (int i = 0; i < capacity; i++) buffer[i] = null;
    }
}
