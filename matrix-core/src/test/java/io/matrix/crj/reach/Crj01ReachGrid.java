package io.matrix.crj.reach;

import io.matrix.cognitive.report.ReportProcedureLearner;
import io.matrix.cognitive.report.ScopeStrategy;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.PremiseSemantics;
import io.matrix.knowledge.ScopeAssessment;
import io.matrix.knowledge.ScopeCriterion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRJ-01 / COMPETENCE-REACH — the six-arm grid: three scope strategies crossed with two
 * criteria, measured on identical inputs.
 *
 * <p><b>What this measures.</b> For every case, every condition and every arm: whether the arm
 * <b>admitted</b> the case, what it <b>served</b> when it did (the restricted reading) and what
 * it <b>fell back</b> to when it did not (the reference reading), whether the served answer
 carried the evidence the oracle says a correct answer must carry, and what the whole path
 * <b>cost</b>.</p>
 *
 * <p><b>Why the readings are the served methods' own readings.</b> The reference reading is
 * {@code askInView(view, question, level, Set.of(), false)} and the restricted reading is
 * {@code askInView(view, view, question, level, scope, false)} — the two calls the reference
 * method and the procedure method make. The grid therefore measures what the route would serve,
 * without the route's artefact writes and trace lines, which are the same in every arm and would
 * only add noise.</p>
 *
 * <p><b>Why the six arms see identical inputs.</b> The snapshot is captured <b>once</b> and
 * passed to every arm; the weakened bindings are the workspace's, read at assessment time; the
 * history is the snapshot's own source-revision selections. The readings are the only thing
 * that differs, and they differ by the arm's own scope and rule.</p>
 *
 * <p><b>What the grid never does.</b> It never consults the oracle while an arm is running: the
 * oracle is read after the arm has served, to score what it did. An arm that could see the gold
 * would be fitted to it.</p>
 */
public final class Crj01ReachGrid {

    /** The scope strategies, in the fixed order the arms run in. */
    public static final List<ScopeStrategy> STRATEGIES = List.of(
            ScopeStrategy.CITATION_DERIVED, ScopeStrategy.DECLARED, ScopeStrategy.EVIDENCE_DERIVED);

    /** The criteria, in the fixed order the arms run in. */
    public static final List<ScopeCriterion> CRITERIA = List.of(
            ScopeCriterion.ANCHOR, ScopeCriterion.CLAIM_SUFFICIENCY);

    /**
     * One arm: a scope strategy crossed with a criterion.
     *
     * @param strategy the scope strategy
     * @param criterion the criterion
     */
    public record Arm(ScopeStrategy strategy, ScopeCriterion criterion) {

        /** The arm's name, as the rows carry it. */
        public String id() {
            return strategy.name() + "/" + criterion.name();
        }
    }

    /**
     * The candidate scope of one strategy, and the outcome of its validation.
     *
     * @param domain           the domain
     * @param condition        the condition the scope was derived under
     * @param strategy         the strategy
     * @param support          the candidate scope, as source identifiers
     * @param validated        whether the scope survived the held-out validation
     * @param validationReason why it survived, or why it did not
     * @param agreements       the held-out cases the restricted reading reproduced
     * @param validationCases  the held-out cases the validation was decided over
     * @param agreement        the measured agreement with the reference
     * @param proposalNanos    the cost of obtaining the scope
     * @param validationNanos  the cost of the validation
     */
    public record ScopeRow(String domain, String condition, String strategy, List<String> support,
            boolean validated, String validationReason, int agreements, int validationCases,
            double agreement, Long proposalNanos, Long validationNanos) {

        public ScopeRow {
            support = List.copyOf(support == null ? List.of() : support);
        }
    }

