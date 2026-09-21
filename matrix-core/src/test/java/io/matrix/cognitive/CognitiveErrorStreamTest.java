package io.matrix.cognitive;

import io.matrix.cognitive.CognitiveError.ErrorKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 93 — CognitiveErrorStream: bounded ring buffer (DESIGN-64).
 */
class CognitiveErrorStreamTest {

    @Test
    void emptyStream() {
        CognitiveErrorStream s = new CognitiveErrorStream(8);
        assertThat(s.size()).isEqualTo(0);
        assertThat(s.isEmpty()).isTrue();
        assertThat(s.mostRecent()).isNull();
        assertThat(s.distinctKindCount()).isEqualTo(0);
    }

    @Test
    void recordOneError() {
        CognitiveErrorStream s = new CognitiveErrorStream(8);
        CognitiveError e = new CognitiveError(1, ErrorKind.PREDICTION_ERROR,
                new float[]{0.1f}, "first");
        s.record(e);
        assertThat(s.size()).isEqualTo(1);
        assertThat(s.isEmpty()).isFalse();
        assertThat(s.mostRecent()).isEqualTo(e);
        assertThat(s.distinctKindCount()).isEqualTo(1);
    }

    @Test
    void evictsOldestWhenFull() {
        CognitiveErrorStream s = new CognitiveErrorStream(3);
        CognitiveError e1 = new CognitiveError(1, ErrorKind.PREDICTION_ERROR,
                new float[]{0.1f}, "1");
        CognitiveError e2 = new CognitiveError(2, ErrorKind.ACTION_INEFFECTIVE,
                new float[]{0.2f}, "2");
        CognitiveError e3 = new CognitiveError(3, ErrorKind.INTEGRATION_VIOLATION,
                new float[]{0.3f}, "3");
        CognitiveError e4 = new CognitiveError(4, ErrorKind.MEMORY_RETRIEVAL_MISS,
                new float[]{0.4f}, "4");
        s.record(e1);
        s.record(e2);
        s.record(e3);
        s.record(e4);
        assertThat(s.size()).isEqualTo(3);
        // Oldest (e1) should be evicted; e2, e3, e4 remain
        int count = 0;
        boolean containsE1 = false;
        for (CognitiveError e : s) {
            if (e == e1) containsE1 = true;
            count++;
        }
        assertThat(count).isEqualTo(3);
        assertThat(containsE1).isFalse();
        assertThat(s.mostRecent()).isEqualTo(e4);
    }

    @Test
    void snapshotHashStable() {
        CognitiveErrorStream s1 = new CognitiveErrorStream(8);
        CognitiveErrorStream s2 = new CognitiveErrorStream(8);
        CognitiveError e = new CognitiveError(1, ErrorKind.PREDICTION_ERROR,
                new float[]{0.1f, 0.2f}, "test");
        s1.record(e);
        s2.record(e);
        assertThat(s1.snapshotHash()).isEqualTo(s2.snapshotHash());
    }

    @Test
    void distinctKindCounts() {
        CognitiveErrorStream s = new CognitiveErrorStream(16);
        s.record(new CognitiveError(1, ErrorKind.PREDICTION_ERROR, new float[0], "a"));
        s.record(new CognitiveError(2, ErrorKind.PREDICTION_ERROR, new float[0], "b"));
        s.record(new CognitiveError(3, ErrorKind.ACTION_INEFFECTIVE, new float[0], "c"));
        s.record(new CognitiveError(4, ErrorKind.INTEGRATION_VIOLATION, new float[0], "d"));
        assertThat(s.distinctKindCount()).isEqualTo(3);
    }

    @Test
    void invalidCapacityThrows() {
        try {
            new CognitiveErrorStream(0);
            assertThat(false).as("should throw").isTrue();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
