package io.matrix.cognitive.report;

import io.matrix.cognitive.KernelSelector;
import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.cognitive.algorithms.AlgorithmMethod;
import io.matrix.cognitive.algorithms.AlgorithmRegistry;
import io.matrix.cognitive.algorithms.CostLedger;
import io.matrix.cognitive.algorithms.ProblemRequest;

import jakarta.enterprise.context.ApplicationScoped;

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
 * second journey for the same identity sees what the first one cost. That is also why
 * the planner is an <b>application-scoped bean</b>: the route injects it, so the ledger
 * must be the application's, not a request's — and a bean the container cannot resolve
 * is a build failure, not a runtime surprise.</p>
 */
@ApplicationScoped
public class ReportJourneyPlanner {

    /** The weight the cost of a method is given against its accuracy evidence. */
    public static final double COST_WEIGHT = 1.0;

    private final KernelSelector selector;
    private final CostLedger ledger;
    private final Map<String, JourneyMethod> methods = new LinkedHashMap<>();

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
    public ReportJourneyPlanner(CostLedger ledger, List<JourneyMethod> methods) {
        this.ledger = ledger;
        AlgorithmRegistry registry = new AlgorithmRegistry();
        for (JourneyMethod method : methods) {
            register(registry, method);
        }
        this.selector = new KernelSelector(registry, ledger);
    }

    private void register(AlgorithmRegistry registry, JourneyMethod method) {
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
        return plan(request, null);
    }

    /**
     * The plan for one request, with the method FORCED.
     *
     * <p><b>What this is for.</b> The counterfactual arm of the closed loop: the same
     * request, the same workspace and the same consent, with the method chosen by
     * somebody else. The two arms then differ in exactly one thing — the method — and
     * every difference in what happened can be attributed to it.</p>
     *
     * <p><b>What it does NOT do.</b> It does not widen anything. The consent, the
     * output-root confinement, the writer's lease and the idempotency are the action's
     * own rules and they are checked before the journey starts, whatever the plan says.
     * A forced method still runs inside them, and a forced method whose own
     * preconditions do not hold refuses the request rather than running anyway.</p>
     *
     * <p><b>Who may call it.</b> The serving route never does: the route's request
     * carries no method, no method id and nothing a caller could turn into one, and the
     * route's own entry point calls {@link #plan(ProblemRequest)}. This parameter exists
     * for the counterfactual measurement and for nothing else.</p>
     *
     * @param request        the problem, with the facts that are known about it
     * @param forcedMethodId the method that must run, or null to let the evidence choose
     * @return the plan: the chosen method and the decision that chose it
     */
    public Plan plan(ProblemRequest request, String forcedMethodId) {
        return plan(request, forcedMethodId, List.of());
    }

    /**
     * The plan for a request, with the methods the caller OFFERS in addition to the
     * portfolio's own.
     *
     * <p>The offered methods are the VALIDATED PROCEDURES of the workspace: a procedure
     * becomes a member of the family for the request that may use it, and it is offered only
     * with the facts that establish its applicability domain. The catalogue is therefore
     * per request, while the COST LEDGER is not: the ledger is the planner's own and outlives
     * the request, so what a method cost is remembered across requests — including the
     * request that follows a procedure.</p>
     *
     * <p>An offered method never displaces the journey's own methods: an id that is already
     * in the portfolio is the portfolio's.</p>
     *
     * @param request        the problem, with the facts that are known about it
     * @param forcedMethodId the method that must run, or null to let the evidence choose
     * @param offered        the methods the caller offers, beyond the portfolio's own
     * @return the plan: the chosen method and the decision
     */
    public Plan plan(ProblemRequest request, String forcedMethodId, List<JourneyMethod> offered) {
        if (offered == null || offered.isEmpty()) {
            return planOver(methods, selector, request, forcedMethodId);
        }
        Map<String, JourneyMethod> catalogue = new LinkedHashMap<>(methods);
        AlgorithmRegistry requestRegistry = new AlgorithmRegistry();
        for (JourneyMethod method : catalogue.values()) {
            requestRegistry.register(method);
        }
        for (JourneyMethod method : offered) {
            String id = method.capability().id();
            if (catalogue.containsKey(id)) {
                continue;
            }
            requestRegistry.register(method);
            catalogue.put(id, method);
        }
        return planOver(catalogue, new KernelSelector(requestRegistry, ledger), request,
                forcedMethodId);
    }

    private Plan planOver(Map<String, JourneyMethod> catalogue, KernelSelector requestSelector,
            ProblemRequest request, String forcedMethodId) {
        AlgorithmDecision decision = requestSelector.selectTyped(request, COST_WEIGHT).decision();
        JourneyMethod chosen = decision.selectedAlgorithmId() == null
                ? null
                : catalogue.get(decision.selectedAlgorithmId());
        if (forcedMethodId == null) {
            return new Plan(chosen, decision, null);
        }
        JourneyMethod forced = catalogue.get(forcedMethodId);
        if (forced == null) {
            throw new IllegalArgumentException("no method with the id " + forcedMethodId
                    + " serves the report journey");
        }
        return new Plan(forced, decision, forcedMethodId);
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
     * A method of the report journey family.
     *
     * <p>The family's methods all speak the same payload and return the same output, so the
     * selector may offer any of them for any request of the family and the route may read the
     * result without knowing which one ran. That is what lets a VALIDATED PROCEDURE compete
     * with the reference method on equal terms: it is a method of this family, not a wrapper
     * around one.</p>
     */
    public interface JourneyMethod
            extends AlgorithmMethod<ReportJourney.Problem, ReportJourney.Result> {
    }

    /**
     * The plan: the method the kernel chose and the decision that chose it.
     *
     * @param method   the chosen method, or null when the request was refused
     * @param decision the decision record
     * @param forcedMethodId the method that was forced, or null when the evidence chose
     */
    public record Plan(JourneyMethod method, AlgorithmDecision decision,
            String forcedMethodId) {

        /** Whether the kernel had no method for the request. */
        public boolean refused() {
            return method == null;
        }

        /** Whether the method was forced rather than chosen. */
        public boolean forced() {
            return forcedMethodId != null;
        }

        /** Why the method was chosen, or why none was. */
        public String reason() {
            if (decision == null) {
                return "";
            }
            if (forcedMethodId == null) {
                return decision.reason();
            }
            return "the method was FORCED to " + forcedMethodId + " for the counterfactual; the "
                    + "evidence would have chosen " + decision.selectedAlgorithmId() + " ("
                    + decision.reason() + ")";
        }
    }
}
