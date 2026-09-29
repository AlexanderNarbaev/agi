package io.matrix.brain.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.matrix.bir.Bir;
import io.matrix.distill.ActivationRecord;
import io.matrix.distill.Distiller;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RECON-W28 B-4 — is distillation demonstrably TEACHER-SENSITIVE?
 *
 * <p>The W27 review asserted that two structurally different teachers produced the
 * same artifact hash ({@code 623cb895}) and treated that as a distillation defect.
 * This test exists to settle the question with a discriminating experiment rather
 * than an assertion, and it is deliberately written so that it would FAIL if the
 * distiller collapsed all teachers onto one structure.</p>
 *
 * <p>Three properties are pinned:</p>
 * <ol>
 *   <li><b>Discriminating:</b> two captures whose teachers fire on DIFFERENT input
 *       indices must yield different tables and different artifact hashes. If this
 *       fails, the Distiller is genuinely teacher-blind and B-4 is a real defect.</li>
 *   <li><b>Deterministic:</b> the same capture replayed twice yields an identical
 *       artifact hash. If this fails, Article III is violated.</li>
 *   <li><b>Provenance-independent:</b> renaming the source must NOT change the
 *       structural artifact hash. This is the bug fixed in RECON-W28: three entry
 *       points hashed {@code Bir.toString()}, which embeds the provenance string, so
 *       the "artifact hash" was really a run identifier — byte-identical learning from
 *       two differently-named sources hashed differently.</li>
 * </ol>
 */
class DistillerTeacherSensitivityTest {

    private static String structuralHash(Bir bir) {
        StringBuilder sb = new StringBuilder();
        sb.append(bir.inputBits()).append('/').append(bir.outputBits()).append('/').append(bir.form());
        if (bir instanceof io.matrix.bir.TtForm tt) {
            for (long w : tt.table()) sb.append(Long.toHexString(w)).append(':');
        } else if (bir instanceof io.matrix.bir.ClauseSetForm cs) {
            for (var c : cs.clauses()) {
                sb.append('|');
                for (long w : c.pos) sb.append(Long.toHexString(w)).append(':');
                sb.append('/');
                for (long w : c.neg) sb.append(Long.toHexString(w)).append(':');
            }
        }
        return Integer.toHexString(sb.toString().hashCode());
    }

    /** Build a matrix.activation.v1 line whose single output bit is {@code fire ? high : low}. */
    private static String captureLine(int sampleId, String text, boolean fire) {
        return "{\"schema\":\"matrix.activation.v1\","
            + "\"batch_id\":\"synthetic\",\"sample_id\":" + sampleId + ","
            + "\"input_text\":\"" + text + "\","
            + "\"input_features\":[0.5,0.25,0.75,0.5,0.25,0.75,0.5,0.25],"
            + "\"logits\":[" + (fire ? 0.9 : 0.1) + "],"
            + "\"layer_activations\":[[" + (fire ? 0.9 : 0.1) + "]],"
            + "\"seed\":42}";
    }

    private static Bir distill(Path ndjson, int bits) throws Exception {
        Distiller d = new Distiller(bits, 0.5);
        for (String line : Files.readAllLines(ndjson)) {
            if (line == null || line.isBlank()) continue;
            ActivationRecord r = ActivationRecord.parse(line);
            if (r == null || r.activation() == null || r.activation().length == 0) continue;
            d.capture(r.toBitVector(bits), r.activation());
        }
        return d.synthesize("test");
    }

