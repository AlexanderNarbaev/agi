package io.matrix.budgeter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BudgeterHomeostatTest {

    @Test
    void allocateBudget() {
        var bh = new BudgeterHomeostat();
        var result = bh.allocate("row1", 1000, 1024, 5000);
        assertTrue(result.allocated());
        assertTrue(bh.withinBudget("row1"));
        assertTrue(bh.remainingWallTime("row1") > 0);
    }

    @Test
    void budgetExpiry() throws InterruptedException {
        var bh = new BudgeterHomeostat();
        bh.allocate("row1", 1000, 1024, 10); // 10ms
        Thread.sleep(20);
        assertFalse(bh.withinBudget("row1"));
    }

    @Test
    void corridorCorrection() {
        var bh = new BudgeterHomeostat();
        bh.registerCorridor("cpu", 0.0, 100.0);
        var c = bh.updateCorridor("cpu", 150.0); // above max
        assertNotNull(c);
        assertTrue(c.correction() < 0); // negative correction (reduce)
        assertEquals(1, bh.totalCorrections());
    }

    @Test
    void corridorWithinRange() {
        var bh = new BudgeterHomeostat();
        bh.registerCorridor("mem", 0.0, 100.0);
        var c = bh.updateCorridor("mem", 50.0);
        assertNotNull(c);
        assertEquals(0.0, c.correction()); // no correction needed
    }

    @Test
    void multipleCorridors() {
        var bh = new BudgeterHomeostat();
        bh.registerCorridor("a", 0, 100);
        bh.registerCorridor("b", 0, 100);
        bh.updateCorridor("a", 50);
        bh.updateCorridor("b", 150);
        assertEquals(50.0, bh.corridorStatus("a").current());
        assertEquals(150.0, bh.corridorStatus("b").current());
    }
}
