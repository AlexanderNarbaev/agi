package io.matrix.consciousness;

/**
 * RUN 290 — CycleOutput (output formatting).
 *
 * <p>Formats cycle results for different output targets.
 */
public final class CycleOutput {

    /** Format as plain text. */
    public static String toText(BrainLoopService.CycleResult r) {
        return String.format("[%s] %s", r.accepted() ? "OK" : "DENY", r.action());
    }

    /** Format as JSON-like. */
    public static String toJson(BrainLoopService.CycleResult r) {
        return String.format(
                "{\"accepted\":%b,\"action\":\"%s\",\"arousal\":%.2f,\"focus\":%d}",
                r.accepted(), escape(r.action()), r.arousal(), r.focusCount());
    }

    /** Format as CSV line. */
    public static String toCsv(BrainLoopService.CycleResult r) {
        return String.format("%b,%s,%.2f,%d,%.3f",
                r.accepted(), escape(r.action()), r.arousal(),
                r.focusCount(), r.predictionError());
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
