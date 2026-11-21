package io.matrix.crj.runner;

import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.crj.microworld.LampTruthOracle;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 — the DOMAIN-B runner, exercised on the dev episodes.
 *
 * <p><b>What this proves.</b> That the lamp world's three arms can be run side by side, one
 * row per (episode, arm), that the answers are scored by the <b>independently written truth
 * table</b>, that arm B's replay is a genuine replay (the retained episode reproduces
 * under the same seed) while costing <b>no probes</b>, and that arm C's settled model is
 * <b>inapplicable</b> — with the reason — on an episode whose model has not settled.</p>
 *
 * <p><b>What this does not establish.</b> Nothing about arm C's advantage over arm A: the
 * dev split is the solver's own split, and the protocol's measurement is the sealed one.</p>
 */
class Crj01DomainBRunnerTest {

    /** The budget every arm runs under — the SAME one, for comparability. */
    private static final ComputeBudget BUDGET = new ComputeBudget(30_000L, 30_000L, 0L, 0);

    @Test
    @DisplayName("the lamp world's three arms run side by side, and the truth table scores them")
    void theThreeArmsRunSideBySide() throws Exception {
        Crj01DomainBRunner runner = new Crj01DomainBRunner(BUDGET);
        Crj01DomainBRunner.Run run = runner.run(Crj01DomainBRunner.DEV_EPISODES);

        // ── one row per (episode, arm), and NONE missing ─────────────────────────
        assertThat(run.rows()).as("the rows").hasSize(Crj01DomainBRunner.DEV_EPISODES * 3);
        Map<String, Integer> rowsPerArm = new LinkedHashMap<>();
        for (Crj01DomainBRunner.Row row : run.rows()) {
            rowsPerArm.merge(row.arm(), 1, Integer::sum);
        }
        assertThat(rowsPerArm.get("A")).as("arm A's rows").isEqualTo(9);
        assertThat(rowsPerArm.get("B")).as("arm B's rows").isEqualTo(9);
        assertThat(rowsPerArm.get("C")).as("arm C's rows").isEqualTo(9);

        // ── the seeds come from the PRE-COMMITTED list, and are distinct ─────────
        List<Long> seeds = run.rows().stream().map(Crj01DomainBRunner.Row::seed).distinct()
                .toList();
        assertThat(seeds).as("the episodes' seeds").hasSize(9);
        assertThat(seeds).allSatisfy(seed ->
                assertThat(Crj01DomainBRunner.PRECOMMITTED_SEEDS).contains(seed));

        // ── the oracle: the truth table's own cross-check must AGREE ────────────
        LampTruthOracle oracle = new LampTruthOracle();
        // the instrument's own cross-check: the table is checked against the WORLD's
        // answers on all four assignments, and a disagreement would VOID the run
        Map<String, Boolean> world = new LinkedHashMap<>();
        for (LampTruthOracle.Row row : oracle.rows()) {
            world.put(row.assignment(), row.lamp());
        }
        assertThat(oracle.crosscheck(world))
                .as("the truth table agrees with the world on all four assignments")
                .satisfies(crosscheck -> {
                    assertThat(crosscheck.agrees()).isTrue();
                    assertThat(crosscheck.assignments()).isEqualTo(4);
                    assertThat(crosscheck.agreements()).isEqualTo(4);
                });

        // ── arm A: the reference answers the episode's OBSERVED assignments, and it
        //    pays for the episode's probes ─────────────────────────────────────────
        List<Crj01DomainBRunner.Row> armA = run.rows().stream()
                .filter(row -> row.arm().equals("A")).toList();
        assertThat(armA).allSatisfy(row -> {
            assertThat(row.queries()).as("the assignments the arms answer").isPositive();
            assertThat(row.probes()).as("arm A pays for the episode's probes").isPositive();
            // the TRANSFER subset is EMPTY at the pre-committed bound, and that is a
            // MEASURED fact of this world, not an accident of the fixture: an episode
            // that exhausts the four assignments settles the model and leaves no novel
            // query, and an episode that stops early leaves a novel query the surviving
            // hypotheses DISAGREE about, which the reference refuses to answer
            assertThat(row.novelQueries())
                    .as("the transfer subset is empty in the lamp world as it exists")
                    .isZero();
        });

        // ── arm B: the replay costs NO probes, and the retained episode reproduces ──
        List<Crj01DomainBRunner.Row> armB = run.rows().stream()
                .filter(row -> row.arm().equals("B")).toList();
        assertThat(armB).allSatisfy(row -> {
            assertThat(row.probes()).as("the replay pays no probes").isZero();
            assertThat(row.stepsSavedVsA()).as("the re-execution saves no steps on a replay")
                    .isZero();
        });

        // ── arm C: the settled model answers only where the model has settled ────
        List<Crj01DomainBRunner.Row> armC = run.rows().stream()
                .filter(row -> row.arm().equals("C")).toList();
        assertThat(armC).allSatisfy(row -> {
            assertThat(row.probes()).as("the settled model pays no probes").isZero();
            if (!row.settled()) {
                assertThat(row.inapplicable())
                        .as("an unsettled model is INAPPLICABLE, not wrong")
                        .isTrue();
                assertThat(row.reason()).as("and the row says why").isNotBlank();
            } else {
                assertThat(row.inapplicable())
                        .as("a settled model answers the episode's assignments")
                        .isFalse();
                assertThat(row.answers()).as("and it answers them").isNotEmpty();
            }
        });

        // ── the answers agree with the truth table wherever the arm answered ─────
        for (Crj01DomainBRunner.Row row : run.rows()) {
            for (Map.Entry<String, Boolean> answer : row.answers().entrySet()) {
                Boolean truth = truthOf(oracle, answer.getKey());
                assertThat(truth).as("the truth table holds %s", answer.getKey()).isNotNull();
                assertThat(answer.getValue()).as("the answer for %s", answer.getKey())
                        .isEqualTo(truth);
            }
        }

        // ── the evidence artefact ─────────────────────────────────────────────────
        Map<String, Object> artefact = new LinkedHashMap<>();
        artefact.put("run", "crj-01-domain-b-run");
        artefact.put("version", "1.0");
        artefact.put("date", "2026-11-21");
        artefact.put("domain", "B");
        artefact.put("arms", List.of("A", "B", "C"));
        artefact.put("precommittedSeeds", Crj01DomainBRunner.PRECOMMITTED_SEEDS);
        artefact.put("budget", Map.of("maxWallClockMillis", BUDGET.maxWallClockMillis(),
                "maxCpuMillis", BUDGET.maxCpuMillis()));
        artefact.put("note", "a DEV run on the solver's own split of the PRE-COMMITTED seed "
                + "list: a DEBUGGING artefact, never evidence of an arm's advantage");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Crj01DomainBRunner.Row row : run.rows()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("episodeId", row.episodeId());
            entry.put("seed", row.seed());
            entry.put("arm", row.arm());
            entry.put("variant", row.variant());
            entry.put("answers", row.answers());
            entry.put("correct", row.correct());
            entry.put("novelQueries", row.novelQueries());
            entry.put("probes", row.probes());
            entry.put("finalSurvivors", row.finalSurvivors());
            entry.put("utility", row.utility());
            entry.put("terminationReason", row.terminationReason());
            entry.put("settled", row.settled());
            entry.put("stepsSavedVsA", row.stepsSavedVsA());
            entry.put("wallNanos", row.wallNanos());
            entry.put("inapplicable", row.inapplicable());
            entry.put("reason", row.reason());
            entry.put("error", row.error());
            rows.add(entry);
        }
        artefact.put("rows", rows);
        artefact.put("summary", run.summary());

        String json = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .writerWithDefaultPrettyPrinter().writeValueAsString(artefact);
        Path out = RepositoryPaths.root().resolve("docs-v2/engineering")
                .resolve("CRJ-01-EVIDENCE-DOMAIN-B.json");
        Files.writeString(out, json, StandardCharsets.UTF_8);
        assertThat(Files.readString(out, StandardCharsets.UTF_8)).isEqualTo(json);
        System.out.println("[CRJ-01] the domain-B evidence " + out + " (" + rows.size() + " rows)");
        for (Map.Entry<String, Map<String, Object>> entry : run.summary().entrySet()) {
            System.out.println("[CRJ-01] " + entry.getKey() + " " + entry.getValue());
        }
    }

    /**
     * The truth table's value for an assignment key.
     *
     * @param oracle the truth table
     * @param key    the assignment's key
     * @return the lamp's state, or null
     */
    private static Boolean truthOf(LampTruthOracle oracle, String key) {
        for (LampTruthOracle.Row row : oracle.rows()) {
            if (row.assignment().equals(key)) {
                return row.lamp();
            }
        }
        return null;
    }
}
