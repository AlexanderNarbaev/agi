package io.matrix.federation.liquid;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * W572 — Tests for Conjugate DP Budgeter.
 */
class ConjugateDPBudgeterTest {

    @Test
    void testSimpleQuadraticOptimization() {
        ConjugateDPBudgeter budgeter = new ConjugateDPBudgeter(1, 100, 1e-6, 0.1);
        budgeter.setInitialWeights(new double[]{5.0});

        ConjugateDPBudgeter.OptimizationResult result = budgeter.optimize(
                w -> Math.pow(w[0] + 1, 2),
                w -> new double[]{2 * (w[0] + 1)}
        );

        assertTrue(result.converged());
        assertEquals(-1.0, result.optimalWeights()[0], 0.01);
    }

    @Test
    void testMultiDimensionalOptimization() {
        ConjugateDPBudgeter budgeter = new ConjugateDPBudgeter(2, 200, 1e-6, 0.1);
        budgeter.setInitialWeights(new double[]{0.0, 0.0});

        ConjugateDPBudgeter.OptimizationResult result = budgeter.optimize(
                w -> Math.pow(w[0] - 2, 2) + Math.pow(w[1] - 3, 2),
                w -> new double[]{2 * (w[0] - 2), 2 * (w[1] - 3)}
        );

        assertTrue(result.converged());
        assertEquals(2.0, result.optimalWeights()[0], 0.01);
        assertEquals(3.0, result.optimalWeights()[1], 0.01);
    }

    @Test
    void testMaxIterationsLimit() {
        ConjugateDPBudgeter budgeter = new ConjugateDPBudgeter(1, 5, 1e-10, 0.001);
        budgeter.setInitialWeights(new double[]{100.0});

        ConjugateDPBudgeter.OptimizationResult result = budgeter.optimize(
                w -> w[0] * w[0],
                w -> new double[]{2 * w[0]}
        );

        assertFalse(result.converged());
        assertEquals(5, result.iterations());
    }

    @Test
    void testGetCurrentWeights() {
        ConjugateDPBudgeter budgeter = new ConjugateDPBudgeter(3, 100, 1e-6, 0.1);
        budgeter.setInitialWeights(new double[]{1.0, 2.0, 3.0});

        double[] weights = budgeter.getCurrentWeights();
        assertEquals(3, weights.length);
        assertEquals(1.0, weights[0]);
        assertEquals(2.0, weights[1]);
        assertEquals(3.0, weights[2]);
    }
}
