package io.matrix.cognitive.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.algorithms.ProcedureValidator;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.KnowledgeWorkbench;

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
        for (Case heldOutCase : heldOut) {
            if (heldOutCase.caseId() != null && quarantinedIds.contains(heldOutCase.caseId())) {
                return new Outcome(false, "the validation case " + heldOutCase.caseId()
                        + " is quarantined, so it is evaluation traffic and cannot serve as an "
                        + "independent case", null);
            }
            for (Observation observation : observed) {
                if (observation.episodeId() != null
                        && observation.episodeId().equals(heldOutCase.caseId())) {
                    return new Outcome(false, "the validation case " + heldOutCase.caseId()
                            + " is one of the observed episodes, so it is not held out",
                            null);
                }
                if (observation.question() != null
                        && observation.question().equals(heldOutCase.question())) {
                    return new Outcome(false, "the validation case " + heldOutCase.caseId()
                            + " asks the question the procedure was proposed from, so it is not "
                            + "held out", null);
                }
            }
        }
        if (heldOut.size() < MINIMUM_CASES) {
            return new Outcome(false, "the candidate was validated on " + heldOut.size()
                    + " held-out case(s) and at least " + MINIMUM_CASES
                    + " are required before a promotion can be decided", null);
        }

        Set<String> support = new LinkedHashSet<>();
        for (Observation observation : observed) {
            if (observation.sourceIds() != null) {
                support.addAll(observation.sourceIds());
            }
        }
        if (support.isEmpty()) {
            return new Outcome(false,
                    "the observed episodes named no source, so there is no support set to learn",
                    null);
        }

        int agreements = 0;
        // the restricted reading is TIMED as it runs: the promotion is the one moment that
        // knows what the narrower reading costs before the method has ever run, and a
        // procedure's FIRST selection is made on its declared cost - a promise. Carrying the
        // validation's own measurement makes that first decision rest on a measurement.
        List<Long> restrictedNanos = new ArrayList<>();
        for (Case heldOutCase : heldOut) {
            Answer reference = workbench.ask(workspaceId, heldOutCase.question(), heldOutCase.level());
            // the WARMUP pass: the first reading of a path pays the JIT's warmup, and a
            // cost measured on a cold path is not the cost the method will pay when the
            // serving process runs it. The warmup is not timed and not judged.
            workbench.ask(workspaceId, heldOutCase.question(), heldOutCase.level(), support);
            long start = System.nanoTime();
            Answer restricted = workbench.ask(workspaceId, heldOutCase.question(),
                    heldOutCase.level(), support);
            restrictedNanos.add(System.nanoTime() - start);
            if (!agrees(reference, restricted)) {
                return new Outcome(false, "the candidate disagreed with the reference on the "
                        + "held-out case '" + heldOutCase.caseId() + "', so it was not promoted",
                        null);
            }
            agreements++;
        }
        double agreement = (double) agreements / (double) heldOut.size();
        Long measuredCostNanos = medianNanos(restrictedNanos);

        Procedure procedure = ReportProcedure.record(support,
                "the sources the observed journeys of this workspace drew on",
                heldOut.size(), agreement, episodeIds(observed),
                "validated on " + heldOut.size() + " held-out case(s) against the reference, "
                        + "over the support set of " + support.size() + " source(s)", promotedAt);
        store.recordProcedure(workspaceId, procedure, measuredCostNanos);
        return new Outcome(true, "the candidate agreed with the reference on all "
                + heldOut.size() + " held-out case(s) and was promoted", procedure);
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
            if (retained == null || retained.responseJson() == null) {
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
