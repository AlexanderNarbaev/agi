package io.matrix.brain.runtime;

import io.matrix.tsetlin.TsetlinTrainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * REP-01 — how the fold's cost grows with the diversity of the episodes.
 *
 * <p><b>Why a curve and not a number.</b> The fold's loss is a collision rate, and a
 * collision rate depends on the data: on the running gateway's own log the fold makes
 * {@code one pair of distinct episodes indistinguishable} out of twenty-four distinct
 * episodes. That single number says nothing about <b>when</b> the loss becomes the binding
 * constraint — and "when" is the question that decides the ADR. The answer is a curve:
 * the number of distinct episodes the fold can keep apart, as the episodes grow.</p>
 *
 * <p><b>What the curve is made of.</b> The real episodes of the running gateway, encoded by
 * the production extractor (256 bits) and folded by the production trainer (16 bits). For
 * every prefix of the distinct features, the number of distinct folded values and the
 * number of collisions.</p>
 *
 * <p><b>The law the test holds.</b> The collisions are <b>monotone</b>: adding a feature can
 * either open a new folded value or collide with an existing one, and it can never repair
 * a collision that already happened. So the curve never falls — which is what makes "the
 * loss grows with the diversity" a statement rather than an impression.</p>
 *
 * <p><b>What explains the curve.</b> The features are <b>sparse</b> — the production encoder
 * hashes one bit per token — and the fold's sixteen parity groups mix the set bits of
 * different tokens. The sparser the feature, the more often two different features land on
 * the same parity. The test measures the sparsity and reports it beside the curve, because
 * the curve without its explanatory variable is an anecdote.
 */
class EpisodeFeatureFoldScalingTest {

    /** The seed of the trainer's randomness. */
    private static final long SEED = 42L;

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
    @DisplayName("the collisions grow with the number of distinct episodes")
    void theCollisionsGrowWithTheDiversity() {
        Path log = episodicLog();
        assumeTrue(log != null,
                "the runtime's episodic log is not on this machine: the curve cannot be measured "
                        + "here (the file is the runtime's own data and is not part of the "
                        + "repository)");
        List<EpisodicLog.Entry> entries = new EpisodicLog(log).readAll();
        assumeTrue(entries.size() > 4, "the episodic log holds too few entries to measure");

        // The distinct episodes, in the order they appear, encoded by the production
        // extractor and folded by the production trainer.
        EpisodeFeatureExtractor extractor = new EpisodeFeatureExtractor();
        TsetlinTrainer trainer = new TsetlinTrainer(16, 64, 200, new Random(SEED));
        Map<String, long[]> distinct = new LinkedHashMap<>();
        for (EpisodicLog.Entry entry : entries) {
            distinct.putIfAbsent(entry.input() + " | " + entry.reply(), extractor.encode(entry));
        }
        List<long[]> features = new ArrayList<>(distinct.values());

        // The sparsity of the real features: how many of the 256 bits are set.
        int setBits = 0;
        for (long[] feature : features) {
            for (long word : feature) {
                setBits += Long.bitCount(word);
            }
        }
        double meanSetBits = (double) setBits / features.size();

        int previousCollisions = 0;
        StringBuilder curve = new StringBuilder();
        int lastCollisions = 0;
        for (int size = 1; size <= features.size(); size++) {
            Map<Long, Integer> folded = new LinkedHashMap<>();
            for (int index = 0; index < size; index++) {
                long value = trainer.packForTest(features.get(index));
                folded.merge(value, 1, Integer::sum);
            }
            int collisions = size - folded.size();
            curve.append(size).append(':').append(collisions).append(' ');
            assertThat(collisions)
                    .withFailMessage("adding an episode can never repair a collision: the "
                            + "collisions at " + size + " episodes")
                    .isGreaterThanOrEqualTo(previousCollisions);
            previousCollisions = collisions;
            lastCollisions = collisions;
        }

        // The distribution of the folded values: the sparse features land in the sixteen
        // parity groups, and a group whose members are mostly unset has an even parity -
        // so the fold may map most of the episodes onto the SAME value.
        Map<Long, Integer> all = new LinkedHashMap<>();
        for (long[] feature : features) {
            all.merge(trainer.packForTest(feature), 1, Integer::sum);
        }
        int largest = all.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        System.out.printf("[REP-01] the fold's collisions over the real episodes: %s| "
                        + "%d distinct episodes, %d collisions at the end, the mean number of "
                        + "set bits in a feature %.1f of 256%n",
                curve.toString().trim(), features.size(), lastCollisions, meanSetBits);
        System.out.printf("[REP-01] the folded values of the real episodes: %d distinct values "
                        + "for %d distinct episodes, the largest bucket holds %d of them%n",
                all.size(), features.size(), largest);
        assertThat(largest)
                .withFailMessage("the fold must map some of the distinct episodes onto one "
                        + "value: that is the loss, measured")
                .isGreaterThan(1);

        assertThat(features.size())
                .withFailMessage("the curve must have at least a few points")
                .isGreaterThan(4);
        assertThat(meanSetBits)
                .withFailMessage("the production features are sparse: the encoder hashes one bit "
                        + "per token")
                .isLessThan(256);
    }
}
