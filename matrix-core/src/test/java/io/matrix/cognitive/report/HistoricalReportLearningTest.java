package io.matrix.cognitive.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.cognitive.CognitiveStore;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HistoricalReportLearningTest {

    @Test
    void historicalReportsDoNotTrainEitherLearner(@TempDir Path directory) {
        CognitiveStore store = new CognitiveStore(directory.resolve("cognitive.sqlite"));
        try {
            retain(store, "historical", true);
            retain(store, "current", false);
            var episodes = List.of("historical", "current");
            assertThat(ReportClaimLearner.observationsFrom(store, episodes)).hasSize(1);
            assertThat(ReportClaimLearner.observationsFrom(store, List.of("historical"))).isEmpty();
            assertThat(ReportProcedureLearner.observationsFrom(store, episodes)).hasSize(1);
            assertThat(ReportProcedureLearner.observationsFrom(store, List.of("historical"))).isEmpty();
        } finally {
            store.close();
        }
    }

    private static void retain(CognitiveStore store, String episode, boolean historical) {
        ReportJourneyTrace.ensureEpisode(store, episode, "workspace-a", episode);
        var retained = new ReportJourney.Retained("reference", "What is recorded?",
                "INTRODUCTORY", "workspace-fingerprint", "artifact-digest", "report.md",
                """
                {"evidence":[{"claimId":"claim-1","sourceId":"source-1"}]}
                """, historical);
        ReportJourneyTrace.appendAll(store, episode, List.of(new CognitiveStore.EventRecord(
                ReportJourneyTrace.PHASE_RETAINED, ReportJourneyTrace.ACTOR_ROUTE,
                ReportJourneyTrace.payload(retained))));
    }
}