    /**
     * One measured row: one case, one condition, one arm.
     *
     * @param domain             the domain
     * @param condition          the condition
     * @param arm                the arm's name
     * @param strategy           the arm's scope strategy
     * @param criterion          the arm's criterion
     * @param caseId             the case
     * @param kind               the case's kind
     * @param scope              the arm's scope
     * @param admitted           whether the arm admitted the case
     * @param admissionReason    the reason the arm admitted or refused it
     * @param missingClaimIds    the claims the workspace's reading would cite and the scope does
     *                            not carry
     * @param served             the path that served the case: the procedure or the reference
     * @param citedClaimIds      the claims the served answer cited
     * @param necessaryClaimIds  the claims the oracle says a correct answer must cite
     * @param necessarySourceIds the sources that carry those claims
     * @param evidenceComplete   whether the served answer cited every necessary claim
     * @param completeness       the share of the necessary claims the served answer cited
     * @param carries            whether the scope could have carried the necessary evidence
     * @param disposition        the served answer's disposition
     * @param dispositionAllowed whether the disposition is among the allowed ones
     * @param answerable         whether the oracle says the workspace holds the evidence
     * @param falseAdmission     whether this admission was a false one
     * @param falseRejection     whether this refusal was a false one
     * @param fallback           whether the arm fell back to the reference
     * @param fallbackReason     why it fell back
     * @param coverage           the served answer's mean evidence coverage
     * @param workspaceCoverage  the coverage the workspace's own reading would reach
     * @param scopeCoverage      the best coverage the scope carries for the question
     * @param selectionNanos     the cost of the admission decision
     * @param readNanos          the cost of the reading that served the case
     * @param fallbackNanos      the cost of the fallback, when there was one
     * @param totalNanos         the whole path's cost
     */
    public record Row(String domain, String condition, String arm, String strategy, String criterion,
            String caseId, String kind, String inputFingerprint, List<String> scope, boolean admitted,
            String admissionReason,
            List<String> missingClaimIds, String served, List<String> citedClaimIds,
            List<String> necessaryClaimIds, List<String> necessarySourceIds, boolean evidenceComplete,
            double completeness, boolean carries, String disposition, boolean dispositionAllowed,
            boolean answerable, boolean falseAdmission, boolean falseRejection, boolean fallback,
            String fallbackReason, double coverage, double workspaceCoverage, double scopeCoverage,
            long selectionNanos, long readNanos, long fallbackNanos, long totalNanos) {

        public Row {
            scope = List.copyOf(scope == null ? List.of() : scope);
            missingClaimIds = List.copyOf(missingClaimIds == null ? List.of() : missingClaimIds);
            citedClaimIds = List.copyOf(citedClaimIds == null ? List.of() : citedClaimIds);
            necessaryClaimIds = List.copyOf(
                    necessaryClaimIds == null ? List.of() : necessaryClaimIds);
            necessarySourceIds = List.copyOf(
                    necessarySourceIds == null ? List.of() : necessarySourceIds);
        }
    }

