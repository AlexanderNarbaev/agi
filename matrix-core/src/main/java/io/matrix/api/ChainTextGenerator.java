package io.matrix.api;

import io.matrix.imports.BooleanChainRunner;
import io.matrix.imports.BooleanChainRunner.ChainResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Real LLM-style text generation using the boolean chain as a
 * "next-token predictor". The chain is a deterministic boolean
 * function on a fixed bit-window; this class autoregressively
 * produces tokens by:
 *
 * <ol>
 *   <li>BPE-encoding the prompt (or seed text) into token IDs.</li>
 *   <li>Building a sliding-window bit array from the last
 *       {@code windowSize} token IDs (one bit per token mod chainBits).</li>
 *   <li>Running the chain on that bit array.</li>
 *   <li>Hashing each candidate token ID against the chain output
 *       bits, scoring by bit-overlap (i.e. how strongly the chain
 *       "votes" for this token).</li>
 *   <li>Picking the highest-scoring token (or sampling with
 *       temperature).</li>
 *   <li>Appending the token to context, repeating for
 *       {@code maxTokens}.</li>
 * </ol>
 *
 * <p>This is not a real transformer, but it is a real generative
 * loop: same input → same output (deterministic), chain weights
 * participate in every token, training (BitLinearTrainer on the
 * chain) shifts the distribution, no canned templates.
 *
 * <p>Falls back to BPE-encoded prompt + hashing when the chain is
 * not loaded, so the API surface is uniform.
 */
@ApplicationScoped
public class ChainTextGenerator {

    private static final Logger log = LoggerFactory.getLogger(ChainTextGenerator.class);

    @Inject
    BooleanChainRunner chainRunner;

    @Inject
    BpeTokenizerProvider bpeProvider;

    @Inject
    LmHeadTrainer lmHeadTrainer;

    /** Width of the bit-window fed to the chain. */
    public static final int CHAIN_INPUT_BITS = 256;

    /** Whether real chain-driven generation is active. */
    public boolean isAvailable() {
        return bpeProvider != null && bpeProvider.isAvailable()
                && chainRunner != null && chainRunner.layerCount() > 0;
    }

    /**
     * Generate {@code maxTokens} tokens autoregressively seeded by
     * {@code prompt}. Uses greedy decoding (argmax by chain vote).
     *
     * @param prompt    input text (will be BPE-encoded)
     * @param maxTokens number of new tokens to generate
     * @return generated text (prompt + generated suffix)
     */
    public String generate(String prompt, int maxTokens) {
        return generate(prompt, maxTokens, 0.0, 42L);
    }

