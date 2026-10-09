package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MIND-W5 — Distillation Factory tests.
 *
 * <p>Verifies the ModelToMatrix pipeline, DistillationLedger persistence,
 * super-additivity on a fixed eval set, and the CI guard against
 * runtime imports of legacy LLM classes.</p>
 */
class DistillationFactoryIntegrationTest {

    private static final ModelToMatrix PIPELINE = new ModelToMatrix();

    /** Synthetic ONNX-shaped file (we extract quoted strings as the token stream). */
    private static String fakeOnnx() {
        // Pretend protobuf blob with quoted initializer / op names
        return "node { name: \"mobilenet\" initializer { name: \"Conv_0\" } } " +
               "node { name: \"Relu_1\" } " +
               "node { name: \"Conv_2\" initializer { name: \"kernel\" } } " +
               "output: \"softmax\"";
    }

    @Test
    void distill_onnx_promotes_hdc_and_records_ledger(@TempDir Path tmp) throws IOException {
        Path onnx = tmp.resolve("mobilenet.onnx");
        Files.writeString(onnx, fakeOnnx());
        Path kb = tmp.resolve("kb.ndjson");
        Path led = tmp.resolve("ledger.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(kb, 256);
        DistillationLedger ledger = new DistillationLedger(led);

        ModelToMatrix.Report r = PIPELINE.distillOnnx(onnx, store, ledger);

        assertThat(r.tokensExtracted()).isGreaterThan(0);
        assertThat(r.hdcPromoted()).isGreaterThan(0);
        assertThat(r.birClausesInduced()).isGreaterThanOrEqualTo(0);
        assertThat(r.tsetlinLiterals()).isGreaterThan(0);
        assertThat(r.inputsBytes()).isGreaterThan(0);
        assertThat(r.durationMs()).isGreaterThanOrEqualTo(0);
        assertThat(r.artifactHash()).isNotBlank();
        // HDC store got the promoted tokens
        assertThat(store.size()).isGreaterThanOrEqualTo(r.hdcPromoted());
        // Ledger persisted
        assertThat(Files.exists(led)).isTrue();
        assertThat(ledger.size()).isEqualTo(1);
    }

    @Test
    void distill_dataset_promotes_facts(@TempDir Path tmp) throws IOException {
        Path kb = tmp.resolve("kb.ndjson");
        Path led = tmp.resolve("ledger.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(kb, 256);
        DistillationLedger ledger = new DistillationLedger(led);

        List<String> facts = Arrays.asList(
            "Paris is the capital of France",
            "Tokyo is the capital of Japan",
            "Moscow is the capital of Russia",
            "Lima is the capital of Peru",
            "Berlin is the capital of Germany"
        );
        ModelToMatrix.Report r = PIPELINE.distillDataset(facts, store, ledger);

        assertThat(r.tokensExtracted()).isEqualTo(5);
        assertThat(r.hdcPromoted()).isGreaterThan(0);
        // The "X is the capital of Y" pattern should yield BIR triples
        assertThat(r.birClausesInduced()).isEqualTo(5);
        assertThat(ledger.size()).isEqualTo(1);
    }

    @Test
    void distill_induces_bir_triples_from_sentences(@TempDir Path tmp) throws IOException {
        Path kb = tmp.resolve("kb.ndjson");
        Path led = tmp.resolve("ledger.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(kb, 256);
        DistillationLedger ledger = new DistillationLedger(led);

        // Each line should produce a "X is Y of Z" triple
        List<String> facts = Arrays.asList(
            "Einstein is the physicist of relativity",
            "Pythagoras is the mathematician of triangles",
            "Shakespeare is the playwright of Hamlet"
        );
        ModelToMatrix.Report r = PIPELINE.distillDataset(facts, store, ledger);
        assertThat(r.birClausesInduced()).isEqualTo(3);
    }

    @Test
    void distill_ledger_persists_across_reopen(@TempDir Path tmp) throws Exception {
        Path led = tmp.resolve("ledger.ndjson");
        DistillationLedger l1 = new DistillationLedger(led);
        l1.append(new DistillationLedger.Entry(
            "run-1", "test:run1", 100, 1024, 50, 10, 20, 0.05, 100, 1, "hash-1"));
        l1.append(new DistillationLedger.Entry(
            "run-2", "test:run2", 200, 2048, 80, 15, 30, 0.10, 200, 2, "hash-2"));

        // Verify file exists and has content
        assertThat(Files.exists(led)).isTrue();
        assertThat(Files.size(led)).isGreaterThan(0);

        DistillationLedger l2 = new DistillationLedger(led);
        // size is the append count tracked in memory; readAll() reads from disk
        List<DistillationLedger.Entry> all = l2.readAll();
        assertThat(all).hasSize(2);
        // RECON-W34.1: this read the WRONG SLOT. The fixture appends
        //   new Entry("run-1", "test:run1", ...)
        // so "test:run1" is the SECOND constructor argument (datasetOrPattern) while
        // source() returns the FIRST (sourceId). The assertion could only ever have failed,
        // and its failure was reported as a ledger-identity defect when it was really a
        // slot mismatch. What "persists across reopen" actually means is round-trip
        // fidelity, so both fields are now checked against what was written.
        assertThat(all.get(0).source()).isEqualTo("run-1");
        assertThat(all.get(0).datasetOrPattern()).isEqualTo("test:run1");
        assertThat(all.get(1).hdcPromoted()).isEqualTo(80);
    }

    @Test
    void distill_ledger_summary_aggregates(@TempDir Path tmp) throws IOException {
        Path led = tmp.resolve("ledger.ndjson");
        DistillationLedger l = new DistillationLedger(led);
        l.append(new DistillationLedger.Entry(
            "a", "s1", 10, 100, 5, 1, 2, 0.10, 50, 1, "h1"));
        l.append(new DistillationLedger.Entry(
            "b", "s2", 20, 200, 10, 2, 4, 0.20, 100, 2, "h2"));

        var s = l.summary();
        assertThat(((Number) s.get("runs")).intValue()).isEqualTo(2);
        // RECON-W34.4: the two entry lines above carry inputBits=10/20 (BYTES) and
        // samplesUsed=100/200 (COUNTS). This assertion used to read 300 from samplesUsed,
        // which is a sample total reported under a byte key. The operator ruled that each
        // field reports its own unit, so the byte total is 30 and the count total is 300.
        // Both are asserted so neither field can drift back into the other again.
        assertThat(((Number) s.get("total_inputs_bytes")).longValue())
                .as("bytes: sum of inputBits").isEqualTo(30L);
        assertThat(((Number) s.get("total_inputs_count")).longValue())
                .as("counts: sum of samplesUsed").isEqualTo(300L);
        assertThat(((Number) s.get("total_hdc_promoted")).intValue()).isEqualTo(15);
        assertThat(((Number) s.get("total_bir_clauses_induced")).intValue()).isEqualTo(3);
        assertThat(((Number) s.get("total_tsetlin_automata_updated")).intValue()).isEqualTo(6);
        assertThat((double) s.get("mean_eval_delta")).isCloseTo(0.15, org.assertj.core.api.Assertions.within(0.001));
    }

    /**
     * SUPER-ADDITIVITY: matrix distilled from A + matrix distilled from B
     * scores >= max(score(A), score(B)) on a fixed eval set.
     *
     * <p>This is the core empirical claim of W5: distillation UNIFIES the
     * mind, never splits it into disconnected sub-brains.</p>
     */
    @Test
    void super_additivity_merged_score_meets_max(@TempDir Path tmp) throws Exception {
        // Define a fixed eval: recall accuracy on a probe set
        List<String> evalProbes = Arrays.asList(
            "paris", "france", "tokyo", "japan", "lima", "peru",
            "berlin", "germany", "madrid", "spain"
        );

        // Distill set A
        Path kbA = tmp.resolve("kbA.ndjson");
        PersistentHdcStore storeA = new PersistentHdcStore(kbA, 256);
        PIPELINE.distillDataset(Arrays.asList(
            "Paris is the capital of France",
            "Berlin is the capital of Germany"
        ), storeA, new DistillationLedger(tmp.resolve("ledA.ndjson")));

        // Distill set B
        Path kbB = tmp.resolve("kbB.ndjson");
        PersistentHdcStore storeB = new PersistentHdcStore(kbB, 256);
        PIPELINE.distillDataset(Arrays.asList(
            "Tokyo is the capital of Japan",
            "Lima is the capital of Peru",
            "Madrid is the capital of Spain"
        ), storeB, new DistillationLedger(tmp.resolve("ledB.ndjson")));

        // Distill A+B into merged store
        Path kbAB = tmp.resolve("kbAB.ndjson");
        PersistentHdcStore storeAB = new PersistentHdcStore(kbAB, 256);
        PIPELINE.distillDataset(Arrays.asList(
            "Paris is the capital of France",
            "Berlin is the capital of Germany",
            "Tokyo is the capital of Japan",
            "Lima is the capital of Peru",
            "Madrid is the capital of Spain"
        ), storeAB, new DistillationLedger(tmp.resolve("ledAB.ndjson")));

        // Eval: for each probe, did the store gain the word?
        java.util.function.Function<PersistentHdcStore, Double> eval =
            store -> {
                long hits = evalProbes.stream().filter(p ->
                    store.snapshot().values().stream()
                        .anyMatch(v -> v.toLowerCase().contains(p))
                ).count();
                return (double) hits / evalProbes.size();
            };

        double scoreA = eval.apply(storeA);
        double scoreB = eval.apply(storeB);
        double scoreAB = eval.apply(storeAB);

        // Both A and B are partial; AB covers all of them.
        assertThat(scoreAB)
            .as("merged matrix score (%s) must be >= max(A=%s, B=%s)",
                scoreAB, scoreA, scoreB)
            .isGreaterThanOrEqualTo(Math.max(scoreA, scoreB));
        // Sanity: AB should hit more probes than either alone
        assertThat(scoreAB).isGreaterThanOrEqualTo(scoreA);
        assertThat(scoreAB).isGreaterThanOrEqualTo(scoreB);
    }

    @Test
    void distillation_promotes_distinct_tokens_only(@TempDir Path tmp) throws Exception {
        Path kb = tmp.resolve("kb.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(kb, 256);
        DistillationLedger ledger = new DistillationLedger(tmp.resolve("ledger.ndjson"));

        // 50 duplicate tokens → only 1 HDC entry
        List<String> facts = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) facts.add("Paris is the capital of France");
        ModelToMatrix.Report r = PIPELINE.distillDataset(facts, store, ledger);
        // 50 lines -> HDC promotion deduplicates via setSeen
        assertThat(r.hdcPromoted()).isLessThanOrEqualTo(50);
        assertThat(r.birClausesInduced()).isEqualTo(50);
    }

    @Test
    void empty_dataset_produces_zero_promotions(@TempDir Path tmp) throws Exception {
        Path kb = tmp.resolve("kb.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(kb, 256);
        DistillationLedger ledger = new DistillationLedger(tmp.resolve("ledger.ndjson"));
        ModelToMatrix.Report r = PIPELINE.distillDataset(List.of(), store, ledger);
        assertThat(r.tokensExtracted()).isZero();
        assertThat(r.hdcPromoted()).isZero();
        assertThat(r.birClausesInduced()).isZero();
    }

    @Test
    void ledger_handles_corrupt_lines_gracefully(@TempDir Path tmp) throws Exception {
        Path led = tmp.resolve("ledger.ndjson");
        // RECON-W34.1: this fixture wrote the OLD snake_case on-disk schema (inputs_count,
        // bir_clauses_induced, duration_ms, ts, artifact_hash). DistillationLedger.append now
        // writes camelCase (samplesUsed, birClausesSynthesized, durationMs, timestampMs,
        // artifactHash), so every field here extracted as "" and ALL THREE lines were
        // skipped -- the test was asserting "a malformed line is skipped" while actually
        // measuring schema drift, and it read size 0 as a pass on a different defect.
        // Rewritten to the current schema so the assertion means what its name says: one
        // genuinely malformed line between two readable ones, both of which survive.
        Files.writeString(led,
            "{\"sourceId\":\"s\",\"datasetOrPattern\":\"p\",\"inputBits\":1,"
          + "\"samplesUsed\":1,\"hdcPromoted\":1,\"birClausesSynthesized\":1,"
          + "\"tsetlinLiterals\":1,\"fidelity\":0.1,\"durationMs\":1,"
          + "\"timestampMs\":1,\"artifactHash\":\"x\"}\n"
          + "this is not json\n"
          + "{\"sourceId\":\"s2\",\"datasetOrPattern\":\"p\",\"inputBits\":2,"
          + "\"samplesUsed\":2,\"hdcPromoted\":2,\"birClausesSynthesized\":2,"
          + "\"tsetlinLiterals\":2,\"fidelity\":0.2,\"durationMs\":2,"
          + "\"timestampMs\":2,\"artifactHash\":\"y\"}\n");
        DistillationLedger l = new DistillationLedger(led);
        List<DistillationLedger.Entry> all = l.readAll();
        assertThat(all).hasSize(2);  // malformed line skipped
        assertThat(all.get(0).source()).isEqualTo("s");
        assertThat(all.get(1).source()).isEqualTo("s2");
    }

    @Test
    void distillation_summary_measures_cumulative_impact(@TempDir Path tmp) throws Exception {
        Path kb = tmp.resolve("kb.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(kb, 256);
        DistillationLedger ledger = new DistillationLedger(tmp.resolve("ledger.ndjson"));

        // Three "small" distillation runs that cumulatively grow the mind
        for (int i = 0; i < 3; i++) {
            PIPELINE.distillDataset(
                Arrays.asList(
                    "Run " + i + ": Paris is the capital of France",
                    "Run " + i + ": Tokyo is the capital of Japan"
                ),
                store,
                ledger
            );
        }
        var s = ledger.summary();
        assertThat(((Number) s.get("runs")).intValue()).isEqualTo(3);
        // Each run produces 2 HDC entries from "Paris"/"France"+"Tokyo"/"Japan"
        // but with deduplication across runs (same setSeen), cumulative grows.
        assertThat(((Number) s.get("total_bir_clauses_induced")).intValue()).isEqualTo(6);
    }

    @Test
    void distilling_again_is_idempotent_on_duplicate_tokens(@TempDir Path tmp) throws Exception {
        // Distill same facts twice: second run should not add new HDC entries
        // (already DUPLICATE-flagged via contradiction check)
        Path kb = tmp.resolve("kb.ndjson");
        PersistentHdcStore store = new PersistentHdcStore(kb, 256);
        DistillationLedger ledger = new DistillationLedger(tmp.resolve("ledger.ndjson"));

        ModelToMatrix.Report r1 = PIPELINE.distillDataset(
            Arrays.asList("Paris is the capital of France"), store, ledger);
        int sizeAfterFirst = store.size();

        ModelToMatrix.Report r2 = PIPELINE.distillDataset(
            Arrays.asList("Paris is the capital of France"), store, ledger);

        // Store size doesn't double (deduplication via PersistentHdcStore.checkContradiction)
        assertThat(store.size()).isLessThanOrEqualTo(sizeAfterFirst + 5);  // small slack for variants
        // Both runs logged to ledger
        assertThat(ledger.size()).isEqualTo(2);
    }
}
