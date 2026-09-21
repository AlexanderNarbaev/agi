package io.matrix.monotone;

import io.matrix.bir.ClauseSetForm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MonotoneDecoderTest {

    @Test
    void decodeMonotoneAnd() {
        // AND gate: f(00)=0, f(01)=0, f(10)=0, f(11)=1 — monotone
        boolean[] tt = {false, false, false, true};
        var decoder = new MonotoneDecoder(2);
        ClauseSetForm cs = decoder.decode(tt, "and-monotone");
        assertEquals(2, cs.inputBits());
        assertEquals(1, cs.clauses().size()); // single clause: x0 AND x1
        long[] out = new long[1];
        cs.eval(new long[]{0}, out); assertEquals(0, out[0]);
        cs.eval(new long[]{3}, out); assertEquals(1, out[0]);
    }

    @Test
    void decodeMonotoneOr() {
        // OR gate: f(00)=0, f(01)=1, f(10)=1, f(11)=1 — monotone
        boolean[] tt = {false, true, true, true};
        var decoder = new MonotoneDecoder(2);
        ClauseSetForm cs = decoder.decode(tt, "or-monotone");
        assertEquals(2, cs.clauses().size()); // x0 and x1 as separate clauses
        long[] out = new long[1];
        cs.eval(new long[]{0}, out); assertEquals(0, out[0]);
        cs.eval(new long[]{1}, out); assertEquals(1, out[0]);
    }

    @Test
    void rejectsNonMonotone() {
        // XOR: f(00)=0, f(01)=1, f(10)=1, f(11)=0 — NOT monotone
        boolean[] tt = {false, true, true, false};
        var decoder = new MonotoneDecoder(2);
        assertThrows(IllegalArgumentException.class, () -> decoder.decode(tt, "xor"));
    }

    @Test
    void decodeConstantOne() {
        // Always 1 — trivially monotone
        boolean[] tt = {true, true, true, true};
        var decoder = new MonotoneDecoder(2);
        ClauseSetForm cs = decoder.decode(tt, "const-one");
        assertEquals(1, cs.clauses().size()); // empty clause (always true)
        long[] out = new long[1];
        cs.eval(new long[]{0}, out); assertEquals(1, out[0]);
    }
}
