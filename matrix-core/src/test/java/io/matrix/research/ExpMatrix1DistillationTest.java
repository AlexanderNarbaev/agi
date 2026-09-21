package io.matrix.research;

import io.matrix.bir.Bir;
import io.matrix.bir.BooleanRuntime;
import io.matrix.bir.TtForm;
import io.matrix.distill.Distiller;
import io.matrix.distill.OnnxActivationTeacher;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.1 distillation harness: capture teacher activations on a
 * synthetic FFN16 corpus, distill into a BIR (TtForm), measure
 * fidelity and latency against the ORT-CPU baseline.
 *
 * <p>This is the minimum-viable distillation wave — no model download.
 * It reuses the synthetic FFN16 teacher already in the repo, exercises
 * the existing {@link Distiller} pipeline, and produces honest numbers.
 */
class ExpMatrix1DistillationTest {

    private static final int INPUT_BITS = 4;       // 16 cells manageable
    private static final int CORPUS_SIZE = 200;
    private static final int TRAIN_SIZE = 160;     // 80/20 split

    @Test
    void distillFfn16AndMeasureFidelityAndLatency() throws Exception {
        Path modelPath = locateTeacher();
        if (modelPath == null) {
            System.out.println("[EXP-MATRIX.1] no teacher model; skipping");
            return;
        }
        try (OnnxActivationTeacher teacher = new OnnxActivationTeacher(modelPath)) {
            // 1. Build corpus
            long[][] corpus = new long[CORPUS_SIZE][1];
            float[][] teacherOut = new float[CORPUS_SIZE][1];
            Random rng = new Random(0xDEADBEEF);
            for (int i = 0; i < CORPUS_SIZE; i++) {
                long val = rng.nextLong() & ((1L << INPUT_BITS) - 1);
                corpus[i][0] = val;
                // map to 16-bit teacher input (LSB-aligned)
                float[] expanded = expandTo16Bits(val);
                float[] act = teacher.inferBatch(new float[][]{expanded});
                teacherOut[i][0] = act[0];
            }

            // 2. Capture + synthesize (Distiller)
            Distiller distiller = new Distiller(INPUT_BITS, 0.5);
            for (int i = 0; i < TRAIN_SIZE; i++) {
                distiller.capture(corpus[i], teacherOut[i]);
            }
            Bir distilled = distiller.synthesize("EXP-MATRIX.1");

            // 3. Measure fidelity on held-out test set
            int matches = 0;
            for (int i = TRAIN_SIZE; i < CORPUS_SIZE; i++) {
                long[] distOut = BooleanRuntime.evaluate(distilled, corpus[i]);
                float[] teachOut = teacher.inferBatch(new float[][]{expandTo16Bits(corpus[i][0])});
                boolean teachBit = teachOut[0] >= 0.5f;
                boolean distBit = (distOut[0] & 1) == 1;
                if (teachBit == distBit) matches++;
            }
            int nTest = CORPUS_SIZE - TRAIN_SIZE;
            double fidelity = (double) matches / nTest;
            System.out.printf("[EXP-MATRIX.1] distillation fidelity on %d test points: %.3f%n",
                    nTest, fidelity);

            // 4. Latency comparison
            // warmup
            for (int w = 0; w < 50; w++) BooleanRuntime.evaluate(distilled, corpus[0]);
            long birT0 = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                BooleanRuntime.evaluate(distilled, corpus[i % CORPUS_SIZE]);
            }
            long birTotalNs = System.nanoTime() - birT0;

            long ortT0 = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                teacher.inferBatch(new float[][]{expandTo16Bits(corpus[i % CORPUS_SIZE][0])});
            }
            long ortTotalNs = System.nanoTime() - ortT0;

            double birPerCall = (double) birTotalNs / 1000;
            double ortPerCall = (double) ortTotalNs / 1000;
            System.out.printf("[EXP-MATRIX.1] BIR per-call=%.0f ns, ORT per-call=%.0f ns%n",
                    birPerCall, ortPerCall);
            System.out.printf("[EXP-MATRIX.1] ratio BIR/ORT = %.3f%n",
                    birPerCall / Math.max(1, ortPerCall));

            assertThat(distilled).isNotNull();
        }
    }

    private static float[] expandTo16Bits(long val) {
        // map a 4-bit value to a 16-element float array (LSB first)
        float[] out = new float[16];
        for (int i = 0; i < 16; i++) {
            out[i] = ((val >> (i % INPUT_BITS)) & 1L) == 1L ? 1.0f : 0.0f;
        }
        return out;
    }

    private static Path locateTeacher() throws Exception {
        try (InputStream is = ExpMatrix1DistillationTest.class
                .getResourceAsStream("/models/teacher/teacher_ffn16.onnx")) {
            if (is != null) {
                Path tmp = Files.createTempFile("teacher_ffn16", ".onnx");
                Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
                tmp.toFile().deleteOnExit();
                return tmp;
            }
        }
        for (Path candidate : new Path[]{
                Path.of("models/teacher/teacher_ffn16.onnx"),
                Path.of("../models/teacher/teacher_ffn16.onnx"),
                Path.of("/home/alexandr-narbaev/Projects/agi/models/teacher/teacher_ffn16.onnx")
        }) {
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath();
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static TtForm exampleDistilled() {
        return new TtForm(4, new long[]{0xFFFFFFFFL}, "example", 1.0);
    }
}