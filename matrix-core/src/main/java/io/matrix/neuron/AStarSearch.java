package io.matrix.neuron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * DESIGN-44 — A* search (Hart, Nilsson, Raphael 1968).
 * Best-first graph search with f(n) = g(n) + h(n).
 * Complete + optimal given admissible heuristic.
 * Pure function (CONSTITUTION I).
 */
public final class AStarSearch {

    private AStarSearch() {}

    public record Node(int id, double x, double y) {}

    /** Edge in the graph: (from, to, cost). */
    public record Edge(int from, int to, double cost) {}

    public record SearchResult(
            boolean found,
            List<Integer> path,
            int nodesExpanded,
            double totalCost
    ) {}

    /**
     * A* from start to goal using Euclidean heuristic.
     */
    public static SearchResult search(int start, int goal,
                                      Map<Integer, Node> nodes,
                                      List<Edge> edges) {
        if (nodes == null || edges == null) {
            throw new IllegalArgumentException("null");
        }
        if (!nodes.containsKey(start) || !nodes.containsKey(goal)) {
            return new SearchResult(false, List.of(), 0, Double.POSITIVE_INFINITY);
        }
        // Adjacency list
        Map<Integer, List<Edge>> adj = new HashMap<>();
        for (Edge e : edges) {
            adj.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e);
        }
        // Open set (priority queue by f-score)
        Map<Integer, Double> gScore = new HashMap<>();
        Map<Integer, Integer> cameFrom = new HashMap<>();
        Map<Integer, Double> fScore = new HashMap<>();
        PriorityQueue<int[]> open = new PriorityQueue<>((a, b) -> {
            int cmp = Double.compare(fScore.getOrDefault(a[0], Double.POSITIVE_INFINITY),
                    fScore.getOrDefault(b[0], Double.POSITIVE_INFINITY));
            return cmp;
        });
        int[] startArr = {start};
        gScore.put(start, 0.0);
        fScore.put(start, heuristic(nodes.get(start), nodes.get(goal)));
        open.add(startArr);
        int expanded = 0;
        while (!open.isEmpty()) {
            int[] currentArr = open.poll();
            int current = currentArr[0];
            expanded++;
            if (current == goal) {
                List<Integer> path = reconstructPath(cameFrom, current);
                return new SearchResult(true, path, expanded, gScore.get(current));
            }
            for (Edge edge : adj.getOrDefault(current, List.of())) {
                double tentativeG = gScore.get(current) + edge.cost();
                if (tentativeG < gScore.getOrDefault(edge.to(), Double.POSITIVE_INFINITY)) {
                    cameFrom.put(edge.to(), current);
                    gScore.put(edge.to(), tentativeG);
                    fScore.put(edge.to(), tentativeG
                            + heuristic(nodes.get(edge.to()), nodes.get(goal)));
                    open.add(new int[]{edge.to()});
                }
            }
        }
        return new SearchResult(false, List.of(), expanded, Double.POSITIVE_INFINITY);
    }

    private static double heuristic(Node a, Node b) {
        if (a == null || b == null) return 0;
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static List<Integer> reconstructPath(Map<Integer, Integer> cameFrom,
                                                int current) {
        List<Integer> path = new ArrayList<>();
        path.add(current);
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(0, current);
        }
        return path;
    }
}