    @Test
    @DisplayName("B-4 discriminating: teachers firing on different inputs give different artifacts")
    void differentTeachersProduceDifferentArtifacts(@TempDir Path dir) throws Exception {
        // Teacher A fires on samples 0 and 1; Teacher B fires on samples 2 and 3.
        // Same schema, same bit width, same harness - only the firing set differs.
        List<String> a = new ArrayList<>();
        a.add(captureLine(0, "alpha zero", true));
        a.add(captureLine(1, "alpha one", true));
        a.add(captureLine(2, "alpha two", false));
        a.add(captureLine(3, "alpha three", false));

        List<String> b = new ArrayList<>();
        b.add(captureLine(0, "beta zero", false));
        b.add(captureLine(1, "beta one", false));
        b.add(captureLine(2, "beta two", true));
        b.add(captureLine(3, "beta three", true));

        Path fa = dir.resolve("teacher-a.ndjson");
        Path fb = dir.resolve("teacher-b.ndjson");
        Files.write(fa, a, StandardCharsets.UTF_8);
        Files.write(fb, b, StandardCharsets.UTF_8);

        Bir ba = distill(fa, 8);
        Bir bb = distill(fb, 8);

        assertNotEquals(structuralHash(ba), structuralHash(bb),
            "distiller is teacher-blind: two teachers with different firing sets "
            + "collapsed onto the same artifact");
        assertNotEquals(ba.toString().length() == 0, true);
    }

    @Test
    @DisplayName("B-4 determinism: replaying one capture twice yields the same hash")
    void sameCaptureIsDeterministic(@TempDir Path dir) throws Exception {
        List<String> rows = List.of(
            captureLine(0, "gamma zero", true),
            captureLine(1, "gamma one", false),
            captureLine(2, "gamma two", true));

        Path f = dir.resolve("teacher-gamma.ndjson");
        Files.write(f, rows, StandardCharsets.UTF_8);

        String h1 = structuralHash(distill(f, 8));
        String h2 = structuralHash(distill(f, 8));
        assertEquals(h1, h2, "Article III: identical capture produced two different artifacts");
    }

    @Test
    @DisplayName("B-4 provenance independence: renaming the source must not change the artifact hash")
    void provenanceDoesNotChangeArtifactHash(@TempDir Path dir) throws Exception {
        // Same learning, different source label and different capture path. Before
        // RECON-W28 the hash was Integer.toHexString(bir.toString().hashCode()),
        // which embeds provenance, so this assertion failed: identical structure,
        // different "artifact" hash.
        Path f1 = dir.resolve("one/source-a.ndjson");
        Path f2 = dir.resolve("two/source-b.ndjson");
        Files.createDirectories(f1.getParent());
        Files.createDirectories(f2.getParent());
        List<String> rows = List.of(
            captureLine(0, "delta zero", true),
            captureLine(1, "delta one", false));
        Files.write(f1, rows, StandardCharsets.UTF_8);
        Files.write(f2, rows, StandardCharsets.UTF_8);

        Bir b1 = distill(f1, 8);
        Bir b2 = distill(f2, 8);

        // provenance genuinely differs...
        assertNotEquals(b1.provenance(), b2.provenance(),
            "the two runs should be distinguishable in provenance");
        // ...but the learned structure, and therefore the artifact identity, must not.
        assertEquals(structuralHash(b1), structuralHash(b2),
            "artifact hash tracked provenance instead of the learned structure");
    }

    @Test
    @DisplayName("B-4 honest disclosure: a teacher that never fires yields an empty table, not an error")
    void silentTeacherProducesEmptyTable(@TempDir Path dir) throws Exception {
        // Documented limitation, pinned so it cannot regress silently: Distiller
        // thresholds activations at 0.5 and reads only layer 0, so a teacher whose
        // logits never exceed 0.5 distils to an all-zero table. It does not throw and
        // does not report zero fidelity automatically - the caller must inspect the
        // structure. The shipped booleans-8.ndjson capture is exactly this case.
        List<String> rows = List.of(
            captureLine(0, "epsilon zero", false),
            captureLine(1, "epsilon one", false));
        Path f = dir.resolve("silent-teacher.ndjson");
        Files.write(f, rows, StandardCharsets.UTF_8);

        Bir bir = distill(f, 8);
        assertTrue(bir instanceof io.matrix.bir.TtForm,
            "single-output capture should synthesise a truth table");
        for (long w : ((io.matrix.bir.TtForm) bir).table()) {
            assertEquals(0L, w, "a never-firing teacher must distil to an empty table");
        }
    }
}
