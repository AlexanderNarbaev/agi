package io.matrix.cognitive.algorithms;

import java.util.List;

/**
 * W44.2 — the shortest-path family's shared input and output.
 *
 * <p>The family has three members — Dijkstra, Bellman-Ford and a bounded uniform-cost search —
 * and they must be comparable: the benchmark asserts that the exact methods agree with each
 * other, and that the bounded method agrees with them when it finishes inside its budget. They
 * can only be compared if they speak the same shape, so the shape lives here rather than in any
 * one of them.</p>
 *
 * <p>The output carries a {@code complete} flag. It is not decoration: the bounded search can
 * return before it has proven anything, and a result that does not say so would be read as an
 * answer. The flag is the difference between "the shortest path is 7" and "nothing cheaper than
 * 7 was found before the budget ran out".</p>
 */
public record ShortestPathProblem(int vertexCount, List<Edge> edges, int source) {

    /**
     * A directed edge.
     *
     * @param from   the tail vertex
     * @param to     the head vertex
     * @param weight the cost of the edge
     */
    public record Edge(int from, int to, double weight) {
    }

    /**
     * The result of a shortest-path computation.
     *
     * @param distances    the cost to each vertex; {@link Double#POSITIVE_INFINITY} when unreachable
     * @param predecessors the previous vertex on the path, or -1 when there is none
     * @param complete     false when the search stopped before it could prove anything
     * @param note         what the result is and is not
     */
    public record Outcome(double[] distances, int[] predecessors, boolean complete, String note) {

        public Outcome {
            distances = distances == null ? new double[0] : distances.clone();
            predecessors = predecessors == null ? new int[0] : predecessors.clone();
            note = note == null ? "" : note;
        }

        /** The cost to a vertex, or infinity when it was not reached. */
        public double distanceTo(int vertex) {
            return vertex < 0 || vertex >= distances.length ? Double.POSITIVE_INFINITY : distances[vertex];
        }

        /**
         * The path to a vertex, reconstructed from the predecessor chain.
         *
         * @return the vertices from the source to the target, or an empty array when unreachable
         */
        public int[] pathTo(int target) {
            if (target < 0 || target >= predecessors.length) {
                return new int[0];
            }
            if (predecessors[target] < 0 && target != 0) {
                return new int[0];
            }
            int length = 0;
            for (int at = target; at != -1; at = predecessors[at]) {
                length++;
            }
            int[] path = new int[length];
            int at = target;
            for (int i = length - 1; i >= 0; i--) {
                path[i] = at;
                at = predecessors[at];
            }
            return path;
        }
    }

    public ShortestPathProblem {
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    /** The edges leaving a vertex, in the adjacency form Dijkstra takes. */
    public List<List<io.matrix.neuron.Dijkstra.Edge>> adjacency() {
        List<List<io.matrix.neuron.Dijkstra.Edge>> adjacency = new java.util.ArrayList<>(vertexCount);
        for (int i = 0; i < vertexCount; i++) {
            adjacency.add(new java.util.ArrayList<>());
        }
        for (Edge edge : edges) {
            if (edge.from() >= 0 && edge.from() < vertexCount) {
                adjacency.get(edge.from()).add(new io.matrix.neuron.Dijkstra.Edge(edge.to(), edge.weight()));
            }
        }
        return adjacency;
    }

    /** The edges in the flat form Bellman-Ford takes. */
    public List<io.matrix.neuron.BellmanFord.Edge> flatEdges() {
        List<io.matrix.neuron.BellmanFord.Edge> flat = new java.util.ArrayList<>(edges.size());
        for (Edge edge : edges) {
            flat.add(new io.matrix.neuron.BellmanFord.Edge(edge.from(), edge.to(), edge.weight()));
        }
        return flat;
    }

    /** True when no edge has a negative weight. */
    public boolean allWeightsNonNegative() {
        for (Edge edge : edges) {
            if (edge.weight() < 0.0) {
                return false;
            }
        }
        return true;
    }
}
