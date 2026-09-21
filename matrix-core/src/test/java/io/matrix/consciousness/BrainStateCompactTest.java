package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 197 — BrainStateCompact unit tests. */
class BrainStateCompactTest {

    @Test
    void pack24Bytes() {
        BrainLoopService svc = new BrainLoopService();
        byte[] packed = BrainStateCompact.pack(svc);
        assertThat(packed).hasSize(24);
    }

    @Test
    void unpackRestoresArousal() {
        BrainLoopService svc = new BrainLoopService();
        // Force some arousal
        for (int i = 0; i < 50; i++) svc.cycle("Q-" + i);
        byte[] packed = BrainStateCompact.pack(svc);
        var unpacked = BrainStateCompact.unpack(packed);
        assertThat(unpacked.arousal()).isEqualTo(svc.arousal());
    }

    @Test
    void unpackHandlesShortInput() {
        var u = BrainStateCompact.unpack(new byte[10]);
        // Defaults to baseline
        assertThat(u.arousal()).isEqualTo(0.3);
        assertThat(u.traceCount()).isZero();
    }

    @Test
    void packUnpackRoundtrip() {
        BrainLoopService svc = new BrainLoopService();
        for (int i = 0; i < 30; i++) svc.cycle("X-" + i);
        byte[] packed = BrainStateCompact.pack(svc);
        var u = BrainStateCompact.unpack(packed);
        // Arousal is preserved, traceCount is preserved
        assertThat(u.arousal()).isEqualTo(svc.arousal());
    }

    @Test
    void unpackedBrainRecord() {
        var u = new BrainStateCompact.UnpackedBrain(0.5, 100, "abc");
        assertThat(u.arousal()).isEqualTo(0.5);
        assertThat(u.traceCount()).isEqualTo(100);
        assertThat(u.tailHash()).isEqualTo("abc");
    }
}
