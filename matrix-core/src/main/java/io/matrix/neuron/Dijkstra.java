package io.matrix.neuron;

import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * RUN 420 — Dijkstra single-source shortest paths (non-negative weights).
 * <p>Pure function. Returns the cost to every node + the predecessor chain.
 * CONSTITUTION I-safe: deterministic tie-breaking via cheaper edge first.
 */
public final class Dijkstra {

    private Dijkstra() {}

    /**
     * Edge description for adjacent-list input.
     */
    public record Edge(int to, double weight) {}

    /**
     * Result of single-source Dijkstra.
     *
     * @param distances   cheapest known distance to each node
     * @param predecessors -1 if unreachable; previous node on shortest path otherwise
     */
    public record Result(double[] distances, int[] predecessors) {}

    /**
     * Single-source Dijkstra.
     *
     * @param adjacency    outgoing edges per node (sparse)
     * @param source       start node
     * @param unreachableSentinel cost to use for unreachable nodes (default = {@link Double#POSITIVE_INFINITY})
     */
    public static Result shortestPaths(java.util.List<? extends java.util.List<Edge>> adjacency,
                                       int source, double unreachableSentinel) {
        int n = adjacency.size();
        double[] dist = new double[n];
        int[] prev = new int[n];
        boolean[] visited = new boolean[n];
        Arrays.fill(dist, unreachableSentinel == 0 ? Double.POSITIVE_INFINITY : unreachableSentinel);
        Arrays.fill(prev, -1);
        if (unreachableSentinel == 0) Arrays.fill(dist, Double.POSITIVE_INFINITY);
        if (source < 0 || source >= n) throw new IllegalArgumentException("source");
        dist[source] = 0.0;

        PriorityQueue<int[]> pq = new PriorityQueue<>(
                (a, b) -> Double.compare(dist[a[0]] + a[1] * 1e-12, dist[b[0]] + b[1] * 1e-12));
        pq.offer(new int[]{source, 0});

        while (!pq.isEmpty()) {
            int[] head = pq.poll();
            int u = head[0];
            if (visited[u]) continue;
            visited[u] = true;
            for (Edge e : adjacency.get(u)) {
                if (dist[u] == Double.POSITIVE_INFINITY) continue;
                double alt = dist[u] + e.weight;
                if (alt < dist[e.to]) {
                    dist[e.to] = alt;
                    prev[e.to] = u;
                    pq.offer(new int[]{e.to, 0});
                }
            }
        }
        return new Result(dist, prev);
    }

    /** Reconstruct path from {@link Result}. Empty list if unreachable. */
    public static int[] reconstructPath(Result r, int target) {
        if (r.distances()[target] == Double.POSITIVE_INFINITY) return new int[0];
        java.util.ArrayList<Integer> path = new java.util.ArrayList<>();
        for (int at = target; at != -1; at = r.predecessors()[at]) path.add(0, at);
        int[] out = new int[path.size()];
        for (int i = 0; i < out.length; i++) out[i] = path.get(i);
        return out;
    }
}
