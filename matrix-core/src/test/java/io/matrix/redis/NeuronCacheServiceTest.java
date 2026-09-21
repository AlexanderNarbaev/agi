package io.matrix.redis;

import io.lettuce.core.RedisClient;
import io.matrix.cluster.NeuronId;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NeuronCacheServiceTest {

    private static RedisClient redisClient;
    private static NeuronCacheService service;

    @BeforeAll
    static void setUp() {
        try {
            redisClient = RedisClient.create("redis://localhost:6379");
            redisClient.connect().sync().ping();
            service = new NeuronCacheService(redisClient);
        } catch (Exception e) {
            assumeTrue(false, "Redis not available on localhost:6379 — skipping integration tests");
        }
    }

    @AfterEach
    void tearDown() {
        if (service != null && testNeuronId != null) {
            service.invalidateNeuron(testNeuronId);
        }
    }

    private final NeuronId testNeuronId = NeuronId.create();

    @Test
    void shouldCacheAndRetrieveNeuron() {
        TruthTable table = TruthTable.fromLong(4, 0b1010101010101010L);
        service.cacheNeuron(testNeuronId, table);

        var retrieved = service.getNeuron(testNeuronId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().k()).isEqualTo(4);
        assertThat(retrieved.get().evaluate(0)).isFalse();
        assertThat(retrieved.get().evaluate(1)).isTrue();
    }

    @Test
    void shouldReturnEmptyForUnknownNeuron() {
        NeuronId unknown = NeuronId.create();
        var result = service.getNeuron(unknown);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldInvalidateNeuron() {
        TruthTable table = TruthTable.fromLong(3, 0xFFL);
        service.cacheNeuron(testNeuronId, table);

        service.invalidateNeuron(testNeuronId);

        var result = service.getNeuron(testNeuronId);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldCacheAndRetrieveBrainState() {
        service.cacheBrainState("agent-1", 42L, "MOVE_FORWARD");

        var state = service.getBrainState("agent-1");
        assertThat(state).isPresent();
        assertThat(state.get()).isEqualTo("42|MOVE_FORWARD");
    }

    @Test
    void shouldReturnEmptyForUnknownBrainState() {
        var result = service.getBrainState("nonexistent-agent");
        assertThat(result).isEmpty();
    }

    @Test
    void shouldOverwriteExistingCacheEntry() {
        TruthTable first = TruthTable.fromLong(2, 0b0101L);
        TruthTable second = TruthTable.fromLong(2, 0b1010L);

        service.cacheNeuron(testNeuronId, first);
        service.cacheNeuron(testNeuronId, second);

        var retrieved = service.getNeuron(testNeuronId);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().evaluate(0)).isFalse();
        assertThat(retrieved.get().evaluate(1)).isTrue();
        assertThat(retrieved.get().evaluate(2)).isFalse();
        assertThat(retrieved.get().evaluate(3)).isTrue();
    }

    @Test
    void shouldSerializeAndDeserializeRoundTrip() {
        BitSet bits = new BitSet(16);
        bits.set(0); bits.set(3); bits.set(7); bits.set(15);
        TruthTable table = TruthTable.of(4, bits);

        service.cacheNeuron(testNeuronId, table);
        var retrieved = service.getNeuron(testNeuronId);

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().k()).isEqualTo(4);
        for (int i = 0; i < 16; i++) {
            assertThat(retrieved.get().evaluate(i))
                    .as("bit " + i)
                    .isEqualTo(bits.get(i));
        }
    }
}
