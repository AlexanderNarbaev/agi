package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import io.matrix.cognitive.report.ReportJourneyRepeatabilityProbe;
import io.matrix.cognitive.report.ReportJourneyRepeatabilityProbe.Result;
import io.matrix.security.NetworkExposure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * P2 — the read-only design probe of the path's repeatability.
 *
 * <p><b>What the directive asks for.</b> A probe that measures, on the existing route, how
 * often a second analogous request would consult a trace — and that can be repeated
 * without changing the state it measures.</p>
 *
 * <p><b>What this test establishes.</b> That the path repeats (every request takes it),
 * that the census over the durable trace reads the same numbers the live sequence
 * produced, that running the census again changes nothing, and — the finding the probe
 * exists to deliver — how narrow the trace's consultation is in a realistic sequence:
 * only a repeated identity consults it, because the retained journey is bound to the
 * action's identity.</p>
 */
class ReportJourneyRepeatabilityProbeTest {

    /** The workspace the journey runs over; the bundled photosynthesis package. */
    private static final String WORKSPACE = KnowledgeWorkbench.DEFAULT_WORKSPACE;

    /** The question most of the sequence asks. */
    private static final String QUESTION = "What is photosynthesis?";

    /** The permissions an authorized output root must have: nobody else may write to it. */
    private static final EnumSet<PosixFilePermission> OWNER_ONLY = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);

    @TempDir
    Path directory;

    /** The workbenches this test opened, so that every one of them is closed. */
    private final List<KnowledgeWorkbench> workbenches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        NetworkExposure.configure(NetworkExposure.Mode.LOCAL_ONLY, null);
    }

    @AfterEach
    void tearDown() {
        NetworkExposure.reset();
        workbenches.forEach(KnowledgeWorkbench::close);
    }

    @Test
    @DisplayName("the read-only probe shows that the path repeats, and repeats itself unchanged")
    void theProbeShowsThatThePathRepeats() throws Exception {
        CognitiveService cognitive = new CognitiveService();
        ArtifactReportResource route = route("probe", cognitive);

        // A REALISTIC SEQUENCE: the same question twice under one key, then the same
        // question under another key, then another question.
        List<ArtifactReportResource.ReportRequest> sequence = List.of(
                request(QUESTION, "key-a"),
                request(QUESTION, "key-a"),
                request(QUESTION, "key-b"),
                request("What is chlorophyll?", "key-c"));

        Result live = ReportJourneyRepeatabilityProbe.run(route, WORKSPACE, sequence);

        assertThat(live.requests()).isEqualTo(4);
        assertThat(live.thePathRepeats())
                .as("the journey repeats: every request took the path")
                .isTrue();
        assertThat(live.replays())
                .as("only the repeated identity found a retained journey")
                .isEqualTo(1);

        // THE CENSUS READS THE SAME NUMBERS FROM THE DURABLE TRACE — and changes nothing.
        Result census = ReportJourneyRepeatabilityProbe.census(cognitive.store());
        assertThat(census.journeys()).isEqualTo(4);
        assertThat(census.replays()).isEqualTo(1);
        assertThat(census.traceConsultations())
                .as("the trace was consulted once: the retained journey is bound to the "
                        + "action's identity, so an analogous request under another key does "
                        + "not find it")
                .isEqualTo(1);
        assertThat(census.episodes())
                .as("three identities, four journeys")
                .hasSize(3);

        // RUNNING THE PROBE AGAIN CHANGES NOTHING — the census is read-only.
        Result again = ReportJourneyRepeatabilityProbe.census(cognitive.store());
        assertThat(again).isEqualTo(census);

        // AND THE PROBE CAN BE REPEATED ON A FRESH COPY: the same sequence, the same
        // numbers, the same identities.
        CognitiveService secondService = new CognitiveService();
        ArtifactReportResource second = route("probe-again", secondService);
        Result liveAgain = ReportJourneyRepeatabilityProbe.run(second, WORKSPACE, sequence);
        Result censusAgain = ReportJourneyRepeatabilityProbe.census(secondService.store());
        assertThat(liveAgain).isEqualTo(live);
        assertThat(censusAgain).isEqualTo(census);
    }

    // ── the fixtures ────────────────────────────────────────────────────────

    /** The route over a fresh temporary workspace, wired the way the container wires it. */
    private ArtifactReportResource route(String name, CognitiveService cognitive)
            throws IOException {
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(
                Files.createDirectories(directory.resolve("databases")).resolve(name + ".sqlite")
                        .toString());
        bench.loadBundledPackage(WORKSPACE);
        workbenches.add(bench);
        Path root = Files.createDirectories(directory.resolve("roots").resolve(name));
        Files.setPosixFilePermissions(root, OWNER_ONLY);
        cognitive.workbench(bench);
        ArtifactReportResource route = new ArtifactReportResource();
        route.workbench = bench;
        route.actions = new ArtifactActionService(true, root.toString());
        route.planner = new ReportJourneyPlanner();
        route.cognitive = cognitive;
        return route;
    }

    /** One request of the sequence. */
    private static ArtifactReportResource.ReportRequest request(String question, String key) {
        return new ArtifactReportResource.ReportRequest(question, null, Boolean.TRUE, key, null);
    }
}
