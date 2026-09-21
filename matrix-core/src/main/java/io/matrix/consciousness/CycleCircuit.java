package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 306 — CycleCircuit (round-robin scheduling).
 *
 * <p>Cycles through handlers in round-robin order.
 */
public final class CycleCircuit {

    private final List<String> handlers;
    private int index = 0;

    public CycleCircuit(List<String> handlers) {
        this.handlers = new ArrayList<>(handlers);
    }

    /** Get next handler in round-robin. */
    public synchronized String next() {
        if (handlers.isEmpty()) return null;
        String handler = handlers.get(index);
        index = (index + 1) % handlers.size();
        return handler;
    }

    public synchronized int size() { return handlers.size(); }
    public synchronized int currentIndex() { return index; }
}
