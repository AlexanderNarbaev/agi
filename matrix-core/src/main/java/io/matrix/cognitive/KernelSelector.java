package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmDecision;
import io.matrix.cognitive.algorithms.AlgorithmMethod;
import io.matrix.cognitive.algorithms.AlgorithmRegistry;
import io.matrix.cognitive.algorithms.AlgorithmSelector;
import io.matrix.cognitive.algorithms.CostLedger;
import io.matrix.cognitive.algorithms.ProblemRequest;

/**
 * W44 — the adapter between the kernel and the algorithm selector.
 *
 * <p>The kernel asks for a method; the selector answers with a method and a decision that says
 * why. The cost weight the selector uses comes from the kernel's caller — in the serving path
 * that is the regulatory bias, so a system under load weighs cost more heavily and a system at
 * rest does not. The weight is therefore an INPUT, never a constant buried in the selector.</p>
 *
 * <p>The cost ledger is shared and lives across cycles: a method's cost has to be MEASURED
 * before it can be preferred, and a measurement that is thrown away at the end of the cycle
 * would mean the selector never learns anything.</p>
 */
public final class KernelSelector implements CognitiveKernel.AlgorithmSelectorBridge {

    private final AlgorithmRegistry registry;
    private final CostLedger ledger;

    /** Creates the adapter over a registry with an empty ledger. */
    public KernelSelector(AlgorithmRegistry registry) {
        this(registry, new CostLedger());
    }

    /** Creates the adapter over a registry with a shared ledger. */
    public KernelSelector(AlgorithmRegistry registry, CostLedger ledger) {
        this.registry = registry;
        this.ledger = ledger;
    }

    @Override
    public Selection select(ProblemRequest request, double costWeight) {
        AlgorithmSelector selector = new AlgorithmSelector(registry, ledger, costWeight);
        AlgorithmSelector.Selection<MicroWorldComputation, Boolean> chosen =
                selector.select(request, costWeight);
        return new Selection(chosen.method(), chosen.decision());
    }

    /**
     * P2 — chooses a method for a request whose payload the caller types.
     *
     * <p>The bridge above is typed to the microworld's own computation, because the cycle
     * that uses it is. A family outside the microworld — the report journey — has a
     * payload of its own, and the selector's erasure would cast that payload to the
     * microworld's type when it asks a candidate whether its preconditions hold. The
     * candidate would answer "I cannot accept this request's payload", and the family
     * would have no admissible method at all.</p>
     *
     * <p>This entry point keeps the type the caller supplies, so the candidate's own
     * precondition check sees the payload it was written for. The selection itself is
     * the selector's, unchanged.</p>
     *
     * @param request    the problem
     * @param costWeight how strongly a method's cost counts against its usefulness
     * @param <I>        the payload's type
     * @param <O>        the result's type
     * @return the chosen method and the decision that chose it
     */
    public <I, O> TypedSelection<I, O> selectTyped(ProblemRequest request, double costWeight) {
        AlgorithmSelector selector = new AlgorithmSelector(registry, ledger, costWeight);
        AlgorithmSelector.Selection<I, O> chosen = selector.select(request, costWeight);
        return new TypedSelection<>(chosen.method(), chosen.decision());
    }

    /**
     * A chosen method and the decision that chose it, typed by the caller.
     *
     * @param method   the method to run, or null when the request was refused
     * @param decision why it was chosen, and what else was considered
     * @param <I>      the payload's type
     * @param <O>      the result's type
     */
    public record TypedSelection<I, O>(AlgorithmMethod<I, O> method, AlgorithmDecision decision) {
    }

    /** The ledger the measured costs are recorded in. */
    CostLedger ledger() {
        return ledger;
    }

    /**
     * W51 §5 — runs the method and records what it cost.
     *
     * <p>This is the adapter's whole reason for existing as a holder of the ledger: the
     * ledger is shared and lives across cycles, so a method's cost is MEASURED once and
     * every later decision prefers it on the measurement instead of on the declared
     * complexity. Before this, nothing ever wrote to the ledger and the selector never
     * learned anything.</p>
     *
     * @param methodId the method being run
     * @param body     the work
     * @param <T>      what the work produces
     * @return what the work produced
     */
    @Override
    public <T> T measure(String methodId, java.util.function.Supplier<T> body) {
        return ledger.measure(methodId, body);
    }

    @Override
    public <T> T measure(String methodId, String family, String capabilityVersion,
                         java.util.function.Supplier<T> body) {
        return ledger.measure(methodId, family, capabilityVersion, body);
    }
}
