package io.matrix.ethics;

import io.matrix.audit.FrozenFNLHashChain;
import io.matrix.audit.HashChain;
import io.matrix.audit.HashLink;
import io.matrix.ethics.frozen.FrozenEthicalFNL;
import io.matrix.ethics.frozen.FrozenEthicalFNL.Result;

import java.util.BitSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Highest-level FROZEN-FNL orchestrator: combines the canonical
 * {@link FrozenEthicalFNL} network with a {@link HashChain} so every
 * evaluation produces a tamper-evident audit trail.
 *
 * <p>This is the entry point the rest of matrix-core uses when it wants
 * a "guaranteed immutable" ethics decision. The chain is in-memory by
 * default but can be rehydrated from any append-only medium via
 * {@link #restoreFrom(java.util.List)}.
 *
 * <p>Configuration:
 * <ul>
 *   <li>{@link EthicalFilter#FROZEN_FNL} is the canonical 6-neuron network.</li>
 *   <li>The {@link HashChain} starts empty; call {@link #attestNow()} to
 *       record the network fingerprint, then {@link #evaluate(String)} to
 *       record every decision.</li>
 *   <li>Use {@link #verifyAuditTrail()} after a session to confirm
 *       no retroactive tampering occurred.</li>
 * </ul>
 *
 * <p>Ref: L7 §5 (FROZEN contract), L12 §4 (legal audit), L5 §5.5 (immutability).
 */
public final class FROZENFNLGuardian {

    private final FrozenEthicalFNL fnl;
    private final FrozenFNLHashChain audit;
    private final AtomicLong totalDecisions = new AtomicLong();
    private final AtomicLong totalRejections = new AtomicLong();

    public FROZENFNLGuardian() {
        this(EthicalFilter.FROZEN_FNL, new FrozenFNLHashChain(EthicalFilter.FROZEN_FNL));
    }

    public FROZENFNLGuardian(FrozenEthicalFNL fnl, FrozenFNLHashChain audit) {
        this.fnl = Objects.requireNonNull(fnl, "fnl");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    /**
     * Evaluate {@code text} through the FROZEN FNL, recording the decision
     * in the audit hash chain. Returns the verdict.
     */
    public EthicalVerdict evaluate(String text) {
        Objects.requireNonNull(text, "text");
        BitSet features = fnl.featureExtractor().extract(text);
        Result r = fnl.evaluate(features);
        totalDecisions.incrementAndGet();
        if (!r.approved()) totalRejections.incrementAndGet();
        audit.recordDecision(r.approved() ? "APPROVED" : "REJECTED", features);
        return r.approved() ? EthicalVerdict.APPROVED : EthicalVerdict.REJECTED;
    }

    /**
     * Append an attestation link to the audit chain. Use this once per
     * session, or whenever the FROZEN network is verified/rebuilt.
     */
    public HashLink attestNow() {
        return audit.attestNetwork();
    }

    /**
     * Verify the entire audit trail is intact. Returns {@code true} when
     * every link's hash matches its fields and every previousHash matches
     * the previous link's hash.
     */
    public boolean verifyAuditTrail() {
        return audit.chain().verify();
    }

    /** Number of decisions recorded so far. */
    public long totalDecisions() { return totalDecisions.get(); }
    /** Number of REJECTED decisions so far. */
    public long totalRejections() { return totalRejections.get(); }

    public HashChain chain() { return audit.chain(); }
    public FrozenEthicalFNL fnl() { return fnl; }

    /** Replace the chain from a previously-persisted snapshot. */
    public void restoreFrom(java.util.List<HashLink> links) {
        audit.chain().restore(links);
    }

    /** Audit summary for monitoring / dashboards. */
    public String summary() {
        return String.format(Locale.ROOT,
                "FROZENFNLGuardian[decisions=%d rejected=%d chain=%s]",
                totalDecisions.get(), totalRejections.get(), audit.chain().summary());
    }
}
