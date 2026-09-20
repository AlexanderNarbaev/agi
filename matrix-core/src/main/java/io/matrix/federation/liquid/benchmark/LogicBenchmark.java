package io.matrix.federation.liquid.benchmark;

import java.util.*;

/**
 * W601 — Logic & Reasoning Benchmark.
 *
 * Tests BIR/HDC vs baselines on synthetic logic puzzles.
 * Inspired by LogiQA/ReClor datasets.
 */
public final class LogicBenchmark {

    /**
     * A logic puzzle with premises and a question.
     */
    public record LogicPuzzle(
            String id,
            String category,
            List<String> premises,
            String question,
            List<String> options,
            int correctIndex,
            String explanation
    ) {}

    /**
     * Result of solving a puzzle.
     */
    public record PuzzleResult(
            String puzzleId,
            int selectedIndex,
            boolean correct,
            long latencyMs,
            double confidence,
            String reasoning
    ) {}

    /**
     * Summary of benchmark run.
     */
    public record LogicBenchmarkSummary(
            String modelName,
            int totalPuzzles,
            int correct,
            double accuracy,
            double avgLatencyMs,
            Map<String, Double> categoryAccuracy,
            Map<String, Integer> categoryCounts
    ) {}

    private final List<LogicPuzzle> puzzles = new ArrayList<>();

    /**
     * Generate synthetic logic puzzles.
     */
    public static List<LogicPuzzle> generateSyntheticPuzzles(int count) {
        List<LogicPuzzle> puzzles = new ArrayList<>();
        Random rng = new Random(42);

        // Syllogisms
        for (int i = 0; i < count / 4; i++) {
            puzzles.add(generateSyllogism(i, rng));
        }

        // Modus Ponens/Tollens
        for (int i = 0; i < count / 4; i++) {
            puzzles.add(generateModusPonens(i + count / 4, rng));
        }

        // Conditional chains
        for (int i = 0; i < count / 4; i++) {
            puzzles.add(generateConditionalChain(i + count / 2, rng));
        }

        // Disjunctive syllogism
        for (int i = 0; i < count / 4; i++) {
            puzzles.add(generateDisjunctive(i + 3 * count / 4, rng));
        }

        return puzzles;
    }

    private static LogicPuzzle generateSyllogism(int id, Random rng) {
        String[] animals = {"cats", "dogs", "birds", "fish", "mice"};
        String[] qualities = {"fast", "large", "noisy", "friendly", "wild"};

        String a = animals[rng.nextInt(animals.length)];
        String b = animals[rng.nextInt(animals.length)];
        String q = qualities[rng.nextInt(qualities.length)];

        while (a.equals(b)) b = animals[rng.nextInt(animals.length)];

        List<String> premises = List.of(
                "All " + a + " are " + b + ".",
                "All " + b + " are " + q + "."
        );

        String question = "Are all " + a + " " + q + "?";
        List<String> options = List.of("Yes", "No", "Cannot be determined");

        return new LogicPuzzle(
                "syl-" + id, "syllogism", premises, question, options, 0,
                "All " + a + " are " + b + ", and all " + b + " are " + q + ", so all " + a + " are " + q + "."
        );
    }

    private static LogicPuzzle generateModusPonens(int id, Random rng) {
        String[] actions = {"study hard", "exercise daily", "sleep well", "eat healthy"};
        String[] outcomes = {"succeed", "be healthy", "be rested", "be strong"};

        String action = actions[rng.nextInt(actions.length)];
        String outcome = outcomes[rng.nextInt(outcomes.length)];

        List<String> premises = List.of(
                "If someone " + action + ", they will " + outcome + ".",
                "Alex " + action + "."
        );

        String question = "Will Alex " + outcome + "?";
        List<String> options = List.of("Yes", "No", "Cannot be determined");

        return new LogicPuzzle(
                "mp-" + id, "modus_ponens", premises, question, options, 0,
                "Modus ponens: If P then Q, P is true, therefore Q."
        );
    }

    private static LogicPuzzle generateConditionalChain(int id, Random rng) {
        String[] items = {"A", "B", "C", "D", "E"};
        int len = 3 + rng.nextInt(2);
        List<String> chain = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            chain.add(items[i]);
        }

        List<String> premises = new ArrayList<>();
        for (int i = 0; i < chain.size() - 1; i++) {
            premises.add("If " + chain.get(i) + " then " + chain.get(i + 1) + ".");
        }
        premises.add(chain.get(0) + " is true.");

        String question = "Is " + chain.get(chain.size() - 1) + " true?";
        List<String> options = List.of("Yes", "No", "Cannot be determined");

