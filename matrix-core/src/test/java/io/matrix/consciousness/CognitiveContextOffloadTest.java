package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveContextOffloadTest {

    @Test
    void emptyReturnsZeroStats() {
        CognitiveContextOffload off = new CognitiveContextOffload(4, 8, 16);
        CognitiveContextOffload.TierStats s = off.stats();
        assertThat(s.vramSize()).isEqualTo(0);
        assertThat(s.dramSize()).isEqualTo(0);
        assertThat(s.ssdSize()).isEqualTo(0);
        assertThat(s.total()).isEqualTo(0);
    }

    @Test
    void appendFillsVRAM() {
        CognitiveContextOffload off = new CognitiveContextOffload(4, 8, 16);
        for (int i = 0; i < 3; i++) off.append(makeProfile(i));
        CognitiveContextOffload.TierStats s = off.stats();
        assertThat(s.vramSize()).isEqualTo(3);
        assertThat(s.dramSize()).isEqualTo(0);
    }

    @Test
    void overflowGoesToDRAM() {
        CognitiveContextOffload off = new CognitiveContextOffload(2, 4, 16);
        for (int i = 0; i < 5; i++) off.append(makeProfile(i));
        CognitiveContextOffload.TierStats s = off.stats();
        assertThat(s.total()).isEqualTo(5);
        assertThat(s.vramSize()).isLessThanOrEqualTo(2);
        assertThat(s.dramSize()).isGreaterThan(0);
    }

    @Test
    void overflowGoesToSSD() {
        CognitiveContextOffload off = new CognitiveContextOffload(2, 2, 16);
        for (int i = 0; i < 10; i++) off.append(makeProfile(i));
        CognitiveContextOffload.TierStats s = off.stats();
        assertThat(s.total()).isEqualTo(10);
        assertThat(s.ssdSize()).isGreaterThan(0);
    }

    @Test
    void invalidCapacitiesThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveContextOffload(0, 4, 16)
        );
    }

    @Test
    void nullAppendNoOp() {
        CognitiveContextOffload off = new CognitiveContextOffload(4, 8, 16);
        off.append(null);
        assertThat(off.stats().total()).isEqualTo(0);
    }

    @Test
    void allReturnsAllTiers() {
        CognitiveContextOffload off = new CognitiveContextOffload(2, 4, 8);
        for (int i = 0; i < 10; i++) off.append(makeProfile(i));
        assertThat(off.all().size()).isEqualTo(off.stats().total());
    }

    @Test
    void retrievalLatencyIncreasesWithSize() {
        CognitiveContextOffload off = new CognitiveContextOffload(2, 4, 8);
        int initialLatency = off.retrievalLatency();
        for (int i = 0; i < 10; i++) off.append(makeProfile(i));
        assertThat(off.retrievalLatency()).isGreaterThan(initialLatency);
    }

    @Test
    void totalCapacityCorrect() {
        CognitiveContextOffload off = new CognitiveContextOffload(4, 8, 16);
        assertThat(off.totalCapacity()).isEqualTo(28);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
