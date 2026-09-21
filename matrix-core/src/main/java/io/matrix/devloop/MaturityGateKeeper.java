package io.matrix.devloop;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Maturity-gate keeper: monotonic MA-0…MA-5 promotion (SPEC-000#fr-5).
 *
 * <p>Transitions are forward-only (monotonicity invariant, SPEC-000 INV-3): there is no
 * {@code demote} API, so no sequence of criterion evaluations can lower the level. A
 * promotion from {@code current} to {@code current.next()} succeeds only when a
 * threshold-checker is registered for the target gate AND that checker accepts the supplied
 * {@link GateCriteria}. Criteria are supplied as a {@code Map<MaturityLevel, Predicate<GateCriteria>>}
 * (gate → threshold-checker).
 *
 * <p>Drift-based demotion (SPEC-000#fr-5) is a quarantine of permissions handled out-of-band,
 * not a rollback of this monotonic ladder.
 */
public final class MaturityGateKeeper {

    private final Map<MaturityLevel, Predicate<GateCriteria>> criteria;
    private MaturityLevel current = MaturityLevel.MA_0_SANDBOX;

    /**
     * @param criteria gate→threshold-checker map; keyed by the TARGET level of each gate
     *                 (e.g. the {@code MA_1_LOCAL} entry guards the MA-0→MA-1 promotion)
     */
    public MaturityGateKeeper(Map<MaturityLevel, Predicate<GateCriteria>> criteria) {
        this.criteria = Map.copyOf(criteria);
    }

    /** Current maturity level. */
    public MaturityLevel current() {
        return current;
    }

    /**
     * Attempt a forward transition to the next level.
     *
     * @param evidence observable criteria evidence for the target gate's checker
     * @return approved transition (with the new level) or a denied result with a reason
     */
    public TransitionResult advance(GateCriteria evidence) {
        Objects.requireNonNull(evidence, "evidence");
        MaturityLevel target = current.next();
        if (target == current) {
            return TransitionResult.denied("already at ceiling " + current);
        }
        Predicate<GateCriteria> checker = criteria.get(target);
        if (checker == null) {
            return TransitionResult.denied("no criterion registered for gate " + target);
        }
        if (!checker.test(evidence)) {
            return TransitionResult.denied("criteria not satisfied for " + target);
        }
        current = target;
        return TransitionResult.approved(target);
    }

    /** Result of a transition attempt. */
    public record TransitionResult(boolean approved, MaturityLevel newLevel, String reason) {
        public static TransitionResult approved(MaturityLevel level) {
            return new TransitionResult(true, level, "approved");
        }

        public static TransitionResult denied(String reason) {
            return new TransitionResult(false, null, reason);
        }
    }
}
