package io.matrix.knowledge;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Grades a learner's answer against an assessment item (owner directive §28).
 *
 * <p>The rule is the item's key terms: the score is the share of the terms
 * that appear in the answer, after the same normalisation the retrieval uses.
 * It is a transparent rule, it is deterministic, and it needs no external
 * service - which matters because the baseline demonstration must work
 * without a model provider.
 */
public final class AssessmentGrader {

    /** The share of the key terms an answer must contain to count as correct. */
    public static final double DEFAULT_PASS_THRESHOLD = 0.6;

    private AssessmentGrader() {
    }

    /** The score of an answer against an item, in [0,1]. */
    public static double score(AssessmentItem item, String learnerAnswer) {
        Set<String> present = new HashSet<>(TextFeatures.tokens(learnerAnswer));
        if (present.isEmpty()) {
            return 0.0;
        }
        long hits = 0;
        for (String term : item.keyTerms()) {
            if (present.contains(TextFeatures.fold(term.toLowerCase(java.util.Locale.ROOT)))) {
                hits++;
            }
        }
        return (double) hits / item.keyTerms().size();
    }

    /** Whether an answer passes an item. */
    public static boolean isCorrect(AssessmentItem item, String learnerAnswer) {
        return isCorrect(item, learnerAnswer, DEFAULT_PASS_THRESHOLD);
    }

    /** Whether an answer passes an item at a stated threshold. */
    public static boolean isCorrect(AssessmentItem item, String learnerAnswer, double threshold) {
        return score(item, learnerAnswer) >= threshold;
    }

    /** The key terms the answer was missing, for feedback. */
    public static List<String> missingTerms(AssessmentItem item, String learnerAnswer) {
        Set<String> present = new HashSet<>(TextFeatures.tokens(learnerAnswer));
        return item.keyTerms().stream()
                .filter(term -> !present.contains(TextFeatures.fold(term.toLowerCase(java.util.Locale.ROOT))))
                .toList();
    }
}
