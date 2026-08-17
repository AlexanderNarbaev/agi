package io.matrix.cauldron;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H-018 GUHA-style candidate generator and H-019 Levin schedule tests.
 */
class GuhaAndLevinTest {

    @Test
    void guhaGenerateCandidates() {
        List<String> atoms = List.of("a", "b", "c", "d");
        GuhaCandidateGenerator gen = new GuhaCandidateGenerator(atoms, 2, 42L);

        // Create examples with patterns
        List<Set<String>> examples = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Set<String> ex = new HashSet<>();
            ex.add("a");
            ex.add("b");
            if (i % 2 == 0) ex.add("c");
            if (i % 3 == 0) ex.add("d");
            examples.add(ex);
        }

        List<GuhaCandidateGenerator.CandidateRule> candidates = gen.generate(examples, 100);
        assertFalse(candidates.isEmpty());

        // Verify candidates have proper structure
        for (var c : candidates) {
            assertNotNull(c.antecedent());
            assertNotNull(c.succedent());
            assertTrue(c.support() > 0);
            assertTrue(c.confidence() >= 0.8);
        }
    }

    @Test
    void levinScheduleBudgetAllocation() {
        List<String> atoms = List.of("a", "b", "c");
        GuhaCandidateGenerator gen = new GuhaCandidateGenerator(atoms, 2, 42L);
        List<Set<String>> examples = List.of(
                Set.of("a", "b"), Set.of("a", "c"), Set.of("a", "b", "c"),
                Set.of("a", "b"), Set.of("a", "c"), Set.of("a", "b", "c"),
                Set.of("a", "b"), Set.of("a", "c"), Set.of("a", "b", "c")
        );
        List<GuhaCandidateGenerator.CandidateRule> candidates = gen.generate(examples, 50);

        LevinSchedule levin = new LevinSchedule(42L);
        List<LevinSchedule.ScheduledCandidate> scheduled = levin.schedule(candidates);

        assertFalse(scheduled.isEmpty());

        // Verify total budget = 1
        double totalBudget = 0;
        for (var s : scheduled) totalBudget += s.budgetFraction();
        assertEquals(1.0, totalBudget, 0.001);

        // Verify sorted by fraction descending
        for (int i = 1; i < scheduled.size(); i++) {
            double prev = scheduled.get(i - 1).budgetFraction();
            double curr = scheduled.get(i).budgetFraction();
            assertTrue(prev >= curr, "Schedule should be sorted descending");
        }
    }

    @Test
    void coverageMetric() {
        List<String> atoms = List.of("x", "y");
        GuhaCandidateGenerator gen = new GuhaCandidateGenerator(atoms, 1, 42L);
        List<Set<String>> examples = List.of(
                Set.of("x", "y"), Set.of("x", "y"), Set.of("x", "y"),
                Set.of("x", "y"), Set.of("x", "y"), Set.of("x", "y"),
                Set.of("x", "y"), Set.of("x", "y"), Set.of("x", "y"),
                Set.of("x", "y")
        );
        List<GuhaCandidateGenerator.CandidateRule> candidates = gen.generate(examples, 20);

        // Coverage should be calculable
        double cov = gen.coverage(candidates, candidates);
        assertEquals(1.0, cov, 0.01);
    }
}
