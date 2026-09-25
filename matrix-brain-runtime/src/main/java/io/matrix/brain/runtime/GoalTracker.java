package io.matrix.brain.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * MIND-W4 — Goal tracker.
 *
 * <p>Stores named goals with progress, status, and audit trail. Backed by
 * in-memory state (W4) — a future W7 wave can persist this to SQLite.</p>
 *
 * <p>CONSTITUTION Article IV — autonomous actions (e.g. inbox ingest)
 * are recorded here BEFORE they fire, so the operator can audit them.</p>
 */
public final class GoalTracker {

    public enum Status { PENDING, IN_PROGRESS, COMPLETED, ABANDONED }

    public record Goal(
        String id,
        String name,
        String description,
        Status status,
        double progress,        // 0.0 .. 1.0
        long createdAtMillis,
        long updatedAtMillis
    ) {
        public Goal withStatus(Status newStatus) {
            return new Goal(id, name, description, newStatus, progress,
                createdAtMillis, System.currentTimeMillis());
        }
        public Goal withProgress(double newProgress) {
            double clamped = Math.max(0.0, Math.min(1.0, newProgress));
            Status newStatus = (clamped >= 1.0) ? Status.COMPLETED : status;
            return new Goal(id, name, description, newStatus, clamped,
                createdAtMillis, System.currentTimeMillis());
        }
    }

    private final Map<String, Goal> goals = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** Create a new goal. Returns the Goal record (with id assigned). */
    public Goal addGoal(String name, String description) {
        lock.writeLock().lock();
        try {
            long now = System.currentTimeMillis();
            Goal g = new Goal(UUID.randomUUID().toString(), name, description,
                Status.PENDING, 0.0, now, now);
            goals.put(g.id(), g);
            return g;
        } finally {
            lock.writeLock().unlock(); }
    }

    /** Update progress for an existing goal. Returns the new state or null if not found. */
    public Goal updateProgress(String id, double progress) {
        lock.writeLock().lock();
        try {
            Goal cur = goals.get(id);
            if (cur == null) return null;
            Goal next = cur.withProgress(progress);
            goals.put(id, next);
            return next;
        } finally {
            lock.writeLock().unlock(); }
    }

    public Goal markCompleted(String id) {
        lock.writeLock().lock();
        try {
            Goal cur = goals.get(id);
            if (cur == null) return null;
            Goal next = cur.withStatus(Status.COMPLETED).withProgress(1.0);
            goals.put(id, next);
            return next;
        } finally {
            lock.writeLock().unlock(); }
    }

    public Goal markAbandoned(String id) {
        lock.writeLock().lock();
        try {
            Goal cur = goals.get(id);
            if (cur == null) return null;
            Goal next = cur.withStatus(Status.ABANDONED);
            goals.put(id, next);
            return next;
        } finally {
            lock.writeLock().unlock(); }
    }

    /** All goals, insertion-ordered. */
    public List<Goal> listGoals() {
        lock.readLock().lock();
        try { return new ArrayList<>(goals.values()); }
        finally { lock.readLock().unlock(); }
    }

    public Goal get(String id) {
        lock.readLock().lock();
        try { return goals.get(id); }
        finally { lock.readLock().unlock(); }
    }

    public int size() {
        lock.readLock().lock();
        try { return goals.size(); }
        finally { lock.readLock().unlock(); }
    }

    /** Convenience: snapshot of goals for /v1/status rendering. */
    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Goal g : listGoals()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", g.id());
            e.put("name", g.name());
            e.put("status", g.status().name());
            e.put("progress", g.progress());
            list.add(e);
        }
        out.put("count", list.size());
        out.put("goals", list);
        return out;
    }
}
