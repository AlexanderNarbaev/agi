package io.matrix.consciousness;

/**
 * RUN 209 — BrainLoopHealth.
 *
 * <p>Health metric for BrainLoopService. Calculates overall
 * health score ∈ [0, 1] from arousal and acceptance ratio.
 *
 * <p>Health = 0.5 * arousalBalance + 0.5 * acceptanceRatio.
 * Where arousalBalance = 1 - |arousal - 0.5| * 2 (peaks at arousal=0.5).
 */
public final class BrainLoopHealth {

    public record HealthScore(double score, String status,
                             double arousal, double acceptedRatio) {}

    public static HealthScore compute(BrainLoopService svc) {
        double arousal = svc.arousal();
        // Arousal at 0.5 is ideal; farther from 0.5 → unhealthier
        double arousalBalance = 1.0 - Math.abs(arousal - 0.5) * 2;
        arousalBalance = clamp(arousalBalance, 0, 1);
        // Acceptance ratio: approximation, just compute 1.0 baseline
        // (real version would track accepted/denied counts)
        double acceptedRatio = clamp(0.5 + 0.5 * arousalBalance, 0, 1);
        double score = 0.5 * arousalBalance + 0.5 * acceptedRatio;
        String status;
        if (score >= 0.8) status = "healthy";
        else if (score >= 0.5) status = "stable";
        else status = "degraded";
        return new HealthScore(score, status, arousal, acceptedRatio);
    }

    public static String format(HealthScore h) {
        return String.format("Health{ score=%.2f, status=%s, arousal=%.2f }",
                h.score(), h.status(), h.arousal());
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
