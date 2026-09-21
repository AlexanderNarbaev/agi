package io.matrix.consciousness;

/**
 * RUN 313 — CycleHealthChecker (health check).
 *
 * <p>Checks if the cognitive loop is healthy.
 */
public final class CycleHealthChecker {

    public enum Status { HEALTHY, DEGRADED, UNHEALTHY }

    public static Status check(BrainLoopService svc) {
        double arousal = svc.arousal();
        if (arousal <= 0.3) return Status.HEALTHY;  // baseline is 0.3
        if (arousal < 0.7) return Status.DEGRADED;
        return Status.UNHEALTHY;
    }

    public static String describe(Status status) {
        return switch (status) {
            case HEALTHY -> "arousal low, system nominal";
            case DEGRADED -> "arousal moderate, watch closely";
            case UNHEALTHY -> "arousal high, intervention needed";
        };
    }
}
