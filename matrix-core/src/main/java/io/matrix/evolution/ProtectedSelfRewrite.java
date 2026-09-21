package io.matrix.evolution;

import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import io.matrix.ethics.StructuralSafetyGuard;
import io.matrix.ethics.StructuralSafetyGuard.SafetyVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Self-evolution gate — wraps self-modification requests in a two-stage review:
 *
 * <ol>
 *   <li><b>L7 ethics audit</b> — proposed changes are mapped to a textual
 *       description, then run through {@link EthicalFilter.evaluate}. REJECTED → blocked.</li>
 *   <li><b>L5/L7 structural safety</b> — for changes tagged {@code medium} or higher,
 *       a synthetic operation is sent through {@link StructuralSafetyGuard.evaluate}
 *       which may require human-in-the-loop approval.</li>
 * </ol>
 *
 * <p>If both gates pass, the {@code requester} (a {@link SelfRewrite}) is invoked.
 * Otherwise the change is refused and a {@link Refusal} is produced for audit.
 *
 * <p>Ref: L7 §4 (self-modification with ethics + structural gates).
 */
public final class ProtectedSelfRewrite {

    private static final Logger log = LoggerFactory.getLogger(ProtectedSelfRewrite.class);

    private final EthicalFilter ethics;
    private final StructuralSafetyGuard guard;

    public ProtectedSelfRewrite(EthicalFilter ethics, StructuralSafetyGuard guard) {
        if (ethics == null) throw new IllegalArgumentException("ethics required");
        if (guard == null) throw new IllegalArgumentException("guard required");
        this.ethics = ethics;
        this.guard = guard;
    }

    /** A proposed self-rewrite awaiting approval. */
    public record RewriteRequest(
            String id,
            String ownerClass,
            String methodName,
            String description,
            java.util.Map<String, String> context) {

        public RewriteRequest {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(ownerClass, "ownerClass");
            Objects.requireNonNull(methodName, "methodName");
            Objects.requireNonNull(description, "description");
            context = context == null ? java.util.Map.of() : new TreeMap<>(context);
        }

        public String syntheticOperation() {
            // Method name alone — so StructuralSafetyGuard's gated-operations
            // set (e.g. {@code deploy_production}) matches structurally.
            // Provenance (ownerClass) is preserved separately in description().
            return methodName;
        }

        /** Approximate severity: heuristic, overridable via {@code context}. */
        public String severity() {
            String s = context.get("severity");
            return s == null ? "low" : s;
        }
    }

    /** Result: APPROVED + applied, ESCALATED + awaiting human gate, REJECTED + refused. */
    public record Decision(
            Status status,
            String reason,
            EthicalVerdict ethicalVerdict,
            SafetyVerdict structuralVerdict,
            RewriteRequest request,
            Instant timestamp) {

        public enum Status { APPROVED, ESCALATED, REJECTED }

        public boolean approved() { return status == Status.APPROVED; }
        public boolean blocked() { return status == Status.REJECTED; }
    }

    /** Functional hook: do the actual change once both gates pass. */
    @FunctionalInterface
    public interface ApplyRewrite {
        void apply(RewriteRequest request);
    }

    /**
     * Decides whether {@code req} can proceed and (on approval) runs {@code applier}.
     * Returns the {@link Decision} regardless of outcome.
     */
    public Decision gateAndApply(RewriteRequest req, ApplyRewrite applier) {
        Objects.requireNonNull(req, "req");

        // ── Gate 1: ethics ──
        EthicalVerdict v = ethics.evaluate(req.description(), java.util.List.of());
        if (v == EthicalVerdict.REJECTED) {
            log.warn("Self-rewrite REJECTED by ethics: {} / {}",
                    req.ownerClass(), req.methodName());
            return new Decision(Decision.Status.REJECTED,
                    "EthicalFilter REJECTED: " + req.description(),
                    v, null, req, Instant.now());
        }

        // ── Gate 2: structural safety ──
        SafetyVerdict sv = guard.evaluate(req.syntheticOperation(), req.context());
        if (sv.decision() == StructuralSafetyGuard.Decision.BLOCKED) {
            return new Decision(Decision.Status.REJECTED,
                    "StructuralSafetyGuard BLOCKED: " + sv.reason(),
                    v, sv, req, Instant.now());
        }
        if (sv.decision() == StructuralSafetyGuard.Decision.REQUIRES_APPROVAL) {
            return new Decision(Decision.Status.ESCALATED,
                    "Human gate requested: " + sv.reason() + " (id=" + sv.gateId().orElse("?") + ")",
                    v, sv, req, Instant.now());
        }

        // ── Apply ──
        if (applier != null) {
            try {
                applier.apply(req);
            } catch (RuntimeException re) {
                return new Decision(Decision.Status.REJECTED,
                        "Applier threw: " + re.getMessage(),
                        v, sv, req, Instant.now());
            }
        }
        return new Decision(Decision.Status.APPROVED,
                "Both gates passed", v, sv, req, Instant.now());
    }

    /** Convenience: gate only (no application). */
    public Decision gateOnly(RewriteRequest req) {
        return gateAndApply(req, null);
    }
}
