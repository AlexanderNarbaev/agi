package io.matrix.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W436 — Tests for ConversationModelEval.
 */
class ConversationModelEvalTest {
    
    @Test
    void testModelEvalClassExists() throws NoSuchMethodException {
        var main = io.matrix.cli.ConversationModelEval.class
            .getMethod("main", String[].class);
        assertNotNull(main);
    }
    
    @Test
    void testTruncate(@TempDir Path tempDir) throws Exception {
        var method = io.matrix.cli.ConversationModelEval.class
            .getDeclaredMethod("truncate", String.class, int.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(null, "Hello World", 5);
        assertEquals("Hello...", result);
    }
    
    @Test
    void testTruncateShort(@TempDir Path tempDir) throws Exception {
        var method = io.matrix.cli.ConversationModelEval.class
            .getDeclaredMethod("truncate", String.class, int.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(null, "Hi", 10);
        assertEquals("Hi", result);
    }
    
    @Test
    void testLoadDefaultTests(@TempDir Path tempDir) throws Exception {
        var method = io.matrix.cli.ConversationModelEval.class
            .getDeclaredMethod("loadDefaultTests", java.util.List.class);
        method.setAccessible(true);
        
        java.util.List<Object> list = new java.util.ArrayList<>();
        method.invoke(null, list);
        
        assertFalse(list.isEmpty());
        // Just verify we have tests
        assertTrue(list.size() >= 5);
    }
}
