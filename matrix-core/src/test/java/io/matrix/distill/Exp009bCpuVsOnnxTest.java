package io.matrix.distill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-009B: дистилляция активаций ONNX-учителя (FFN 16→64→GELU→64→1) в BIR
 * и измерение инференса на CPU. Single-run JVM; НЕ JMH-grade.
 */
class Exp009bCpuVsOnnxTest {

    private static final int BITS = 16;
    private static final int N = 2000;
    private static final int TRAIN = 1000;

    @TempDir
    Path tmp;

    @Test
    void distillAndMeasure() throws Exception {
        Path model = java.util.stream.Stream.of(
                        Path.of("../models/teacher/teacher_ffn16.onnx"),
                        Path.of("models/teacher/teacher_ffn16.onnx"))
                .filter(Files::isRegularFile)
                .findFirst()
                .orElse(null);
        org.junit.jupiter.api.Assumptions.assumeTrue(model != null,
                "teacher .onnx не сгенерирован — запустите scripts/gen_teacher_onnx.py");

        Random rnd = new Random(42);
        long[] inputs = new long[N];
        for (int i = 0; i < N; i++) {
            inputs[i] = rnd.nextLong() & 0xFFFFL;
        }

        try (OnnxActivationTeacher teacher = new OnnxActivationTeacher(model)) {
            float[][] batch = new float[N][];
            for (int i = 0; i < N; i++) {
                batch[i] = OnnxActivationTeacher.unpackFeatures(inputs[i], BITS);
            }

            long t0 = System.nanoTime();
            float[] acts = teacher.inferBatch(batch);
            long t1 = System.nanoTime();

            // Медиана активаций как порог бинаризации.
            float[] sorted = acts.clone();
            java.util.Arrays.sort(sorted);
            double median = sorted[N / 2];

            Distiller d = new Distiller(BITS, median);
            for (int i = 0; i < N; i++) {
                d.capture(new long[]{inputs[i]}, new float[]{acts[i]});
            }
            io.matrix.bir.Bir bir = d.synthesize("exp009b");

            long t2 = System.nanoTime();
            io.matrix.bir.BirForm form = (io.matrix.bir.BirForm) bir;
            long[] in = new long[1];
            long[] out = new long[1];
            for (int i = 0; i < N; i++) {
                in[0] = inputs[i];
                form.eval(in, out);
            }
            long t3 = System.nanoTime();

            // Согласованность на holdout-половине.
            int agree = 0, total = N - TRAIN;
            float[] testActs = java.util.Arrays.copyOfRange(acts, TRAIN, N);
            float[][] testBatch = new float[total][];
            for (int i = 0; i < total; i++) testBatch[i] = batch[TRAIN + i];
            float[] teacherHoldout = teacher.inferBatch(testBatch);
            double threshold2 = median;
            for (int i = 0; i < total; i++) {
                in[0] = inputs[TRAIN + i];
                form.eval(in, out);
                boolean birBit = out[0] != 0;
                // Эталон: знак активации учителя относительно порога.
                boolean tBit = teacherHoldout[i] >= threshold2;
                if (birBit == tBit) agree++;
            }
            double agreement = agree / (double) total;

            double ortCpuMs = (t1 - t0) / 1e6;
            double birMs = (t3 - t2) / 1e6;

            System.out.printf(
                    "EXP009B ortCpuMs=%.3f birMs=%.3f speedupBirOverOrt=%.2fx fidelityHoldout=%.4f%n",
                    ortCpuMs, birMs, ortCpuMs / Math.max(birMs, 1e-9), agreement);

            assertThat(agreement).isGreaterThan(0.95);
        }
    }

    /** Проба CUDA Execution Provider: печатает причину недоступности (статус GPU-инференса). */
    @Test
    void cudaProviderProbe() {
        try {
            var so = new ai.onnxruntime.OrtSession.SessionOptions();
            so.addCUDA();
            System.out.println("EXP009B cudaEP=available");
        } catch (Throwable t) {
            System.out.println("EXP009B cudaEP=unavailable reason=" + t.getMessage());
        }
    }
}
