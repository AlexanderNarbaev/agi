package io.matrix.cognitive.report;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.LearningTrace;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * P2 — the trace of one journey, written where the next task can read it.
 *
 * <p><b>What the trace holds.</b> The request, the method the kernel chose, the reason it
 * chose it, the action that ran, the observed effect and the verification. The trace is
 * append-only and ordered, and every step names what produced it, so a reader can go from
 * a line of the trace to the code that decided it.</p>
 *
 * <p><b>Where it is written.</b> Through the kernel's own durable mechanism: the events of
 * one episode in the cognitive store, which is the store the kernel already writes its own
 * cycles into. There is no new database and no new file system authority — the trace lives
 * beside the episodes the system already keeps, and a restart loses nothing.</p>
 *
 * <p><b>What a later task does with it.</b> It consults it before the method is chosen: the
 * episode of an identity holds what the last journey for that identity did, and the replay
 * path reads the retained answer and the workspace fingerprint out of it. A trace that
 * cannot be replayed is not evidence of anything.</p>
 */
public final class ReportJourneyTrace {

    /** The phase in which the kernel chose a method and said why. */
    public static final String PHASE_PLAN = "PLAN";

    /** The phase in which the request's permission was honoured. */
    public static final String PHASE_PERMISSION = "PERMISSION";

    /** The phase in which the action ran. */
    public static final String PHASE_ACTION = "ACTION";

    /** The phase in which the effect was observed by the independent reader. */
    public static final String PHASE_OBSERVE = "OBSERVE";

    /** The phase in which the observation was checked against what was reported. */
    public static final String PHASE_VERIFY = "VERIFY";

    /** The phase in which the journey's result was recorded. */
    public static final String PHASE_RESULT = "RESULT";

    /** The phase in which the answer was retained for a later journey. */
    public static final String PHASE_RETAINED = "RETAINED";

    /** What produced the plan. */
    public static final String ACTOR_PLANNER = "ReportJourneyPlanner";

    /** What produced the permission, the action and the result. */
    public static final String ACTOR_ROUTE = "ArtifactReportResource";

    /** What produced the action's own result. */
    public static final String ACTOR_ACTION = "ArtifactAction";

    /** What produced the observation. */
    public static final String ACTOR_OBSERVER = "ArtifactObserver";

    /** The durable counter the trace's positions come from. */
    private static final String SEQUENCE = "report-journey";

    /** The key the retained payload is stored under. */
    private static final String RETAINED_METHOD = "methodId";
    private static final String RETAINED_QUESTION = "question";
    private static final String RETAINED_LEVEL = "level";
    private static final String RETAINED_FINGERPRINT = "workspaceFingerprint";
    private static final String RETAINED_SHA256 = "sha256";
    private static final String RETAINED_FILE_NAME = "fileName";
    private static final String RETAINED_RESPONSE = "responseJson";
    private static final String RETAINED_HISTORICAL = "historical";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReportJourneyTrace() {
    }

    /**
     * The episode a journey's trace lives in.
     *
     * <p>The identity is the action's own derivation of the request, so the same request
     * names the same episode and a later journey finds what the earlier one left there.</p>
     *
     * @param identity the action identity
     * @return the episode id
     */
    public static String episodeId(String identity) {
        return "report-" + identity;
    }

    /**
     * Makes sure the journey's episode is a REAL episode of the workspace.
     *
     * <p><b>Why this exists.</b> The trace's steps live in an episode, and an episode is a row
     * in the store's own table of episodes — a container with an account of what it was, a
     * workspace it belongs to and an instant it started. Writing the steps alone left the
     * episode in the event table and nowhere else: the store's listing of a workspace's
     * episodes did not contain it, and a reader who followed a provenance to the episode a
     * competence was learned in found nothing there.</p>
     *
     * <p><b>Why it is opened and not replaced.</b> The episode is keyed by the action
     * identity, so the same request always names the same episode — and a later journey
     * continues it. The account of what the episode was belongs to the first journey that
     * opened it, and a later one does not rewrite it. The episode is opened once and stays
     * open, because the identity's record keeps growing: the trace of an identity is an
     * ongoing record, not a bounded cycle.</p>
     *
     * @param store      the kernel's store
     * @param episodeId  the episode the journey belongs to
     * @param workspaceId the workspace the journey served
     * @param identity   the action identity the episode is derived from
     * @return whether this call opened the episode
     */
    public static boolean ensureEpisode(CognitiveStore store, String episodeId, String workspaceId,
                                         String identity) {
        return store.ensureEpisode(episodeId, workspaceId, "the report journey of " + identity,
                Instant.now().toString());
    }

