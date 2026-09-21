package io.matrix.federation.benchmark;

import io.matrix.federation.consensus.LocalConsensusEngine;
import io.matrix.federation.consensus.LocalConsensusEngine.ConsensusResult;
import io.matrix.federation.proto.CapabilityLevel;
import io.matrix.federation.proto.ConsensusProposal;
import io.matrix.federation.proto.ConsensusVote;
import io.matrix.federation.proto.VoteDecision;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * W367 — JMH benchmarks for LocalConsensusEngine.
 * 
 * Measures:
 * - Single vote addition
 * - Vote collection (1000 votes)
 * - Consensus evaluation
 * - Vote creation overhead
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ConsensusBenchmark {
    
    @Param({"10", "100", "1000"})
    private int voteCount;
    
    private LocalConsensusEngine engine;
    private LocalConsensusEngine engineWithVotes;
    private ConsensusProposal proposal;
    private Random rng;
    
    @Setup(Level.Trial)
    public void setup() {
        engine = new LocalConsensusEngine(42L);
        engineWithVotes = new LocalConsensusEngine(42L);
        proposal = ConsensusProposal.newBuilder().setProposalId("bench").build();
        rng = new Random(42L);
        
        // Pre-populate
        for (int i = 0; i < 1000; i++) {
            VoteDecision[] decs = {VoteDecision.VOTE_YES, VoteDecision.VOTE_NO, VoteDecision.VOTE_ABSTAIN};
            VoteDecision dec = decs[rng.nextInt(decs.length)];
            CapabilityLevel[] lvls = {
                CapabilityLevel.CAPABILITY_L2_ADULT,
                CapabilityLevel.CAPABILITY_L3_SPECIALIST,
                CapabilityLevel.CAPABILITY_L5_MASTER
            };
            CapabilityLevel lvl = lvls[rng.nextInt(lvls.length)];
            engineWithVotes.addVote(engineWithVotes.createVote("bench", dec, lvl, 1.0));
        }
    }
    
    @Benchmark
    public void singleVoteAddition() {
        engine.addVote(engine.createVote("bench", VoteDecision.VOTE_YES, 
            CapabilityLevel.CAPABILITY_L3_SPECIALIST, 1.0));
    }
    
    @Benchmark
    public ConsensusResult evaluateManyVotes() {
        return engineWithVotes.evaluate(proposal);
    }
    
    @Benchmark
    public ConsensusVote createSingleVote() {
        return engine.createVote("bench", VoteDecision.VOTE_YES, 
            CapabilityLevel.CAPABILITY_L3_SPECIALIST, 1.0);
    }
    
    @Benchmark
    public double getVotingWeight() {
        return LocalConsensusEngine.getVotingWeight(CapabilityLevel.CAPABILITY_L3_SPECIALIST);
    }
    
    @Benchmark
    public void addBatchOfVotes() {
        for (int i = 0; i < voteCount; i++) {
            engine.addVote(engine.createVote("bench", VoteDecision.VOTE_YES,
                CapabilityLevel.CAPABILITY_L2_ADULT, 1.0));
        }
    }
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(ConsensusBenchmark.class.getSimpleName())
            .build();
        new Runner(opt).run();
    }
}
