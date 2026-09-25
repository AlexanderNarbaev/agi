package io.matrix.brain.runtime.stages;

import io.matrix.brain.runtime.BrcStep;

import java.util.BitSet;
import java.util.List;

/**
 * MIND-W1 — Stage 2: Signal.
 *
 * <p>Tokenizes and normalizes input into a deterministic BitSet observation
 * (256-dim feature vector). No LLM. No wall-clock. Pure hashing of tokens.</p>
 */
public final class SignalStage {

    public static final int DIM = 256;

    public record SignalObservation(BitSet features, int tokenCount, List<String> tokens) {
        public SignalObservation {
            features = (BitSet) features.clone();
            tokens = List.copyOf(tokens);
        }
    }

    public SignalObservation encode(String input, List<BrcStep> trace) {
        if (input == null) {
            BrcStep step = BrcStep.of("SIGNAL", true, 0.95,
                List.of("tokens=0", "dim=" + DIM));
            trace.add(step);
            return new SignalObservation(new BitSet(DIM), 0, List.of());
        }
        String[] tokens = input.toLowerCase()
            .replaceAll("[^a-z0-9+\\-*/= а-яё\\s]", " ")
            .trim()
            .split("\\s+");
        // Filter out empty tokens that split() may produce from leading/trailing spaces.
        List<String> tokenList = new java.util.ArrayList<>();
        for (String t : tokens) {
            if (!t.isBlank()) tokenList.add(t);
        }
        BitSet bits = new BitSet(DIM);
        for (String t : tokenList) {
            // FNV-1a 32-bit hash, modulo DIM
            int h = 0x811c9dc5;
            for (int i = 0; i < t.length(); i++) {
                h ^= t.charAt(i);
                h *= 0x01000193;
            }
            bits.set((h & 0x7fffffff) % DIM);
        }
        BrcStep step = BrcStep.of("SIGNAL", true, 0.95,
            List.of("tokens=" + tokenList.size(), "dim=" + DIM));
        trace.add(step);
        return new SignalObservation(bits, tokenList.size(), List.copyOf(tokenList));
    }
}
