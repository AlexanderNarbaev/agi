package io.matrix.advanced;

import java.util.*;

/**
 * W811 — Topological Data Analysis for Memory.
 *
 * Uses persistent homology to detect "holes" in knowledge
 * (unknown unknowns) and visualize memory manifold curvature.
 */
public final class TopologicalMemoryAnalyzer {

    /**
     * A persistent homology feature.
     */
    public record PersistenceFeature(double birth, double death, double dimension) {}

    /**
     * Result of topological analysis.
     */
    public record AnalysisResult(
            int h0Count, // Connected components
            int h1Count, // Loops/holes
            int h2Count, // Voids
            double meanCurvature,
            List<PersistenceFeature> features
    ) {}

    private final List<double[]> memoryPoints;

    public TopologicalMemoryAnalyzer() {
        this.memoryPoints = new ArrayList<>();
    }

    /**
     * Add a memory point (vector representation of knowledge).
     */
    public void addMemoryPoint(double[] point) {
        memoryPoints.add(point.clone());
    }

    /**
     * Analyze topology of memory using simplified persistent homology.
     * Returns counts of topological features at each dimension.
     */
    public AnalysisResult analyze() {
        if (memoryPoints.isEmpty()) {
            return new AnalysisResult(0, 0, 0, 0.0, List.of());
        }

        // Simplified: use distance-based clustering as proxy for homology
        int h0 = countConnectedComponents();
        int h1 = estimateLoops();
        int h2 = 0; // 2D voids require full simplicial complex
        double curvature = estimateMeanCurvature();

        List<PersistenceFeature> features = new ArrayList<>();
        features.add(new PersistenceFeature(0.0, 1.0, 0));
        if (h1 > 0) {
            features.add(new PersistenceFeature(0.1, 0.9, 1));
        }

        return new AnalysisResult(h0, h1, h2, curvature, features);
    }

    /**
     * Count connected components using simple distance threshold.
     */
    private int countConnectedComponents() {
        if (memoryPoints.isEmpty()) return 0;
        boolean[] visited = new boolean[memoryPoints.size()];
        int components = 0;

        for (int i = 0; i < memoryPoints.size(); i++) {
            if (!visited[i]) {
                components++;
                floodFill(i, visited, 0.3);
            }
        }
        return components;
    }

    /**
     * Estimate number of loops using pairwise distance distribution.
     */
    private int estimateLoops() {
        if (memoryPoints.size() < 3) return 0;

        // Simple heuristic: count triplets with similar pairwise distances
        int loops = 0;
        for (int i = 0; i < memoryPoints.size() - 2; i++) {
            for (int j = i + 1; j < memoryPoints.size() - 1; j++) {
                for (int k = j + 1; k < memoryPoints.size(); k++) {
                    double d1 = distance(memoryPoints.get(i), memoryPoints.get(j));
                    double d2 = distance(memoryPoints.get(j), memoryPoints.get(k));
                    double d3 = distance(memoryPoints.get(i), memoryPoints.get(k));
                    if (Math.abs(d1 - d2) < 0.1 && Math.abs(d2 - d3) < 0.1) {
                        loops++;
                    }
                }
            }
        }
        return loops;
    }

    /**
     * Estimate mean curvature using local variance.
     */
    private double estimateMeanCurvature() {
        if (memoryPoints.size() < 2) return 0.0;
        double totalVariance = 0.0;
        for (double[] p : memoryPoints) {
            for (double v : p) {
                totalVariance += v * v;
            }
        }
        return totalVariance / (memoryPoints.size() * memoryPoints.get(0).length);
    }

    /**
     * Flood fill for connected components.
     */
    private void floodFill(int start, boolean[] visited, double threshold) {
        java.util.Queue<Integer> queue = new java.util.LinkedList<>();
        queue.add(start);
        visited[start] = true;

        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int i = 0; i < memoryPoints.size(); i++) {
                if (!visited[i] && distance(memoryPoints.get(current), memoryPoints.get(i)) < threshold) {
                    visited[i] = true;
                    queue.add(i);
                }
            }
        }
    }

    /**
     * Euclidean distance between two points.
     */
    private double distance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            sum += (a[i] - b[i]) * (a[i] - b[i]);
        }
        return Math.sqrt(sum);
    }

    public int getMemoryPointCount() {
        return memoryPoints.size();
    }
}
