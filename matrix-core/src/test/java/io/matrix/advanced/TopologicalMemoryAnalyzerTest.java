package io.matrix.advanced;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TopologicalMemoryAnalyzerTest {

    @Test
    void testCreateAnalyzer() {
        TopologicalMemoryAnalyzer analyzer = new TopologicalMemoryAnalyzer();
        assertNotNull(analyzer);
        assertEquals(0, analyzer.getMemoryPointCount());
    }

    @Test
    void testAddMemoryPoint() {
        TopologicalMemoryAnalyzer analyzer = new TopologicalMemoryAnalyzer();
        analyzer.addMemoryPoint(new double[]{0.1, 0.2, 0.3});
        assertEquals(1, analyzer.getMemoryPointCount());
    }

    @Test
    void testAnalyzeEmptyMemory() {
        TopologicalMemoryAnalyzer analyzer = new TopologicalMemoryAnalyzer();
        TopologicalMemoryAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(0, result.h0Count());
        assertEquals(0, result.h1Count());
        assertEquals(0, result.h2Count());
    }

    @Test
    void testAnalyzeWithPoints() {
        TopologicalMemoryAnalyzer analyzer = new TopologicalMemoryAnalyzer();
        analyzer.addMemoryPoint(new double[]{0.1, 0.2});
        analyzer.addMemoryPoint(new double[]{0.15, 0.25});
        analyzer.addMemoryPoint(new double[]{0.8, 0.9});

        TopologicalMemoryAnalyzer.AnalysisResult result = analyzer.analyze();
        assertTrue(result.h0Count() >= 1);
        assertTrue(result.meanCurvature() >= 0);
    }

    @Test
    void testAnalyzeDetectsClusters() {
        TopologicalMemoryAnalyzer analyzer = new TopologicalMemoryAnalyzer();

        // Cluster 1 (close points)
        analyzer.addMemoryPoint(new double[]{0.1, 0.1});
        analyzer.addMemoryPoint(new double[]{0.11, 0.11});
        analyzer.addMemoryPoint(new double[]{0.12, 0.12});

        // Cluster 2 (far points)
        analyzer.addMemoryPoint(new double[]{0.9, 0.9});
        analyzer.addMemoryPoint(new double[]{0.91, 0.91});

        TopologicalMemoryAnalyzer.AnalysisResult result = analyzer.analyze();
        assertEquals(2, result.h0Count(), "Should detect 2 clusters");
    }

    @Test
    void testCurvatureEstimation() {
        TopologicalMemoryAnalyzer analyzer = new TopologicalMemoryAnalyzer();
        analyzer.addMemoryPoint(new double[]{0.5, 0.5});
        TopologicalMemoryAnalyzer.AnalysisResult result = analyzer.analyze();
        assertTrue(result.meanCurvature() >= 0);
    }

    @Test
    void testAnalyzeWithLoops() {
        TopologicalMemoryAnalyzer analyzer = new TopologicalMemoryAnalyzer();
        // Equilateral triangle points
        analyzer.addMemoryPoint(new double[]{0.0, 0.0});
        analyzer.addMemoryPoint(new double[]{1.0, 0.0});
        analyzer.addMemoryPoint(new double[]{0.5, 0.866});

        TopologicalMemoryAnalyzer.AnalysisResult result = analyzer.analyze();
        assertNotNull(result.features());
        assertFalse(result.features().isEmpty());
    }
}