    /**
     * Appends several steps of one journey in one durable write.
     *
     * <p>The steps are appended in order, in one transaction: a trace is wholly there or
     * wholly absent, and the cost of the durable write is paid once rather than once per
     * step.</p>
     *
     * @param store     the kernel's store
     * @param episodeId the episode the journey belongs to
     * @param steps     the steps, in order
     */
    public static void appendAll(CognitiveStore store, String episodeId,
            List<CognitiveStore.EventRecord> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        store.appendEvents(episodeId, steps, Instant.now().toString());
    }

    /**
     * Appends one step to a journey's trace.
     *
     * @param store    the kernel's store
     * @param episodeId the episode the journey belongs to
     * @param phase    what happened
     * @param actor    what produced it
     * @param detail   what it was
     */
    public static void append(CognitiveStore store, String episodeId, String phase, String actor,
            String detail) {
        long position = store.nextSequence(SEQUENCE);
        store.appendEvent(episodeId + "-evt-" + position, episodeId, position, phase, actor,
                detail, Instant.now().toString());
    }

    /**
     * The steps of a journey's trace, in order.
     *
     * @param store     the kernel's store
     * @param episodeId the episode
     * @return the steps; empty when the episode has none
     */
    public static List<LearningTrace.Step> steps(CognitiveStore store, String episodeId) {
        return store.events(episodeId);
    }

    /**
     * The retained journey of an episode, when it left one behind.
     *
     * @param store     the kernel's store
     * @param episodeId the episode
     * @return the retained journey, or null when there is none
     */
    public static ReportJourney.Retained retained(CognitiveStore store, String episodeId) {
        for (LearningTrace.Step step : store.events(episodeId)) {
            if (!PHASE_RETAINED.equals(step.phase())) {
                continue;
            }
            return parse(step.detail());
        }
        return null;
    }

    /**
     * The retained payload of a journey, as the trace stores it.
     *
     * @param retained the retained journey
     * @return the payload, as JSON
     */
    public static String payload(ReportJourney.Retained retained) {
        try {
            return MAPPER.writeValueAsString(Map.of(
                    RETAINED_METHOD, retained.methodId(),
                    RETAINED_QUESTION, retained.question() == null ? "" : retained.question(),
                    RETAINED_LEVEL, retained.level() == null ? "" : retained.level(),
                    RETAINED_FINGERPRINT, retained.workspaceFingerprint(),
                    RETAINED_SHA256, retained.sha256(),
                    RETAINED_FILE_NAME, retained.fileName(),
                    RETAINED_RESPONSE, retained.responseJson(),
                    RETAINED_HISTORICAL, Boolean.toString(retained.historical())));
        } catch (IOException e) {
            throw new IllegalStateException("failed to write the retained journey", e);
        }
    }

    private static ReportJourney.Retained parse(String payload) {
        try {
            Map<String, String> fields = MAPPER.readValue(payload,
                    new TypeReference<Map<String, String>>() {
                    });
            // a record written before the question was retained names no question, and an
            // unnamed question is not replayable: the replay fails closed on it rather
            // than serving an answer whose question it cannot state
            String question = fields.get(RETAINED_QUESTION);
            String level = fields.get(RETAINED_LEVEL);
            return new ReportJourney.Retained(fields.get(RETAINED_METHOD),
                    question == null || question.isBlank() ? null : question,
                    level == null || level.isBlank() ? null : level,
                    fields.get(RETAINED_FINGERPRINT), fields.get(RETAINED_SHA256),
                    fields.get(RETAINED_FILE_NAME), fields.get(RETAINED_RESPONSE),
                    Boolean.parseBoolean(fields.get(RETAINED_HISTORICAL)));
        } catch (IOException e) {
            throw new IllegalStateException("failed to read the retained journey", e);
        }
    }
}
