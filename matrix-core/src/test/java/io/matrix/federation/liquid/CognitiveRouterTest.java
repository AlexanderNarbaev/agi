package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W577 — Tests for Cognitive Router.
 */
class CognitiveRouterTest {

    @Test
    void testEthicalDilemmaRoutesDeep() {
        Map<String, KineticModulator> mods = createDefaultModulators();
        CognitiveRouter router = new CognitiveRouter(mods, 0.5, 0.5);

        CognitiveRouter.RoutingDecision d = router.route("Is it ethical to lie?", 0.3, 0.8, true);
        assertEquals(CognitiveRouter.CognitiveMode.DEEP_UNDERSTANDING, d.mode());
        assertTrue(d.reason().contains("Ethical"));
    }

    @Test
    void testHighCortisolRoutesDeep() {
        Map<String, KineticModulator> mods = createDefaultModulators();
        mods.put("CORTISOL", new KineticModulator("CORTISOL", "C", 0.9, 0.1, 0, 1, 0.9, 1, 0.01, false));

        CognitiveRouter router = new CognitiveRouter(mods, 0.5, 0.5);
        CognitiveRouter.RoutingDecision d = router.route("test", 0.3, 0.8, false);
        assertEquals(CognitiveRouter.CognitiveMode.DEEP_UNDERSTANDING, d.mode());
    }

    @Test
    void testLowConfidenceRoutesDeep() {
        Map<String, KineticModulator> mods = createDefaultModulators();
        CognitiveRouter router = new CognitiveRouter(mods, 0.5, 0.5);

        CognitiveRouter.RoutingDecision d = router.route("test", 0.3, 0.2, false);
        assertEquals(CognitiveRouter.CognitiveMode.DEEP_UNDERSTANDING, d.mode());
    }

    @Test
    void testHighNoveltyRoutesDeep() {
        Map<String, KineticModulator> mods = createDefaultModulators();
        CognitiveRouter router = new CognitiveRouter(mods, 0.5, 0.5);

        CognitiveRouter.RoutingDecision d = router.route("test", 0.8, 0.8, false);
        assertEquals(CognitiveRouter.CognitiveMode.DEEP_UNDERSTANDING, d.mode());
    }

    @Test
    void testRoutineRoutesEfficiency() {
        Map<String, KineticModulator> mods = createDefaultModulators();
        CognitiveRouter router = new CognitiveRouter(mods, 0.5, 0.5);

        CognitiveRouter.RoutingDecision d = router.route("test", 0.3, 0.8, false);
        assertEquals(CognitiveRouter.CognitiveMode.HIGH_EFFICIENCY, d.mode());
    }

    @Test
    void testAutoRouteEthical() {
        Map<String, KineticModulator> mods = createDefaultModulators();
        CognitiveRouter router = new CognitiveRouter(mods, 0.5, 0.5);

        CognitiveRouter.RoutingDecision d = router.routeAuto("Is it moral to steal?");
        assertEquals(CognitiveRouter.CognitiveMode.DEEP_UNDERSTANDING, d.mode());
    }

    @Test
    void testAutoRouteRoutine() {
        Map<String, KineticModulator> mods = createDefaultModulators();
        CognitiveRouter router = new CognitiveRouter(mods, 0.5, 0.5);

        CognitiveRouter.RoutingDecision d = router.routeAuto("hello");
        assertEquals(CognitiveRouter.CognitiveMode.HIGH_EFFICIENCY, d.mode());
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
