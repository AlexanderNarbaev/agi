package io.matrix.brain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W521 — Tests for BrainSensorBridge.
 */
class BrainSensorBridgeTest {
    
    @Test
    void testClassExists() {
        assertNotNull(BrainSensorBridge.class);
    }
    
    @Test
    void testMainMethod() throws NoSuchMethodException {
        var main = BrainSensorBridge.class.getMethod("main", String[].class);
        assertNotNull(main);
    }
    
    @Test
    void testProcessStdinMethod() throws NoSuchMethodException {
        var method = BrainSensorBridge.class.getMethod("processStdin", String.class);
        assertNotNull(method);
    }
    
    @Test
    void testProcessFilesMethod() throws NoSuchMethodException {
        var method = BrainSensorBridge.class.getMethod("processFiles");
        assertNotNull(method);
    }
    
    @Test
    void testProcessFileMethod() throws NoSuchMethodException {
        var method = BrainSensorBridge.class.getMethod("processFile", java.nio.file.Path.class);
        assertNotNull(method);
    }
    
    @Test
    void testGetProcessedCountMethod() throws NoSuchMethodException {
        var method = BrainSensorBridge.class.getMethod("getProcessedCount");
        assertNotNull(method);
    }
    
    @Test
    void testCloseMethod() throws NoSuchMethodException {
        var method = BrainSensorBridge.class.getMethod("close");
        assertNotNull(method);
    }
}

class AutonomyIntegrationTest {
    
    @Test
    void testBrainRunnerCanLearn(@TempDir Path tempDir) throws Exception {
        // Create a conversation file
        Path convFile = tempDir.resolve("test.ndjson");
        Files.writeString(convFile,
            "{\"role\":\"user\",\"content\":\"What is machine learning?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Machine learning is a subset of AI where systems learn patterns from data to make predictions without being explicitly programmed.\"}\n"
        );
        
        io.matrix.knowledge.SimpleKnowledgeBase kb = new io.matrix.knowledge.SimpleKnowledgeBase();
        io.matrix.learning.ConversationLearner learner = new io.matrix.learning.ConversationLearner(kb, tempDir);
        
        int learned = learner.learnFromFile(convFile);
        assertEquals(1, learned);
        assertEquals(1, kb.size());
        
        // Verify the knowledge is searchable
        var results = kb.retrieve("machine learning", 1);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).doc().content().contains("subset of AI"));
    }
    
    @Test
    void testConfidenceFilterIntegration() {
        ConfidenceFilter filter = new ConfidenceFilter(0.5);
        
        // High confidence accepted
        BrainCycle.CycleResult highConf = new BrainCycle.CycleResult(
            true, "ACCEPT", "The answer is correct.", 0.5, 5, 0.1, 0.95, 0, 100
        );
        String filtered = filter.filter(highConf.reply(), highConf.confidence());
        assertNotNull(filtered);
        
        // Low confidence rejected
        BrainCycle.CycleResult lowConf = new BrainCycle.CycleResult(
            true, "ACCEPT", "I think so.", 0.5, 5, 0.1, 0.15, 0, 100
        );
        String filtered2 = filter.filter(lowConf.reply(), lowConf.confidence());
        assertNull(filtered2);
    }
}
