package io.matrix.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W420 — Tests for ConversationCompact.
 */
class ConversationCompactTest {
    
    @Test
    void testExtractFieldReplacesNewlines(@TempDir Path tempDir) throws Exception {
        var method = io.matrix.cli.ConversationCompact.class
            .getDeclaredMethod("extractField", String.class, String.class);
        method.setAccessible(true);
        
        // Newlines should be replaced with spaces in compact view
        String json = "{\"role\":\"user\",\"content\":\"line1\\nline2\"}";
        String result = (String) method.invoke(null, json, "content");
        assertEquals("line1 line2", result);
    }
    
    @Test
    void testExtractFieldHandlesEscapes(@TempDir Path tempDir) throws Exception {
        var method = io.matrix.cli.ConversationCompact.class
            .getDeclaredMethod("extractField", String.class, String.class);
        method.setAccessible(true);
        
        String json = "{\"content\":\"hello\\tworld\"}";
        String result = (String) method.invoke(null, json, "content");
        assertEquals("hello world", result);  // compact converts tabs to spaces
    }
}
