package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * RUN 236 — CyclePrioritizer (priority queue for cycles).
 *
 * <p>Schedules brain cycles by priority. High-priority cycles
 * (e.g., emergencies) are dispatched first.
 *
 * <p>Deterministic given insertion order and priorities.
 */
public final class CyclePrioritizer {

    public record PendingCycle(int id, String input, int priority) {}

    private final PriorityQueue<PendingCycle> queue = new PriorityQueue<>(
            Comparator.comparingInt(PendingCycle::priority).reversed()
                    .thenComparingInt(PendingCycle::id));

    private int nextId = 0;

    public synchronized void enqueue(String input, int priority) {
        queue.add(new PendingCycle(++nextId, input, priority));
    }

    public synchronized PendingCycle dequeue() {
        return queue.poll();
    }

    public synchronized int size() { return queue.size(); }

    public synchronized List<PendingCycle> snapshot() {
        var list = new ArrayList<>(queue);
        list.sort(Comparator.comparingInt(PendingCycle::priority).reversed()
                .thenComparingInt(PendingCycle::id));
        return list;
    }
}
