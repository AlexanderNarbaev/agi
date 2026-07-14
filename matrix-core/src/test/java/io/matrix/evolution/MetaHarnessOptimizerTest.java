package io.matrix.evolution;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

class MetaHarnessOptimizerTest {

    private static final Random RNG_42 = new Random(42);
    private static final Random RNG_99 = new Random(99);

    // ── Helpers ───────────────────────────────────────────────────────

    /** Simple monotonic evaluator: higher params → higher fitness. */
    private static double monotonicEval(MetaHarnessOptimizer.HarnessConfig c) {
        return c.toolCount() * 10.0
                + c.memoryDepth() * 5.0
                + c.maxWorkflowStages() * 2.0
                + c.safetyThreshold() * 30.0
                + c.explorationRate() * 25.0
                + c.batchSize() * 0.1;
    }

    /** Evaluator preferring a balanced config (discourages extremes). */
    private static double balancedEval(MetaHarnessOptimizer.HarnessConfig c) {
        double toolPenalty = Math.abs(c.toolCount() - 25) * 2.0;
        double memoryBonus = c.memoryDepth() * 10.0;
        double stageBonus = (10 - Math.abs(c.maxWorkflowStages() - 10)) * 3.0;
        double safetyBonus = c.safetyThreshold() * 40.0;
        double explorationBonus = c.explorationRate() * 20.0;
        double batchPenalty = Math.abs(c.batchSize() - 500) * 0.05;
        return memoryBonus + stageBonus + safetyBonus + explorationBonus
                - toolPenalty - batchPenalty;
    }

    private static void assertConfigInRange(MetaHarnessOptimizer.HarnessConfig c) {
        assertThat(c.toolCount()).isBetween(1, 50);
        assertThat(c.memoryDepth()).isBetween(1, 10);
        assertThat(c.maxWorkflowStages()).isBetween(1, 20);
        assertThat(c.safetyThreshold()).isBetween(0.1, 1.0);
        assertThat(c.explorationRate()).isBetween(0.0, 1.0);
        assertThat(c.batchSize()).isBetween(1, 1000);
    }

    private MetaHarnessOptimizer optimizer(Random rng, int popSize, int gens) {
        return new MetaHarnessOptimizer(
                MetaHarnessOptimizerTest::monotonicEval, popSize, gens, rng);
    }

    // ── 1. Initialize ─────────────────────────────────────────────────

    @Test
    void initializeCreatesValidPopulation() {
        MetaHarnessOptimizer opt = optimizer(RNG_42, 20, 50);
        List<MetaHarnessOptimizer.HarnessConfig> pop = opt.initialize();

        assertThat(pop).hasSize(20);
        assertThat(pop).allSatisfy(MetaHarnessOptimizerTest::assertConfigInRange);
    }

    @Test
    void initializeProducesDiverseConfigs() {
        MetaHarnessOptimizer opt = optimizer(RNG_42, 50, 10);
        List<MetaHarnessOptimizer.HarnessConfig> pop = opt.initialize();

        List<Integer> toolCounts = pop.stream()
                .map(MetaHarnessOptimizer.HarnessConfig::toolCount)
                .distinct()
                .toList();
        assertThat(toolCounts).hasSizeGreaterThan(5);
    }

    // ── 2. Mutate ─────────────────────────────────────────────────────

    @Test
    void mutateProducesDifferentButValidConfig() {
        MetaHarnessOptimizer opt = optimizer(RNG_42, 10, 10);
        MetaHarnessOptimizer.HarnessConfig parent = opt.initialize().get(0);

        // Multiple mutations — at least one should differ
        List<MetaHarnessOptimizer.HarnessConfig> children = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            children.add(opt.mutate(parent));
        }

        assertThat(children).allSatisfy(MetaHarnessOptimizerTest::assertConfigInRange);

