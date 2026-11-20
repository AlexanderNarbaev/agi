package io.matrix.cognitive.report;

import io.matrix.cognitive.algorithms.AlgorithmCapability;
import io.matrix.cognitive.algorithms.AlgorithmRefusalException;
import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.cognitive.algorithms.DeclaredCost;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.algorithms.ProcedureBacked;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Source;

import java.util.List;
import java.util.Set;

/**
 * CRJ-01 / OD-082 — the domain-A procedure, offered to the kernel as a method of the
 * knowledge-report family.
 *
 * <p><b>What it does.</b> It answers the question from the procedure's validated SUPPORT SET:
 * the workspace is read as the sources the validation covered, and everything after the
 * reading is the asking path's own — the demand analysis, the premise assessment, the
 * corrections and the notes. The path is therefore a real path that produces a real answer,
 * and it is cheaper than the reference because it reads fewer claims.</p>
 *
 * <p><b>Why it is a member of the family and not a wrapper.</b> The selector offers every
 * method of a family the same payload and expects the same OUTPUT type, so a procedure that
 * returned a different output would be rejected for a type reason rather than a substantive
 * one. This method therefore speaks the family's own type — {@link ReportJourney.Result} — and
 * it writes the artifact through the same writer and the same identity the reference method
 * uses, so the action's contract is unchanged.</p>
 *
 * <p><b>Why the applicability is a precondition.</b> The procedure's domain is checked by the
 * selector, because the method declares the procedure's facts as its preconditions: a request
 * that does not establish that the support set is still present cannot select this method, and
 * the reference method answers instead. The method checks the same thing itself — the support
 * set's sources must still be in the workspace — because a method that is asked to run
 * directly must not run outside its domain.</p>
 *
 * <p><b>What its accuracy evidence is.</b> The measured agreement with the reference on
 * INDEPENDENT cases, with the case count attached, exactly as the portfolio's own procedure
 * bridge states it. The score the selector computes is built on a measurement rather than on
 * a promise.</p>
 */
public final class ReportProcedureMethod
        implements ReportJourneyPlanner.JourneyMethod, ProcedureBacked {

    /** The prefix that marks a capability id as a procedure rather than an algorithm. */
    public static final String ID_PREFIX = "procedure.";

    /**
     * The cost of replaying a validated procedure, in milliseconds.
     *
     * <p>CONVENTION: a procedure is a cheap path by construction — it was promoted precisely
     * because it was cheaper than the reference — so its declared cost is the smallest the
     * portfolio declares. The MEASURED cost replaces it in the planner's ledger.</p>
     */
    public static final long MEASURED_COST_MILLIS = 1L;

    private final Procedure procedure;
    private final Set<String> support;
    private final AlgorithmCapability capability;

    /**
     * @param procedure the validated procedure this method replays
     */
    public ReportProcedureMethod(Procedure procedure) {
        if (procedure == null) {
            throw new IllegalArgumentException("procedure must not be null");
        }
        this.procedure = procedure;
        this.support = ReportProcedure.supportOf(procedure);
        this.capability = new AlgorithmCapability(
                ID_PREFIX + procedure.id(),
                procedure.version(),
                Set.of(procedure.problemFamily()),
                procedure.requiredFacts(),
                Set.of(ReportJourney.Problem.class.getName()),
                Set.of(ReportJourney.Result.class.getName()),
                new AlgorithmCapability.ComplexityProfile(
                        DeclaredCost.declare("O(claims of the support set), against the "
                                + "reference's O(claims of the workspace)", MEASURED_COST_MILLIS, 0),
                        "O(claims of the support set)"),
                new AlgorithmCapability.ResourceProfile(
                        "one workspace snapshot, read as the support set; the snapshot is not "
                                + "held by the method", true, false,
                        "the support set is no longer present, so the reference method takes over"),
                new AlgorithmCapability.AccuracyProfile(
                        procedure.validated()
                                ? "agreement with the reference on INDEPENDENT cases"
                                : "agreement with the reference over the model's own data "
                                        + "(IN-SAMPLE: not an independent validation)",
                        procedure.validationAgreement(),
                        procedure.validationCases(),
                        procedure.validated()
                                ? "the independent validation of the procedure " + procedure.id()
                                : "the model's own observations, for the procedure " + procedure.id(),
                        procedure.promotedAt()),
                new AlgorithmCapability.DeterminismProfile(true,
                        "none: the procedure reads the workspace as it is and applies a "
                                + "validated support set to it"),
                new AlgorithmCapability.ReliabilityProfile(
                        "the support set is no longer present, so the reference method takes over",
                        false, "the reference method answers"),
                new AlgorithmCapability.VerificationStrategy(
                        procedure.validated()
                                ? "the procedure was validated against its reference on "
                                        + "independent cases"
                                : "the procedure agrees with its reference over the model's own "
                                        + "data; it is a derived shortcut, not a validated procedure",
                        "io.matrix.cognitive.algorithms.ProcedureValidator.validate"),
                procedure.referenceAlgorithmId());
    }

    /** The procedure this method replays. */
    public Procedure procedure() {
        return procedure;
    }

    /** The support set the procedure answers from. */
    public Set<String> support() {
        return support;
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
    public boolean preconditionsHold(ReportJourney.Problem input) {
        if (input == null || !procedure.active() || support.isEmpty()) {
            return false;
        }
        // the domain must still hold: the support set's sources must be in the workspace
        // as it is now, and the question must be one the workspace can be read for
        Set<String> present = input.workbench().workspace(input.workspaceId()).sources().stream()
                .map(Source::id)
                .collect(java.util.stream.Collectors.toSet());
        return present.containsAll(support);
    }

    @Override
    public ReportJourney.Result execute(ReportJourney.Problem input, ComputeBudget budget) {
        if (!preconditionsHold(input)) {
            throw new AlgorithmRefusalException("the procedure " + procedure.id()
                    + " does not hold for this request: its support set is no longer present, "
                    + "so the reference method must answer");
        }
        AbstractionLevel level = input.level();
        Answer answer = input.workbench().ask(input.workspaceId(), input.question(), level, support);
        byte[] content = ReportBuilder.markdown(input.workspaceId(), input.question(), answer)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var write = input.writer().write(input.identity(), input.reportName(), content);
        return new ReportJourney.Result(answer, content, write, input.workspaceFingerprint(), false);
    }

    /** The claim ids the answer cited — the shape two answers are compared by. */
    static List<String> citedClaims(Answer answer) {
        if (answer == null) {
            return List.of();
        }
        return answer.claims().stream().map(Claim::id).sorted().toList();
    }
}
