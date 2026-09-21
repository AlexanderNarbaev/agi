package io.matrix.consciousness;

/**
 * RUN 266 — CycleFeedback (per-cycle feedback scores).
 *
 * <p>Stores a simple numeric feedback score for each completed
 * cycle. Higher scores = more positive outcomes. Used for
 * reinforcement learning patterns.
 */
public final class CycleFeedback {

    public record FeedbackRecord(int cycle, double score, String note) {}

    private final java.util.List<FeedbackRecord> records = new java.util.ArrayList<>();
    private final double smoothingAlpha;

    public CycleFeedback() { this(0.1); }
    public CycleFeedback(double alpha) { this.smoothingAlpha = alpha; }

    public synchronized void record(int cycle, double score, String note) {
        records.add(new FeedbackRecord(cycle, score, note));
    }

    public synchronized double movingAverage() {
        if (records.isEmpty()) return 0;
        double ma = records.get(0).score();
        for (int i = 1; i < records.size(); i++) {
            ma = smoothingAlpha * records.get(i).score()
                    + (1 - smoothingAlpha) * ma;
        }
        return ma;
    }

    public synchronized double lastScore() {
        return records.isEmpty() ? 0 : records.get(records.size() - 1).score();
    }

    public synchronized int size() { return records.size(); }

    public synchronized void clear() { records.clear(); }
}
