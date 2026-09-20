package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W584 — Tests for Comparative Report Generator.
 */
class ComparativeReportGeneratorTest {

    @Test
    void testGenerateReport() {
        ComparativeReportGenerator generator = new ComparativeReportGenerator();

        MatrixBenchmarkSuite.BenchmarkSummary matrix = new MatrixBenchmarkSuite.BenchmarkSummary(
                "MATRIX-BIR", 10, 8, 0.8, 5.0, 10.0, 15.0, 100,
                Map.of("logic", 0.9, "math", 0.7));

        MatrixBenchmarkSuite.BenchmarkSummary baseline = new MatrixBenchmarkSuite.BenchmarkSummary(
                "Qwen-0.5B", 10, 7, 0.7, 50.0, 100.0, 150.0, 1000,
                Map.of("logic", 0.8, "math", 0.6));

        String report = generator.generateReport(matrix, baseline);

        assertTrue(report.contains("MATRIX"));
        assertTrue(report.contains("Baseline"));
        assertTrue(report.contains("Accuracy"));
        assertTrue(report.contains("Energy Estimate"));
    }

    @Test
    void testReportContainsDomainBreakdown() {
        ComparativeReportGenerator generator = new ComparativeReportGenerator();

        MatrixBenchmarkSuite.BenchmarkSummary matrix = new MatrixBenchmarkSuite.BenchmarkSummary(
                "MATRIX", 5, 4, 0.8, 5.0, 10.0, 15.0, 50,
                Map.of("logic", 0.9, "math", 0.7, "science", 0.8));

        MatrixBenchmarkSuite.BenchmarkSummary baseline = new MatrixBenchmarkSuite.BenchmarkSummary(
                "LLM", 5, 3, 0.6, 50.0, 100.0, 150.0, 500,
                Map.of("logic", 0.7, "math", 0.5));

        String report = generator.generateReport(matrix, baseline);

        assertTrue(report.contains("logic"));
        assertTrue(report.contains("math"));
        assertTrue(report.contains("science"));
    }
}
