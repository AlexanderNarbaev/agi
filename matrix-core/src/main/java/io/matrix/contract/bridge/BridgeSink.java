package io.matrix.contract.bridge;

/**
 * W51 — the port through which an accepted message reaches the epistemic store (BRIDGE-1).
 *
 * <p><b>Why the bridge does not write to the store itself.</b> BRIDGE-1 requires the bridge to
 * be an explicit, named, opt-in component with one owner, and BRIDGE-10 requires it to be the
 * only class allowed to reach both chains. A class that opened the SQLite file itself would
 * be a class that reaches the persistence stack — and the isolation gate of
 * {@code docs-v2/engineering/W45.2-CHAIN-BOUNDARY.md} exists precisely to prove that chain B
 * reaches no class that can open a database. The bridge therefore decides and audits, and the
 * write is delegated to whoever owns the store.</p>
 *
 * <p><b>Why this keeps the package pure.</b> The implementation of this interface is the one
 * place in the bridge that knows about the epistemic store, and it lives on the chain A side.
 * Everything in {@code io.matrix.contract.bridge} — the protocol, the envelope, the payload,
 * the budget, the audit record and the idempotency registry — is free of the store, of
 * {@code java.sql} and of the database path, which is what lets chain B read the protocol
 * without pulling in anything of chain A.</p>
 *
 * <p><b>What the sink is promised.</b> The bridge calls {@link #accept(BridgeEnvelope)} at
 * most once per request id (BRIDGE-8), and only after the message has satisfied every check.
 * The sink is responsible for its own atomicity: if it throws, the bridge records no outcome,
 * and a retry will try again — which is the right behaviour for a write whose fate is
 * unknown.</p>
 */
@FunctionalInterface
public interface BridgeSink {

    /**
     * Records an accepted message.
     *
     * @param envelope the message that satisfied the contract
     */
    void accept(BridgeEnvelope envelope);
}
