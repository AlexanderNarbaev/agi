package io.matrix.bridge;

import io.matrix.contract.bridge.BridgeBudget;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgeIdempotency;
import io.matrix.contract.bridge.BridgeReceiver;
import io.matrix.contract.bridge.BridgeSigner;
import io.matrix.knowledge.KnowledgeWorkbench;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * W51 — the chain A side of the bridge: the one owner of the bridge's write path.
 *
 * <p><b>What this is.</b> The W45.2 decision said "no bridge" and wrote down the contract a
 * future bridge must satisfy (BRIDGE-1 … BRIDGE-10). W51 implements that contract. This class
 * is the chain A half of it: it receives a message, decides whether the message satisfies the
 * contract, and — only if it does — lets the message reach the epistemic store as an
 * <i>unverified observation</i>.</p>
 *
 * <p><b>BRIDGE-5 is the whole of the write surface.</b> The bridge may add an observation, a
 * candidate or an evidence link. It may not alter a claim, a confidence, a goal, a safety
 * policy, a disposition or an adjudication. Every record the bridge writes therefore arrives
 * in the disposition the epistemic store gives to something nobody has checked, and the
 * bridge never raises a status.</p>
 *
 * <p><b>The effect is durable-idempotent, not just idempotent.</b> BRIDGE-8 requires the
 * idempotency to survive the death of either process. The in-memory registry of the protocol
 * cannot promise that, so the <b>claim identity is derived from the request id</b>: a message
 * that arrives twice asks for a claim that already exists, and the sink recognises it and
 * writes nothing. The store is therefore the ledger, and the ledger cannot be lost by a
 * restart.</p>
 *
 * <p><b>No secret, no trust.</b> When the runtime is given no verification secret, the
 * receiver refuses every message with {@code NO_SECRET_CONFIGURED}. There is no degraded mode
 * and no default secret: a bridge that cannot authenticate a call does not serve it.</p>
 *
 * <p><b>Determinism.</b> The clock is a parameter of the constructor, the identity of a
 * bridged claim is derived from the request id, and nothing here reads a clock or a random
 * source.</p>
 */
public final class BridgeService {

    /** The authority a bridged report's source carries: nobody has checked it. */
    public static final int BRIDGED_REPORT_AUTHORITY = 20;

    /** The freshness a bridged report's source carries: it was made just now. */
    public static final int BRIDGED_REPORT_FRESHNESS = 80;

    /** The quality of the evidence link a bridged observation produces. */
    public static final int BRIDGED_OBSERVATION_QUALITY = 10;

    /** The prefix of the identity of a claim that arrived over the bridge. */
    public static final String BRIDGED_CLAIM_PREFIX = "clm-bridge-";

    /** The prefix of the identity of a source that arrived over the bridge. */
    public static final String BRIDGED_SOURCE_PREFIX = "src-bridge-";

    private final KnowledgeWorkbench workbench;
    private final BridgeReceiver receiver;
    private final BridgeAudit audit;
    private final EpistemicBridgeSink sink;
    private final String workspaceId;
    private final Supplier<String> clock;

    /**
     * Creates the service.
     *
     * @param workbench    the epistemic store of chain A; the ONLY writer of the bridge's
     *                     effects
     * @param verifier     the signer that verifies incoming messages; {@code null} means that
     *                     every message is refused
     * @param granted      the rights the bridge grants to its senders
     * @param budget       the budget enforced before the store is touched
     * @param idempotency  the registry that makes a repeated delivery recognisable
     * @param workspaceId  the workspace the bridged records belong to
     * @param clock        the source of the instants written with the records
     */
    public BridgeService(KnowledgeWorkbench workbench, BridgeSigner verifier,
                         Set<BridgeCapability> granted, BridgeBudget budget,
                         BridgeIdempotency idempotency, BridgeAudit audit, String workspaceId,
                         Supplier<String> clock) {
        if (workbench == null) {
            throw new IllegalArgumentException("workbench must not be null");
        }
        if (budget == null) {
            throw new IllegalArgumentException("budget must not be null");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException("idempotency must not be null");
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspace id must not be blank");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (audit == null) {
            throw new IllegalArgumentException("audit must not be null");
        }
        this.workbench = workbench;
        this.audit = audit;
        this.workspaceId = workspaceId;
        this.clock = clock;
        this.sink = new EpistemicBridgeSink(workbench, workspaceId, clock);
        this.receiver = new BridgeReceiver(verifier, granted, budget, idempotency, sink);
    }

    /**
     * Accepts one message.
     *
     * @param envelope the message
     * @return the decision
     */
    public BridgeDecision accept(BridgeEnvelope envelope) {
        BridgeDecision decision = receiver.receive(envelope, nowMillis());
        audit.append(io.matrix.contract.bridge.BridgeAuditRecord.of(decision, envelope,
                clock.get()));
        return decision;
    }

    /**
     * Accepts a batch of messages.
     *
     * @param batch the messages
     * @return one decision per message, in the order of the batch
     */
    public List<BridgeDecision> acceptBatch(List<BridgeEnvelope> batch) {
        List<BridgeDecision> decisions = receiver.receiveBatch(batch, nowMillis());
        for (int i = 0; i < decisions.size() && i < batch.size(); i++) {
            audit.append(io.matrix.contract.bridge.BridgeAuditRecord.of(decisions.get(i),
                    batch.get(i), clock.get()));
        }
        return decisions;
    }

    /** The rights the bridge grants. */
    public Set<BridgeCapability> grantedCapabilities() {
        return receiver.grantedCapabilities();
    }

    /** The audit of the calls the bridge made and refused. */
    public List<io.matrix.contract.bridge.BridgeAuditRecord> auditLog() {
        return receiver.auditLog();
    }

    /** The workspace the bridged records belong to. */
    public String workspaceId() {
        return workspaceId;
    }

    /**
     * The epoch milliseconds of the current instant.
     *
     * <p>The protocol's budget is counted in epoch milliseconds, and the clock of the service
     * is a supplier of an instant string. The two are kept apart: the budget's counter is a
     * resource boundary and is allowed to read the system clock, while every record the
     * bridge writes carries the injected instant.</p>
     */
    private long nowMillis() {
        return System.currentTimeMillis();
    }
}
