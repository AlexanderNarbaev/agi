package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.cognitive.algorithms.AlgorithmRefusalException;
import io.matrix.cognitive.algorithms.AlgorithmMethod;
import io.matrix.cognitive.algorithms.AlgorithmSelector;
import io.matrix.cognitive.algorithms.CostLedger;
import io.matrix.cognitive.algorithms.Preconditions;
import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.cognitive.algorithms.ProblemRequest;
import io.matrix.cognitive.algorithms.ShortestPathProblem;
import io.matrix.cognitive.neuromodulation.RegulationBias;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * W44 — a typed entry point into the algorithm portfolio, on the same terms as the micro-world
 * cycle.
 *
 * <p>The portfolio is not a catalogue: it has to be reachable from a request a user actually
 * makes, and the request has to be answered by the method the selector chose, for the reasons
 * the selector recorded. This class is that entry point for the shortest-path family, and it
 * uses exactly the same machinery the kernel uses: the same registry, the same selector, the
 * same cost ledger and the same regulatory cost weight.</p>
 *
 * <p>What makes the family interesting is that its members have genuinely different
 * preconditions. Dijkstra needs non-negative weights and its library implementation does not
 * check them, so the fact is established here and the method checks it again. Bellman-Ford
 * accepts negative weights and refuses a negative CYCLE. The bounded search accepts a bounded
 * answer and refuses to answer outside its node budget. The selector's choice therefore
 * depends on what is TRUE about the request, not on a preference order.</p>
 */
public final class CognitiveSolver {

    /** The problem family this solver serves. */
    public static final String FAMILY_SHORTEST_PATH = "graph.shortest-path";

    /** The wall-clock budget a solve may spend, in milliseconds. */
    public static final long DEFAULT_SOLVE_BUDGET_MILLIS = 250L;

    /** One edge of the requested graph. */
    public record EdgeSpec(int from, int to, double weight) {
    }

    /** The request: a graph, a source and a target. */
    public record GraphRequest(int vertexCount, List<EdgeSpec> edges, int source, int target) {
        public GraphRequest {
            edges = edges == null ? List.of() : List.copyOf(edges);
        }
    }

