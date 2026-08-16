package io.matrix.chat;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ChatDrivenTrainerCountersTest {

    @Test
    void newTrainerHasZeroCounters() {
        var t = new ChatDrivenTrainer();
        assertEquals(0, t.totalCycles());
        assertEquals(0, t.totalPairs());
        assertEquals(0, t.totalFeedbacks());
        assertEquals(0, t.totalTrains());
    }

    @Test
    void conversationFeedbackConstructorAndAccessors() {
        var fb = new ConversationFeedback(
                "fb-1", "conv-1", 0.8, "helpful", "user-1",
                Instant.now());
        assertEquals("fb-1", fb.feedbackId());
        assertEquals("conv-1", fb.conversationId());
        assertEquals(0.8, fb.rating());
        assertEquals("helpful", fb.comment());
        assertEquals("user-1", fb.userId());
    }

    @Test
    void conversationFeedbackThumbsUp() {
        var fb = ConversationFeedback.thumbsUp("conv-1", "user-1", "great");
        assertEquals("conv-1", fb.conversationId());
        assertEquals("user-1", fb.userId());
        assertEquals("great", fb.comment());
        assertEquals(ConversationFeedback.RATING_POSITIVE, fb.rating());
        assertTrue(fb.isPositive());
        assertFalse(fb.isNegative());
    }

    @Test
    void conversationFeedbackThumbsDown() {
        var fb = ConversationFeedback.thumbsDown("conv-1", "user-1", "wrong");
        assertEquals(ConversationFeedback.RATING_NEGATIVE, fb.rating());
        assertTrue(fb.isNegative());
        assertFalse(fb.isPositive());
    }

    @Test
    void conversationFeedbackIsPositiveAndNegative() {
        var positive = new ConversationFeedback(
                "fb-1", "conv-1", 1.0, "ok", "u1", Instant.now());
        assertTrue(positive.isPositive());

        var negative = new ConversationFeedback(
                "fb-2", "conv-1", 0.0, "no", "u1", Instant.now());
        assertTrue(negative.isNegative());

        var neutral = new ConversationFeedback(
                "fb-3", "conv-1", 0.5, "meh", "u1", Instant.now());
        assertFalse(neutral.isPositive());
        assertFalse(neutral.isNegative());
    }

    @Test
    void chatTrainingPairGeneratorClassLoads() {
        assertNotNull(ChatTrainingPairGenerator.class);
    }

    @Test
    void conversationRecorderClassLoads() {
        assertNotNull(ConversationRecorder.class);
    }

    @Test
    void conversationFeedbackStoreClassLoads() {
        assertNotNull(ConversationFeedbackStore.class);
    }

    @Test
    void conversationRecordNewConversationIdIsUnique() {
        var ids = new java.util.HashSet<String>();
        for (int i = 0; i < 100; i++) {
            ids.add(ConversationRecord.newConversationId());
        }
        assertEquals(100, ids.size(), "All 100 generated IDs must be unique");
    }
}