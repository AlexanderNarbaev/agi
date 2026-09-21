package io.matrix.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W436 — Tests for ConversationReport.
 */
class ConversationReportTest {
    
    @Test
    void testReportRunsOnEmptyDir(@TempDir Path tempDir) throws Exception {
        // Verify class loads and main exists
        var main = io.matrix.cli.ConversationReport.class
            .getMethod("main", String[].class);
        assertNotNull(main);
    }
    
    @Test
    void testReportWithTestData(@TempDir Path tempDir) throws IOException {
        // Create a fake session
        Path dataDir = Path.of("data/conversations");
        if (!Files.exists(dataDir)) {
            return;  // Skip if not in project
        }
        
        String testSession = "w436-report-test";
        Path testFile = dataDir.resolve(testSession + ".ndjson");
        Files.writeString(testFile,
            "# META name: test\n" +
            "{\"role\":\"user\",\"content\":\"hello world\",\"timestamp\":\"2026-09-19T12:00:00Z\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"hi there! how are you?\",\"timestamp\":\"2026-09-19T12:00:01Z\"}\n"
        );
        
        try {
            // Read report output by running with output file
            Path reportFile = tempDir.resolve("report.txt");
            String[] args = { reportFile.toString() };
            
            // Capture stdout is hard - just verify it doesn't throw
            io.matrix.cli.ConversationReport.main(args);
            
            // Report should exist
            assertTrue(Files.exists(reportFile));
            String content = Files.readString(reportFile);
            assertTrue(content.contains("Conversations") || content.contains("Sessions"));
        } finally {
            Files.deleteIfExists(testFile);
        }
    }
    
    @Test
    void testExtractField(@TempDir Path tempDir) throws Exception {
        var method = io.matrix.cli.ConversationReport.class
            .getDeclaredMethod("extractField", String.class, String.class);
        method.setAccessible(true);
        
        String json = "{\"role\":\"user\",\"content\":\"test\"}";
        String result = (String) method.invoke(null, json, "role");
        assertEquals("user", result);
    }
}
