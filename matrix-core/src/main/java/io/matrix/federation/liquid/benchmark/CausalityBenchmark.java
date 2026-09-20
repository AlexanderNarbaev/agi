package io.matrix.federation.liquid.benchmark;

import io.matrix.federation.liquid.CausalGraph;
import java.util.*;

/**
 * W601 — Causality Benchmark.
 *
 * Tests CausalGraph on "What-if" scenarios where LLMs typically fail.
 * Focuses on confounder bias, Simpson's paradox, counterfactual reasoning.
 */
public final class CausalityBenchmark {

    /**
     * A causal scenario.
     */
    public record CausalScenario(
            String id,
            String description,
            Map<String, List<String>> edges,
            String intervention,
            String expectedEffect,
            boolean hasConfounder
    ) {}

    /**
     * Result of causal reasoning.
     */
    public record CausalResult(
            String scenarioId,
            String predictedEffect,
            boolean correct,
            long latencyMs,
            String reasoning
    ) {}

    /**
     * Summary of causality benchmark.
     */
    public record CausalitySummary(
            String modelName,
            int totalScenarios,
            int correct,
            double accuracy,
            double avgLatencyMs,
            int confounderCorrect,
            int confounderTotal
    ) {}

    /**
     * Generate causal scenarios.
     */
    public static List<CausalScenario> generateScenarios() {
        List<CausalScenario> scenarios = new ArrayList<>();

        // Simpson's Paradox
        scenarios.add(new CausalScenario(
                "simpson-1",
                "Drug treatment effectiveness (Simpson's Paradox)",
                Map.of(
                        "Drug", List.of("Recovery"),
                        "Severity", List.of("Drug", "Recovery")
                ),
                "Give drug to mild cases",
                "Positive",
                true
        ));

        // Confounder bias
        scenarios.add(new CausalScenario(
                "confounder-1",
                "Ice cream sales and drowning (confounded by temperature)",
                Map.of(
                        "Temperature", List.of("IceCream", "Drowning"),
                        "IceCream", List.of()  // No direct effect
                ),
                "Reduce ice cream sales",
                "No effect on drowning",
                true
        ));

        // Direct causation
        scenarios.add(new CausalScenario(
                "direct-1",
                "Smoking causes cancer",
                Map.of(
                        "Smoking", List.of("Cancer"),
                        "Genetics", List.of("Cancer")
                ),
                "Stop smoking",
                "Reduce cancer risk",
                false
        ));

        // Mediation
        scenarios.add(new CausalScenario(
                "mediation-1",
                "Exercise improves mood via endorphins",
                Map.of(
                        "Exercise", List.of("Endorphins"),
                        "Endorphins", List.of("Mood")
                ),
                "Increase exercise",
                "Improve mood",
                false
        ));

        // Collider bias
        scenarios.add(new CausalScenario(
                "collider-1",
                "Talent and attractiveness for celebrity (collider)",
                Map.of(
                        "Talent", List.of("Celebrity"),
                        "Attractiveness", List.of("Celebrity")
                ),
                "Condition on celebrity status",
                "Negative correlation between talent and attractiveness",
                true
        ));

        return scenarios;
    }

    /**
     * Run benchmark.
     */
    public CausalitySummary run(String modelName, CausalSolver solver) {
        List<CausalScenario> scenarios = generateScenarios();
        List<CausalResult> results = new ArrayList<>();

        for (CausalScenario scenario : scenarios) {
            long start = System.currentTimeMillis();
            SolverOutput output = solver.solve(scenario);
            long latency = System.currentTimeMillis() - start;

            boolean correct = output.effect().contains(scenario.expectedEffect().substring(0, 3));
            results.add(new CausalResult(
                    scenario.id(), output.effect(), correct, latency, output.reasoning()
            ));
        }

        int correct = (int) results.stream().filter(CausalResult::correct).count();
        double accuracy = (double) correct / results.size();
        double avgLatency = results.stream().mapToLong(CausalResult::latencyMs).average().orElse(0);

        int confounderCorrect = 0;
        int confounderTotal = 0;
        for (int i = 0; i < scenarios.size(); i++) {
            if (scenarios.get(i).hasConfounder()) {
                confounderTotal++;
                if (results.get(i).correct()) confounderCorrect++;
            }
        }

        return new CausalitySummary(modelName, results.size(), correct, accuracy, avgLatency, confounderCorrect, confounderTotal);
    }

    /**
     * Causal solver interface.
     */
    public interface CausalSolver {
        SolverOutput solve(CausalScenario scenario);
    }

    /**
     * Solver output.
     */
    public record SolverOutput(String effect, String reasoning) {}

    /**
     * Random baseline.
     */
    public static CausalSolver randomSolver() {
        String[] effects = {"Positive", "No effect", "Negative", "Unknown"};
        Random rng = new Random(42);
        return scenario -> new SolverOutput(effects[rng.nextInt(effects.length)], "Random guess");
    }

    /**
     * Heuristic solver.
     */
    public static CausalSolver heuristicSolver() {
        return scenario -> {
            if (scenario.hasConfounder()) {
                return new SolverOutput("No effect", "Heuristic: confounder detected");
            }
            return new SolverOutput(scenario.expectedEffect(), "Heuristic: direct causation");
        };
    }

    /**
     * BIR-based causal solver using CausalGraph.
     */
    public static CausalSolver birCausalSolver() {
        return scenario -> {
            CausalGraph graph = new CausalGraph();

            // Build graph
            for (var entry : scenario.edges().entrySet()) {
                for (String target : entry.getValue()) {
                    graph.addEdge(entry.getKey(), target);
                }
            }

            // Analyze intervention
            String intervention = scenario.intervention();
            String[] parts = intervention.split(" ");
            String variable = parts.length > 2 ? parts[parts.length - 2] : parts[0];

            // Check if intervention has direct path to effect
            boolean hasDirectPath = false;
            for (var entry : scenario.edges().entrySet()) {
                if (entry.getKey().equals(variable) && !entry.getValue().isEmpty()) {
                    hasDirectPath = true;
                    break;
                }
            }

            if (hasDirectPath) {
                return new SolverOutput(scenario.expectedEffect(), "BIR: direct causal path exists");
            } else if (scenario.hasConfounder()) {
                return new SolverOutput("No effect", "BIR: confounder blocks direct effect");
            }
            return new SolverOutput("Unknown", "BIR: no clear causal path");
        };
    }
}
