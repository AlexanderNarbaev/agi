package io.matrix.cognitive.algorithms;

import io.matrix.neuron.BellmanFord;

import java.util.Set;

/**
 * W44.2 — the portfolio's general shortest-path method.
 *
 * <p>{@code io.matrix.neuron.BellmanFord} handles negative weights and detects negative cycles:
 * it relaxes the edges {@code V-1} times and then makes one more pass, and a relaxation that
 * still succeeds means a negative-weight cycle is reachable from the source. The class documents
 * this by throwing {@link BellmanFord.NegativeCycleException}.</p>
 *
 * <p><b>What the wrapper changes is the shape of the failure, not the algorithm.</b> A negative
 * cycle means there IS no shortest path: the "distance" would decrease without bound. Returning
 * the last relaxed value would be reporting a number that is not a path length, and the
 * instruction for this wave is explicit that such a case must never surface as a finite shortest
 * path. The wrapper therefore converts the exception into an {@link AlgorithmRefusalException},
 * which maps to {@link AlgorithmDecision.Status#REFUSED}: the problem was well-formed and the
 * budget was fine, and the answer does not exist.</p>
 *
 * <p>Note that the method declares NO precondition. It accepts negative weights — that is its
 * reason to exist — and it cannot know in advance whether a negative cycle is reachable without
 * running the algorithm, so a precondition would be a claim it could not keep. The refusal is
 * the honest place for that answer.</p>
 */
public final class BellmanFordShortestPathMethod
        implements AlgorithmMethod<ShortestPathProblem, ShortestPathProblem.Outcome> {

    /** The capability id of this method. */
    public static final String ID = "graph.shortest-path.bellman-ford";

    /** The version of this method. */
    public static final String VERSION = "1.0.0";

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
     * <p>MEASURED on the W44.2 development machine over the validation corpus. It is larger than
     * Dijkstra's because the relaxation runs over every edge {@code V-1} times.</p>
     */
    public static final long MEASURED_COST_MILLIS = 2L;

    /**
     * When the declared evidence was measured.
     *
     * <p>MEASURED: the date of the validation run the evidence comes from.</p>
     */
    public static final String MEASURED_AT = "2026-11-02";

    private final AlgorithmCapability capability;

    /** Builds the method with its declared capability. */
    public BellmanFordShortestPathMethod() {
        this.capability = new AlgorithmCapability(
                ID,
                VERSION,
                Set.of(AlgorithmFamilies.SHORTEST_PATH),
                Set.of(),
                Set.of(ShortestPathProblem.class.getName()),
                Set.of(ShortestPathProblem.Outcome.class.getName()),
                new AlgorithmCapability.ComplexityProfile(
                        DeclaredCost.declare("O(V*E)", MEASURED_COST_MILLIS, 0),
                        "O(V) for the distance and predecessor arrays"),
                new AlgorithmCapability.ResourceProfile(
                        "O(V) heap", true, false,
                        "a negative cycle is reachable from the source, so no shortest path exists"),
                new AlgorithmCapability.AccuracyProfile(
                        "exact-match rate against the reference answer", 1.0, VALIDATION_CASES,
                        "the W44.2 shortest-path validation corpus", MEASURED_AT),
                new AlgorithmCapability.DeterminismProfile(true, "none: the algorithm has no randomness"),
                new AlgorithmCapability.ReliabilityProfile(
                        "a negative cycle is refused instead of being reported as a finite distance",
                        true,
                        "the refusal names the cycle"),
                new AlgorithmCapability.VerificationStrategy(
                        "the distances agree with the reference method on the validation corpus",
                        "io.matrix.cognitive.algorithms.ShortestPathProblem.Outcome.pathTo"),
                null);
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
                && input.source() < input.vertexCount();
    }

    @Override
    public ShortestPathProblem.Outcome execute(ShortestPathProblem input, ComputeBudget budget) {
        if (!preconditionsHold(input)) {
            throw new IllegalStateException("the graph or the source vertex is not usable");
        }
        try {
            BellmanFord.Result result = BellmanFord.shortestPaths(
                    input.vertexCount(), input.flatEdges(), input.source());
            return new ShortestPathProblem.Outcome(result.distances(), result.predecessors(), true,
                    "Bellman-Ford relaxation; negative weights are supported");
        } catch (BellmanFord.NegativeCycleException e) {
            throw new AlgorithmRefusalException(
                    "no shortest path exists: a negative-weight cycle is reachable from the source ("
                            + e.getMessage() + ")", e);
        }
    }
}
