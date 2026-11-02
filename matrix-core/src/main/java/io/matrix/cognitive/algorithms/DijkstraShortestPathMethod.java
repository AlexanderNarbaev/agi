package io.matrix.cognitive.algorithms;

import io.matrix.neuron.Dijkstra;

import java.util.Set;

/**
 * W44.2 — the portfolio's fast shortest-path method, with the precondition Dijkstra does not have.
 *
 * <p><b>Why this wrapper exists at all.</b> {@code io.matrix.neuron.Dijkstra} documents that it
 * computes single-source shortest paths "non-negative weights", and its code never checks that
 * the weights are non-negative. The algorithm is not wrong in the presence of a negative edge in
 * the sense of producing a wrong-looking number — it produces a number that is not the shortest
 * path, because the greedy expansion assumes that a settled vertex can never be improved. A
 * method that silently returns a wrong shortest path is the worst kind of member for a portfolio
 * whose whole point is to be chosen on declared conditions.</p>
 *
 * <p>So the precondition lives here, in the wrapper that owns the contract:
 * {@link Preconditions#NON_NEGATIVE_WEIGHTS} is declared, the method checks it against the
 * actual edges, and the selector refuses to admit the method for a graph that violates it. The
 * fallback is {@link BellmanFordShortestPathMethod#ID}, which handles negative weights — and
 * refuses, honestly, when the graph has a negative cycle.</p>
 *
 * <p>The wrapper does not reimplement anything: it adapts the input to {@link Dijkstra}'s
 * adjacency form and calls it.</p>
 */
public final class DijkstraShortestPathMethod
        implements AlgorithmMethod<ShortestPathProblem, ShortestPathProblem.Outcome> {

    /** The capability id of this method. */
    public static final String ID = "graph.shortest-path.dijkstra";

    /** The version of this method. */
    public static final String VERSION = "1.0.0";

    /** The capability id of the fallback method. */
    public static final String FALLBACK_ID = BellmanFordShortestPathMethod.ID;

    /**
     * The number of graphs the declared accuracy was measured over.
     *
     * <p>DERIVED from the validation corpus in the test, which asserts the corpus has exactly
     * this many cases.</p>
     */
    public static final int VALIDATION_CASES = 12;

    /**
     * The measured cost of one shortest-path computation, in milliseconds.
     *
     * <p>MEASURED on the W44.2 development machine over the validation corpus.</p>
     */
    public static final long MEASURED_COST_MILLIS = 1L;

    /**
     * When the declared evidence was measured.
     *
     * <p>MEASURED: the date of the validation run the evidence comes from.</p>
     */
    public static final String MEASURED_AT = "2026-11-02";

    private final AlgorithmCapability capability;

    /** Builds the method with its declared capability. */
    public DijkstraShortestPathMethod() {
        this.capability = new AlgorithmCapability(
                ID,
                VERSION,
                Set.of(AlgorithmFamilies.SHORTEST_PATH),
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS),
                Set.of(ShortestPathProblem.class.getName()),
                Set.of(ShortestPathProblem.Outcome.class.getName()),
                new AlgorithmCapability.ComplexityProfile(
                        DeclaredCost.declare("O(V+E)", MEASURED_COST_MILLIS, 0),
                        "O(V) for the distance and predecessor arrays"),
                new AlgorithmCapability.ResourceProfile(
                        "O(V) heap", true, false,
                        "the weights are not all non-negative, so the method is not admitted"),
                new AlgorithmCapability.AccuracyProfile(
                        "exact-match rate against the reference answer", 1.0, VALIDATION_CASES,
                        "the W44.2 shortest-path validation corpus", MEASURED_AT),
                new AlgorithmCapability.DeterminismProfile(true, "none: the algorithm has no randomness"),
                new AlgorithmCapability.ReliabilityProfile(
                        "a negative weight is refused before the search starts", true,
                        "the fallback method takes over"),
                new AlgorithmCapability.VerificationStrategy(
                        "the distances agree with the reference method on the validation corpus",
                        "io.matrix.cognitive.algorithms.ShortestPathProblem.Outcome.pathTo"),
                FALLBACK_ID);
    }

    @Override
    public AlgorithmCapability capability() {
        return capability;
    }

    @Override
    public boolean preconditionsHold(ShortestPathProblem input) {
        return input != null
                && input.vertexCount() > 0
                && input.source() >= 0
                && input.source() < input.vertexCount()
                && input.allWeightsNonNegative();
    }

    @Override
    public ShortestPathProblem.Outcome execute(ShortestPathProblem input, ComputeBudget budget) {
        if (!preconditionsHold(input)) {
            throw new IllegalStateException(
                    "precondition " + Preconditions.NON_NEGATIVE_WEIGHTS + " does not hold: "
                            + "the graph has a negative edge, so Dijkstra's expansion is not valid");
        }
        Dijkstra.Result result = Dijkstra.shortestPaths(
                input.adjacency(), input.source(), Double.POSITIVE_INFINITY);
        return new ShortestPathProblem.Outcome(result.distances(), result.predecessors(), true,
                "Dijkstra's expansion; the weights were checked to be non-negative first");
    }
}
