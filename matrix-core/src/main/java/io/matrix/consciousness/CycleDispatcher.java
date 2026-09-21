package io.matrix.consciousness;

import java.util.function.Consumer;

/**
 * RUN 309 — CycleDispatcher (event dispatching).
 *
 * <p>Dispatches cycle events to registered handlers.
 */
public final class CycleDispatcher {

    public enum Event { CYCLE_START, CYCLE_END, CYCLE_ERROR, GATE_TRIGGERED }

    private Consumer<Event> handler;

    public void register(Consumer<Event> handler) {
        this.handler = handler;
    }

    public void dispatch(Event event) {
        if (handler != null) handler.accept(event);
    }
}
