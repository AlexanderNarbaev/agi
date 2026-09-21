package io.matrix.brain;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W488 — Tests for LearningMemory.
 */
class LearningMemoryTest {
    
    @Test
    void testStoreAndRecall(@TempDir Path tempDir) {
        LearningMemory mem = new LearningMemory(tempDir.resolve("test.json"));
        
        mem.store("key1", "value1");
        mem.store("key2", "value2");
        
        assertEquals("value1", mem.recall("key1"));
        assertEquals("value2", mem.recall("key2"));
        assertEquals(2, mem.size());
    }
    
    @Test
    void testPersistAcrossInstances(@TempDir Path tempDir) {
        Path file = tempDir.resolve("persist.json");
        
        // Store in first instance
        LearningMemory mem1 = new LearningMemory(file);
        mem1.store("fact", "gravity is a force");
        mem1.store("source", "conversation-1");
        
        // Recall in second instance (simulating restart)
        LearningMemory mem2 = new LearningMemory(file);
        assertEquals("gravity is a force", mem2.recall("fact"));
        assertEquals("conversation-1", mem2.recall("source"));
        assertEquals(2, mem2.size());
    }
    
    @Test
    void testOverwriteExistingKey(@TempDir Path tempDir) {
        LearningMemory mem = new LearningMemory(tempDir.resolve("test.json"));
        
        mem.store("key", "old");
        assertEquals("old", mem.recall("key"));
        
        mem.store("key", "new");
        assertEquals("new", mem.recall("key"));
        assertEquals(1, mem.size());
    }
    
    @Test
    void testRecallNonExistent(@TempDir Path tempDir) {
        LearningMemory mem = new LearningMemory(tempDir.resolve("test.json"));
        assertNull(mem.recall("nonexistent"));
    }
    
    @Test
    void testAllReturnsCopy(@TempDir Path tempDir) {
        LearningMemory mem = new LearningMemory(tempDir.resolve("test.json"));
        mem.store("a", "1");
        
        var all = mem.all();
        all.put("b", "2");  // should not affect original
        
        assertNull(mem.recall("b"));
        assertEquals(1, mem.size());
    }
    
    @Test
    void testEmptyMemoryFile(@TempDir Path tempDir) {
        LearningMemory mem = new LearningMemory(tempDir.resolve("empty.json"));
        assertEquals(0, mem.size());
        assertNull(mem.recall("anything"));
    }
    
    @Test
    void testCorruptedFile(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("bad.json");
        Files.writeString(file, "not json at all");
        
        LearningMemory mem = new LearningMemory(file);
        assertEquals(0, mem.size());  // should not crash
    }
    
    @Test
    void testMainMethod() {
        // Just verify it exists and doesn't crash
        LearningMemory.main(new String[]{});
    }
}
