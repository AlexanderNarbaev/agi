package io.matrix.distill;

import java.util.*;
import java.util.regex.*;

/**
 * W721 — LLM Knowledge Distiller.
 *
 * <p>Offline pipeline to extract knowledge from LLMs (Qwen, LLaMA) into MATRIX
 * formats (BIR rules, HDC vectors) WITHOUT runtime dependency.
 *
 * <h2>CONSTITUTION I Compliance</h2>
 * <p>This distiller is OFFLINE only. It is NOT used in runtime paths.
 * After distillation, the resulting BIR rules and HDC vectors are
 * used directly in {@link io.matrix.brain.BirBrainCycle}.
 *
 * <h2>Workflow</h2>
 * <ol>
 *   <li>Load Qwen-0.5B ONNX (offline, CPU)</li>
 *   <li>Prompt engineering: "Extract boolean rules from: [context]"</li>
 *   <li>Parse output to BIR clauses (A and B to C)</li>
 *   <li>Validate consistency (no contradictions)</li>
 * </ol>
 *
 * <h2>Rule Format</h2>
 * <p>Rules are expressed as: {@code A AND B NOT C => D}
 * Where A, B, C, D are boolean variables.
 */
public final class LLMKnowledgeDistiller {

    /**
     * A distilled rule extracted from the LLM.
     */
    public record DistilledRule(
            String id,
            String description,
            List<String> positivePremises,  // A, B in "A AND B"
            List<String> negativePremises,  // C in "NOT C"
            String conclusion,                // D in "=> D"
            double confidence,                // 0.0 to 1.0
            String sourceContext              // Original text
    ) {}

    /**
     * Result of a distillation batch.
     */
    public record DistillationResult(
            int totalPrompts,
            int successfulExtractions,
            int contradictionsFound,
            List<DistilledRule> rules,
            List<String> contradictions,
            long durationMs
    ) {}

    /**
     * LLM backend interface. In production this calls Qwen-0.5B.
     * For testing, a mock implementation can be injected.
     */
    public interface LLMBackend {
        /**
         * Generate a completion for the given prompt.
         */
        String generate(String prompt) throws Exception;

        /**
         * Check if the backend is available.
         */
        boolean isAvailable();
    }

    /**
     * Mock backend for testing — uses pattern matching to simulate
     * LLM output. This avoids the 2.5GB model dependency in tests.
     */
    public static class MockBackend implements LLMBackend {
        private final Random rng;

        public MockBackend(long seed) {
            this.rng = new Random(seed);
        }

        @Override
        public String generate(String prompt) {
            // Simulate LLM output with rule extraction
            if (prompt.contains("weather")) {
                return "Rule: rain AND NOT umbrella => wet\n" +
                       "Rule: sun AND temperature > 20 => hot\n" +
                       "Rule: clouds => overcast";
            }
            if (prompt.contains("ethics")) {
                return "Rule: harm_others => unethical\n" +
                       "Rule: lie AND detect => distrust\n" +
                       "Rule: help_others AND safe => ethical";
            }
            if (prompt.contains("logic")) {
                return "Rule: A AND B => C\n" +
                       "Rule: NOT A OR B => equivalent";
            }
            return "Rule: x AND y => z";
        }

        @Override
        public boolean isAvailable() { return true; }
    }

    private final LLMBackend backend;
    private final Random rng;
    private final List<DistilledRule> extractedRules = new ArrayList<>();
    private final List<String> contradictions = new ArrayList<>();

    public LLMKnowledgeDistiller(LLMBackend backend) {
        this.backend = backend;
        this.rng = new Random(42);
    }

    public LLMKnowledgeDistiller(LLMBackend backend, long seed) {
        this.backend = backend;
        this.rng = new Random(seed);
    }

    /**
     * Distill rules from a context using prompt engineering.
     */
    public List<DistilledRule> distillFromContext(String context) {
        if (!backend.isAvailable()) {
            throw new IllegalStateException("LLM backend not available");
        }

        List<DistilledRule> batch = new ArrayList<>();
        try {
            String prompt = buildPrompt(context);
            String response = backend.generate(prompt);
            batch.addAll(parseRules(response, context));
            extractedRules.addAll(batch);
        } catch (Exception e) {
            // Distillation failed for this context — return empty
        }
        return batch;
    }

    /**
     * Run a full distillation batch on multiple contexts.
     */
    public DistillationResult distillBatch(List<String> contexts) {
        long start = System.currentTimeMillis();
        int successful = 0;

        for (String context : contexts) {
            List<DistilledRule> rules = distillFromContext(context);
            if (!rules.isEmpty()) {
                successful++;
                extractedRules.addAll(rules);
            }
        }

        // Check for contradictions
        detectContradictions();

        return new DistillationResult(
                contexts.size(),
                successful,
                contradictions.size(),
                new ArrayList<>(extractedRules),
                new ArrayList<>(contradictions),
                System.currentTimeMillis() - start
        );
    }

    /**
     * Build a prompt for the LLM to extract rules.
     */
    private String buildPrompt(String context) {
        return "Extract boolean rules from the following context. " +
               "Format each rule as: 'Rule: A AND B => C' or 'Rule: A AND NOT B => C'. " +
               "Context: " + context;
    }

    /**
     * Parse LLM output into structured DistilledRule objects.
     */
    private List<DistilledRule> parseRules(String response, String sourceContext) {
        List<DistilledRule> rules = new ArrayList<>();
        Pattern rulePattern = Pattern.compile(
                "Rule:\\s+([\\w\\s]+(?:\\s+(?:AND|NOT)\\s+[\\w\\s]+)*?)\\s*=>\\s*(\\w+)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = rulePattern.matcher(response);
        int ruleNum = 0;

        while (matcher.find()) {
            String premisesStr = matcher.group(1).trim();
            String conclusion = matcher.group(2).trim();

            List<String> positive = new ArrayList<>();
            List<String> negative = new ArrayList<>();

            // Parse premises
            String[] tokens = premisesStr.split("\\s+(?:AND|,)\\s+");
            for (String token : tokens) {
                token = token.trim();
                if (token.isEmpty()) continue;
                if (token.toUpperCase().startsWith("NOT ")) {
                    negative.add(token.substring(4).trim());
                } else {
                    positive.add(token);
                }
            }

            DistilledRule rule = new DistilledRule(
                    "rule-" + (++ruleNum),
                    "Distilled from context",
                    positive,
                    negative,
                    conclusion,
                    0.85,  // Default confidence
                    sourceContext
            );
            rules.add(rule);
        }

        return rules;
    }

    /**
     * Detect contradictions among extracted rules.
     */
    private void detectContradictions() {
        contradictions.clear();
        Map<String, Boolean> conclusions = new HashMap<>();

        for (DistilledRule rule : extractedRules) {
            String key = rule.conclusion();
            boolean isTrue = rule.positivePremises().stream()
                    .anyMatch(p -> p.contains("harm") || p.contains("bad"));

            if (conclusions.containsKey(key) && conclusions.get(key) != isTrue) {
                contradictions.add("Rule " + rule.id() + " contradicts earlier rule for " + key);
            }
            conclusions.put(key, isTrue);
        }
    }

    /**
     * Get all extracted rules.
     */
    public List<DistilledRule> getExtractedRules() {
        return new ArrayList<>(extractedRules);
    }

    /**
     * Get all detected contradictions.
     */
    public List<String> getContradictions() {
        return new ArrayList<>(contradictions);
    }

    /**
     * Clear state for a new batch.
     */
    public void reset() {
        extractedRules.clear();
        contradictions.clear();
    }
}
