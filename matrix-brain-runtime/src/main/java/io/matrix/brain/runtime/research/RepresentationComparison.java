package io.matrix.brain.runtime.research;

import io.matrix.brain.runtime.EpisodeFeatureExtractor;
import io.matrix.tsetlin.TsetlinTrainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * EPISTEMIC-INTEGRITY P3 — the comparison of the representations on a validated outcome.
 *
 * <p><b>What it compares.</b> The production feature and the production fold, two
 * projections derived from the training split only, the ceiling of any function of the
 * raw feature (1-NN), and three controls (the majority class, a label-blind random
 * projection, and the production fold trained on shuffled labels). Every representation
 * is scored on the SAME partition, with the SAME metrics, so the numbers are comparable
 * row by row.</p>
 *
 * <p><b>What is held fixed.</b> The trainer width (16 bits), the clause count (64), the
 * state count (200), the epoch count (10) and the production fold are the production
 * ones — this class measures them, it does not change them.</p>
 *
 * <p><b>The metrics.</b> Accuracy and BALANCED accuracy (the mean of the two per-class
 * recalls), with the confusion counts, because a window with one class makes accuracy
 * meaningless — the same defect the earlier wave found in the production fidelity.</p>
 *
 * <p><b>The cost.</b> The acquisition cost of a representation: the training time, the
 * inference time per episode, and the bytes per episode the representation has to keep.</p>
 */
public final class RepresentationComparison {

    /** The trainer's input width — the production one. */
    public static final int TRAINER_BITS = 16;

    /** The production clause count. */
    public static final int CLAUSES = 64;

    /** The production state count. */
    public static final int STATES = 200;

    /** The production epoch count. */
    public static final int EPOCHS = 10;

    /** The production feature width. */
    public static final int FEATURE_BITS = EpisodeFeatureExtractor.DEFAULT_DIM;

    private RepresentationComparison() {
    }

    /** The representations the comparison measures. */
    public enum Representation {
        /** The raw 256-bit production feature, consumed by the 1-NN ceiling only. */
        RAW_256,
        /** The production fold: 256 bits XOR-folded to 16, trained by the production trainer. */
        FOLD_16,
        /** The positions that vary in the training split, 16 of them, trained by the production trainer. */
        INFORMATIVE_16,
        /** The positions with the largest class-conditional separation in the training split. */
        DISCRIMINATIVE_16,
        /** A label-blind random projection of 16 positions — the control. */
        RANDOM_PROJECTION_16,
        /** The production fold trained on SHUFFLED labels — the control. */
        SHUFFLED_LABELS,
        /** The majority class of the training split — the simplest baseline. */
        MAJORITY_CLASS,
        /** 1-NN on the raw 256-bit feature — the ceiling of any function of it. */
        ONE_NN_256
    }

    /** One measured representation. */
    public record Result(
            Representation representation,
            int trainSize,
            int testSize,
            int testPositives,
            int testNegatives,
            int distinctTrainValues,
            int crossLabelCollisions,
            int truePositive,
            int falsePositive,
            int trueNegative,
            int falseNegative,
            double accuracy,
            double balancedAccuracy,
            long trainMillis,
            double inferenceMicrosPerEpisode,
            int bytesPerEpisode) {

        /** The positive-class recall. */
        public double positiveRecall() {
            int positives = truePositive + falseNegative;
            return positives == 0 ? 0.0 : (double) truePositive / positives;
        }

        /** The negative-class recall. */
        public double negativeRecall() {
            int negatives = trueNegative + falsePositive;
            return negatives == 0 ? 0.0 : (double) trueNegative / negatives;
        }
    }

    /**
     * Measures every representation on one partition.
     *
     * @param observations the labelled observations
     * @param partition    the leakage-controlled partition
     * @param seed         the seed of the trainer and of the random projection
     * @return one result per representation, in the declared order
     */
    public static List<Result> compare(List<OutcomeSignal> observations,
                                       LeakageControlledPartition partition,
                                       long seed) {
        return compare(observations, partition, seed, false);
    }

