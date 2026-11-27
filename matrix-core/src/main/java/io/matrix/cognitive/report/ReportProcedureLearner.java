package io.matrix.cognitive.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.algorithms.ProcedureValidator;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.ScopeCriterion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRJ-01 / OD-082 — the producer of a domain-A procedure: an observed episode becomes a
 * candidate, the candidate is checked on held-out cases, and only a candidate that survives
 * is promoted with a version and a provenance.
 *
 * <p><b>The sequence, and why each step refuses.</b> This class is the W44 directive's sequence
 * §22 made executable for the knowledge-report family: propose from what was OBSERVED,
 * validate on cases that were NOT observed, promote only what preserves the required
 * quality, and record where the standing rests. Four gates stand between an observation and a
 * promoted procedure, and every one of them can refuse:</p>
 *
 * <ol>
 *   <li><b>The admission gate.</b> A record is admitted when it is learning-eligible AND it is
 *       not named in the quarantine list. The quarantine list is supplied by the caller and it
 *       is <b>fail-closed</b>: when it is absent, NOTHING is admitted. The chain-B isolation
 *       mechanism falls back to "nothing is quarantined" when its ledger is missing, and that
 *       fallback re-admits the evaluation traffic; this gate does not repeat that mistake.</li>
 *   <li><b>The held-out-case rule.</b> A validation case must not be one of the observed
 *       episodes — neither by identity nor by question. A case the procedure was proposed
 *       from cannot be evidence that the procedure generalises, and a rule that cannot say
 *       which cases it saw cannot be checked.</li>
 *   <li><b>The case count.</b> The minimum is the portfolio's own named constant
 *       ({@link ProcedureValidator#MINIMUM_VALIDATION_CASES}), so the threshold is visible in
 *       the code and can be argued about.</li>
 *   <li><b>The agreement.</b> The candidate must agree with the reference on EVERY held-out
 *       case: the same verdict and the same cited claims. One disagreement refuses the
 *       promotion and names the case, because a procedure is replayed without being checked
 *       and a disagreement the caller cannot see is the failure the whole mechanism exists to
 *       prevent.</li>
 * </ol>
 *
 * <p><b>What the support set is.</b> The union of the sources the observed journeys drew on.
 * It is a candidate, not a fact: whether the restricted path reproduces the reference on
 * questions nobody observed is exactly what the validation decides, and the promotion is
 * refused when it does not.</p>
 *
 * <p><b>No clock.</b> The promotion instant is supplied by the caller. A package whose
 * decisions must not depend on the wall clock does not read one.</p>
 */
public final class ReportProcedureLearner {

    /** The minimum number of held-out cases a promotion may be decided on. */
    public static final int MINIMUM_CASES = ProcedureValidator.MINIMUM_VALIDATION_CASES;

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * One observed episode: what an independently observed journey's effect carried.
     *
     * @param episodeId the episode the observation came from
     * @param question  the question that journey answered
     * @param level     the level it answered at
     * @param claimIds  the claims its answer cited
     * @param sourceIds the sources those claims came from
     */
    public record Observation(String episodeId, String question, AbstractionLevel level,
            List<String> claimIds, List<String> sourceIds) {
    }

    /**
     * One held-out case: a question the procedure must answer like the reference.
     *
     * @param caseId   the case's identity
     * @param question the question
     * @param level    the level the answer is asked for
     */
    public record Case(String caseId, String question, AbstractionLevel level) {
    }

    /**
     * The outcome of a learning attempt.
     *
     * @param promoted  whether the procedure entered the store
     * @param reason    why, in words a reader can act on
     * @param procedure the stored procedure, or null when the attempt was refused
     */
    public record Outcome(boolean promoted, String reason, Procedure procedure) {

        public Outcome {
            reason = reason == null ? "" : reason;
        }
    }

    private final KnowledgeWorkbench workbench;
    private final CognitiveStore store;

    /**
     * @param workbench the workspace the learning reads
     * @param store     the store the promoted procedure is written into
     */
    public ReportProcedureLearner(KnowledgeWorkbench workbench, CognitiveStore store) {
        if (workbench == null) {
            throw new IllegalArgumentException("workbench must not be null");
        }
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        this.workbench = workbench;
        this.store = store;
    }

    /**
     * The outcome of validating a candidate scope.
     *
     * @param accepted         whether the candidate scope survived the validation
     * @param reason           why, in words a reader can act on
     * @param agreements       the cases the restricted reading reproduced the reference on
     * @param cases            the cases the validation was decided over
     * @param agreement        the measured agreement with the reference
     * @param measuredCostNanos the median cost of the restricted reading, in nanoseconds
     */
    public record Validation(boolean accepted, String reason, int agreements, int cases,
            double agreement, Long measuredCostNanos) {

        public Validation {
            reason = reason == null ? "" : reason;
        }
    }

    /**
     * The outcome of obtaining a candidate scope.
     *
     * @param accepted whether the scope could be obtained
     * @param reason   why, in words a reader can act on
     * @param support  the candidate scope, as source identifiers
     * @param strategy the strategy that produced it
     */
    public record Scope(boolean accepted, String reason, Set<String> support,
            ScopeStrategy strategy) {

        public Scope {
            support = Set.copyOf(support == null ? Set.of() : support);
            reason = reason == null ? "" : reason;
        }
    }

    /**
     * Learns a procedure from the observed episodes, or refuses to.
     *
     * @param workspaceId    the workspace the procedure belongs to
     * @param observations   the observed, learning-eligible episodes
     * @param cases          the held-out cases the candidate must survive
     * @param quarantinedIds the ids of the records that are evaluation traffic; null refuses
     * @param promotedAt     the instant the procedure entered the active set
     * @return the outcome
     */
    public Outcome learn(String workspaceId, List<Observation> observations, List<Case> cases,
            Set<String> quarantinedIds, String promotedAt) {
        return learn(workspaceId, observations, cases, quarantinedIds, promotedAt,
                ScopeStrategy.CITATION_DERIVED, null, null);
    }

    /**
     * Learns a procedure from the observed episodes under a named scope strategy, or refuses to.
     *
     * <p><b>What the strategy changes and what it does not.</b> The strategy decides where the
     * candidate support set's boundary comes from; it does not decide whether the candidate is
     * promoted. Every strategy is validated by the <b>same</b> held-out validation, and a
     * candidate scope that does not reproduce the reference on every held-out case is refused
     * whatever produced it.</p>
     *
     * <p><b>Why the strategy is a parameter and not a commitment.</b> The ADAPTIVE-COMPETENCE
     * milestone's open question is a governance question about what a competence IS — whether
     * its support set should be chosen by the learning episode's citations at all, or declared.
     * A milestone that changed the default would have answered that question by fiat. This one
     * measures the strategies against one another and leaves the default where it was.</p>
     *
     * @param workspaceId    the workspace the procedure belongs to
     * @param observations   the observed, learning-eligible episodes
     * @param cases          the held-out cases the candidate must survive
     * @param quarantinedIds the ids of the records that are evaluation traffic; null refuses
     * @param promotedAt     the instant the procedure entered the active set
     * @param strategy       how the candidate scope is obtained
     * @param declaredScope  the documents the competence declares it is about; required by the
     *                       declared strategy, ignored by the others
     * @param criterion      the rule the support set is judged by at request time; null means
     *                       the incumbent
     * @return the outcome
     */
    public Outcome learn(String workspaceId, List<Observation> observations, List<Case> cases,
            Set<String> quarantinedIds, String promotedAt, ScopeStrategy strategy,
            Set<String> declaredScope, ScopeCriterion criterion) {
        if (quarantinedIds == null) {
            return new Outcome(false,
                    "the quarantine list is absent, so no record can be admitted to the durable "
                            + "learning; the gate fails closed rather than admitting the "
                            + "evaluation traffic", null);
        }
        List<Observation> observed = observations == null ? List.of() : observations;
        List<Case> heldOut = cases == null ? List.of() : cases;
        if (observed.isEmpty()) {
            return new Outcome(false, "no episode was observed, so there is nothing to learn from",
                    null);
        }
        for (Observation observation : observed) {
            if (observation.episodeId() != null && quarantinedIds.contains(observation.episodeId())) {
                return new Outcome(false, "the episode " + observation.episodeId()
                        + " is quarantined, so it is not learning-eligible and was not admitted",
                        null);
            }
        }
        String heldOutRefusal = heldOutRefusal(observed, heldOut, quarantinedIds);
        if (heldOutRefusal != null) {
            return new Outcome(false, heldOutRefusal, null);
        }

        var selectedView = io.matrix.knowledge.SourceRevisionView.project(workbench.workspace(workspaceId), null);
        Scope scope = scopeOf(workspaceId, observed, strategy, declaredScope, selectedView);
        if (!scope.accepted()) {
            return new Outcome(false, scope.reason(), null);
        }
        Set<String> support = scope.support();

        Validation validation = validate(workspaceId, observed, support, heldOut, quarantinedIds);
        if (!validation.accepted()) {
            return new Outcome(false, validation.reason(), null);
        }

        Procedure procedure = ReportProcedure.record(support,
                "the sources the observed journeys of this workspace drew on",
                validation.cases(), validation.agreement(), episodeIds(observed),
                "validated on " + validation.cases() + " held-out case(s) against the reference, "
                        + "over the support set of " + support.size() + " source(s)", promotedAt,
                criterion);
        store.recordProcedure(workspaceId, procedure, validation.measuredCostNanos());
        return new Outcome(true, "the candidate agreed with the reference on all "
                + validation.cases() + " held-out case(s) and was promoted", procedure);
    }

    /**
     * Validates a candidate scope on held-out cases, or refuses to.
     *
     * <p><b>Why this is a separate door.</b> The candidate scope and the rule that judges it are
     * two independent questions, and the milestone compares the two questions against each
     * other. A scope that has not been validated cannot be compared with a scope that has, so
     * the validation has to be runnable on a scope the caller obtained by its own means — and it
     * has to be the SAME validation, or the comparison would be between two different rules.</p>
     *
     * <p><b>What it enforces.</b> The held-out rule (a validation case may be neither an
     * observed episode nor the question the scope was proposed from), the minimum case count, the
     * applicability of the scope's sources in the workspace as it is now, the agreement with the
     * reference on EVERY held-out case, and the immutability of the source view across the
     * validation.</p>
     *
     * @param workspaceId    the workspace the candidate belongs to
     * @param observations   the observed, learning-eligible episodes the scope came from
     * @param support        the candidate scope, as source identifiers
     * @param heldOut        the held-out cases the candidate must survive
     * @param quarantinedIds the ids of the records that are evaluation traffic; null refuses
     * @return the validation outcome
     */
    public Validation validate(String workspaceId, List<Observation> observations,
            Set<String> support, List<Case> heldOut, Set<String> quarantinedIds) {
        List<Observation> observed = observations == null ? List.of() : observations;
        List<Case> cases = heldOut == null ? List.of() : heldOut;
        if (quarantinedIds == null) {
            return new Validation(false,
                    "the quarantine list is absent, so no record can be admitted to the durable "
                            + "learning; the gate fails closed rather than admitting the "
                            + "evaluation traffic", 0, 0, 0.0, null);
        }
        String refusal = heldOutRefusal(observed, cases, quarantinedIds);
        if (refusal != null) {
            return new Validation(false, refusal, 0, cases.size(), 0.0, null);
        }
        if (support == null || support.isEmpty()) {
            return new Validation(false, "the candidate scope holds no source, so there is no "
                    + "support set to validate", 0, cases.size(), 0.0, null);
        }

        var selectedView = io.matrix.knowledge.SourceRevisionView.project(workbench.workspace(workspaceId), null);
        Set<String> applicableSources = selectedView.sources().stream().map(io.matrix.knowledge.Source::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!applicableSources.containsAll(support)) {
            return new Validation(false, "an observed support source is no longer applicable",
                    0, cases.size(), 0.0, null);
        }

        int agreements = 0;
        // the restricted reading is TIMED as it runs: the promotion is the one moment that
        // knows what the narrower reading costs before the method has ever run, and a
        // procedure's FIRST selection is made on its declared cost - a promise. Carrying the
        // validation's own measurement makes that first decision rest on a measurement.
        List<Long> restrictedNanos = new ArrayList<>();
        for (Case heldOutCase : cases) {
            Answer reference = workbench.askInView(selectedView, heldOutCase.question(), heldOutCase.level(), Set.of());
            // the WARMUP pass: the first reading of a path pays the JIT's warmup, and a
            // cost measured on a cold path is not the cost the method will pay when the
            // serving process runs it. The warmup is not timed and not judged.
            //
            // The restricted reading takes its relevance statistics from the SAME view the
            // reference was read from: the support set decides WHAT MAY BE CITED, and the
            // workspace's own statistics decide HOW RELEVANT a claim is. Without that, the
            // restriction would change the scoring as well as the candidates, and the two
            // readings would not be comparable.
            workbench.askInView(selectedView, selectedView, heldOutCase.question(), heldOutCase.level(), support, false);
            long start = System.nanoTime();
            Answer restricted = workbench.askInView(selectedView, selectedView, heldOutCase.question(), heldOutCase.level(), support, false);
            restrictedNanos.add(System.nanoTime() - start);
            if (!agrees(reference, restricted)) {
                return new Validation(false, "the candidate disagreed with the reference on the "
                        + "held-out case '" + heldOutCase.caseId() + "', so it was not promoted",
                        agreements, cases.size(), 0.0, null);
            }
            agreements++;
        }
        double agreement = (double) agreements / (double) cases.size();
        Long measuredCostNanos = medianNanos(restrictedNanos);

        if (!WorkspaceFingerprint.of(selectedView).equals(WorkspaceFingerprint.of(
                io.matrix.knowledge.SourceRevisionView.project(workbench.workspace(workspaceId), null)))) {
            return new Validation(false, "the applicable source view changed during validation",
                    agreements, cases.size(), agreement, null);
        }
        return new Validation(true, "the candidate agreed with the reference on all "
                + cases.size() + " held-out case(s)", agreements, cases.size(), agreement,
                measuredCostNanos);
    }

    /**
     * The refusal the held-out rule makes, or null when it makes none.
     *
     * @param observed       the observed episodes
     * @param cases          the held-out cases
     * @param quarantinedIds the ids of the records that are evaluation traffic
     * @return the reason, or null
     */
    private String heldOutRefusal(List<Observation> observed, List<Case> cases,
            Set<String> quarantinedIds) {
        for (Case heldOutCase : cases) {
            if (heldOutCase.caseId() != null && quarantinedIds.contains(heldOutCase.caseId())) {
                return "the validation case " + heldOutCase.caseId()
                        + " is quarantined, so it is evaluation traffic and cannot serve as an "
                        + "independent case";
            }
            for (Observation observation : observed) {
                if (observation.episodeId() != null
                        && observation.episodeId().equals(heldOutCase.caseId())) {
                    return "the validation case " + heldOutCase.caseId()
                            + " is one of the observed episodes, so it is not held out";
                }
                if (observation.question() != null
                        && observation.question().equals(heldOutCase.question())) {
                    return "the validation case " + heldOutCase.caseId()
                            + " asks the question the procedure was proposed from, so it is not "
                            + "held out";
                }
            }
        }
        if (cases.size() < MINIMUM_CASES) {
            return "the candidate was validated on " + cases.size()
                    + " held-out case(s) and at least " + MINIMUM_CASES
                    + " are required before a promotion can be decided";
        }
        return null;
    }

    /**
     * The candidate scope the named strategy obtains, or the refusal it makes.
     *
     * <p><b>Why this is public.</b> The candidate scope and the rule that judges it are two
     * independent questions, and the milestone compares them against each other. A strategy
     * that could only be exercised by promoting a competence could not be compared with another
     * strategy on the same cases — and a comparison that used a second implementation of the
     * strategy would be a comparison of two implementations rather than of two strategies. The
     * derivation is therefore one method, and the comparison calls it.</p>
     *
     * <p><b>Why the declared scope BOUNDS the learning instead of refusing it.</b> A competence
     * that declares the documents it is about may only learn within them: a citation outside
     * the declaration is not learned, and it can therefore never widen the scope. The first
     * version of this rule REFUSED such an episode outright, and the pre-registration's §10
     * records the change and its reason: a refusing rule makes the declared arm EMPTY in any
     * domain whose learning episode cited an incidental source — which is exactly the situation
     * the milestone exists to measure — and an arm with no scope cannot be compared with
     * anything. The guard against a declaration that is too narrow is not this rule but the
     * VALIDATION, which asks whether the restricted reading still reproduces the reference on
     * every held-out case, and which refuses a scope that cannot.</p>
     *
     * @param workspaceId   the workspace the competence belongs to
     * @param observed      the observed episodes
     * @param strategy      the strategy that obtains the scope
     * @param declaredScope the documents the competence declares it is about
     * @return the scope
     */
    public Scope scope(String workspaceId, List<Observation> observed, ScopeStrategy strategy,
            Set<String> declaredScope) {
        var selectedView = io.matrix.knowledge.SourceRevisionView.project(
                workbench.workspace(workspaceId), null);
        return scopeOf(workspaceId, observed, strategy, declaredScope, selectedView);
    }

    /**
     * The candidate scope the named strategy obtains, or the refusal it makes.
     *
     * @param workspaceId   the workspace the competence belongs to
     * @param observed      the observed episodes
     * @param strategy      the strategy that obtains the scope
     * @param declaredScope the documents the competence declares it is about
     * @param selectedView  the applicable source view of the workspace
     * @return the scope
     */
    private Scope scopeOf(String workspaceId, List<Observation> observed, ScopeStrategy strategy,
            Set<String> declaredScope, io.matrix.knowledge.KnowledgeSnapshot selectedView) {
        ScopeStrategy rule = strategy == null ? ScopeStrategy.CITATION_DERIVED : strategy;
        switch (rule) {
            case CITATION_DERIVED: {
                Set<String> support = new LinkedHashSet<>();
                for (Observation observation : observed) {
                    if (observation.sourceIds() != null) {
                        support.addAll(observation.sourceIds());
                    }
                }
                if (support.isEmpty()) {
                    return new Scope(false, "the observed episodes named no source, so there is no "
                            + "support set to learn", Set.of(), rule);
                }
                return new Scope(true, "the support set is the union of the sources the observed "
                        + "journeys drew on", support, rule);
            }
            case DECLARED: {
                if (declaredScope == null || declaredScope.isEmpty()) {
                    return new Scope(false, "the declared scope names no document, so there is no "
                            + "support set to learn", Set.of(), rule);
                }
                // THE DECLARATION BOUNDS THE LEARNING. A competence that declares the
                // documents it is about may only learn within them: a citation the episode
                // made outside the declaration is NOT learned, and it can therefore never
                // widen the scope. The guard against a declaration that is too narrow is not
                // a rule here — it is the VALIDATION, which asks whether the restricted
                // reading still reproduces the reference on the held-out cases.
                Set<String> outside = new LinkedHashSet<>();
                for (Observation observation : observed) {
                    if (observation.sourceIds() == null) {
                        continue;
                    }
                    for (String sourceId : observation.sourceIds()) {
                        if (!declaredScope.contains(sourceId)) {
                            outside.add(sourceId);
                        }
                    }
                }
                return new Scope(true, "the support set is the set of documents the competence "
                        + "declared it is about; the observed journeys cited "
                        + outside.size() + " source(s) outside it, which the declaration "
                        + "therefore does not admit", declaredScope, rule);
            }
            case EVIDENCE_DERIVED: {
                Set<String> support = new LinkedHashSet<>();
                for (Observation observation : observed) {
                    if (observation.question() == null) {
                        continue;
                    }
                    support.addAll(workbench.assessScope(selectedView, selectedView,
                            observation.question(),
                            observation.level() == null ? AbstractionLevel.INTRODUCTORY
                                    : observation.level(),
                            Set.of(), false, ScopeCriterion.ANCHOR).workspaceCitedIds()
                            .stream().map(claimId -> sourceOf(selectedView, claimId))
                            .filter(java.util.Objects::nonNull).toList());
                }
                if (support.isEmpty()) {
                    return new Scope(false, "the observed episodes' questions reach no claim, so "
                            + "there is no support set to learn", Set.of(), rule);
                }
                return new Scope(true, "the support set is the set of sources that carry the claims "
                        + "the workspace's own reading reaches for the observed questions",
                        support, rule);
            }
            default:
                return new Scope(false, "the scope strategy is not one this learner knows",
                        Set.of(), rule);
        }
    }

    /**
     * The source a claim was introduced by.
     *
     * @param view    the view the claim belongs to
     * @param claimId the claim's identifier
     * @return the source identifier, or null when the claim has none
     */
    private String sourceOf(io.matrix.knowledge.KnowledgeSnapshot view, String claimId) {
        for (io.matrix.knowledge.Claim claim : view.claims()) {
            if (claim.id().equals(claimId)) {
                return claim.introducedBySourceId();
            }
        }
        return null;
    }

    /**
     * The median of the timings, in nanoseconds.
     *
     * <p>The median and not the mean: the distribution of a reading on a shared machine has a
     * long tail, and the mean of such a tail is not the cost of the reading. The repository's
     * own latency assertion uses the median of its passes for exactly this reason.</p>
     *
     * @param nanos the timings
     * @return the median, or null when nothing was timed
     */
    private static Long medianNanos(List<Long> nanos) {
        if (nanos == null || nanos.isEmpty()) {
            return null;
        }
        List<Long> sorted = new ArrayList<>(nanos);
        java.util.Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    /**
     * The observations of the episodes of a workspace, read from the journeys' own traces.
     *
     * <p>What is read is the RETAINED payload of each episode: the question the journey
     * answered and the evidence its response carried. A journey that left no retained payload
     * is not an observation — nothing was retained, so nothing was learned from.</p>
     *
     * @param store      the store the journeys' traces are in
     * @param episodeIds the episodes to read
     * @return the observations
     */
    public static List<Observation> observationsFrom(CognitiveStore store, List<String> episodeIds) {
        List<Observation> observations = new ArrayList<>();
        if (episodeIds == null) {
            return observations;
        }
        for (String episodeId : episodeIds) {
            ReportJourney.Retained retained = ReportJourneyTrace.retained(store, episodeId);
            if (retained == null || retained.historical() || retained.responseJson() == null) {
                continue;
            }
            List<String> claimIds = new ArrayList<>();
            List<String> sourceIds = new ArrayList<>();
            try {
                Map<String, Object> response = JSON.readValue(retained.responseJson(),
                        new TypeReference<Map<String, Object>>() {
                        });
                Object evidence = response.get("evidence");
                if (evidence instanceof List<?> references) {
                    for (Object reference : references) {
                        if (!(reference instanceof Map<?, ?> fields)) {
                            continue;
                        }
                        Object claimId = fields.get("claimId");
                        Object sourceId = fields.get("sourceId");
                        if (claimId instanceof String claim && !claim.isBlank()) {
                            claimIds.add(claim);
                        }
                        if (sourceId instanceof String source && !source.isBlank()
                                && !sourceIds.contains(source)) {
                            sourceIds.add(source);
                        }
                    }
                }
            } catch (IOException unreadable) {
                continue;
            }
            observations.add(new Observation(episodeId, retained.question(),
                    retained.level() == null ? AbstractionLevel.INTRODUCTORY
                            : AbstractionLevel.valueOf(retained.level()),
                    claimIds, sourceIds));
        }
        return observations;
    }

    /**
     * Whether the restricted path answered like the reference.
     *
     * <p>The verdict and the cited claims must both agree. The verdict alone is too weak: two
     * answers may both be "answered" and cite different evidence. The claims alone are too
     * weak in the other direction: a path that cites the same claims but refuses to answer
     * them has not answered.</p>
     *
     * @param reference the reference path's answer
     * @param restricted the restricted path's answer
     * @return whether they agree
     */
    private static boolean agrees(Answer reference, Answer restricted) {
        if (reference == null || restricted == null) {
            return false;
        }
        if (reference.answered() != restricted.answered()) {
            return false;
        }
        return ReportProcedureMethod.citedClaims(reference)
                .equals(ReportProcedureMethod.citedClaims(restricted));
    }

    private static String episodeIds(List<Observation> observations) {
        List<String> ids = new ArrayList<>();
        for (Observation observation : observations) {
            if (observation.episodeId() != null && !ids.contains(observation.episodeId())) {
                ids.add(observation.episodeId());
            }
        }
        return String.join(",", ids);
    }
}
