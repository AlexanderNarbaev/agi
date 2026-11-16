package io.matrix.cognitive.report;

import io.matrix.cognitive.KernelSelector;
import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.cognitive.algorithms.AlgorithmRegistry;
import io.matrix.cognitive.algorithms.CostLedger;
import io.matrix.cognitive.algorithms.ProblemRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * P2 — the kernel's plan for one report journey.
 *
 * <p><b>What this is.</b> The place where the product chooses a method and says why. The
 * selector it uses is the kernel's own: the registry of methods, the cost ledger that
 * holds what those methods were measured to cost, and the decision record that says which
 * candidate was chosen, which were rejected, and on what grounds.</p>
 *
 * <p><b>What the plan is not.</b> It is not an authority. The plan decides HOW the journey
 * runs; it cannot widen the consent, the output-root confinement or the writer's lease,
 * and it cannot make a request servable that the route would otherwise refuse. A request
 * the kernel has no method for is refused by the route with a reason — the plan does not
 * fall back to "no method chosen" silently.</p>
 *
 * <p><b>Why the ledger is here.</b> A method's cost has to be measured before it can be
 * preferred, and a measurement that is thrown away at the end of the request would mean
 * the selector never learns anything. The ledger therefore outlives the request: the
 * second journey for the same identity sees what the first one cost.</p>
 */
public final class ReportJourneyPlanner {

    /** The weight the cost of a method is given against its accuracy evidence. */
    public static final double COST_WEIGHT = 1.0;

    private final KernelSelector selector;
    private final CostLedger ledger;
    private final Map<String, ReportJourneyMethod> methods = new LinkedHashMap<>();

    /** Creates the planner with its own ledger. */
    public ReportJourneyPlanner() {
        this(new CostLedger());
    }

    /**
     * Creates the planner over a shared ledger.
     *
     * @param ledger the ledger the measured costs are kept in
     */
    public ReportJourneyPlanner(CostLedger ledger) {
        this(ledger, List.of(ReportJourneyMethod.reference(), ReportJourneyMethod.replay()));
    }

    /**
     * Creates the planner over a shared ledger and a catalogue of the caller's own methods.
     *
     * <p>An empty catalogue is the honest way to ask what the route does when the kernel
     * has no method for the request: the plan is refused, and the route says so.</p>
     *
     * @param ledger  the ledger the measured costs are kept in
     * @param methods the methods the portfolio offers
     */
    public ReportJourneyPlanner(CostLedger ledger, List<ReportJourneyMethod> methods) {
        this.ledger = ledger;
        AlgorithmRegistry registry = new AlgorithmRegistry();
        for (ReportJourneyMethod method : methods) {
            register(registry, method);
        }
        this.selector = new KernelSelector(registry, ledger);
    }

    private void register(AlgorithmRegistry registry, ReportJourneyMethod method) {
        registry.register(method);
        methods.put(method.capability().id(), method);
    }

    /**
     * The plan for one request.
     *
     * @param request the problem, with the facts that are known about it
     * @return the plan: the chosen method and the decision that chose it
     */
    public Plan plan(ProblemRequest request) {
        AlgorithmDecision decision = selector.selectTyped(request, COST_WEIGHT).decision();
        ReportJourneyMethod method = decision.selectedAlgorithmId() == null
                ? null
                : methods.get(decision.selectedAlgorithmId());
        return new Plan(method, decision);
    }

    /**
     * Runs a body and records what it cost, under the method's own id.
     *
     * @param methodId the method that is running
     * @param body     the work
     * @param <T>      what the work produces
     * @return what the work produced
     */
    public <T> T measure(String methodId, Supplier<T> body) {
        return ledger.measure(methodId, body);
    }

    /** The ledger the measured costs are kept in. */
    public CostLedger ledger() {
        return ledger;
    }

    /**
     * The plan: the method the kernel chose and the decision that chose it.
     *
     * @param method   the chosen method, or null when the request was refused
     * @param decision the decision record
     */
    public record Plan(ReportJourneyMethod method, AlgorithmDecision decision) {

        /** Whether the kernel had no method for the request. */
        public boolean refused() {
            return method == null;
        }

        /** Why the method was chosen, or why none was. */
        public String reason() {
            return decision == null ? "" : decision.reason();
        }
    }
}
