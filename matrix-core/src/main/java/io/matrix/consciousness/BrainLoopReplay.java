package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 260 — BrainLoopReplay (cycle replay log).
 *
 * <p>Records inputs and their outcomes for replay. Used for
 * debugging and regression tests.
 */
public final class BrainLoopReplay {

    public record ReplayEntry(int sequence, String input,
                              boolean accepted, String action) {}

    private final List<ReplayEntry> entries = new ArrayList<>();
    private int nextSeq = 0;

    public synchronized ReplayEntry record(String input, boolean accepted,
                                          String action) {
        ReplayEntry e = new ReplayEntry(++nextSeq, input, accepted, action);
        entries.add(e);
        return e;
    }

    public synchronized List<ReplayEntry> all() {
        return new ArrayList<>(entries);
    }

    public synchronized int size() { return entries.size(); }

    public synchronized void clear() { entries.clear(); nextSeq = 0; }

    /** Replay all entries against a target brain. */
    public synchronized int replay(BrainLoopService svc) {
        int accepted = 0;
        for (var e : entries) {
            var r = svc.cycle(e.input());
            if (r.accepted()) accepted++;
        }
        return accepted;
    }
}
