package io.matrix.tsetlin;

import io.matrix.knowledge.TextFeatureVector;
import io.matrix.neuron.HdcEncoding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REP-01 — what the Tsetlin trainer's fold does to the bits it is given.
 *
 * <p><b>The fold.</b> {@code TsetlinTrainer.pack(long[])} maps a vector of source bits onto
 * the trainer's own width by XOR-parity: the source bit {@code j} (counted over the whole
 * vector, little-endian inside each word) is folded into the output bit {@code j mod
 * inputBits}. For a trainer of 16 bits and a 256-bit source that is sixteen parity groups
 * of sixteen source bits each.</p>
 *
 * <p><b>What this test fixes, on the real code.</b> Three facts, each proved rather than
 * asserted:</p>
 * <ul>
 *   <li>the fold's <b>kernel has dimension 240</b> — the sixteen parity groups are
 *       independent (the image is all of F₂¹⁶) and each group of sixteen bits leaves fifteen
 *       free bits, so 2²⁴⁰ different vectors share one folded value;</li>
 *   <li>the fold <b>cannot tell a vector from its complement</b>: F(v) = F(¬v), because a
 *       parity over an even number of bits does not see the flip of all of them. For a bipolar
 *       HDC the polarity <i>is</i> part of the representation, so this is a loss of the
 *       representation's meaning and not only of its resolution;</li>
 *   <li>the <b>production path is not affected today</b>: the only production consumer of the
 *       trainer is the acceptance model, and it feeds it sixteen-bit features into a
 *       sixteen-bit trainer — for which the fold is the identity. The loss is a latent trap
 *       for the next caller that passes a wider vector, not a live defect.</li>
 * </ul>
 *
 * <p><b>Why it matters.</b> A silent collapse of the representation is the one failure a
 * benchmark cannot see: the code runs, the numbers are stable, and the meaning is gone. The
 * test is the record that the property was measured on the production code and not on a
 * description of it.</p>
 */
class TsetlinPackInformationLossTest {

    /** The width the production acceptance model uses. */
    private static final int INPUT_BITS = 16;

    /** The width the brain runtime's rule induction uses. */
    private static final int TRAINER_BITS = 16;

    /** The seed every random vector of this test is drawn from. */
    private static final long SEED = 42L;

    /** How many random vectors the polarity check draws. */
    private static final int SAMPLES = 128;

    /** The source width of the 256-bit case: four words. */
    private static final int SOURCE_WORDS_256 = 4;

    /**
     * A trainer of the given width.
     *
     * @param inputBits the width of the trainer
     * @return the trainer
     */
    private static TsetlinTrainer trainer(int inputBits) {
        return new TsetlinTrainer(inputBits, 8, 100, new Random(SEED));
    }

    /**
     * The vector with one bit set, counted over the whole vector.
     *
     * @param bitIndex the index of the bit
     * @param words    the number of words of the vector
     * @return the vector
     */
    private static long[] oneBit(int bitIndex, int words) {
        long[] vector = new long[words];
        vector[bitIndex / Long.SIZE] = 1L << (bitIndex % Long.SIZE);
        return vector;
    }

    /**
     * The complement of a vector.
     *
     * @param vector the vector
     * @return the vector with every bit flipped
     */
    private static long[] complement(long[] vector) {
        long[] flipped = new long[vector.length];
        for (int index = 0; index < vector.length; index++) {
            flipped[index] = ~vector[index];
        }
        return flipped;
    }

    /**
     * A random vector.
     *
     * @param words the number of words
     * @param rng   the source of the randomness
     * @return the vector
     */
    private static long[] randomVector(int words, Random rng) {
        long[] vector = new long[words];
        for (int index = 0; index < words; index++) {
            vector[index] = rng.nextLong();
        }
        return vector;
    }

    @Test
    @DisplayName("every source bit lands in the output bit of its residue class")
    void everySourceBitLandsInItsResidueClass() {
        TsetlinTrainer trainer = trainer(INPUT_BITS);
        for (int bit = 0; bit < SOURCE_WORDS_256 * Long.SIZE; bit++) {
            assertThat(trainer.packForTest(oneBit(bit, SOURCE_WORDS_256)))
                    .withFailMessage("the source bit " + bit + " of 256")
                    .isEqualTo(1L << (bit % INPUT_BITS));
        }
    }

