package io.matrix.cognitive.report;

import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.algorithms.ProcedureValidator;
import io.matrix.cognitive.algorithms.ValidationKind;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRJ-01 / OD-082 — the producer of a LEARNED CLAIM.
 *
 * <p><b>What it does.</b> It takes the journeys the system observed, proposes the claims they
 * agreed on, checks the proposal on <b>held-out</b> questions it was never proposed from, and
 * <b>promotes</b> the proposal — with a version and a provenance — or refuses it, naming the
 * gate that refused it.</p>
 *
 * <p><b>The gates, and what each one catches.</b></p>
 * <ol>
 *   <li><b>the quarantine gate</b> — fail-closed. An absent quarantine list refuses
 *       everything, because the evaluation traffic must never become the durable learning;</li>
 *   <li><b>the held-out-case rule</b> — a validation case may not be one of the observed
 *       episodes, neither by identity nor by question, because a case the proposal came from
 *       proves nothing;</li>
 *   <li><b>the minimum case count</b> — at least
 *       {@value ProcedureValidator#MINIMUM_VALIDATION_CASES} held-out cases, the repository's
 *       own convention for a validated procedure;</li>
 *   <li><b>the agreement gate</b> — the reference's answer must carry the candidate claim on
 *       <b>every</b> held-out case, and the answer's verdict must be the same. One
 *       disagreement refuses the promotion and the reason names the case;</li>
 *   <li><b>the containment gate</b> — the candidate's proposition must occur <b>verbatim</b>
 *       in its source's text. This is the gate the CRJ-01 <b>source-span oracle</b> asks
 *       independently, and it is what makes a learned claim a statement the document
 *       <b>literally contains</b> rather than a paraphrase of it. A paraphrase is the
 *       workspace's business — the extraction machinery's — and a learned claim that
 *       asserted one would be asserting something nobody checked against the text.</li>
 * </ol>
 *
 * <p><b>What is proposed.</b> The claims <b>every</b> observed journey cited — the common
 * core of what the journeys relied on, not the union of what any of them happened to
 * mention. A claim one journey cited and another did not is not something the system
 * consistently relied on, and a learned claim is a claim about consistency.</p>
 *
 * <p><b>Why the containment gate is the interesting one.</b> It is the gate that makes the
 * learned claim <b>safe</b> and the gate the instrument <b>checks</b>: the system decides
 * with the workspace's copy of the text, and the oracle decides with the corpus file's
 * bytes. Two independent implementations of one question, and a disagreement between them
 * would be a finding about the corpus.</p>
 */
public final class ReportClaimLearner {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The minimum number of held-out cases a promotion may be decided on. */
    public static final int MINIMUM_CASES = ProcedureValidator.MINIMUM_VALIDATION_CASES;

    /**
     * One observed episode: what an independently observed journey's answer carried.
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
     * One held-out case: a question the claim must answer like the reference.
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
     * <p><b>Why it carries a LIST.</b> The observed journeys usually agreed on more than one
     * claim, and each of them is a separate candidate: each is checked against the source's
     * text and against the reference's own answers, and each that survives is promoted with
     * its own provenance. A learner that looked at only the first candidate would report a
     * refusal that says nothing about the others — and the others may be exactly the claims
     * the system can use.</p>
     *
     * @param promoted whether at least one claim entered the store
     * @param reason   why, in words a reader can act on
     * @param claims   the stored claims; empty when the attempt was refused
     */
    public record Outcome(boolean promoted, String reason, List<LearnedClaim> claims) {

        public Outcome {
            reason = reason == null ? "" : reason;
            claims = List.copyOf(claims == null ? List.of() : claims);
        }

        /** The first promoted claim, or null when nothing was promoted. */
        public LearnedClaim claim() {
            return claims.isEmpty() ? null : claims.get(0);
        }
    }

    private final KnowledgeWorkbench workbench;
    private final CognitiveStore store;

    /**
     * @param workbench the workspace the learning reads
     * @param store     the store the promoted claim is written into
     */
    public ReportClaimLearner(KnowledgeWorkbench workbench, CognitiveStore store) {
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
     * Learns a claim from the observed episodes, or refuses to.
     *
     * @param workspaceId    the workspace the claim belongs to
     * @param observations   the observed, learning-eligible episodes
     * @param cases          the held-out cases the candidate must survive
     * @param quarantinedIds the ids of the records that are evaluation traffic; null refuses
     * @param promotedAt     the instant the claim entered the active set
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
                    List.of());
        }
        for (Observation observation : observed) {
            if (observation.episodeId() != null && quarantinedIds.contains(observation.episodeId())) {
                return new Outcome(false, "the episode " + observation.episodeId()
                        + " is quarantined, so it is not learning-eligible and was not admitted",
                        List.of());
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
                            + " is one of the observed episodes, so it is not held out", List.of());
                }
                if (observation.question() != null
                        && observation.question().equals(heldOutCase.question())) {
                    return new Outcome(false, "the validation case " + heldOutCase.caseId()
                            + " asks the question the claim was proposed from, so it is not "
                            + "held out", null);
                }
            }
        }
        if (heldOut.size() < MINIMUM_CASES) {
            return new Outcome(false, "the candidate was validated on " + heldOut.size()
                    + " held-out case(s) and at least " + MINIMUM_CASES
                    + " are required before a promotion can be decided", List.of());
        }

        List<String> common = commonClaims(observed);
        if (common.isEmpty()) {
            return new Outcome(false,
                    "the observed episodes cited no claim in common, so there is nothing they "
                            + "consistently relied on to learn", List.of());
        }

        // the reference's own answers, read ONCE: every candidate is checked against the
        // same reading, and reading the workspace five times per candidate would measure the
        // reading rather than the candidate
        List<Answer> references = new ArrayList<>();
        for (Case heldOutCase : heldOut) {
            Answer reference = workbench.ask(workspaceId, heldOutCase.question(),
                    heldOutCase.level());
            if (!reference.answered()) {
                return new Outcome(false, "the reference did not answer the held-out case '"
                        + heldOutCase.caseId() + "', so a candidate could not be checked "
                        + "against it", List.of());
            }
            references.add(reference);
        }

        Set<String> support = new LinkedHashSet<>();
        for (Observation observation : observed) {
            if (observation.sourceIds() != null) {
                support.addAll(observation.sourceIds());
            }
        }

        List<LearnedClaim> promoted = new ArrayList<>();
        String firstRefusal = "";
        for (String claimId : common) {
            Claim candidate = claimById(workspaceId, claimId);
            if (candidate == null) {
                if (firstRefusal.isEmpty()) {
                    firstRefusal = "the workspace does not hold the claim " + claimId
                            + ", so there is no production claim to validate";
                }
                continue;
            }
            // ── the containment gate: the source must LITERALLY contain it ─────────
            Source source = sourceById(workspaceId, candidate.introducedBySourceId());
            if (source == null) {
                if (firstRefusal.isEmpty()) {
                    firstRefusal = "the source " + candidate.introducedBySourceId()
                            + " of the claim " + candidate.id() + " is not in the workspace, so "
                            + "the proposition cannot be checked against the text";
                }
                continue;
            }
            String text = source.excerpt() == null ? "" : source.excerpt();
            if (!text.contains(candidate.proposition())) {
                if (firstRefusal.isEmpty()) {
                    firstRefusal = "the proposition of the claim " + candidate.id()
                            + " does not occur verbatim in the text of " + source.id() + ", so it "
                            + "is a paraphrase of the document rather than a span of it, and a "
                            + "learned claim may assert only what the document literally "
                            + "contains";
                }
                continue;
            }
            // ── the agreement gate: the reference must carry it on EVERY case ──────
            String disagreement = "";
            for (int index = 0; index < heldOut.size(); index++) {
                boolean carries = references.get(index).claims().stream()
                        .anyMatch(claim -> claim.id().equals(candidate.id()));
                if (!carries) {
                    disagreement = "the candidate was not among the claims the reference cited "
                            + "on the held-out case '" + heldOut.get(index).caseId() + "', so it "
                            + "does not answer that question and was not promoted";
                    break;
                }
            }
            if (!disagreement.isEmpty()) {
                if (firstRefusal.isEmpty()) {
                    firstRefusal = disagreement;
                }
                continue;
            }

            Set<String> claimSupport = new LinkedHashSet<>(support);
            claimSupport.add(source.id());
            LearnedClaim learned = LearnedClaim.of(workspaceId, candidate.id(),
                    candidate.proposition(), candidate.conceptId(), source.id(), claimSupport,
                    heldOut.size(), 1.0, episodeIds(observed),
                    "validated on " + heldOut.size() + " held-out case(s) against the reference, "
                            + "and its proposition occurs verbatim in the text of " + source.id(),
                    promotedAt);
            store.recordLearnedClaim(workspaceId, learned);
            promoted.add(learned);
        }

        if (promoted.isEmpty()) {
            return new Outcome(false, firstRefusal.isEmpty()
                    ? "no candidate survived the gates"
                    : firstRefusal, List.of());
        }
        return new Outcome(true, promoted.size() + " candidate(s) were carried by the reference "
                + "on all " + heldOut.size() + " held-out case(s) and were promoted", promoted);
    }

    /**
     * The claims EVERY observed journey cited.
     *
     * @param observed the observations
     * @return the common claims, in the order the first observation cited them
     */
    private static List<String> commonClaims(List<Observation> observed) {
        List<String> common = null;
        for (Observation observation : observed) {
            List<String> cited = observation.claimIds() == null ? List.of() : observation.claimIds();
            if (common == null) {
                common = new ArrayList<>(cited);
                continue;
            }
            common.retainAll(cited);
        }
        return common == null ? List.of() : common;
    }

    /** The workspace's claim with that id, or null. */
    private Claim claimById(String workspaceId, String claimId) {
        for (Claim claim : workbench.workspace(workspaceId).claims()) {
            if (claim.id().equals(claimId)) {
                return claim;
            }
        }
        return null;
    }

    /** The workspace's source with that id, or null. */
    private Source sourceById(String workspaceId, String sourceId) {
        if (sourceId == null) {
            return null;
        }
        for (Source source : workbench.workspace(workspaceId).sources()) {
            if (source.id().equals(sourceId)) {
                return source;
            }
        }
        return null;
    }

    /** The episodes the observations came from. */
    private static String episodeIds(List<Observation> observed) {
        List<String> ids = new ArrayList<>();
        for (Observation observation : observed) {
            if (observation.episodeId() != null && !ids.contains(observation.episodeId())) {
                ids.add(observation.episodeId());
            }
        }
        return String.join(",", ids);
    }

    /** The validation kind a learned claim carries. */
    public static ValidationKind kind() {
        return ValidationKind.INDEPENDENT;
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
}
