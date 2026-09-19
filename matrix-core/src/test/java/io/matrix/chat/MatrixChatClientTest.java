package io.matrix.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W451 — Tests for MatrixChatClient.
 * 
 * Tests the builder pattern and basic API.
 * Does not test actual model inference (too slow for unit tests).
 */
class MatrixChatClientTest {
    
    @Test
    void testBuilderExists() {
        assertNotNull(MatrixChatClient.builder());
    }
    
    @Test
    void testBuilderClass() throws NoSuchMethodException {
        var buildMethod = MatrixChatClient.Builder.class
            .getMethod("build");
        assertNotNull(buildMethod);
    }
    
    @Test
    void testBuilderMethods() throws NoSuchMethodException {
        // Verify all builder methods exist
        assertNotNull(MatrixChatClient.Builder.class
            .getMethod("modelPath", String.class));
        assertNotNull(MatrixChatClient.Builder.class
            .getMethod("dataDir", String.class));
        assertNotNull(MatrixChatClient.Builder.class
            .getMethod("useGpu", boolean.class));
        assertNotNull(MatrixChatClient.Builder.class
            .getMethod("maxNewTokens", int.class));
    }
    
    @Test
    void testTurnRecord() {
        // Verify the Turn record exists
        var recordComponents = MatrixChatClient.Turn.class.getRecordComponents();
        assertEquals(2, recordComponents.length);
    }
    
    @Test
    void testClientClassHasMainMethods() throws NoSuchMethodException {
        // All public API methods
        assertNotNull(MatrixChatClient.class.getMethod("chat", String.class, String.class));
        assertNotNull(MatrixChatClient.class.getMethod("getHistory", String.class));
        assertNotNull(MatrixChatClient.class.getMethod("deleteSession", String.class));
        assertNotNull(MatrixChatClient.class.getMethod("close"));
        assertNotNull(MatrixChatClient.class.getMethod("builder"));
    }
}