    /** The outcome of a solve. */
    public record SolveOutcome(
            String family,
            String selectedAlgorithmId,
            AlgorithmDecision decision,
            String answer,
            List<String> notes) {
        public SolveOutcome {
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }

    private final AlgorithmSelector selector;
    private final CostLedger ledger;

    /** Creates a solver over a selector and the ledger it records into. */
    public CognitiveSolver(AlgorithmSelector selector, CostLedger ledger) {
        this.selector = selector;
        this.ledger = ledger;
    }

    /**
     * Solves a shortest-path request.
     *
     * @param request   the graph and the two vertices
     * @param costWeight how heavily cost counts against usefulness; the caller supplies it
     * @param allowBounded whether a bounded (possibly incomplete) answer is acceptable
     * @return the outcome, with the decision that chose the method
     */
    public SolveOutcome solveGraph(GraphRequest request, double costWeight, boolean allowBounded) {
        List<ShortestPathProblem.Edge> edges = new ArrayList<>();
        for (EdgeSpec edge : request.edges()) {
            edges.add(new ShortestPathProblem.Edge(edge.from(), edge.to(), edge.weight()));
        }
        ShortestPathProblem problem = new ShortestPathProblem(request.vertexCount(), edges,
                request.source());

        Set<String> facts = new LinkedHashSet<>();
        if (problem.allWeightsNonNegative()) {
            facts.add(Preconditions.NON_NEGATIVE_WEIGHTS);
        }
        if (allowBounded) {
            facts.add(Preconditions.BOUNDED_RESULT_ACCEPTED);
        }

        ProblemRequest call = new ProblemRequest(FAMILY_SHORTEST_PATH, problem, facts,
                ComputeBudget.millis(DEFAULT_SOLVE_BUDGET_MILLIS));
        AlgorithmSelector.Selection<ShortestPathProblem, ShortestPathProblem.Outcome> chosen =
                selector.select(call, costWeight);
        AlgorithmDecision decision = chosen.decision();
        List<String> notes = new ArrayList<>();
        if (decision.status() == AlgorithmDecision.Status.REFUSED) {
            notes.add("no method was admitted: " + decision.reason());
            return new SolveOutcome(FAMILY_SHORTEST_PATH, null, decision,
                    "No computation was permitted for this request.", notes);
        }
        ShortestPathProblem.Outcome outcome;
        try {
            outcome = ledger.measure(chosen.method().capability().id(),
                    () -> chosen.method().execute(problem,
                            ComputeBudget.millis(
                                    DEFAULT_SOLVE_BUDGET_MILLIS)));
        } catch (AlgorithmRefusalException e) {
            // A refusal is a RESULT, not a crash: the graph has no shortest path (a negative
            // cycle), and the honest answer says so. It must never become a finite distance.
            notes.add("the method refused: " + e.getMessage());
            AlgorithmDecision refused = new AlgorithmDecision(FAMILY_SHORTEST_PATH,
                    decision.selectedAlgorithmId(), decision.selectedAlgorithmVersion(),
                    "the selected method refused: " + e.getMessage(),
                    decision.candidates(), decision.budget(),
                    AlgorithmDecision.Status.REFUSED, decision.fallbackAlgorithmId(),
                    "no result was produced");
            return new SolveOutcome(FAMILY_SHORTEST_PATH, decision.selectedAlgorithmId(),
                    refused, "No shortest path exists: " + e.getMessage() + ".", notes);
        } catch (RuntimeException e) {
            AlgorithmDecision failed = new AlgorithmDecision(FAMILY_SHORTEST_PATH,
                    decision.selectedAlgorithmId(), decision.selectedAlgorithmVersion(),
                    "the selected method failed: " + e.getMessage(),
                    decision.candidates(), decision.budget(),
                    AlgorithmSelector.statusFor(e), decision.fallbackAlgorithmId(),
                    "no result was produced");
            return new SolveOutcome(FAMILY_SHORTEST_PATH, decision.selectedAlgorithmId(),
                    failed, "The computation did not finish: " + e.getMessage() + ".", notes);
        }
        double distance = outcome.distanceTo(request.target());
        String answer = outcome.complete()
                ? "the shortest distance from " + request.source() + " to " + request.target()
                + " is " + distance + ", along " + describePath(outcome.pathTo(request.target()))
                : "the search did not finish within its budget; the partial answer is "
                + distance + " and it is NOT proven";
        if (!outcome.complete()) {
            notes.add("the result is bounded, not proven");
        }
        return new SolveOutcome(FAMILY_SHORTEST_PATH, decision.selectedAlgorithmId(), decision,
                answer, notes);
    }

    private static String describePath(int[] path) {
        if (path == null || path.length == 0) {
            return "no path";
        }
        StringBuilder sb = new StringBuilder();
        for (int vertex : path) {
            if (sb.length() > 0) {
                sb.append(" → ");
            }
            sb.append(vertex);
        }
        return sb.toString();
    }

    /** The ledger the measured costs are recorded in. */
    public CostLedger ledger() {
        return ledger;
    }

    /** The regulatory cost weight this solver would use at a given bias. */
    public static double costWeightOf(RegulationBias bias) {
        return bias == null ? 1.0 : bias.costWeightMultiplier();
    }

    /** The selector this solver uses, for diagnostics. */
    public AlgorithmSelector selector() {
        return selector;
    }

    /** The method the selector would choose, without running it. */
    public AlgorithmMethod<?, ?> methodFor(GraphRequest request, boolean allowBounded) {
        List<ShortestPathProblem.Edge> edges = new ArrayList<>();
        for (EdgeSpec edge : request.edges()) {
            edges.add(new ShortestPathProblem.Edge(edge.from(), edge.to(), edge.weight()));
        }
        ShortestPathProblem problem = new ShortestPathProblem(request.vertexCount(), edges,
                request.source());
        Set<String> facts = new LinkedHashSet<>();
        if (problem.allWeightsNonNegative()) {
            facts.add(Preconditions.NON_NEGATIVE_WEIGHTS);
        }
        if (allowBounded) {
            facts.add(Preconditions.BOUNDED_RESULT_ACCEPTED);
        }
        return selector.select(new ProblemRequest(FAMILY_SHORTEST_PATH, problem, facts,
                ComputeBudget.millis(DEFAULT_SOLVE_BUDGET_MILLIS)),
                1.0).method();
    }
}
