package io.matrix.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationFeedbackStoreTest {

    @Test
    void thumbsUpSetsRatingAndIsPositive(@TempDir Path tmp) throws Exception {
        ConversationFeedbackStore s = new ConversationFeedbackStore();
        s.storageDir = tmp.toString();
        s.onStart(null);

        s.submit(ConversationFeedback.thumbsUp("c1", "alice", "good"));
        assertThat(s.ratingFor("c1")).isEqualTo(ConversationFeedback.RATING_POSITIVE);
        assertThat(s.hasPositiveFeedback("c1")).isTrue();
        assertThat(s.hasNegativeFeedback("c1")).isFalse();
        assertThat(s.feedbackCountFor("c1")).isEqualTo(1);

        Path expected = tmp.resolve("feedback-" + java.time.LocalDate.now() + ".ndjson");
        s.flush();
        assertThat(Files.exists(expected)).isTrue();
    }

    @Test
    void thumbsDownMarksNegative(@TempDir Path tmp) {
        ConversationFeedbackStore s = new ConversationFeedbackStore();
        s.storageDir = tmp.toString();
        s.onStart(null);

        s.submit(ConversationFeedback.thumbsDown("c2", "bob", "wrong"));
        assertThat(s.hasNegativeFeedback("c2")).isTrue();
        assertThat(s.hasPositiveFeedback("c2")).isFalse();
    }

    @Test
    void defaultRatingIsNeutral(@TempDir Path tmp) {
        ConversationFeedbackStore s = new ConversationFeedbackStore();
        s.storageDir = tmp.toString();
        s.onStart(null);

        assertThat(s.ratingFor("never-rated")).isEqualTo(ConversationFeedback.RATING_NEUTRAL);
    }

    @Test
    void invalidRatingRejected() {
        try {
            new ConversationFeedback("fb-1", "c", 1.5, null, null, null);
            assertThat(false).as("should have thrown").isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("rating must be in [0.0, 1.0]");
        }
    }
}