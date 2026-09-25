package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W587 — Tests for Biochemical Dashboard.
 */
class BiochemicalDashboardTest {

    @Test
    void testGenerateHtml() {
        Map<String, KineticModulator> mods = new HashMap<>();
        mods.put("DOPAMINE", new KineticModulator("DOPAMINE", "Dopamine", 0.5, 0.1, 0, 1, 0.5, 1, 0.01, false));
        mods.put("SEROTONIN", new KineticModulator("SEROTONIN", "Serotonin", 0.5, 0.1, 0, 1, 0.6, 1, 0.01, false));

        CorridorHomeostat homeostat = CorridorHomeostat.createDefault();

        BiochemicalDashboard dashboard = new BiochemicalDashboard(mods, homeostat);
        String html = dashboard.generateHtml();

        assertTrue(html.contains("MATRIX Biochemical Dashboard"));
        assertTrue(html.contains("Dopamine"));
        assertTrue(html.contains("Serotonin"));
        assertTrue(html.contains("d3.v7.min.js"));
    }

    @Test
    void testGenerateModulatorJson() {
        Map<String, KineticModulator> mods = new HashMap<>();
        mods.put("DOPAMINE", new KineticModulator("DOPAMINE", "Dopamine", 0.5, 0.1, 0, 1, 0.5, 1, 0.01, false));

        CorridorHomeostat homeostat = CorridorHomeostat.createDefault();

        BiochemicalDashboard dashboard = new BiochemicalDashboard(mods, homeostat);
        String json = dashboard.generateModulatorJson();

        assertTrue(json.contains("DOPAMINE"));
        assertTrue(json.contains("Dopamine"));
        assertTrue(json.contains("level"));
    }

    @Test
    void testHtmlContainsHeatmap() {
        Map<String, KineticModulator> mods = new HashMap<>();
        CorridorHomeostat homeostat = CorridorHomeostat.createDefault();

        BiochemicalDashboard dashboard = new BiochemicalDashboard(mods, homeostat);
        String html = dashboard.generateHtml();

        assertTrue(html.contains("heatmap"));
        assertTrue(html.contains("Brain Activity"));
    }
}
