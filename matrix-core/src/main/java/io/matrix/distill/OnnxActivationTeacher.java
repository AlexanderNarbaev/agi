package io.matrix.distill;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * OnnxActivationTeacher — activation source for SPEC-001 Этап B distillation,
 * backed by an exported ONNX teacher model (ONNX Runtime уже в classpath).
 *
 * <p>Contract: модель принимает тензор {@code [batch, inputBits]} float и
 * возвращает {@code [batch, 1]} — скалярную активацию на строку.
 * Дистиллятор бинаризует активации порогом.
 *
 * <p>NOTE: реальный учитель .onnx экспортируется оффлайн
 * (scripts/gen_teacher_onnx.py создаёт крошечный FFN для EXP-009B).
 */
public final class OnnxActivationTeacher implements AutoCloseable {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;

    public OnnxActivationTeacher(Path modelPath) throws Exception {
        if (!Files.isRegularFile(modelPath)) {
            throw new IllegalArgumentException(
                    "teacher model not found: " + modelPath
                            + " — сгенерируйте scripts/gen_teacher_onnx.py (BLOCKED-EXT: teacher-model)");
        }
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(modelPath.toString());
        this.inputName = session.getInputNames().iterator().next();
    }

    /** Пакетный инференс: {@code batch[r][c]} float-признаки → активация на строку. */
    public float[] inferBatch(float[][] batch) throws Exception {
        try (OnnxTensor t = OnnxTensor.createTensor(env, batch);
             var result = session.run(Map.of(inputName, t))) {
            var out = result.get(0);
            Object v = out.getValue();
            if (v instanceof float[][] m) {
                float[] res = new float[m.length];
                for (int i = 0; i < m.length; i++) res[i] = m[i][0];
                return res;
            }
            if (v instanceof float[] flat) {
                return flat;
            }
            throw new IllegalStateException("unexpected teacher output type: " + v.getClass());
        }
    }

    /** Признаки из упакованного слова LSB-first: бит i = признак i. */
    public static float[] unpackFeatures(long packed, int bits) {
        float[] f = new float[bits];
        for (int b = 0; b < bits; b++) {
            f[b] = (packed >>> b) & 1L;
        }
        return f;
    }

    @Override
    public void close() throws Exception {
        session.close();
    }
}
