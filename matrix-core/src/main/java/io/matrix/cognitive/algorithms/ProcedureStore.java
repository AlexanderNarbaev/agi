package io.matrix.cognitive.algorithms;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * W44.2 — the store of validated, reusable procedures, and the rules that retire them.
 *
 * <p>The W44 directive §22 gives the whole life cycle in one sentence: solve the problem
 * expensively, record the reference result, generate a cheaper candidate, validate it on held-out
 * cases, promote it only if it preserves the required quality, store the applicability
 * conditions, monitor it, and invalidate it after drift. This class holds the procedures and
 * enforces the two rules that are about state rather than about validity — promotion and
 * retirement.</p>
 *
 * <p><b>Promotion</b> is delegated to {@link ProcedureValidator}: a procedure enters the store
 * only after it has agreed with its reference on every held-out case, and the stored record
 * carries the measured agreement and the case count, so the score the selector computes later is
 * built on a measurement rather than on a promise.</p>
 *
 * <p><b>Retirement</b> is the rule this class exists for. A procedure is replayed WITHOUT being
 * checked — that is the whole point of the cheap path — so something has to notice when the world
 * moved. {@link #recordUse(String, boolean, double)} tracks how often a procedure was used and how
 * often it failed, and compares the agreement observed on recent uses against
 * {@link #MINIMUM_AGREEMENT}. Below that line the procedure is invalidated, and an invalidated
 * procedure is no longer offered by {@link #findApplicable(String, Set)}, so the reference
 * method takes over again.</p>
 *
 * <p><b>No clock.</b> The procedure record carries a {@code promotedAt} and an
 * {@code invalidatedAt} as strings, and they are supplied by the caller rather than read here.
 * A package whose decisions must not depend on the wall clock does not read one — and this
 * package's only clock read is in {@link CostLedger#measure(String, java.util.function.Supplier)}.</p>
 *
 * <p>Thread safety: synchronized on the store. The procedure set is small and is written far
 * less often than it is read.</p>
 */
public final class ProcedureStore {

    /**
     * The agreement below which a procedure is retired.
     *
     * <p>CONVENTION: a procedure that was validated perfectly and now agrees less than this
     * most of the time has drifted out of its applicability domain. The line is high because a
     * procedure is replayed unverified: the cost of a wrong replay is the cost of a wrong answer
     * nobody checked.</p>
     */
    public static final double MINIMUM_AGREEMENT = 0.95;

    /**
     * The outcome of a promotion attempt.
     *
     * @param promoted whether the procedure entered the store
     * @param reason why, in words a reader can act on
     * @param procedure the stored procedure, or the candidate when it was refused
     */
    public record PromotionOutcome(boolean promoted, String reason, Procedure procedure) {

        public PromotionOutcome {
            reason = reason == null ? "" : reason;
        }
    }

    private final Map<String, Procedure> procedures = new LinkedHashMap<>();

    /**
     * Validates a candidate and stores it when it passes.
     *
     * @param candidate the procedure being promoted; its {@code promotedAt} is used as supplied
     * @param cases the held-out validation cases
     * @return the outcome; the procedure is in the store only when {@code promoted} is true
     */
    public synchronized PromotionOutcome promote(Procedure candidate, List<ValidationCase> cases) {
        ProcedureValidator.ValidationOutcome validation = ProcedureValidator.validate(candidate, cases);
        if (!validation.accepted()) {
            return new PromotionOutcome(false, validation.reason(), candidate);
        }
        Procedure stored = new Procedure(
                candidate.id(),
                candidate.name(),
                candidate.problemFamily(),
                candidate.version(),
                candidate.applicability(),
                candidate.requiredFacts(),
                candidate.referenceAlgorithmId(),
                validation.totalCases(),
                validation.agreement(),
                candidate.useCount(),
                candidate.failureCount(),
                candidate.promotedAt(),
                null,
                candidate.validationKind());
        procedures.put(stored.id(), stored);
        return new PromotionOutcome(true, validation.reason(), stored);
    }

    /**
     * The procedure with the given id.
     *
     * @param id the procedure id
     * @return the procedure, or empty when nothing is stored under that id
     */
    public synchronized Optional<Procedure> byId(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(procedures.get(id));
    }

    /** Every stored procedure, in promotion order. */
    public synchronized List<Procedure> all() {
        return List.copyOf(procedures.values());
    }

    /** How many procedures are stored. */
    public synchronized int size() {
        return procedures.size();
    }

    /**
     * The active procedure that applies to a family under a set of facts.
     *
     * <p>A procedure applies when it is still active and when the facts satisfy its
     * {@link Procedure#requiredFacts()}. The applicability domain is checked HERE rather than
     * assumed, because a procedure that was validated under one set of conditions and replayed
     * under another is not a validated procedure any more.</p>
     *
     * @param family the problem family
     * @param facts the facts known about the request
     * @return the applicable procedure, or empty when none applies
     */
    public synchronized Optional<Procedure> findApplicable(String family, Set<String> facts) {
        for (Procedure procedure : procedures.values()) {
            if (!procedure.active()) {
                continue;
            }
            if (!procedure.problemFamily().equals(family)) {
                continue;
            }
            if (procedure.applicable(facts)) {
                return Optional.of(procedure);
            }
        }
        return Optional.empty();
    }

    /**
     * Records one use of a procedure and applies the drift rule.
     *
     * <p>The observed agreement is what the caller saw when it checked the procedure's answer
     * against the reference — on the cheap path that check is optional, and when it is skipped
     * the agreement observed is simply the last known one, which the caller supplies.</p>
     *
     * @param id the procedure id
     * @param succeeded whether the use completed without an error
     * @param observedAgreement the agreement the caller measured on this use, in [0,1]
     * @return the updated procedure, invalidated when the agreement fell below the threshold
     */
    public synchronized Procedure recordUse(String id, boolean succeeded, double observedAgreement) {
        Procedure current = procedures.get(id);
        if (current == null) {
            throw new IllegalArgumentException("no procedure is stored under the id: " + id);
        }
        Procedure updated = current.used(succeeded);
        if (observedAgreement < MINIMUM_AGREEMENT) {
            updated = new Procedure(updated.id(), updated.name(), updated.problemFamily(),
                    updated.version(), updated.applicability(), updated.requiredFacts(),
                    updated.referenceAlgorithmId(), updated.validationCases(),
                    updated.validationAgreement(), updated.useCount(), updated.failureCount(),
                    updated.promotedAt(),
                    "the measured agreement fell to " + observedAgreement + ", below the threshold of "
                            + MINIMUM_AGREEMENT,
                    updated.validationKind());
        }
        procedures.put(id, updated);
        return updated;
    }

    /**
     * Retires a procedure explicitly.
     *
     * @param id the procedure id
     * @param reason why it was retired
     * @return the retired procedure
     */
    public synchronized Procedure invalidate(String id, String reason) {
        Procedure current = procedures.get(id);
        if (current == null) {
            throw new IllegalArgumentException("no procedure is stored under the id: " + id);
        }
        Procedure retired = new Procedure(current.id(), current.name(), current.problemFamily(),
                current.version(), current.applicability(), current.requiredFacts(),
                current.referenceAlgorithmId(), current.validationCases(),
                current.validationAgreement(), current.useCount(), current.failureCount(),
                current.promotedAt(), reason == null || reason.isBlank() ? "retired" : reason,
                current.validationKind());
        procedures.put(id, retired);
        return retired;
    }

    /** The procedures that are still active. */
    public synchronized List<Procedure> active() {
        List<Procedure> active = new ArrayList<>();
        for (Procedure procedure : procedures.values()) {
            if (procedure.active()) {
                active.add(procedure);
            }
        }
        return List.copyOf(active);
    }
}
