package io.matrix.bir;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BirTest {

    @Test
    void ttFormEval() {
        // AND gate: k=2, table = [0,0,0,1]
        long[] table = {0b1000L}; // bit 3 = 1
        var tt = new TtForm(2, table, "test", 1.0);
        long[] out = new long[1];
        tt.eval(new long[]{0}, out); assertEquals(0, out[0]);
        tt.eval(new long[]{1}, out); assertEquals(0, out[0]);
        tt.eval(new long[]{2}, out); assertEquals(0, out[0]);
        tt.eval(new long[]{3}, out); assertEquals(1, out[0]);
    }

    @Test
    void clauseSetFormEval() {
        // Clause: x0 AND NOT x1
        long[] pos = {1L}; // x0 = 1
        long[] neg = {2L}; // x1 = 0
        var clause = new ClauseSetForm.Clause(pos, neg);
        var cs = new ClauseSetForm(2, java.util.List.of(clause), "test", 1.0);
        long[] out = new long[1];
        cs.eval(new long[]{1}, out); assertEquals(1, out[0]); // x0=1,x1=0 → fires
        cs.eval(new long[]{3}, out); assertEquals(0, out[0]); // x1=1 → no fire
    }

    @Test
    void bddFormEval() {
        // Simple BDD: if x0 then 1 else 0
        var builder = new BddForm.Builder();
        int root = builder.mk(0, 0, 1); // if x0=0 → 0, if x0=1 → 1
        var bdd = builder.build(1, "test", root);
        long[] out = new long[1];
        bdd.eval(new long[]{0}, out); assertEquals(0, out[0]);
        bdd.eval(new long[]{1}, out); assertEquals(1, out[0]);
    }

    @Test
    void ttToBddConversion() {
        // AND gate
        long[] table = {0b1000L};
        var tt = new TtForm(2, table, "test", 1.0);
        var bdd = BirCompiler.ttToBdd(tt);
        long[] out = new long[1];
        bdd.eval(new long[]{0}, out); assertEquals(0, out[0]);
        bdd.eval(new long[]{3}, out); assertEquals(1, out[0]);
    }

    @Test
    void bddToTtConversion() {
        // if x0 then 1 else 0
        var builder = new BddForm.Builder();
        int root = builder.mk(0, 0, 1);
        var bdd = builder.build(1, "test", root);
        var tt = BirCompiler.bddToTt(bdd);
        long[] out = new long[1];
        tt.eval(new long[]{0}, out); assertEquals(0, out[0]);
        tt.eval(new long[]{1}, out); assertEquals(1, out[0]);
    }

    @Test
    void booleanRuntimeEvaluate() {
        long[] table = {0b1000L};
        var tt = new TtForm(2, table, "test", 1.0);
        long[] out = BooleanRuntime.evaluate(tt, new long[]{3});
        assertEquals(1, out[0]);
    }

    @Test
    void booleanRuntimeEquivalent() {
        long[] table = {0b1000L};
        var tt1 = new TtForm(2, table, "test1", 1.0);
        var tt2 = new TtForm(2, table, "test2", 1.0);
        assertTrue(BooleanRuntime.equivalent(tt1, tt2));
    }
}
