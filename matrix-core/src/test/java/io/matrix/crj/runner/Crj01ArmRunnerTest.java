package io.matrix.crj.runner;

import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 — the arm runner, exercised on the dev split.
 *
 * <p><b>What this proves.</b> That the comparison's instrument exists and behaves as the
 * protocol requires: every case produces a row <b>for every arm</b> — including the ones that
 * were refused or inapplicable — each arm runs in its <b>own</b> workspace, each arm's answer
 * is scored by the <b>source-span oracle</b>, and the rows are written to
 * {@code CRJ-01-EVIDENCE.json} with the arm's summary.</p>
 *
 * <p><b>What this does NOT establish.</b> It measures nothing about arm C's advantage. The
 * dev split is the split the solver's own author wrote, so every number here is a
 * <b>debugging</b> number and not evidence: the protocol's §5.1 reserves the measurement for
 * the sealed split, which an author who implements no arm writes. What this run proves is
 * that the instrument produces the rows the measurement will be made of, and that the rows
 * are complete.</p>
 */
class Crj01ArmRunnerTest {

    /** The dev split the run is made on. */
    private static final String DATASET = "docs-v2/engineering/CRJ-01-DEV-SPLIT.json";

    /** The budget every arm runs under — the SAME one, so the arms are comparable. */
    private static final ComputeBudget BUDGET = new ComputeBudget(30_000L, 30_000L, 0L, 0);

    @Test
    @DisplayName("every case produces a row for every arm, and the evidence is written")
    void everyCaseProducesARowForEveryArm(@TempDir Path temporary) throws Exception {
        Path split = RepositoryPaths.root().resolve(DATASET);
        assertThat(split).as("the dev split %s is missing", DATASET).exists();
        Crj01ArmRunner.Split cases = Crj01ArmRunner.readSplit(split);
        assertThat(cases.cases()).as("the dev split carries cases").hasSize(9);

        Crj01ArmRunner runner = new Crj01ArmRunner(temporary.resolve("corpus"));
        Crj01ArmRunner.Run run = runner.run(cases, temporary.resolve("arms"), BUDGET);

        // ── the rows are COMPLETE: one per (case, arm), plus arm B's two variants ─
        Map<String, Integer> rowsPerArm = new LinkedHashMap<>();
        for (Crj01ArmRunner.Row row : run.rows()) {
            rowsPerArm.merge(row.arm(), 1, Integer::sum);
        }
        // arm A and arm C answer every case; arm B is measured twice per case, once on a
        // novel task and once on an exact repeat
        assertThat(rowsPerArm.get("A")).as("arm A's rows").isEqualTo(9);
        assertThat(rowsPerArm.get("C")).as("arm C's rows").isEqualTo(9);
        assertThat(rowsPerArm.get("B")).as("arm B's rows, novel and repeat").isEqualTo(18);
        assertThat(run.rows()).as("and every row names its case and its arm")
                .allSatisfy(row -> {
                    assertThat(row.caseId()).isNotBlank();
                    assertThat(row.arm()).isIn("A", "B", "C");
                    assertThat(row.oracleOutcome()).isNotBlank();
                });

        // ── the arms ran in the PRE-REGISTERED order, and each in its own workspace ─
        assertThat(run.rows().get(0).arm()).as("the first row is arm A's").isEqualTo("A");
        assertThat(run.rows().get(9).arm()).as("arm B's rows come second").isEqualTo("B");
        assertThat(run.rows().get(27).arm()).as("arm C's rows come last").isEqualTo("C");

        // ── arm C PROMOTED a procedure and ran it; arm A ran the reference ─
        List<Crj01ArmRunner.Row> armC = run.rows().stream()
                .filter(row -> row.arm().equals("C")).toList();
        assertThat(armC).as("arm C's rows").isNotEmpty();
        assertThat(armC).allSatisfy(row ->
                assertThat(row.methodId())
                        .as("arm C ran the promoted procedure, not the reference")
                        .startsWith("procedure.report.procedure."));
        List<Crj01ArmRunner.Row> armA = run.rows().stream()
                .filter(row -> row.arm().equals("A")).toList();
        assertThat(armA).allSatisfy(row ->
                assertThat(row.methodId()).isEqualTo("report.journey.reference.v1"));

        // ── the oracle scored every row: a row says GROUNDED or why not ──
        assertThat(run.rows()).allSatisfy(row -> {
            assertThat(row.oracleReason()).as("the row of %s carries the oracle's reason",
                    row.caseId()).isNotBlank();
            if (row.grounded()) {
                assertThat(row.oracleOutcome()).isIn("GROUNDED", "DISPOSITION_MATCHED");
            }
        });

        // ── the wall and the CPU time were MEASURED — the G5 metrics that were
        //    NOT_INSTRUMENTED at the baseline ──
        assertThat(run.rows()).allSatisfy(row -> {
            assertThat(row.wallNanos()).as("the wall time of %s was measured", row.caseId())
                    .isPositive();
            assertThat(row.cpuNanos()).as("the CPU time of %s was measured", row.caseId())
                    .isNotNegative();
        });

        // ── the evidence artefact ───────────────────────────────────────────────
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("run", "crj-01-arm-run");
        evidence.put("dataset", cases.name());
        evidence.put("datasetVersion", cases.version());
        evidence.put("domain", "A");
        evidence.put("arms", List.of("A", "B", "C"));
        evidence.put("budget", Map.of("maxWallClockMillis", BUDGET.maxWallClockMillis(),
                "maxCpuMillis", BUDGET.maxCpuMillis()));
        evidence.put("note", "a DEV run on the solver's own split: a DEBUGGING artefact, "
                + "never evidence of an arm's advantage");
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (Crj01ArmRunner.Row row : run.rows()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("caseId", row.caseId());
            entry.put("arm", row.arm());
            entry.put("variant", row.variant());
            entry.put("methodId", row.methodId());
            entry.put("disposition", row.disposition());
            entry.put("epistemicDisposition", row.epistemicDisposition());
            entry.put("premiseStatus", row.premiseStatus());
            entry.put("grounded", row.grounded());
            entry.put("oracleOutcome", row.oracleOutcome());
            entry.put("oracleReason", row.oracleReason());
            entry.put("citedClaimIds", row.citedClaimIds());
            entry.put("citations", row.citations());
            entry.put("goldPropositionsMatched", row.goldPropositionsMatched());
            entry.put("wallNanos", row.wallNanos());
            entry.put("cpuNanos", row.cpuNanos());
            entry.put("inapplicable", row.inapplicable());
            entry.put("effectVerified", row.effectVerified());
            entry.put("artifactSha256", row.artifactSha256());
            entry.put("traceRef", row.traceRef());
            entry.put("conflictCount", row.conflictCount());
            entry.put("reason", row.reason());
            entry.put("error", row.error());
            rows.add(entry);
        }
        evidence.put("rows", rows);
        evidence.put("summary", run.summary());

        String json = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .writerWithDefaultPrettyPrinter().writeValueAsString(evidence);
        Path artefact = RepositoryPaths.root().resolve("docs-v2/engineering")
                .resolve("CRJ-01-EVIDENCE.json");
        Files.writeString(artefact, json, StandardCharsets.UTF_8);
        assertThat(Files.readString(artefact, StandardCharsets.UTF_8)).isEqualTo(json);
        System.out.println("[CRJ-01] the evidence " + artefact + " (" + rows.size() + " rows)");
        for (Map.Entry<String, Map<String, Object>> entry : run.summary().entrySet()) {
            System.out.println("[CRJ-01] " + entry.getKey() + " " + entry.getValue());
        }
    }
}
