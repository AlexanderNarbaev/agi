package io.matrix.reasoning;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RUN 40 — Per-stage latency tracker (H-047).
 *
 * <p>Records wall-clock latency for each canonical loop stage
 * (perception, attention, deliberation, gate, action).
 * Tracks count, sum (ms), min (ms), max (ms) per stage.
 *
 * <p>H-047 acceptance: per-stage p99 latency in budget for ≥9/10
 * runs under realistic load. Budgets:
 * <ul>
 *   <li>perception:    <5ms (p99)</li>
 *   <li>attention:     <5ms (p99)</li>
 *   <li>deliberation:  <50ms (p99)</li>
 *   <li>gate:          <5ms (p99)</li>
 *   <li>action:        <10ms (p99)</li>
 * </ul>
 *
 * <p>Honest caveat: this tracker measures WIDTH of each stage but
 * doesn't enforce budgets — it reports the data for H-047 EXP to
 * evaluate. Enforcement is a separate concern.
 */
public class StageLatencyTracker {

    /** Canonical stages in the consciousness loop. */
    public enum Stage {
        PERCEPTION, ATTENTION, DELIBERATION, GATE, ACTION
    }

    /** Per-stage stats. */
    private static final class StageStats {
        final AtomicLong count = new AtomicLong();
        final AtomicLong sumNs = new AtomicLong();
        volatile long maxNs = 0;
        volatile long minNs = Long.MAX_VALUE;
    }

    private final EnumMap<Stage, StageStats> stats = new EnumMap<>(Stage.class);
    private final EnumMap<Stage, Long> budgetsNs = new EnumMap<>(Stage.class);

    public StageLatencyTracker() {
        for (Stage s : Stage.values()) stats.put(s, new StageStats());
        budgetsNs.put(Stage.PERCEPTION, 5_000_000L);    // 5ms
        budgetsNs.put(Stage.ATTENTION, 5_000_000L);     // 5ms
        budgetsNs.put(Stage.DELIBERATION, 50_000_000L); // 50ms
        budgetsNs.put(Stage.GATE, 5_000_000L);          // 5ms
        budgetsNs.put(Stage.ACTION, 10_000_000L);       // 10ms
    }

    /** Record one measurement for a stage. */
    public void record(Stage stage, long elapsedNs) {
        if (stage == null || elapsedNs < 0) return;
        StageStats s = stats.get(stage);
        s.count.incrementAndGet();
        s.sumNs.addAndGet(elapsedNs);
        if (elapsedNs > s.maxNs) s.maxNs = elapsedNs;
        if (elapsedNs < s.minNs) s.minNs = elapsedNs;
    }

    /** Convenience: record using System.nanoTime() before/after pair. */
    public long startTimer() {
        return System.nanoTime();
    }

    public void recordElapsed(Stage stage, long startNs) {
        record(stage, System.nanoTime() - startNs);
    }

    /** Get count for a stage. */
    public long count(Stage stage) {
        StageStats s = stats.get(stage);
        return s == null ? 0 : s.count.get();
    }

    /** Get sum (in nanoseconds) for a stage. */
    public long sumNs(Stage stage) {
        StageStats s = stats.get(stage);
        return s == null ? 0 : s.sumNs.get();
    }

    /** Get max (in nanoseconds) for a stage. */
    public long maxNs(Stage stage) {
        StageStats s = stats.get(stage);
        return s == null ? 0 : s.maxNs;
    }

    /** Get min (in nanoseconds) for a stage. */
    public long minNs(Stage stage) {
        StageStats s = stats.get(stage);
        return s == null ? Long.MAX_VALUE : s.minNs;
    }

    /** Get mean (in nanoseconds) for a stage. */
    public double meanNs(Stage stage) {
        long c = count(stage);
        if (c == 0) return 0.0;
        return (double) sumNs(stage) / c;
    }

    /** Get budget (in nanoseconds) for a stage. */
    public long budgetNs(Stage stage) {
        return budgetsNs.getOrDefault(stage, Long.MAX_VALUE);
    }

    /** Check whether max for a stage is within budget. */
    public boolean withinBudget(Stage stage) {
        return maxNs(stage) <= budgetNs(stage);
    }

    /** Get a snapshot of all stage stats. */
    public Map<String, Map<String, Object>> snapshot() {
        Map<String, Map<String, Object>> out = new java.util.LinkedHashMap<>();
        for (Stage s : Stage.values()) {
            Map<String, Object> sub = new java.util.LinkedHashMap<>();
            sub.put("count", count(s));
            sub.put("meanMs", meanNs(s) / 1_000_000.0);
            sub.put("maxMs", maxNs(s) / 1_000_000.0);
            sub.put("minMs", count(s) == 0 ? 0 : minNs(s) / 1_000_000.0);
            sub.put("budgetMs", budgetNs(s) / 1_000_000.0);
            sub.put("withinBudget", withinBudget(s));
            out.put(s.name(), sub);
        }
        return out;
    }

    /** Reset all stats. */
    public void reset() {
        for (Stage s : Stage.values()) {
            StageStats st = stats.get(s);
            st.count.set(0);
            st.sumNs.set(0);
            st.maxNs = 0;
            st.minNs = Long.MAX_VALUE;
        }
    }
}
