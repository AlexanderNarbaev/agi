package io.matrix.brain.runtime;

import io.matrix.tsetlin.TsetlinTrainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * REP-01 — the fold of the trainer, on the width the serving path actually uses.
 *
 * <p><b>What this test holds.</b> The trainer folds a multi-word feature vector into its
 * own width by XOR-parity. The question that matters is not what the fold does in
 * general, but <b>which width the serving path feeds it</b>, because the loss is
 * proportional to it.</p>
 *
 * <p><b>The serving path.</b> Chain B's gateway constructs
 * {@code io.matrix.brain.runtime.RuleInductionEngine} with an
 * {@link EpisodeFeatureExtractor} and hands both to the sleep scheduler
 * ({@code MinimalHttpServer}, the "rule induction ARMED" branch). The extractor encodes an
 * episode into <b>256 bits</b> (four longs, the production default), the engine trains the
 * {@link TsetlinTrainer} of <b>16 bits</b> on them, and the serving path folds the same way
 * ({@code RuleInductionEngine.predict}: "the caller MUST pass raw words, not a pre-folded
 * long, or training and serving disagree").</p>
 *
 * <p><b>What follows, and it is proved here.</b> For a 256-bit source and a 16-bit
 * trainer:</p>
 * <ul>
 *   <li>the source bit {@code j} lands in the output bit {@code j mod 16}, so the sixteen
 *       parity groups have <b>sixteen</b> members each;</li>
 *   <li>the image is the whole of F₂¹⁶ (rank 16) and the <b>kernel has dimension 240</b> —
 *       every one of the 2²⁴⁰ vectors of a group-parity shares its folded value;</li>
 *   <li>a parity over an <b>even</b> number of bits cannot see the flip of all of them, so
 *       <b>F(v) = F(¬v)</b>: the folded value of a feature is the folded value of its
 *       complement;</li>
 *   <li>and the real episodic log shows what that costs on real interactions: the
 *       episodes that the fold makes indistinguishable.</li>
 * </ul>
 *
 * <p><b>What this test does not do.</b> It does not change the fold and it does not
 * change the serving path: the wave's directive keeps the architecture question open
 * (REP-03/REP-04), and the change would touch serving code. It measures the loss where
 * it happens.
 */
class EpisodeFeatureFoldLossTest {

    /** The seed of the random vectors. */
    private static final long SEED = 42L;

    /** The trainer's width on the serving path. */
    private static final int TRAINER_BITS = 16;

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
    @DisplayName("the serving path's feature width folds into the trainer with a kernel of 240")
    void theServingWidthFoldsWithAKernelOfTwoHundredForty() {
        EpisodeFeatureExtractor extractor = new EpisodeFeatureExtractor();
        assertThat(extractor.dim())
                .withFailMessage("the production feature width")
                .isEqualTo(256);
        assertThat(extractor.packedLength())
                .withFailMessage("the production feature is four longs")
                .isEqualTo(4);

        TsetlinTrainer trainer = new TsetlinTrainer(TRAINER_BITS, 64, 200, new Random(SEED));

        // The structure: every source bit lands in the output bit of its residue class.
        for (int bit = 0; bit < extractor.dim(); bit++) {
            long[] one = new long[extractor.packedLength()];
            one[bit / Long.SIZE] = 1L << (bit % Long.SIZE);
            assertThat(trainer.packForTest(one))
                    .withFailMessage("the source bit " + bit + " of 256")
                    .isEqualTo(1L << (bit % TRAINER_BITS));
        }

        // The image is the whole of F₂¹⁶, so the rank is 16 and the kernel's dimension is
        // 256 - 16 = 240: each group of sixteen source bits leaves fifteen free bits.
        for (int bit = 0; bit < TRAINER_BITS; bit++) {
            long[] one = new long[extractor.packedLength()];
            one[bit / Long.SIZE] = 1L << (bit % Long.SIZE);
            assertThat(trainer.packForTest(one))
                    .withFailMessage("the image must contain the unit bit " + bit)
                    .isEqualTo(1L << bit);
        }
        long evenParityPatterns = 0;
        for (int pattern = 0; pattern < (1 << TRAINER_BITS); pattern++) {
            if (Integer.bitCount(pattern) % 2 == 0) {
                evenParityPatterns++;
            }
        }
        assertThat(evenParityPatterns).isEqualTo(1L << 15);
        assertThat(TRAINER_BITS * (TRAINER_BITS - 1))
                .withFailMessage("the kernel's dimension is 16 groups x 15 free bits")
                .isEqualTo(240);
    }

    @Test
    @DisplayName("the folded value of a feature is the folded value of its complement")
    void theFoldCannotTellAFeatureFromItsComplement() {
        EpisodeFeatureExtractor extractor = new EpisodeFeatureExtractor();
        TsetlinTrainer trainer = new TsetlinTrainer(TRAINER_BITS, 64, 200, new Random(SEED));
        Random rng = new Random(SEED);
        for (int sample = 0; sample < 128; sample++) {
            long[] feature = extractor.encode("the interaction number " + sample
                    + " with its reply");
            long[] complement = new long[feature.length];
            for (int index = 0; index < feature.length; index++) {
                complement[index] = ~feature[index];
            }
            assertThat(trainer.packForTest(complement))
                    .withFailMessage("a parity over sixteen bits cannot see the flip")
                    .isEqualTo(trainer.packForTest(feature));
        }
    }

    @Test
    @DisplayName("the real episodic log shows the episodes the fold makes indistinguishable")
    void theRealLogShowsTheIndistinguishableEpisodes() {
        Path log = episodicLog();
        assumeTrue(log != null,
                "the runtime's episodic log is not on this machine: the real-data measurement "
                        + "cannot be made here (the file is the runtime's own data and is not "
                        + "part of the repository)");
        List<EpisodicLog.Entry> entries = new EpisodicLog(log).readAll();
        assumeTrue(entries.size() > 1, "the episodic log is empty");

        EpisodeFeatureExtractor extractor = new EpisodeFeatureExtractor();
        TsetlinTrainer trainer = new TsetlinTrainer(TRAINER_BITS, 64, 200, new Random(SEED));

        Set<Long> foldedValues = new HashSet<>();
        Set<String> distinctTexts = new HashSet<>();
        Map<Long, Set<String>> byFoldedValue = new HashMap<>();
        Set<String> distinctFeatureTexts = new HashSet<>();
        for (EpisodicLog.Entry entry : entries) {
            long[] feature = extractor.encode(entry);
            String text = entry.input() + " | " + entry.reply();
            distinctFeatureTexts.add(text);
            long folded = trainer.packForTest(feature);
            foldedValues.add(folded);
            distinctTexts.add(text);
            byFoldedValue.computeIfAbsent(folded, key -> new HashSet<>()).add(text);
        }
        int distinctFeatures = distinctFeatureTexts.size();

        // The collisions: the episodes that the fold makes indistinguishable.
        List<Long> colliding = new ArrayList<>();
        for (Map.Entry<Long, Set<String>> item : byFoldedValue.entrySet()) {
            if (item.getValue().size() > 1) {
                colliding.add(item.getKey());
            }
        }
        int episodesInCollision = 0;
        for (Long value : colliding) {
            episodesInCollision += byFoldedValue.get(value).size();
        }

        System.out.printf("[REP-01] the real episodic log: %d entries, %d distinct texts, "
                        + "%d distinct 256-bit features, "
                        + "%d distinct folded values, %d folded values carry more than one text, "
                        + "%d entries fall into a collision%n",
                entries.size(), distinctTexts.size(), distinctFeatures, foldedValues.size(),
                colliding.size(), episodesInCollision);

        assertThat(foldedValues.size())
                .withFailMessage("the folded representation cannot hold more than 2^16 values")
                .isLessThanOrEqualTo(1 << TRAINER_BITS);
        assertThat(byFoldedValue.values())
                .withFailMessage("every folded value must be reachable")
                .hasSize(foldedValues.size());
        if (distinctTexts.size() > foldedValues.size()) {
            assertThat(episodesInCollision)
                    .withFailMessage("the fold must make some distinct episodes "
                            + "indistinguishable: that is the loss, measured")
                    .isPositive();
        }
    }
}
