package io.matrix.api;

import java.util.List;

/**
 * RUN 119 — Adaptive model router.
 *
 * <p>Routes requests to different models based on prompt
 * characteristics (length, complexity, etc.). For now, picks
 * based on prompt token count.
 */
public final class AdaptiveModelRouter {

    public enum Tier { SMALL, MEDIUM, LARGE }

    private final int smallThreshold;
    private final int largeThreshold;
    private final List<String> models;

    public AdaptiveModelRouter(int smallThreshold, int largeThreshold,
                                List<String> models) {
        if (smallThreshold <= 0 || largeThreshold <= smallThreshold) {
            throw new IllegalArgumentException("invalid thresholds");
        }
        if (models == null || models.size() != 3) {
            throw new IllegalArgumentException("need 3 models");
        }
        this.smallThreshold = smallThreshold;
        this.largeThreshold = largeThreshold;
        this.models = List.copyOf(models);
    }

    /** Default router: <100 tokens small, <500 large, otherwise medium. */
    public static AdaptiveModelRouter defaultRouter() {
        return new AdaptiveModelRouter(100, 500,
                List.of("qwen:0.5b", "qwen:1.5b", "qwen:7b"));
    }

    public Tier tierFor(int tokenCount) {
        if (tokenCount < smallThreshold) return Tier.SMALL;
        if (tokenCount < largeThreshold) return Tier.MEDIUM;
        return Tier.LARGE;
    }

    public String modelFor(int tokenCount) {
        return models.get(tierFor(tokenCount).ordinal());
    }

    public List<String> models() { return models; }
    public int smallThreshold() { return smallThreshold; }
    public int largeThreshold() { return largeThreshold; }
}
