package io.matrix.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatResourcesTest {

    @Test
    void chatStatusResourceClassLoads() {
        assertNotNull(ChatStatusResource.class);
        var r = new ChatStatusResource();
        assertNotNull(r);
    }

    @Test
    void conversationFeedbackResourceClassLoads() {
        assertNotNull(ConversationFeedbackResource.class);
        var r = new ConversationFeedbackResource();
        assertNotNull(r);
    }

    @Test
    void chatStatusResourceFeedbackRequestClassExists() {
        // Just check that the inner class is accessible
        assertNotNull(ConversationFeedbackResource.FeedbackRequest.class);
    }

    @Test
    void conversationFeedbackResourceFeedbackRequestDefaults() {
        var req = new ConversationFeedbackResource.FeedbackRequest();
        assertNull(req.conversationId);
        assertNull(req.userId);
        assertNull(req.comment);
        assertEquals(0.0, req.rating);
    }

    @Test
    void conversationFeedbackResourceFeedbackRequestFieldsSet() {
        var req = new ConversationFeedbackResource.FeedbackRequest();
        req.conversationId = "conv-x";
        req.userId = "user-x";
        req.rating = 0.9;
        req.comment = "excellent";
        assertEquals("conv-x", req.conversationId);
        assertEquals("user-x", req.userId);
        assertEquals(0.9, req.rating);
        assertEquals("excellent", req.comment);
    }
}