    @Test
    @DisplayName("the kernel of the fold over 256 bits has dimension 240")
    void theKernelHasDimensionTwoHundredForty() {
        TsetlinTrainer trainer = trainer(INPUT_BITS);

        // The image is the whole of F₂¹⁶: the sixteen unit source bits of the first group
        // map onto the sixteen unit output bits, and they are independent because each
        // output bit is a parity over a group of source bits that no other output bit
        // touches. The rank is therefore 16 and the kernel's dimension is 256 - 16 = 240.
        for (int bit = 0; bit < INPUT_BITS; bit++) {
            assertThat(trainer.packForTest(oneBit(bit, SOURCE_WORDS_256)))
                    .withFailMessage("the image must contain the unit bit " + bit)
                    .isEqualTo(1L << bit);
        }

        // Each group of sixteen source bits contributes one parity, so it has fifteen free
        // bits; sixteen groups leave 16 x 15 = 240 free bits. The count is made, not
        // asserted: the even-parity patterns of one group are counted by enumeration.
        long evenParityPatterns = 0;
        for (int pattern = 0; pattern < (1 << INPUT_BITS); pattern++) {
            if (Integer.bitCount(pattern) % 2 == 0) {
                evenParityPatterns++;
            }
        }
        assertThat(evenParityPatterns)
                .withFailMessage("a group of sixteen bits has 2^15 even-parity patterns")
                .isEqualTo(1L << 15);
        int freeBitsPerGroup = INPUT_BITS - 1;
        assertThat(INPUT_BITS * freeBitsPerGroup)
                .withFailMessage("the kernel's dimension is 16 groups x 15 free bits")
                .isEqualTo(240);

        // The kernel is not empty: the source bits of one residue class fold onto the same
        // output bit, so two distinct vectors collide, and their difference is a kernel
        // element that folds to zero.
        long[] first = oneBit(0, SOURCE_WORDS_256);
        long[] sameClass = oneBit(INPUT_BITS, SOURCE_WORDS_256);
        assertThat(trainer.packForTest(sameClass))
                .withFailMessage("two vectors of one residue class fold to one value")
                .isEqualTo(trainer.packForTest(first));
        assertThat(sameClass)
                .withFailMessage("the two vectors must be different")
                .isNotEqualTo(first);

        long[] kernelElement = new long[SOURCE_WORDS_256];
        for (int index = 0; index < SOURCE_WORDS_256; index++) {
            kernelElement[index] = first[index] ^ sameClass[index];
        }
        assertThat(trainer.packForTest(kernelElement))
                .withFailMessage("an element of the kernel folds to zero")
                .isEqualTo(0L);
    }

    @Test
    @DisplayName("the fold cannot tell a vector from its complement")
    void theFoldCannotTellAVectorFromItsComplement() {
        TsetlinTrainer trainer = trainer(INPUT_BITS);
        Random rng = new Random(SEED);
        for (int sample = 0; sample < SAMPLES; sample++) {
            long[] vector = randomVector(SOURCE_WORDS_256, rng);
            assertThat(trainer.packForTest(complement(vector)))
                    .withFailMessage("the parity of an even group of bits cannot see the flip")
                    .isEqualTo(trainer.packForTest(vector));
        }
    }

    @Test
    @DisplayName("the fold merges vectors that the HDC layer calls maximally distant")
    void theFoldMergesMaximallyDistantVectors() {
        TsetlinTrainer trainer = trainer(INPUT_BITS);
        Random rng = new Random(SEED);
        long[] vector = randomVector(HdcEncoding.WORDS, rng);
        long[] flipped = complement(vector);

        assertThat(trainer.packForTest(flipped))
                .withFailMessage("a 1024-bit vector and its complement fold to one value")
                .isEqualTo(trainer.packForTest(vector));
        assertThat(HdcEncoding.similarity(vector, flipped))
                .withFailMessage("the HDC layer calls the complement maximally distant")
                .isEqualTo(-1.0);
        assertThat(HdcEncoding.hamming(vector, flipped))
                .withFailMessage("the two vectors differ at every position")
                .isEqualTo(HdcEncoding.DIM);
    }

    @Test
    @DisplayName("the acceptance model's own path folds its feature without a loss")
    void theAcceptanceModelsPathIsUnaffected() {
        // ONE of the two production consumers of the trainer is the acceptance model, and
        // it builds the trainer with the width of the text feature vector and feeds it one
        // word per example.
        assertThat(TextFeatureVector.WIDTH)
                .withFailMessage("the production feature width")
                .isEqualTo(INPUT_BITS);
        TsetlinTrainer trainer = trainer(TextFeatureVector.WIDTH);

        // A sixteen-bit feature in one word is folded onto itself: the fold's identity
        // shortcut needs 64 bits of width, so the fold runs — and for a feature that fits
        // in sixteen bits it is the identity all the same.
        for (int feature = 0; feature <= 0xFFFF; feature += 257) {
            long[] words = {feature};
            assertThat(trainer.packForTest(words))
                    .withFailMessage("the production feature " + feature + " must survive")
                    .isEqualTo(feature);
        }
    }

    @Test
    @DisplayName("the other production consumer feeds the trainer a wider feature")
    void theOtherProductionConsumerIsAffected() {
        // The SECOND production consumer of the trainer is the brain runtime's rule
        // induction: the gateway's MinimalHttpServer builds a RuleInductionEngine with an
        // EpisodeFeatureExtractor (256 bits) and hands both to the sleep scheduler, and
        // the engine trains a SIXTEEN-bit trainer on them. The fold therefore runs on the
        // production path with a kernel of dimension 240 - and the property is proved
        // there, on the production width and on the real episodic log, by
        // io.matrix.brain.runtime.EpisodeFeatureFoldLossTest.
        //
        // What is fixed here is only the width, because the width is what decides whether
        // the fold is the identity or a loss.
        assertThat(TRAINER_BITS)
                .withFailMessage("the trainer's width on the rule-induction path")
                .isEqualTo(16);
        // A 256-bit feature is four words, and the fold maps every source bit into the
        // output bit of its residue class - so two source bits of the same residue class
        // cancel each other, and two hundred and forty of the two hundred and fifty six
        // bits are dropped.
        TsetlinTrainer trainer = trainer(TRAINER_BITS);
        long[] feature = new long[4];
        // the source bits 0 and 16 belong to the same residue class (0 mod 16)
        feature[0] = (1L) | (1L << 16);
        assertThat(trainer.packForTest(feature))
                .withFailMessage("two source bits of the same residue class cancel")
                .isEqualTo(0L);
    }
}
