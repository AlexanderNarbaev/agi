package io.matrix.evolution;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Multi-objective Pareto fitness for evolution.
 *
 * <p>Evaluates candidates on four axes: quality, robustness, latency, complexity.
 * Selects Pareto-optimal (non-dominated) candidates from the population.
 */
public final class ParetoFitness {

    private ParetoFitness() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Normalised multi-objective fitness vector.
     *
     * <p>All four components are in {@code [0, 1]}. Higher quality and robustness
     * are better; lower latency and complexity penalties are better.
     */
    public record FitnessVector(double quality, double robustness, double latencyPenalty,
                                double complexityPenalty) {

        /** Factory: normalises raw latency (ms) and complexity to {@code [0, 1]} penalties. */
        public static FitnessVector of(double quality, double robustness, double latencyMs,
                                       int complexity) {
            double latencyPenalty = Math.min(1.0, latencyMs / 500.0);
            double complexityPenalty = Math.min(1.0, complexity / 20.0);
            return new FitnessVector(quality, robustness, latencyPenalty, complexityPenalty);
        }

        /**
         * Returns {@code true} when {@code this} is strictly better than {@code other}
         * on at least one axis and no worse on any axis.
         */
        public boolean dominates(FitnessVector other) {
            boolean atLeastOneBetter = false;

            if (quality > other.quality) {
                atLeastOneBetter = true;
            } else if (quality < other.quality) {
                return false;
            }

            if (robustness > other.robustness) {
                atLeastOneBetter = true;
            } else if (robustness < other.robustness) {
                return false;
            }

            if (latencyPenalty < other.latencyPenalty) {
                atLeastOneBetter = true;
            } else if (latencyPenalty > other.latencyPenalty) {
                return false;
            }

            if (complexityPenalty < other.complexityPenalty) {
                atLeastOneBetter = true;
            } else if (complexityPenalty > other.complexityPenalty) {
                return false;
            }

            return atLeastOneBetter;
        }

        /**
         * Weighted composite score in {@code [0, 1]}.
         *
         * <p>Weights: 0.4 quality + 0.25 robustness + 0.2 (1 − latencyPenalty)
         * + 0.15 (1 − complexityPenalty).
         */
        public double compositeScore() {
            return 0.4 * quality
                    + 0.25 * robustness
                    + 0.2 * (1.0 - latencyPenalty)
                    + 0.15 * (1.0 - complexityPenalty);
        }
    }

    /** An evaluated candidate annotated with its fitness vector and Pareto flag. */
    public record EvaluatedCandidate<T>(T candidate, FitnessVector fitness,
                                        boolean isParetoOptimal) {
    }

    /**
     * Evaluates every candidate and marks which ones belong to the Pareto front.
     *
     * @param candidates population to evaluate
     * @param evaluator  maps a candidate to its {@link FitnessVector}
     * @param <T>        candidate type
     * @return list of evaluated candidates with {@code isParetoOptimal} set
     */
    public static <T> List<EvaluatedCandidate<T>> paretoSelect(
            List<T> candidates, Function<T, FitnessVector> evaluator) {

        List<FitnessVector> vectors = new ArrayList<>(candidates.size());
        for (T c : candidates) {
            vectors.add(evaluator.apply(c));
        }

        List<FitnessVector> front = paretoFront(vectors);

        List<EvaluatedCandidate<T>> result = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            boolean optimal = front.contains(vectors.get(i));
            result.add(new EvaluatedCandidate<>(candidates.get(i), vectors.get(i), optimal));
        }
        return result;
    }

    /**
     * Returns the Pareto-optimal (non-dominated) subset of the given vectors.
     *
     * @param vectors fitness vectors to filter
     * @return the Pareto-front vectors
     */
    public static List<FitnessVector> paretoFront(List<FitnessVector> vectors) {
        List<FitnessVector> front = new ArrayList<>();
        for (FitnessVector v : vectors) {
            boolean dominated = false;
            for (FitnessVector other : vectors) {
                if (other != v && other.dominates(v)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                front.add(v);
            }
        }
        return front;
    }
}
