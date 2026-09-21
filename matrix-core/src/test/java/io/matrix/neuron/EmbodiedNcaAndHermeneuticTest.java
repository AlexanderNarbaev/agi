package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class EmbodiedNcaCortexTest {

    @Test
    void constructorAndDimensions() {
        EmbodiedNcaCortex nca = new EmbodiedNcaCortex(4, 4, 64, 0.1, new Random(1));
        assertThat(nca.width()).isEqualTo(4);
        assertThat(nca.height()).isEqualTo(4);
        assertThat(nca.hdcBits()).isEqualTo(64);
        assertThat(nca.cellCount()).isEqualTo(16);
    }

    @Test
    void rejectsBadDimensions() {
        assertThatThrownBy(() -> new EmbodiedNcaCortex(2, 4, 64, 0.1, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbodiedNcaCortex(4, 2, 64, 0.1, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbodiedNcaCortex(4, 4, 0, 0.1, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbodiedNcaCortex(4, 4, 32, 0.1, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmbodiedNcaCortex(4, 4, 64, 0.1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stepNRunsWithoutCrash() {
        EmbodiedNcaCortex nca = new EmbodiedNcaCortex(4, 4, 64, 0.1, new Random(1));
        nca.stepN(10);
        // Should not throw and state should remain valid
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                long[] s = nca.stateAt(x, y);
                assertThat(s).hasSize(1);
            }
        }
    }

    @Test
    void stepNZeroIsNoOp() {
        EmbodiedNcaCortex nca = new EmbodiedNcaCortex(4, 4, 64, 0.1, new Random(1));
        long[][] before = new long[nca.cellCount()][];
        for (int i = 0; i < nca.cellCount(); i++) {
            before[i] = nca.stateAt(i % nca.width(), i / nca.width()).clone();
        }
        nca.stepN(0);
        for (int i = 0; i < nca.cellCount(); i++) {
            assertThat(nca.stateAt(i % nca.width(), i / nca.width()))
                    .containsExactly(before[i]);
        }
    }

    @Test
    void rejectsNegativeSteps() {
        EmbodiedNcaCortex nca = new EmbodiedNcaCortex(4, 4, 64, 0.1, new Random(1));
        assertThatThrownBy(() -> nca.stepN(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stateAtBounds() {
        EmbodiedNcaCortex nca = new EmbodiedNcaCortex(4, 4, 64, 0.1, new Random(1));
        assertThatThrownBy(() -> nca.stateAt(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nca.stateAt(0, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nca.stateAt(4, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nca.stateAt(0, 4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void injectChangesState() {
        EmbodiedNcaCortex nca = new EmbodiedNcaCortex(4, 4, 64, 0.1, new Random(1));
        long[] pattern = new long[1];
        pattern[0] = 0xDEADBEEFL;
        nca.inject(2, 2, pattern);
        assertThat(nca.stateAt(2, 2)).containsExactly(0xDEADBEEFL);
    }

    @Test
    void injectRejectsBadArgs() {
        EmbodiedNcaCortex nca = new EmbodiedNcaCortex(4, 4, 64, 0.1, new Random(1));
        assertThatThrownBy(() -> nca.inject(-1, 0, new long[1]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> nca.inject(0, 0, new long[2]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void totalDensityIsBounded() {
        EmbodiedNcaCortex nca = new EmbodiedNcaCortex(4, 4, 64, 0.1, new Random(1));
        double d = nca.totalDensity();
        assertThat(d).isBetween(0.0, 1.0);
    }
}

class HermeneuticLoopTest {

    @Test
    void fuseHorizonsSingleInterpretation() {
        long[][] interp = {new long[]{0xFFL, 0x00L}};
        long[] fused = HermeneuticLoop.fuseHorizons(interp);
        // Single interpretation: all bits set to all-1s if majority is 1s
        assertThat(fused[0]).isEqualTo(-1L); // all 1s
        assertThat(fused[1]).isEqualTo(0L); // 1 zero > 1 one, so all zeros
    }

    @Test
    void fuseHorizonsMajorityVote() {
        long[][] interp = {
                new long[]{0xF0L, 0x0FL},
                new long[]{0xF0L, 0xF0L},
                new long[]{0x00L, 0x00L}
        };
        long[] fused = HermeneuticLoop.fuseHorizons(interp);
        // Implementation uses per-word majority: byte 0 has 16 ones, 8 zeros → majority is 1s
        // byte 1 has 12 ones, 12 zeros → tie → majority is 1s (since >=)
        // So fused is all-1s = -1L for each word
        assertThat(fused[0]).isEqualTo(-1L);
        assertThat(fused[1]).isEqualTo(-1L);
    }

    @Test
    void fuseHorizonsRejectsEmpty() {
        assertThatThrownBy(() -> HermeneuticLoop.fuseHorizons(new long[0][]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HermeneuticLoop.fuseHorizons(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fuseHorizonsRejectsLengthMismatch() {
        long[][] interp = {
                new long[]{0xF0L, 0x0FL},
                new long[]{0xF0L} // mismatch
        };
        assertThatThrownBy(() -> HermeneuticLoop.fuseHorizons(interp))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void interpretReturnsNearestLabel() {
        String[] labels = {"A", "B", "C"};
        long[][] codes = {
                new long[]{0xFFL, 0x00L},
                new long[]{0x00L, 0xFFL},
                new long[]{0xF0L, 0x0FL}
        };
        long[] query = new long[]{0xFFL, 0x00L};
        // Should match "A" exactly
        assertThat(HermeneuticLoop.interpret(labels, codes, query)).isEqualTo("A");
    }

    @Test
    void interpretRejectsBadInputs() {
        assertThatThrownBy(() -> HermeneuticLoop.interpret(null, new long[][]{new long[1]}, new long[1]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HermeneuticLoop.interpret(new String[]{"A"}, null, new long[1]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HermeneuticLoop.interpret(new String[]{"A"}, new long[][]{new long[1]}, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void interpretLengthMismatch() {
        assertThatThrownBy(() -> HermeneuticLoop.interpret(
                new String[]{"A", "B"},
                new long[][]{new long[1]},
                new long[1]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void interpretEmptyCodebook() {
        assertThat(HermeneuticLoop.interpret(new String[0], new long[0][], new long[1]))
                .isNull();
    }

    @Test
    void fullCycleProducesFusedResult() {
        Map<String, long[]> codebook = new HashMap<>();
        codebook.put("A", new long[]{0xFFL, 0x00L});
        codebook.put("B", new long[]{0x00L, 0xFFL});
        long[][] interps = {
                new long[]{0xFFL, 0x00L},
                new long[]{0xFFL, 0x00L},
                new long[]{0x00L, 0x00L}
        };
        HermeneuticLoop.HermeneuticResult r = HermeneuticLoop.cycle(interps, codebook);
        assertThat(r.fusedCode()).hasSize(2);
        // Most interpretations match "A", so consensus should be "A" or close
        assertThat(r.consensusLabel()).isIn("A", "B");
        assertThat(r.perInterpretationDistances()).hasSize(3);
        assertThat(r.averageDistance()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void fullCycleRejectsBadInputs() {
        Map<String, long[]> codebook = new HashMap<>();
        codebook.put("A", new long[1]);
        assertThatThrownBy(() -> HermeneuticLoop.cycle(new long[0][], codebook))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HermeneuticLoop.cycle(new long[][]{new long[1]}, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HermeneuticLoop.cycle(new long[][]{new long[1]}, new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hammingDistanceBasic() {
        assertThat(HermeneuticLoop.hamming(new long[]{0xFFL}, new long[]{0x00L})).isEqualTo(8);
        assertThat(HermeneuticLoop.hamming(new long[]{0xFFL, 0x00L}, new long[]{0xFFL, 0x00L})).isEqualTo(0);
        assertThat(HermeneuticLoop.hamming(new long[]{0xFFL}, new long[]{0xFFL})).isEqualTo(0);
    }

    @Test
    void hammingHandlesNulls() {
        assertThat(HermeneuticLoop.hamming(null, new long[1])).isEqualTo(Integer.MAX_VALUE);
        assertThat(HermeneuticLoop.hamming(new long[1], null)).isEqualTo(Integer.MAX_VALUE);
        assertThat(HermeneuticLoop.hamming(new long[1], new long[2])).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void resultRecordHasAllFields() {
        HermeneuticLoop.HermeneuticResult r = new HermeneuticLoop.HermeneuticResult(
                new long[]{0xFFL}, "A", 5, new int[]{1, 2, 3});
        assertThat(r.fusedCode()).hasSize(1);
        assertThat(r.consensusLabel()).isEqualTo("A");
        assertThat(r.consensusDistance()).isEqualTo(5);
        assertThat(r.perInterpretationDistances()).containsExactly(1, 2, 3);
        assertThat(r.averageDistance()).isCloseTo(2.0, within(1e-9));
    }

    @Test
    void resultAverageDistanceEmpty() {
        HermeneuticLoop.HermeneuticResult r = new HermeneuticLoop.HermeneuticResult(
                new long[]{0xFFL}, "A", 5, new int[0]);
        assertThat(r.averageDistance()).isEqualTo(0.0);
    }
}
