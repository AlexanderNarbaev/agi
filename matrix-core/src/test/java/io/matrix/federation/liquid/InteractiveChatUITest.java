package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W590 — Tests for Interactive Chat UI.
 */
class InteractiveChatUITest {

    @Test
    void testProcessMessage() {
        Map<String, KineticModulator> mods = createDefaultModulators();
        CognitiveRouter router = new CognitiveRouter(mods, 0.5, 0.5);
        ThoughtProcessVisualizer viz = new ThoughtProcessVisualizer();
        InteractiveChatUI ui = new InteractiveChatUI(router, mods, viz);

        InteractiveChatUI.ChatResponse response = ui.processMessage("Hello", false);

        assertNotNull(response);
        assertNotNull(response.response());
        assertTrue(response.confidence() > 0);
        assertNotNull(response.mode());
    }

    @Test
    void testForceDeepMode() {
        Map<String, KineticModulator> mods = createDefaultModulators();
        CognitiveRouter router = new CognitiveRouter(mods, 0.5, 0.5);
        ThoughtProcessVisualizer viz = new ThoughtProcessVisualizer();
        InteractiveChatUI ui = new InteractiveChatUI(router, mods, viz);

        InteractiveChatUI.ChatResponse response = ui.processMessage("test", true);

        assertEquals(CognitiveRouter.CognitiveMode.DEEP_UNDERSTANDING, response.mode());
    }

    @Test
    void testModulatorStates() {
        Map<String, KineticModulator> mods = createDefaultModulators();
        CognitiveRouter router = new CognitiveRouter(mods, 0.5, 0.5);
        ThoughtProcessVisualizer viz = new ThoughtProcessVisualizer();
        InteractiveChatUI ui = new InteractiveChatUI(router, mods, viz);

        InteractiveChatUI.ChatResponse response = ui.processMessage("test", false);

        assertNotNull(response.modulatorStates());
        assertTrue(response.modulatorStates().containsKey("DOPAMINE"));
    }

    @Test
    void testThoughtSteps() {
        Map<String, KineticModulator> mods = createDefaultModulators();
        CognitiveRouter router = new CognitiveRouter(mods, 0.5, 0.5);
        ThoughtProcessVisualizer viz = new ThoughtProcessVisualizer();
        InteractiveChatUI ui = new InteractiveChatUI(router, mods, viz);

        InteractiveChatUI.ChatResponse response = ui.processMessage("test", false);

        assertTrue(response.thoughtSteps() > 0);
    }

    @Test
    void testToHtml() {
        Map<String, KineticModulator> mods = createDefaultModulators();
        CognitiveRouter router = new CognitiveRouter(mods, 0.5, 0.5);
        ThoughtProcessVisualizer viz = new ThoughtProcessVisualizer();
        InteractiveChatUI ui = new InteractiveChatUI(router, mods, viz);

        String html = ui.toHtml();

        assertTrue(html.contains("MATRIX Chat v2"));
        assertTrue(html.contains("Deep Mode"));
    }

    private Map<String, KineticModulator> createDefaultModulators() {
        Map<String, KineticModulator> mods = new HashMap<>();
        mods.put("DOPAMINE", new KineticModulator("DOPAMINE", "D", 0.5, 0.1, 0, 1, 0.5, 1, 0.01, false));
        mods.put("SEROTONIN", new KineticModulator("SEROTONIN", "S", 0.5, 0.1, 0, 1, 0.5, 1, 0.01, false));
        mods.put("CORTISOL", new KineticModulator("CORTISOL", "C", 0.3, 0.1, 0, 1, 0.3, 1, 0.01, false));
        mods.put("NOREPINEPHRINE", new KineticModulator("NOREPINEPHRINE", "N", 0.3, 0.1, 0, 1, 0.3, 1, 0.01, false));
        return mods;
    }
}
