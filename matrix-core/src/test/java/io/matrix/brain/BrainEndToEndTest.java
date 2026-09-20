package io.matrix.brain;

import io.matrix.knowledge.SimpleKnowledgeBase;
import io.matrix.learning.BrainImprover;
import io.matrix.learning.ConversationLearner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W513 — End-to-End Brain Test.
 * 
 * Tests the complete brain flow:
 * 1. Knowledge base with learned facts
 * 2. RAG-augmented brain
 * 3. Confidence filtering (anti-hallucination)
 * 4. Learning from conversations
 * 5. Self-improvement loop
 */
class BrainEndToEndTest {
    
    @Test
    void testKnowledgeBaseGrowsWithLearnedFacts(@TempDir Path tempDir) throws Exception {
        // Create a conversation
        Path convFile = tempDir.resolve("test.ndjson");
        Files.writeString(convFile,
            "{\"role\":\"user\",\"content\":\"What is quantum computing?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Quantum computing uses qubits instead of bits to process information simultaneously in superposition, enabling exponentially faster computation for certain problems.\"}\n"
        );
        
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        ConversationLearner learner = new ConversationLearner(kb, tempDir);
        
        assertEquals(0, kb.size());
        int learned = learner.learnFromFile(convFile);
        assertEquals(1, learned);
        assertEquals(1, kb.size());
    }
    
    @Test
    void testBrainImproverCycle(@TempDir Path tempDir) throws Exception {
        Path conv = tempDir.resolve("conv.ndjson");
        Files.writeString(conv,
            "{\"role\":\"user\",\"content\":\"What is machine learning?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Machine learning is a subset of AI where systems learn patterns from data to make predictions without being explicitly programmed.\"}\n"
        );
        
        BrainImprover improver = new BrainImprover(tempDir);
        int learned = improver.improveOnce();
        assertEquals(1, learned);
        assertEquals(1, improver.getKnowledgeBase().size());
        assertEquals(1, improver.getCyclesCompleted());
    }
    
    @Test
    void testKnowledgeRetrievalAfterLearning(@TempDir Path tempDir) throws Exception {
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        kb.addDocument("ml", "machine learning", 
            "Machine learning is a subset of AI. It learns from data.");
        kb.addDocument("dl", "deep learning", 
            "Deep learning uses neural networks with multiple layers.");
        
        var results = kb.retrieve("machine learning", 2);
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).doc().content().contains("subset of AI"));
    }
    
    @Test
    void testConfidenceFilter() {
        ConfidenceFilter filter = new ConfidenceFilter(0.3);
        
        // High confidence accepted
        String high = filter.filter("The answer is 4.", 0.9);
        assertNotNull(high);
        
        // Low confidence rejected
        String low = filter.filter("I think so.", 0.1);
        assertNull(low);
        
        // Rejection message
        String msg = filter.rejectionMessage(0.15);
        assertTrue(msg.contains("not confident"));
    }
    
    @Test
    void testConversationLearnerIgnoresNoise(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("noisy.ndjson");
        Files.writeString(file,
            "{\"role\":\"user\",\"content\":\"h\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"ok\"}\n" +
            "{\"role\":\"user\",\"content\":\"What is gravity?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Gravity is the force of attraction between masses. It keeps us on Earth and orbits the Moon around our planet.\"}\n"
        );
        
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        ConversationLearner learner = new ConversationLearner(kb, tempDir);
        int learned = learner.learnFromFile(file);
        assertEquals(1, learned);  // Only gravity pair (ok is too short)
    }
}
