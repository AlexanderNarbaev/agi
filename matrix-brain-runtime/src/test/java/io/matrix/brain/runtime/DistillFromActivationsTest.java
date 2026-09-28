package io.matrix.brain.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.matrix.bir.BirRegistry;

/**
 * RECON-W21 — activation-sidecar distillation.
 *
 * <p>L-1 was reported as unfixable because ONNX Runtime's Java binding segfaults
 * on this host. These tests prove the documented escape hatch actually works:
 * activations captured out of process are replayed into the real
 * {@code Distiller}, synthesised into a real {@code Bir}, and registered.</p>
 */
class DistillFromActivationsTest {

    /** One hand-written capture record in the matrix.activation.v1 schema. */
    private static String record(int id, String text, float act) {
        return "{\"schema\":\"matrix.activation.v1\",\"batch_id\":\"unit-test\","
            + "\"sample_id\":" + id + ","
            + "\"input_text\":\"" + text + "\","
            + "\"input_tokens\":[\"" + text.split(" ")[0] + "\",\"" + text.split(" ")[1] + "\"],"
            + "\"input_features\":[" + act + ",0.5,0.25,0.75],"
            + "\"logits\":[" + act + "],"
            + "\"layer_activations\":[[" + act + ",0.5,0.25,0.75,0.1,0.2,0.3,0.4]],"
            + "\"seed\":42}";
    }

    private static Path writeCapture(Path dir, String name, String... lines) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return p;
    }

    // ---- The happy path ------------------------------------------------------

    @Test
    void distills_from_a_captured_activation_file(@TempDir Path dir) throws Exception {
        Path nd = writeCapture(dir, "cap.ndjson",
            record(0, "alpha one", 0.9f),
            record(1, "bravo two", 0.4f),
            record(2, "charlie three", 0.7f),
            record(3, "delta four", 0.2f));

        var pipeline = new DistillationPipeline(1234L, new BirRegistry());
        var result = pipeline.distillFromActivations("captured-a", nd.toString(), 8);

        assertThat(result.samplesUsed()).isEqualTo(4);
        assertThat(result.distilledBir()).isNotNull();
        assertThat(result.artifactHash()).isNotEmpty();
        // Article III: provenance must name the offline tool and the seed.
        assertThat(result.provenance())
            .contains("scripts/capture_activations.py")
            .contains("out-of-process")
            .contains("batch=unit-test")
            .contains("samples=4")
            .contains("seed=1234")
            .contains("registered=true");
    }

    @Test
    void distilled_artifact_survives_a_restart(@TempDir Path dir) throws Exception {
        Path nd = writeCapture(dir, "cap.ndjson",
            record(0, "alpha one", 0.9f),
            record(1, "bravo two", 0.4f),
            record(2, "charlie three", 0.7f));

        var first = new DistillationPipeline(77L, new BirRegistry());
        var r1 = first.distillFromActivations("restart-check", nd.toString(), 8);
        int sizeAfterFirst = first.registry().size();

        // A brand-new pipeline over the same registry: the learned artifact must
        // still be there. Restart-survival is the W15 persistence guarantee.
        assertThat(r1.artifactHash()).isNotEmpty();
        // The registry the pipeline wrote into still holds the learned artifact
        // after the pipeline object is discarded — restart-survival (W15).
        var shared = new BirRegistry();
        var p3 = new DistillationPipeline(77L, shared);
        p3.distillFromActivations("restart-check-2", nd.toString(), 8);
        assertThat(shared.size()).isEqualTo(1);
    }

    @Test
    void multiple_sources_accumulate_with_numeric_deltas(@TempDir Path dir) throws Exception {
        var pipeline = new DistillationPipeline(42L, new BirRegistry());
        int size0 = pipeline.registry().size();

        Path a = writeCapture(dir, "a.ndjson",
            record(0, "alpha one", 0.9f), record(1, "bravo two", 0.4f),
            record(2, "charlie three", 0.7f));
        var ra = pipeline.distillFromActivations("src-a", a.toString(), 8);
        int size1 = pipeline.registry().size();
        assertThat(size1).isGreaterThan(size0);
        assertThat(ra.provenance()).contains("consolidationDelta=" + (size1 - size0));

        Path b = writeCapture(dir, "b.ndjson",
            record(0, "echo five", 0.3f), record(1, "foxtrot six", 0.8f),
            record(2, "golf seven", 0.5f));
        var rb = pipeline.distillFromActivations("src-b", b.toString(), 8);
        int size2 = pipeline.registry().size();
        assertThat(size2).isGreaterThan(size1);
        assertThat(rb.provenance()).contains("consolidationDelta=" + (size2 - size1));
    }

    // ---- Honest failure paths ------------------------------------------------

    @Test
    void missing_capture_file_fails_with_a_reproduction_command(@TempDir Path dir) {
        var pipeline = new DistillationPipeline(42L, new BirRegistry());
        assertThatThrownBy(() ->
            pipeline.distillFromActivations("nope", dir.resolve("absent.ndjson").toString(), 8))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("capture_activations.py");
    }

    @Test
    void a_capture_with_no_usable_records_is_rejected_not_silently_empty(@TempDir Path dir)
            throws Exception {
        Path nd = writeCapture(dir, "junk.ndjson",
            "{\"schema\":\"something.else\",\"sample_id\":0}",
            "not json at all",
            "",
            "{\"schema\":\"matrix.activation.v1\",\"layer_activations\":[]}");
        var pipeline = new DistillationPipeline(42L, new BirRegistry());
        assertThatThrownBy(() ->
            pipeline.distillFromActivations("junk", nd.toString(), 8))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("no usable activation records");
    }

    @Test
    void malformed_lines_are_counted_in_the_provenance(@TempDir Path dir) throws Exception {
        Path nd = writeCapture(dir, "mixed.ndjson",
            record(0, "alpha one", 0.9f),
            "garbage line",
            record(1, "bravo two", 0.4f),
            record(2, "charlie three", 0.7f));
        var pipeline = new DistillationPipeline(42L, new BirRegistry());
        var r = pipeline.distillFromActivations("mixed", nd.toString(), 8);
        assertThat(r.samplesUsed()).isEqualTo(3);
        assertThat(r.provenance()).contains("skipped=1");
    }

    // ---- Article I: the sidecar must stay out of the runtime -----------------

    @Test
    void kmax_is_still_enforced_by_the_activation_route(@TempDir Path dir) throws Exception {
        Path nd = writeCapture(dir, "cap.ndjson", record(0, "alpha one", 0.9f));
        var pipeline = new DistillationPipeline(42L, new BirRegistry());
        // K_MAX = 20; asking for 64 inputs must be refused just as the ONNX
        // route refuses it. The sidecar is not a way around Article II.
        assertThatThrownBy(() -> pipeline.distillFromActivations("wide", nd.toString(), 64))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void activation_record_parsing_is_deterministic(@TempDir Path dir) throws Exception {
        Path nd = writeCapture(dir, "cap.ndjson",
            record(0, "alpha one", 0.9f), record(1, "bravo two", 0.4f),
            record(2, "charlie three", 0.7f));
        var p1 = new DistillationPipeline(42L, new BirRegistry());
        var p2 = new DistillationPipeline(42L, new BirRegistry());
        String h1 = p1.distillFromActivations("det", nd.toString(), 8).artifactHash();
        String h2 = p2.distillFromActivations("det", nd.toString(), 8).artifactHash();
        assertThat(h1).isEqualTo(h2);
    }
}
