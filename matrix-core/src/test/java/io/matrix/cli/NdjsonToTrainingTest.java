package io.matrix.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W404 — Tests for NdjsonToTraining.
 * 
 * The training converter is testable without the Qwen model
 * since it operates only on NDJSON files.
 */
class NdjsonToTrainingTest {
    
    @Test
    void testExtractPairsFromValidNdjson(@TempDir Path tempDir) throws Exception {
        Path ndjson = tempDir.resolve("test.ndjson");
        Files.writeString(ndjson,
            "{\"role\":\"user\",\"content\":\"Hello\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Hi there!\"}\n" +
            "{\"role\":\"user\",\"content\":\"How are you?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"I'm good!\"}\n"
        );
        
        // Invoke extractPairs via reflection
        try {
            var method = io.matrix.cli.NdjsonToTraining.class
                .getDeclaredMethod("extractPairs", Path.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String[]> pairs = (List<String[]>) method.invoke(null, ndjson);
            
            assertEquals(2, pairs.size());
            assertEquals("Hello", pairs.get(0)[0]);
            assertEquals("Hi there!", pairs.get(0)[1]);
            assertEquals("How are you?", pairs.get(1)[0]);
            assertEquals("I'm good!", pairs.get(1)[1]);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }
    }
    
    @Test
    void testExtractPairsSkipsIncomplete(@TempDir Path tempDir) throws Exception {
        // Missing assistant response should not produce pair
        Path ndjson = tempDir.resolve("test.ndjson");
        Files.writeString(ndjson,
            "{\"role\":\"user\",\"content\":\"Lonely question\"}\n"
        );
        
        try {
            var method = io.matrix.cli.NdjsonToTraining.class
                .getDeclaredMethod("extractPairs", Path.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<String[]> pairs = (List<String[]>) method.invoke(null, ndjson);
            assertEquals(0, pairs.size(), "Should skip incomplete conversations");
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }
    }
    
    @Test
    void testExtractFieldHandlesMissing() throws Exception {
        var method = io.matrix.cli.NdjsonToTraining.class
            .getDeclaredMethod("extractField", String.class, String.class);
        method.setAccessible(true);
        
        String result = (String) method.invoke(null, "{\"role\":\"user\"}", "content");
        assertNull(result);
    }
    
    @Test
    void testExtractFieldHandlesEscapes() throws Exception {
        var method = io.matrix.cli.NdjsonToTraining.class
            .getDeclaredMethod("extractField", String.class, String.class);
        method.setAccessible(true);
        
        String json = "{\"content\":\"Hello \\\"world\\\"\"}";
        String result = (String) method.invoke(null, json, "content");
        assertEquals("Hello \"world\"", result);
    }
}
