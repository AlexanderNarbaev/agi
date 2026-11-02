package io.matrix.cognitive;

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
final class KernelSelector implements CognitiveKernel.AlgorithmSelectorBridge {

    private final AlgorithmRegistry registry;
    private final CostLedger ledger;

    /** Creates the adapter over a registry with an empty ledger. */
    KernelSelector(AlgorithmRegistry registry) {
        this(registry, new CostLedger());
    }

    /** Creates the adapter over a registry with a shared ledger. */
    KernelSelector(AlgorithmRegistry registry, CostLedger ledger) {
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

    /** The ledger the measured costs are recorded in. */
    CostLedger ledger() {
        return ledger;
    }
}
