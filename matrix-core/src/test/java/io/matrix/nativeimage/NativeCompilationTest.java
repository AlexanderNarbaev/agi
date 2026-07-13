package io.matrix.nativeimage;

import io.matrix.cluster.ClusterConfig;
import io.matrix.cluster.NeuronClusterActor;
import io.matrix.cluster.NeuronId;
import io.matrix.cluster.Signal;
import io.matrix.neuron.NeuronLayer;
import io.matrix.neuron.TruthTable;
import io.matrix.reasoning.BrcChain;
import io.matrix.rag.BooleanIndex;
import io.matrix.rag.BooleanRag;
import io.matrix.compression.BooleanCompressor;
import io.matrix.compression.SimdEvaluator;
import io.matrix.mcts.MctsTree;
import io.matrix.agent.AgentLoop;
import io.matrix.snapshot.ClusterSnapshot;
import io.matrix.snapshot.SnapshotStore;
import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;
import io.matrix.consensus.ConsensusEngine;
import io.matrix.consensus.Proposal;
import io.matrix.consensus.Vote;
import io.matrix.consensus.ConsensusLevel;
import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import io.matrix.memory.MemoryHierarchy;
import io.matrix.observability.MatrixMetrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraalVM Native Image compilation validation tests.
 *
 * <p>These tests run in JVM mode to validate that all classes required for
 * native compilation are properly configured in reflection/resource configs.
 * When running in native mode (via {@code nativeTest}), they verify the
 * native binary works correctly.
 *
 * <p>Run JVM mode:   {@code ./gradlew :matrix-core:test --tests "io.matrix.nativeimage.*"}
 * <p>Run native mode: {@code ./gradlew :matrix-core:testNative -Dquarkus.native.enabled=true}
 */
@Tag("native-compilation")
class NativeCompilationTest {

    // ─── 1. Reflection config validation ───

