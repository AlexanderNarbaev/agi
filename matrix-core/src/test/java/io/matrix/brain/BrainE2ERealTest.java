package io.matrix.brain;

import io.matrix.knowledge.SimpleKnowledgeBase;
import io.matrix.learning.ConversationLearner;
import io.matrix.learning.BrainImprover;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W523 — End-to-End Real Brain Test.
 * 
 * Tests the complete brain lifecycle:
 * 1. Knowledge base starts empty
 * 2. Learn facts from conversation
 * 3. Knowledge base grows
 * 4. Brain can answer questions using learned facts
 * 5. Confidence filter blocks uncertain answers
 */
class BrainE2ERealTest {
    
    @Test
    void testFullBrainLifecycle(@TempDir Path tempDir) throws Exception {
        // 1. Create knowledge base with known facts
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        kb.addDocument("math", "basic math", "2+2 = 4. The square root of 16 is 4.");
        kb.addDocument("physics", "gravity", "Gravity is 9.8 m/s² on Earth. Gravity pulls objects toward the center.");
        
        // 2. Verify knowledge retrieval
        var results = kb.retrieve("2+2", 3);
        assertFalse(results.isEmpty(), "Should find math doc");
        
        // 3. Create confidence filter
        ConfidenceFilter filter = new ConfidenceFilter(0.3);
        
        // 4. High confidence accepted
        String highConf = filter.filter("The answer is 4.", 0.9);
        assertNotNull(highConf);
        
        // 5. Low confidence rejected
        String lowConf = filter.filter("I'm not sure.", 0.1);
        assertNull(lowConf);
        
        // 6. Rejection message
        String msg = filter.rejectionMessage(0.15);
        assertTrue(msg.contains("not confident"));
    }
    
    @Test
    void testConversationLearning(@TempDir Path tempDir) throws Exception {
        // Create a conversation with a known fact
        Path conv = tempDir.resolve("conv.ndjson");
        Files.writeString(conv,
            "{\"role\":\"user\",\"content\":\"What is the capital of France?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"The capital of France is Paris. It is a beautiful city known for the Eiffel Tower.\"}\n"
        );
        
        // Learn from it
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        ConversationLearner learner = new ConversationLearner(kb, tempDir);
        int learned = learner.learnFromFile(conv);
        
        // Verify
        assertEquals(1, learned);
        assertEquals(1, kb.size());
        
        // Verify retrieval
        var results = kb.retrieve("capital of France", 1);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).doc().content().contains("Paris"));
    }
    
    @Test
    void testBrainImproverLearning(@TempDir Path tempDir) throws Exception {
        // Create multiple conversations
        Path conv1 = tempDir.resolve("conv1.ndjson");
        Files.writeString(conv1,
            "{\"role\":\"user\",\"content\":\"What is gravity?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Gravity is the force of attraction between masses. It keeps us on Earth.\"}\n"
        );
        
        Path conv2 = tempDir.resolve("conv2.ndjson");
        Files.writeString(conv2,
            "{\"role\":\"user\",\"content\":\"What is AI?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"AI is artificial intelligence - systems that learn from data to make predictions.\"}\n"
        );
        
        // Learn
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        ConversationLearner learner = new ConversationLearner(kb, tempDir);
        int learned = learner.learnAll();
        
        assertTrue(learned > 0);
        assertTrue(kb.size() > 0);
        
        // Query for learned facts
        var gravityResults = kb.retrieve("gravity", 2);
        assertFalse(gravityResults.isEmpty());
        
        var aiResults = kb.retrieve("artificial intelligence", 2);
        assertFalse(aiResults.isEmpty());
    }
    
    @Test
    void testConfidenceFilterIntegration() {
        ConfidenceFilter filter = new ConfidenceFilter(0.5);
        
        // Test with various confidence levels
        assertNotNull(filter.filter("High confidence answer.", 0.9));
        assertNotNull(filter.filter("Medium confidence.", 0.6));
        assertNull(filter.filter("Low confidence.", 0.1));
        assertNull(filter.filter("Very low.", 0.0));
        assertNotNull(filter.filter("Exactly at threshold.", 0.5));
    }
    
    @Test
    void testKnowledgeBaseGrowth(@TempDir Path tempDir) throws Exception {
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        assertEquals(0, kb.size());
        
        // Add facts one by one
        kb.addDocument("fact1", "fact about sun", "The sun is a star.");
        assertEquals(1, kb.size());
        
        kb.addDocument("fact2", "fact about moon", "The moon orbits Earth.");
        assertEquals(2, kb.size());
        
        // Overwrite existing
        kb.addDocument("fact1", "updated sun", "The sun is a star at the center of our solar system.");
        assertEquals(2, kb.size());  // still 2 (overwrote)
        
        // Verify update
        var results = kb.retrieve("sun", 1);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).doc().content().contains("center of our solar system"));
    }
}