        return new LogicPuzzle(
                "cc-" + id, "conditional_chain", premises, question, options, 0,
                "Chain: " + String.join(" → ", chain)
        );
    }

    private static LogicPuzzle generateDisjunctive(int id, Random rng) {
        String[] items = {"X", "Y", "Z"};
        String a = items[rng.nextInt(items.length)];
        String b;
        do { b = items[rng.nextInt(items.length)]; } while (b.equals(a));

        List<String> premises = List.of(
                "Either " + a + " or " + b + " is true.",
                a + " is false."
        );

        String question = "Is " + b + " true?";
        List<String> options = List.of("Yes", "No", "Cannot be determined");

        return new LogicPuzzle(
                "ds-" + id, "disjunctive", premises, question, options, 0,
                "Disjunctive syllogism: A or B, not A, therefore B."
        );
    }

    /**
     * Add puzzles to benchmark.
     */
    public void addPuzzles(List<LogicPuzzle> puzzles) {
        this.puzzles.addAll(puzzles);
    }

    /**
     * Run benchmark with a solver.
     */
    public LogicBenchmarkSummary run(String modelName, LogicSolver solver) {
        List<PuzzleResult> results = new ArrayList<>();
        Map<String, List<PuzzleResult>> byCategory = new HashMap<>();

        for (LogicPuzzle puzzle : puzzles) {
            long start = System.currentTimeMillis();
            SolverOutput output = solver.solve(puzzle);
            long latency = System.currentTimeMillis() - start;

            boolean correct = output.selectedIndex() == puzzle.correctIndex();
            PuzzleResult result = new PuzzleResult(
                    puzzle.id(), output.selectedIndex(), correct, latency,
                    output.confidence(), output.reasoning()
            );
            results.add(result);
            byCategory.computeIfAbsent(puzzle.category(), k -> new ArrayList<>()).add(result);
        }

        int correctCount = (int) results.stream().filter(PuzzleResult::correct).count();
        double accuracy = (double) correctCount / results.size();
        double avgLatency = results.stream().mapToLong(PuzzleResult::latencyMs).average().orElse(0);

        Map<String, Double> categoryAccuracy = new HashMap<>();
        Map<String, Integer> categoryCounts = new HashMap<>();
        for (var entry : byCategory.entrySet()) {
            long catCorrect = entry.getValue().stream().filter(PuzzleResult::correct).count();
            categoryAccuracy.put(entry.getKey(), (double) catCorrect / entry.getValue().size());
            categoryCounts.put(entry.getKey(), entry.getValue().size());
        }

        return new LogicBenchmarkSummary(modelName, puzzles.size(), correctCount, accuracy, avgLatency, categoryAccuracy, categoryCounts);
    }

    /**
     * Solver interface.
     */
    public interface LogicSolver {
        SolverOutput solve(LogicPuzzle puzzle);
    }

    /**
     * Solver output.
     */
    public record SolverOutput(int selectedIndex, double confidence, String reasoning) {}

    /**
     * Random baseline solver.
     */
    public static LogicSolver randomSolver() {
        Random rng = new Random(42);
        return puzzle -> new SolverOutput(rng.nextInt(puzzle.options().size()), 0.25, "Random guess");
    }

    /**
     * Heuristic solver (rule-based).
     */
    public static LogicSolver heuristicSolver() {
        return puzzle -> {
            String question = puzzle.question().toLowerCase();
            List<String> premises = puzzle.premises();

            boolean hasAllPattern = premises.stream().anyMatch(p -> p.toLowerCase().startsWith("all "));
            boolean hasIfPattern = premises.stream().anyMatch(p -> p.toLowerCase().startsWith("if "));

            if (hasAllPattern || hasIfPattern) {
                return new SolverOutput(0, 0.7, "Heuristic: detected logical pattern");
            }

            return new SolverOutput(2, 0.4, "Heuristic: no clear pattern");
        };
    }

    /**
     * BIR-based solver.
     */
    public static LogicSolver birSolver() {
        return puzzle -> {
            String combined = String.join(" ", puzzle.premises()) + " " + puzzle.question();
            boolean hasNegation = combined.contains("not") || combined.contains("false");
            boolean hasAffirmation = combined.contains("true") || combined.contains("is true");

            if (hasAffirmation && !hasNegation) {
                return new SolverOutput(0, 0.85, "BIR: affirmative pattern detected");
            } else if (hasNegation) {
                return new SolverOutput(1, 0.8, "BIR: negation pattern detected");
            }
            return new SolverOutput(2, 0.5, "BIR: ambiguous");
        };
    }
}
