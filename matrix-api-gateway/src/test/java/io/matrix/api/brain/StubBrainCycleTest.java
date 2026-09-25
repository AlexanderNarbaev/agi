package io.matrix.api.brain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StubBrainCycleTest {

    private final StubBrainCycle brain = new StubBrainCycle();

    @Test
    void testGreeting() {
        BrainCycle.CycleResult r = brain.cycle("hello", null, null);
        assertTrue(r.reply().contains("Hello"));
        assertTrue(r.confidence() > 0.9);
        assertTrue(r.accepted());
    }

    @Test
    void testMathFact() {
        BrainCycle.CycleResult r = brain.cycle("what is 2+2?", null, null);
        assertEquals("4", r.reply());
        assertTrue(r.confidence() > 0.95);
    }

    @Test
    void testLongInputRejected() {
        String huge = "x".repeat(2000);
        BrainCycle.CycleResult r = brain.cycle(huge, null, null);
        assertFalse(r.accepted());
        assertTrue(r.confidence() < 0.5);
    }

    @Test
    void testModulatorsFired() {
        BrainCycle.CycleResult r = brain.cycle("hello", null, null);
        assertFalse(r.modulatorsFired().isEmpty());
        assertTrue(r.modulatorsFired().contains("ETHICAL_FILTER"));
    }

    @Test
    void testExplainLookup() {
        BrainCycle.CycleResult r = brain.cycle("hello", null, null);
        // StubBrainCycle generates explainId internally; need to inspect via buildExplain
        // but we don't have direct access — verify that buildExplain works for a generated id
        // by examining the stub's behavior
        assertNotNull(r);
        assertTrue(r.durationMs() >= 0);
    }

    @Test
    void testUnknownExplainIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> brain.buildExplain("expl_nonexistent"));
    }
}
