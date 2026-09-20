package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W581 — Tests for Rule Importer.
 */
class RuleImporterTest {

    @Test
    void testImportRule() {
        RuleImporter importer = new RuleImporter();

        RuleImporter.DistilledRule rule = new RuleImporter.DistilledRule(
                "r1", "logic", "A implies B", "B is true", 0.9,
                List.of("A", "A→B", "B"), Map.of());

        importer.importRule(rule);

        assertEquals(1, importer.getRuleCount());
        assertNotNull(importer.getRule("r1"));
    }

    @Test
    void testGetRulesByDomain() {
        RuleImporter importer = new RuleImporter();

        importer.importRule(new RuleImporter.DistilledRule("r1", "logic", "A", "B", 0.9, List.of(), Map.of()));
        importer.importRule(new RuleImporter.DistilledRule("r2", "logic", "C", "D", 0.8, List.of(), Map.of()));
        importer.importRule(new RuleImporter.DistilledRule("r3", "math", "1+1", "2", 0.95, List.of(), Map.of()));

        assertEquals(2, importer.getRulesByDomain("logic").size());
        assertEquals(1, importer.getRulesByDomain("math").size());
        assertEquals(0, importer.getRulesByDomain("unknown").size());
    }

    @Test
    void testGetHighConfidenceRules() {
        RuleImporter importer = new RuleImporter();

        importer.importRule(new RuleImporter.DistilledRule("r1", "logic", "A", "B", 0.9, List.of(), Map.of()));
        importer.importRule(new RuleImporter.DistilledRule("r2", "logic", "C", "D", 0.5, List.of(), Map.of()));
        importer.importRule(new RuleImporter.DistilledRule("r3", "math", "1", "2", 0.8, List.of(), Map.of()));

        List<RuleImporter.DistilledRule> high = importer.getHighConfidenceRules(0.8);
        assertEquals(2, high.size());
    }

    @Test
    void testParseJson() {
        String json = "{\"id\":\"r1\",\"domain\":\"logic\",\"premise\":\"A\",\"conclusion\":\"B\",\"confidence\":0.9,\"chain\":[\"A\",\"B\"]}";

        RuleImporter.DistilledRule rule = RuleImporter.parseJson(json);

        assertEquals("r1", rule.id());
        assertEquals("logic", rule.domain());
        assertEquals("A", rule.premise());
        assertEquals("B", rule.conclusion());
        assertEquals(0.9, rule.confidence(), 0.001);
        assertEquals(2, rule.chain().size());
    }

    @Test
    void testGetDomainCounts() {
        RuleImporter importer = new RuleImporter();

        importer.importRule(new RuleImporter.DistilledRule("r1", "logic", "A", "B", 0.9, List.of(), Map.of()));
        importer.importRule(new RuleImporter.DistilledRule("r2", "logic", "C", "D", 0.8, List.of(), Map.of()));
        importer.importRule(new RuleImporter.DistilledRule("r3", "math", "1", "2", 0.95, List.of(), Map.of()));

        Map<String, Integer> counts = importer.getDomainCounts();
        assertEquals(2, counts.get("logic"));
        assertEquals(1, counts.get("math"));
    }

    @Test
    void testImportMultipleRules() {
        RuleImporter importer = new RuleImporter();

        List<RuleImporter.DistilledRule> rules = List.of(
                new RuleImporter.DistilledRule("r1", "logic", "A", "B", 0.9, List.of(), Map.of()),
                new RuleImporter.DistilledRule("r2", "logic", "C", "D", 0.8, List.of(), Map.of())
        );

        importer.importRules(rules);
        assertEquals(2, importer.getRuleCount());
    }
}
