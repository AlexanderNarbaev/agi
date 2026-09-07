package io.matrix.consciousness;

/**
 * RUN 153 — Impulse (top-down motivation).
 *
 * <p>Top-down impulses drive attention from inside the cognitive
 * loop. Per PARADIGM §4.4, the Mediator coordinates multiple
 * impulses via weighted consensus. Each impulse has:
 * <ul>
 *   <li>priority (deterministic 0..1)</li>
 *   <li>weight (configurable, persisted in Mediator state)</li>
 *   <li>source (curiosity, integrity, goal-driven, novelty)</li>
 * </ul>
 *
 * <p>All fields are immutable; merging happens via
 * {@link AttentionRouter#merge} which is deterministic.
 */
public final class Impulse {

    public enum Source {
        CURIOSITY, INTEGRITY, GOAL, NOVELTY, REFLEX
    }

    public final Source source;
    public final double priority;
    public final double weight;
    public final String target;

    public Impulse(Source source, double priority, double weight, String target) {
        this.source = source;
        this.priority = Math.max(0.0, Math.min(1.0, priority));
        this.weight = Math.max(0.0, Math.min(1.0, weight));
        this.target = target == null ? "" : target;
    }

    /** Effective score: priority * weight (deterministic). */
    public double effectiveScore() {
        return priority * weight;
    }

    @Override
    public String toString() {
        return "Impulse{" + source + " priority=" + priority
                + " weight=" + weight + " target='" + target + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Impulse i)) return false;
        return Double.compare(priority, i.priority) == 0
                && Double.compare(weight, i.weight) == 0
                && source == i.source
                && target.equals(i.target);
    }

    @Override
    public int hashCode() {
        int h = source.hashCode();
        h = 31 * h + Double.hashCode(priority);
        h = 31 * h + Double.hashCode(weight);
        h = 31 * h + target.hashCode();
        return h;
    }
}
