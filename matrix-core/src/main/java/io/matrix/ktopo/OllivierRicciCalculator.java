package io.matrix.ktopo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ollivier-Ricci edge curvature on small undirected weighted graphs (SPEC-003 FR-2).
 *
 * <p><b>Definition.</b> For adjacent vertices {@code x, y} define the probability
 * measure
 * <pre>
 *   μ_x(x) = α                            (idleness mass on the node itself)
 *   μ_x(v) = (1 - α) / deg(x)   for each neighbor v of x
 * </pre>
 * where {@code deg(x)} is the (unweighted) degree of {@code x}. The edge curvature is
 * <pre>
 *   κ(x, y) = 1 - W1(μ_x, μ_y) / d(x, y)
 * </pre>
 * with {@code W1} the 1-Wasserstein distance under the shortest-path graph metric and
 * {@code d(x,y)} the shortest-path distance between the endpoints (Ollivier 2007;
 * Lin–Lu–Yau 2011).
 *
 * <p><b>Parameter choice (documented).</b> {@link #DEFAULT_ALPHA} = {@value #DEFAULT_ALPHA}
 * (classic idleness used in community-detection Ricci-flow literature, cf. Ni et al.
 * 2019). It is overridable via {@link #OllivierRicciCalculator(double)} so callers can
 * reproduce the flat measure ({@code α = 1/(deg+1)} makes all node measures identical
 * on a clique → {@code W1 = 0 → κ = +1}, the maximal positive curvature).
 *
 * <p><b>Transport solver (documented method).</b> {@code W1} is solved <em>exactly</em>
 * on the small support sets (support = node + neighbors, size ≤ {@link #MAX_DEGREE}+1)
 * as a min-cost transportation linear program via the <em>successive shortest augmenting
 * path</em> (SSAP) algorithm. Two naive approaches are explicitly avoided because they
 * are invalid for general metric costs:
 * <ul>
 *   <li>greedy sorted-mass matching — not optimal for non-Euclidean/metric supports;</li>
 *   <li>North-West corner rule — only a feasible (not optimal) starting basis.</li>
 * </ul>
 * SSAP is exact: it terminates with the optimal objective of the transportation LP.
 * Complexity is {@code O(F · V · E)} where {@code F ≤ |supply| + |demand| ≤ 2·(MAX_DEGREE+1)}
 * augmentations, {@code V ≤ 2·(MAX_DEGREE+1)+2} nodes and {@code E ≤ V²} residual arcs —
 * a small constant, since {@code MAX_DEGREE = 32} caps every support. Shortest paths in
 * the residual network are computed with Bellman-Ford (tolerates the negative residual
 * arcs that arise after augmentation). Everything is deterministic: arrays and fixed
 * iteration order, no hash-order dependence.
 *
 * <p><b>Degree cap.</b> {@code MAX_DEGREE = 32} bounds the per-node support size and
 * hence the solver cost; a node with higher degree is rejected with
 * {@link IllegalArgumentException} (documented scope limit, preserves determinism).
 *
 * <p><b>Sign convention (verified).</b> Positive {@code κ} = dense community; {@code κ ≈ 0}
 * = flat (path/1D); negative {@code κ} = bridge/branching (e.g. an edge linking two
 * cliques, or a tree edge with both endpoints of degree ≥ 3).
 */
public final class OllivierRicciCalculator {

    /** Classic idleness mass on the node itself (documented choice). */
    public static final double DEFAULT_ALPHA = 0.15;

    /** Maximum node degree considered; bounds the exact transport support. */
    public static final int MAX_DEGREE = 32;

    private final double alpha;

    /** Create a calculator with the classic {@link #DEFAULT_ALPHA} idleness. */
    public OllivierRicciCalculator() {
        this(DEFAULT_ALPHA);
    }

    /**
     * Create a calculator with a custom idleness {@code α ∈ [0,1]}.
     *
     * @param alpha mass kept on the node itself; {@code 0} = pure uniform-over-neighbors,
     *              {@code 1} = degenerate (all mass on node, trivial transport)
     */
    public OllivierRicciCalculator(double alpha) {
        if (!(alpha >= 0.0 && alpha <= 1.0)) {
            throw new IllegalArgumentException("alpha must be in [0,1], got " + alpha);
        }
        this.alpha = alpha;
    }

    /** The configured idleness mass {@code α}. */
    public double alpha() {
        return alpha;
    }

    /**
     * Compute the Ollivier-Ricci curvature of every edge.
     *
     * @param graph small undirected weighted graph
     * @return array of curvature values aligned with {@code graph.u()}/{@code graph.v()}
     */
    public double[] computeCurvatures(Graph graph) {
        double[][] dist = allPairsShortestPaths(graph);
        double[] out = new double[graph.edgeCount()];
        for (int e = 0; e < graph.edgeCount(); e++) {
            out[e] = curvatureBetween(graph, graph.u()[e], graph.v()[e], dist);
        }
        return out;
    }

    /**
     * Compute the Ollivier-Ricci curvature for a single pair of vertices using their
     * shortest-path distance as the metric.
     *
     * @param graph small undirected weighted graph
     * @param x     source vertex
     * @param y     target vertex (x ≠ y)
     */
    public double curvature(Graph graph, int x, int y) {
        if (x == y) {
            throw new IllegalArgumentException("curvature is defined for distinct vertices");
        }
        double[][] dist = allPairsShortestPaths(graph);
        return curvatureBetween(graph, x, y, dist);
    }

    private double curvatureBetween(Graph graph, int x, int y, double[][] dist) {
        double d = dist[x][y];
        double w1 = wassersteinBetween(graph, x, y, dist);
        return 1.0 - w1 / d;
    }

    /**
     * Exact W1 between μ_x and μ_y, supports restricted to node + neighbors of each.
     */
    private double wassersteinBetween(Graph graph, int x, int y, double[][] dist) {
        double[] mx = measure(graph, x);
        double[] my = measure(graph, y);

        // Union support, in ascending node-index order (deterministic).
        List<Integer> support = new ArrayList<>();
        for (int i = 0; i < graph.n(); i++) {
            if (mx[i] > 0.0 || my[i] > 0.0) {
                support.add(i);
            }
        }
        int k = support.size();
        double[] supply = new double[k];
        double[] demand = new double[k];
        double[][] cost = new double[k][k];
        for (int i = 0; i < k; i++) {
            supply[i] = mx[support.get(i)];
            demand[i] = my[support.get(i)];
            for (int j = 0; j < k; j++) {
                cost[i][j] = dist[support.get(i)][support.get(j)];
            }
        }
        return wasserstein1(supply, demand, cost);
    }

    /** Probability measure of a node: {@code α} on itself, rest uniform over neighbors. */
    private double[] measure(Graph graph, int x) {
        int[] nbrs = neighbors(graph, x);
        int deg = nbrs.length;
        if (deg > MAX_DEGREE) {
            throw new IllegalArgumentException(
                    "degree " + deg + " of node " + x + " exceeds MAX_DEGREE=" + MAX_DEGREE);
        }
        double[] m = new double[graph.n()];
        if (deg == 0) {
            m[x] = 1.0; // isolated node: all mass on itself
            return m;
        }
        m[x] = alpha;
        double rest = (1.0 - alpha) / deg;
        for (int nb : nbrs) {
            m[nb] = rest;
        }
        return m;
    }

    /** Sorted neighbor list of a node (ascending index → deterministic). */
    private int[] neighbors(Graph graph, int x) {
        List<Integer> list = new ArrayList<>();
        for (int e = 0; e < graph.edgeCount(); e++) {
            if (graph.u()[e] == x) {
                list.add(graph.v()[e]);
            } else if (graph.v()[e] == x) {
                list.add(graph.u()[e]);
            }
        }
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        Arrays.sort(arr);
        return arr;
    }

    /** Floyd-Warshall all-pairs shortest paths (weights = edge lengths). Deterministic. */
    private double[][] allPairsShortestPaths(Graph graph) {
        int n = graph.n();
        double[][] d = new double[n][n];
        for (double[] row : d) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        for (int i = 0; i < n; i++) {
            d[i][i] = 0.0;
        }
        for (int e = 0; e < graph.edgeCount(); e++) {
            int a = graph.u()[e];
            int b = graph.v()[e];
            double w = graph.w()[e];
            if (w < d[a][b]) {
                d[a][b] = w;
                d[b][a] = w;
            }
        }
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double via = d[i][k] + d[k][j];
                    if (via < d[i][j]) {
                        d[i][j] = via;
                    }
                }
            }
        }
        return d;
    }

    /**
     * Exact 1-Wasserstein distance between two discrete measures on the same finite
     * support, solved as a min-cost transportation LP by successive shortest augmenting
     * paths (SSAP). Both measures must have equal total mass.
     *
     * @param supply source masses (sum = 1)
     * @param demand sink masses (sum = 1)
     * @param cost   cost matrix (metric distances between support points), cost[i][j] ≥ 0
     * @return exact optimal transport cost
     */
    private static double wasserstein1(double[] supply, double[] demand, double[][] cost) {
        int ns = supply.length;
        int nt = demand.length;
        if (ns == 0 || nt == 0) {
            return 0.0;
        }

        // Min-cost flow network: s=0, sources 1..ns, sinks ns+1..ns+nt, t=ns+nt+1.
        int V = ns + nt + 2;
        int s = 0;
        int t = V - 1;

        // Edge list with reverse edges for residual graph.
        List<Edge> edges = new ArrayList<>();
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < V; i++) {
            adj.add(new ArrayList<>());
        }

        // s -> source_i (capacity = supply_i, cost 0)
        for (int i = 0; i < ns; i++) {
            addEdge(adj, edges, s, 1 + i, supply[i], 0.0);
        }
        // source_i -> sink_j (capacity = inf, cost = cost[i][j])
        for (int i = 0; i < ns; i++) {
            for (int j = 0; j < nt; j++) {
                addEdge(adj, edges, 1 + i, ns + 1 + j, Double.POSITIVE_INFINITY, cost[i][j]);
            }
        }
        // sink_j -> t (capacity = demand_j, cost 0)
        for (int j = 0; j < nt; j++) {
            addEdge(adj, edges, ns + 1 + j, t, demand[j], 0.0);
        }

        double totalFlow = 0.0;
        for (double sSup : supply) {
            totalFlow += sSup;
        }

        double totalCost = 0.0;
        double pushed = 0.0;
        while (pushed < totalFlow - 1e-12) {
            // Bellman-Ford shortest path in residual graph (handles negative arcs).
            double[] distArr = new double[V];
            int[] prevEdge = new int[V];
            Arrays.fill(distArr, Double.POSITIVE_INFINITY);
            Arrays.fill(prevEdge, -1);
            distArr[s] = 0.0;

            boolean changed = true;
            for (int iter = 0; iter < V - 1 && changed; iter++) {
                changed = false;
                for (int u = 0; u < V; u++) {
                    if (distArr[u] == Double.POSITIVE_INFINITY) {
                        continue;
                    }
                    for (int eid : adj.get(u)) {
                        Edge e = edges.get(eid);
                        if (e.cap > 1e-12 && distArr[u] + e.cost < distArr[e.to] - 1e-12) {
                            distArr[e.to] = distArr[u] + e.cost;
                            prevEdge[e.to] = eid;
                            changed = true;
                        }
                    }
                }
            }
            if (prevEdge[t] == -1) {
                // Should not happen when total supply == total demand.
                throw new IllegalStateException("no augmenting path; supply/demand totals mismatch");
            }

            // Find bottleneck along the path.
            double bottleneck = Double.POSITIVE_INFINITY;
            for (int cur = t; cur != s; ) {
                int eid = prevEdge[cur];
                Edge e = edges.get(eid);
                bottleneck = Math.min(bottleneck, e.cap);
                cur = e.from;
            }
            // Augment.
            for (int cur = t; cur != s; ) {
                int eid = prevEdge[cur];
                edges.get(eid).cap -= bottleneck;
                edges.get(eid ^ 1).cap += bottleneck; // reverse edge (paired by construction)
                cur = edges.get(eid).from;
            }
            totalCost += bottleneck * distArr[t];
            pushed += bottleneck;
        }
        return totalCost;
    }

    /** Add a directed edge (u → v) and its reverse residual edge (v → u, cap 0, -cost). */
    private static void addEdge(List<List<Integer>> adj, List<Edge> edges, int u, int v, double cap, double cost) {
        Edge forward = new Edge(u, v, cap, cost);
        Edge reverse = new Edge(v, u, 0.0, -cost);
        adj.get(u).add(edges.size());
        edges.add(forward);
        adj.get(v).add(edges.size());
        edges.add(reverse);
    }

    /** Residual edge: from, to, residual capacity, cost. */
    private static final class Edge {
        final int from;
        final int to;
        double cap;
        final double cost;

        Edge(int from, int to, double cap, double cost) {
            this.from = from;
            this.to = to;
            this.cap = cap;
            this.cost = cost;
        }
    }
}
