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
     * Converts MPDT truth table logic into natural language explanation with DNF.
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

        // Extract DNF (Disjunctive Normal Form)
        String dnf = extractDNF(table, inputLabels);
        if (!dnf.isEmpty()) {
            sb.append("\nDNF: ").append(dnf);
        }

        return sb.toString();
    }

    /**
     * Extracts DNF from truth table: OR of AND terms for each input where output=1.
     */
    private String extractDNF(TruthTable table, Map<Integer, String> inputLabels) {
        List<String> terms = new ArrayList<>();
        int total = 1 << table.k();

        for (int input = 0; input < total; input++) {
            if (table.evaluate(input)) {
                List<String> literals = new ArrayList<>();
                for (int bit = 0; bit < table.k(); bit++) {
                    String label = inputLabels != null && inputLabels.containsKey(bit)
                            ? inputLabels.get(bit)
                            : "x" + bit;
                    if ((input & (1 << bit)) != 0) {
                        literals.add(label);
                    } else {
                        literals.add("¬" + label);
                    }
                }
                terms.add(String.join(" ∧ ", literals));
            }
        }

        if (terms.isEmpty()) return "";
        if (terms.size() == 1) return terms.get(0);
        return "(" + String.join(") ∨ (", terms) + ")";
    }

    /**
     * Hybrid reasoning: use MPDT for verifiable logic, LLM for NL context.
     *
     * <p>Extracts relevant input bits from LLM context by hashing the context
     * to determine which input combinations to evaluate, then evaluates the
     * truth table at those points.
     */
    public HybridResult reason(TruthTable logicCore, String llmContext) {
        // Hash LLM context to determine input selection
        int contextHash = llmContext.hashCode();
        int maxInput = (1 << logicCore.k()) - 1;
        int selectedInput = Math.abs(contextHash) & maxInput;

        boolean mpdtResult = evaluateSample(logicCore, selectedInput);
        double confidence = confidence(logicCore);

        // Evaluate at multiple points for richer analysis
        int trueCount = 0;
        int evalCount = Math.min(8, maxInput + 1);
        for (int i = 0; i < evalCount; i++) {
            int input = Math.abs(contextHash + i) & maxInput;
            if (evaluateSample(logicCore, input)) trueCount++;
        }
        double contextRelevance = (double) trueCount / evalCount;

        return new HybridResult(
                mpdtResult,
                confidence * contextRelevance,
                "MPDT result at context-derived input " + selectedInput + ": " + mpdtResult
                        + " | Confidence: " + String.format("%.2f", confidence)
                        + " | Context relevance: " + String.format("%.2f", contextRelevance)
                        + " | LLM context: " + llmContext.substring(0, Math.min(llmContext.length(), 60))
        );
    }

    // ── Rule parsing ──

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

    /**
     * Builds a real truth table from a boolean rule by parsing the condition
     * and evaluating it for all possible input combinations.
     */
    private TruthTable ruleToTable(BooleanRule rule) {
        String condition = rule.condition();
        Set<Character> varSet = new LinkedHashSet<>();
        for (char c : condition.toCharArray()) {
            if (Character.isLetter(c) && !isOperator(c)) {
                varSet.add(Character.toLowerCase(c));
            }
        }

        if (varSet.isEmpty()) {
            // No variables found — constant output
            boolean constant = condition.contains("true") || condition.contains("yes");
            BitSet bits = new BitSet(2);
            if (constant) bits.set(0, 2);
            return TruthTable.of(1, bits);
        }

        int k = Math.min(8, varSet.size());
        List<Character> vars = varSet.stream().limit(k).toList();

        // Build bitmask: for each input combination, evaluate the condition
        int total = 1 << k;
        BitSet outputBits = new BitSet(total);

        for (int input = 0; input < total; input++) {
            Map<Character, Boolean> assignment = new HashMap<>();
            for (int i = 0; i < k; i++) {
                assignment.put(vars.get(i), (input & (1 << i)) != 0);
            }
            if (evaluateCondition(condition, assignment)) {
                outputBits.set(input);
            }
        }

        return TruthTable.of(k, outputBits);
    }

    /**
     * Evaluates a boolean condition string against a variable assignment.
     * Supports: and, or, not, true, false, variable names.
     */
    private boolean evaluateCondition(String condition, Map<Character, Boolean> assignment) {
        // Normalize: replace words with tokens
        String expr = condition
                .replace(" and ", " & ")
                .replace(" or ", " | ")
                .replace(" not ", " ! ")
                .replace("true", "1")
                .replace("false", "0")
                .replace("yes", "1")
                .replace("no", "0")
                .trim();

        // Replace variable names with their boolean values
        StringBuilder resolved = new StringBuilder();
        for (char c : expr.toCharArray()) {
            if (Character.isLetter(c) && assignment.containsKey(Character.toLowerCase(c))) {
                resolved.append(assignment.get(Character.toLowerCase(c)) ? "1" : "0");
            } else {
                resolved.append(c);
            }
        }

        return evaluateBooleanExpression(resolved.toString().trim());
    }

    /**
     * Evaluates a simple boolean expression with &, |, ! operators and 0/1 values.
     * Uses recursive descent parsing for correct precedence.
     */
    private boolean evaluateBooleanExpression(String expr) {
        expr = expr.replaceAll("\\s+", "");
        if (expr.isEmpty()) return false;
        Boolean result = parseOr(expr, new int[]{0});
        return result != null && result;
    }

    private Boolean parseOr(String expr, int[] pos) {
        Boolean left = parseAnd(expr, pos);
        if (left == null) return null;
        while (pos[0] < expr.length() && expr.charAt(pos[0]) == '|') {
            pos[0]++;
            Boolean right = parseAnd(expr, pos);
            if (right == null) break;
            left = left || right;
        }
        return left;
    }

    private Boolean parseAnd(String expr, int[] pos) {
        Boolean left = parseNot(expr, pos);
        if (left == null) return null;
        while (pos[0] < expr.length() && expr.charAt(pos[0]) == '&') {
            pos[0]++;
            Boolean right = parseNot(expr, pos);
            if (right == null) break;
            left = left && right;
        }
        return left;
    }

    private Boolean parseNot(String expr, int[] pos) {
        if (pos[0] < expr.length() && expr.charAt(pos[0]) == '!') {
            pos[0]++;
            Boolean val = parsePrimary(expr, pos);
            return val != null ? !val : null;
        }
        return parsePrimary(expr, pos);
    }

    private Boolean parsePrimary(String expr, int[] pos) {
        if (pos[0] >= expr.length()) return null;
        char c = expr.charAt(pos[0]);

        if (c == '1') { pos[0]++; return true; }
        if (c == '0') { pos[0]++; return false; }
        if (c == '(') {
            pos[0]++;
            Boolean val = parseOr(expr, pos);
            if (pos[0] < expr.length() && expr.charAt(pos[0]) == ')') pos[0]++;
            return val;
        }
        return null;
    }

    private boolean isOperator(char c) {
        return c == 'a' || c == 'o' || c == 'n'; // and, or, not
    }

    // ── Helpers ──

    private boolean evaluateSample(TruthTable table, int input) {
        return table.evaluate(input);
    }

    private double confidence(TruthTable table) {
        long ones = table.table().cardinality();
        long total = 1L << table.k();
        double ratio = (double) ones / total;
        return Math.max(ratio, 1.0 - ratio);
    }

    // ── Records ──

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
