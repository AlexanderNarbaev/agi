package io.matrix.goals;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 215 — GoalTracker (active goals).
 *
 * <p>Manages a list of active and completed goals. Each goal has
 * a unique ID and a priority. Pure Java, deterministic.
 *
 * <p>Used to drive top-down impulses in the cognitive loop.
 */
public final class GoalTracker {

    public enum Status { ACTIVE, COMPLETED, ABANDONED }

    public record Goal(int id, String description,
                       Status status, int priority) {}

    private final List<Goal> goals = new ArrayList<>();
    private int nextId = 1;

    public synchronized Goal add(String description, int priority) {
        Goal g = new Goal(nextId++, description,
                Status.ACTIVE, priority);
        goals.add(g);
        return g;
    }

    public synchronized void complete(int id) {
        for (int i = 0; i < goals.size(); i++) {
            if (goals.get(i).id() == id) {
                goals.set(i, new Goal(id, goals.get(i).description(),
                        Status.COMPLETED, goals.get(i).priority()));
                return;
            }
        }
    }

    public synchronized void abandon(int id) {
        for (int i = 0; i < goals.size(); i++) {
            if (goals.get(i).id() == id) {
                goals.set(i, new Goal(id, goals.get(i).description(),
                        Status.ABANDONED, goals.get(i).priority()));
                return;
            }
        }
    }

    public synchronized List<Goal> activeGoals() {
        List<Goal> out = new ArrayList<>();
        for (Goal g : goals) {
            if (g.status() == Status.ACTIVE) out.add(g);
        }
        out.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        return out;
    }

    public synchronized int activeCount() {
        int n = 0;
        for (Goal g : goals) if (g.status() == Status.ACTIVE) n++;
        return n;
    }

    public synchronized int completedCount() {
        int n = 0;
        for (Goal g : goals) if (g.status() == Status.COMPLETED) n++;
        return n;
    }

    public synchronized int size() { return goals.size(); }
}
