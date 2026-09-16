package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CognitiveSlidingWindowTest {

    @Test
    void emptyWindowHasZeroSize() {
        CognitiveSlidingWindow sw = new CognitiveSlidingWindow(2, 8);
        assertThat(sw.size()).isEqualTo(0);
    }

    @Test
    void addIncreasesSize() {
        CognitiveSlidingWindow sw = new CognitiveSlidingWindow(2, 8);
        sw.add(makeProfile(0.5));
        // First add: 1 in window, 1 in sinks (auto-promoted)
        assertThat(sw.windowCount()).isLessThanOrEqualTo(2);
        assertThat(sw.size()).isGreaterThan(0);
    }

    @Test
    void windowSizeRespected() {
        CognitiveSlidingWindow sw = new CognitiveSlidingWindow(2, 4);
        for (int i = 0; i < 10; i++) sw.add(makeProfile(i / 10.0));
        assertThat(sw.windowCount()).isEqualTo(4);
    }

    @Test
    void sinksAccumulated() {
        CognitiveSlidingWindow sw = new CognitiveSlidingWindow(3, 8);
        for (int i = 0; i < 5; i++) sw.add(makeProfile(i / 5.0));
        assertThat(sw.sinkCount()).isLessThanOrEqualTo(3);
    }

    @Test
    void invalidSinkCountThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveSlidingWindow(-1, 8)
        );
    }

    @Test
    void invalidWindowSizeThrows() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CognitiveSlidingWindow(2, 0)
        );
    }

    @Test
    void nullAddIsNoOp() {
        CognitiveSlidingWindow sw = new CognitiveSlidingWindow(2, 8);
        sw.add(null);
        assertThat(sw.size()).isEqualTo(0);
    }

    @Test
    void windowUtilizationBounded() {
        CognitiveSlidingWindow sw = new CognitiveSlidingWindow(2, 8);
        for (int i = 0; i < 20; i++) sw.add(makeProfile(i / 20.0));
        assertThat(sw.windowUtilization()).isCloseTo(1.0, offset(1e-9));
    }

    @Test
    void allContainsBothSinksAndWindow() {
        CognitiveSlidingWindow sw = new CognitiveSlidingWindow(2, 4);
        for (int i = 0; i < 10; i++) sw.add(makeProfile(i / 10.0));
        List<CognitiveGenesisProfile> all = sw.all();
        assertThat(all.size()).isEqualTo(sw.size());
        assertThat(all.size()).isGreaterThanOrEqualTo(sw.sinkCount() + sw.windowCount());
    }

    @Test
    void zeroSinksIsValid() {
        CognitiveSlidingWindow sw = new CognitiveSlidingWindow(0, 4);
        sw.add(makeProfile(0.5));
        assertThat(sw.sinkCount()).isEqualTo(0);
        assertThat(sw.windowCount()).isEqualTo(1);
        assertThat(sw.sinkUtilization()).isEqualTo(1.0);
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }

    private static org.assertj.core.data.Offset<Double> offset(double tol) {
        return org.assertj.core.data.Offset.offset(tol);
    }
}
