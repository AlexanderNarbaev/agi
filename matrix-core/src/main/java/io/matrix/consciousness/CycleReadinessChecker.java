package io.matrix.consciousness;

/**
 * RUN 315 — CycleReadinessChecker (readiness check).
 *
 * <p>Checks if the cognitive loop is ready to process cycles.
 */
public final class CycleReadinessChecker {

    public static boolean isReady(BrainLoopService svc) {
        return svc != null && svc.arousal() < 0.95;
    }

    public static String reason(BrainLoopService svc) {
        if (svc == null) return "service null";
        if (svc.arousal() >= 0.95) return "arousal too high";
        return "ready";
    }
}
