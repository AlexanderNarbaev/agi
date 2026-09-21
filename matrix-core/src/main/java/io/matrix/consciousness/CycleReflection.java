package io.matrix.consciousness;

/**
 * RUN 267 — CycleReflection (self-reflection on cycle outcomes).
 *
 * <p>After N cycles, computes reflection metrics: how often was
 * the gate denied? What was the average arousal? Did the feedback
 * trend up or down?
 */
public final class CycleReflection {

    public record Reflection(int totalCycles,
                              int deniedCycles,
                              double avgArousal,
                              double feedbackTrend,
                              String summary) {}

    public static Reflection reflect(BrainLoopService svc,
                                     CycleFeedback feedback,
                                     int deniedCycles) {
        int total = svc.trace().count() / 5;
        double feedbackTrend = feedback.lastScore();
        String summary;
        if (total == 0) {
            summary = "no cycles";
        } else if (deniedCycles == 0) {
            summary = "all accepted";
        } else if (deniedCycles >= total) {
            summary = "all denied";
        } else {
            summary = String.format("%d/%d accepted", total - deniedCycles, total);
        }
        return new Reflection(total, deniedCycles, svc.arousal(),
                feedbackTrend, summary);
    }

    public static String format(Reflection r) {
        return String.format(
                "Reflection{ cycles=%d, denied=%d, arousal=%.2f, trend=%.2f, %s }",
                r.totalCycles(), r.deniedCycles(), r.avgArousal(),
                r.feedbackTrend(), r.summary());
    }
}
