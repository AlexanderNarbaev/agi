package io.matrix.cognitive;

import io.matrix.cognitive.CognitiveError.ErrorKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 93 — CognitiveError: deterministic failure records (DESIGN-64).
 */
class CognitiveErrorTest {

    @Test
    void deterministicHashStable() {
        float[] state = {0.1f, 0.2f, 0.3f};
        CognitiveError e1 = new CognitiveError(42, ErrorKind.PREDICTION_ERROR,
                state, "test");
        CognitiveError e2 = new CognitiveError(42, ErrorKind.PREDICTION_ERROR,
                state, "test");
        assertThat(e1.deterministicHash()).isEqualTo(e2.deterministicHash());
    }

    @Test
    void differentContentDifferentHash() {
        float[] state = {0.1f, 0.2f, 0.3f};
        CognitiveError e1 = new CognitiveError(42, ErrorKind.PREDICTION_ERROR,
                state, "test1");
        CognitiveError e2 = new CognitiveError(42, ErrorKind.PREDICTION_ERROR,
                state, "test2");
        assertThat(e1.deterministicHash()).isNotEqualTo(e2.deterministicHash());
    }

    @Test
    void differentKindDifferentHash() {
        float[] state = {0.1f, 0.2f, 0.3f};
        CognitiveError e1 = new CognitiveError(42, ErrorKind.PREDICTION_ERROR,
                state, "test");
        CognitiveError e2 = new CognitiveError(42, ErrorKind.ACTION_INEFFECTIVE,
                state, "test");
        assertThat(e1.deterministicHash()).isNotEqualTo(e2.deterministicHash());
    }
}
