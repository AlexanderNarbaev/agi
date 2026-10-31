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
        boolean heldOut) {

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
    }
}
