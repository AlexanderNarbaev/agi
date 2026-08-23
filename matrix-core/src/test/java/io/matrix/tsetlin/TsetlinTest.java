package io.matrix.tsetlin;

import io.matrix.bir.ClauseSetForm;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class TsetlinTest {

    @Test
    void automatonBasics() {
        var a = new TsetlinAutomaton(10);
        assertFalse(a.action()); // starts at state 10 (exclude side)
        a.penalty();             // canonical penalty: one step toward include
        assertTrue(a.action());  // state 11
        a.reward();              // canonical reward: deepen include side
        assertEquals(12, a.state());
        a.penalize();            // step back toward exclude
        assertEquals(11, a.state());
    }

    @Test
    void automatonMonotonicity() {
        // Boundary saturation
        var top = new TsetlinAutomaton(5, 10);
        top.reward();
        assertEquals(10, top.state());
        var bot = new TsetlinAutomaton(5, 1);
        bot.reward(); // canonical reward deepens EXCLUDE at the floor
        assertEquals(1, bot.state());

        // Canonical one-step Type I row crosses exclude→include on a
        // TRUE-valued excluded literal (penalty toward inclusion).
        var c = new TsetlinAutomaton(5);
        c.feedbackTypeI(true);
        assertTrue(c.action());
    }

    @Test
    void typeI_feedback() {
        var a = new TsetlinAutomaton(5);
        a.feedbackTypeI(true);  // TRUE-valued excluded literal → penalty → include
        assertTrue(a.action());
        a.feedbackTypeI(false); // FALSE-valued included literal → penalty → exclude
        assertFalse(a.action());
    }

    @Test
    void typeII_feedback() {
        var a = new TsetlinAutomaton(5);
        a.reward(); // deepen exclusion? canonical reward deepens EXCLUDE here
        assertFalse(a.action());
        a.includeNow();
        assertTrue(a.action());
        a.feedbackTypeII(true); // present-in-negative & included → drop out
        assertFalse(a.action());
    }

    @Test
    void trainerProducesValidDistilledArtifact() {
        var trainer = new TsetlinTrainer(2, 4, 10, new Random(42));
        long[][] inputs = {{0}, {1}, {2}, {3}};
        boolean[] labels = {false, false, false, true}; // AND
        trainer.trainBatch(inputs, labels, 200);
        ClauseSetForm cs = trainer.toClauseSet("and-gate");
        assertNotNull(cs);
        assertEquals(2, cs.inputBits());
        assertEquals("clauseset", cs.form());
        long[] out = new long[1];
        for (long[] in : inputs) { // artifact must be evaluable everywhere
            cs.eval(in, out);
            assertTrue(out[0] == 0 || out[0] == 1);
        }
    }

    @Test
    void sameSeedSameArtifact() {
        long[][] inputs = {{0}, {1}, {2}, {3}};
        boolean[] labels = {false, true, true, true};
        var a = new TsetlinTrainer(2, 6, 10, new Random(7L));
        var b = new TsetlinTrainer(2, 6, 10, new Random(7L));
        a.trainBatch(inputs, labels, 50);
        b.trainBatch(inputs, labels, 50);
        var ca = a.toClauseSet("s");
        var cb = b.toClauseSet("s");
        assertEquals(ca.clauses().size(), cb.clauses().size());
        for (int i = 0; i < ca.clauses().size(); i++) {
            assertArrayEquals(ca.clauses().get(i).pos, cb.clauses().get(i).pos);
            assertArrayEquals(ca.clauses().get(i).neg, cb.clauses().get(i).neg);
        }
    }
}
