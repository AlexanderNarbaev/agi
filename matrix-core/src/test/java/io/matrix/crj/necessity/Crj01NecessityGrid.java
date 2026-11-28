package io.matrix.crj.necessity;

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
 * CRJ-01 / EPISTEMIC-NECESSITY — the paired grid: the incumbent rule against the
 * necessary-prerequisite rule, measured on identical inputs.
 *
 * <p><b>What this measures.</b> For every case, every condition, every scope and every rule:
 * whether the rule <b>admitted</b> the scope, what the oracle says about that scope, and —
 * separately — what the caller would have <b>received</b>. The two are kept apart on purpose:
 * the rule decides whether a competence may serve a task, and the user-observable outcome is
 * a different fact about the same run.</p>
 *
 * <p><b>Why the oracle's label is a statement about the SCOPE.</b> The oracle knows what
 * evidence a correct answer must stand on; it does not know what any rule would do with it.
 * A scope <b>carries</b> a case when it holds the source of every necessary sentence, or the
 * source of every sentence of one alternative sufficient set — and a case the workspace cannot
 * answer at all is carried by <b>no</b> scope. That label is the truth the rules are scored
 * against; the rules never see it while they run.</p>
 *
 * <p><b>Why the readings are the served methods' own readings.</b> The reference reading is
 * {@code askInView(view, question, level, Set.of(), false)} and the restricted reading is
 * {@code askInView(view, view, question, level, scope, false)} — the two calls the reference
 * method and the procedure method make. The grid therefore measures what the route would serve,
 * without the route's artefact writes and trace lines, which are the same in every arm.</p>
 *
 * <p><b>Why the inputs are identical.</b> The snapshot is captured <b>once</b> and passed to
 * every arm; the weakened bindings are the workspace's, read at assessment time; the history is
 * the snapshot's own source-revision selections. Every row carries the fingerprint of the view
 * it was measured against, and the suite requires the arms of a cell to carry <b>one</b>.</p>
 */
public final class Crj01NecessityGrid {

    /** The scope strategies, in the fixed order the arms run in. */
    public static final List<ScopeStrategy> STRATEGIES = List.of(
            ScopeStrategy.CITATION_DERIVED, ScopeStrategy.DECLARED, ScopeStrategy.EVIDENCE_DERIVED);

    /** The rules, in the fixed order the arms run in: the incumbent and the proposal. */
    public static final List<ScopeCriterion> CRITERIA = List.of(
            ScopeCriterion.ANCHOR, ScopeCriterion.NECESSARY_PREREQUISITE);

    /**
     * The candidate scope of one strategy, and the outcome of its validation.
     *
     * @param domain           the domain
     * @param condition        the condition the scope was derived under
     * @param strategy         the strategy
     * @param support          the candidate scope, as source identifiers
     * @param validated        whether the scope survived the held-out validation
     * @param validationReason why it survived, or why it did not
     * @param agreement        the measured agreement with the reference
     * @param validationCases  the held-out cases the validation was decided over
     */
    public record ScopeRow(String domain, String condition, String strategy, List<String> support,
            boolean validated, String validationReason, double agreement, int validationCases) {

        public ScopeRow {
            support = List.copyOf(support == null ? List.of() : support);
        }
    }

