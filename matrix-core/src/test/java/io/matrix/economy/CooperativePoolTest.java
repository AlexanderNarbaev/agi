package io.matrix.economy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CooperativePoolTest {

    @Test
    void shouldBeEmptyInitially() {
        CooperativePool pool = new CooperativePool();

        assertThat(pool.contributorCount()).isEqualTo(0);
        assertThat(pool.totalCpuAvailable()).isEqualTo(0);
    }

    @Test
    void shouldContributeResources() {
        CooperativePool pool = new CooperativePool();

        double credits = pool.contribute("i1", 4.0, 16.0, 100.0);

        assertThat(credits).isGreaterThan(0);
        assertThat(pool.totalCpuAvailable()).isEqualTo(4.0);
        assertThat(pool.totalMemoryAvailable()).isEqualTo(16.0);
        assertThat(pool.contributorCount()).isEqualTo(1);
    }

    @Test
    void shouldAllocateResources() {
        CooperativePool pool = new CooperativePool();
        pool.contribute("i1", 8.0, 32.0, 200.0);

        var allocation = pool.allocate("consumer-1", 4.0, 16.0, 100.0);

        assertThat(allocation).isNotNull();
        assertThat(allocation.consumerId()).isEqualTo("consumer-1");
        assertThat(allocation.creditCost()).isGreaterThan(0);
    }

    @Test
    void shouldRejectOverallocation() {
        CooperativePool pool = new CooperativePool();
        pool.contribute("i1", 2.0, 8.0, 50.0);

        var allocation = pool.allocate("consumer-1", 10.0, 32.0, 100.0);

        assertThat(allocation).isNull();
    }

    @Test
    void shouldTrackAllocations() {
        CooperativePool pool = new CooperativePool();
        pool.contribute("i1", 10.0, 64.0, 500.0);
        pool.allocate("c1", 3.0, 16.0, 100.0);

        assertThat(pool.allocations()).hasSize(1);
    }

    @Test
    void shouldCalculateCreditsCorrectly() {
        CooperativePool pool = new CooperativePool();

        double credits = pool.contribute("i1", 2.0, 4.0, 10.0);

        double expected = 2.0 * 5.0 + 4.0 * 2.0 + 10.0 * 0.5;
        assertThat(credits).isEqualTo(expected);
    }
}