    @Test
    void neuronLayerShouldBeInstantiableViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.neuron.NeuronLayer");
        assertThat(clazz).isNotNull();
        assertThat(clazz.getDeclaredConstructors()).isNotEmpty();
    }

    @Test
    void truthTableShouldBeInstantiableViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.neuron.TruthTable");
        assertThat(clazz).isNotNull();
        assertThat(clazz.getDeclaredConstructors()).isNotEmpty();
    }

    @Test
    void brcChainShouldBeInstantiableViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.reasoning.BrcChain");
        assertThat(clazz).isNotNull();
        assertThat(clazz.getDeclaredConstructors()).isNotEmpty();
    }

    @Test
    void booleanIndexShouldBeInstantiableViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.rag.BooleanIndex");
        assertThat(clazz).isNotNull();
        assertThat(clazz.getDeclaredConstructors()).isNotEmpty();
    }

    @Test
    void booleanRagShouldBeInstantiableViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.rag.BooleanRag");
        assertThat(clazz).isNotNull();
        assertThat(clazz.getDeclaredConstructors()).isNotEmpty();
    }

    @Test
    void mctsTreeShouldBeInstantiableViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.mcts.MctsTree");
        assertThat(clazz).isNotNull();
        assertThat(clazz.getDeclaredConstructors()).isNotEmpty();
    }

    @Test
    void agentLoopShouldBeInstantiableViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.agent.AgentLoop");
        assertThat(clazz).isNotNull();
        assertThat(clazz.getDeclaredConstructors()).isNotEmpty();
    }

    @Test
    void consensusEngineShouldBeInstantiableViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.consensus.ConsensusEngine");
        assertThat(clazz).isNotNull();
        assertThat(clazz.getDeclaredConstructors()).isNotEmpty();
    }

    @Test
    void ethicalFilterShouldBeInstantiableViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.ethics.EthicalFilter");
        assertThat(clazz).isNotNull();
        assertThat(clazz.getDeclaredConstructors()).isNotEmpty();
    }

    @Test
    void memoryHierarchyShouldBeInstantiableViaReflection() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.memory.MemoryHierarchy");
        assertThat(clazz).isNotNull();
        assertThat(clazz.getDeclaredConstructors()).isNotEmpty();
    }

    // ─── 2. Pekko actor inner classes ───

    @Test
    void neuronClusterActorCommandShouldBeAccessible() throws Exception {
        Class<?> commandClass = Class.forName("io.matrix.cluster.NeuronClusterActor$Command");
        assertThat(commandClass).isNotNull();
        assertThat(commandClass.isInterface()).isTrue();
    }

    @Test
    void neuronClusterActorResponseShouldBeAccessible() throws Exception {
        Class<?> responseClass = Class.forName("io.matrix.cluster.NeuronClusterActor$Response");
        assertThat(responseClass).isNotNull();
        assertThat(responseClass.isInterface()).isTrue();
    }

    @Test
    void neuronClusterActorLoadNeuronShouldBeAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.cluster.NeuronClusterActor$LoadNeuron");
        assertThat(clazz).isNotNull();
        assertThat(clazz.getRecordComponents()).isNotEmpty();
    }

    @Test
    void neuronClusterActorInjectSignalShouldBeAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.cluster.NeuronClusterActor$InjectSignal");
        assertThat(clazz).isNotNull();
        assertThat(clazz.getRecordComponents()).isNotEmpty();
    }

    @Test
    void neuronClusterActorTickResultShouldBeAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.cluster.NeuronClusterActor$TickResult");
        assertThat(clazz).isNotNull();
        assertThat(clazz.getRecordComponents()).isNotEmpty();
    }

    @Test
    void clusterMediatorCommandShouldBeAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.mediator.hierarchy.ClusterMediator$Command");
        assertThat(clazz).isNotNull();
        assertThat(clazz.isInterface()).isTrue();
    }

    @Test
    void clusterMediatorResponseShouldBeAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.mediator.hierarchy.ClusterMediator$Response");
        assertThat(clazz).isNotNull();
        assertThat(clazz.isInterface()).isTrue();
    }

    // ─── 3. Serialization config validation ───

    @Test
    void jacksonSerializerShouldBeAccessible() throws Exception {
        Class<?> clazz = Class.forName("org.apache.pekko.serialization.jackson.JacksonSerializer");
        assertThat(clazz).isNotNull();
    }

    @Test
    void objectMapperShouldBeAccessible() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new TestPayload("test", 42));
        assertThat(json).contains("test").contains("42");
    }

    // ─── 4. Resource config validation ───

    @Test
    void applicationPropertiesShouldBeLoadable() {
        InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("application.properties");
        assertThat(is).as("application.properties must be in native image").isNotNull();
    }

    @Test
    void nativeImageReflectConfigShouldBeLoadable() {
        InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/native-image/reflect-config.json");
        assertThat(is).as("reflect-config.json must be in native image").isNotNull();
    }

    @Test
    void nativeImageResourceConfigShouldBeLoadable() {
        InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/native-image/resource-config.json");
        assertThat(is).as("resource-config.json must be in native image").isNotNull();
    }

    @Test
    void nativeImageSerializationConfigShouldBeLoadable() {
        InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/native-image/serialization-config.json");
        assertThat(is).as("serialization-config.json must be in native image").isNotNull();
    }

    @Test
    void nativeImageJniConfigShouldBeLoadable() {
        InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("META-INF/native-image/jni-config.json");
        assertThat(is).as("jni-config.json must be in native image").isNotNull();
    }

    // ─── 5. Avro classes ───

    @Test
    void avroGenericRecordShouldBeAccessible() throws Exception {
        Class<?> clazz = Class.forName("org.apache.avro.generic.GenericData$Record");
        assertThat(clazz).isNotNull();
        assertThat(clazz.getDeclaredConstructors()).isNotEmpty();
    }

    @Test
    void avroGenericRecordShouldBeInstantiable() {
        org.apache.avro.Schema schema = org.apache.avro.Schema.createRecord(
                "TestRecord", "test", "io.matrix.test", false,
                List.of(new org.apache.avro.Schema.Field("name",
                        org.apache.avro.Schema.create(org.apache.avro.Schema.Type.STRING), "test", null))
        );
        GenericRecord record = new GenericData.Record(schema);
        record.put("name", "test");
        assertThat(record).isNotNull();
        assertThat(record.get("name")).isEqualTo("test");
    }

    // ─── 6. Pekko actor system ───

    @Test
    void actorSystemShouldBeCreatable() {
        ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "native-test");
        assertThat(system).isNotNull();
        assertThat(system.name()).isEqualTo("native-test");
        system.terminate();
    }

    // ─── 7. Cluster domain classes ───

    @Test
    void neuronIdShouldBeCreatable() {
        NeuronId id = new NeuronId(UUID.randomUUID(), 1L);
        assertThat(id).isNotNull();
        assertThat(id.generation()).isEqualTo(1L);
    }

    @Test
    void signalShouldBeCreatable() {
        NeuronId source = new NeuronId(UUID.randomUUID(), 1L);
        NeuronId target = new NeuronId(UUID.randomUUID(), 2L);
        Signal signal = new Signal(source, target, true);
        assertThat(signal).isNotNull();
        assertThat(signal.sourceId()).isEqualTo(source);
    }

    @Test
    void clusterConfigShouldBeCreatable() {
        ClusterConfig config = ClusterConfig.defaults();
        assertThat(config).isNotNull();
        assertThat(config.maxNeurons()).isEqualTo(1000);
    }

    @Test
    void clusterConfigForSizeShouldWork() {
        ClusterConfig config = ClusterConfig.forSize(50);
        assertThat(config).isNotNull();
        assertThat(config.maxNeurons()).isEqualTo(50);
    }

    @Test
    void clusterSnapshotShouldBeCreatable() {
        ClusterSnapshot snapshot = new ClusterSnapshot();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.snapshotId()).isEmpty();
    }

    // ─── 8. Domain classes ───

    @Test
    void driverStateShouldBeCreatable() {
        DriverState state = new DriverState(
                DriverType.CURIOSITY, 0.5, 0.4,
                0.1, 0.05, 0.8, 0.2
        );
        assertThat(state).isNotNull();
        assertThat(state.type()).isEqualTo(DriverType.CURIOSITY);
    }

    @Test
    void driverTypeShouldHaveAllValues() {
        assertThat(DriverType.values()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(DriverType.ENERGY).isNotNull();
        assertThat(DriverType.SAFETY).isNotNull();
        assertThat(DriverType.CURIOSITY).isNotNull();
        assertThat(DriverType.UBUNTU).isNotNull();
    }

    @Test
    void ethicalVerdictShouldBeAccessible() throws Exception {
        Class<?> clazz = Class.forName("io.matrix.ethics.EthicalVerdict");
        assertThat(clazz).isNotNull();
        assertThat(clazz.isEnum()).isTrue();
    }

    @Test
    void proposalShouldBeCreatable() {
        Proposal proposal = Proposal.create(
                ConsensusLevel.LEVEL_1, "neuron-1", "mutate", "payload"
        );
        assertThat(proposal).isNotNull();
        assertThat(proposal.proposerId()).isEqualTo("neuron-1");
        assertThat(proposal.level()).isEqualTo(ConsensusLevel.LEVEL_1);
    }

    @Test
    void voteShouldBeCreatable() {
        UUID proposalId = UUID.randomUUID();
        Vote vote = Vote.approve(proposalId, "neuron-1", 1.0);
        assertThat(vote).isNotNull();
        assertThat(vote.proposalId()).isEqualTo(proposalId);
        assertThat(vote.approve()).isTrue();
    }

    @Test
    void consensusLevelShouldHaveAllValues() {
        assertThat(ConsensusLevel.values()).hasSize(4);
        assertThat(ConsensusLevel.LEVEL_0).isNotNull();
        assertThat(ConsensusLevel.LEVEL_1).isNotNull();
        assertThat(ConsensusLevel.LEVEL_2).isNotNull();
        assertThat(ConsensusLevel.LEVEL_3).isNotNull();
    }

    // ─── 9. Compression / SIMD ───

    @Test
    void booleanCompressorShouldBeAccessible() {
        assertThat(BooleanCompressor.class).isNotNull();
        assertThat(BooleanCompressor.Method.values()).containsExactly(
                BooleanCompressor.Method.RLE, BooleanCompressor.Method.BITMASK
        );
    }

    @Test
    void simdEvaluatorShouldBeAccessible() {
        assertThat(SimdEvaluator.class).isNotNull();
        assertThat(SimdEvaluator.class.getDeclaredMethods()).isNotEmpty();
    }

    // ─── 10. Startup time measurement ───

    @Test
    void measureStartupTime() {
        long start = System.nanoTime();
        // Simulate core initialization
        ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "startup-test");
        ClusterConfig config = ClusterConfig.defaults();
        NeuronId id = new NeuronId(UUID.randomUUID(), 1L);
        long elapsed = System.nanoTime() - start;

        system.terminate();

        // JVM startup should be < 2s, native should be < 500ms
        double elapsedMs = elapsed / 1_000_000.0;
        assertThat(elapsedMs).isLessThan(5000); // 5s max for JVM warmup
    }

    // ─── 11. Memory footprint ───

    @Test
    void measureMemoryFootprint() {
        Runtime runtime = Runtime.getRuntime();
        long before = runtime.totalMemory() - runtime.freeMemory();

        // Create core objects
        ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "memory-test");
        ClusterConfig config = ClusterConfig.defaults();
        BooleanIndex index = BooleanIndex.builder().build();
        BooleanRag rag = BooleanRag.builder().index(index).build();
        MemoryHierarchy memory = new MemoryHierarchy();

        long after = runtime.totalMemory() - runtime.freeMemory();
        long usedBytes = after - before;

        system.terminate();

        // Native image should use < 100MB, JVM < 256MB for core objects
        double usedMB = usedBytes / (1024.0 * 1024.0);
        assertThat(usedMB).isLessThan(512); // 512MB max
    }

    // ─── Helper ───

    record TestPayload(String name, int value) {}
}
