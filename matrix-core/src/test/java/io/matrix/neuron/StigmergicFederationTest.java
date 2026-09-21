package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StigmergicFederationTest {

    @Test
    void depositIncreasesPheromone() {
        StigmergicFederation fed = new StigmergicFederation(0.1, 1.0, new Random(1));
        fed.deposit("brain_0", 1.0);
        assertThat(fed.strength("brain_0")).isEqualTo(1.0);
        fed.deposit("brain_0", 0.5);
        assertThat(fed.strength("brain_0")).isEqualTo(1.5);
    }

    @Test
    void evaporateReducesPheromone() {
        StigmergicFederation fed = new StigmergicFederation(0.5, 1.0, new Random(1));
        fed.deposit("X", 2.0);
        fed.evaporate();
        // After 50% evaporation: 2.0 * 0.5 = 1.0
        assertThat(fed.strength("X")).isEqualTo(1.0);
    }

    @Test
    void evaporateRemovesWeakPheromones() {
        StigmergicFederation fed = new StigmergicFederation(0.99, 1.0, new Random(1));
        fed.deposit("weak", 0.001);
        fed.evaporate();
        // After 99% evaporation: 0.001 * 0.01 = 0.00001, below 0.001 threshold
        assertThat(fed.size()).isEqualTo(0);
    }

    @Test
    void sampleLocationBiasesTowardsHighPheromone() {
        StigmergicFederation fed = new StigmergicFederation(0.1, 1.0, new Random(1));
        // High pheromone on "A", low on "B"
        fed.deposit("A", 100.0);
        fed.deposit("B", 0.001);
        String[] choices = {"A", "B"};
        // Sample many times, A should dominate
        int aCount = 0;
        for (int i = 0; i < 100; i++) {
            if ("A".equals(fed.sampleLocation(choices))) aCount++;
        }
        assertThat(aCount).isGreaterThan(80); // should be ~99%
    }

    @Test
    void rejectsBadInputs() {
        assertThatThrownBy(() -> new StigmergicFederation(-0.1, 1.0, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StigmergicFederation(0.1, 0.0, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StigmergicFederation(0.1, 1.0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullLocation() {
        StigmergicFederation fed = new StigmergicFederation(0.1, 1.0, new Random(1));
        assertThatThrownBy(() -> fed.deposit(null, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBadDepositAmount() {
        StigmergicFederation fed = new StigmergicFederation(0.1, 1.0, new Random(1));
        assertThatThrownBy(() -> fed.deposit("X", 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fed.deposit("X", -1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sampleLocationNullOrEmpty() {
        StigmergicFederation fed = new StigmergicFederation(0.1, 1.0, new Random(1));
        assertThat(fed.sampleLocation(null)).isNull();
        assertThat(fed.sampleLocation(new String[0])).isNull();
    }

    @Test
    void clearRemovesAll() {
        StigmergicFederation fed = new StigmergicFederation(0.1, 1.0, new Random(1));
        fed.deposit("A", 1.0);
        fed.deposit("B", 2.0);
        assertThat(fed.size()).isEqualTo(2);
        fed.clear();
        assertThat(fed.size()).isEqualTo(0);
    }

    @Test
    void strengthReturnsZeroForUnknownLocation() {
        StigmergicFederation fed = new StigmergicFederation(0.1, 1.0, new Random(1));
        assertThat(fed.strength("unknown")).isEqualTo(0.0);
    }
}
