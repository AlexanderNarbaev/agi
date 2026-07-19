package io.matrix.chat;

import java.time.Instant;
import java.util.UUID;

/**
 * User-supplied quality rating for a single conversation record.
 *
 * <p>Stored separately from {@link ConversationRecord} so that feedback can
 * arrive minutes (or days) after the conversation itself, without backfilling
 * the chat log. The training pipeline joins the two by {@link #conversationId()}.
 *
 * <p>Scale:
 * <ul>
 *   <li>{@code 1.0} — explicit thumbs-up, "great answer"</li>
 *   <li>{@code 0.0} — explicit thumbs-down, "wrong / unhelpful"</li>
 *   <li>{@code 0.5} — implicit default (no rating yet)</li>
 * </ul>
 */
public record ConversationFeedback(
        String feedbackId,
        String conversationId,
        double rating,
        String comment,
        String userId,
        Instant timestamp
) {

    public static final double RATING_POSITIVE = 1.0;
    public static final double RATING_NEUTRAL = 0.5;
    public static final double RATING_NEGATIVE = 0.0;

    public ConversationFeedback {
        if (feedbackId == null) {
            feedbackId = "fb-" + UUID.randomUUID();
        }
        if (rating < 0.0 || rating > 1.0) {
            throw new IllegalArgumentException("rating must be in [0.0, 1.0], got " + rating);
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public static ConversationFeedback thumbsUp(String conversationId, String userId, String comment) {
        return new ConversationFeedback(null, conversationId,
                RATING_POSITIVE, comment, userId, Instant.now());
    }

    public static ConversationFeedback thumbsDown(String conversationId, String userId, String comment) {
        return new ConversationFeedback(null, conversationId,
                RATING_NEGATIVE, comment, userId, Instant.now());
    }

    /** A feedback entry that signals the answer was correct / useful. */
    public boolean isPositive() {
        return rating >= 0.7;
    }

    /** A feedback entry that signals the answer was wrong / harmful. */
    public boolean isNegative() {
        return rating <= 0.3;
    }
}