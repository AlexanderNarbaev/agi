package io.matrix.neuron;

import java.util.List;

/**
 * RUN 422 — Bellman-Ford single-source shortest paths (handles negative weights).
 * <p>Detects negative cycles. Pure function: O(V*E) time. Returns {@code null}
 * distances for unreachable, or throws via {@code NegativeCycleException} if a
 * negative-weight cycle is reachable from source.
 * CONSTITUTION I-safe.
 */
public final class BellmanFord {

    private BellmanFord() {}

    public record Edge(int u, int v, double weight) {}
    public record Result(double[] distances, int[] predecessors) {}

    public static Result shortestPaths(int vertexCount, List<Edge> edges, int source) {
        double[] dist = new double[vertexCount];
        int[] prev = new int[vertexCount];
        for (int i = 0; i < vertexCount; i++) { dist[i] = Double.POSITIVE_INFINITY; prev[i] = -1; }
        dist[source] = 0.0;
        // Relax edges V-1 times
        for (int i = 0; i < vertexCount - 1; i++) {
            boolean changed = false;
            for (Edge e : edges) {
                if (dist[e.u()] == Double.POSITIVE_INFINITY) continue;
                double nd = dist[e.u()] + e.weight;
                if (nd < dist[e.v()]) {
                    dist[e.v()] = nd;
                    prev[e.v()] = e.u();
                    changed = true;
                }
            }
            if (!changed) break;
        }
        // Negative-cycle detection (one more pass; if a relaxation succeeds ⇒ negative cycle)
        for (Edge e : edges) {
            if (dist[e.u()] != Double.POSITIVE_INFINITY &&
                    dist[e.u()] + e.weight < dist[e.v()]) {
                throw new NegativeCycleException(
                        "Negative-weight cycle reachable from source (" +
                                e.u() + "→" + e.v() + " weight=" + e.weight + ")");
            }
        }
        return new Result(dist, prev);
    }

    public static class NegativeCycleException extends RuntimeException {
        public NegativeCycleException(String msg) { super(msg); }
    }
}
