package io.matrix.cognitive.report;

import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.knowledge.ArtifactReportResource;
import io.matrix.knowledge.KnowledgeWorkbench;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The report journey's trace episode.
 *
 * <p><b>Why this test exists.</b> A journey's trace lives in an episode — the store's own
 * container for "one run of something" — and every step of the journey is appended to it. But
 * the trace only ever wrote <b>events</b>, and an event names its episode in a column; nothing
 * ever <b>opened</b> the episode itself. So the journey's episode existed in the event table
 * and did not exist in the episode table, and the store's own listing of a workspace's
 * episodes did not contain it.</p>
 *
 * <p><b>Why that matters.</b> The learned procedure's provenance names the episode it was
 * learned in. A reader who follows that provenance — through the store's own accessor — found
 * nothing: the account of the episode the competence came from was not there, even though
 * every step of the journey was.</p>
 *
 * <p><b>What this test pins.</b> That the journey's episode is a REAL episode: it is opened,
 * it belongs to the workspace the journey served, and it is opened <b>once</b> — a replayed
 * journey continues the same episode rather than opening a second one or a conflicting
 * one.</p>
 */
class ReportJourneyEpisodeTest {

    @Test
    @DisplayName("the journey's trace episode is a real episode of the workspace")
    void theJourneyOpensItsEpisode(@TempDir Path directory) throws Exception {
        Path root = Files.createDirectories(directory.resolve("root"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        bench.loadBundledPackage(KnowledgeWorkbench.DEFAULT_WORKSPACE);
        try {
            CognitiveService cognitive = new CognitiveService();
            cognitive.workbench(bench);
            ArtifactReportResource route = route(bench, cognitive, root);

            var response = route.report(KnowledgeWorkbench.DEFAULT_WORKSPACE,
                    new ArtifactReportResource.ReportRequest("What is photosynthesis?",
                            null, true, "key-episode", null));
            assertThat(response.getStatus()).as("the journey succeeded").isEqualTo(201);

            CognitiveStore store = cognitive.store();
            // the identity is the ACTION's own derivation, so the test asks the same
            // question the route asks rather than guessing the shape of the answer
            String identity = route.actions.identity(KnowledgeWorkbench.DEFAULT_WORKSPACE,
                    "key-episode", "What is photosynthesis?");
            String episodeId = ReportJourneyTrace.episodeId(identity);

            assertThat(store.episodes(KnowledgeWorkbench.DEFAULT_WORKSPACE))
                    .as("the journey's episode is one of the workspace's episodes")
                    .contains(episodeId);
            assertThat(store.events(episodeId))
                    .as("and the journey's steps live in it")
                    .isNotEmpty();
        } finally {
            bench.close();
        }
    }

    @Test
    @DisplayName("a replayed journey continues the SAME episode rather than opening a second one")
    void theReplayedJourneyContinuesTheEpisode(@TempDir Path directory) throws Exception {
        Path root = Files.createDirectories(directory.resolve("root"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        bench.loadBundledPackage(KnowledgeWorkbench.DEFAULT_WORKSPACE);
        try {
            CognitiveService cognitive = new CognitiveService();
            cognitive.workbench(bench);
            ArtifactReportResource route = route(bench, cognitive, root);
            var request = new ArtifactReportResource.ReportRequest("What is photosynthesis?",
                    null, true, "key-replay", null);

            var first = route.report(KnowledgeWorkbench.DEFAULT_WORKSPACE, request);
            assertThat(first.getStatus()).isEqualTo(201);
            String firstIdentity = route.actions.identity(KnowledgeWorkbench.DEFAULT_WORKSPACE,
                    "key-replay", "What is photosynthesis?");
            int stepsAfterTheFirst = cognitive.store()
                    .events(ReportJourneyTrace.episodeId(firstIdentity)).size();

            var second = route.report(KnowledgeWorkbench.DEFAULT_WORKSPACE, request);
            assertThat(second.getStatus()).as("the replay is served").isEqualTo(200);

            CognitiveStore store = cognitive.store();
            String identity = route.actions.identity(KnowledgeWorkbench.DEFAULT_WORKSPACE,
                    "key-replay", "What is photosynthesis?");
            String episodeId = ReportJourneyTrace.episodeId(identity);
            List<String> episodes = store.episodes(KnowledgeWorkbench.DEFAULT_WORKSPACE);
            assertThat(episodes)
                    .as("the replay did not open a second episode for the same identity")
                    .containsExactly(episodeId);
            assertThat(store.events(episodeId))
                    .as("the replay left its own step in the same episode")
                    .hasSizeGreaterThan(stepsAfterTheFirst);
        } finally {
            bench.close();
        }
    }

    /** The route, wired by hand the way the served path wires it. */
    private static ArtifactReportResource route(KnowledgeWorkbench bench, CognitiveService cognitive,
                                                Path root) {
        ArtifactReportResource route = new ArtifactReportResource();
        route.workbench = bench;
        route.actions = new ArtifactActionService(true, root.toString());
        route.planner = new ReportJourneyPlanner();
        route.cognitive = cognitive;
        return route;
    }
}
