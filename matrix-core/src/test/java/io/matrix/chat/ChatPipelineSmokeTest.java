package io.matrix.chat;

import io.matrix.chat.feedback.FeedbackAggregator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the public surface that operators rely on:
 * <ul>
 *   <li>{@link ConversationRecorder#flush()} drains the buffer</li>
 *   <li>{@link ChatTrainingPairGenerator#generateFromRecords(List)} returns an
 *       immutable list</li>
 *   <li>{@link ConversationFeedbackStore#ratingFor(String)} is null-safe for
 *       unknown conversations</li>
 * </ul>
 */
class ChatPipelineSmokeTest {

    @Test
    void recorderFlushAndReadAllRoundtrip(@TempDir Path tmp) throws Exception {
        ConversationRecorder r = new ConversationRecorder();
        r.storageDir = tmp.toString();
        r.enabled = true;
        r.flushIntervalSeconds = 60;
        r.onStart(null);

        r.recordAll(List.of(
                ConversationRecord.user("c1", "u", "M", "Hi"),
                ConversationRecord.assistant("c1", "u", "M",
                        "Hello!", "APPROVED", 0L, 0.1, 1)
        ));
        assertThat(r.totalRecorded()).isEqualTo(2);
        assertThat(r.totalFlushed()).isEqualTo(0);
        assertThat(r.flush()).isEqualTo(2);
        assertThat(r.totalFlushed()).isEqualTo(2);

        List<ConversationRecord> read = r.readAll(100);
        assertThat(read).hasSize(2);
        assertThat(read.get(0).role()).isEqualTo("user");
        assertThat(read.get(1).role()).isEqualTo("assistant");

        r.onStop(null);
    }

    @Test
    void feedbackStoreNullSafeForUnknownConversation(@TempDir Path tmp) {
        ConversationFeedbackStore s = new ConversationFeedbackStore();
        s.storageDir = tmp.toString();
        s.onStart(null);
        assertThat(s.ratingFor("never-seen")).isEqualTo(ConversationFeedback.RATING_NEUTRAL);
        assertThat(s.feedbackCountFor("never-seen")).isEqualTo(0L);
        assertThat(s.hasNegativeFeedback("never-seen")).isFalse();
        assertThat(s.hasPositiveFeedback("never-seen")).isFalse();
    }

    @Test
    void pairGeneratorReturnsImmutableList(@TempDir Path tmp) {
        ConversationRecorder recorder = new ConversationRecorder();
        recorder.storageDir = tmp.toString();
        recorder.enabled = true;
        recorder.flushIntervalSeconds = 60;
        recorder.onStart(null);

        ConversationFeedbackStore feedback = new ConversationFeedbackStore();
        feedback.storageDir = tmp.toString();
        feedback.onStart(null);

        FeedbackAggregator agg = new FeedbackAggregator();
        try {
            Field f = FeedbackAggregator.class.getDeclaredField("store");
            f.setAccessible(true);
            f.set(agg, feedback);
        } catch (Exception e) {
            // noop
        }

        Path output = tmp.resolve("pairs.jsonl");
        ChatTrainingPairGenerator gen = new ChatTrainingPairGenerator();
        gen.outputFile = output.toString();
        gen.recorder = recorder;
        gen.feedback = agg;
        gen.minInputLength = 1;
        gen.maxInputLength = 10000;
        gen.minOutputLength = 1;
        gen.maxOutputLength = 10000;
        gen.requiredRatingFloor = 0.0;
        gen.onStart(null);

        List<ChatTrainingPairGenerator.TrainingPair> result =
                gen.generateFromRecords(List.of(
                        ConversationRecord.user("c", "u", "M", "ping"),
                        ConversationRecord.assistant("c", "u", "M",
                                "pong", "APPROVED", 0L, 0.1, 1)
                ));
        assertThat(result).hasSize(1);
        try {
            result.add(null);
            assertThat(false).as("expected immutable list").isTrue();
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }
}