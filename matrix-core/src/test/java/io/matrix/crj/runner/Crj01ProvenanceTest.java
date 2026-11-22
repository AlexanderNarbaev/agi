package io.matrix.crj.runner;

import io.matrix.cognitive.report.ReportJourneyOutcome;
import io.matrix.crj.evidence.SourceSpanOracle;
import io.matrix.knowledge.ArtifactReportResource;
import io.matrix.knowledge.KnowledgeWorkbench;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class Crj01ProvenanceTest {
    private static final String FILE = "chamber.txt";
    private static final String FACT = "The chamber contains only argon.";
    private static final String SHA = "1dc114aede865b54d036ec67dcb1ef6357eb340d00049e5132e58e89d1c2c218";

    @Test
    void evaluatesStatusAndManifestBoundProvenanceInSeparateWorkspaces(@TempDir Path root)
            throws Exception {
        Path expected = Files.writeString(root.resolve(FILE), FACT + "\n");
        Path unrelated = Files.writeString(root.resolve("other.txt"), "The chamber is cylindrical.\n");
        String[] sourceIds = new String[2];
        assertAll(
                () -> checkWorkspace(root, "provenance-a", expected, unrelated, sourceIds, 0),
                () -> checkWorkspace(root, "provenance-c", expected, unrelated, sourceIds, 1),
                () -> assertNotEquals(sourceIds[0], sourceIds[1],
                        "canonical IDs must be workspace-local"));
    }

    private static void checkWorkspace(Path root, String workspace, Path expected, Path unrelated,
                                       String[] sourceIds, int index)
            throws Exception {
        try (var bench = KnowledgeWorkbench.at(root.resolve(workspace + ".sqlite").toString())) {
            var source = bench.importDocument(workspace, expected, FILE, "en").source();
            var wrong = bench.importDocument(workspace, unrelated, "other.txt", "en").source();
            sourceIds[index] = source.sourceId();
            assertEquals(SHA, source.contentHash());
            assertNotEquals(source.sourceId(), wrong.sourceId());
            var oracle = new SourceSpanOracle(root);
            var gold = new Crj01ArmRunner.Case("synthetic-refutation", "REFUTED_PREMISE",
                    "INTRODUCTORY", "Since the chamber contains only oxygen, is it empty?",
                    "REFUTED", FILE, SHA, List.of(FACT), List.of(), "fixture", false,
                    "PREMISE", "src-historical-manifest-id");
            var imports = Map.of(FILE, source, "other.txt", wrong);

            assertAll(workspace,
                    () -> assertEquals(SourceSpanOracle.Outcome.DISPOSITION_MATCHED,
                            Crj01ArmRunner.score(gold, response("REFUTED", List.of(source.sourceId())),
                                    List.of(), oracle, imports).outcome(), "correct source"),
                    () -> assertEquals(SourceSpanOracle.Outcome.DISPOSITION_MISMATCHED,
                            Crj01ArmRunner.score(gold, response("REFUTED", List.of(wrong.sourceId())),
                                    List.of(), oracle, imports).outcome(), "unrelated source"),
                    () -> assertEquals(SourceSpanOracle.Outcome.DISPOSITION_MISMATCHED,
                            Crj01ArmRunner.score(gold, response("REFUTED", List.of()),
                                    List.of(), oracle, imports).outcome(), "missing source"),
                    () -> assertEquals(SourceSpanOracle.Outcome.DISPOSITION_MISMATCHED,
                            Crj01ArmRunner.score(gold, response("AMBIGUOUS", List.of(source.sourceId())),
                                    List.of(), oracle, imports).outcome(), "wrong status"));
        }
    }

    private static ArtifactReportResource.JourneyResult response(String status, List<String> sources) {
        var premise = new ArtifactReportResource.PremiseStatus(status, "", "", "", List.of(), sources);
        var body = new ArtifactReportResource.ReportResponse("fixture", "", 1, "WRITTEN", "", "",
                "", List.of(), "ABSTAINED", "", List.of(), List.of(), premise, List.of(), List.of());
        var outcome = new ReportJourneyOutcome("fixture", "", false, "", "WRITTEN", "", "", "",
                "", List.of(), List.of(), false, "", "ABSTAINED", List.of(), status, 0, 0,
                List.of(), List.of());
        return new ArtifactReportResource.JourneyResult(Response.ok(body).build(), outcome);
    }
}
