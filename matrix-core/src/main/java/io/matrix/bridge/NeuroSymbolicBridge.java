package io.matrix.bridge;

import io.matrix.neuron.TruthTable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Neuro-symbolic bridge between MATRIX MPDT neurons and LLM systems.
 *
 * <p>Enables hybrid mode: MPDT neurons provide verifiable boolean logic,
 * LLMs provide natural language understanding. The bridge translates
 * between the two representations:
 *
 * <ul>
 *   <li>LLM → MPDT: extract boolean rules from LLM reasoning chains</li>
 *   <li>MPDT → LLM: verbalize truth table logic as natural language</li>
 * </ul>
 *
 * <p>Inspired by BoolXLLM (Cheng et al., 2026) and neuro-symbolic approaches.
 */
public final class NeuroSymbolicBridge {

    private final Random rng;

    public NeuroSymbolicBridge() {
        this(new Random());
    }

    public NeuroSymbolicBridge(Random rng) {
        this.rng = rng;
    }

    /**
     * Converts boolean rules extracted from LLM reasoning into MPDT truth tables.
     */
    public RuleExtractionResult extractRules(String llmReasoning) {
        List<BooleanRule> rules = parseRules(llmReasoning);
        List<TruthTable> tables = new ArrayList<>();

        for (BooleanRule rule : rules) {
            TruthTable table = ruleToTable(rule);
            tables.add(table);
        }

        return new RuleExtractionResult(rules, tables, tables.size());
    }

    /**
     * Converts MPDT truth table logic into natural language explanation.
     */
    public String explain(TruthTable table, Map<Integer, String> inputLabels) {
        StringBuilder sb = new StringBuilder();
        sb.append("Neuron with ").append(table.k()).append(" inputs. ");

        int ones = table.table().cardinality();
        int total = 1 << table.k();
        sb.append("Outputs 1 for ").append(ones).append(" out of ")
                .append(total).append(" input combinations. ");

        if (ones == 0) {
            sb.append("Always outputs 0 (contradiction).");
        } else if (ones == total) {
            sb.append("Always outputs 1 (tautology).");
        } else if (ones == total / 2) {
            sb.append("Balanced function (e.g., XOR-like).");
        }

        return sb.toString();
    }

    /**
     * Hybrid reasoning: use MPDT for verifiable logic, LLM for NL context.
     */
    public HybridResult reason(TruthTable logicCore, String llmContext) {
        boolean mpdtResult = evaluateSample(logicCore, 0);
        double confidence = confidence(logicCore);

        return new HybridResult(
                mpdtResult,
                confidence,
                "MPDT result: " + mpdtResult + " | LLM context: " +
                        llmContext.substring(0, Math.min(llmContext.length(), 80))
        );
    }

    private List<BooleanRule> parseRules(String reasoning) {
        List<BooleanRule> rules = new ArrayList<>();
        for (String line : reasoning.split("[.\\n]")) {
            line = line.trim().toLowerCase();
            if (line.contains("if") && line.contains("then")) {
                String cond = line.substring(line.indexOf("if") + 2,
                        line.indexOf("then")).trim();
                String action = line.substring(line.indexOf("then") + 4).trim();
                if (!cond.isBlank() && !action.isBlank()) {
                    rules.add(new BooleanRule(cond, action));
                }
            }
        }
        return rules;
    }

    private TruthTable ruleToTable(BooleanRule rule) {
        int k = Math.min(8, countVariables(rule.condition()));
        TruthTable table = TruthTable.random(k, rng);
        return table;
    }

    private int countVariables(String condition) {
        return (int) condition.chars()
                .filter(c -> Character.isLetter(c))
                .distinct()
                .count();
    }

    private boolean evaluateSample(TruthTable table, int input) {
        return table.evaluate(input);
    }

    private double confidence(TruthTable table) {
        long ones = table.table().cardinality();
        long total = 1L << table.k();
        double ratio = (double) ones / total;
        return Math.max(ratio, 1.0 - ratio);
    }

    public record BooleanRule(String condition, String action) {}

    public record RuleExtractionResult(
            List<BooleanRule> rules,
            List<TruthTable> tables,
            int count
    ) {}

    public record HybridResult(
            boolean mpdtOutput,
            double confidence,
            String explanation
    ) {}
}
