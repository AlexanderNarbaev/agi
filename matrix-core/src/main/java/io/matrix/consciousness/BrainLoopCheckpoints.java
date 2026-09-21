package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 242 — BrainLoopCheckpoints (named checkpoints).
 *
 * <p>Tagged snapshots of brain state for rollback. Useful for
 * testing long-running cycles or research runs.
 */
public final class BrainLoopCheckpoints {

    public record Checkpoint(String tag, int traceCount,
                             double arousal, String tailHash) {}

    private final List<Checkpoint> checkpoints = new ArrayList<>();

    public synchronized Checkpoint create(BrainLoopService svc, String tag) {
        Checkpoint cp = new Checkpoint(tag, svc.trace().count(),
                svc.arousal(),
                svc.trace().count() == 0 ? "" : svc.trace().last().hash);
        checkpoints.add(cp);
        return cp;
    }

    public synchronized List<Checkpoint> list() {
        return new ArrayList<>(checkpoints);
    }

    public synchronized int size() { return checkpoints.size(); }

    public synchronized Checkpoint findByTag(String tag) {
        for (Checkpoint cp : checkpoints) {
            if (cp.tag().equals(tag)) return cp;
        }
        return null;
    }

    public synchronized void clear() { checkpoints.clear(); }
}
