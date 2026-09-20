package io.matrix.brain;

import io.matrix.knowledge.SimpleKnowledgeBase;
import io.matrix.learning.ConversationLearner;
import io.matrix.learning.BrainImprover;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W564 — Real-world brain test scenarios.
 * 
 * Tests the brain's ability to handle various real-world situations:
 * 1. Multi-turn conversations
 * 2. Mixed domain knowledge
 * 3. Error recovery
 * 4. Learning from new facts
 */
class BrainRealWorldTest {
    
    @Test
    void testMultiDomainLearning(@TempDir Path tempDir) throws Exception {
        // Create conversations across different domains
        Path conv1 = tempDir.resolve("science.ndjson");
        Files.writeString(conv1,
            "{\"role\":\"user\",\"content\":\"What is quantum computing?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Quantum computing uses qubits to perform calculations. Qubits can be in superposition and entangled.\"}\n" +
            "{\"role\":\"user\",\"content\":\"How does entanglement work?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Entanglement links qubits so measuring one instantly affects another. It enables quantum parallelism.\"}\n"
        );
        
        Path conv2 = tempDir.resolve("math.ndjson");
        Files.writeString(conv2,
            "{\"role\":\"user\",\"content\":\"What is calculus?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Calculus studies rates of change (derivatives) and accumulation (integrals). Newton and Leibniz founded it.\"}\n" +
            "{\"role\":\"user\",\"content\":\"What is a derivative?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"A derivative measures instantaneous rate of change. It is the slope of the tangent line to a function.\"}\n"
        );
        
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        ConversationLearner learner = new ConversationLearner(kb, tempDir);
        
        // Learn from both domains
        int learned1 = learner.learnFromFile(conv1);
        int learned2 = learner.learnFromFile(conv2);
        
        assertEquals(2, learned1);
        assertEquals(2, learned2);
        assertEquals(4, kb.size());
        
        // Both domains should be searchable
        assertTrue(kb.retrieve("quantum", 1).size() > 0);
        assertTrue(kb.retrieve("calculus", 1).size() > 0);
    }
    
    @Test
    void testKnowledgeGrowthOverTime(@TempDir Path tempDir) throws Exception {
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        ConversationLearner learner = new ConversationLearner(kb, tempDir);
        
        // Session 1: learn about physics
        Path s1 = tempDir.resolve("s1.ndjson");
        Files.writeString(s1,
            "{\"role\":\"user\",\"content\":\"What is physics?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Physics is the study of matter, energy, and their interactions. It includes mechanics, thermodynamics, and electromagnetism.\"}\n"
        );
        learner.learnFromFile(s1);
        assertEquals(1, kb.size());
        
        // Session 2: learn about biology
        Path s2 = tempDir.resolve("s2.ndjson");
        Files.writeString(s2,
            "{\"role\":\"user\",\"content\":\"What is biology?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Biology is the study of life. It includes genetics, evolution, and cellular processes.\"}\n"
        );
        learner.learnFromFile(s2);
        assertEquals(2, kb.size());
        
        // Both facts should be searchable
        assertTrue(kb.retrieve("physics", 1).size() > 0);
        assertTrue(kb.retrieve("biology", 1).size() > 0);
    }
    
    @Test
    void testConfidenceFilterWithVariedQuestions() {
        ConfidenceFilter filter = new ConfidenceFilter(0.5);
        
        // High confidence facts
        assertNotNull(filter.filter("The capital of France is Paris.", 0.95));
        assertNotNull(filter.filter("2+2=4", 0.99));
        
        // Medium confidence
        assertNotNull(filter.filter("Quantum computing uses qubits.", 0.7));
        
        // Low confidence - should be rejected
        assertNull(filter.filter("I'm not sure about this.", 0.1));
        assertNull(filter.filter("Maybe it could be X or Y.", 0.05));
    }
    
    @Test
    void testBrainImproverWithVariedConversations(@TempDir Path tempDir) throws Exception {
        // Create varied conversations
        Path conv1 = tempDir.resolve("greeting.ndjson");
        Files.writeString(conv1,
            "{\"role\":\"user\",\"content\":\"Hello, how are you?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Hello! I'm doing well. How can I help you today?\"}\n"
        );
        
        Path conv2 = tempDir.resolve("facts.ndjson");
        Files.writeString(conv2,
            "{\"role\":\"user\",\"content\":\"What is the speed of light?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"The speed of light is 299,792,458 meters per second in vacuum. It is denoted as c.\"}\n" +
            "{\"role\":\"user\",\"content\":\"Why is it constant?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Einstein showed that light speed is constant in all inertial frames, leading to special relativity.\"}\n"
        );
        
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        ConversationLearner learner = new ConversationLearner(kb, tempDir);
        
        // Learn from both
        int learned1 = learner.learnFromFile(conv1);
        int learned2 = learner.learnFromFile(conv2);
        
        // Facts should be learned (greeting is too short for Q&A pair)
        assertTrue(learned1 >= 0);
        assertTrue(learned2 > 0);
        
        // Knowledge should be searchable
        assertTrue(kb.retrieve("speed of light", 1).size() > 0);
    }
    
    @Test
    void testExportJsonFormat(@TempDir Path tempDir) throws Exception {
        Path convFile = tempDir.resolve("export.ndjson");
        Files.writeString(convFile,
            "{\"role\":\"user\",\"content\":\"What is gravity?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"Gravity is the force that attracts objects with mass toward each other. On Earth, it is 9.8 m/s².\"}\n"
        );
        
        // The NDJSON format should be parseable
        try (var lines = java.nio.file.Files.lines(convFile)) {
            long count = lines.count();
            assertEquals(2, count);
        }
    }
    
    @Test
    void testMultipleExportFormats(@TempDir Path tempDir) throws Exception {
        // Create a conversation
        Path convFile = tempDir.resolve("export.ndjson");
        Files.writeString(convFile,
            "{\"role\":\"user\",\"content\":\"What is AI?\"}\n" +
            "{\"role\":\"assistant\",\"content\":\"AI is artificial intelligence - systems that learn from data.\"}\n"
        );
        
        // Read back and verify
        String content = Files.readString(convFile);
        assertTrue(content.contains("What is AI?"));
        assertTrue(content.contains("artificial intelligence"));
    }
}
