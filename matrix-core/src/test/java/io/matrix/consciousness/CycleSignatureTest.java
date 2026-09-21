package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 251 — CycleSignature unit tests. */
class CycleSignatureTest {

    @Test
    void emptyTraceProducesEmptyHashes() {
        var svc = new BrainLoopService();
        var sig = CycleSignature.compute(1, "X", svc.trace());
        assertThat(sig.headHash()).isEmpty();
        assertThat(sig.tailHash()).isEmpty();
        assertThat(sig.cycleId()).isEqualTo("c1");
    }

    @Test
    void nonEmptyTraceProducesHashes() {
        var svc = new BrainLoopService();
        svc.cycle("X");
        var sig = CycleSignature.compute(1, "X", svc.trace());
        assertThat(sig.headHash()).isNotEmpty();
        assertThat(sig.tailHash()).isNotEmpty();
        assertThat(sig.combinedHex().length()).isLessThanOrEqualTo(16);
    }

    @Test
    void cycleIdsAreUnique() {
        var svc = new BrainLoopService();
        var s1 = CycleSignature.compute(1, "X", svc.trace());
        var s2 = CycleSignature.compute(2, "Y", svc.trace());
        assertThat(s1.cycleId()).isNotEqualTo(s2.cycleId());
    }

    @Test
    void shortHashTruncates() {
        String h = "abcdef0123456789";
        assertThat(CycleSignature.shortHash(h)).hasSize(8);
    }

    @Test
    void shortHashEmpty() {
        assertThat(CycleSignature.shortHash("")).isEmpty();
        assertThat(CycleSignature.shortHash(null)).isEmpty();
    }

    @Test
    void shortHashShortInput() {
        assertThat(CycleSignature.shortHash("abc")).isEqualTo("abc");
    }
}
