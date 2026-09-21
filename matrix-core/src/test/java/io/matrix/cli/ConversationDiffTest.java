package io.matrix.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W420 — Tests for ConversationDiff.
 */
class ConversationDiffTest {
    
    @Test
    void testReadRecordsValidFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("test.ndjson");
        Files.writeString(file,
            "{\"role\":\"user\",\"content\":\"Hello\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Hi!\"}\n"
        );
        
        var method = io.matrix.cli.ConversationDiff.class
            .getDeclaredMethod("readRecords", Path.class);
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        var records = (java.util.List<?>) method.invoke(null, file);
        
        assertEquals(2, records.size());
    }
    
    @Test
    void testReadRecordsEmpty(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("empty.ndjson");
        Files.writeString(file, "");
        
        var method = io.matrix.cli.ConversationDiff.class
            .getDeclaredMethod("readRecords", Path.class);
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked")
        var records = (java.util.List<?>) method.invoke(null, file);
        assertEquals(0, records.size());
    }
    
    @Test
    void testTruncate(@TempDir Path tempDir) throws Exception {
        var method = io.matrix.cli.ConversationDiff.class
            .getDeclaredMethod("truncate", String.class, int.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(null, "Hello World", 5);
        assertEquals("Hello...", result);
    }
    
    @Test
    void testTruncateShort(@TempDir Path tempDir) throws Exception {
        var method = io.matrix.cli.ConversationDiff.class
            .getDeclaredMethod("truncate", String.class, int.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(null, "Hi", 10);
        assertEquals("Hi", result);
    }
    
    @Test
    void testTruncateNull(@TempDir Path tempDir) throws Exception {
        var method = io.matrix.cli.ConversationDiff.class
            .getDeclaredMethod("truncate", String.class, int.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(null, (String) null, 10);
        assertEquals("<none>", result);
    }
}
