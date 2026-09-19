package io.matrix.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W426 — Tests for ConversationFind.
 */
class ConversationFindTest {
    
    @Test
    void testComputeTF(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("words.ndjson");
        Files.writeString(file,
            "{\"role\":\"user\",\"content\":\"hello world hello\"}\n"
        );
        
        var method = io.matrix.cli.ConversationFind.class
            .getDeclaredMethod("computeTF", Path.class);
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        var tf = (java.util.Map<String, Integer>) method.invoke(null, file);
        
        assertEquals(2, tf.get("hello"));  // appears twice
        assertEquals(1, tf.get("world"));
        // "a" filtered out (length <= 2)
    }
    
    @Test
    void testComputeTFEmptyFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("empty.ndjson");
        Files.writeString(file, "");
        
        var method = io.matrix.cli.ConversationFind.class
            .getDeclaredMethod("computeTF", Path.class);
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        var tf = (java.util.Map<String, Integer>) method.invoke(null, file);
        
        assertTrue(tf.isEmpty());
    }
    
    @Test
    void testExtractField(@TempDir Path tempDir) throws Exception {
        var method = io.matrix.cli.ConversationFind.class
            .getDeclaredMethod("extractField", String.class, String.class);
        method.setAccessible(true);
        
        String json = "{\"role\":\"assistant\",\"content\":\"test\"}";
        String result = (String) method.invoke(null, json, "role");
        assertEquals("assistant", result);
    }
}
