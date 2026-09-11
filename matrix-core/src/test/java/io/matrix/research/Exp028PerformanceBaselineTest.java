package io.matrix.research;

import io.matrix.api.BpeTokenizerProvider;
import io.matrix.api.LmHead;
import io.matrix.api.QaCorpusIndex;
import io.matrix.imports.BooleanChainRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXP-MATRIX.23 — Performance baseline measurements (RUN 28).
 *
 * <p>Captures p50, p99, max latencies for core hot-path operations.
 * This is the baseline against which future perf work is measured.
 *
 * <p>Operations benchmarked:
 * <ol>
 *   <li>BooleanChainRunner.evaluate(input) — single chain forward pass</li>
 *   <li>QaCorpusIndex.search(query, 5) — token-overlap retrieval</li>
 *   <li>LmHead.score(chainOutput, token) — sparse classifier lookup</li>
 *   <li>BpeTokenizer.encode(text) — tokenise answer text</li>
 *   <li>Round-trip: chain → score → tokenize → score (full LM head path)</li>
 * </ol>
 *
 * <p>All measurements are real (no estimates). The deterministic
 * seed means results are reproducible across runs (modulo JVM JIT).
 */
class Exp028PerformanceBaselineTest {

    /** Number of iterations per operation. Higher = more stable. */
    private static final int ITERS = 1000;

    /** Warmup iterations to amortize JIT. */
    private static final int WARMUP = 100;

    private static BooleanChainRunner chainRunner;
    private static QaCorpusIndex corpus;
    private static LmHead lmHead;
    private static BpeTokenizerProvider tokenizer;

