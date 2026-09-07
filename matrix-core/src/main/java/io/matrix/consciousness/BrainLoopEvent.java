package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 207 — BrainLoopEvent (typed event for cycle results).
 *
 * <p>Type-safe event representation for BrainLoopService.cycle()
 * output. Useful for streaming, logging, or actor patterns.
 */
public final class BrainLoopEvent {

    public enum Type { CYCLE_ACCEPTED, CYCLE_DENIED, GATE_TRIGGERED,
                       TRACE_RECEIVED, MEMORY_PROMOTED }

    public record Event(Type type, String input, String output,
                        String reason, long sequenceNumber) {}

    private final List<Event> events = new ArrayList<>();
    private long sequence = 0;

    public synchronized Event emitCycleAccepted(String input, String output) {
        return record(BrainLoopEvent.Type.CYCLE_ACCEPTED, input, output, "ok");
    }

    public synchronized Event emitCycleDenied(String input, String reason) {
        return record(BrainLoopEvent.Type.CYCLE_DENIED, input, "denied", reason);
    }

    public synchronized Event emitGateTriggered(String reason) {
        return record(BrainLoopEvent.Type.GATE_TRIGGERED, "", "", reason);
    }

    public synchronized Event emitTraceReceived(int count) {
        return record(BrainLoopEvent.Type.TRACE_RECEIVED, "", "count=" + count, "trace");
    }

    private synchronized Event record(Type type, String input, String output,
                                       String reason) {
        Event e = new Event(type, input, output, reason, ++sequence);
        events.add(e);
        return e;
    }

    public synchronized List<Event> events() {
        return new ArrayList<>(events);
    }

    public synchronized int acceptedCount() {
        return (int) events.stream()
                .filter(e -> e.type() == Type.CYCLE_ACCEPTED).count();
    }

    public synchronized int deniedCount() {
        return (int) events.stream()
                .filter(e -> e.type() == Type.CYCLE_DENIED).count();
    }

    public synchronized void clear() {
        events.clear();
        sequence = 0;
    }
}
