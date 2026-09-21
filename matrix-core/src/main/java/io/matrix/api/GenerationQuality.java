package io.matrix.api;

/**
 * RUN 138 — Generation quality heuristics.
 *
 * <p>Computes simple quality metrics on a generated string:
 * length, vocabulary diversity, repetition, etc.
 */
public final class GenerationQuality {

    public record QualityMetrics(int charCount, int wordCount,
                                  int uniqueWords, double uniqueRatio,
                                  int lines, boolean hasRepetition) {}

    public QualityMetrics evaluate(String text) {
        if (text == null || text.isBlank()) {
            return new QualityMetrics(0, 0, 0, 0.0, 0, false);
        }
        int charCount = text.length();
        String[] words = text.trim().split("\\s+");
        int wordCount = words.length;
        java.util.Set<String> unique = new java.util.HashSet<>();
        for (String w : words) unique.add(w.toLowerCase());
        double uniqueRatio = wordCount == 0 ? 0.0
                : (double) unique.size() / wordCount;
        int lines = text.split("\n").length;
        // Detect simple 3-gram repetition
        boolean hasRepetition = detectRepetition(text);
        return new QualityMetrics(charCount, wordCount, unique.size(),
                uniqueRatio, lines, hasRepetition);
    }

    /** Visible-for-testing repetition detector. */
    boolean detectRepetition(String text) {
        if (text.length() < 30) return false;
        // Look for any 5-character substring that appears 3+ times
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (int i = 0; i + 5 <= text.length(); i++) {
            String sub = text.substring(i, i + 5);
            counts.merge(sub, 1, Integer::sum);
        }
        for (int c : counts.values()) {
            if (c >= 3) return true;
        }
        return false;
    }
}
