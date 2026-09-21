package io.matrix.pilot;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 166 — PilotGridWorld unit tests. */
class PilotGridWorldTest {

    @Test
    void genomeSizeIs20() {
        assertThat(PilotGridWorld.GENOME_BITS).isEqualTo(20);
        // K_MAX=20 — exactly at boundary
    }

    @Test
    void randomGenomeHas20Bits() {
        PilotGridWorld.Genome g = PilotGridWorld.randomGenome(42L);
        assertThat(g.bits()).hasSize(20);
    }

    @Test
    void deterministicForSameSeed() {
        PilotGridWorld.Genome a = PilotGridWorld.randomGenome(42L);
        PilotGridWorld.Genome b = PilotGridWorld.randomGenome(42L);
        assertThat(a.bits()).isEqualTo(b.bits());
    }

    @Test
    void differentSeedsProduceDifferentGenomes() {
        PilotGridWorld.Genome a = PilotGridWorld.randomGenome(1L);
        PilotGridWorld.Genome b = PilotGridWorld.randomGenome(2L);
        assertThat(a.bits()).isNotEqualTo(b.bits());
    }

    @Test
    void decodeProducesValidAgent() {
        PilotGridWorld.Genome g = PilotGridWorld.randomGenome(0L);
        PilotGridWorld.Agent a = PilotGridWorld.decode(g);
        assertThat(a.weights()).hasDimensions(4, 4);
        assertThat(a.thresholds()).hasSize(4);
    }

    @Test
    void decideReturnsValidAction() {
        PilotGridWorld.Genome g = PilotGridWorld.randomGenome(0L);
        PilotGridWorld.Agent a = PilotGridWorld.decode(g);
        int[] sensors = {1, 0, 0, 0}; // N cell is interesting
        int action = PilotGridWorld.decide(a, sensors);
        // First action (N) should win because sensor N=1
        assertThat(action).isZero();
    }

    @Test
    void decideReturnsValidActionIndices() {
        PilotGridWorld.Genome g = PilotGridWorld.randomGenome(0L);
        PilotGridWorld.Agent a = PilotGridWorld.decode(g);
        Random rng = new Random(123L);
        for (int i = 0; i < 50; i++) {
            int[] sensors = {rng.nextInt(2), rng.nextInt(2), rng.nextInt(2), rng.nextInt(2)};
            int action = PilotGridWorld.decide(a, sensors);
            assertThat(action).isBetween(0, 3);
        }
    }

    @Test
    void fitnessSumsRewards() {
        List<Double> rewards = List.of(1.0, 2.0, 3.0, 4.0);
        assertThat(PilotGridWorld.fitness(rewards)).isEqualTo(10.0);
    }

    @Test
    void evolveKeepsPopulationSize() {
        List<PilotGridWorld.Genome> pop = new ArrayList<>();
        List<Double> fits = new ArrayList<>();
        Random rng = new Random(0L);
        for (int i = 0; i < 20; i++) {
            pop.add(PilotGridWorld.randomGenome(rng.nextLong()));
            fits.add(rng.nextDouble());
        }
        List<PilotGridWorld.Genome> next = PilotGridWorld.evolve(pop, fits, 100L);
        assertThat(next).hasSize(20);
    }

    @Test
    void evolveIsDeterministic() {
        List<PilotGridWorld.Genome> pop = new ArrayList<>();
        List<Double> fits = new ArrayList<>();
        Random rng = new Random(0L);
        for (int i = 0; i < 10; i++) {
            pop.add(PilotGridWorld.randomGenome(rng.nextLong()));
            fits.add(rng.nextDouble());
        }
        List<PilotGridWorld.Genome> a = PilotGridWorld.evolve(pop, fits, 100L);
        List<PilotGridWorld.Genome> b = PilotGridWorld.evolve(pop, fits, 100L);
        // Compare bit-by-bit
        for (int i = 0; i < a.size(); i++) {
            assertThat(a.get(i).bits()).isEqualTo(b.get(i).bits());
        }
    }

    @Test
    void evolvePreservesBestHalf() {
        // Best fitness agent should survive
        PilotGridWorld.Genome best = new PilotGridWorld.Genome(new boolean[20]);
        for (int i = 0; i < 20; i++) best.bits()[i] = true;
        List<PilotGridWorld.Genome> pop = new ArrayList<>();
        List<Double> fits = new ArrayList<>();
        pop.add(best);
        fits.add(100.0); // super fit
        for (int i = 0; i < 9; i++) {
            pop.add(PilotGridWorld.randomGenome(i));
            fits.add((double) i);
        }
        List<PilotGridWorld.Genome> next = PilotGridWorld.evolve(pop, fits, 100L);
        // The best should survive in the first half (elitism)
        boolean bestFound = false;
        for (int i = 0; i < next.size() / 2; i++) {
            if (java.util.Arrays.equals(next.get(i).bits(), best.bits())) {
                bestFound = true;
                break;
            }
        }
        assertThat(bestFound).isTrue();
    }

    @Test
    void genomeHasDecodableBits() {
        PilotGridWorld.Genome g = PilotGridWorld.randomGenome(0L);
        assertThat(g.bits()).isNotNull();
        assertThat(g.bits().length).isEqualTo(20);
    }
}
