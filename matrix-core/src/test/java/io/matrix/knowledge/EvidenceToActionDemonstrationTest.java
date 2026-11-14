package io.matrix.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.actions.ActionRegistry;
import io.matrix.actions.ArtifactAction;
import io.matrix.actions.ArtifactWriter;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B3 — the Evidence-to-Action demonstration: a real artifact, from real evidence, through the
 * real action contract, with the negative controls.
 *
 * <p>The workflow is the directive's: the licensed seed corpus enters a workspace through the
 * production import path; the workspace answers through its own retrieval; the report is
 * assembled from the answer's citations (never from anything the test invents); the report
 * bytes pass through the registered bounded file action; the artifact is verified on disk;
 * the trace (the writer's append-only index) is read back; and the negative controls attempt
 * the writes that must be refused.</p>
 *
 * <p>What it does not claim: the report's sentences are the workspace's claims, and the write
 * certifies only that the artifact was written — not that its contents are true. Sections the
 * workspace cannot support are marked {@code UNGROUNDED} and are not promoted.</p>
 */
class EvidenceToActionDemonstrationTest {

    private static final String QUESTION =
            "What is plate tectonics and what does it say about the Earth's lithosphere?";
    private static final String WORKSPACE = "b3-demonstration";
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporary;

    @Test
    @DisplayName("B3 — evidence to a real bounded artifact, with refusals and a trace")
    void evidenceToAction() throws Exception {
        Path database = temporary.resolve("workbench.sqlite");
        // the positive write lands in the persistent demonstration directory when the
        // runbook names one, so the artifact and its trace can be inspected after the run
        String persistent = System.getProperty("matrix.b3.dir");
        Path root = persistent == null || persistent.isBlank()
                ? temporary.resolve("out") : Path.of(persistent);
        Files.createDirectories(root);
        // the negative controls get a throwaway root of their own, so the persistent
        // demonstration directory holds only the authorized artifact and its trace
        Path controlsRoot = temporary.resolve("controls");
        Files.createDirectories(controlsRoot);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("record", "B3-EVIDENCE-TO-ACTION");
        report.put("date", "2026-11-14");
        report.put("task", QUESTION);
        report.put("workspace", WORKSPACE);

        long start = System.nanoTime();
        KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString());
        long seedStart = System.nanoTime();
        AcquisitionCycle.Report seed = AcquisitionCycle.loadSeed(workbench, WORKSPACE);
        long seedNanos = System.nanoTime() - seedStart;
        Answer answer = workbench.ask(WORKSPACE, QUESTION, AbstractionLevel.INTRODUCTORY);

        report.put("sources", answer.sources().stream().map(Source::title).toList());
        report.put("claims", answer.claims().size());
        report.put("answered", answer.answered());
        report.put("statement", answer.statement());
        report.put("limitations", answer.limitations());
        report.put("unmatchedTerms", answer.unmatchedTerms());
        report.put("seedDocuments", seed != null ? seed.acquired() : List.of());
        report.put("seedClaimsAdded", seed != null ? seed.claimsAdded() : 0);

        // ── the report, assembled only from the workspace's evidence ────────
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Evidence report\n\n");
        markdown.append("**Question.** ").append(QUESTION).append("\n\n");
        markdown.append("**Workspace.** ").append(WORKSPACE)
                .append(" · **Sources.** ").append(answer.sources().size())
                .append(" · **Claims.** ").append(answer.claims().size()).append("\n\n");
        markdown.append("## Answer\n\n");
        if (answer.answered()) {
            markdown.append(answer.statement()).append("\n\n");
        } else {
            markdown.append("The workspace could not answer this question: ")
                    .append(answer.abstentionReason()).append("\n\n");
            markdown.append("**UNGROUNDED** — no answer is asserted.\n\n");
        }
        markdown.append("## Supported claims\n\n");
        if (answer.claims().isEmpty()) {
            markdown.append("_None: the workspace holds no established claim for this question._\n\n");
        }
        Map<String, String> sourceById = new LinkedHashMap<>();
        answer.sources().forEach(source -> sourceById.put(source.id(), source.title()));
        for (Claim claim : answer.claims()) {
            markdown.append("- `").append(claim.id()).append("` ").append(claim.proposition())
                    .append(" — ").append(claim.status()).append("\n");
        }
        markdown.append("\n## Sources\n\n");
        for (Source source : answer.sources()) {
            markdown.append("- ").append(source.title()).append(" (")
                    .append(source.id()).append(")\n");
        }
        markdown.append("\n## Limitations\n\n");
        for (String limitation : answer.limitations()) {
            markdown.append("- ").append(limitation).append("\n");
        }
        if (!answer.unmatchedTerms().isEmpty()) {
            markdown.append("\n## Not covered by the workspace\n\n");
            for (String term : answer.unmatchedTerms()) {
                markdown.append("- `").append(term).append("`\n");
            }
        }
        markdown.append("\n## Epistemic status\n\n");
        markdown.append("- The claims above are the workspace's own, with their ids and "
                + "statuses; this report asserts nothing the citations do not carry.\n");
        if (!answer.answered()) {
            markdown.append("- The question is **not answered**; the absence of an answer is "
                    + "the epistemic result, not a defect of the file action.\n");
        }
        byte[] content = markdown.toString().getBytes(StandardCharsets.UTF_8);

        // ── the action ──────────────────────────────────────────────────────
        ArtifactWriter writer = new ArtifactWriter(root);
        ActionRegistry registry = new ActionRegistry();
        registry.register(ArtifactAction.of(writer));
        assertThat(registry.has(ArtifactAction.NAME)).isTrue();

        long writeStart = System.nanoTime();
        ActionRegistry.ActionResult actionResult = registry.get(ArtifactAction.NAME).execute(Map.of(
                "actionId", "b3-demo-1", "fileName", "evidence-report.md", "content", content));
        long writeNanos = System.nanoTime() - writeStart;
        assertThat(actionResult.success()).isTrue();
        ArtifactWriter.Result written = (ArtifactWriter.Result) actionResult.output();
        // against a directory that already holds this artifact (the committed demonstration)
        // the writer reports the commit it finds; both dispositions mean the artifact is there
        assertThat(written.disposition()).isIn(ArtifactWriter.Disposition.WRITTEN,
                ArtifactWriter.Disposition.REPLAYED);

        // ── the verification ────────────────────────────────────────────────
        Path artifact = root.resolve("evidence-report.md");
        assertThat(artifact).exists();
        String text = Files.readString(artifact);
        assertThat(written.sha256()).isEqualTo(sha256(artifact));
        for (Claim claim : answer.claims()) {
            assertThat(text).contains(claim.id());
        }
        for (Source source : answer.sources()) {
            assertThat(text).contains(source.title());
        }
        report.put("artifactPath", artifact.toString());
        report.put("artifactBytes", content.length);
        report.put("artifactSha256", written.sha256());
        report.put("verification", "the artifact exists, its digest matches the action's "
                + "record, and every cited claim and source appears in it");

        // ── the negative controls ───────────────────────────────────────────
        List<Map<String, Object>> refusals = new ArrayList<>();
        Path outside = temporary.resolve("symlink-target.md");
        Files.writeString(outside, "# outside\n");
        Files.createSymbolicLink(controlsRoot.resolve("escape.md"), outside);
        ArtifactWriter controlsWriter = new ArtifactWriter(controlsRoot);
        ActionRegistry controlsRegistry = new ActionRegistry();
        controlsRegistry.register(ArtifactAction.of(controlsWriter));
        for (String[] attempt : new String[][] {
                {"b3-outside", "../outside.md"},
                {"b3-absolute", "/etc/evidence-report.md"},
                {"b3-symlink", "escape.md"}}) {
            ActionRegistry.ActionResult refused = controlsRegistry.get(ArtifactAction.NAME)
                    .execute(Map.of("actionId", attempt[0], "fileName", attempt[1],
                            "content", content));
            assertThat(refused.success()).isFalse();
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("actionId", attempt[0]);
            record.put("fileName", attempt[1]);
            record.put("outcome", refused.error());
            refusals.add(record);
        }
        assertThat(Files.readString(outside)).isEqualTo("# outside\n");
        assertThat(Files.exists(Path.of("/etc/evidence-report.md"))).isFalse();
        report.put("refusals", refusals);

        // ── the idempotent retry and the crash recovery ─────────────────────
        ActionRegistry.ActionResult retry = registry.get(ArtifactAction.NAME).execute(Map.of(
                "actionId", "b3-demo-1", "fileName", "evidence-report.md", "content", content));
        assertThat(retry.success()).isTrue();
        assertThat(((ArtifactWriter.Result) retry.output()).disposition())
                .isEqualTo(ArtifactWriter.Disposition.REPLAYED);
        Map<String, Object> recovery = writer.recover("b3-demo-1");
        assertThat(recovery.get("disposition")).isEqualTo("COMMITTED");
        report.put("retryDisposition", "REPLAYED (no second write)");
        report.put("recovery", recovery);

        // ── the trace, read back from disk ──────────────────────────────────
        List<ArtifactWriter.Record> trace = writer.readIndex();
        report.put("tracePath", writer.index().toString());
        report.put("traceRecords", trace.size());
        report.put("trace", trace);
        assertThat(trace).extracting(ArtifactWriter.Record::status)
                .contains("STAGED", "COMMITTED");
        for (Claim claim : answer.claims()) {
            boolean known = trace.stream().anyMatch(record ->
                    record.actionId().equals("b3-demo-1"));
            assertThat(known).isTrue();
        }

        long totalNanos = System.nanoTime() - start;
        report.put("latencyMillis", Map.of(
                "seed", seedNanos / 1_000_000,
                "write", writeNanos / 1_000_000,
                "total", totalNanos / 1_000_000));

        System.out.println("[B3] " + JSON.writeValueAsString(report));
        String artifactPath = System.getProperty("matrix.b3.result");
        if (artifactPath != null && !artifactPath.isBlank()) {
            Path out = Path.of(artifactPath);
            Files.createDirectories(out.toAbsolutePath().getParent());
            Files.writeString(out, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            System.out.println("[B3] the result is at " + out.toAbsolutePath());
        }
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(file)));
    }
}