    /**
     * Measures every representation against the PRODUCTION label instead of the
     * validated outcome.
     *
     * @param observations      the labelled observations
     * @param partition         the leakage-controlled partition
     * @param seed              the seed of the trainer
     * @param productionLabel   true to use {@code confidence >= 0.7 AND accepted}
     * @return one result per representation
     */
    public static List<Result> compare(List<OutcomeSignal> observations,
                                       LeakageControlledPartition partition,
                                       long seed, boolean productionLabel) {
        List<Result> results = new ArrayList<>();
        for (Representation representation : Representation.values()) {
            results.add(measure(representation, observations, partition, seed, productionLabel));
        }
        return results;
    }

    /** Measures one representation against the validated outcome. */
    public static Result measure(Representation representation,
                                  List<OutcomeSignal> observations,
                                  LeakageControlledPartition partition,
                                  long seed) {
        return measure(representation, observations, partition, seed, false);
    }

    /**
     * Measures one representation.
     *
     * @param representation  the representation
     * @param observations    the labelled observations
     * @param partition       the leakage-controlled partition
     * @param seed            the seed of the trainer
     * @param productionLabel true to use the production label as the target
     * @return the measured result
     */
    public static Result measure(Representation representation,
                                  List<OutcomeSignal> observations,
                                  LeakageControlledPartition partition,
                                  long seed, boolean productionLabel) {
        long[][] raw = new long[observations.size()][];
        boolean[] labels = new boolean[observations.size()];
        for (int i = 0; i < observations.size(); i++) {
            raw[i] = observations.get(i).feature(FEATURE_BITS);
            labels[i] = productionLabel
                    ? observations.get(i).productionPositive()
                    : observations.get(i).positive();
        }

        int[] train = toArray(partition.train());
        int[] test = toArray(partition.test());

        // The training labels of the CONTROL are shuffled; every other representation
        // sees the labels as they are.
        boolean[] trainLabels = new boolean[train.length];
        for (int i = 0; i < train.length; i++) trainLabels[i] = labels[train[i]];
        if (representation == Representation.SHUFFLED_LABELS) {
            shuffle(trainLabels, new Random(seed));
        }

        if (train.length == 0 || test.length == 0) {
            // an empty half has nothing to measure: the row is reported as empty rather
            // than as a score of zero, so it cannot be mistaken for a measurement
            return new Result(representation, train.length, test.length, 0, 0, 0, 0,
                    0, 0, 0, 0, 0.0, 0.0, 0, 0.0, TRAINER_BITS / 8);
        }
        long[][] projected = project(representation, raw, train, trainLabels, seed);

        int distinct = distinctValues(projected, train);
        // The collision is counted over the OBSERVATION labels of the train half: a
        // distinct value that carries both classes is a value the learner cannot use.
        int collisions = crossLabelCollisions(projected, train, labels);

        long trainStarted = System.nanoTime();
        boolean[] predicted = new boolean[test.length];
        double inferenceNanos = 0.0;
        int trainMillis;

        if (representation == Representation.MAJORITY_CLASS) {
            int positives = 0;
            for (boolean label : trainLabels) if (label) positives++;
            boolean majority = trainLabels.length > 0 && positives * 2 > trainLabels.length;
            Arrays.fill(predicted, majority);
            trainMillis = 0;
        } else if (representation == Representation.ONE_NN_256) {
            long started = System.nanoTime();
            for (int t = 0; t < test.length; t++) {
                int best = -1;
                int bestDistance = Integer.MAX_VALUE;
                for (int r : train) {
                    int distance = EpisodeFeatureExtractor.hammingDistance(raw[test[t]], raw[r]);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = r;
                    }
                }
                predicted[t] = best >= 0 && labels[best];
            }
            inferenceNanos = (System.nanoTime() - started) / (double) Math.max(1, test.length);
            trainMillis = 0;
        } else {
            TsetlinTrainer trainer = new TsetlinTrainer(TRAINER_BITS, CLAUSES, STATES,
                    new Random(seed));
            long[][] trainInputs = new long[train.length][];
            for (int i = 0; i < train.length; i++) trainInputs[i] = projected[train[i]];
            trainer.trainBatch(trainInputs, trainLabels, EPOCHS);
            trainMillis = (int) ((System.nanoTime() - trainStarted) / 1_000_000L);
            long started = System.nanoTime();
            for (int t = 0; t < test.length; t++) {
                predicted[t] = trainer.predict(trainer.packForTest(projected[test[t]]));
            }
            inferenceNanos = (System.nanoTime() - started) / (double) Math.max(1, test.length);
        }

