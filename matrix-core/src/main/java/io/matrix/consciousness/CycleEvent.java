package io.matrix.consciousness;

/**
 * RUN 310 — CycleEvent (typed cycle event).
 *
 * <p>Rich event object for cycle lifecycle.
 */
public record CycleEvent(
        int cycleId,
        EventType type,
        String input,
        String output,
        boolean accepted,
        double arousal,
        long timestampMillis) {

    public enum EventType { START, END, ERROR, TIMEOUT }

    public static CycleEvent start(int cycleId, String input) {
        return new CycleEvent(cycleId, EventType.START, input, null,
                false, 0, System.currentTimeMillis());
    }

    public static CycleEvent end(int cycleId, String input,
                                  String output, boolean accepted,
                                  double arousal) {
        return new CycleEvent(cycleId, EventType.END, input, output,
                accepted, arousal, System.currentTimeMillis());
    }

    public static CycleEvent error(int cycleId, String input, String error) {
        return new CycleEvent(cycleId, EventType.ERROR, input, error,
                false, 0, System.currentTimeMillis());
    }
}
