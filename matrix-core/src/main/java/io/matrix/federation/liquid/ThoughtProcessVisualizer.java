package io.matrix.federation.liquid;

import java.util.*;

/**
 * W589 — Thought Process Visualizer.
 *
 * Shows active BIR rules, HDC matches, MCTS tree in real-time.
 * "Explain this decision" button.
 */
public final class ThoughtProcessVisualizer {

    /**
     * A thought step in the reasoning process.
     */
    public record ThoughtStep(
            String type,        // BIR, HDC, MCTS, CASUAL, ROUTE
            String description,
            double confidence,
            Map<String, String> details
    ) {}

    private final List<ThoughtStep> steps = new ArrayList<>();

    /**
     * Record a thought step.
     */
    public void recordStep(String type, String description, double confidence, Map<String, String> details) {
        steps.add(new ThoughtStep(type, description, confidence, details));
    }

    /**
     * Record a BIR rule activation.
     */
    public void recordBirRule(String ruleId, String premise, String conclusion, double confidence) {
        recordStep("BIR", "Rule: " + premise + " → " + conclusion, confidence,
                Map.of("ruleId", ruleId, "premise", premise, "conclusion", conclusion));
    }

    /**
     * Record an HDC pattern match.
     */
    public void recordHdcMatch(String patternId, double similarity) {
        recordStep("HDC", "Pattern match: " + patternId, similarity,
                Map.of("patternId", patternId, "similarity", String.format("%.4f", similarity)));
    }

    /**
     * Record an MCTS decision.
     */
    public void recordMctsDecision(String action, double expectedReward, int simulations) {
        recordStep("MCTS", "Decision: " + action, expectedReward,
                Map.of("action", action, "simulations", String.valueOf(simulations)));
    }

    /**
     * Record a causal reasoning step.
     */
    public void recordCausalReasoning(String cause, String effect, double confidence) {
        recordStep("CAUSAL", cause + " → " + effect, confidence,
                Map.of("cause", cause, "effect", effect));
    }

    /**
     * Record a routing decision.
     */
    public void recordRouting(CognitiveRouter.CognitiveMode mode, String reason) {
        recordStep("ROUTE", "Mode: " + mode.name(), 1.0,
                Map.of("mode", mode.name(), "reason", reason));
    }

    /**
     * Generate JSON representation.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder("{\"steps\":[");
        boolean first = true;
        for (ThoughtStep step : steps) {
            if (!first) sb.append(",");
            sb.append("{\"type\":\"").append(step.type()).append("\"")
              .append(",\"description\":\"").append(step.description()).append("\"")
              .append(",\"confidence\":").append(step.confidence())
              .append(",\"details\":{");
            boolean firstDetail = true;
            for (var entry : step.details().entrySet()) {
                if (!firstDetail) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
                firstDetail = false;
            }
            sb.append("}}");
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Generate HTML visualization.
     */
    public String toHtml() {
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html><html><head><title>MATRIX Thought Process</title>");
        sb.append("<style>");
        sb.append("body{font-family:monospace;background:#0a0a0a;color:#00ff88;padding:20px;}");
        sb.append("h1{color:#00ffff;}");
        sb.append(".step{background:#111;border-left:3px solid #00ff88;padding:10px;margin:5px 0;}");
        sb.append(".step.BIR{border-color:#00ff88;}");
        sb.append(".step.HDC{border-color:#00aaff;}");
        sb.append(".step.MCTS{border-color:#ffaa00;}");
        sb.append(".step.CAUSAL{border-color:#ff6600;}");
        sb.append(".step.ROUTE{border-color:#ff00ff;}");
        sb.append(".type{font-weight:bold;color:#00ffff;}");
        sb.append(".confidence{color:#ffaa00;}");
        sb.append(".details{font-size:12px;color:#888;}");
        sb.append("</style></head><body>");

        sb.append("<h1>MATRIX Thought Process</h1>");
        sb.append("<div id='steps'>");

        for (ThoughtStep step : steps) {
            sb.append("<div class='step ").append(step.type()).append("'>");
            sb.append("<span class='type'>[").append(step.type()).append("]</span> ");
            sb.append(step.description());
            sb.append(" <span class='confidence'>(").append(String.format("%.2f", step.confidence())).append(")</span>");
            if (!step.details().isEmpty()) {
                sb.append("<div class='details'>");
                for (var entry : step.details().entrySet()) {
                    sb.append(entry.getKey()).append(": ").append(entry.getValue()).append(" ");
                }
                sb.append("</div>");
            }
            sb.append("</div>");
        }

        sb.append("</div>");

        // Explain button
        sb.append("<button onclick='explain()'>Explain Decision</button>");
        sb.append("<div id='explanation'></div>");

        sb.append("<script>");
        sb.append("function explain(){");
        sb.append("const steps=").append(toJson()).append(".steps;");
        sb.append("let html='<h2>Decision Explanation</h2><ul>';");
        sb.append("steps.forEach(s=>{html+='<li><b>'+s.type+'</b>: '+s.description+' (confidence: '+s.confidence.toFixed(2)+')</li>';});");
        sb.append("html+='</ul>';");
        sb.append("document.getElementById('explanation').innerHTML=html;");
        sb.append("}");
        sb.append("</script>");

        sb.append("</body></html>");

        return sb.toString();
    }

    /**
     * Get all steps.
     */
    public List<ThoughtStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    /**
     * Clear all steps.
     */
    public void clear() {
        steps.clear();
    }

    /**
     * Get step count.
     */
    public int getStepCount() {
        return steps.size();
    }
}
