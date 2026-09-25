package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W589 — Tests for Thought Process Visualizer.
 */
class ThoughtProcessVisualizerTest {

    @Test
    void testRecordBirRule() {
        ThoughtProcessVisualizer viz = new ThoughtProcessVisualizer();
        viz.recordBirRule("r1", "A", "B", 0.9);

        assertEquals(1, viz.getStepCount());
        assertEquals("BIR", viz.getSteps().get(0).type());
    }

    @Test
    void testRecordHdcMatch() {
        ThoughtProcessVisualizer viz = new ThoughtProcessVisualizer();
        viz.recordHdcMatch("pattern1", 0.85);

        assertEquals(1, viz.getStepCount());
        assertEquals("HDC", viz.getSteps().get(0).type());
    }

    @Test
    void testRecordMctsDecision() {
        ThoughtProcessVisualizer viz = new ThoughtProcessVisualizer();
        viz.recordMctsDecision("move_north", 0.7, 100);

        assertEquals(1, viz.getStepCount());
        assertEquals("MCTS", viz.getSteps().get(0).type());
    }

    @Test
    void testRecordCausalReasoning() {
        ThoughtProcessVisualizer viz = new ThoughtProcessVisualizer();
        viz.recordCausalReasoning("rain", "wet_ground", 0.95);

        assertEquals(1, viz.getStepCount());
        assertEquals("CAUSAL", viz.getSteps().get(0).type());
    }

    @Test
    void testRecordRouting() {
        ThoughtProcessVisualizer viz = new ThoughtProcessVisualizer();
        viz.recordRouting(CognitiveRouter.CognitiveMode.DEEP_UNDERSTANDING, "Low confidence");

        assertEquals(1, viz.getStepCount());
        assertEquals("ROUTE", viz.getSteps().get(0).type());
    }

    @Test
    void testToJson() {
        ThoughtProcessVisualizer viz = new ThoughtProcessVisualizer();
        viz.recordBirRule("r1", "A", "B", 0.9);
        viz.recordHdcMatch("p1", 0.8);

        String json = viz.toJson();

        assertTrue(json.contains("\"steps\""));
        assertTrue(json.contains("BIR"));
        assertTrue(json.contains("HDC"));
    }

    @Test
    void testToHtml() {
        ThoughtProcessVisualizer viz = new ThoughtProcessVisualizer();
        viz.recordBirRule("r1", "A", "B", 0.9);

        String html = viz.toHtml();

        assertTrue(html.contains("MATRIX Thought Process"));
        assertTrue(html.contains("Explain Decision"));
        assertTrue(html.contains("BIR"));
    }

    @Test
    void testClear() {
        ThoughtProcessVisualizer viz = new ThoughtProcessVisualizer();
        viz.recordBirRule("r1", "A", "B", 0.9);
        viz.recordHdcMatch("p1", 0.8);

        assertEquals(2, viz.getStepCount());

        viz.clear();
        assertEquals(0, viz.getStepCount());
    }
}
