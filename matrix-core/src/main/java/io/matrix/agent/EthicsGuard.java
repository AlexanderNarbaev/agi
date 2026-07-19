package io.matrix.agent;

import io.matrix.audit.HashLink;
import io.matrix.ethics.EthicalVerdict;
import io.matrix.ethics.FROZENFNLGuardian;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Adapter that gates the {@link AgentLoop} through the FROZEN ethical FNL.
 *
 * <p>Each agent decision (action + effect description) is funnelled through
 * {@link FROZENFNLGuardian#evaluate(String)} before the agent commits to
 * executing the action. REJECT verdicts short-circuit execution and produce
 * a FROZEN audit link.
 *
 * <p>This is the missing wiring that closes the loop between the agent loop
 * and the FROZEN contract:
 * <ul>
 *   <li>Every agent action is gated through the FROZEN FNL</li>
 *   <li>REJECT actions are NOT executed (effect is suppressed)</li>
 *   <li>Every decision is recorded in the FROZEN audit chain (tamper-evident)</li>
 *   <li>The guardian's counter reflects gate traffic, providing monitoring</li>
 * </ul>
 *
 * <p>Ref: L7 §3.1 (FROZEN contract), Wave 22-C (BotEthicsPipeline), Wave 31.
 */
public final class EthicsGuard {

    private final FROZENFNLGuardian guardian;
    private final Function<HashLink, Void> sink;
    private final AtomicLong totalChecked = new AtomicLong();
    private final AtomicLong totalAllowed = new AtomicLong();
    private final AtomicLong totalBlocked = new AtomicLong();

    /** Standard constructor: gate through the given FROZEN guardian. */
    public EthicsGuard(FROZENFNLGuardian guardian) {
        this(guardian, link -> null);
    }

    /** Constructor with a sink for additional audit-link processing. */
    public EthicsGuard(FROZENFNLGuardian guardian, Function<HashLink, Void> sink) {
        this.guardian = Objects.requireNonNull(guardian, "guardian");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /**
     * Gate an action description through the FROZEN FNL.
     * Returns a {@link Gate} describing the verdict and the audit link.
     */
    public Gate gate(String actionDescription) {
        Objects.requireNonNull(actionDescription, "actionDescription");
        EthicalVerdict verdict = guardian.evaluate(actionDescription);
        totalChecked.incrementAndGet();
        // The guardian appends a decision link; we capture the last link
        // so the caller can record it externally.
        HashLink last = lastLink();
        if (sink != null) sink.apply(last);
        return switch (verdict) {
            case APPROVED -> {
                totalAllowed.incrementAndGet();
                yield Gate.allow(last);
            }
            case REJECTED -> {
                totalBlocked.incrementAndGet();
                yield Gate.deny(last);
            }
            case ESCALATED -> Gate.escalate(last);
            case MODIFIED -> Gate.allow(last);  // modified = still allowed (with caveats)
        };
    }

    /** Convenience: returns true iff the action is allowed. */
    public boolean isAllowed(String actionDescription) {
        return gate(actionDescription).allowed();
    }

    public FROZENFNLGuardian guardian() { return guardian; }
    public long totalChecked() { return totalChecked.get(); }
    public long totalAllowed() { return totalAllowed.get(); }
    public long totalBlocked() { return totalBlocked.get(); }

    private HashLink lastLink() {
        var chain = guardian.chain();
        if (chain.size() == 0) return null;
        // Safe lookup via snapshot.
        var snap = chain.snapshot();
        return snap.get(snap.size() - 1);
    }

    /**
     * Result of a single gating decision.
     */
    public record Gate(boolean allowed, boolean escalated, HashLink auditLink) {
        public static Gate allow(HashLink link) { return new Gate(true, false, link); }
        public static Gate deny(HashLink link) { return new Gate(false, false, link); }
        public static Gate escalate(HashLink link) { return new Gate(false, true, link); }

        /** String representation suitable for log output. */
        public String summary() {
            String state = allowed ? "ALLOW" : (escalated ? "ESCALATE" : "DENY");
            String hash = auditLink == null ? "no-link" : auditLink.hash().substring(0, 12);
            return String.format(Locale.ROOT, "Gate[%s link=%s]", state, hash);
        }
    }
}