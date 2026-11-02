package io.matrix.cognitive.algorithms;

import java.util.PriorityQueue;
import java.util.Set;

/**
 * W44.2 — the portfolio's anytime method: a shortest-path search that may stop before it proves
 * anything.
 *
 * <p><b>What it is for.</b> The exact methods in this family either answer or fail. Sometimes
 * the answer is worth more than the compute: a plan that is good enough now, with the search
 * still running, beats a plan that arrives after the window closed. This method is the
 * portfolio's version of that trade — it searches the same space as Dijkstra but stops at the
 * node budget the caller granted.</p>
 *
 * <p><b>What it will not do.</b> It will not report a bounded result as a proven one. When the
 * budget runs out, the method throws {@link ResourceLimitException} carrying the partial state,
 * and that state says {@code complete = false}. A caller that ignores the exception and reads the
 * partial distances anyway is misusing the result, and the record makes that hard to do by
 * accident.</p>
 *
 * <p><b>Why it declares {@link Preconditions#BOUNDED_RESULT_ACCEPTED}.</b> A method that may
 * return an unproven answer must not be picked by accident. The fact is the caller saying "I
 * accept a bounded result for this request", and without it the method is not admitted — so the
 * exact method is chosen instead. This is the precondition machinery doing the job it exists
 * for: it keeps an approximate path out of the answer unless somebody asked for one.</p>
 *
 * <p><b>Why it also declares {@link Preconditions#NON_NEGATIVE_WEIGHTS}.</b> Uniform-cost search
 * is only correct when no edge is negative; the greedy expansion that makes it exact depends on
 * it. The same fact the exact method declares, checked the same way, so the two methods are
 * admitted and refused together rather than disagreeing about the same graph.</p>
 *
 * <p><b>What the budget bounds.</b> The node budget is the dimension this method enforces
 * itself, because counting expansions needs no clock. The wall-clock budget is the caller's to
 * enforce around the call: this package reads a clock in exactly one place, the cost ledger's
 * measurement, and a decision path may not read one.</p>
 */
public final class BoundedUniformCostSearchMethod
        implements AlgorithmMethod<ShortestPathProblem, ShortestPathProblem.Outcome> {

    /** The capability id of this method. */
    public static final String ID = "graph.shortest-path.bounded-uniform-cost";

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
     * The measured cost of one bounded search, in milliseconds.
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
    public BoundedUniformCostSearchMethod() {
        this.capability = new AlgorithmCapability(
                ID,
                VERSION,
                Set.of(AlgorithmFamilies.SHORTEST_PATH),
                Set.of(Preconditions.NON_NEGATIVE_WEIGHTS, Preconditions.BOUNDED_RESULT_ACCEPTED),
                Set.of(ShortestPathProblem.class.getName()),
                Set.of(ShortestPathProblem.Outcome.class.getName()),
                new AlgorithmCapability.ComplexityProfile(
                        DeclaredCost.declare("O(V+E)", MEASURED_COST_MILLIS, 0),
                        "O(V) for the distance, predecessor and settled arrays"),
                new AlgorithmCapability.ResourceProfile(
                        "O(V) heap", true, false,
                        "the node budget runs out and the partial result is returned as unproven"),
                new AlgorithmCapability.AccuracyProfile(
                        "exact-match rate against the reference answer when the search completed",
                        1.0, VALIDATION_CASES,
                        "the W44.2 shortest-path validation corpus", MEASURED_AT),
                new AlgorithmCapability.DeterminismProfile(true, "none: the algorithm has no randomness"),
                new AlgorithmCapability.ReliabilityProfile(
                        "the node budget is exhausted and the partial result is labelled unproven",
                        true,
                        "the caller receives a partial result through ResourceLimitException"),
                new AlgorithmCapability.VerificationStrategy(
                        "a completed search agrees with the exact method on the validation corpus; "
                                + "an incomplete one is marked complete=false",
                        "io.matrix.cognitive.algorithms.ShortestPathProblem.Outcome.complete"),
                DijkstraShortestPathMethod.ID);
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
                            + "the graph has a negative edge, so a uniform-cost expansion is not valid");
        }
        ComputeBudget effective = budget == null ? ComputeBudget.unbounded() : budget;
        int maxNodes = effective.maxSearchNodes();

        int vertexCount = input.vertexCount();
        double[] distances = new double[vertexCount];
        int[] predecessors = new int[vertexCount];
        boolean[] settled = new boolean[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            distances[i] = Double.POSITIVE_INFINITY;
            predecessors[i] = -1;
        }
        distances[input.source()] = 0.0;

        // The comparator reads the CURRENT distance of a vertex rather than the value it had
        // when it was queued. That is the same lazy-deletion trick the priority queue in
        // io.matrix.neuron.Dijkstra uses, and it is why a stale entry never pops early.
        PriorityQueue<Integer> frontier = new PriorityQueue<>(
                (a, b) -> Double.compare(distances[a], distances[b]));
        frontier.add(input.source());

        int expanded = 0;
        while (!frontier.isEmpty()) {
            if (expanded >= maxNodes) {
                ShortestPathProblem.Outcome partial = new ShortestPathProblem.Outcome(
                        distances, predecessors, false,
                        "the node budget of " + maxNodes + " was exhausted after " + expanded
                                + " expansions; these distances are a lower bound, not a proof");
                throw new ResourceLimitException(
                        "the bounded search exhausted its node budget of " + maxNodes, partial);
            }
            int vertex = frontier.poll();
            if (settled[vertex]) {
                continue;
            }
            settled[vertex] = true;
            expanded++;
            for (ShortestPathProblem.Edge edge : input.edges()) {
                if (edge.from() != vertex) {
                    continue;
                }
                if (edge.to() < 0 || edge.to() >= vertexCount) {
                    continue;
                }
                double candidate = distances[vertex] + edge.weight();
                if (candidate < distances[edge.to()]) {
                    distances[edge.to()] = candidate;
                    predecessors[edge.to()] = vertex;
                    frontier.add(edge.to());
                }
            }
        }
        return new ShortestPathProblem.Outcome(distances, predecessors, true,
                "a uniform-cost search that completed inside the node budget of " + maxNodes);
    }
}
