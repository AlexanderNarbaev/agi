package io.matrix.consciousness;

import io.matrix.auditor.MatrixTrace;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 259 — BrainLoopSnapshotsStore (in-memory snapshot store).
 *
 * <p>Stores brain state snapshots with named tags. Useful for
 * long-running research or production rollback.
 */
public final class BrainLoopSnapshotsStore {

    public record StoredSnapshot(String tag, int traceCount,
                                 double arousal, String tailHash) {}

    private final List<StoredSnapshot> store = new ArrayList<>();
    private final int maxSnapshots;

    public BrainLoopSnapshotsStore() { this(100); }
    public BrainLoopSnapshotsStore(int max) {
        this.maxSnapshots = max;
    }

    public synchronized StoredSnapshot store(BrainLoopService svc, String tag) {
        if (store.size() >= maxSnapshots) {
            store.remove(0);  // FIFO eviction
        }
        var steps = svc.trace().steps();
        String tail = steps.isEmpty() ? "" : steps.get(steps.size() - 1).hash;
        StoredSnapshot s = new StoredSnapshot(tag, svc.trace().count(),
                svc.arousal(), tail);
        store.add(s);
        return s;
    }

    public synchronized List<StoredSnapshot> list() {
        return new ArrayList<>(store);
    }

    public synchronized StoredSnapshot findByTag(String tag) {
        for (StoredSnapshot s : store) {
            if (s.tag().equals(tag)) return s;
        }
        return null;
    }

    public synchronized int size() { return store.size(); }

    public synchronized void clear() { store.clear(); }
}
