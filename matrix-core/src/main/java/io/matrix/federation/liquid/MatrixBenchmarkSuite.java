package io.matrix.federation.liquid;

import java.util.*;

/**
 * W583 — Matrix Benchmark Suite.
 *
 * Runs standard tests for accuracy, latency, and throughput.
 * Compares BIR/HDC vs LLM baselines.
 */
public final class MatrixBenchmarkSuite {

    /**
     * A benchmark test case.
     */
    public record BenchmarkCase(
            String id,
            String description,
            String input,
            String expectedOutput,
            String domain
    ) {}

    /**
     * Result of running a benchmark.
     */
    public record BenchmarkResult(
            String caseId,
            boolean correct,
            long latencyMs,
            double confidence,
            String actualOutput
    ) {}

    /**
     * Summary of a benchmark run.
     */
    public record BenchmarkSummary(
            String modelName,
            int totalCases,
            int correctCases,
            double accuracy,
            double avgLatencyMs,
            double p95LatencyMs,
            double p99LatencyMs,
            long totalDurationMs,
            Map<String, Double> domainAccuracy
    ) {}

    private final List<BenchmarkCase> testCases = new ArrayList<>();

    /**
     * Add a test case.
     */
    public void addCase(BenchmarkCase testCase) {
        testCases.add(testCase);
    }

    /**
     * Add multiple test cases.
     */
    public void addCases(List<BenchmarkCase> testCases) {
        this.testCases.addAll(testCases);
    }

    /**
     * Run benchmark with a model.
     *
     * @param modelName name of the model being tested
     * @param model     the model to test (returns output for input)
     * @return benchmark summary
     */
    public BenchmarkSummary run(String modelName, BenchmarkModel model) {
        List<BenchmarkResult> results = new ArrayList<>();
        long totalStart = System.currentTimeMillis();

        for (BenchmarkCase testCase : testCases) {
            long start = System.currentTimeMillis();

            ModelOutput output = model.predict(testCase.input());
            boolean correct = matchesExpected(output.text(), testCase.expectedOutput());

            long latency = System.currentTimeMillis() - start;
            results.add(new BenchmarkResult(
                    testCase.id(),
                    correct,
                    latency,
                    output.confidence(),
                    output.text()
            ));
        }

        long totalDuration = System.currentTimeMillis() - totalStart;

        return buildSummary(modelName, results, totalDuration);
    }

    /**
     * Model interface for benchmarking.
     */
    public interface BenchmarkModel {
        ModelOutput predict(String input);
    }

    public record ModelOutput(String text, double confidence) {}

    /**
     * Get test cases.
     */
    public List<BenchmarkCase> getTestCases() {
        return Collections.unmodifiableList(testCases);
    }

    /**
     * Get test case count.
     */
    public int getCaseCount() {
        return testCases.size();
    }

    private boolean matchesExpected(String actual, String expected) {
        if (actual == null || expected == null) return false;
        // Normalize: lowercase, trim, remove punctuation
        String normActual = actual.toLowerCase().trim().replaceAll("[^a-z0-9 ]", "");
        String normExpected = expected.toLowerCase().trim().replaceAll("[^a-z0-9 ]", "");
        return normActual.contains(normExpected) || normExpected.contains(normActual);
    }

    private BenchmarkSummary buildSummary(String modelName, List<BenchmarkResult> results, long totalDuration) {
        int total = results.size();
        int correct = (int) results.stream().filter(BenchmarkResult::correct).count();
        double accuracy = total > 0 ? (double) correct / total : 0.0;

        List<Long> latencies = results.stream().map(BenchmarkResult::latencyMs).sorted().toList();
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double p95Latency = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.95));
        double p99Latency = latencies.isEmpty() ? 0 : latencies.get((int) (latencies.size() * 0.99));

        // Domain accuracy
        Map<String, List<BenchmarkResult>> byDomain = new HashMap<>();
        for (BenchmarkResult result : results) {
            BenchmarkCase testCase = testCases.stream()
                    .filter(tc -> tc.id().equals(result.caseId()))
                    .findFirst().orElse(null);
            if (testCase != null) {
                byDomain.computeIfAbsent(testCase.domain(), k -> new ArrayList<>()).add(result);
            }
        }

        Map<String, Double> domainAccuracy = new HashMap<>();
        byDomain.forEach((domain, domainResults) -> {
            int domainCorrect = (int) domainResults.stream().filter(BenchmarkResult::correct).count();
            domainAccuracy.put(domain, (double) domainCorrect / domainResults.size());
        });

        return new BenchmarkSummary(modelName, total, correct, accuracy,
                avgLatency, p95Latency, p99Latency, totalDuration, domainAccuracy);
    }

    /**
     * Generate standard benchmark cases.
     */
    public static MatrixBenchmarkSuite createStandard() {
        MatrixBenchmarkSuite suite = new MatrixBenchmarkSuite();

        // Logic cases
        suite.addCase(new BenchmarkCase("logic-1", "Simple implication", "If A then B. A is true.", "B is true", "logic"));
        suite.addCase(new BenchmarkCase("logic-2", "Modus tollens", "If A then B. B is false.", "A is false", "logic"));
        suite.addCase(new BenchmarkCase("logic-3", "Syllogism", "All men are mortal. Socrates is a man.", "Socrates is mortal", "logic"));

        // Math cases
        suite.addCase(new BenchmarkCase("math-1", "Addition", "What is 2+2?", "4", "math"));
        suite.addCase(new BenchmarkCase("math-2", "Multiplication", "What is 3*7?", "21", "math"));

        // Science cases
        suite.addCase(new BenchmarkCase("science-1", "Physics", "What is the speed of light?", "299792458", "science"));
        suite.addCase(new BenchmarkCase("science-2", "Chemistry", "What is H2O?", "water", "science"));

        return suite;
    }
}
