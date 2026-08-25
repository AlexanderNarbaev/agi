package io.matrix.distill;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * OnnxActivationTeacher — activation source for SPEC-001 Этап B distillation,
 * backed by an exported ONNX teacher model (ONNX Runtime is already on the
 * classpath).
 *
 * <p>Contract: single float output tensor whose scalar value is the teacher's
 * activation for the binarized input vector. The caller (Distiller) binarizes
 * activations against the corpus median.
 *
 * <p>NOTE: a real teacher .onnx must be exported offline (e.g. from a small
 * LLM FFN slice). Until such an artifact exists, construction fails fast with
 * a descriptive error — BLOCKED-EXT(teacher-model), not silently skipped.
 */
public final class OnnxActivationTeacher implements AutoCloseable {

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;

    public OnnxActivationTeacher(Path modelPath) throws Exception {
        if (!Files.isRegularFile(modelPath)) {
            throw new IllegalArgumentException(
                    "teacher model not found: " + modelPath
                            + " — export an ONNX teacher first (BLOCKED-EXT: teacher-model)");
        }
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(modelPath.toString());
        this.inputName = session.getInputNames().iterator().next();
    }

    /**
     * Runs the teacher on a packed input word.
     *
     * @return scalar activation
     */
    public float activations(long packedInput) throws Exception {
        try (OnnxTensor t = OnnxTensor.createTensor(env, new long[]{packedInput});
             var result = session.run(Map.of(inputName, t))) {
            var out = result.get(0);
            if (out.getValue() instanceof float[] arr && arr.length > 0) {
                return arr[0];
            }
            if (out.getValue() instanceof Float f) {
                return f;
            }
            throw new IllegalStateException(
                    "unexpected teacher output type: " + out.getValue().getClass());
        }
    }

    @Override
    public void close() throws Exception {
        session.close();
    }
}
