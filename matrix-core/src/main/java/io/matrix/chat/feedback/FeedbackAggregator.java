package io.matrix.chat.feedback;

import io.matrix.chat.ConversationFeedbackStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Convenience façade for callers (like {@code ChatTrainingPairGenerator}) that
 * need a read-only view of feedback state without injecting the full
 * {@link ConversationFeedbackStore} directly.
 *
 * <p>Keeps the generator's surface area minimal: it only asks "what's the
 * latest rating for this conversation?" and never mutates feedback state.
 */
@ApplicationScoped
public class FeedbackAggregator {

    @Inject
    ConversationFeedbackStore store;

    /** Latest rating (0.0..1.0), defaulting to 0.5 (neutral). */
    public double ratingFor(String conversationId) {
        return store.ratingFor(conversationId);
    }

    public long feedbackCountFor(String conversationId) {
        return store.feedbackCountFor(conversationId);
    }

    public boolean hasPositiveFeedback(String conversationId) {
        return store.hasPositiveFeedback(conversationId);
    }

    public boolean hasNegativeFeedback(String conversationId) {
        return store.hasNegativeFeedback(conversationId);
    }
}