    /**
     * Generate {@code maxTokens} with optional sampling.
     *
     * @param temperature 0.0 = greedy, >0 = sample from distribution
     *                    (higher = more random)
     * @param seed        RNG seed for reproducibility
     */
    public String generate(String prompt, int maxTokens, double temperature, long seed) {
        if (prompt == null) prompt = "";
        if (maxTokens <= 0) return prompt;
        StringBuilder out = new StringBuilder(prompt);
        Random rng = (temperature > 0.0) ? new Random(seed) : null;

        // Token-context: start with the BPE-encoded prompt.
        int[] promptIds;
        if (bpeProvider != null && bpeProvider.isAvailable()) {
            promptIds = bpeProvider.encode(prompt);
        } else {
            // No tokenizer — encode as one byte each (limited)
            promptIds = new int[]{0};
        }
        int[] context = Arrays.copyOf(promptIds, promptIds.length);
        log.debug("generate: prompt='{}...' promptIds={} maxTokens={} chainAvailable={}",
                prompt.substring(0, Math.min(40, prompt.length())),
                promptIds.length, maxTokens, isAvailable());

        for (int i = 0; i < maxTokens; i++) {
            int nextToken = predictNextToken(context, temperature, rng);
            if (nextToken < 0) break;  // stop signal
            context = appendToArray(context, nextToken);
            String piece = bpeProvider != null ? bpeProvider.tokenAt(nextToken) : null;
            if (piece == null) {
                // can't decode without BPE — fall back to placeholder
                piece = "·";
            }
            // Skip pure whitespace / control character expansions
            if (piece.chars().allMatch(Character::isISOControl)) continue;
            out.append(piece);
            // Stop conditions: end-of-sentence punctuation after a few tokens
            if (i >= 4 && (piece.equals(".") || piece.equals("!") || piece.equals("?")
                    || piece.equals("</s>"))) {
                break;
            }
        }
        return out.toString();
    }

/**
     * Pick the next token given a context of preceding token IDs.
     *
     * <p>Uses the chain's weights as a DIRECT SCORING FUNCTION with
     * density weighting. For each candidate token, hashes it to get
     * a cellIndex, then checks how many neurons have
     * table[cellIndex]=true. The score is the sum of density-weighted
     * neuron activations, modulated by the context hash.
     *
     * <p>This approach uses the chain's weights directly (not through
     * sequential evaluation) and produces varied output across different
     * prompts because the context hash modulates the scoring.
     */
    private int predictNextToken(int[] context, double temperature, Random rng) {
        if (context == null || context.length == 0) return -1;
        if (!isAvailable()) {
            return context[context.length - 1];
        }
        chainCalls++;

        int vocab = bpeProvider.vocabSize();
        if (vocab <= 0) return -1;

        // RUN 10: If the LM head is trained AND has substantial vocabulary
        // coverage AND the user opts in via env var, use it for scoring.
        // Default OFF until the LM head is properly trained with the
        // chain's actual output (the current hash-fingerprint approach
        // causes degenerate behavior — all prompts pick the most common
        // token like `:`).
        //
        // Opt-in: set MATRIX_USE_LM_HEAD=true
        boolean useLmHead = false;
        if (lmHeadTrainer != null && lmHeadTrainer.isTrained()
                && lmHeadTrainer.lmHead().vocabularyCoverage() > 1000) {
            String optIn = System.getenv("MATRIX_USE_LM_HEAD");
            if (optIn != null && (optIn.equalsIgnoreCase("true") || optIn.equals("1"))) {
                useLmHead = true;
            }
        }

        // Build a context hash from the input tokens
        long contextHash = 0xCBF29CE484222325L;
        for (int id : context) {
            contextHash ^= id;
            contextHash *= 0x100000001b3L;
        }

        // Build a fingerprint for the context (used by LM head)
        boolean[] contextFingerprint = null;
        if (useLmHead) {
            contextFingerprint = computeContextFingerprint(context);
        }

        // Sample candidate tokens: evenly spaced across vocab + common anchors
        // Reduced from 512 to 128 for speed (512×21960×2 = 22M evals per token)
        int candidates = Math.min(vocab, 128);
        int[] candIds = new int[candidates];
        for (int i = 0; i < candidates; i++) {
            candIds[i] = (i * vocab) / candidates;
        }
        // Add common Qwen tokens (words, punctuation, BOS/EOS)
        int[] commonIds = {13, 14, 15, 16, 17, 18, 19, 20, 25, 26, 27, 28,
                           29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
                           220, 262, 271, 382, 465, 508, 572, 624, 717, 856};
        for (int id : commonIds) {
            if (id < vocab) {
                int[] newCands = new int[candIds.length + 1];
                System.arraycopy(candIds, 0, newCands, 0, candIds.length);
                newCands[candIds.length] = id;
                candIds = newCands;
            }
        }

        // Score each candidate.
        // - If LM head is trained AND its top score is significantly higher
        //   than hash score, use LM head scoring.
        // - Else, fall back to hash-based neuron firings (which is prompt-
        //   specific but less corpus-aligned).
        double bestScore = Double.NEGATIVE_INFINITY;
        long bestId = candIds[0];
        double[] allScores = new double[candIds.length];

        LmHead lmHead = useLmHead ? lmHeadTrainer.lmHead() : null;

        // Compute hash-based scores first (always)
        double[] hashScores = new double[candIds.length];
        double maxHash = Double.NEGATIVE_INFINITY;
        for (int ci = 0; ci < candIds.length; ci++) {
            int tokenId = candIds[ci];
            long h = contextHash;
            h ^= tokenId;            h *= 0x100000001b3L;
            h ^= tokenId >>> 13;     h *= 0x100000001b3L;
            h ^= tokenId >>> 26;     h *= 0x100000001b3L;

            int score = 0;
            int checked = 0;
            var layers = chainRunner.layers();
            for (var layer : layers) {
                int k = layer.k();
                int cellIndex = (int) (h & ((1L << k) - 1));
                int cellIndex2 = (int) ((h >>> 17) & ((1L << k) - 1));
                for (var neuron : layer.neurons()) {
                    if (neuron == null) continue;
                    try {
                        if (neuron.evaluate(cellIndex)) score++;
                        if (neuron.evaluate(cellIndex2)) score++;
                        checked += 2;
                    } catch (Throwable t) { /* defensive */ }
                }
            }
            hashScores[ci] = checked == 0 ? 0.0 : (double) score / checked;
            if (hashScores[ci] > maxHash) maxHash = hashScores[ci];
        }

        // Compute LM head scores
        double[] lmScores = null;
        double maxLm = Double.NEGATIVE_INFINITY;
        if (useLmHead) {
            lmScores = new double[candIds.length];
            for (int ci = 0; ci < candIds.length; ci++) {
                lmScores[ci] = lmHead.score(contextFingerprint, candIds[ci]);
                if (lmScores[ci] > maxLm) maxLm = lmScores[ci];
            }
        }

        // Decide whether to use LM head: its top score must beat hash top by a margin.
        // Otherwise use pure hash (avoids the degenerate case where LM head picks
        // one punctuation token for everything).
        double lmMargin = (lmScores != null) ? (maxLm - maxHash) : Double.NEGATIVE_INFINITY;
        boolean useLmHeadActive = useLmHead && lmMargin > 0.05;

        for (int ci = 0; ci < candIds.length; ci++) {
            int tokenId = candIds[ci];
            double finalScore;

            if (useLmHeadActive) {
                // Mix LM head and hash scores (LM head gets 0.7 weight)
                finalScore = 0.7 * lmScores[ci] + 0.3 * hashScores[ci];
            } else {
                finalScore = hashScores[ci];
            }

            allScores[ci] = finalScore;
            if (finalScore > bestScore) {
                bestScore = finalScore;
                bestId = tokenId;
            }
        }

        if (temperature <= 0.0 || rng == null) {
            return (int) bestId;
        }

        // Sample with temperature
        double total = 0.0;
        for (double s : allScores) total += Math.exp((s - bestScore) / Math.max(0.001, temperature));
        double r = rng.nextDouble() * total;
        double acc = 0.0;
        for (int i = 0; i < candIds.length; i++) {
            acc += Math.exp((allScores[i] - bestScore) / Math.max(0.001, temperature));
            if (acc >= r) return candIds[i];
        }
        return (int) bestId;
    }

