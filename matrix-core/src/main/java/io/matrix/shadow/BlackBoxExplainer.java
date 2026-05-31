package io.matrix.shadow;

import java.util.List;

/**
 * Explains decisions made by external AI systems.
 *
 * <p>Counters the "Black Box AI" force of degradation by providing
 * transparency into how external systems reach conclusions.
 *
 * <p>Ref: L8_Roadmap.md §3.6
 */
public class BlackBoxExplainer {

    public enum TransparencyLevel {
        OPAQUE, PARTIAL, TRANSPARENT
    }

    public record Explanation(
            String decision,
            TransparencyLevel level,
            String reasoning,
            double confidence,
            boolean reliable
    ) {}

    /**
     * Analyzes an external AI decision and generates an explanation.
     */
    public Explanation explain(String externalDecision, List<String> context) {
        String lower = externalDecision.toLowerCase();

        if (containsAny(lower, "because", "due to", "based on", "according to")) {
            return new Explanation(externalDecision, TransparencyLevel.TRANSPARENT,
                    "Decision includes explicit reasoning in its statement",
                    0.9, true);
        }

        if (context.isEmpty()) {
            return new Explanation(externalDecision, TransparencyLevel.OPAQUE,
                    "No context provided — decision is opaque. Request explainability.",
                    0.3, false);
        }

        StringBuilder reasoning = new StringBuilder("Based on context: ");
        for (int i = 0; i < Math.min(3, context.size()); i++) {
            if (i > 0) reasoning.append("; ");
            reasoning.append(context.get(i));
        }

        return new Explanation(externalDecision, TransparencyLevel.PARTIAL,
                reasoning.toString(), 0.5, false);
    }

    /**
     * Verifies whether a source is trustworthy.
     */
    public Explanation verifySource(String sourceUrl, List<String> knownTrustedSources) {
        if (sourceUrl == null || sourceUrl.isEmpty()) {
            return new Explanation("Unknown source", TransparencyLevel.OPAQUE,
                    "No source provided — cannot verify", 0.0, false);
        }

        for (String trusted : knownTrustedSources) {
            if (sourceUrl.contains(trusted)) {
                return new Explanation(sourceUrl, TransparencyLevel.TRANSPARENT,
                        "Source matches trusted domain: " + trusted, 0.9, true);
            }
        }

        return new Explanation(sourceUrl, TransparencyLevel.PARTIAL,
                "Source not in trusted list — additional verification recommended",
                0.4, false);
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }
}
