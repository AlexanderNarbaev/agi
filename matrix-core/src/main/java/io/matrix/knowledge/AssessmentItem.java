package io.matrix.knowledge;

import java.util.List;

/**
 * One question in a versioned assessment dataset (owner directive §26).
 *
 * <p>Grading is transparent: the item declares the TERMS that must appear in
 * a correct answer, and the answer is graded by the share of those terms it
 * contains. That is deliberately not an opaque judgement — a learner, a
 * teacher or a reviewer can see exactly why an answer was accepted or
 * rejected, and the same rule applies to every item.
 *
 * <p>An item that is marked {@code heldOut} belongs to the evaluation set:
 * it must never be used to build the knowledge model or to tune the
 * learning process (owner directive §26).
 */
public record AssessmentItem(
        String id,
        String conceptId,
        LearningDimension dimension,
        String prompt,
        List<String> keyTerms,
        int difficulty,
        String variant,
        boolean critical,
        boolean heldOut,
        List<Alternative> alternatives,
        String correctAlternativeId) {

    /**
     * One of the answers a learner may choose.
     *
     * <p>An alternative may be bound to a named MISCONCEPTION, so that an
     * experiment can tell a learner that chose a wrong answer for a
     * particular reason from one that chose it for no reason at all. The
     * binding is a name, not a judgement: the experiment reports which
     * misconception the learner fell into.
     */
    public record Alternative(String id, String text, String misconceptionId) {
        public Alternative {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("an alternative must have an id");
            }
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("alternative " + id + " must have a text");
            }
            misconceptionId = misconceptionId == null ? "" : misconceptionId;
        }
    }

    public AssessmentItem {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("assessment item id must not be blank");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("assessment item prompt must not be blank");
        }
        keyTerms = List.copyOf(keyTerms == null ? List.of() : keyTerms);
        if (keyTerms.isEmpty()) {
            throw new IllegalArgumentException("assessment item " + id + " has no key terms to grade against");
        }
        alternatives = List.copyOf(alternatives == null ? List.of() : alternatives);
        if (!alternatives.isEmpty() && (correctAlternativeId == null || correctAlternativeId.isBlank())) {
            throw new IllegalArgumentException("assessment item " + id
                    + " offers alternatives and must name the correct one");
        }
    }

    /**
     * An item with no alternatives - the original form, graded by the share
     * of its key terms the answer contains.
     */
    public AssessmentItem(String id, String conceptId, LearningDimension dimension,
            String prompt, List<String> keyTerms, int difficulty, String variant,
            boolean critical, boolean heldOut) {
        this(id, conceptId, dimension, prompt, keyTerms, difficulty, variant, critical, heldOut,
                List.of(), null);
    }

    /** The alternative with the given identifier, or null. */
    public Alternative alternative(String alternativeId) {
        return alternatives.stream()
                .filter(alternative -> alternative.id().equals(alternativeId))
                .findFirst()
                .orElse(null);
    }

    /** The correct alternative, or null when the item offers none. */
    public Alternative correctAlternative() {
        return correctAlternativeId == null ? null : alternative(correctAlternativeId);
    }

    /** True when the item can be answered by choosing an alternative. */
    public boolean hasAlternatives() {
        return !alternatives.isEmpty();
    }
}
