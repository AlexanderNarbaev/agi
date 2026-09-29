package io.matrix.brain.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.matrix.bir.BirRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W28 B-4 — artifact identity, and the second bug the first fix hid.
 *
 * <p><b>What this pins, and why each assertion exists.</b></p>
 *
 * <p><b>1. The hash must be over the learning, not the run.</b> The W21 fix
 * content-addressed the artifact by hashing arity, form kind and clause masks. Three
 * other entry points still hashed {@code Bir.toString()}, which embeds provenance, so
 * identical learning from differently-named sources produced different identities.
 * All four now use {@code contentHash}.</p>
 *
 * <p><b>2. The hash must actually include the learned structure.</b> {@code contentHash}
 * serialised {@code ClauseSetForm} clauses but had no branch for {@code TtForm}, so for
 * a truth-table artifact it hashed arity and form kind and nothing else. Every
 * single-output teacher therefore produced one constant hash — which is exactly why the
 * W27 report's {@code 623cb895} read like a fixed artifact identity, and why two
 * genuinely different captures were indistinguishable. This is the bug that made the
 * W21 "content-addressed" claim false for the path that most captures actually take.</p>
 *
 * <p><b>3. Measured input semantics.</b> {@code toBitVector} derives the bit vector
 * from {@code input_tokens}, falling back to treating the whole {@code input_text} as a
 * single token. It does <em>not</em> read {@code input_features}. Measured directly:
 * index 204 for "the quick brown fox", 48 for "a completely different sentence", 127
 * for "x", 36 for {@code input_tokens=[Alpha,Beta]}. So the input text is what moves
 * the artifact, and features are inert with respect to what is learned. An earlier
 * draft of this file asserted the opposite — that rewording must <em>not</em> change
 * the hash — which was a belief I had not checked; it only ever "passed" because the
 * hash was constant. That test is deleted rather than made to pass.</p>
 */
class DistillationArtifactIdentityTest {

    private static DistillationPipeline pipe(Path storage) throws Exception {
        Files.createDirectories(storage);
        return new DistillationPipeline(42L, new BirRegistry());
    }

    private static String captureLine(int sampleId, String text, float activation) {
        return "{\"schema\":\"matrix.activation.v1\","
            + "\"batch_id\":\"identity-test\",\"sample_id\":" + sampleId + ","
            + "\"input_text\":\"" + text + "\","
            + "\"input_features\":[0.5,0.25,0.75,0.5,0.25,0.75,0.5,0.25],"
            + "\"logits\":[" + activation + "],"
            + "\"layer_activations\":[[" + activation + "]],"
            + "\"seed\":42}";
    }

    @Test
    @DisplayName("B-4: distillFromOnnxTeacher fails loudly when the teacher model is absent")
    void onnxTeacherMissingModelFailsLoudly(@TempDir Path tmp) throws Exception {
        // One of the three entry points that had the wrong hash had no test at all,
        // which is how it stayed wrong through two review cycles. Its failure contract
        // is what matters here: the historic failure mode of this method was a segfault
        // deep in the ONNX Java binding, which told the caller nothing.
        DistillationPipeline p = pipe(tmp);
        Path missing = tmp.resolve("no-such-teacher.onnx");
        java.io.IOException ex = assertThrows(java.io.IOException.class,
            () -> p.distillFromOnnxTeacher("teacher", missing, 8, 4));
        assertTrue(ex.getMessage().contains("ONNX teacher not found"),
            "the message must say what is wrong, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("gen_teacher_onnx.py"),
            "the message must say how to fix it, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("B-4: distillFromOnnxTeacher rejects a non-positive sample count")
    void onnxTeacherRejectsBadSampleCount(@TempDir Path tmp) throws Exception {
        DistillationPipeline p = pipe(tmp);
        Path fake = tmp.resolve("teacher.onnx");
        Files.write(fake, new byte[]{0});
        assertThrows(IllegalArgumentException.class,
            () -> p.distillFromOnnxTeacher("teacher", fake, 8, 0),
            "sampleCount must be > 0; a zero-sample run is not a distillation");
    }

    @Test
    @DisplayName("B-4: provenance carries a teacherFingerprint of the capture bytes")
    void provenanceCarriesTeacherFingerprint(@TempDir Path tmp) throws Exception {
        DistillationPipeline p = pipe(tmp);
        Path nd = tmp.resolve("cap.ndjson");
        Files.write(nd, List.of(captureLine(0, "one", 0.9f), captureLine(1, "two", 0.1f)),
            StandardCharsets.UTF_8);

        DistillationPipeline.RunResult r = p.distillFromActivations("cap", nd, 8);
        assertTrue(r.provenance().contains("teacherFingerprint=sha256:"),
            "provenance must carry the capture fingerprint, got: " + r.provenance());
        assertTrue(r.provenance().contains("identity-test"),
            "provenance must name the capture batch, got: " + r.provenance());
    }

    @Test
    @DisplayName("B-4: the hash is over the learning, so the source label does not move it")
    void hashIsIndependentOfSourceLabel(@TempDir Path tmp) throws Exception {
        Path nd = tmp.resolve("cap2.ndjson");
        Files.write(nd, List.of(
            captureLine(0, "alpha", 0.9f), captureLine(1, "beta", 0.8f)), StandardCharsets.UTF_8);

        DistillationPipeline p1 = pipe(tmp);
        DistillationPipeline p2 = pipe(tmp.resolve("second"));
        assertEquals(p1.distillFromActivations("label-one", nd, 8).artifactHash(),
            p2.distillFromActivations("label-two", nd, 8).artifactHash(),
            "identical learning from differently-named sources is one artifact identity");
    }

    @Test
    @DisplayName("B-4: different inputs yield different artifact hashes (the regression that found the bug)")
    void differentInputsYieldDifferentHashes(@TempDir Path tmp) throws Exception {
        // Before the TtForm branch was added, BOTH of these returned 623cb895.
        DistillationPipeline p = pipe(tmp);
        Path a = tmp.resolve("a.ndjson");
        Path b = tmp.resolve("b.ndjson");
        Files.write(a, List.of(captureLine(0, "the quick brown fox", 0.9f)),
            StandardCharsets.UTF_8);
        Files.write(b, List.of(captureLine(0, "a completely different sentence", 0.9f)),
            StandardCharsets.UTF_8);
        assertNotEquals(p.distillFromActivations("a", a, 8).artifactHash(),
            p.distillFromActivations("b", b, 8).artifactHash(),
            "two different truth tables must not collapse onto one artifact identity");
    }

    @Test
    @DisplayName("B-4: a firing pattern that differs from another must hash differently")
    void differentFiringPatternsYieldDifferentHashes(@TempDir Path tmp) throws Exception {
        // Same text, so the same input bit vector, but a different OUTPUT pattern: one
        // capture fires and the other does not. If the table were still unserialised
        // these would collide too.
        DistillationPipeline p = pipe(tmp);
        Path fires = tmp.resolve("fires.ndjson");
        Path silent = tmp.resolve("silent.ndjson");
        Files.write(fires, List.of(captureLine(0, "same words here", 0.9f)),
            StandardCharsets.UTF_8);
        Files.write(silent, List.of(captureLine(0, "same words here", 0.1f)),
            StandardCharsets.UTF_8);
        assertNotEquals(p.distillFromActivations("fires", fires, 8).artifactHash(),
            p.distillFromActivations("silent", silent, 8).artifactHash(),
            "a firing teacher and a silent teacher must have different artifacts");
    }
}