    /**
     * One measured row: one case, one condition, one scope, one rule.
     *
     * @param domain          the domain
     * @param condition       the condition of the workspace
     * @param strategy        the scope strategy
     * @param criterion       the rule
     * @param caseId          the case
     * @param caseCondition   the condition the case exists for
     * @param inputFingerprint the fingerprint of the view the row was measured against
     * @param scope           the scope, as source identifiers
     * @param admitted        whether the rule admitted the scope
     * @param reason          the reason the rule admitted or refused it
     * @param missingClaimIds the requirements the scope does not carry
     * @param oracleAdequate  whether the oracle says the scope carries the case's evidence
     * @param classification  the confusion-matrix cell: TP, FP, TN or FN
     * @param carriedFiles    the necessary sentences the scope carries
     * @param missingFiles    the necessary sentences the scope does not carry
     * @param served          the path that served the case: the procedure or the reference
     * @param citedClaimIds   the claims the served answer cited
     * @param disposition     the served answer's disposition
     * @param dispositionAllowed whether the disposition is among the allowed ones
     * @param evidenceComplete whether the served answer cited every necessary claim
     * @param goldReachable   whether the workspace's own reading reaches EVERY necessary claim of
     *                         the case. It is a property of the CASE and the RETRIEVER, not of
     *                         the scope: a case whose gold no reading reaches measures the
     *                         retriever, and the row says so.
     * @param unreachablePropositions the necessary propositions the reading does not reach
     * @param selectionNanos  the cost of the admission decision
     * @param readNanos       the cost of the reading that served the case
     * @param fallbackNanos   the cost of the fallback, when there was one
     */
    public record Row(String domain, String condition, String strategy, String criterion,
            String caseId, String caseCondition, String inputFingerprint, List<String> scope,
            boolean admitted, String reason, List<String> missingClaimIds, boolean oracleAdequate,
            String classification, List<String> carriedFiles, List<String> missingFiles,
            String served, List<String> citedClaimIds, String disposition,
            boolean dispositionAllowed, boolean evidenceComplete, boolean goldReachable,
            List<String> unreachablePropositions, long selectionNanos,
            long readNanos, long fallbackNanos) {

        public Row {
            scope = List.copyOf(scope == null ? List.of() : scope);
            missingClaimIds = List.copyOf(missingClaimIds == null ? List.of() : missingClaimIds);
            carriedFiles = List.copyOf(carriedFiles == null ? List.of() : carriedFiles);
            missingFiles = List.copyOf(missingFiles == null ? List.of() : missingFiles);
            citedClaimIds = List.copyOf(citedClaimIds == null ? List.of() : citedClaimIds);
            unreachablePropositions = List.copyOf(
                    unreachablePropositions == null ? List.of() : unreachablePropositions);
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
     * @param bench               the workbench the workspace is read through
     * @param learner             the learner that derives and validates the candidate scopes
     * @param workspace           the workspace the grid runs in
     * @param domain              the domain
     * @param condition           the condition the grid runs under
     * @param view                the immutable snapshot every arm reads
     * @param declaredFiles       the files a competence about the domain declares it is about
     * @param sourceIdByFile      the production source id of every corpus file
     * @param observations        the observed episodes the scopes are derived from
     * @param validationCases     the held-out cases the candidate scopes are validated on
     * @param evaluationCases     the cases the grid measures
     * @param claimIdByProposition the production claim id of every proposition of the view
     */
    public record Input(KnowledgeWorkbench bench, ReportProcedureLearner learner, String workspace,
            String domain, String condition, KnowledgeSnapshot view, List<String> declaredFiles,
            Map<String, String> sourceIdByFile,
            List<ReportProcedureLearner.Observation> observations,
            List<ReportProcedureLearner.Case> validationCases,
            List<Crj01NecessityCases.Case> evaluationCases,
            Map<String, String> claimIdByProposition) {
    }

    private Crj01NecessityGrid() {
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

        Map<ScopeStrategy, List<String>> supportByStrategy = new LinkedHashMap<>();
        for (ScopeStrategy strategy : STRATEGIES) {
            Set<String> declared = new LinkedHashSet<>();
            for (String file : input.declaredFiles()) {
                String sourceId = input.sourceIdByFile().get(file);
                if (sourceId != null) {
                    declared.add(sourceId);
                }
            }
            ReportProcedureLearner.Scope scope = input.learner().scope(input.workspace(),
                    input.observations(), strategy, declared);
            supportByStrategy.put(strategy, List.copyOf(scope.support()));
            ReportProcedureLearner.Validation validation = input.learner().validate(
                    input.workspace(), input.observations(), scope.support(),
                    input.validationCases(), Set.of());
            scopes.add(new ScopeRow(input.domain(), input.condition(), strategy.name(),
                    List.copyOf(scope.support()), validation.accepted(), validation.reason(),
                    validation.agreement(), validation.cases()));
        }

        String inputFingerprint = fingerprint(input);

        for (Crj01NecessityCases.Case testCase : input.evaluationCases()) {
            AbstractionLevel level = AbstractionLevel.valueOf(testCase.level());
            long referenceStart = System.nanoTime();
            Answer reference = input.bench().askInView(input.view(), testCase.question(), level,
                    Set.of(), false);
            long referenceNanos = System.nanoTime() - referenceStart;
            Set<String> referenceCited = new LinkedHashSet<>();
            for (Claim claim : reference.claims()) {
                referenceCited.add(claim.id());
            }

            List<String> necessaryClaimIds = new ArrayList<>();
            for (Crj01NecessityCases.Evidence evidence : testCase.necessaryEvidence()) {
                String claimId = input.claimIdByProposition().get(evidence.proposition());
                if (claimId != null) {
                    necessaryClaimIds.add(claimId);
                }
            }

            for (ScopeStrategy strategy : STRATEGIES) {
                List<String> scope = supportByStrategy.get(strategy);
                Set<String> scopeSources = new LinkedHashSet<>(scope);
                long restrictedStart = System.nanoTime();
                Answer restricted = input.bench().askInView(input.bench().workspace(
                        input.workspace()), input.bench().workspace(input.workspace()),
                        testCase.question(), level, scopeSources, false);
                long restrictedNanos = System.nanoTime() - restrictedStart;

                // THE ORACLE'S "CARRIES" — a scope carries a necessary sentence when the VIEW
                // holds a claim that states it AND that claim's source is in the scope. The
                // source ids alone are not enough: under a source revision the workspace may
                // hold the source and not the claim, and a scope that cannot cite the claim
                // does not carry the sentence. The first version of this measured the scope's
                // SOURCE ids alone, and eight rows under SUPERSEDED then contradicted
                // themselves — the oracle said the scope carried the evidence and the served
                // answer did not cite it. The independent reviewer found it.
                boolean oracleAdequate = oracleAdequate(testCase, scopeSources,
                        input.sourceIdByFile(), input.view());
                List<String> carriedFiles = new ArrayList<>();
                List<String> missingFiles = new ArrayList<>();
                for (Crj01NecessityCases.Evidence evidence : testCase.necessaryEvidence()) {
                    if (carries(input.view(), evidence.proposition(), scopeSources)) {
                        carriedFiles.add(evidence.corpusFile());
                    } else {
                        missingFiles.add(evidence.corpusFile());
                    }
                }

                for (ScopeCriterion criterion : CRITERIA) {
                    // THE ARM'S OWN SNAPSHOT. Each arm takes a FRESH snapshot of the
                    // workspace, so the fingerprint of the arm is a measurement of what that
                    // arm actually read — and the check that the arms of a cell share one
                    // input becomes a check that the workspace did not change under them.
                    // The first version copied one fingerprint into every row of the grid, so
                    // the check could not fail. The independent reviewer found it.
                    KnowledgeSnapshot armView = input.bench().workspace(input.workspace());
                    long selectionStart = System.nanoTime();
                    ScopeAssessment assessment = input.bench().assessScope(armView,
                            armView, testCase.question(), level, scopeSources, false, criterion);
                    long selectionNanos = System.nanoTime() - selectionStart;

                    boolean admitted = assessment.applicable();
                    Answer served = admitted ? restricted : reference;
                    long readNanos = admitted ? restrictedNanos : 0L;
                    long fallbackNanos = admitted ? 0L : referenceNanos;
                    List<String> cited = new ArrayList<>();
                    for (Claim claim : served.claims()) {
                        cited.add(claim.id());
                    }
                    cited.sort(String::compareTo);
                    Set<String> citedSet = new LinkedHashSet<>(cited);
                    boolean complete = true;
                    for (String claimId : necessaryClaimIds) {
                        if (!citedSet.contains(claimId)) {
                            complete = false;
                            break;
                        }
                    }
                    // THE REACHABILITY OF THE CASE'S GOLD — a property of the case and the
                    // RETRIEVER, and it is measured on the REFERENCE reading: the workspace's
                    // own reading of the task over the whole view. It says whether the gold is
                    // reachable AT ALL; it says nothing about what a narrow scope served.
                    // The first version measured it on the SERVED answer, so a narrow scope
                    // made an unreachable gold look reachable — and the milestone's own
                    // selection of a failure case was then wrong.
                    List<String> unreachable = new ArrayList<>();
                    for (Crj01NecessityCases.Evidence evidence : testCase.necessaryEvidence()) {
                        String claimId = input.claimIdByProposition().get(evidence.proposition());
                        if (claimId == null || !referenceCited.contains(claimId)) {
                            unreachable.add(evidence.corpusFile() + ": "
                                    + evidence.proposition());
                        }
                    }
                    boolean goldReachable = unreachable.isEmpty();
                    String disposition = dispositionOf(served);
                    boolean dispositionAllowed = testCase.allowedDispositions().isEmpty()
                            || testCase.allowedDispositions().contains(disposition);
                    String classification = admitted
                            ? (oracleAdequate ? "TP" : "FP")
                            : (oracleAdequate ? "FN" : "TN");

                    // THE FINGERPRINT OF WHAT THIS ARM ACTUALLY READ. It is computed HERE,
                    // from the view the arm was handed, and not copied from a constant of the
                    // grid: a fingerprint copied into every row could never differ between two
                    // arms, so the check that the arms share one input was a tautology. The
                    // independent reviewer found it.
                    rows.add(new Row(input.domain(), input.condition(), strategy.name(),
                            criterion.name(), testCase.id(), testCase.condition(),
                            fingerprint(input, armView),
                            scope, admitted, assessment.reason(), assessment.missingClaimIds(),
                            oracleAdequate, classification, carriedFiles, missingFiles,
                            admitted ? "procedure" : "reference", cited, disposition,
                            dispositionAllowed, complete, goldReachable, unreachable,
                            selectionNanos, readNanos, fallbackNanos));
                }
            }
        }
        return new Result(scopes, rows);
    }

    /**
     * Whether the oracle says a scope carries a case's evidence.
     *
     * <p><b>The rule.</b> A case the workspace cannot answer at all is carried by <b>no</b>
     * scope — a missing premise and an unanswerable question are not carried by a document that
     * states half the answer. Otherwise a scope carries the case when it holds the source of
     * every necessary sentence, or the source of every sentence of one alternative sufficient
     * set.</p>
     *
     * @param testCase       the case
     * @param scopeSources   the sources the scope holds
     * @param sourceIdByFile the production source id of every corpus file
     * @return whether the scope carries the case's evidence
     */
    public static boolean oracleAdequate(Crj01NecessityCases.Case testCase,
            Set<String> scopeSources, Map<String, String> sourceIdByFile,
            KnowledgeSnapshot view) {
        if (!testCase.answerable()) {
            return false;
        }
        if (carries(testCase.necessaryEvidence(), scopeSources, sourceIdByFile, view)) {
            return true;
        }
        for (List<Crj01NecessityCases.Evidence> alternative : testCase.alternativeSufficientSets()) {
            if (!alternative.isEmpty()
                    && carries(alternative, scopeSources, sourceIdByFile, view)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a scope carries every sentence of a set.
     *
     * <p><b>What "carries" means, exactly.</b> The scope holds a source, and the VIEW holds a
     * claim that states the sentence and was introduced by one of the scope's sources. Both
     * halves are needed: a scope that holds the source but whose claim the view does not carry
     * — because a source revision took it back — cannot cite it, so it does not carry the
     * sentence.</p>
     *
     * @param evidence       the sentences
     * @param scopeSources   the sources the scope holds
     * @param sourceIdByFile the production source id of every corpus file
     * @param view           the view the question was asked over
     * @return whether the scope carries the set
     */
    private static boolean carries(List<Crj01NecessityCases.Evidence> evidence,
            Set<String> scopeSources, Map<String, String> sourceIdByFile,
            KnowledgeSnapshot view) {
        if (evidence == null || evidence.isEmpty()) {
            return false;
        }
        for (Crj01NecessityCases.Evidence entry : evidence) {
            String sourceId = sourceIdByFile.get(entry.corpusFile());
            if (sourceId == null || !scopeSources.contains(sourceId)) {
                return false;
            }
            if (!carries(view, entry.proposition(), scopeSources)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the view holds a claim stating a proposition whose source the scope holds.
     *
     * @param view         the view
     * @param proposition  the proposition
     * @param scopeSources the sources the scope holds
     * @return whether the scope carries the proposition
     */
    private static boolean carries(KnowledgeSnapshot view, String proposition,
            Set<String> scopeSources) {
        if (view == null) {
            return false;
        }
        for (Claim claim : view.claims()) {
            if (!proposition.equals(claim.proposition())) {
                continue;
            }
            String sourceId = claim.introducedBySourceId();
            if (sourceId != null && scopeSources.contains(sourceId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The disposition of an answer, in the vocabulary the oracle uses.
     *
     * @param answer the answer
     * @return the disposition
     */
    public static String dispositionOf(Answer answer) {
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
     * The fingerprint of the input a cell was measured against.
     *
     * @param input the grid's input
     * @return the fingerprint
     */
    private static String fingerprint(Input input) {
        return fingerprint(input, input.view());
    }

    /**
     * The fingerprint of the view an arm read.
     *
     * @param input the grid's input
     * @param view  the view the arm read
     * @return the fingerprint
     */
    private static String fingerprint(Input input, KnowledgeSnapshot view) {
        // THE MATERIAL OF THE FINGERPRINT — the workspace and WHAT it holds. The export
        // instant is deliberately NOT in it: two snapshots of an unchanged workspace are the
        // same input, and a fingerprint that carried the instant would report a difference
        // where there is none. What the fingerprint must catch is the workspace CHANGING.
        StringBuilder material = new StringBuilder();
        material.append(input.workspace()).append('|');
        for (Claim claim : view.claims()) {
            material.append(claim.id()).append(',');
        }
        material.append('|');
        for (io.matrix.knowledge.Source source : view.sources()) {
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
}
