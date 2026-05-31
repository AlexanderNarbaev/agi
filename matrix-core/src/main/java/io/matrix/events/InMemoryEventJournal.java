package io.matrix.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory event journal.
 *
 * <p>For Phase 1, events are stored in memory. Later phases will replace
 * this with a Kafka-backed implementation.
 *
 * <p>Ref: L6_Memory.md §3.2
 */
public final class InMemoryEventJournal implements EventJournal {

    private final List<ClusterEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public long append(ClusterEvent event) {
        events.add(event);
        return events.size() - 1;
    }

    @Override
    public List<ClusterEvent> replayFrom(long fromIndex) {
        if (fromIndex < 0 || fromIndex >= events.size()) {
            return List.of();
        }
        var result = new ArrayList<ClusterEvent>();
        for (long i = fromIndex; i < events.size(); i++) {
            result.add(events.get((int) i));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<ClusterEvent> replayAll() {
        return replayFrom(0);
    }

    @Override
    public long size() {
        return events.size();
    }
}
