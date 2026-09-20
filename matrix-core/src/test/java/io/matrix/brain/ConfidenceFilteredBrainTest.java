package io.matrix.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W491 — Tests for ConfidenceFilteredBrain.
 */
class ConfidenceFilteredBrainTest {
    
    @Test
    void testHighConfidenceAccepted() {
        // Create a mock brain that returns high confidence
        BrainCycle mockBrain = new BrainCycle() {
            @Override
            public CycleResult cycle(String input) {
                return new CycleResult(
                    true, "ACCEPT:ok", "The answer is correct.",
                    0.5, 5, 0.1, 0.95,  // high confidence (0.95)
                    0, 100
                );
            }
            @Override
            public void close() {}
        };
        
        ConfidenceFilteredBrain filtered = new ConfidenceFilteredBrain(mockBrain, new ConfidenceFilter(0.3));
        BrainCycle.CycleResult result = filtered.cycle("test");
        
        assertTrue(result.accepted());
        assertEquals("The answer is correct.", result.reply());
        assertEquals(1, filtered.getAcceptedCount());
        assertEquals(0, filtered.getRejectedCount());
    }
    
    @Test
    void testLowConfidenceRejected() {
        // Create a mock brain that returns low confidence
        BrainCycle mockBrain = new BrainCycle() {
            @Override
            public CycleResult cycle(String input) {
                return new CycleResult(
                    true, "ACCEPT:maybe", "I think so.",
                    0.5, 5, 0.1, 0.15,  // low confidence (0.15)
                    0, 100
                );
            }
            @Override
            public void close() {}
        };
        
        ConfidenceFilteredBrain filtered = new ConfidenceFilteredBrain(mockBrain, new ConfidenceFilter(0.3));
        BrainCycle.CycleResult result = filtered.cycle("test");
        
        assertFalse(result.accepted());
        assertTrue(result.reply().contains("not confident enough"));
        assertEquals(0, filtered.getAcceptedCount());
        assertEquals(1, filtered.getRejectedCount());
    }
    
    @Test
    void testDefaultFilterThreshold() {
        ConfidenceFilter filter = new ConfidenceFilter();
        assertEquals(0.3, filter.getMinConfidence(), 0.001);
    }
    
    @Test
    void testCustomFilterThreshold() {
        ConfidenceFilter filter = new ConfidenceFilter(0.8);
        assertEquals(0.8, filter.getMinConfidence(), 0.001);
    }
    
    @Test
    void testFilterAcceptsHighConfidence() {
        ConfidenceFilter filter = new ConfidenceFilter(0.5);
        String result = filter.filter("The answer is correct.", 0.9);
        assertNotNull(result);
    }
    
    @Test
    void testFilterRejectsLowConfidence() {
        ConfidenceFilter filter = new ConfidenceFilter(0.5);
        String result = filter.filter("I think so.", 0.1);
        assertNull(result);
    }
    
    @Test
    void testRejectionMessage() {
        ConfidenceFilter filter = new ConfidenceFilter(0.3);
        String msg = filter.rejectionMessage(0.15);
        assertTrue(msg.contains("15") && msg.contains("%"));
        assertTrue(msg.contains("30") && msg.contains("%"));
    }
    
    @Test
    void testMultipleCyclesTracking() {
        BrainCycle mockBrain = new BrainCycle() {
            int count = 0;
            @Override
            public CycleResult cycle(String input) {
                count++;
                double conf = count == 1 ? 0.9 : 0.1;  // first high, second low
                return new CycleResult(
                    true, "test", "reply",
                    0.5, 5, 0.1, conf,
                    0, 100
                );
            }
            @Override
            public void close() {}
        };
        
        ConfidenceFilteredBrain filtered = new ConfidenceFilteredBrain(mockBrain, new ConfidenceFilter(0.3));
        filtered.cycle("test1");  // accepted (0.9)
        filtered.cycle("test2");  // rejected (0.1)
        
        assertEquals(1, filtered.getAcceptedCount());
        assertEquals(1, filtered.getRejectedCount());
        assertEquals(0.5, filtered.getRejectionRate(), 0.01);
    }
}
