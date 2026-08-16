package io.matrix.distill;

import io.matrix.bir.Bir;
import io.matrix.bir.TtForm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistillerTest {

    @Test
    void distillSingleOutput() {
        var d = new Distiller(2, 0.5);
        // Teacher: AND gate (both inputs > 0.5)
        d.capture(new long[]{0}, new float[]{0.0f});
        d.capture(new long[]{1}, new float[]{0.0f});
        d.capture(new long[]{2}, new float[]{0.0f});
        d.capture(new long[]{3}, new float[]{1.0f});

        Bir bir = d.synthesize("and-distilled");
        assertNotNull(bir);
        assertEquals(2, bir.inputBits());

        long[] out = new long[1];
        ((TtForm) bir).eval(new long[]{0}, out); assertEquals(0, out[0]);
        ((TtForm) bir).eval(new long[]{3}, out); assertEquals(1, out[0]);
    }

    @Test
    void distillMultiOutput() {
        var d = new Distiller(2, 0.5);
        d.capture(new long[]{0}, new float[]{0.0f, 1.0f});
        d.capture(new long[]{1}, new float[]{1.0f, 0.0f});
        d.capture(new long[]{2}, new float[]{1.0f, 1.0f});
        d.capture(new long[]{3}, new float[]{0.0f, 0.0f});

        Bir bir = d.synthesize("multi-distilled");
        assertNotNull(bir);
    }

    @Test
    void fidelityMeasurement() {
        var d = new Distiller(2, 0.5);
        d.capture(new long[]{0}, new float[]{0.0f});
        d.capture(new long[]{1}, new float[]{0.0f});
        d.capture(new long[]{2}, new float[]{0.0f});
        d.capture(new long[]{3}, new float[]{1.0f});

        Bir bir = d.synthesize("test");
        long[][] testInputs = {{0}, {1}, {2}, {3}};
        float[][] expected = {{0.0f}, {0.0f}, {0.0f}, {1.0f}};
        double fidelity = d.fidelity(bir, testInputs, expected);
        assertEquals(1.0, fidelity, 0.001);
    }

    @Test
    void emptyCaptureThrows() {
        var d = new Distiller(2, 0.5);
        assertThrows(IllegalStateException.class, () -> d.synthesize("empty"));
    }
}
