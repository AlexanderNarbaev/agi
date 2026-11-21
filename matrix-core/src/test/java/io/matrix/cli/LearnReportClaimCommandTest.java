package io.matrix.cli;

import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.report.ReportJourneyTrace;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.ArtifactReportResource;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The operator's command for the learned claim.
 *
 * <p><b>What this pins.</b> That the command is the operator's path to the second half of arm
 * C: it drives the learner against the journeys' own traces, it <b>fails closed</b> without the
 * quarantine list, and when it promotes, the promoted claim is in the store with its version
 * and its provenance.</p>
 *
 * <p><b>Why the command runs alone.</b> The store has one writer, so the journey that
 * produced the observations is finished and its store is closed before the command opens the
 * same database — exactly as an operator would do it.</p>
 */
class LearnReportClaimCommandTest {

    /** The workspace the pool is imported into. */
    private static final String WORKSPACE = "research-pool";

    @Test
    @DisplayName("the command refuses without the quarantine file, because the gate fails closed")
    void theAbsentQuarantineFileRefuses(@TempDir Path directory) throws Exception {
        Path cases = directory.resolve("cases.tsv");
        Files.writeString(cases, "case-0\tWhat is plate tectonics?\n", StandardCharsets.UTF_8);

        int exit = new picocli.CommandLine(new LearnReportClaimCommand()).execute(
                "--workspace", WORKSPACE,
                "--episodes", "episode-0",
                "--cases", cases.toString(),
                "--quarantine", directory.resolve("absent.txt").toString(),
                "--knowledge-path", directory.resolve("db.sqlite").toString());

        assertThat(exit).as("the command refuses before it touches anything").isEqualTo(2);
    }

    @Test
    @DisplayName("the command refuses without the cases file")
    void theAbsentCasesFileRefuses(@TempDir Path directory) throws Exception {
        Path quarantine = directory.resolve("quarantine.txt");
        Files.writeString(quarantine, "", StandardCharsets.UTF_8);

        int exit = new picocli.CommandLine(new LearnReportClaimCommand()).execute(
                "--workspace", WORKSPACE,
                "--episodes", "episode-0",
                "--cases", directory.resolve("absent.tsv").toString(),
                "--quarantine", quarantine.toString(),
                "--knowledge-path", directory.resolve("db.sqlite").toString());

        assertThat(exit).isEqualTo(2);
    }

    @Test
    @DisplayName("the command promotes the claim the journeys agreed on, with its provenance")
    void theCommandPromotesTheClaim(@TempDir Path directory) throws Exception {
        Path root = Files.createDirectories(directory.resolve("root"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");

        // ── the journeys: they leave the retained payload the learner reads ──────
        String episodeId;
        {
            KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
            ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
            for (ClasspathCorpus.Entry entry : pool.entries()) {
                bench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
            }
            CognitiveService cognitive = new CognitiveService();
            cognitive.workbench(bench);
            ArtifactReportResource route = new ArtifactReportResource();
            route.workbench = bench;
            route.actions = new ArtifactActionService(true, root.toString());
            route.planner = new io.matrix.cognitive.report.ReportJourneyPlanner();
            route.cognitive = cognitive;
            // the journey's question must DIFFER from every held-out case's question: the
            // held-out rule refuses a case that asks the question the claim was proposed
            // from, and a case that does is not held out
            route.report(WORKSPACE, new ArtifactReportResource.ReportRequest(
                    "What is the theory of plate tectonics?", null, true, "key-cli", null));
            episodeId = ReportJourneyTrace.episodeId(route.actions.identity(WORKSPACE, "key-cli",
                    "What is the theory of plate tectonics?"));
            // the one-writer rule: the store is closed before the command opens the file
            cognitive.store().close();
            bench.close();
        }

        Path cases = directory.resolve("cases.tsv");
        Files.writeString(cases, """
                case-0	What is plate tectonics?
                case-1	What does plate tectonics describe?
                case-2	Explain plate tectonics
                case-3	What causes plate tectonics?
                case-4	How does plate tectonics work?
                """, StandardCharsets.UTF_8);
        Path quarantine = directory.resolve("quarantine.txt");
        Files.writeString(quarantine, "", StandardCharsets.UTF_8);

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.PrintStream previous = System.out;
        System.setOut(new java.io.PrintStream(out, true, StandardCharsets.UTF_8));
        int exit;
        try {
            exit = new picocli.CommandLine(new LearnReportClaimCommand()).execute(
                    "--workspace", WORKSPACE,
                    "--episodes", episodeId,
                    "--cases", cases.toString(),
                    "--quarantine", quarantine.toString(),
                    "--knowledge-path", database.toString());
        } finally {
            System.setOut(previous);
        }
        String output = out.toString(StandardCharsets.UTF_8);

        assertThat(exit).as("the command promoted the claim; it said: " + output).isEqualTo(0);

        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        try {
            CognitiveService cognitive = new CognitiveService();
            cognitive.workbench(bench);
            List<io.matrix.cognitive.report.LearnedClaim> claims =
                    cognitive.store().learnedClaims(WORKSPACE);
            // the learner promotes EVERY candidate that survives the gates, so the store
            // holds the claims the reference carried on all five held-out cases - not one
            assertThat(claims).as("the promoted claims are in the store").isNotEmpty();
            for (io.matrix.cognitive.report.LearnedClaim claim : claims) {
                assertThat(claim.version()).isEqualTo(
                        io.matrix.cognitive.report.LearnedClaim.FIRST_VERSION);
                assertThat(claim.learnedInEpisode()).contains(episodeId);
                assertThat(claim.validationCases()).isEqualTo(5);
                assertThat(claim.agreement()).isEqualTo(1.0);
                assertThat(claim.validationNote()).contains("verbatim");
            }
            cognitive.store().close();
        } finally {
            bench.close();
        }
    }
}
