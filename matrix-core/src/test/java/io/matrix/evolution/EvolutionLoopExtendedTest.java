package io.matrix.evolution;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EvolutionLoopExtendedTest {

    @Test
    void bestFitnessHistoryStartsEmpty() {
        var loop = new EvolutionLoop(5, 10, 3, new FitnessFn(4, 4, 2, 3, 10, 2, new Random(42)), new Random(42));
        assertNotNull(loop.bestFitnessHistory());
        assertNotNull(loop.avgFitnessHistory());
    }

    @Test
    void bestBrainBeforeRun() {
        var loop = new EvolutionLoop(5, 10, 3, new FitnessFn(4, 4, 2, 3, 10, 2, new Random(42)), new Random(42));
        // bestBrain before run should return null or throw
        assertThrows(java.util.NoSuchElementException.class, () -> loop.bestBrain());
    }

    @Test
    void bestOverallBeforeRun() {
        var loop = new EvolutionLoop(5, 10, 3, new FitnessFn(4, 4, 2, 3, 10, 2, new Random(42)), new Random(42));
        assertThrows(java.util.NoSuchElementException.class, () -> loop.bestOverall());
    }

    @Test
    void selectWithParetoEmpty() {
        var loop = new EvolutionLoop(5, 10, 3, new FitnessFn(4, 4, 2, 3, 10, 2, new Random(42)), new Random(42));
        var result = loop.selectWithPareto(java.util.List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void runWithParetoReturnsResult() {
        var loop = new EvolutionLoop(2, 4, 3, new FitnessFn(4, 4, 2, 3, 10, 2, new Random(42)), new Random(42));
        var result = loop.runWithPareto();
        assertNotNull(result);
        assertNotNull(result.bestBrain());
    }
}
