package io.matrix.brain;

import io.matrix.knowledge.SimpleKnowledgeBase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W566 — Tests for BirBrainCycle (BIR-based brain, no LLM).
 */
class BirBrainCycleTest {

    @Test
    void testCycleReturnsResult() {
        Random rng = new Random(42);
        BirBrainCycle brain = new BirBrainCycle(rng);
        
        BrainCycle.CycleResult result = brain.cycle("Hello world");
        
        assertNotNull(result);
        assertNotNull(result.reply());
        assertTrue(result.durationMs() >= 0);
        assertTrue(result.confidence() >= 0);
        assertTrue(result.confidence() <= 1);
        
        brain.close();
    }

    @Test
    void testCycleWithLearnedKnowledge() {
        Random rng = new Random(42);
        BirBrainCycle brain = new BirBrainCycle(rng);
        
        // Teach the brain
        brain.learn("What is the capital of France?", "The capital of France is Paris.");
        brain.learn("What is 2+2?", "2+2 equals 4.");
        
        // Query with similar input
        BrainCycle.CycleResult result = brain.cycle("What is the capital of France?");
        
        assertNotNull(result);
        // After learning, the brain should have some response
        assertFalse(result.reply().isEmpty());
        
        brain.close();
    }

    @Test
    void testLearnIncreasesMemory() {
        Random rng = new Random(42);
        BirBrainCycle brain = new BirBrainCycle(rng);
        
        assertEquals(0, brain.getHdcBrain().size());
        
        brain.learn("question1", "answer1");
        assertEquals(1, brain.getHdcBrain().size());
        
        brain.learn("question2", "answer2");
        assertEquals(2, brain.getHdcBrain().size());
        
        brain.close();
    }

    @Test
    void testLearnFromKnowledgeBase(@TempDir Path tempDir) throws Exception {
        // Create a knowledge file
        Path kbDir = tempDir.resolve("knowledge");
        Files.createDirectories(kbDir);
        Files.writeString(kbDir.resolve("facts.md"),
            "# Facts\n\nThe Earth orbits the Sun. Water boils at 100°C.");
        
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        kb.loadFromDir(kbDir);
        
        Random rng = new Random(42);
        BirBrainCycle brain = new BirBrainCycle(rng, kb);
        
        // Directly learn from the KB documents
        var docs = kb.retrieve("Earth", 10);
        for (var doc : docs) {
            brain.learn(doc.doc().content(), doc.doc().id());
        }
        
        // Should have learned from the document
        assertTrue(brain.getHdcBrain().size() > 0);
        
        brain.close();
    }

    @Test
    void testCycleWithRAG(@TempDir Path tempDir) throws Exception {
        // Create knowledge
        Path kbDir = tempDir.resolve("knowledge");
        Files.createDirectories(kbDir);
        Files.writeString(kbDir.resolve("capitals.md"),
            "# Capitals\n\nThe capital of France is Paris. The capital of Germany is Berlin.");
        
        SimpleKnowledgeBase kb = new SimpleKnowledgeBase();
        kb.loadFromDir(kbDir);
        
        Random rng = new Random(42);
        BirBrainCycle brain = new BirBrainCycle(rng, kb);
        
        // Query with RAG
        BrainCycle.CycleResult result = brain.cycle("What is the capital of France?");
        
        assertNotNull(result);
        assertFalse(result.reply().isEmpty());
        
        brain.close();
    }

    @Test
    void testAuditTrail() {
        Random rng = new Random(42);
        BirBrainCycle brain = new BirBrainCycle(rng);
        
        brain.cycle("test input 1");
        brain.cycle("test input 2");
        
        // Audit chain should have entries
        assertTrue(brain.getAudit().size() > 0);
        
        brain.close();
    }

    @Test
    void testConfidenceRange() {
        Random rng = new Random(42);
        BirBrainCycle brain = new BirBrainCycle(rng);
        
        // Teach something
        brain.learn("What is AI?", "AI is artificial intelligence.");
        
        BrainCycle.CycleResult result = brain.cycle("What is AI?");
        
        // Confidence should be in [0, 1]
        assertTrue(result.confidence() >= 0.0, "confidence >= 0");
        assertTrue(result.confidence() <= 1.0, "confidence <= 1");
        
        brain.close();
    }

    @Test
    void testMultipleCycles() {
        Random rng = new Random(42);
        BirBrainCycle brain = new BirBrainCycle(rng);
        
        // Multiple cycles should work
        for (int i = 0; i < 10; i++) {
            BrainCycle.CycleResult result = brain.cycle("test " + i);
            assertNotNull(result);
            assertNotNull(result.reply());
        }
        
        brain.close();
    }

    @Test
    void testDeterministicBehavior() {
        // Same seed → same results
        Random rng1 = new Random(42);
        BirBrainCycle brain1 = new BirBrainCycle(rng1);
        
        Random rng2 = new Random(42);
        BirBrainCycle brain2 = new BirBrainCycle(rng2);
        
        BrainCycle.CycleResult r1 = brain1.cycle("deterministic test");
        BrainCycle.CycleResult r2 = brain2.cycle("deterministic test");
        
        assertEquals(r1.reply(), r2.reply());
        assertEquals(r1.accepted(), r2.accepted());
        
        brain1.close();
        brain2.close();
    }

    @Test
    void testNoLLMDependency() {
        // Verify no QwenOnnxBridge references in the class
        Random rng = new Random(42);
        BirBrainCycle brain = new BirBrainCycle(rng);
        
        // The brain should work without any ONNX model loaded
        BrainCycle.CycleResult result = brain.cycle("test");
        assertNotNull(result);
        
        brain.close();
    }
}
