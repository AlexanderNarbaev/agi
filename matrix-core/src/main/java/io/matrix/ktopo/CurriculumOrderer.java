package io.matrix.ktopo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Orders knowledge-graph components dense→periphery to feed the curriculum
 * traversal of SPEC-000 (SPEC-003 FR-6).
 *
 * <p>Each connected component is scored by its edge density
 * {@code |E_c| / C(|V_c|,2)}; components are returned from densest to most
 * peripheral. Ties are broken lexicographically by component id (the smallest
 * vertex name). Deterministic throughout.
 */
public final class CurriculumOrderer {

    private CurriculumOrderer() {}

    /**
     * @param graph undirected graph (vertices are indices {@code 0..n-1})
     * @param names vertex names aligned with graph indices
     * @return vertices grouped per connected component, components sorted
     *         dense-first; vertices inside a component sorted lexicographically
     */
    public static List<List<String>> order(Graph graph, List<String> names) {
        int n = graph.n();
        if (names.size() != n) {
            throw new IllegalArgumentException("names must align with graph vertices");
        }

        // Union-find over undirected edges.
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int e = 0; e < graph.edgeCount(); e++) {
            union(parent, graph.u()[e], graph.v()[e]);
        }

        Map<Integer, TreeSet<String>> members = new HashMap<>();
        Map<Integer, int[]> stats = new HashMap<>(); // root -> [vertexCount, edgeCount]
        for (int i = 0; i < n; i++) {
            members.computeIfAbsent(find(parent, i), k -> new TreeSet<>()).add(names.get(i));
        }
        for (int i = 0; i < n; i++) {
            stats.computeIfAbsent(find(parent, i), k -> new int[2])[0]++;
        }
        for (int e = 0; e < graph.edgeCount(); e++) {
            stats.get(find(parent, graph.u()[e]))[1]++;
        }

        record Scored(double density, String id, List<String> vertices) {}
        List<Scored> scored = new ArrayList<>();
        for (var entry : members.entrySet()) {
            int root = entry.getKey();
            List<String> vertices = List.copyOf(entry.getValue());
            int m = vertices.size();
            long possiblePairs = (long) m * (m - 1) / 2;
            double density = possiblePairs == 0 ? 0.0 : stats.get(root)[1] / (double) possiblePairs;
            scored.add(new Scored(density, vertices.get(0), vertices));
        }
        scored.sort(Comparator.comparingDouble(Scored::density).reversed()
                .thenComparing(Scored::id));
        return scored.stream().map(Scored::vertices).toList();
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) {
            parent[rb] = ra;
        }
    }
}
