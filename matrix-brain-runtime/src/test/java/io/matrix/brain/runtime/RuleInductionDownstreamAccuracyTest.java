package io.matrix.brain.runtime;

import io.matrix.tsetlin.TsetlinTrainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * REP-01 — does the Tsetlin trainer need the whole width, or the informative part of it?
 *
 * <p><b>The question this test answers.</b> The trainer of chain B's rule induction is 16
 * bits wide, and the feature the sleep cycle hands it is 256 bits wide; the fold between
 * them has a kernel of dimension 240. The owner's question is precise: <b>does Tsetlin
 * need the whole width of the vector, or a smaller informative feature set?</b> Two
 * questions hide inside it, and this test answers both on the real data with the
 * production's own metric.</p>
 *
 * <ol>
 *   <li><b>How many of the 256 bits actually vary?</b> The production method
 *       {@code TsetlinTrainer.countInformativeBits} counts the dimensions that take both
 *       values in the training data. If the informative set is small, then a projection
 *       onto it is <b>lossless for this data</b> — and the fold's extra loss (the 240
 *       dropped bits) carries nothing that the data has.</li>
 *   <li><b>What does the downstream accuracy say?</b> The engine's own metric is the
 *       held-out fidelity on its 80/20 split. The production path (the 256-bit features,
 *       folded by the trainer) is compared with the <b>informative projection</b> (the same
 *       features, projected onto the bits that vary, so that the fold becomes the
 *       identity). If the projection is not worse, then the information the fold drops is
 *       not information the task needs — and the decision becomes "say what the fold is
 *       for", not "the learner was blind".</li>
 * </ol>
 *
 * <p><b>What it does not do.</b> It does not change the fold, the engine or the serving
 * path: the architecture question is the owner's (REP-03/REP-04), and a change there
 * touches serving code. It measures what the learner can and cannot see.
 */
class RuleInductionDownstreamAccuracyTest {

    /** The seed the production engine uses. */
    private static final long SEED = 42L;

    /** The width of the feature the sleep cycle produces. */
    private static final int FEATURE_BITS = 256;

