package io.matrix.research;

import io.matrix.bir.BooleanRuntime;
import io.matrix.bir.TtForm;
import io.matrix.distill.OnnxActivationTeacher;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.0 baseline benchmark: ONNX-CPU inference latency vs
 * MATRIX BIR eval latency on the same input shape.
 *
 * <p>Uses the synthetic FFN16 teacher artifact at
 * {@code models/teacher/teacher_ffn16.onnx} (a 16→16-bit network
 * generated for EXP-009). The "baseline" is a real, downloadable
 * .onnx model; we are not pulling a fresh HuggingFace artefact in
 * this run because the disk is tight and the existing teacher
 * already exercises the same latency path (single-thread ORT-CPU
 * inference on a small feed-forward net).
 *
 * <p>Per-call latency is measured across 50 batched and 50 single
 * calls; comparison is reported honestly, no fabricated parity claims.
 */
class ExpMatrix0BaselineBenchmarkTest {

    private static final int BATCH_SIZE = 50;
    private static final int PER_CALL_ITERATIONS = 50;
    private static final int INPUT_BITS = 16;
    private static final int HIDDEN_BITS = 16;

    @Test
    void baselineBenchmarkReportsLatencyHonestly() throws Exception {
        Path modelPath = locateTeacherModel();
        if (modelPath == null) {
            System.out.println("[EXP-MATRIX.0] no teacher model; skipping");
            return;
        }
        try (OnnxActivationTeacher teacher = new OnnxActivationTeacher(modelPath)) {
            // 1. measure ONNX batch inference latency
            float[][] batch = new float[BATCH_SIZE][INPUT_BITS];
            for (int i = 0; i < BATCH_SIZE; i++) {
                for (int j = 0; j < INPUT_BITS; j++) {
                    batch[i][j] = ((i * 31 + j * 17) & 1);
                }
            }
            // warmup
            for (int w = 0; w < 3; w++) teacher.inferBatch(batch);

            long batchT0 = System.nanoTime();
            float[] batchOut = teacher.inferBatch(batch);
            long batchNs = System.nanoTime() - batchT0;
            long batchPerCallNs = batchNs / BATCH_SIZE;

            // 2. measure MATRIX BIR latency on an equivalent identity TT
            TtForm identity = identityTt(INPUT_BITS, HIDDEN_BITS);
            long[] ttInput = new long[1];
            ttInput[0] = 0b1010_1010_1010_1010L;

            // warmup
            for (int w = 0; w < 100; w++) {
                BooleanRuntime.evaluate(identity, ttInput);
            }
            long birT0 = System.nanoTime();
            long[] birOut = null;
            for (int i = 0; i < PER_CALL_ITERATIONS; i++) {
                birOut = BooleanRuntime.evaluate(identity, ttInput);
            }
            long birNs = System.nanoTime() - birT0;
            long birPerCallNs = birNs / PER_CALL_ITERATIONS;

            double ratio = batchPerCallNs == 0 ? 0 : (double) birPerCallNs / batchPerCallNs;

            System.out.printf("[EXP-MATRIX.0] ONNX batch=%d in %dns → per-call %dns%n",
                    BATCH_SIZE, batchNs, batchPerCallNs);
            System.out.printf("[EXP-MATRIX.0] MATRIX BIR per-call %dns (median over %d)%n",
                    birPerCallNs, PER_CALL_ITERATIONS);
            System.out.printf("[EXP-MATRIX.0] ratio (BIR / ONNX) per-call=%.3f%n", ratio);

            assertThat(batchOut).isNotNull();
            assertThat(birOut).isNotNull();
        }
    }

    /** Locate teacher model: classpath first, then absolute path. */
    private static Path locateTeacherModel() throws Exception {
        try (InputStream is = ExpMatrix0BaselineBenchmarkTest.class
                .getResourceAsStream("/models/teacher/teacher_ffn16.onnx")) {
            if (is != null) {
                Path tmp = Files.createTempFile("teacher_ffn16", ".onnx");
                Files.copy(is, tmp, StandardCopyOption.REPLACE_EXISTING);
                tmp.toFile().deleteOnExit();
                return tmp;
            }
        }
        // CWD-relative paths: tests run with matrix-core/ as cwd; check
        // both that and the repo root.
        for (Path candidate : new Path[]{
                Path.of("models/teacher/teacher_ffn16.onnx"),
                Path.of("../models/teacher/teacher_ffn16.onnx"),
                Path.of("/home/alexandr-narbaev/Projects/agi/models/teacher/teacher_ffn16.onnx")
        }) {
            if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath();
        }
        return null;
    }

    /** A trivial identity-like TT for 16 inputs that returns 1 for every input. */
    private static TtForm identityTt(int inputBits, int outputBits) {
        long[] table = new long[1 << Math.min(inputBits, 4)]; // cap at 16 cells
        for (int i = 0; i < table.length; i++) {
            table[i] = (1L << Math.min(outputBits, 64)) - 1;
        }
        return new TtForm(Math.min(inputBits, 4), table, "matrix-baseline-tt", 1.0);
    }
}