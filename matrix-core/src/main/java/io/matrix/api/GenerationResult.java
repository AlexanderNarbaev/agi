package io.matrix.api;

import java.util.List;

/**
 * RUN 86 — GenerationResult record returned by
 * {@link QwenOnnxBridge#generateWithProbs}.
 *
 * <p>Captures the generated text plus per-step probability info so
 * clients can inspect what the model was "thinking".
 */
public record GenerationResult(
        String text,
        int tokensGenerated,
        long elapsedMs,
        List<TokenStep> steps
) {
    /** Per-step result with argmax and top-N candidates. */
    public record TokenStep(
            int tokenId,
            String token,
            double probability,
            List<Candidate> topCandidates
    ) {}

    /** Single top-N candidate token. */
    public record Candidate(int tokenId, String token, double probability) {}

    /** Average per-token confidence (mean of argmax probs). */
    public double avgConfidence() {
        if (steps == null || steps.isEmpty()) return 0.0;
        double sum = 0.0;
        for (TokenStep s : steps) sum += s.probability();
        return sum / steps.size();
    }

    /** Min per-token confidence (worst step). */
    public double minConfidence() {
        if (steps == null || steps.isEmpty()) return 0.0;
        double min = Double.POSITIVE_INFINITY;
        for (TokenStep s : steps) {
            if (s.probability() < min) min = s.probability();
        }
        return min;
    }
}
