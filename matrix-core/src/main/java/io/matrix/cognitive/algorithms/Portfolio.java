package io.matrix.cognitive.algorithms;

/**
 * W44.2 — the portfolio as it ships: the catalogue of methods the selector chooses from.
 *
 * <p>This is the assembly point. The individual methods are independent of each other and of the
 * selector; the portfolio is what puts them in one registry so that a request can be answered by
 * whichever of them fits the request's conditions.</p>
 *
 * <p><b>Every method here wraps or extends code that already existed.</b> The entailment method
 * wraps {@code io.matrix.knowledge.EntailmentChecker}; the shortest-path methods wrap
 * {@code io.matrix.neuron.Dijkstra} and {@code io.matrix.neuron.BellmanFord}; the membership
 * methods reuse {@code io.matrix.neuron.Trie} and {@code io.matrix.neuron.BloomFilter}. Two
 * things are new: the exact arithmetic evaluator, because {@code matrix-core} had no expression
 * evaluator at all and could not depend on the one in {@code matrix-brain-runtime}; and the
 * bounded uniform-cost search, because an anytime method has to exist for the family to have a
 * "cheap and possibly partial" option at all.</p>
 *
 * <p><b>Nothing here is wired into a request path yet.</b> The portfolio is a catalogue; the
 * selector reads it; whether a request reaches the selector is the caller's business. That is
 * deliberate — this wave adds the mechanism, and the mechanism is not worth anything if it is
 * attached to a path that has not been measured.</p>
 */
public final class Portfolio {

    private Portfolio() {
    }

    /**
     * A registry holding every method of this package.
     *
     * @return the catalogue, in registration order
     */
    public static AlgorithmRegistry defaultRegistry() {
        return new AlgorithmRegistry()
                .register(new IntegerArithmeticMethod())
                .register(new EntailmentMethod())
                .register(new DijkstraShortestPathMethod())
                .register(new BellmanFordShortestPathMethod())
                .register(new BoundedUniformCostSearchMethod())
                .register(new LinearScanMembershipMethod())
                .register(new TrieMembershipMethod())
                .register(new BloomFilterMembershipMethod());
    }

    /**
     * A selector over the default catalogue.
     *
     * @param costLedger the ledger the selector reads measured costs from
     * @return the selector
     */
    public static AlgorithmSelector defaultSelector(CostLedger costLedger) {
        return new AlgorithmSelector(defaultRegistry(), costLedger);
    }
}
