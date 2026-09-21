package io.matrix.embodied;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ActiveInferenceEngineTest {

    @Test
    void testCreateEngine() {
        ActiveInferenceEngine engine = new ActiveInferenceEngine(10, 42L);
        assertNotNull(engine);
        assertEquals(10, engine.getBeliefs().length);
    }

    @Test
    void testPredict() {
        ActiveInferenceEngine engine = new ActiveInferenceEngine(5, 42L);
        ActiveInferenceEngine.Prediction pred = engine.predict();
        assertEquals(5, pred.predicted().length);
        assertTrue(pred.confidence() > 0);
    }

    @Test
    void testSelectAction() {
        ActiveInferenceEngine engine = new ActiveInferenceEngine(3, 42L);
        List<ActiveInferenceEngine.Action> actions = Arrays.asList(
            new ActiveInferenceEngine.Action("explore", 0.8),
            new ActiveInferenceEngine.Action("exploit", 0.3),
            new ActiveInferenceEngine.Action("wait", 0.5)
        );
        double[] obs = {1.0, 0.5, -0.2};
        ActiveInferenceEngine.Action selected = engine.selectAction(actions, new ActiveInferenceEngine.Observation(obs));
        assertEquals("exploit", selected.name(), "Should select action with lowest free energy");
    }

    @Test
    void testBeliefUpdate() {
        ActiveInferenceEngine engine = new ActiveInferenceEngine(3, 42L);
        double[] before = engine.getBeliefs().clone();
        engine.selectAction(
            List.of(new ActiveInferenceEngine.Action("test", 0.5)),
            new ActiveInferenceEngine.Observation(new double[]{1.0, 2.0, 3.0})
        );
        double[] after = engine.getBeliefs();
        // Beliefs should have changed
        boolean changed = false;
        for (int i = 0; i < before.length; i++) {
            if (before[i] != after[i]) { changed = true; break; }
        }
        assertTrue(changed, "Beliefs should update based on observation");
    }

    @Test
    void testEmptyActionList() {
        ActiveInferenceEngine engine = new ActiveInferenceEngine(3, 42L);
        // Should not crash with empty list — returns null from stream
        assertDoesNotThrow(() -> {
            engine.selectAction(List.of(), new ActiveInferenceEngine.Observation(new double[3]));
        });
    }
}
