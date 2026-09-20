package io.matrix.federation.liquid;

import java.util.*;

/**
 * W581 — Rule Importer for offline distillation.
 *
 * Loads JSON rules from distillation pipeline into BIR/HDC.
 * Supports:
 * - Logical chains from LLM distillation
 * - Confidence scores
 * - Domain tagging
 */
public final class RuleImporter {

    private final Map<String, List<DistilledRule>> rulesByDomain = new HashMap<>();
    private final Map<String, DistilledRule> rulesById = new HashMap<>();

    /**
     * A distilled rule from the LLM distillation pipeline.
     */
    public record DistilledRule(
            String id,
            String domain,
            String premise,
            String conclusion,
            double confidence,
            List<String> chain,
            Map<String, String> metadata
    ) {}

    /**
     * Import a rule.
     */
    public void importRule(DistilledRule rule) {
        rulesById.put(rule.id(), rule);
        rulesByDomain.computeIfAbsent(rule.domain(), k -> new ArrayList<>()).add(rule);
    }

    /**
     * Import multiple rules.
     */
    public void importRules(List<DistilledRule> rules) {
        for (DistilledRule rule : rules) {
            importRule(rule);
        }
    }

    /**
     * Parse a JSON string into a DistilledRule.
     * Expected format:
     * {"id":"r1","domain":"logic","premise":"A","conclusion":"B","confidence":0.9,"chain":["A","B"]}
     */
    public static DistilledRule parseJson(String json) {
        // Simple JSON parser for rule format
        String id = extractField(json, "id");
        String domain = extractField(json, "domain");
        String premise = extractField(json, "premise");
        String conclusion = extractField(json, "conclusion");
        double confidence = Double.parseDouble(extractField(json, "confidence"));
        List<String> chain = extractArray(json, "chain");

        return new DistilledRule(id, domain, premise, conclusion, confidence, chain, Map.of());
    }

    /**
     * Get rules by domain.
     */
    public List<DistilledRule> getRulesByDomain(String domain) {
        return rulesByDomain.getOrDefault(domain, List.of());
    }

    /**
     * Get a rule by ID.
     */
    public DistilledRule getRule(String id) {
        return rulesById.get(id);
    }

    /**
     * Get all rules.
     */
    public List<DistilledRule> getAllRules() {
        return new ArrayList<>(rulesById.values());
    }

    /**
     * Get high-confidence rules (above threshold).
     */
    public List<DistilledRule> getHighConfidenceRules(double threshold) {
        List<DistilledRule> result = new ArrayList<>();
        for (DistilledRule rule : rulesById.values()) {
            if (rule.confidence() >= threshold) {
                result.add(rule);
            }
        }
        return result;
    }

    /**
     * Get rule count by domain.
     */
    public Map<String, Integer> getDomainCounts() {
        Map<String, Integer> counts = new HashMap<>();
        rulesByDomain.forEach((domain, rules) -> counts.put(domain, rules.size()));
        return counts;
    }

    /**
     * Total rule count.
     */
    public int getRuleCount() {
        return rulesById.size();
    }

    // Simple JSON extraction helpers
    private static String extractField(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            // Try numeric value
            needle = "\"" + field + "\":";
            start = json.indexOf(needle);
            if (start < 0) return "";
            start += needle.length();
            int end = json.indexOf(',', start);
            if (end < 0) end = json.indexOf('}', start);
            return json.substring(start, end).trim();
        }
        start += needle.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private static List<String> extractArray(String json, String field) {
        String needle = "\"" + field + "\":[";
        int start = json.indexOf(needle);
        if (start < 0) return List.of();
        start += needle.length();
        int end = json.indexOf(']', start);
        String arrayContent = json.substring(start, end);

        List<String> result = new ArrayList<>();
        for (String item : arrayContent.split(",")) {
            item = item.trim();
            if (item.startsWith("\"")) item = item.substring(1);
            if (item.endsWith("\"")) item = item.substring(0, item.length() - 1);
            result.add(item);
        }
        return result;
    }
}
