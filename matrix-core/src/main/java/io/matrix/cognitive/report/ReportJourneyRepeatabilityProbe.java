package io.matrix.cognitive.report;

import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.LearningTrace;
import io.matrix.knowledge.ArtifactReportResource;

import java.util.ArrayList;
import java.util.List;

/**
 * P2 — the read-only design probe of the path's repeatability.
 *
 * <p><b>What the probe asks.</b> The design's next executable step: "measure, on the
 * existing route, how often a second analogous request would even consult a trace — i.e.
 * whether the journey repeats at all in a realistic sequence of requests. If the journey
 * does not repeat, the loop's premise is false."</p>
 *
 * <p><b>What the probe does NOT do.</b> It does not change anything. The census reads the
 * durable trace and reports what the path did; running it twice over the same trace
 * gives the same answer, because a probe that changed the state it measures would be
 * measuring its own effect. The live mode exists only to produce a trace to census: it
 * runs over a temporary workspace and leaves the owner's store untouched.</p>
 *
 * <p><b>What the numbers mean.</b> A <b>journey</b> is one request that took the path. A
 * <b>trace consultation</b> is a journey that found a retained journey and could therefore
 * consider the replay — the replay is admissible only when the trace was read, so the
 * two counts are the same fact seen from two sides. A <b>replay</b> is a journey that
 * chose the replay.</p>
 */
public final class ReportJourneyRepeatabilityProbe {

    private ReportJourneyRepeatabilityProbe() {
    }

    /**
     * What the probe measured.
     *
     * @param episodes            the identities the trace holds a journey for
     * @param journeys            the journeys the path took
     * @param traceConsultations  the journeys that found a retained journey
     * @param replays             the journeys that chose the replay
     * @param requests            the requests the live mode made
     */
    public record Result(List<String> episodes, int journeys, int traceConsultations,
            int replays, int requests) {

        public Result {
            episodes = episodes == null ? List.of() : List.copyOf(episodes);
        }

        /** Whether every request took the path: the journey repeats. */
        public boolean thePathRepeats() {
            return requests == 0 || journeys >= requests;
        }
    }

    /**
     * The census: what the durable trace says, read without changing anything.
     *
     * @param store the kernel's store
     * @return what the trace holds
     */
    public static Result census(CognitiveStore store) {
        List<String> episodes = new ArrayList<>();
        int journeys = 0;
        int consultations = 0;
        int replays = 0;
        for (String episode : store.eventEpisodeIds()) {
            List<LearningTrace.Step> steps = store.events(episode);
            int episodeJourneys = 0;
            for (LearningTrace.Step step : steps) {
                if (ReportJourneyTrace.PHASE_PLAN.equals(step.phase())) {
                    episodeJourneys++;
                }
                if (ReportJourneyTrace.PHASE_PERMISSION.equals(step.phase())
                        && step.detail().contains("a retained journey was found")) {
                    consultations++;
                }
                if (ReportJourneyTrace.PHASE_RESULT.equals(step.phase())
                        && step.detail().contains("by replaying the retained journey")) {
                    replays++;
                }
            }
            if (episodeJourneys == 0) {
                continue;
            }
            episodes.add(episode);
            journeys += episodeJourneys;
        }
        return new Result(episodes, journeys, consultations, replays, journeys);
    }

    /**
     * The live probe: a sequence of analogous requests, and what the path did with them.
     *
     * <p>The sequence is the caller's: the probe does not invent requests, because
     * "analogous" is a property of the traffic, not of the probe. It measures what the
     * route did with them.</p>
     *
     * @param route      the route the requests are made through
     * @param workspaceId the workspace the requests are addressed to
     * @param requests   the requests, in order
     * @return what the path did
     */
    public static Result run(ArtifactReportResource route, String workspaceId,
            List<ArtifactReportResource.ReportRequest> requests) {
        int replays = 0;
        for (ArtifactReportResource.ReportRequest request : requests) {
            ArtifactReportResource.JourneyResult result = route.journey(workspaceId, request, null);
            if ("REPLAYED".equals(result.outcome().disposition())) {
                replays++;
            }
        }
        return new Result(List.of(), requests.size(), replays, replays, requests.size());
    }
}
