package io.matrix.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W426 — Tests for ConversationValidate.
 */
class ConversationValidateTest {
    
    @Test
    void testValidateValidFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("valid.ndjson");
        Files.writeString(file,
            "{\"role\":\"user\",\"content\":\"Hello\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Hi!\"}\n"
        );
        
        var method = io.matrix.cli.ConversationValidate.class
            .getDeclaredMethod("validateFile", Path.class);
        method.setAccessible(true);
        
        // Should not throw
        method.invoke(null, file);
        assertTrue(Files.exists(file));
    }
    
    @Test
    void testValidateFileWithMeta(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("meta.ndjson");
        Files.writeString(file,
            "# META name: test\n" +
            "{\"role\":\"user\",\"content\":\"Hello\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Hi!\"}\n"
        );
        
        var method = io.matrix.cli.ConversationValidate.class
            .getDeclaredMethod("validateFile", Path.class);
        method.setAccessible(true);
        method.invoke(null, file);
    }
    
    @Test
    void testValidateFileInvalidJson(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("invalid.ndjson");
        Files.writeString(file, "not valid json\n");
        
        var method = io.matrix.cli.ConversationValidate.class
            .getDeclaredMethod("validateFile", Path.class);
        method.setAccessible(true);
        // Should report error but not throw
        method.invoke(null, file);
    }
    
    @Test
    void testExtractField(@TempDir Path tempDir) throws Exception {
        var method = io.matrix.cli.ConversationValidate.class
            .getDeclaredMethod("extractField", String.class, String.class);
        method.setAccessible(true);
        
        String json = "{\"role\":\"user\",\"content\":\"test\"}";
        String result = (String) method.invoke(null, json, "role");
        assertEquals("user", result);
    }
}
