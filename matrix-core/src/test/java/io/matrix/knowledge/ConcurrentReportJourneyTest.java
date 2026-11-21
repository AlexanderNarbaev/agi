package io.matrix.knowledge;

import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The report journey under concurrent requests.
 *
 * <p><b>Why this test exists.</b> The journey writes to the knowledge database TWICE: the
 * asked question goes into the knowledge store, and the trace goes into the cognitive store —
 * and the two stores are <b>two connections to the same SQLite file</b>. A request that runs
 * while another one is writing therefore meets a database that another connection has just
 * changed, and a read-then-write that holds its read snapshot cannot be upgraded to a write:
 * SQLite answers {@code SQLITE_BUSY} at once, and the busy timeout does not apply to that
 * case, because waiting cannot help a stale snapshot.
 *
 * <p>The journey swallowed that answer and reported it as {@code output_root_unavailable},
 * which is a lie about the cause: nothing was wrong with the output root. The request was
 * refused because two writers of one file met.</p>
 *
 * <p><b>What this test pins.</b> That two concurrent requests with the same identity both
 * succeed — the one that writes and the one that replays — over many rounds, because the
 * occurrence of the failure depends on the timing and a single round proves nothing.</p>
 */
class ConcurrentReportJourneyTest {

    private static final int ROUNDS = 40;

    @Test
    @DisplayName("two concurrent requests with the same identity both succeed, over many rounds")
    void theConcurrentRequestsBothSucceed() throws Exception {
        List<String> refusals = new ArrayList<>();
        for (int round = 0; round < ROUNDS; round++) {
            refusals.addAll(oneRound(round));
        }
        assertThat(refusals)
                .as("the journey must not refuse a request because another writer of the same "
                        + "database changed it while the request was reading")
                .isEmpty();
    }

    /** One round: two racers, the same identity, the same question. */
    private List<String> oneRound(int round) throws Exception {
        Path directory = Files.createTempDirectory("concurrent-journey-");
        Path root = Files.createDirectories(directory.resolve("root"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        bench.loadBundledPackage(KnowledgeWorkbench.DEFAULT_WORKSPACE);
        ArtifactActionService actions = new ArtifactActionService(true, root.toString());
        ArtifactReportResource route = new ArtifactReportResource();
        route.workbench = bench;
        route.actions = actions;
        route.planner = new ReportJourneyPlanner();
        CognitiveService cognitive = new CognitiveService();
        cognitive.workbench(bench);
        route.cognitive = cognitive;

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> answers = new ArrayList<>();
        try {
            for (int racer = 0; racer < 2; racer++) {
                answers.add(pool.submit(() -> {
                    start.await();
                    var response = route.report(KnowledgeWorkbench.DEFAULT_WORKSPACE,
                            new ArtifactReportResource.ReportRequest("What is photosynthesis?",
                                    null, true, "key-concurrent", null));
                    return Map.of("status", response.getStatus(),
                            "body", String.valueOf(response.getEntity()));
                }));
            }
            start.countDown();
            List<String> refusals = new ArrayList<>();
            for (Future<Object> answer : answers) {
                @SuppressWarnings("unchecked")
                Map<String, Object> read = (Map<String, Object>) answer.get();
                int status = (int) read.get("status");
                if (status != 200 && status != 201) {
                    refusals.add("round " + round + ": status " + status + " — "
                            + read.get("body"));
                }
            }
            return refusals;
        } finally {
            pool.shutdownNow();
            bench.close();
        }
    }
}
