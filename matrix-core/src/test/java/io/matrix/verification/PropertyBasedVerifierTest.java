package io.matrix.verification;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class PropertyBasedVerifierTest {

    private final PropertyBasedVerifier verifier = new PropertyBasedVerifier();

    @Test
    void binaryOutputAcceptsValid() {
        assertTrue(verifier.verifyBinaryOutput(new int[]{0, 1, 1, 0, 1}));
    }

    @Test
    void binaryOutputRejectsInvalid() {
        assertFalse(verifier.verifyBinaryOutput(new int[]{0, 1, 2, 0}));
    }

    @Test
    void binaryOutputEmptyArray() {
        assertTrue(verifier.verifyBinaryOutput(new int[]{}));
    }

    @Test
    void monotonicFitnessAccepts() {
        double[] history = {0.1, 0.2, 0.3, 0.4};
        assertTrue(verifier.verifyMonotonicFitness(history));
    }

    @Test
    void monotonicFitnessRejectsDecrease() {
        double[] history = {0.4, 0.3, 0.5};
        assertFalse(verifier.verifyMonotonicFitness(history));
    }

    @Test
    void monotonicFitnessEqualValues() {
        double[] history = {0.3, 0.3, 0.3};
        assertTrue(verifier.verifyMonotonicFitness(history));
    }

    @Test
    void frozenImmutabilityAcceptsEqual() {
        Set<String> before = Set.of("A", "B", "C");
        Set<String> after = Set.of("A", "B", "C");
        assertTrue(verifier.verifyFrozenImmutability(before, after));
    }

    @Test
    void frozenImmutabilityRejectsDifferent() {
        Set<String> before = Set.of("A", "B", "C");
        Set<String> after = Set.of("A", "B", "D");
        assertFalse(verifier.verifyFrozenImmutability(before, after));
    }

    @Test
    void consensusSafetyAcceptsSameValues() {
        Map<Integer, List<String>> rounds = Map.of(1, List.of("A", "A", "A"));
        assertTrue(verifier.verifyConsensusSafety(rounds));
    }

    @Test
    void consensusSafetyRejectsDifferentValues() {
        Map<Integer, List<String>> rounds = Map.of(1, List.of("A", "B", "A"));
        assertFalse(verifier.verifyConsensusSafety(rounds));
    }

    @Test
    void generateRandomBooleanLength() {
        var rng = new Random(42);
        boolean[] result = verifier.generateRandomBoolean(100, rng);
        assertEquals(100, result.length);
    }

    @Test
    void generateMonotonicFitnessIsMonotonic() {
        var rng = new Random(42);
        double[] history = verifier.generateMonotonicFitness(10, rng);
        assertTrue(verifier.verifyMonotonicFitness(history));
    }

    @Test
    void generateMonotonicFitnessLength() {
        var rng = new Random(42);
        double[] history = verifier.generateMonotonicFitness(20, rng);
        assertEquals(20, history.length);
    }
}