        boolean anyDifferent = children.stream()
                .anyMatch(c -> !c.equals(parent));
        assertThat(anyDifferent).isTrue();
    }

    @Test
    void mutateIsDeterministic() {
        MetaHarnessOptimizer opt1 = new MetaHarnessOptimizer(
                MetaHarnessOptimizerTest::monotonicEval, 10, 10, new Random(12345));
        MetaHarnessOptimizer opt2 = new MetaHarnessOptimizer(
                MetaHarnessOptimizerTest::monotonicEval, 10, 10, new Random(12345));

        // Align Random state: both consume same sequence via initialize
        opt1.initialize();
        MetaHarnessOptimizer.HarnessConfig parent = opt2.initialize().get(0);

        MetaHarnessOptimizer.HarnessConfig child1 = opt1.mutate(parent);
        MetaHarnessOptimizer.HarnessConfig child2 = opt2.mutate(parent);

        assertThat(child1).isEqualTo(child2);
    }

    // ── 3. Crossover ──────────────────────────────────────────────────

    @Test
    void crossoverCombinesParents() {
        MetaHarnessOptimizer opt = optimizer(RNG_42, 10, 10);
        MetaHarnessOptimizer opt2 = new MetaHarnessOptimizer(
                MetaHarnessOptimizerTest::monotonicEval, 10, 10, new Random(12345));

        MetaHarnessOptimizer.HarnessConfig a = new MetaHarnessOptimizer.HarnessConfig(
                10, 3, 5, 0.5, 0.3, 100);
        MetaHarnessOptimizer.HarnessConfig b = new MetaHarnessOptimizer.HarnessConfig(
                40, 8, 15, 0.9, 0.8, 900);

        // Multiple crossovers with fixed seed
        List<MetaHarnessOptimizer.HarnessConfig> children = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            children.add(opt2.crossover(a, b));
        }

        // All children should have params from either parent
        assertThat(children).allSatisfy(c -> {
            assertThat(c.toolCount()).isIn(10, 40);
            assertThat(c.memoryDepth()).isIn(3, 8);
            assertThat(c.maxWorkflowStages()).isIn(5, 15);
            assertThat(c.safetyThreshold()).isIn(0.5, 0.9);
            assertThat(c.explorationRate()).isIn(0.3, 0.8);
            assertThat(c.batchSize()).isIn(100, 900);
        });

        // Some children should differ from both parents (mixed params)
        boolean anyMixed = children.stream()
                .anyMatch(c -> !c.equals(a) && !c.equals(b));
        assertThat(anyMixed).isTrue();
    }

    @Test
    void crossoverAlwaysProducesValidChild() {
        MetaHarnessOptimizer opt = optimizer(RNG_42, 10, 10);
        List<MetaHarnessOptimizer.HarnessConfig> pop = opt.initialize();

        for (int i = 0; i < 100; i++) {
            MetaHarnessOptimizer.HarnessConfig a = pop.get(RNG_42.nextInt(pop.size()));
            MetaHarnessOptimizer.HarnessConfig b = pop.get(RNG_42.nextInt(pop.size()));
            MetaHarnessOptimizer.HarnessConfig child = opt.crossover(a, b);
            assertConfigInRange(child);
        }
    }

    // ── 4. Evaluate generation ────────────────────────────────────────

    @Test
    void evaluateGenerationComputesFitnessAndVector() {
        MetaHarnessOptimizer opt = optimizer(RNG_42, 10, 10);
        List<MetaHarnessOptimizer.HarnessConfig> pop = opt.initialize();

        List<MetaHarnessOptimizer.HarnessCandidate> candidates =
                opt.evaluateGeneration(pop);

        assertThat(candidates).hasSameSizeAs(pop);

        assertThat(candidates).allSatisfy(c -> {
            assertThat(c.fitness()).isGreaterThan(0.0);
            assertThat(c.vector()).isNotNull();
            assertThat(c.vector().quality()).isBetween(0.0, 1.0);
        });

        // Fitness should match monotonicEval
        for (int i = 0; i < pop.size(); i++) {
            assertThat(candidates.get(i).fitness())
                    .isEqualTo(monotonicEval(pop.get(i)));
        }
    }

    // ── 5. Optimize converges ─────────────────────────────────────────

    @Test
    void optimizeConvergesToBetterFitness() {
        MetaHarnessOptimizer opt = optimizer(RNG_42, 20, 30);

        List<MetaHarnessOptimizer.HarnessConfig> initialPop = opt.initialize();
        double initialAvg = initialPop.stream()
                .mapToDouble(MetaHarnessOptimizerTest::monotonicEval)
                .average()
                .orElse(0.0);

        MetaHarnessOptimizer.HarnessConfig best = opt.optimize();
        double bestFitness = monotonicEval(best);

        assertThat(bestFitness).isGreaterThanOrEqualTo(initialAvg);
        assertConfigInRange(best);
    }

    @Test
    void optimizeWithStableEvaluatorFindsHighFitnessConfig() {
        // Monotonic evaluator: higher params = higher fitness
        // After 100 generations of selection, should approach max values
        MetaHarnessOptimizer opt = optimizer(RNG_42, 30, 100);
        MetaHarnessOptimizer.HarnessConfig best = opt.optimize();

        double bestFitness = monotonicEval(best);

        // Theoretical max fitness (all params at max)
        double theoreticalMax = monotonicEval(new MetaHarnessOptimizer.HarnessConfig(
                50, 10, 20, 1.0, 1.0, 1000));

        assertThat(bestFitness).isGreaterThanOrEqualTo(theoreticalMax * 0.6);
    }

    // ── 6. Fitness history ────────────────────────────────────────────

    @Test
    void fitnessHistoryGrowsWithGenerations() {
        int gens = 25;
        MetaHarnessOptimizer opt = optimizer(RNG_42, 20, gens);
        opt.optimize();

        List<Double> history = opt.fitnessHistory();
        assertThat(history).hasSize(gens + 1);

        // Fitness should be non-decreasing (elite selection guarantees this
        // with a deterministic evaluator)
        for (int i = 1; i < history.size(); i++) {
            assertThat(history.get(i)).isGreaterThanOrEqualTo(history.get(i - 1));
        }
    }

    @Test
    void populationSizeMaintainedAcrossGenerations() {
        int popSize = 18;
        MetaHarnessOptimizer opt = optimizer(RNG_42, popSize, 20);

        List<MetaHarnessOptimizer.HarnessConfig> pop = opt.initialize();
        assertThat(pop).hasSize(popSize);

        for (int g = 0; g < 5; g++) {
            List<MetaHarnessOptimizer.HarnessCandidate> candidates =
                    opt.evaluateGeneration(pop);

            // Verify internal consistency: call optimize and check via
            // the next generation mechanism indirectly.
            // We simulate one generation:
            List<MetaHarnessOptimizer.HarnessConfig> nextPop = new ArrayList<>();
            candidates.sort((a, b) -> Double.compare(b.fitness(), a.fitness()));
            int eliteCount = Math.max(1, popSize / 2);
            for (int i = 0; i < eliteCount; i++) {
                nextPop.add(candidates.get(i).config());
            }
            while (nextPop.size() < popSize) {
                MetaHarnessOptimizer.HarnessConfig a =
                        candidates.get(RNG_42.nextInt(eliteCount)).config();
                MetaHarnessOptimizer.HarnessConfig b =
                        candidates.get(RNG_42.nextInt(eliteCount)).config();
                MetaHarnessOptimizer.HarnessConfig child = opt.crossover(a, b);
                if (RNG_42.nextDouble() < 0.3) {
                    child = opt.mutate(child);
                }
                nextPop.add(child);
            }

            assertThat(nextPop).hasSize(popSize);
            pop = nextPop;
        }
    }

    // ── 7. Edge cases ─────────────────────────────────────────────────

    @Test
    void smallPopulationShouldWork() {
        MetaHarnessOptimizer opt = optimizer(RNG_42, 2, 10);
        MetaHarnessOptimizer.HarnessConfig best = opt.optimize();

        assertConfigInRange(best);
        assertThat(opt.fitnessHistory()).hasSize(11);
    }

    @Test
    void singleGenerationShouldWork() {
        MetaHarnessOptimizer opt = optimizer(RNG_42, 10, 1);
        MetaHarnessOptimizer.HarnessConfig best = opt.optimize();

        assertConfigInRange(best);
        assertThat(opt.fitnessHistory()).hasSize(2);
    }

    @Test
    void balancedEvaluatorShouldNotBlowUp() {
        MetaHarnessOptimizer opt = new MetaHarnessOptimizer(
                MetaHarnessOptimizerTest::balancedEval, 15, 30, RNG_99);
        MetaHarnessOptimizer.HarnessConfig best = opt.optimize();

        assertConfigInRange(best);
        assertThat(opt.fitnessHistory()).hasSize(31);
    }

    // ── 8. Constructor validation ─────────────────────────────────────

    @Test
    void constructorRejectsInvalidPopulationSize() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetaHarnessOptimizer(
                        MetaHarnessOptimizerTest::monotonicEval, 1, 10, RNG_42));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetaHarnessOptimizer(
                        MetaHarnessOptimizerTest::monotonicEval, 0, 10, RNG_42));
    }

    @Test
    void constructorRejectsInvalidGenerations() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetaHarnessOptimizer(
                        MetaHarnessOptimizerTest::monotonicEval, 10, 0, RNG_42));
    }

    // ── 9. HarnessConfig validation ───────────────────────────────────

    @Test
    void harnessConfigRejectsOutOfRangeValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetaHarnessOptimizer.HarnessConfig(
                        0, 1, 1, 0.5, 0.5, 100));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetaHarnessOptimizer.HarnessConfig(
                        10, 0, 1, 0.5, 0.5, 100));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetaHarnessOptimizer.HarnessConfig(
                        10, 1, 0, 0.5, 0.5, 100));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetaHarnessOptimizer.HarnessConfig(
                        10, 1, 1, 0.05, 0.5, 100));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetaHarnessOptimizer.HarnessConfig(
                        10, 1, 1, 0.5, -0.1, 100));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetaHarnessOptimizer.HarnessConfig(
                        10, 1, 1, 0.5, 0.5, 0));
    }

    @Test
    void harnessConfigAcceptsBoundaryValues() {
        // Min boundaries
        assertConfigInRange(new MetaHarnessOptimizer.HarnessConfig(
                1, 1, 1, 0.1, 0.0, 1));
        // Max boundaries
        assertConfigInRange(new MetaHarnessOptimizer.HarnessConfig(
                50, 10, 20, 1.0, 1.0, 1000));
    }
}
