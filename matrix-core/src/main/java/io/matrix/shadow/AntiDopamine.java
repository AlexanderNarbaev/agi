package io.matrix.shadow;

/**
 * Assessment of content toxicity and manipulation patterns.
 *
 * <p>Detects: attention traps, addictive patterns, rage bait,
 * infinite scroll loops, and dopamine-optimization dark patterns.
 *
 * <p>Ref: L8_Roadmap.md §3.6
 */
public class AntiDopamine {

    public enum PatternType {
        INFINITE_SCROLL,
        ATTENTION_TRAP,
        RAGE_BAIT,
        ADDICTIVE_LOOP,
        CLICKBAIT,
        FEAR_MONGERING
    }

    public record DopaminePattern(
            PatternType type,
            double confidence,
            String description,
            String evidence
    ) {}

    /**
     * Scans content for manipulative patterns.
     */
    public DopaminePattern scan(String content) {
        String lower = content.toLowerCase();

        if (countPhrases(lower, "scroll", "infinite", "endless") >= 2) {
            return new DopaminePattern(PatternType.INFINITE_SCROLL, 0.8,
                    "Infinite scroll pattern detected",
                    "Content encourages endless consumption");
        }

        if (countPhrases(lower, "shocking", "you won't believe", "jaw-dropping") >= 1) {
            return new DopaminePattern(PatternType.CLICKBAIT, 0.7,
                    "Clickbait pattern detected",
                    "Sensationalist language to capture attention");
        }

        if (countPhrases(lower, "they are destroying", "they want to", "the truth about",
                "secret agenda") >= 2) {
            return new DopaminePattern(PatternType.RAGE_BAIT, 0.75,
                    "Rage bait pattern detected",
                    "Content designed to provoke anger and engagement");
        }

        if (countPhrases(lower, "just one more", "next level", "keep playing",
                "one more try") >= 1) {
            return new DopaminePattern(PatternType.ADDICTIVE_LOOP, 0.85,
                    "Addictive loop pattern detected",
                    "Content designed for compulsive repetition");
        }

        if (countPhrases(lower, "danger warning", "immediate threat", "horrible consequences",
                "catastrophe") >= 2) {
            return new DopaminePattern(PatternType.FEAR_MONGERING, 0.7,
                    "Fear mongering pattern detected",
                    "Content uses fear to manipulate decisions");
        }

        return null;
    }

    /**
     * Returns a safety score [0..1] — 1.0 means clean.
     */
    public double safetyScore(String content) {
        var pattern = scan(content);
        if (pattern == null) return 1.0;
        return Math.max(0.0, 1.0 - pattern.confidence());
    }

    private int countPhrases(String text, String... phrases) {
        int count = 0;
        for (String phrase : phrases) {
            int idx = 0;
            while ((idx = text.indexOf(phrase, idx)) != -1) {
                count++;
                idx += phrase.length();
            }
        }
        return count;
    }
}
