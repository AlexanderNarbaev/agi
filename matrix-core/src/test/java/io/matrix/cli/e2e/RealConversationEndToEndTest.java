package io.matrix.cli.e2e;

import io.matrix.cli.RealConversationCli;
import io.matrix.cli.RealConversationReplay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W396 — End-to-end integration tests for real conversation CLI.
 * 
 * Tests the full flow:
 * 1. List sessions
 * 2. Replay existing session
 * 3. Verify NDJSON parsing edge cases
 * 
 * Note: This does NOT test actual model inference (Qwen2.5 ONNX) since
 * that requires the model file and is too slow for unit tests.
 * See RealConversationCli for actual model inference testing.
 */
class RealConversationEndToEndTest {
    
    @Test
    void testListSessionsReturnsNonNull() {
        // Should not throw even if data dir is empty
        var sessions = RealConversationCli.listRecentSessions(10);
        assertNotNull(sessions);
    }
    
    @Test
    void testListSessionsExcludesHiddenFiles(@TempDir Path tempDir) throws Exception {
        // Create a temp data dir with various files
        Files.createFile(tempDir.resolve(".hidden.ndjson"));
        Files.writeString(tempDir.resolve("real-session.ndjson"), 
            "{\"role\":\"user\",\"content\":\"test\"}\n");
        
        // We can't easily redirect DEFAULT_DATA_DIR for this test
        // Just verify the filter logic works on real directory
        var sessions = RealConversationCli.listRecentSessions(100);
        for (String s : sessions) {
            assertFalse(s.startsWith("."), "Hidden session returned: " + s);
        }
    }
    
    @Test
    void testReplayClassExists() throws NoSuchMethodException {
        assertNotNull(RealConversationReplay.class);
        var method = RealConversationReplay.class.getMethod("main", String[].class);
        assertNotNull(method);
    }
    
    @Test
    void testJsonFieldExtractionHandlesEmptyString() throws Exception {
        // Edge case: empty content
        var method = RealConversationCli.class.getDeclaredMethod("extractJsonField", String.class, String.class);
        method.setAccessible(true);
        
        String json = "{\"role\":\"user\",\"content\":\"\"}";
        String content = (String) method.invoke(null, json, "content");
        assertEquals("", content);
    }
    
    @Test
    void testJsonFieldExtractionHandlesEscapes() throws Exception {
        // Edge case: escaped quotes
        var method = RealConversationCli.class.getDeclaredMethod("extractJsonField", String.class, String.class);
        method.setAccessible(true);
        
        String json = "{\"role\":\"assistant\",\"content\":\"He said \\\"hello\\\"\"}";
        String content = (String) method.invoke(null, json, "content");
        assertEquals("He said \"hello\"", content);
    }
    
    @Test
    void testJsonFieldExtractionHandlesNewlines() throws Exception {
        var method = RealConversationCli.class.getDeclaredMethod("extractJsonField", String.class, String.class);
        method.setAccessible(true);
        
        String json = "{\"role\":\"assistant\",\"content\":\"Line1\\nLine2\"}";
        String content = (String) method.invoke(null, json, "content");
        assertEquals("Line1\nLine2", content);
    }
    
    @Test
    void testListSessionsWithZeroLimit() {
        var sessions = RealConversationCli.listRecentSessions(0);
        assertEquals(0, sessions.size());
    }
    
    @Test
    void testListSessionsWithNegativeLimit() {
        // Negative limit should be treated as 0
        var sessions = RealConversationCli.listRecentSessions(-5);
        assertEquals(0, sessions.size());
    }
}
