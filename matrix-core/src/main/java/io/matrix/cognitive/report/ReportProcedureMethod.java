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
import io.matrix.knowledge.EvidenceAcquisition;
import io.matrix.knowledge.EvidenceAcquisitionRequest;
import io.matrix.knowledge.PrerequisiteAssessment;
import io.matrix.knowledge.ScopeAssessment;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.SupportAdequacy;

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
     * The cost the promotion measured for this procedure, in nanoseconds; -1 when nothing was
     * measured.
     */
    private final long measuredCostNanos;

    /**
     * The scope verdict of the last question this method was asked about, and the key of
     * the request it was made for.
     */
    private ScopeAssessment assessment;

    /** The reason of the last refusal, when the method was refused. */
    private String lastRefusal;
    private String adequacyKey;

    /**
     * The acquisition request the last refusal carried, when the refusal was for a missing
     * prerequisite.
     */
    private io.matrix.knowledge.EvidenceAcquisitionRequest acquisition;

    /**
     * @param procedure the validated procedure this method replays
     */
    public ReportProcedureMethod(Procedure procedure) {
        this(procedure, null);
    }

    /**
     * The method, with the cost its promotion measured.
     *
     * <p><b>Why the measured cost changes what the method declares.</b> A procedure's FIRST
     * selection is made before it has ever run, so the selector weighs it by its declared
     * cost — and a declared cost is a promise. The promotion has already run the restricted
     * reading on its held-out cases, so it knows what that reading actually costs, and a
     * method that declares the MEASUREMENT makes the first decision rest on a measurement
     * rather than on a promise.</p>
     *
     * <p><b>What the label says.</b> The label names what was measured and over how many
     * cases, so a reader can see that the number is a reading's cost and not the journey's:
     * the artifact write is common to both paths and is not part of the difference.</p>
     *
     * @param procedure        the validated procedure this method replays
     * @param measuredCostNanos the cost the promotion measured, in nanoseconds; null when
     *                        nothing was measured
     */
    public ReportProcedureMethod(Procedure procedure, Long measuredCostNanos) {
        if (procedure == null) {
            throw new IllegalArgumentException("procedure must not be null");
        }
        this.procedure = procedure;
        this.measuredCostNanos = measuredCostNanos == null ? -1L : measuredCostNanos;
        this.support = ReportProcedure.supportOf(procedure);
        this.capability = new AlgorithmCapability(
                ID_PREFIX + procedure.id(),
                procedure.version(),
                Set.of(procedure.problemFamily()),
                procedure.requiredFacts(),
                Set.of(ReportJourney.Problem.class.getName()),
                Set.of(ReportJourney.Result.class.getName()),
                new AlgorithmCapability.ComplexityProfile(
                        declaredCost(), "O(claims of the support set)"),
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

    /**
     * The cost this method declares.
     *
     * @return the measured cost in nanoseconds when the promotion measured one, and the
     *         declared constant otherwise
     */
    public long declaredCostMillis() {
        if (measuredCostNanos < 0) {
            return MEASURED_COST_MILLIS;
        }
        // the measurement is in NANOSECONDS and the declared cost is in MILLISECONDS; the
        // conversion rounds UP, so a reading that took a fraction of a millisecond is
        // declared as a whole millisecond rather than as zero
        return Math.max(1L, (measuredCostNanos + 999_999L) / 1_000_000L);
    }

    /**
     * What the declared cost is, in words a reader can act on.
     *
     * <p>A method that has never run declares the portfolio's constant — a promise. A method
     * whose promotion measured the work it stands for declares that MEASUREMENT, and the
     * selector's record says so, so a reader can tell the two apart.</p>
     */
    private String declaredCost() {
        if (measuredCostNanos < 0) {
            return DeclaredCost.declare("O(claims of the support set), against the reference's "
                    + "O(claims of the workspace)", MEASURED_COST_MILLIS, 0);
        }
        return DeclaredCost.declareMeasured("the reading of the support set, measured over "
                + procedure.validationCases() + " held-out case(s), against the reference's "
                + "O(claims of the workspace)", measuredCostNanos);
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
        return refusalOf(input) == null;
    }

    /**
     * Why this procedure does not hold for a request — or null when it does.
     *
     * <p><b>Why the reason is kept and not a boolean.</b> A refusal that says only "the
     * support set is no longer present" is a REFUSAL THAT MAY BE WRONG: the support set can
     * be present and still be unable to answer the question, and the two failures call for
     * different actions by the caller — one is a drift of the workspace, the other is a
     * question outside the competence's reach. The milestone's own rule is that the
     * fallback's reason is recorded with the evidence it was made from, so the reason is
     * carried, not recomputed.</p>
     *
     * @param input the request
     * @return the reason, or null when the procedure may serve the request
     */
    private String refusalOf(ReportJourney.Problem input) {
        lastRefusal = null;
        // THE REQUEST OF THE LAST REFUSAL — and of no other. A successful run clears it, so
        // a reader who asks the method for the request it produced is never handed the
        // request of an earlier refusal. The independent reviewer found that it could be.
        acquisition = null;
        if (input == null) {
            return "the request is empty";
        }
        if (!procedure.active()) {
            return "the procedure " + procedure.id() + " has been invalidated";
        }
        if (support.isEmpty()) {
            return "the procedure " + procedure.id() + " carries no support set";
        }
        // the domain must still hold: the support set's sources must be in the workspace
        // as it is now, and the question must be one the workspace can be read for
        Set<String> present = input.selectedEvidence().sources().stream()
                .map(Source::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!present.containsAll(support)) {
            return remember("its support set is no longer present in the workspace: it holds "
                    + support.size() + " source(s) and the workspace offers " + present.size());
        }
        // THE QUESTION-SPECIFIC PART. The presence of the support set is a statement about the
        // WORKSPACE; whether the support set can answer THIS question is a statement about the
        // EVIDENCE, and the H2 milestone measured what the difference costs: with the
        // presence check alone, a procedure was offered for a question its support could not
        // answer, and the served answer was the restricted reading — on the H2 controls it
        // cited one claim where the reference cited three and still said ANSWERED. The
        // adequacy check compares the two readings' own evidence, so a support set that
        // cannot carry the workspace's best evidence for this question is not applicable,
        // and the reference method answers instead.
        SupportAdequacy assessment = adequacyOf(input);
        if (!assessment.applicable()) {
            rememberAcquisition(input, assessment);
            return remember(assessment.reason());
        }
        return null;
    }

    /**
     * Builds the acquisition request a refusal for a missing prerequisite carries.
     *
     * <p><b>Why a refusal carries a request.</b> The refusal says that the scope does not
     * carry the evidence the task needs. A reader who only has the refusal has nothing to do
     * about it, so the method also names <b>what</b> is missing and <b>through which
     * authorised channel</b> it may be obtained: the evidence is in the workspace, and a wider
     * scope has to be <b>learned and validated</b> before it may be served — a scope that was
     * simply widened would be a competence nobody checked.</p>
     *
     * <p>A refusal for any other reason — the support set is gone, the procedure was
     * invalidated — carries <b>no</b> request: there is nothing to acquire, and a request that
     * named nothing would be a form that asks for nothing.</p>
     *
     * @param input      the request
     * @param assessment the scope assessment the refusal was made from
     */
    private void rememberAcquisition(ReportJourney.Problem input, SupportAdequacy assessment) {
        acquisition = null;
        ScopeAssessment scope = lastScopeAssessment();
        if (scope == null || scope.taskPrerequisites() == null
                || scope.taskPrerequisites().necessary().isEmpty()) {
            return;
        }
        PrerequisiteAssessment prerequisites = PrerequisiteAssessment.of(
                scope.taskPrerequisites(), support);
        if (prerequisites.satisfied() || prerequisites.missing().isEmpty()) {
            return;
        }
        acquisition = EvidenceAcquisition.forMissingEvidence(input.workspaceId(), procedure.id(),
                input.question(), input.level().name(), prerequisites.missing());
    }

    /**
     * Remembers a refusal's reason and returns it.
     *
     * <p>The reason must survive the call that produced it: the selector asks whether the
     * procedure holds, and the note in the journey's plan is written later, from the
     * method's own last word. A presence failure has no adequacy verdict to read, so its
     * reason would be lost — and a fallback with no reason is a record with a hole in
     * it.</p>
     *
     * @param reason the reason
     * @return the reason
     */
    private String remember(String reason) {
        lastRefusal = reason;
        return reason;
    }

    /**
     * The reason of the last refusal this method made.
     *
     * @return the reason, or null when the method was never refused
     */
    public String lastRefusalReason() {
        return lastRefusal;
    }

    /**
     * The acquisition request the last refusal carried.
     *
     * <p>It is <b>null</b> when the refusal was not for a missing prerequisite — a support set
     * that is gone, or a procedure that was invalidated, is not something one acquires — and
     * it is the request that names what is missing and the authorised channel it may arrive
     * through.</p>
     *
     * @return the request, or null when the last refusal carried none
     */
    public io.matrix.knowledge.EvidenceAcquisitionRequest lastAcquisitionRequest() {
        return acquisition;
    }

    /**
     * The adequacy of the support set for this question.
     *
     * <p><b>Why the verdict is remembered.</b> The selector asks a candidate whether its
     * preconditions hold, and the method asks again before it runs — so the check would be
     * made twice for one request, and the check reads the workspace. The verdict is therefore
     * held against the request it was made for, and the key carries the question, the level,
     * the view's fingerprint and the support set, so a request that differs in any of them
     * is answered afresh.</p>
     *
     * @param input the request
     * @return the adequacy of the support set for the request's question
     */
    private SupportAdequacy adequacyOf(ReportJourney.Problem input) {
        return scopeOf(input).toAdequacy();
    }

    /**
     * The scope assessment of the support set for this question.
     *
     * <p><b>Why the rule is read from the record.</b> The rule that decides whether the support
     * set can carry the evidence is part of the domain the procedure may be used in, so it is
     * carried by the procedure's own durable record. A procedure that names no rule is judged by
     * the incumbent, and the served path's behaviour is unchanged for it.</p>
     *
     * @param input the request
     * @return the assessment of the support set for the request's question
     */
    private ScopeAssessment scopeOf(ReportJourney.Problem input) {
        String key = input.question() + "|" + input.level() + "|" + input.workspaceFingerprint()
                + "|" + String.join(",", support);
        if (assessment != null && adequacyKey != null && adequacyKey.equals(key)) {
            return assessment;
        }
        assessment = input.workbench().assessScope(input.selectedEvidence(),
                input.selectedEvidence(), input.question(), input.level(), support,
                input.historical(), ReportProcedure.criterionOf(procedure));
        adequacyKey = key;
        return assessment;
    }

    /**
     * The adequacy verdict of the last question this method was asked about.
     *
     * <p>It is the REASON a procedure was not selected, and it is recorded so that a reader
     * of the journey can see why the fallback happened and what the evidence was — the
     * decision record says that a candidate was blocked, and this says what the block
     * was.</p>
     *
     * @return the verdict, or null when the method was never asked
     */
    public SupportAdequacy lastAssessment() {
        return assessment == null ? null : assessment.toAdequacy();
    }

    /**
     * The scope assessment of the last question this method was asked about.
     *
     * <p>It carries what the adequacy verdict does not: the claims the workspace's own reading
     * would have cited, and the ones the support set does not carry. A reader who wants to know
     * <i>which</i> evidence was missing gets it here rather than by parsing the reason.</p>
     *
     * @return the assessment, or null when the method was never asked
     */
    public ScopeAssessment lastScopeAssessment() {
        return assessment;
    }

    @Override
    public ReportJourney.Result execute(ReportJourney.Problem input, ComputeBudget budget) {
        String refusal = refusalOf(input);
        if (refusal != null) {
            throw new AlgorithmRefusalException("the procedure " + procedure.id()
                    + " does not hold for this request: " + refusal + ", "
                    + "so the reference method must answer");
        }
        AbstractionLevel level = input.level();
        var snapshot = input.selectedEvidence();
        // the relevance statistics come from the SAME view the reference path reads: the
        // support set restricts WHAT MAY BE CITED, and it must not change HOW RELEVANT a
        // claim is — otherwise the replay is a different retrieval and not the restriction
        // the promotion validated.
        Answer answer = input.workbench().askInView(snapshot, snapshot, input.question(), level,
                support, input.historical());
        // the learned claims of the workspace join the evidence, exactly as they do on the
        // reference path: a validated claim is evidence of the question's concept, and the
        // path that answered does not change that
        answer = answer.withAdditionalClaims(LearnedClaims.additions(
                snapshot, answer, input.learnedClaims()));
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
