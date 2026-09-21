package io.matrix.consciousness;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * W247 — Cognitive Beam Search.
 *
 * <p>Inspired by beam search decoder in seq2seq models. Maintain top-K
 * candidate sequences at each step.
 *
 * <p>Process:
 * 1. Start with K empty beams
 * 2. At each step, expand each beam by all possible next tokens
 * 3. Keep top-K by cumulative score
 * 4. Return best beam after N steps
 *
 * <p>In MATRIX: select top-K cognitive profiles by cumulative score.
 *
 * <p>CONSTITUTION VI compliance: beam-search cognitive decoding,
 * not phenomenal consciousness claim.
 */
public final class CognitiveBeamSearch {

    private CognitiveBeamSearch() {}

    /** A single beam (sequence + score). */
    public record Beam(List<CognitiveGenesisProfile> sequence, double score) {
        public Beam {
            sequence = List.copyOf(sequence);
        }
    }

    /** Beam search result. */
    public record BeamSearchResult(List<Beam> topBeams, double bestScore) {}

    /**
     * Run beam search over profile candidates.
     *
     * @param candidates pool of candidate profiles
     * @param beamWidth K (number of beams to keep)
     * @param maxSteps maximum number of steps
     * @param scorer function to score a (sequence, candidate) pair
     * @return top beams sorted by descending score
     */
    public static BeamSearchResult search(List<CognitiveGenesisProfile> candidates,
                                            int beamWidth,
                                            int maxSteps,
                                            BeamScorer scorer) {
        if (candidates == null || candidates.isEmpty() || beamWidth < 1 || maxSteps < 1) {
            return new BeamSearchResult(new ArrayList<>(), 0.0);
        }
        PriorityQueue<Beam> beams = new PriorityQueue<>((a, b) -> Double.compare(b.score(), a.score()));
        beams.add(new Beam(new ArrayList<>(), 0.0));
        for (int step = 0; step < maxSteps && !candidates.isEmpty(); step++) {
            PriorityQueue<Beam> nextBeams = new PriorityQueue<>((a, b) -> Double.compare(b.score(), a.score()));
            for (Beam beam : beams) {
                for (CognitiveGenesisProfile candidate : candidates) {
                    double score = (scorer != null) ? scorer.score(beam.sequence(), candidate) : 0.0;
                    List<CognitiveGenesisProfile> newSeq = new ArrayList<>(beam.sequence());
                    newSeq.add(candidate);
                    nextBeams.add(new Beam(newSeq, beam.score() + score));
                }
            }
            // Keep top K
            beams = new PriorityQueue<>((a, b) -> Double.compare(b.score(), a.score()));
            for (int i = 0; i < beamWidth && !nextBeams.isEmpty(); i++) {
                beams.add(nextBeams.poll());
            }
        }
        List<Beam> result = new ArrayList<>(beams);
        result.sort((a, b) -> Double.compare(b.score(), a.score()));
        double best = result.isEmpty() ? 0.0 : result.get(0).score();
        return new BeamSearchResult(result, best);
    }

    /** Functional interface for scoring. */
    @FunctionalInterface
    public interface BeamScorer {
        double score(List<CognitiveGenesisProfile> prefix, CognitiveGenesisProfile candidate);
    }
}
