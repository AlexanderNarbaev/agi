package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NcaBrainSimulatorTest {

    @Test
    void constructorRejectsBadDims() {
        assertThatThrownBy(() -> new NcaBrainSimulator(2, 3, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NcaBrainSimulator(3, 2, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NcaBrainSimulator(3, 3, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dimensionsAreCorrect() {
        NcaBrainSimulator nca = new NcaBrainSimulator(5, 7, new Random(1));
        assertThat(nca.width()).isEqualTo(5);
        assertThat(nca.height()).isEqualTo(7);
    }

    @Test
    void seedCenterSetsCenterCell() {
        NcaBrainSimulator nca = new NcaBrainSimulator(5, 5, new Random(1));
        float[] seed = {0.1f, 0.2f, 0.3f, 0.4f};
        nca.seedCenter(seed);
        float[] center = nca.cell(2, 2);
        assertThat(center).containsExactly(0.1f, 0.2f, 0.3f, 0.4f);
    }

    @Test
    void seedCenterRejectsBadChannels() {
        NcaBrainSimulator nca = new NcaBrainSimulator(5, 5, new Random(1));
        assertThatThrownBy(() -> nca.seedCenter(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nca.seedCenter(new float[3]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cellGetterReturnsCorrectDimensions() {
        NcaBrainSimulator nca = new NcaBrainSimulator(5, 5, new Random(1));
        float[] c = nca.cell(0, 0);
        assertThat(c).hasSize(NcaBrainSimulator.CHANNELS);
    }

    @Test
    void cellGetterRejectsOutOfBounds() {
        NcaBrainSimulator nca = new NcaBrainSimulator(5, 5, new Random(1));
        assertThatThrownBy(() -> nca.cell(-1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nca.cell(0, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nca.cell(5, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nca.cell(0, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stepNRunsWithoutCrash() {
        NcaBrainSimulator nca = new NcaBrainSimulator(8, 8, new Random(1));
        nca.stepN(10);
        // Just verify no exception and state is still valid
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                float[] c = nca.cell(x, y);
                assertThat(c).hasSize(NcaBrainSimulator.CHANNELS);
                for (float v : c) {
                    assertThat(v).isBetween(0.0f, 1.0f);
                }
            }
        }
    }

    @Test
    void stepNWithZeroStepsIsNoOp() {
        NcaBrainSimulator nca = new NcaBrainSimulator(5, 5, new Random(1));
        float[][] before = nca.snapshot();
        nca.stepN(0);
        float[][] after = nca.snapshot();
        for (int i = 0; i < before.length; i++) {
            for (int c = 0; c < NcaBrainSimulator.CHANNELS; c++) {
                assertThat(after[i][c]).isEqualTo(before[i][c]);
            }
        }
    }

    @Test
    void stepNRejectsNegative() {
        NcaBrainSimulator nca = new NcaBrainSimulator(5, 5, new Random(1));
        assertThatThrownBy(() -> nca.stepN(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshotAndRestoreRoundTrip() {
        NcaBrainSimulator nca = new NcaBrainSimulator(5, 5, new Random(1));
        float[][] snap = nca.snapshot();
        nca.stepN(10);
        nca.restore(snap);
        float[][] after = nca.snapshot();
        for (int i = 0; i < snap.length; i++) {
            for (int c = 0; c < NcaBrainSimulator.CHANNELS; c++) {
                assertThat(after[i][c]).isEqualTo(snap[i][c]);
            }
        }
    }

    @Test
    void restoreRejectsBadSnapshot() {
        NcaBrainSimulator nca = new NcaBrainSimulator(5, 5, new Random(1));
        assertThatThrownBy(() -> nca.restore(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nca.restore(new float[10][]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void distanceToIsZeroForSelf() {
        NcaBrainSimulator nca = new NcaBrainSimulator(5, 5, new Random(1));
        float[][] snap = nca.snapshot();
        double d = nca.distanceTo(snap);
        assertThat(d).isEqualTo(0.0);
    }

    @Test
    void distanceToReflectsDifference() {
        NcaBrainSimulator nca = new NcaBrainSimulator(5, 5, new Random(1));
        float[][] target = new float[25][NcaBrainSimulator.CHANNELS];
        for (int i = 0; i < target.length; i++) {
            for (int c = 0; c < NcaBrainSimulator.CHANNELS; c++) {
                target[i][c] = 0.5f;
            }
        }
        double d = nca.distanceTo(target);
        assertThat(d).isGreaterThan(0.0);
        assertThat(d).isLessThan(1.0);
    }

    @Test
    void distanceToRejectsBadTarget() {
        NcaBrainSimulator nca = new NcaBrainSimulator(5, 5, new Random(1));
        assertThatThrownBy(() -> nca.distanceTo(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nca.distanceTo(new float[10][0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seedCenterRejectsBadArgs() {
        NcaBrainSimulator nca = new NcaBrainSimulator(5, 5, new Random(1));
        assertThatThrownBy(() -> nca.seedCenter(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nca.seedCenter(new float[3]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameSeedProducesSameInitialState() {
        NcaBrainSimulator a = new NcaBrainSimulator(5, 5, new Random(42));
        NcaBrainSimulator b = new NcaBrainSimulator(5, 5, new Random(42));
        for (int i = 0; i < 25; i++) {
            for (int c = 0; c < NcaBrainSimulator.CHANNELS; c++) {
                assertThat(a.cell(i % 5, i / 5)[c]).isEqualTo(b.cell(i % 5, i / 5)[c]);
            }
        }
    }
}
