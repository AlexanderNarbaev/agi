package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.CostLedger;
import io.matrix.cognitive.algorithms.MethodOutcome;
import io.matrix.cognitive.distributed.CognitiveCoordinator;

/**
 * W53 — the sink that writes a remote verdict into the profile of methods.
 *
 * <p><b>Why this class exists.</b> The coordinator names a verdict; the profile is kept by
 * the ledger of the whole process; the two live in different packages and the port between
 * them is the one the coordinator declares. This class is that bridge, and it is a class
 * rather than a lambda in the resource because the bridge is where the two rules meet: the
 * stratum of the verdict (the capability, the family of the task, the version that ran) and
 * the fact that a verdict is <i>not</i> a cost measurement of the local methods — the peer
 * is not a local method, and its price is the dispatch's own record.</p>
 *
 * <p><b>What it does with the cost.</b> Nothing: it records the verdict alone. The peer
 * measured its execution with its own clock, and the local methods' costs were measured with
 * this process's clock — two clocks, two units, and one column would mix them. The peer's
 * price is on the dispatch's own record, where the peer's own stages are already written, and
 * the local methods' prices stay comparable with each other.</p>
 */
public final class LedgerVerdictSink implements CognitiveCoordinator.VerdictSink {

    private final CostLedger ledger;

    /**
     * The sink.
     *
     * @param ledger the profile the verdict is recorded in
     */
    public LedgerVerdictSink(CostLedger ledger) {
        if (ledger == null) {
            throw new IllegalArgumentException("a verdict needs a profile to be recorded in");
        }
        this.ledger = ledger;
    }

    @Override
    public void record(String capabilityId, String family, String capabilityVersion,
                       MethodOutcome outcome) {
        ledger.recordVerdict(capabilityId, outcome, family, capabilityVersion);
    }
}