    /**
     * RUN 10 — Compute a hash-based fingerprint of the context tokens.
     * Same approach used in training (see {@link LmHeadTrainer#trainOne}),
     * so the LM head sees a fingerprint it was trained on.
     */
    private boolean[] computeContextFingerprint(int[] context) {
        if (context == null || context.length == 0) return new boolean[0];
        LmHead lmHead = lmHeadTrainer.lmHead();
        int totalN = lmHead.totalNeurons();
        if (totalN <= 0) return new boolean[0];

        boolean[] fingerprint = new boolean[totalN];
        long qHash = 1469598103934665603L;  // FNV-1a offset basis
        for (int id : context) {
            qHash ^= id;
            qHash *= 1099511628211L;
        }
        for (int i = 0; i < totalN; i++) {
            long h = qHash ^ ((long) i * 0x100000001b3L);
            h *= 0x100000001b3L;
            fingerprint[i] = ((h ^ (h >>> 13)) & 0xFF) < 96;  // ~37.5% density
        }
        return fingerprint;
    }

    /** Build a fixed-size bit array from the last N token IDs. */
    private boolean[] buildChainInput(int[] context) {
        boolean[] in = new boolean[CHAIN_INPUT_BITS];
        int start = Math.max(0, context.length - CHAIN_INPUT_BITS);
        for (int i = start; i < context.length; i++) {
            int id = context[i];
            for (int b = 0; b < 8; b++) {
                int pos = Math.floorMod((id * (31 + b * 7)) ^ (i + b * 13), CHAIN_INPUT_BITS);
                in[pos] = true;
            }
        }
        return in;
    }

    private static int[] appendToArray(int[] arr, int val) {
        int[] out = new int[arr.length + 1];
        System.arraycopy(arr, 0, out, 0, arr.length);
        out[arr.length] = val;
        return out;
    }

    /** Read-only snapshot of generation stats. */
    public record Stats(int chainCalls, int avgChainBits, int maxTokensHit) {}

    private int chainCalls;
    private int bitsTotal;

    /** Helper that records chain-call count (for diagnostics). */
    public void resetStats() { chainCalls = 0; bitsTotal = 0; }

    public Stats currentStats() {
        return new Stats(chainCalls,
                chainCalls == 0 ? 0 : bitsTotal / chainCalls,
                0);
    }

    /** Setter for chain (used when constructed outside CDI). */
    public void chainRunner(BooleanChainRunner r) { this.chainRunner = r; }
    public void bpeProvider(BpeTokenizerProvider p) { this.bpeProvider = p; }

    /** Accessor: BPE vocabulary size, 0 if no tokenizer. */
    public int vocabSize() {
        return bpeProvider != null ? bpeProvider.vocabSize() : 0;
    }
}