    /**
     * The fingerprint of the input a cell was measured against.
     *
     * <p>It is the digest of the workspace, of the snapshot's own identity and of the identity
     * of every claim and source the snapshot holds: two rows with the same fingerprint were
     * measured against the same view of the same workspace.</p>
     *
     * @param input the grid's input
     * @return the fingerprint
     */
    private static String fingerprint(Input input) {
        StringBuilder material = new StringBuilder();
        material.append(input.workspace()).append('|')
                .append(input.view().exportedAt()).append('|');
        for (io.matrix.knowledge.Claim claim : input.view().claims()) {
            material.append(claim.id()).append(',');
        }
        material.append('|');
        for (io.matrix.knowledge.Source source : input.view().sources()) {
            material.append(source.id()).append(',');
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(material.toString().getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.ofDelimiter(":").formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    /**
     * The whole grid.
     *
     * @param scopes the candidate scopes and their validations
     * @param rows   the measured rows
     */
    public record Result(List<ScopeRow> scopes, List<Row> rows) {

        public Result {
            scopes = List.copyOf(scopes == null ? List.of() : scopes);
            rows = List.copyOf(rows == null ? List.of() : rows);
        }
    }

    /**
     * The inputs of one grid.
     *
     * @param bench            the workbench the workspace is read through
     * @param learner          the learner that derives and validates the candidate scopes
     * @param workspace        the workspace the grid runs in
     * @param domain           the domain
     * @param condition        the condition the grid runs under
     * @param view             the immutable snapshot every arm reads
     * @param declaredFiles    the files a competence about the domain declares it is about
     * @param sourceIdByFile   the production source id of every corpus file
     * @param observations     the observed episodes the scopes are derived from
     * @param validationCases  the held-out cases the candidate scopes are validated on
     * @param evaluationCases  the cases the grid measures
     * @param claimIdByProposition the production claim id of every proposition of the view
     * @param sourceIdByClaim  the source that carries every claim of the view
     */
    public record Input(KnowledgeWorkbench bench, ReportProcedureLearner learner, String workspace,
            String domain, String condition, KnowledgeSnapshot view, List<String> declaredFiles,
            Map<String, String> sourceIdByFile,
            List<ReportProcedureLearner.Observation> observations,
            List<ReportProcedureLearner.Case> validationCases,
            List<Crj01ReachCases.Case> evaluationCases,
            Map<String, String> claimIdByProposition, Map<String, String> sourceIdByClaim) {
    }

    private Crj01ReachGrid() {
    }

    /**
     * Runs the grid.
     *
     * @param input the inputs
     * @return the scopes and the rows
     */
    public static Result run(Input input) {
        List<ScopeRow> scopes = new ArrayList<>();
        List<Row> rows = new ArrayList<>();

        // ── the candidate scopes, one per strategy, each subject to the SAME validation ──
        Map<ScopeStrategy, List<String>> supportByStrategy = new LinkedHashMap<>();
        for (ScopeStrategy strategy : STRATEGIES) {
            Set<String> declared = new LinkedHashSet<>();
            for (String file : input.declaredFiles()) {
                String sourceId = input.sourceIdByFile().get(file);
                if (sourceId != null) {
                    declared.add(sourceId);
                }
            }
            long proposalStart = System.nanoTime();
            ReportProcedureLearner.Scope scope = input.learner().scope(input.workspace(),
                    input.observations(), strategy, declared);
            long proposalNanos = System.nanoTime() - proposalStart;
            supportByStrategy.put(strategy, List.copyOf(scope.support()));

            long validationStart = System.nanoTime();
            ReportProcedureLearner.Validation validation = input.learner().validate(
                    input.workspace(), input.observations(), scope.support(),
                    input.validationCases(), Set.of());
            long validationNanos = System.nanoTime() - validationStart;
            scopes.add(new ScopeRow(input.domain(), input.condition(), strategy.name(),
                    List.copyOf(scope.support()), validation.accepted(), validation.reason(),
                    validation.agreements(), validation.cases(), validation.agreement(),
                    proposalNanos, validationNanos));
        }

        // THE FINGERPRINT OF THE CELL'S INPUT. The six arms of a cell must be measured
        // against the SAME snapshot, the same bindings and the same history, and the
        // fingerprint is what makes that checkable from the retained rows instead of from a
        // promise in a test.
        String inputFingerprint = fingerprint(input);

        // ── the arms, the cases and the conditions ───────────────────────────────
        for (Crj01ReachCases.Case testCase : input.evaluationCases()) {
            AbstractionLevel level = AbstractionLevel.valueOf(testCase.level());
            // THE REFERENCE READING — asked once, and shared by every arm that falls back
            long referenceStart = System.nanoTime();
            Answer reference = input.bench().askInView(input.view(), testCase.question(), level,
                    Set.of(), false);
            long referenceNanos = System.nanoTime() - referenceStart;

            List<String> necessaryClaimIds = new ArrayList<>();
            List<String> necessarySourceIds = new ArrayList<>();
            for (Crj01ReachCases.Evidence evidence : testCase.necessaryEvidence()) {
                String claimId = input.claimIdByProposition().get(evidence.proposition());
                if (claimId == null) {
                    continue;
                }
                necessaryClaimIds.add(claimId);
                String sourceId = input.sourceIdByClaim().get(claimId);
                if (sourceId != null && !necessarySourceIds.contains(sourceId)) {
                    necessarySourceIds.add(sourceId);
                }
            }

            for (ScopeStrategy strategy : STRATEGIES) {
                List<String> scope = supportByStrategy.get(strategy);
                // THE RESTRICTED READING — asked once per scope, and shared by the two
                // criteria of that scope, because the rule decides ADMISSION and not the
                // reading
                long restrictedStart = System.nanoTime();
                Answer restricted = input.bench().askInView(input.view(), input.view(),
                        testCase.question(), level, new LinkedHashSet<>(scope), false);
                long restrictedNanos = System.nanoTime() - restrictedStart;

                for (ScopeCriterion criterion : CRITERIA) {
                    long selectionStart = System.nanoTime();
                    ScopeAssessment assessment = input.bench().assessScope(input.view(),
                            input.view(), testCase.question(), level,
                            new LinkedHashSet<>(scope), false, criterion);
                    long selectionNanos = System.nanoTime() - selectionStart;

                    boolean admitted = assessment.applicable();
                    Answer served = admitted ? restricted : reference;
                    // THE COST OF THE PATH THAT WAS SERVED. An admitted row served the
                    // restricted reading, so its read is that reading's cost and its
                    // fallback is zero. A REFUSED row served NOTHING from the scope: its
                    // read is zero and the reference reading is counted ONCE, as the
                    // fallback. Counting the reference reading as both the read and the
                    // fallback would count the same measurement twice.
                    long readNanos = admitted ? restrictedNanos : 0L;
                    long fallbackNanos = admitted ? 0L : referenceNanos;
                    List<String> cited = new ArrayList<>();
                    for (Claim claim : served.claims()) {
                        cited.add(claim.id());
                    }
                    cited.sort(String::compareTo);

                    Set<String> citedSet = new LinkedHashSet<>(cited);
                    int complete = 0;
                    for (String claimId : necessaryClaimIds) {
                        if (citedSet.contains(claimId)) {
                            complete++;
                        }
                    }
                    double completeness = necessaryClaimIds.isEmpty() ? 1.0
                            : (double) complete / (double) necessaryClaimIds.size();
                    // A CASE WITH NO NECESSARY EVIDENCE has nothing to carry: its
                    // completeness is not a failure of the scope but a fact about the case,
                    // and the case's admissibility is decided by its disposition alone.
                    boolean evidenceComplete = necessaryClaimIds.isEmpty()
                            || complete == necessaryClaimIds.size();
                    boolean carries = new LinkedHashSet<>(scope)
                            .containsAll(new LinkedHashSet<>(necessarySourceIds));

                    String disposition = dispositionOf(served);
                    boolean dispositionAllowed = testCase.allowedDispositions().isEmpty()
                            || testCase.allowedDispositions().contains(disposition);

                    boolean acceptable = testCase.answerable()
                            ? evidenceComplete && dispositionAllowed
                            : dispositionAllowed && !disposition.equals("ANSWERED");
                    boolean falseAdmission = admitted && !acceptable;
                    boolean falseRejection = !admitted && testCase.answerable() && carries;

                    rows.add(new Row(input.domain(), input.condition(),
                            strategy.name() + "/" + criterion.name(), strategy.name(),
                            criterion.name(), testCase.id(), testCase.kind(), inputFingerprint,
                            scope, admitted,
                            assessment.reason(), assessment.missingClaimIds(),
                            admitted ? "procedure" : "reference", cited, necessaryClaimIds,
                            necessarySourceIds, evidenceComplete, completeness, carries, disposition,
                            dispositionAllowed, testCase.answerable(), falseAdmission,
                            falseRejection, !admitted, admitted ? "" : assessment.reason(),
                            coverageOf(served), assessment.bestCoverage(),
                            assessment.supportBestCoverage(), selectionNanos, readNanos,
                            fallbackNanos, selectionNanos + readNanos + fallbackNanos));
                }
            }
        }
        return new Result(scopes, rows);
    }

    /**
     * The disposition of an answer, in the vocabulary the oracle uses.
     *
     * <p><b>Why the premise decides it.</b> The product's own rule is that an ambiguous request
     * should ask rather than guess, and that a question whose premise the workspace contradicts
     * must not be answered as if the premise held. Both of those show up in the premise's
     * status, and both of them make the answer a <b>qualified</b> one rather than a plain
     * answer — which is why the oracle is told a disposition of {@code QUALIFIED} means the
     * answer stands, but stands on a premise that is not settled.</p>
     *
     * @param answer the answer
     * @return the disposition
     */
    private static String dispositionOf(Answer answer) {
        if (answer == null || !answer.answered()) {
            return "ABSTAINED";
        }
        PremiseSemantics.Disposition premise = answer.premise();
        if (premise == null) {
            return "ANSWERED";
        }
        return switch (premise.status()) {
            case SUPPORTED, NO_PREMISE -> "ANSWERED";
            case AMBIGUOUS, CONTRADICTORY, UNSUPPORTED, REFUTED -> "QUALIFIED";
        };
    }

    /**
     * The mean coverage of the claims an answer cites.
     *
     * @param answer the answer
     * @return the coverage
     */
    private static double coverageOf(Answer answer) {
        if (answer == null || answer.support() == null) {
            return 0.0;
        }
        return answer.support().evidenceCoverage();
    }
}
