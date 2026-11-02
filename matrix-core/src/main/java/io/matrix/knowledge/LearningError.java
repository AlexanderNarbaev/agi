package io.matrix.knowledge;

import java.util.Set;

/**
 * One error the learner made, and whether it is verified (RECON-W42.6).
 *
 * <p>The mission's §9 says the error-driven learning integration happens
 * *"only after verifying the math, 8 required error fields"*, and that
 * *"do not indiscriminately weaken neural associations after every
 * unexpected event"*.
 *
 * <p><b>The eight fields are required, and the record refuses to be
 * built without them.</b> A record without the concept cannot be acted
 * on; a record without the expected answer cannot be checked; a record
 * without the dimension cannot be routed to the learner.
 *
 * <p><b>The verification is the point.</b> An error is verified when the
 * learner has answered the same item wrongly in <b>two different
 * sessions</b>. A single wrong answer may be a slip, a misread question
 * or a mistyped answer, and integrating it would train the system on a
 * fluke. The integration therefore acts on the verified errors only, and
 * the unverified ones are not even recorded — they are still in the
 * outcomes, which is where a fluke belongs.
 */
public record LearningError(
        String id,
        String workspaceId,
        String itemId,
        String conceptId,
        LearningDimension dimension,
        String learnerAnswer,
        String expectedAnswer,
        boolean verified) {

    public LearningError {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("an error needs an id");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("an error needs a workspace");
        }
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("an error needs an item");
        }
        if (conceptId == null || conceptId.isBlank()) {
            throw new IllegalArgumentException("an error needs a concept");
        }
        if (dimension == null) {
            throw new IllegalArgumentException("an error needs a dimension");
        }
        if (learnerAnswer == null) {
            throw new IllegalArgumentException("an error needs the learner's answer");
        }
        if (expectedAnswer == null) {
            throw new IllegalArgumentException("an error needs the expected answer");
        }
    }

    /**
     * Whether an item's failures are verified.
     *
     * <p>The learner must have got the item wrong in two DIFFERENT
     * sessions. One session may hold a slip; two sessions hold a gap.
     *
     * @param wrongSessions the sessions in which the item was answered
     *     wrongly
     * @return true when the error is verified
     */
    public static boolean verified(Set<String> wrongSessions) {
        return wrongSessions != null && wrongSessions.size() >= 2;
    }
}
