package io.matrix.cluster;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyCacheTest {

    @Test
    void shouldRegisterAndRetrieveConnections() {
        var cache = new TopologyCache();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        cache.registerConnection(a, b);
        cache.registerConnection(c, b);

        assertThat(cache.getInputs(b)).containsExactlyInAnyOrder(a, c);
        assertThat(cache.getOutputs(a)).containsExactly(b);
        assertThat(cache.getOutputs(c)).containsExactly(b);
        assertThat(cache.getInputs(a)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnknownNeuron() {
        var cache = new TopologyCache();
        UUID unknown = UUID.randomUUID();

        assertThat(cache.getInputs(unknown)).isEmpty();
        assertThat(cache.getOutputs(unknown)).isEmpty();
    }

    @Test
    void removeNeuronShouldCleanupBothMaps() {
        var cache = new TopologyCache();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        cache.registerConnection(a, b);
        cache.registerConnection(b, c);

        cache.removeNeuron(b);

        assertThat(cache.getInputs(b)).isEmpty();
        assertThat(cache.getOutputs(b)).isEmpty();
        assertThat(cache.getInputs(c)).doesNotContain(b);
        assertThat(cache.getOutputs(a)).doesNotContain(b);
    }

    @Test
    void connectionCountShouldTrackTotal() {
        var cache = new TopologyCache();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        assertThat(cache.connectionCount()).isEqualTo(0);

        cache.registerConnection(a, b);
        assertThat(cache.connectionCount()).isEqualTo(1);

        cache.registerConnection(a, b);
        assertThat(cache.connectionCount()).isEqualTo(2);

        cache.registerConnection(b, a);
        assertThat(cache.connectionCount()).isEqualTo(3);
    }

    @Test
    void clearShouldRemoveEverything() {
        var cache = new TopologyCache();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        cache.registerConnection(a, b);
        assertThat(cache.connectionCount()).isEqualTo(1);

        cache.clear();
        assertThat(cache.connectionCount()).isEqualTo(0);
        assertThat(cache.getInputs(b)).isEmpty();
    }

    @Test
    void groupByTargetShouldPartitionSignals() {
        var cache = new TopologyCache();
        NeuronId target1 = NeuronId.create();
        NeuronId target2 = NeuronId.create();
        NeuronId source = NeuronId.create();

        Signal s1 = new Signal(source, target1, true);
        Signal s2 = new Signal(source, target1, false);
        Signal s3 = new Signal(source, target2, true);

        var grouped = cache.groupByTarget(List.of(s1, s2, s3));

        assertThat(grouped).hasSize(2);
        assertThat(grouped.get(target1.uuid())).hasSize(2);
        assertThat(grouped.get(target2.uuid())).hasSize(1);
    }

    @Test
    void groupByTargetShouldHandleEmptyList() {
        var cache = new TopologyCache();
        var grouped = cache.groupByTarget(List.of());
        assertThat(grouped).isEmpty();
    }

    @Test
    void concurrentAccessShouldBeSafe() throws InterruptedException {
        var cache = new TopologyCache();
        int threads = 10;
        int opsPerThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    for (int j = 0; j < opsPerThread; j++) {
                        UUID from = UUID.randomUUID();
                        UUID to = UUID.randomUUID();
                        cache.registerConnection(from, to);
                        cache.getInputs(to);
                        cache.getOutputs(from);
                        if (j % 10 == 0) {
                            cache.removeNeuron(from);
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(errors.get()).isEqualTo(0);
        assertThat(cache.connectionCount()).isGreaterThanOrEqualTo(0);
    }
}
