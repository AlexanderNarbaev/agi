package io.matrix.benchmark;

import io.matrix.cauldron.CauldronProtocol;
import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.consensus.ConsensusEngine;
import io.matrix.consensus.ConsensusLevel;
import io.matrix.consensus.Proposal;
import io.matrix.consensus.Vote;
import io.matrix.ethics.EthicalFilter;
import io.matrix.evolution.EvolutionLoop;
import io.matrix.evolution.GeneticOperators;
import io.matrix.neuron.TruthTable;
import io.matrix.simulation.ClusterTopology;

import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class NeuronBenchmark {

    private TruthTable table;
    private EthicalFilter ethics;
    private ConsensusEngine consensus;
    private Random rng;

    @Setup
    public void setup() {
        rng = new Random(42);
        table = TruthTable.random(8, rng);
        ethics = new EthicalFilter();
        consensus = new ConsensusEngine();
    }

    @Benchmark
    public boolean truthTableEvaluate() {
        return table.evaluate(rng.nextInt(256));
    }

    @Benchmark
    public EthicalFilter.EthicalVerdict ethicalFilterEvaluate() {
        return ethics.evaluate("help user learn mathematics", List.of("education"));
    }

    @Benchmark
    public ConsensusEngine.Decision consensusEvaluate() {
        var propId = consensus.propose(Proposal.create(ConsensusLevel.LEVEL_2,
                "node-1", "INTEGRATION_MUTATION", "bench"));
        consensus.castVote(Vote.approve(propId, "voter-1", 0.9));
        return consensus.evaluate(propId);
    }

    @Benchmark
    public double evolutionGeneration() {
        EvolutionLoop loop = EvolutionLoop.small(rng, 8);
        return loop.step();
    }

    @Benchmark
    public double geneticOperatorMutate() {
        TruthTable t = TruthTable.random(8, rng);
        return GeneticOperators.mutateFlipRate(t, 0.05, rng).table().cardinality();
    }

    @Benchmark
    public int clusterTopologyGeneration() {
        ClusterTopology topo = ClusterTopology.small(rng);
        return topo.neuronTables().size();
    }
}
