package io.matrix.brain.runtime;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * RECON-W11 research iteration #2 — HippocampalReplayScheduler.
 *
 * <p>Prioritized replay of episodic entries during sleep consolidation.
 * Mimics hippocampal sharp-wave ripples: recent + high-confidence first,
 * older + low-confidence last (or evicted). Deterministic with seeded RNG.</p>
 */
public final class HippocampalReplayScheduler {

    public record ReplayItem(long id, double confidence, int priorityScore) {}

    private final Deque<ReplayItem> queue = new ArrayDeque<>();
    private long evictedCount = 0;

    /** Add an entry; auto-prioritize by confidence * recency_factor. */
    public void add(long id, double confidence, long recencyTicks) {
        int score = (int) (confidence * 1000 + recencyTicks);
        queue.add(new ReplayItem(id, confidence, score));
    }

    /** Pop the highest-priority item (no eviction). */
    public ReplayItem nextReplay() {
        if (queue.isEmpty()) return null;
        ReplayItem best = queue.peek();
        for (ReplayItem it : queue) {
            if (it.priorityScore() > best.priorityScore()) best = it;
        }
        queue.remove(best);
        return best;
    }

    /** Pop an item; evict if queue > capacity. */
    public ReplayItem nextReplay(int capacity) {
        while (queue.size() > capacity) {
            queue.pollLast();
            evictedCount++;
        }
        return nextReplay();
    }

    public int queueSize() { return queue.size(); }
    public long evictedCount() { return evictedCount; }
}