        int truePositive = 0;
        int falsePositive = 0;
        int trueNegative = 0;
        int falseNegative = 0;
        for (int t = 0; t < test.length; t++) {
            boolean actual = labels[test[t]];
            if (actual && predicted[t]) truePositive++;
            else if (actual) falseNegative++;
            else if (predicted[t]) falsePositive++;
            else trueNegative++;
        }
        int total = test.length;
        double accuracy = total == 0 ? 0.0 : (double) (truePositive + trueNegative) / total;
        double positiveRecall = (truePositive + falseNegative) == 0 ? 0.0
                : (double) truePositive / (truePositive + falseNegative);
        double negativeRecall = (trueNegative + falsePositive) == 0 ? 0.0
                : (double) trueNegative / (trueNegative + falsePositive);
        double balanced = (positiveRecall + negativeRecall) / 2.0;

        int bytes = representation == Representation.RAW_256 || representation == Representation.ONE_NN_256
                ? FEATURE_BITS / 8 : TRAINER_BITS / 8;
        int testPositives = 0;
        for (int index : test) if (labels[index]) testPositives++;
        return new Result(representation, train.length, test.length, testPositives,
                test.length - testPositives, distinct, collisions,
                truePositive, falsePositive, trueNegative, falseNegative,
                accuracy, balanced, trainMillis, inferenceNanos / 1000.0, bytes);
    }

    /**
     * The projection of the raw feature each representation consumes.
     *
     * @param representation the representation
     * @param raw           the raw features
     * @param train         the indices of the train half
     * @param trainLabels   the labels of the train half
     * @param seed          the seed of the label-blind random projection
     * @return the projected features, one per observation
     */
    private static long[][] project(Representation representation, long[][] raw, int[] train,
                                    boolean[] trainLabels, long seed) {
        long[][] out = new long[raw.length][];
        switch (representation) {
            case RAW_256, ONE_NN_256 -> {
                for (int i = 0; i < raw.length; i++) out[i] = raw[i];
            }
            case FOLD_16, SHUFFLED_LABELS -> {
                for (int i = 0; i < raw.length; i++) out[i] = fold(raw[i]);
            }
            case INFORMATIVE_16 -> {
                int[] positions = informativePositions(raw, train);
                for (int i = 0; i < raw.length; i++) out[i] = project(raw[i], positions);
            }
            case DISCRIMINATIVE_16 -> {
                int[] positions = discriminativePositions(raw, train, trainLabels);
                for (int i = 0; i < raw.length; i++) out[i] = project(raw[i], positions);
            }
            case RANDOM_PROJECTION_16 -> {
                Random random = new Random(seed);
                int[] positions = new int[TRAINER_BITS];
                for (int i = 0; i < TRAINER_BITS; i++) positions[i] = random.nextInt(FEATURE_BITS);
                for (int i = 0; i < raw.length; i++) out[i] = project(raw[i], positions);
            }
            case MAJORITY_CLASS -> {
                for (int i = 0; i < raw.length; i++) out[i] = raw[i];
            }
            default -> {
                for (int i = 0; i < raw.length; i++) out[i] = raw[i];
            }
        }
        return out;
    }

    /**
     * The production fold, reimplemented here so the measurement is explicit.
     *
     * <p>The fold is XOR-parity: output bit {@code j} is the parity of the source bits
     * {@code j, j+16, j+32, ...}. The production trainer applies the same fold inside
     * {@code trainStep}, so the training input and the inference input agree.</p>
     */
    public static long[] fold(long[] words) {
        long[] out = new long[1];
        for (int j = 0; j < TRAINER_BITS; j++) {
            int parity = 0;
            for (int word = 0; word < words.length; word++) {
                int bit = j + word * Long.SIZE;
                if (bit >= FEATURE_BITS) break;
                if (((words[word] >>> bit) & 1L) == 1L) parity ^= 1;
            }
            if (parity == 1) out[0] |= (1L << j);
        }
        return out;
    }

    /** The 16 positions that vary in the training split, in ascending order. */
    private static int[] informativePositions(long[][] raw, int[] train) {
        if (train.length == 0) return new int[0];
        boolean[] varies = new boolean[FEATURE_BITS];
        for (int bit = 0; bit < FEATURE_BITS; bit++) {
            int first = bitAt(raw[train[0]], bit);
            boolean same = true;
            for (int index : train) {
                if (bitAt(raw[index], bit) != first) {
                    same = false;
                    break;
                }
            }
            varies[bit] = !same;
        }
        List<Integer> positions = new ArrayList<>();
        for (int bit = 0; bit < FEATURE_BITS && positions.size() < TRAINER_BITS; bit++) {
            if (varies[bit]) positions.add(bit);
        }
        int[] out = new int[positions.size()];
        for (int i = 0; i < positions.size(); i++) out[i] = positions.get(i);
        return out;
    }

    /** The 16 positions with the largest class-conditional separation in the training split. */
    private static int[] discriminativePositions(long[][] raw, int[] train, boolean[] trainLabels) {
        if (train.length == 0) return new int[0];
        int positives = 0;
        for (boolean label : trainLabels) if (label) positives++;
        int negatives = trainLabels.length - positives;
        double[] separation = new double[FEATURE_BITS];
        for (int bit = 0; bit < FEATURE_BITS; bit++) {
            int positiveBits = 0;
            int negativeBits = 0;
            for (int i = 0; i < train.length; i++) {
                int value = bitAt(raw[train[i]], bit);
                if (trainLabels[i] && value == 1) positiveBits++;
                if (!trainLabels[i] && value == 1) negativeBits++;
            }
            double positiveShare = positives == 0 ? 0.0 : (double) positiveBits / positives;
            double negativeShare = negatives == 0 ? 0.0 : (double) negativeBits / negatives;
            separation[bit] = Math.abs(positiveShare - negativeShare);
        }
        Integer[] order = new Integer[FEATURE_BITS];
        for (int bit = 0; bit < FEATURE_BITS; bit++) order[bit] = bit;
        Arrays.sort(order, (left, right) -> {
            int byValue = Double.compare(separation[right], separation[left]);
            return byValue != 0 ? byValue : Integer.compare(left, right);
        });
        int[] out = new int[TRAINER_BITS];
        for (int i = 0; i < TRAINER_BITS; i++) out[i] = order[i];
        return out;
    }

    /** The 16 selected positions, packed into one long. */
    private static long[] project(long[] words, int[] positions) {
        long[] out = new long[1];
        for (int i = 0; i < positions.length && i < TRAINER_BITS; i++) {
            if (bitAt(words, positions[i]) == 1) out[0] |= (1L << i);
        }
        return out;
    }

    /** One bit of a packed feature. */
    private static int bitAt(long[] words, int bit) {
        int word = bit / Long.SIZE;
        if (word >= words.length) return 0;
        return (int) ((words[word] >>> (bit % Long.SIZE)) & 1L);
    }

    /** The number of distinct projected values in the train half. */
    private static int distinctValues(long[][] projected, int[] train) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (int index : train) {
            seen.merge(key(projected[index]), 1, Integer::sum);
        }
        return seen.size();
    }

    /** The number of distinct train values that carry both labels. */
    private static int crossLabelCollisions(long[][] projected, int[] train, boolean[] labels) {
        Map<String, Boolean> first = new LinkedHashMap<>();
        Map<String, Boolean> second = new LinkedHashMap<>();
        for (int index : train) {
            String key = key(projected[index]);
            if (!first.containsKey(key)) first.put(key, labels[index]);
            else if (first.get(key) != labels[index]) second.put(key, labels[index]);
        }
        return second.size();
    }

    /** The key of a projected value: its bits as a string. */
    private static String key(long[] projected) {
        StringBuilder sb = new StringBuilder();
        for (long word : projected) {
            for (int bit = 0; bit < Long.SIZE; bit++) {
                sb.append((word >>> bit) & 1L);
            }
        }
        return sb.toString();
    }

    /** A deterministic shuffle of a label array. */
    private static void shuffle(boolean[] labels, Random random) {
        for (int i = labels.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            boolean tmp = labels[i];
            labels[i] = labels[j];
            labels[j] = tmp;
        }
    }

    /** A primitive list to an int array. */
    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i);
        return out;
    }
}