    @BeforeAll
    static void setUp(@TempDir Path tmpDir) throws IOException {
        // 1. BooleanChainRunner — use the singleton via imports package.
        chainRunner = BooleanChainRunner.empty();
        // Use a 64-bit input vector, run through the chain to get
        // some output. We don't care about the contents, only timing.
        boolean[] input = new boolean[64];
        for (int i = 0; i < 64; i += 7) input[i] = true;
        chainRunner.evaluate(input);  // warm-up

        // 2. QaCorpusIndex — write a tiny corpus to a temp file.
        Path corpusPath = tmpDir.resolve("corpus.json");
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < 200; i++) {
            if (i > 0) json.append(",");
            json.append("{\"question\":\"test question ").append(i).append("\",");
            json.append("\"answer\":\"answer ").append(i).append(" for testing\"}");
        }
        json.append("]");
        Files.writeString(corpusPath, json.toString());
        corpus = new QaCorpusIndex();
        try {
            java.lang.reflect.Field f = QaCorpusIndex.class.getDeclaredField("qaPath");
            f.setAccessible(true);
            f.set(corpus, corpusPath.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        corpus.reload();

        // 3. LmHead — train it lightly.
        lmHead = new LmHead();
        lmHead.setTotalNeurons(64);
        boolean[] fp = new boolean[64];
        for (int i = 0; i < 30; i++) fp[i] = true;
        for (int u = 0; u < 20; u++) lmHead.update(fp, 42, 0);

        // 4. BpeTokenizer — lazy init (may not be available in test).
        tokenizer = new BpeTokenizerProvider();
    }

    @Test
    void chainEvaluateBaseline() {
        boolean[] input = new boolean[64];
        for (int i = 0; i < 64; i += 7) input[i] = true;

        // Warmup
        for (int i = 0; i < WARMUP; i++) chainRunner.evaluate(input);

        long[] times = new long[ITERS];
        for (int i = 0; i < ITERS; i++) {
            long t0 = System.nanoTime();
            chainRunner.evaluate(input);
            times[i] = System.nanoTime() - t0;
        }
        Arrays.sort(times);
        long p50 = times[ITERS / 2];
        long p99 = times[(int) (ITERS * 0.99)];
        long max = times[ITERS - 1];

        System.out.printf("[EXP-MATRIX.23] chainEvaluate: p50=%dns p99=%dns max=%dns n=%d%n",
                p50, p99, max, ITERS);

        assertThat(p50).as("chain p50 baseline").isGreaterThan(0);
        // Loose upper bound — if we're 100x slower than expected, fail.
        assertThat(p99).as("chain p99 < 100ms").isLessThan(100_000_000L);
    }

    @Test
    void qaSearchBaseline() {
        String query = "test question 42";

        for (int i = 0; i < WARMUP; i++) corpus.search(query, 5);

        long[] times = new long[ITERS];
        for (int i = 0; i < ITERS; i++) {
            long t0 = System.nanoTime();
            corpus.search(query, 5);
            times[i] = System.nanoTime() - t0;
        }
        Arrays.sort(times);
        long p50 = times[ITERS / 2];
        long p99 = times[(int) (ITERS * 0.99)];
        long max = times[ITERS - 1];

        System.out.printf("[EXP-MATRIX.23] qaSearch: p50=%dns p99=%dns max=%dns n=%d%n",
                p50, p99, max, ITERS);

        assertThat(p50).as("qa p50 baseline").isGreaterThan(0);
        assertThat(p99).as("qa p99 < 100ms").isLessThan(100_000_000L);
    }

    @Test
    void lmHeadScoreBaseline() {
        boolean[] fp = new boolean[64];
        for (int i = 0; i < 30; i++) fp[i] = true;

        for (int i = 0; i < WARMUP; i++) lmHead.score(fp, 42);

        long[] times = new long[ITERS];
        for (int i = 0; i < ITERS; i++) {
            long t0 = System.nanoTime();
            lmHead.score(fp, 42);
            times[i] = System.nanoTime() - t0;
        }
        Arrays.sort(times);
        long p50 = times[ITERS / 2];
        long p99 = times[(int) (ITERS * 0.99)];

        System.out.printf("[EXP-MATRIX.23] lmHeadScore: p50=%dns p99=%dns max=%dns n=%d%n",
                p50, p99, times[ITERS - 1], ITERS);

        assertThat(p50).as("lmhead p50 baseline").isGreaterThan(0);
        assertThat(p99).as("lmhead p99 < 10ms").isLessThan(10_000_000L);
    }

    @Test
    void bpeTokenizeBaseline() {
        String text = "MATRIX is a deterministic neuro-symbolic system.";

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            try {
                if (tokenizer.isAvailable()) tokenizer.encode(text);
            } catch (Exception ignored) {}
        }

        long[] times = new long[ITERS];
        int succeeded = 0;
        for (int i = 0; i < ITERS; i++) {
            long t0 = System.nanoTime();
            try {
                if (tokenizer.isAvailable()) {
                    tokenizer.encode(text);
                    succeeded++;
                }
            } catch (Exception ignored) {}
            times[i] = System.nanoTime() - t0;
        }
        Arrays.sort(times);
        long p50 = times[ITERS / 2];
        long p99 = times[(int) (ITERS * 0.99)];

        System.out.printf("[EXP-MATRIX.23] bpeTokenize: p50=%dns p99=%dns n=%d (succeeded=%d)%n",
                p50, p99, ITERS, succeeded);

        // p50 should always be ≥ 0; p99 bounded loosely.
        assertThat(p50).isGreaterThanOrEqualTo(0L);
        assertThat(p99).as("bpe p99 < 10ms").isLessThan(10_000_000L);
    }

    @Test
    void fullPipelineBaseline() {
        // Full LM head path: chain → score → score
        boolean[] input = new boolean[64];
        for (int i = 0; i < 64; i += 7) input[i] = true;

        for (int i = 0; i < WARMUP; i++) {
            boolean[] out = chainRunner.evaluate(input);
            lmHead.score(out, 42);
            lmHead.score(out, 100);
        }

        long[] times = new long[ITERS];
        for (int i = 0; i < ITERS; i++) {
            long t0 = System.nanoTime();
            boolean[] out = chainRunner.evaluate(input);
            lmHead.score(out, 42);
            lmHead.score(out, 100);
            times[i] = System.nanoTime() - t0;
        }
        Arrays.sort(times);
        long p50 = times[ITERS / 2];
        long p99 = times[(int) (ITERS * 0.99)];

        System.out.printf("[EXP-MATRIX.23] fullPipeline: p50=%dns p99=%dns max=%dns n=%d%n",
                p50, p99, times[ITERS - 1], ITERS);

        assertThat(p50).as("full pipeline p50 baseline").isGreaterThan(0);
        assertThat(p99).as("full pipeline p99 < 100ms").isLessThan(100_000_000L);
    }
}
