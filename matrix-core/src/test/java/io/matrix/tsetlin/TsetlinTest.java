package io.matrix.tsetlin;

import java.util.Random;

import io.matrix.bir.ClauseSetForm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import static org.assertj.core.api.Assertions.assertThat;

class TsetlinTest {

    @Test
    void automatonStartsExcludedAndWalksArithmetically() {
        var a = new TsetlinAutomaton(10);
        assertEquals(0, a.state());      // deepest exclude
        assertFalse(a.action());
        for (int i = 1; i <= 9; i++) {
            a.inc();
            assertEquals(i, a.state());
            assertFalse(a.includes());   // still below N=10
        }
        a.inc();                          // crosses into include side
        assertTrue(a.action());
        assertEquals(10, a.state());
        a.dec();
        assertFalse(a.action());          // one step back to exclude
    }

    @Test
    void saturationAtBoundaries() {
        var a = new TsetlinAutomaton(5);
        for (int i = 0; i < 50; i++) a.inc();
        assertEquals(10, a.state());      // 2N ceiling
        assertTrue(a.includes());
        var b = new TsetlinAutomaton(5);
        for (int i = 0; i < 50; i++) b.dec();
        assertEquals(0, b.state());       // floor
        assertFalse(b.includes());
    }

    @Test
    void includeNowJumpsToFirstIncludePosition() {
        var a = new TsetlinAutomaton(5);
        assertEquals(0, a.state());
        a.includeNow();
        assertEquals(5, a.state());
        assertTrue(a.includes());
    }

    @Test
    void flatFeedbackDirections() {
        var a = new TsetlinAutomaton(5);
        a.feedbackTypeI(true);   // present → inc toward include
        assertEquals(1, a.state());
        assertFalse(a.includes()); // not yet crossed N=5
        a.feedbackTypeII(false); // absent-in-negative & excluded → step in
        assertEquals(2, a.state());
        var b = new TsetlinAutomaton(5);
        b.feedbackTypeI(true); b.feedbackTypeI(true); b.feedbackTypeI(true);
        b.feedbackTypeI(true); b.feedbackTypeI(true);
        assertTrue(b.includes()); // crossed N after 5 steps
    }

    @Test
    void trainerProducesValidDistilledArtifact() {
        var trainer = new TsetlinTrainer(2, 8, 10, new Random(42L));
        long[][] inputs = {{0}, {1}, {2}, {3}};
        boolean[] labels = {false, false, false, true}; // AND
        trainer.trainBatch(inputs, labels, 300);
        ClauseSetForm cs = trainer.toClauseSet("and-gate");
        assertNotNull(cs);
        assertEquals(2, cs.inputBits());
        long[] out = new long[1];
        int correct = 0;
        for (long[] x : inputs) {
            cs.eval(x, out);
            boolean pred = out[0] == 1L;
            boolean want = x[0] == 3;
            if (pred == want) correct++;
        }
        assertThat(correct).as("AND fit after training").isGreaterThanOrEqualTo(3);
    }

    @Test
    void sameSeedSameArtifact() {
        long[][] inputs = {{0}, {1}, {2}, {3}};
        boolean[] labels = {false, true, true, true};
        var a = new TsetlinTrainer(2, 6, 10, new Random(7L));
        var b = new TsetlinTrainer(2, 6, 10, new Random(7L));
        a.trainBatch(inputs, labels, 100);
        b.trainBatch(inputs, labels, 100);
        var ca = a.toClauseSet("s");
        var cb = b.toClauseSet("s");
        assertEquals(ca.clauses().size(), cb.clauses().size());
        assertThat(a.clauseCount()).isEqualTo(b.clauseCount());
    }
}