    /**
     * The episodic log of the running gateway.
     *
     * @return the path, or null when the runtime data is not on this machine
     */
    private static Path episodicLog() {
        Path start = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (Path directory = start; directory != null; directory = directory.getParent()) {
            Path candidate = directory.resolve("data/mind/episodic.ndjson");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Test
    @DisplayName("the informative projection is not worse than the production fold")
    void theInformativeProjectionIsNotWorse() {
        Path log = episodicLog();
        assumeTrue(log != null,
                "the runtime's episodic log is not on this machine: the downstream measurement "
                        + "cannot be made here (the file is the runtime's own data and is not "
                        + "part of the repository)");
        List<EpisodicLog.Entry> all = new EpisodicLog(log).readAll();
        assumeTrue(all.size() > 8, "the episodic log holds too few entries to measure");

        // The production's own window, feature rule and label rule
        // (RealSleepScheduler.runInduction): the LAST twenty episodes, the 256-bit
        // feature, and the label "confidence >= 0.7 AND accepted".
        int count = Math.min(all.size(), KMaxEnforcer.K_MAX);
        List<EpisodicLog.Entry> entries = all.subList(all.size() - count, all.size());
        EpisodeFeatureExtractor extractor = new EpisodeFeatureExtractor();
        long[][] features = new long[entries.size()][];
        boolean[] labels = new boolean[entries.size()];
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            EpisodicLog.Entry entry = entries.get(index);
            features[index] = extractor.encode(entry);
            labels[index] = entry.confidence() >= 0.7 && entry.accepted();
            ids.add(entry.id());
        }

        // 1. how many of the 256 bits vary?
        int informative = TsetlinTrainer.countInformativeBits(features, FEATURE_BITS);
        System.out.printf("[REP-01] the real episodes: %d entries, %d of the %d feature bits "
                        + "vary, %d positive labels%n",
                entries.size(), informative, FEATURE_BITS,
                positives(labels));

        // 2. the production path: the 256-bit features, folded by the trainer.
        RuleInductionEngine production = new RuleInductionEngine(
                new io.matrix.bir.BirRegistry(), null);
        RuleInductionEngine.RuleInductionResult asIs = production.induce(ids, features, labels);

        // 3. the informative projection: the same features, projected onto the bits that
        //    vary, so that the fold becomes the identity.
        long[][] projected = project(features, FEATURE_BITS, informative);
        RuleInductionEngine informativeEngine = new RuleInductionEngine(
                new io.matrix.bir.BirRegistry(), null);
        RuleInductionEngine.RuleInductionResult narrowed =
                informativeEngine.induce(ids, projected, labels);

        System.out.printf("[REP-01] the downstream fidelity: the production fold %.4f, "
                        + "the informative projection %.4f%n",
                asIs.tsetlinFidelity(), narrowed.tsetlinFidelity());

        assertThat(informative)
                .withFailMessage("the informative width must be countable")
                .isGreaterThan(0);
        assertThat(narrowed.tsetlinFidelity())
                .withFailMessage("the informative projection must not be worse than the "
                        + "production fold: what the fold drops must not be what the task needs")
                .isGreaterThanOrEqualTo(asIs.tsetlinFidelity());
    }

    @Test
    @DisplayName("the fold can only make the classes harder to separate")
    void theFoldCanOnlyMakeTheClassesHarderToSeparate() {
        Path log = episodicLog();
        assumeTrue(log != null,
                "the runtime's episodic log is not on this machine: the measurement cannot be "
                        + "made here (the file is the runtime's own data and is not part of the "
                        + "repository)");
        List<EpisodicLog.Entry> all = new EpisodicLog(log).readAll();
        assumeTrue(all.size() > 8, "the episodic log holds too few entries to measure");

        // The production's window, and a label that is a property of the CONTENT (the
        // reply names the capital of Kenya), so that both classes are present and the
        // feature - which hashes the text - can learn the rule.
        int count = Math.min(all.size(), KMaxEnforcer.K_MAX);
        List<EpisodicLog.Entry> window = all.subList(all.size() - count, all.size());
        EpisodeFeatureExtractor extractor = new EpisodeFeatureExtractor();
        TsetlinTrainer trainer = new TsetlinTrainer(16, 64, 200, new Random(SEED));

        java.util.Map<String, int[]> byFeature = new java.util.LinkedHashMap<>();
        java.util.Map<Long, int[]> byFolded = new java.util.LinkedHashMap<>();
        int positives = 0;
        for (EpisodicLog.Entry entry : window) {
            long[] feature = extractor.encode(entry);
            boolean positive = entry.reply().toLowerCase(java.util.Locale.ROOT).contains("nairobi");
            if (positive) {
                positives++;
            }
            String key = java.util.Arrays.toString(feature);
            long folded = trainer.packForTest(feature);
            byFeature.computeIfAbsent(key, ignored -> new int[2])[positive ? 1 : 0]++;
            byFolded.computeIfAbsent(folded, ignored -> new int[2])[positive ? 1 : 0]++;
        }

        double originalAccuracy = bestAccuracy(byFeature.values());
        double foldedAccuracy = bestAccuracy(byFolded.values());
        System.out.printf("[REP-01] the window of %d episodes with %d positive labels: the "
                        + "original features separate them with %.4f accuracy, the folded ones "
                        + "with %.4f%n",
                window.size(), positives, originalAccuracy, foldedAccuracy);

        assertThat(positives)
                .withFailMessage("the sample must carry both classes, or it measures nothing")
                .isGreaterThan(0);
        assertThat(positives)
                .withFailMessage("the sample must carry both classes, or it measures nothing")
                .isLessThan(window.size());
        assertThat(foldedAccuracy)
                .withFailMessage("the fold is a function of the original, so the partition it "
                        + "induces can only be coarser: the accuracy cannot be higher")
                .isLessThanOrEqualTo(originalAccuracy);
    }

    /**
     * The accuracy of the best classifier that sees only the bucket's counts.
     *
     * @param buckets the counts of each bucket (index 0: negative, index 1: positive)
     * @return the accuracy
     */
    private static double bestAccuracy(java.util.Collection<int[]> buckets) {
        int correct = 0;
        int total = 0;
        for (int[] bucket : buckets) {
            correct += Math.max(bucket[0], bucket[1]);
            total += bucket[0] + bucket[1];
        }
        return total == 0 ? 0.0 : (double) correct / total;
    }

    /**
     * How many labels are positive.
     *
     * @param labels the labels
     * @return the count
     */
    private static int positives(boolean[] labels) {
        int count = 0;
        for (boolean label : labels) {
            if (label) {
                count++;
            }
        }
        return count;
    }

    /**
     * Projects the features onto the bits that vary, remapped into the low positions.
     *
     * @param features   the features
     * @param width      the width of the feature
     * @param informative the number of bits that vary
     * @return the projected features
     */
    private static long[][] project(long[][] features, int width, int informative) {
        // A bit varies when some row has a one and some row has a zero.
        boolean[] sawOne = new boolean[width];
        boolean[] sawZero = new boolean[width];
        for (long[] feature : features) {
            for (int bit = 0; bit < width; bit++) {
                if (((feature[0] >>> bit) & 1L) != 0L) {
                    sawOne[bit] = true;
                } else {
                    sawZero[bit] = true;
                }
            }
        }
        int[] mapping = new int[width];
        int next = 0;
        for (int bit = 0; bit < width; bit++) {
            if (sawOne[bit] && sawZero[bit]) {
                mapping[bit] = next++;
            }
        }
        if (next != informative) {
            throw new IllegalStateException("the projection found " + next
                    + " informative bits, the production method counted " + informative);
        }
        long[][] projected = new long[features.length][];
        for (int row = 0; row < features.length; row++) {
            long packed = 0L;
            for (int bit = 0; bit < width; bit++) {
                if (mapping[bit] >= 0 && ((features[row][0] >>> bit) & 1L) != 0L) {
                    packed |= 1L << mapping[bit];
                }
            }
            projected[row] = new long[] {packed};
        }
        return projected;
    }
}
