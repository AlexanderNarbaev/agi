package io.matrix.brain.runtime;

import java.util.BitSet;
import java.util.List;
import java.util.Random;

/**
 * TRUE-W11 — Negative-Selection Anomaly Detector (research iteration #2).
 *
 * <p>Inspired by the biological immune system: a random repertoire of
 * detectors is generated, then those that match "self" patterns are
 * eliminated. Surviving detectors flag "non-self" inputs as anomalies.</p>
 *
 * <p>META-R R-E (biology) — see {@code docs-v2/designs/DESIGN-NN-negative-selection.md}.</p>
 *
 * <p>This is a research stub (iteration 2). It demonstrates the algorithm
 * but is not yet wired into the gateway's safety chain.</p>
 */
public final class NegativeSelectionDetector {

    /** Number of detectors in the repertoire. */
    public static final int REPERTOIRE_SIZE = 1024;
    /** N-gram feature length. */
    public static final int NGRAM = 4;
    /** Bits per feature (FNV hash modulo). */
    public static final int DIM = 1024;
    /** Match threshold: at least this many detectors must fire. */
    public static final int MATCH_THRESHOLD = 2;

    private final BitSet[] detectors;
    private final Random rng;

    public NegativeSelectionDetector() {
        this(new Random(42L));
    }

    public NegativeSelectionDetector(Random rng) {
        this.rng = rng;
        this.detectors = new BitSet[REPERTOIRE_SIZE];
        for (int i = 0; i < REPERTOIRE_SIZE; i++) {
            detectors[i] = randomDetector();
        }
    }

    /** Generate a single random detector (BitSet of N-gram hashes). */
    private BitSet randomDetector() {
        BitSet bs = new BitSet(DIM);
        int n = 32 + rng.nextInt(16);
        for (int b = 0; b < n; b++) bs.set(rng.nextInt(DIM));
        return bs;
    }

    /**
     * Train the detector by removing any detector that matches any
     * self-sample above the per-detector threshold.
     */
    public void train(List<String> selfCorpus) {
        if (selfCorpus == null || selfCorpus.isEmpty()) return;
        for (int i = 0; i < detectors.length; i++) {
            for (String sample : selfCorpus) {
                if (matches(detectors[i], sample)) {
                    detectors[i] = null;
                    break;
                }
            }
        }
    }

    /** Returns true if the input is anomalous (i.e. matches enough detectors). */
    public boolean isAnomalous(String input) {
        if (input == null) return false;
        int fires = 0;
        for (BitSet d : detectors) {
            if (d == null) continue;
            if (matches(d, input)) {
                fires++;
                if (fires >= MATCH_THRESHOLD) return true;
            }
        }
        return false;
    }

    private static boolean matches(BitSet detector, String text) {
        if (text == null || text.length() < NGRAM) return false;
        for (int i = 0; i <= text.length() - NGRAM; i++) {
            String ngram = text.substring(i, i + NGRAM);
            int h = 0x811c9dc5;
            for (int j = 0; j < NGRAM; j++) {
                h ^= ngram.charAt(j);
                h *= 0x01000193;
            }
            int bit = (h & 0x7fffffff) % DIM;
            // Match if at least ONE n-gram's bit is in the detector (positive detection)
            if (detector.get(bit)) return true;
        }
        return false;
    }

    /** Total surviving detectors (not pruned during training). */
    public int survivors() {
        int n = 0;
        for (BitSet d : detectors) if (d != null) n++;
        return n;
    }
}
