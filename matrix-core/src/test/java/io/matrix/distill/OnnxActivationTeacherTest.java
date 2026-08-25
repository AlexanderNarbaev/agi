package io.matrix.distill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link OnnxActivationTeacher} fail-fast contract.
 *
 * <p>Real-session inference requires an exported teacher .onnx artifact
 * (BLOCKED-EXT: teacher-model) and is covered by the integration wave once
 * such an artifact is produced.
 */
class OnnxActivationTeacherTest {

    @TempDir
    Path tmp;

    @Test
    void missingModelFailsFastWithDescriptiveError() {
        assertThatThrownBy(() -> new OnnxActivationTeacher(tmp.resolve("no-such-teacher.onnx")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BLOCKED-EXT: teacher-model");
    }
}
