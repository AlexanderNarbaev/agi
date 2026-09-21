package io.matrix.advanced;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CategoricalFunctorTest {

    @Test
    void testCreateFunctor() {
        CategoricalFunctor functor = new CategoricalFunctor(42L);
        assertNotNull(functor);
        assertEquals(1024, functor.getDimension());
    }

    @Test
    void testEncodeVariable() {
        CategoricalFunctor functor = new CategoricalFunctor(42L);
        boolean[] v1 = functor.encodeVariable("A");
        assertEquals(1024, v1.length);

        // Same variable returns same vector (memoized)
        boolean[] v1Again = functor.encodeVariable("A");
        assertArrayEquals(v1, v1Again);
    }

    @Test
    void testDifferentVariablesAreOrthogonal() {
        CategoricalFunctor functor = new CategoricalFunctor(42L);
        boolean[] v1 = functor.encodeVariable("A");
        boolean[] v2 = functor.encodeVariable("B");

        double sim = CategoricalFunctor.cosineSimilarity(v1, v2);
        // Random binary vectors should be ~0 similarity
        assertTrue(Math.abs(sim) < 0.1, "Different variables should be near-orthogonal: " + sim);
    }

    @Test
    void testEncodeRule() {
        CategoricalFunctor functor = new CategoricalFunctor(42L);
        boolean[] rule = functor.encodeRule(Arrays.asList("A", "B"), "C");
        assertEquals(1024, rule.length);
    }

    @Test
    void testCommutativity() {
        CategoricalFunctor functor = new CategoricalFunctor(42L);
        double sim = functor.verifyCommutativity(Arrays.asList("A", "B"), "C");
        // Commutativity should give very high similarity
        assertTrue(sim > 0.99, "Commutativity similarity should be > 0.99: " + sim);
    }

    @Test
    void testXorOperation() {
        boolean[] a = {true, false, true};
        boolean[] b = {false, true, true};
        boolean[] result = CategoricalFunctor.xor(a, b);
        assertArrayEquals(new boolean[]{true, true, false}, result);
    }

    @Test
    void testCosineSimilarity() {
        boolean[] v1 = {true, true, false, false};
        boolean[] v2 = {true, true, false, false};
        assertEquals(1.0, CategoricalFunctor.cosineSimilarity(v1, v2), 0.01);

        boolean[] v3 = {false, false, true, true};
        assertEquals(-1.0, CategoricalFunctor.cosineSimilarity(v1, v3), 0.01);
    }

    @Test
    void testDecodeSymbol() {
        CategoricalFunctor functor = new CategoricalFunctor(42L);
        boolean[] v = functor.encodeVariable("X");
        String decoded = functor.decodeSymbol(v);
        assertEquals("X", decoded);
    }

    @Test
    void testSymbolCount() {
        CategoricalFunctor functor = new CategoricalFunctor(42L);
        assertEquals(0, functor.getSymbolCount());

        functor.encodeVariable("A");
        functor.encodeVariable("B");
        assertEquals(2, functor.getSymbolCount());
    }

    @Test
    void testFunctorComposition() {
        CategoricalFunctor functor = new CategoricalFunctor(42L);
        // F(A AND B → C) and F(A) and F(B) should relate consistently
        boolean[] fullRule = functor.encodeRule(Arrays.asList("A", "B"), "C");
        boolean[] a = functor.encodeVariable("A");
        boolean[] b = functor.encodeVariable("B");
        boolean[] c = functor.encodeVariable("C");

        // XOR(A, B, C) should equal fullRule (by definition of functor)
        boolean[] xorAB = CategoricalFunctor.xor(a, b);
        boolean[] xorABC = CategoricalFunctor.xor(xorAB, c);

        assertArrayEquals(fullRule, xorABC);
    }

    @Test
    void testCustomDimension() {
        CategoricalFunctor functor = new CategoricalFunctor(512, 42L);
        assertEquals(512, functor.getDimension());
        boolean[] v = functor.encodeVariable("A");
        assertEquals(512, v.length);
    }
}
