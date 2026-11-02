package io.matrix.cognitive.algorithms;

import java.util.Set;
import java.util.function.Function;

/**
 * W44.2 — a validated procedure, offered to the selector as a method.
 *
 * <p>The {@link Procedure} record describes a cheap path that was validated against an expensive
 * reference on held-out cases, and it names the conditions under which it may be replayed. This
 * class is what makes that description selectable: it turns a stored procedure into a member of
 * the portfolio, with a capability the selector can read and refuse.</p>
 *
 * <p><b>The applicability domain becomes the precondition.</b> A procedure's
 * {@link Procedure#requiredFacts()} are exactly the facts that must hold for its answer to be
 * the answer the reference would have given. Putting them in
 * {@link AlgorithmCapability#requiredPreconditions()} means the selector enforces the domain
 * automatically: a request that does not establish the domain cannot select the procedure, and
 * the reference method takes over. That is the mechanism the W44 directive asks for — "a cached
 * procedure is reusable only inside its verified applicability domain" — expressed in the one
 * vocabulary the portfolio already has.</p>
 *
 * <p><b>The reference is the fallback.</b> The capability names it, so the decision record says
 * where to go when the domain no longer holds. A procedure without a reference is a trap, and
 * the {@link Procedure} record refuses to be built without one.</p>
 *
 * <p><b>The accuracy evidence is the measured agreement.</b> It comes from the validation run
 * that promoted the procedure, with its case count attached, so the selector scores the cheap
 * path against the reference using what was actually observed rather than an assumption.</p>
 *
 * <p>The cheap computation itself is supplied by the caller as a function: the procedure record
 * describes WHAT may be reused and WHEN, and it does not describe how to compute it. Keeping the
 * function outside the record is what lets the store hold procedures whose implementation lives
 * with the subsystem that owns the problem.</p>
 *
 * <p><b>Why the input type is a parameter.</b> A procedure competes with the reference method in
 * the SAME family, and the selector offers every method of a family the same payload. If the
 * procedure's input type were fixed to a wrapper of its own, the reference method could not
 * accept the same request and one of the two would be rejected for a type reason rather than a
 * substantive one. Making the input type a parameter keeps the payload the family's payload, so
 * the two methods are comparable and the choice between them is a choice about evidence.</p>
 *
 * @param <I> the family's input type
 */
public final class ProcedureMethod<I>
        implements AlgorithmMethod<I, ProcedureMethod.ProcedureOutput>, ProcedureBacked {

    /** The prefix that marks a capability id as a procedure rather than an algorithm. */
    public static final String ID_PREFIX = "procedure.";

    /**
     * The cost of replaying a validated procedure, in milliseconds.
     *
     * <p>CONVENTION: a procedure is a cheap path by construction — it was promoted precisely
     * because it was cheaper than the reference — so its declared cost is the smallest the
     * portfolio declares. The measured cost, when the caller has measured it, replaces this.</p>
     */
    public static final long MEASURED_COST_MILLIS = 1L;

    /**
     * The answer a procedure produced.
     *
     * @param answer what the procedure computed
     * @param procedureId the procedure that produced it
     */
    public record ProcedureOutput(Object answer, String procedureId) {
    }

    private final Procedure procedure;
    private final Function<I, Object> executor;
    private final AlgorithmCapability capability;

    /**
     * @param procedure the validated procedure this method replays
     * @param executor the cheap computation the procedure stands for
     */
    public ProcedureMethod(Procedure procedure, Function<I, Object> executor) {
        if (procedure == null) {
            throw new IllegalArgumentException("procedure must not be null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        this.procedure = procedure;
        this.executor = executor;
        this.capability = new AlgorithmCapability(
                ID_PREFIX + procedure.id(),
                procedure.version(),
                Set.of(procedure.problemFamily()),
                procedure.requiredFacts(),
                Set.of("the family's input type"),
                Set.of(ProcedureOutput.class.getName()),
                new AlgorithmCapability.ComplexityProfile(
                        DeclaredCost.declare("O(1)", MEASURED_COST_MILLIS, 0),
                        "O(1) beyond the answer itself"),
                new AlgorithmCapability.ResourceProfile(
                        "O(1) heap", true, false,
                        "the applicability domain does not hold, so the reference method takes over"),
                new AlgorithmCapability.AccuracyProfile(
                        "agreement with the reference on held-out cases",
                        procedure.validationAgreement(),
                        procedure.validationCases(),
                        "the held-out validation of the procedure " + procedure.id(),
                        procedure.promotedAt()),
                new AlgorithmCapability.DeterminismProfile(true,
                        "none: the procedure was validated on a fixed corpus"),
                new AlgorithmCapability.ReliabilityProfile(
                        "the applicability domain does not hold, so the reference method takes over",
                        false, "the reference method answers"),
                new AlgorithmCapability.VerificationStrategy(
                        "the procedure was validated against its reference on held-out cases",
                        "io.matrix.cognitive.algorithms.ProcedureValidator.validate"),
                procedure.referenceAlgorithmId());
    }

    /** The procedure this method replays. */
    public Procedure procedure() {
        return procedure;
    }

    @Override
    public String procedureId() {
        return procedure.id();
    }

    @Override
    public String referenceAlgorithmId() {
        return procedure.referenceAlgorithmId();
    }

    @Override
    public AlgorithmCapability capability() {
        return capability;
    }

    @Override
    public boolean preconditionsHold(I input) {
        // The applicability DOMAIN is the declared precondition, and the selector checks it
        // against the request's facts. What the method checks itself is whether it is still
        // alive: a retired procedure must not be replayed even by a caller that asks for it.
        return input != null && procedure.active();
    }

    @Override
    public ProcedureOutput execute(I input, ComputeBudget budget) {
        if (!preconditionsHold(input)) {
            throw new IllegalStateException(
                    "the procedure " + procedure.id() + " is retired or was given no input, "
                            + "so it must not be replayed");
        }
        return new ProcedureOutput(executor.apply(input), procedure.id());
    }
}
