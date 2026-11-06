package io.matrix.benchmark;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.OrtSession.SessionOptions;
import ai.onnxruntime.providers.OrtCUDAProviderOptions;
import io.matrix.api.QwenOnnxBridge;
import io.matrix.bir.BooleanRuntime;
import io.matrix.bir.TtForm;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * W48-09 — the measurement harness for the ONNX CPU-vs-CUDA benchmark.
 *
 * <p>It measures FIVE series, and they are NOT averaged into one number:
 *
 * <ol>
 *   <li><b>{@code transformer_forward}</b> — one forward pass of the exported
 *       Qwen2.5-0.5B graph at sequence length 32, measured with
 *       {@code OrtSession.run} and nothing else. This is the cost of the
 *       compute itself.</li>
 *   <li><b>{@code transformer_forward_adapter}</b> — the SAME forward pass
 *       measured through MATRIX's own call
 *       ({@code OnnxRuntimeAdapter.greedyNextToken}), which additionally reads
 *       the whole logits tensor back from the device and takes the argmax of
 *       its last position. The difference between the two is the cost of the
 *       readback, and on the CUDA provider it is not small.</li>
 *   <li><b>{@code point_call_onnx}</b> — one forward pass of the teacher FFN
 *       graph ({@code models/teacher/teacher_ffn16.onnx}) with a batch of one.
 *       This is the ONNX "point call": the smallest unit of GPU work, and the
 *       one whose cost is dominated by the launch overhead rather than by the
 *       arithmetic.</li>
 *   <li><b>{@code bir_point_call}</b> — one evaluation of MATRIX's own boolean
 *       runtime ({@code BooleanRuntime.evaluate}). It is pure Java and has NO
 *       GPU implementation, so it is measured in every run to show what the GPU
 *       does and does not change.</li>
 *   <li><b>{@code end_to_end}</b> — the serving path: tokenise, generate
 *       autoregressively through {@link QwenOnnxBridge}, detokenise.</li>
 * </ol>
 *
 * <p><b>The harness FAILS when the CUDA execution provider did not execute.</b>
 * A CPU-only run reported as a GPU result is worse than no result, so the proof
 * is not "the provider was registered" but "the execution profile names the
 * provider that ran the node". The profile is read from a SEPARATE, profiled
 * session, so that the timed sessions carry no profiling overhead — which keeps
 * the CPU and the CUDA timings comparable.
 *
 * <p>The harness is launched by {@code scripts/gpu-benchmark.sh} with an
 * explicit classpath and explicit JVM flags, so that the CPU run and the CUDA
 * run differ in exactly one thing: the provider the session is configured with.
 *
 * <p>Exit codes:
 * <ul>
 *   <li>{@code 0} — the run completed and every assertion held</li>
 *   <li>{@code 2} — the harness itself failed (bad arguments, missing model)</li>
 *   <li>{@code 3} — the CUDA execution provider was requested but did NOT
 *       execute. This is the hard failure, and it is the reason this class
 *       exists.</li>
 *   <li>{@code 4} — the requested provider could not be registered at all</li>
 * </ul>
 */
public final class GpuBenchmarkHarness {

    /** The exit code for "the CUDA provider was requested but did not execute". */
    public static final int EXIT_CUDA_NOT_EXECUTED = 3;
    /** The exit code for "the requested provider could not be registered". */
    public static final int EXIT_PROVIDER_UNAVAILABLE = 4;
    /** The exit code for a harness-level failure. */
    public static final int EXIT_HARNESS_ERROR = 2;

    /** The name ONNX Runtime gives the CUDA provider. */
    public static final String CUDA_PROVIDER_NAME = "CUDAExecutionProvider";

    /** The field in the execution profile that names the provider of a node. */
    private static final Pattern PROVIDER_FIELD =
            Pattern.compile("\"provider\"\\s*:\\s*\"([^\"]+)\"");

    /** The vocabulary size of the exported Qwen graph, from its output shape. */
    private static final int QWEN_VOCAB = 151936;

    private GpuBenchmarkHarness() {
    }

    // ── the configuration ────────────────────────────────────────────────────

    /**
     * What one run measures.
     *
     * @param label the name of this configuration ("cpu", "cuda", ...)
     * @param provider "cpu" or "cuda"
     * @param transformerModel the exported transformer graph
     * @param teacherModel the teacher FFN graph (the point call)
     * @param bridgeModelDir the directory the bridge loads the tokenizer from
     * @param seqLen the transformer sequence length
     * @param warmup the number of untimed warm-up iterations per series
     * @param iterations the number of TIMED iterations per series
     * @param prompt the prompt the end-to-end path is given
     * @param maxNewTokens how many tokens the end-to-end path generates
     * @param profileDirectory where the execution profile is written, or null
     * @param out where the JSON result is written, or null for stdout only
     * @param requireCuda whether the absence of CUDA execution is a failure
     */
    record Config(String label,
                  String provider,
                  Path transformerModel,
                  Path teacherModel,
                  Path bridgeModelDir,
                  int seqLen,
                  int warmup,
                  int iterations,
                  String prompt,
                  int maxNewTokens,
                  Path profileDirectory,
                  Path out,
                  boolean requireCuda) {
    }

    // ── the statistics ───────────────────────────────────────────────────────

    /**
     * The dispersion of a series of samples.
     *
     * @param count how many samples
     * @param min the smallest sample
     * @param median the median (the mean of the two central samples for an even count)
     * @param p95 the 95th percentile by the nearest-rank method
     * @param max the largest sample
     * @param mean the arithmetic mean
     * @param stddev the sample standard deviation (the n-1 denominator)
     */
    public record Stats(int count, double min, double median, double p95,
                        double max, double mean, double stddev) {
    }

    /**
     * The dispersion of a series of samples.
     *
     * <p>The median is the reported central value because the series is a set of
     * timed iterations, not a distribution: one slow iteration (a page fault, a
     * scheduler tick) must not be allowed to define the result.
     *
     * <p>The 95th percentile uses the nearest-rank method — the smallest value
     * at or above which 95 % of the samples lie — so that it is always an
     * OBSERVED sample and never an interpolation between two of them.
     *
     * <p>The standard deviation is the SAMPLE standard deviation, the n-1
     * denominator, because the series is a sample of the process's behaviour and
     * not the whole population.
     *
     * @param samples the samples; must not be empty
     * @return the dispersion
     */
    public static Stats stats(double[] samples) {
        if (samples == null || samples.length == 0) {
            throw new IllegalArgumentException("at least one sample is required");
        }
        double[] sorted = samples.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        double min = sorted[0];
        double max = sorted[n - 1];
        double median = (n % 2 == 1)
                ? sorted[n / 2]
                : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
        int rank = (int) Math.ceil(0.95 * n);
        if (rank < 1) {
            rank = 1;
        }
        if (rank > n) {
            rank = n;
        }
        double p95 = sorted[rank - 1];
        double sum = 0.0;
        for (double s : sorted) {
            sum += s;
        }
        double mean = sum / n;
        double variance = 0.0;
        for (double s : sorted) {
            double delta = s - mean;
            variance += delta * delta;
        }
        double stddev = n > 1 ? Math.sqrt(variance / (n - 1)) : 0.0;
        return new Stats(n, min, median, p95, max, mean, stddev);
    }

    // ── one measured series ──────────────────────────────────────────────────

    /**
     * One timed series: the samples and their dispersion.
     *
     * @param name the name of the series
     * @param unit the unit of the samples ("ms" or "us")
     * @param warmup how many untimed warm-up iterations ran
     * @param samples the timed samples, in the order they were measured
     */
    record Series(String name, String unit, int warmup, double[] samples, Stats stats) {
    }

    // ── entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        int code;
        try {
            code = run(args);
        } catch (Throwable failure) {
            System.out.println("[GPU-BENCH] HARNESS FAILURE: " + failure);
            failure.printStackTrace(System.out);
            code = EXIT_HARNESS_ERROR;
        }
        System.out.flush();
        System.exit(code);
    }

    static int run(String[] args) throws Exception {
        Config config = parseArgs(args);
        PrintStream out = System.out;

        out.println("[GPU-BENCH] label=" + config.label()
                + " provider=" + config.provider()
                + " seqLen=" + config.seqLen()
                + " warmup=" + config.warmup()
                + " iterations=" + config.iterations());
        out.println("[GPU-BENCH] java=" + System.getProperty("java.version")
                + " jvm=" + System.getProperty("java.vm.name")
                + " availableProcessors=" + Runtime.getRuntime().availableProcessors());

        requireFile(config.transformerModel(), "the transformer graph");
        requireFile(config.teacherModel(), "the teacher graph");

        OrtEnvironment environment = OrtEnvironment.getEnvironment();
        List<String> availableProviders = new ArrayList<>();
        for (ai.onnxruntime.OrtProvider p : OrtEnvironment.getAvailableProviders()) {
            availableProviders.add(p.name());
        }
        out.println("[GPU-BENCH] onnxruntime.availableProviders=" + availableProviders);

        // ── the CUDA proof ────────────────────────────────────────────────
        // A SEPARATE, profiled session. The timed sessions carry no profiling
        // overhead, so the CPU and the CUDA timings stay comparable.
        Map<String, Integer> profileProviders = new TreeMap<>();
        boolean cudaObserved = false;
        if (config.profileDirectory() != null) {
            Files.createDirectories(config.profileDirectory());
            profileProviders = captureExecutionProfile(environment, config);
            cudaObserved = profileProviders.containsKey(CUDA_PROVIDER_NAME);
            out.println("[GPU-BENCH] executionProfile.providers=" + profileProviders);
            out.println("[GPU-BENCH] executionProfile.cudaObserved=" + cudaObserved);
        }

        if (config.requireCuda() && !cudaObserved) {
            out.println("[GPU-BENCH] HARD FAILURE: the CUDA execution provider was requested "
                    + "but the execution profile does not name " + CUDA_PROVIDER_NAME
                    + ". The observed providers are " + profileProviders
                    + ". A CPU-only run must never be reported as a GPU result.");
            out.flush();
            return EXIT_CUDA_NOT_EXECUTED;
        }

        // ── the measured series ────────────────────────────────────────────
        List<Series> series = new ArrayList<>();

        // 1. the transformer forward pass, at the sequence length under test
        series.add(measureTransformerForward(environment, config));

        // 2. the ONNX point call — the teacher FFN, one sample at a time
        series.add(measurePointCall(environment, config));

        // 3. MATRIX's own boolean runtime — the CPU counterpart of the point call
        series.add(measureBirPointCall(config));

        // 4. MATRIX's own forward call and the end-to-end serving path, on the
        //    SAME session, so that the two are directly comparable
        series.addAll(measureServingPath(config));

        for (Series s : series) {
            out.println(String.format(Locale.ROOT,
                    "[GPU-BENCH] series=%s unit=%s warmup=%d n=%d min=%.6f median=%.6f "
                            + "p95=%.6f max=%.6f mean=%.6f stddev=%.6f",
                    s.name(), s.unit(), s.warmup(), s.stats().count(),
                    s.stats().min(), s.stats().median(), s.stats().p95(),
                    s.stats().max(), s.stats().mean(), s.stats().stddev()));
        }

        String json = renderJson(config, environment, availableProviders,
                profileProviders, cudaObserved, series);
        if (config.out() != null) {
            Files.createDirectories(config.out().toAbsolutePath().getParent());
            Files.writeString(config.out(), json);
            out.println("[GPU-BENCH] result written to " + config.out().toAbsolutePath());
        }
        out.println("[GPU-BENCH] OK");
        out.flush();
        return 0;
    }

    // ── the four measurements ───────────────────────────────────────────────

    private static Series measureTransformerForward(OrtEnvironment environment, Config config)
            throws OrtException {
        long[] tokenIds = deterministicTokenIds(config.seqLen(), 0x5EEDL);
        try (OrtSession session = createSession(environment, config, config.transformerModel());
             OnnxTensor inputIds = OnnxTensor.createTensor(environment,
                     LongBuffer.wrap(tokenIds), new long[]{1, config.seqLen()});
             OnnxTensor attentionMask = OnnxTensor.createTensor(environment,
                     LongBuffer.wrap(ones(config.seqLen())), new long[]{1, config.seqLen()});
             OnnxTensor positionIds = OnnxTensor.createTensor(environment,
                     LongBuffer.wrap(positions(config.seqLen())), new long[]{1, config.seqLen()})) {
            Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
            inputs.put("input_ids", inputIds);
            inputs.put("attention_mask", attentionMask);
            inputs.put("position_ids", positionIds);

            for (int i = 0; i < config.warmup(); i++) {
                runOnce(session, inputs);
            }
            double[] samples = new double[config.iterations()];
            for (int i = 0; i < config.iterations(); i++) {
                long t0 = System.nanoTime();
                runOnce(session, inputs);
                samples[i] = (System.nanoTime() - t0) / 1_000_000.0;
            }
            return new Series("transformer_forward", "ms", config.warmup(), samples, stats(samples));
        }
    }

    private static void runOnce(OrtSession session, Map<String, OnnxTensor> inputs)
            throws OrtException {
        try (OrtSession.Result ignored = session.run(inputs)) {
            // the result is consumed and released; the graph ran
        }
    }

    private static Series measurePointCall(OrtEnvironment environment, Config config)
            throws OrtException {
        float[] features = deterministicFeatures(16);
        try (OrtSession session = createSession(environment, config, config.teacherModel());
             OnnxTensor input = OnnxTensor.createTensor(environment,
                     FloatBuffer.wrap(features), new long[]{1, features.length})) {
            Map<String, OnnxTensor> inputs = Map.of("x", input);
            for (int i = 0; i < config.warmup(); i++) {
                runOnce(session, inputs);
            }
            double[] samples = new double[config.iterations()];
            for (int i = 0; i < config.iterations(); i++) {
                long t0 = System.nanoTime();
                runOnce(session, inputs);
                samples[i] = (System.nanoTime() - t0) / 1_000_000.0;
            }
            return new Series("point_call_onnx", "ms", config.warmup(), samples, stats(samples));
        }
    }

    private static Series measureBirPointCall(Config config) {
        // The same shape of function the JMH gate measures: a majority-of-4
        // threshold over eight literals, so that the number can be compared
        // with docs-v2/engineering/JMH-GATE-EVIDENCE.md.
        int k = 8;
        long[] table = new long[(1 << k) / 64 + 1];
        for (int i = 0; i < (1 << k); i++) {
            if (Integer.bitCount(i) >= 4) {
                table[i >>> 6] |= (1L << (i & 63));
            }
        }
        TtForm form = new TtForm(k, table, "w48-gpu-benchmark", 1.0);
        long[] input = {0xFFL};

        for (int i = 0; i < config.warmup(); i++) {
            BooleanRuntime.evaluate(form, input);
        }
        double[] samples = new double[config.iterations()];
        for (int i = 0; i < config.iterations(); i++) {
            long t0 = System.nanoTime();
            BooleanRuntime.evaluate(form, input);
            samples[i] = (System.nanoTime() - t0) / 1000.0; // microseconds
        }
        return new Series("bir_point_call", "us", config.warmup(), samples, stats(samples));
    }

    /**
     * The two measurements that share MATRIX's own serving session.
     *
     * <p>The first is MATRIX's own forward call — {@code greedyNextToken} on the
     * adapter — which is what the serving path calls once per generated token.
     * It is NOT the same thing as the raw {@code session.run} measured above:
     * it additionally reads the whole logits tensor back from the device and
     * takes the argmax of its last position. The difference is large on the
     * CUDA provider, because the readback moves 19.5 MB per call.
     *
     * <p>The second is the end-to-end path: tokenise, generate autoregressively,
     * detokenise. Both are measured on the SAME session, so the provider is the
     * only thing that differs between the two configurations.
     *
     * @param config the run configuration
     * @return the two series
     */
    private static List<Series> measureServingPath(Config config)
            throws IOException, OrtException {
        QwenOnnxBridge bridge = new QwenOnnxBridge(config.bridgeModelDir());
        bridge.useGpu("cuda".equals(config.provider()));
        bridge.disableCache();
        bridge.setMaxNewTokens(config.maxNewTokens());
        // The EOS stop is disabled ON PURPOSE: the number of forward passes must
        // be the same in the CPU run and in the CUDA run, whatever the token
        // sequence turns out to be. A CPU and a CUDA run may pick different
        // tokens from the same logits; fixing the step count removes that
        // difference from the comparison.
        bridge.setEosToken(-1L);
        if (!bridge.load()) {
            throw new IllegalStateException("the bridge could not be loaded from "
                    + config.bridgeModelDir());
        }
        try {
            // MATRIX's own forward call.
            long[] tokenIds = deterministicTokenIds(config.seqLen(), 0x5EEDL);
            for (int i = 0; i < config.warmup(); i++) {
                bridge.getOnnx().greedyNextToken(tokenIds);
            }
            double[] forwardSamples = new double[config.iterations()];
            for (int i = 0; i < config.iterations(); i++) {
                long t0 = System.nanoTime();
                bridge.getOnnx().greedyNextToken(tokenIds);
                forwardSamples[i] = (System.nanoTime() - t0) / 1_000_000.0;
            }

            // The end-to-end path.
            for (int i = 0; i < config.warmup(); i++) {
                bridge.generate(config.prompt(), config.maxNewTokens());
            }
            double[] endToEndSamples = new double[config.iterations()];
            for (int i = 0; i < config.iterations(); i++) {
                long t0 = System.nanoTime();
                bridge.generate(config.prompt(), config.maxNewTokens());
                endToEndSamples[i] = (System.nanoTime() - t0) / 1_000_000.0;
            }

            return List.of(
                    new Series("transformer_forward_adapter", "ms", config.warmup(),
                            forwardSamples, stats(forwardSamples)),
                    new Series("end_to_end", "ms", config.warmup(),
                            endToEndSamples, stats(endToEndSamples)));
        } finally {
            bridge.close();
        }
    }

    // ── the session ──────────────────────────────────────────────────────────

    /**
     * A session with the provider this run is configured for.
     *
     * <p>The options are IDENTICAL in the CPU run and in the CUDA run except for
     * the provider registration itself: the same intra-op thread count, the
     * same default optimisation level, the same model.
     *
     * @param environment the ONNX Runtime environment
     * @param config the run configuration
     * @param model the graph to load
     * @return the session
     */
    private static OrtSession createSession(OrtEnvironment environment, Config config, Path model)
            throws OrtException {
        SessionOptions options = new SessionOptions();
        options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
        if ("cuda".equals(config.provider())) {
            OrtCUDAProviderOptions cuda = new OrtCUDAProviderOptions();
            cuda.add("device_id", "0");
            try {
                options.addCUDA(cuda);
            } catch (Exception failure) {
                throw new IllegalStateException(
                        "the CUDA execution provider could not be registered: "
                                + failure.getClass().getSimpleName() + ": " + failure.getMessage(),
                        failure);
            }
        }
        return environment.createSession(model.toString(), options);
    }

    /**
     * Run one forward pass on a PROFILED session and read the providers that
     * actually executed.
     *
     * <p>This is the ground truth. ONNX Runtime's execution profile names the
     * provider that ran every node, and it is the only evidence in the Java
     * binding that the GPU did the work — the session's own configuration only
     * says what it was ASKED to do.
     *
     * @param environment the ONNX Runtime environment
     * @param config the run configuration
     * @return how many nodes each provider ran
     */
    private static Map<String, Integer> captureExecutionProfile(OrtEnvironment environment,
                                                                Config config)
            throws OrtException, IOException {
        SessionOptions options = new SessionOptions();
        options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
        if ("cuda".equals(config.provider())) {
            OrtCUDAProviderOptions cuda = new OrtCUDAProviderOptions();
            cuda.add("device_id", "0");
            options.addCUDA(cuda);
        }
        options.enableProfiling(config.profileDirectory().toString());

        long[] tokenIds = deterministicTokenIds(config.seqLen(), 0x5EEDL);
        Map<String, Integer> counts = new TreeMap<>();
        try (OrtSession session = environment.createSession(
                config.transformerModel().toString(), options);
             OnnxTensor inputIds = OnnxTensor.createTensor(environment,
                     LongBuffer.wrap(tokenIds), new long[]{1, config.seqLen()});
             OnnxTensor attentionMask = OnnxTensor.createTensor(environment,
                     LongBuffer.wrap(ones(config.seqLen())), new long[]{1, config.seqLen()});
             OnnxTensor positionIds = OnnxTensor.createTensor(environment,
                     LongBuffer.wrap(positions(config.seqLen())), new long[]{1, config.seqLen()})) {
            Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
            inputs.put("input_ids", inputIds);
            inputs.put("attention_mask", attentionMask);
            inputs.put("position_ids", positionIds);
            runOnce(session, inputs);
            String profilePath = session.endProfiling();
            String json = Files.readString(Path.of(profilePath));
            Matcher matcher = PROVIDER_FIELD.matcher(json);
            while (matcher.find()) {
                counts.merge(matcher.group(1), 1, Integer::sum);
            }
        }
        return counts;
    }

    // ── the deterministic inputs ─────────────────────────────────────────────

    private static long[] deterministicTokenIds(int length, long seed) {
        long[] ids = new long[length];
        long state = seed;
        for (int i = 0; i < length; i++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            ids[i] = Math.floorMod(state >> 16, (long) QWEN_VOCAB);
        }
        return ids;
    }

    private static long[] ones(int length) {
        long[] values = new long[length];
        Arrays.fill(values, 1L);
        return values;
    }

    private static long[] positions(int length) {
        long[] values = new long[length];
        for (int i = 0; i < length; i++) {
            values[i] = i;
        }
        return values;
    }

    private static float[] deterministicFeatures(int count) {
        float[] values = new float[count];
        long state = 0xB1FL;
        for (int i = 0; i < count; i++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            values[i] = (float) (Math.floorMod(state >> 16, 2L));
        }
        return values;
    }

    private static void requireFile(Path path, String what) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalStateException(what + " is not present: " + path);
        }
    }

    // ── the arguments ────────────────────────────────────────────────────────

    static Config parseArgs(String[] args) {
        String label = "run";
        String provider = "cpu";
        Path transformerModel = Path.of("models/onnx/qwen05b/model.onnx");
        Path teacherModel = Path.of("models/teacher/teacher_ffn16.onnx");
        Path bridgeModelDir = Path.of("models/onnx/qwen05b");
        int seqLen = 32;
        int warmup = 5;
        int iterations = 20;
        String prompt = "The GPU benchmark measures the ONNX execution path.";
        int maxNewTokens = 16;
        Path profileDirectory = null;
        Path out = null;
        boolean requireCuda = false;

        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            String value = (i + 1 < args.length) ? args[i + 1] : null;
            switch (key) {
                case "--label" -> { label = value; i++; }
                case "--provider" -> { provider = value; i++; }
                case "--model" -> { transformerModel = Path.of(value); i++; }
                case "--teacher-model" -> { teacherModel = Path.of(value); i++; }
                case "--model-dir" -> { bridgeModelDir = Path.of(value); i++; }
                case "--seq-len" -> { seqLen = Integer.parseInt(value); i++; }
                case "--warmup" -> { warmup = Integer.parseInt(value); i++; }
                case "--iterations" -> { iterations = Integer.parseInt(value); i++; }
                case "--prompt" -> { prompt = value; i++; }
                case "--max-new-tokens" -> { maxNewTokens = Integer.parseInt(value); i++; }
                case "--profile-dir" -> { profileDirectory = Path.of(value); i++; }
                case "--out" -> { out = Path.of(value); i++; }
                case "--require-cuda" -> requireCuda = true;
                default -> throw new IllegalArgumentException("unknown argument: " + key);
            }
        }
        if (!"cpu".equals(provider) && !"cuda".equals(provider)) {
            throw new IllegalArgumentException("--provider must be cpu or cuda, got: " + provider);
        }
        if (iterations < 1 || warmup < 0 || seqLen < 1) {
            throw new IllegalArgumentException("iterations, warmup and seq-len must be positive");
        }
        return new Config(label, provider, transformerModel, teacherModel, bridgeModelDir,
                seqLen, warmup, iterations, prompt, maxNewTokens, profileDirectory, out,
                requireCuda);
    }

    // ── the JSON ─────────────────────────────────────────────────────────────

    private static String renderJson(Config config,
                                     OrtEnvironment environment,
                                     List<String> availableProviders,
                                     Map<String, Integer> profileProviders,
                                     boolean cudaObserved,
                                     List<Series> series) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"schemaVersion\": \"1.0.0\",\n");
        json.append("  \"label\": \"").append(escape(config.label())).append("\",\n");
        json.append("  \"provider\": \"").append(escape(config.provider())).append("\",\n");
        json.append("  \"requireCuda\": ").append(config.requireCuda()).append(",\n");
        json.append("  \"javaVersion\": \"").append(escape(System.getProperty("java.version")))
                .append("\",\n");
        json.append("  \"javaVendor\": \"").append(escape(System.getProperty("java.vendor")))
                .append("\",\n");
        json.append("  \"jvmName\": \"").append(escape(System.getProperty("java.vm.name")))
                .append("\",\n");
        json.append("  \"availableProcessors\": ")
                .append(Runtime.getRuntime().availableProcessors()).append(",\n");
        json.append("  \"onnxRuntimeVersion\": \"")
                .append(escape(environment.getVersion())).append("\",\n");
        json.append("  \"availableProviders\": ").append(jsonArray(availableProviders)).append(",\n");
        json.append("  \"model\": {\n");
        json.append("    \"transformer\": \"").append(escape(config.transformerModel().toString()))
                .append("\",\n");
        try {
            json.append("    \"transformerSha256\": \"")
                    .append(sha256(config.transformerModel())).append("\",\n");
        } catch (Exception e) {
            json.append("    \"transformerSha256\": \"unavailable\",\n");
        }
        json.append("    \"teacher\": \"").append(escape(config.teacherModel().toString()))
                .append("\",\n");
        try {
            json.append("    \"teacherSha256\": \"").append(sha256(config.teacherModel()))
                    .append("\",\n");
        } catch (Exception e) {
            json.append("    \"teacherSha256\": \"unavailable\",\n");
        }
        json.append("    \"sequenceLength\": ").append(config.seqLen()).append(",\n");
        json.append("    \"warmupIterations\": ").append(config.warmup()).append(",\n");
        json.append("    \"measuredIterations\": ").append(config.iterations()).append(",\n");
        json.append("    \"maxNewTokens\": ").append(config.maxNewTokens()).append("\n");
        json.append("  },\n");
        json.append("  \"executionProfile\": {\n");
        json.append("    \"providers\": {");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : profileProviders.entrySet()) {
            if (!first) {
                json.append(", ");
            }
            json.append("\"").append(escape(entry.getKey())).append("\": ")
                    .append(entry.getValue());
            first = false;
        }
        json.append("},\n");
        json.append("    \"cudaObserved\": ").append(cudaObserved).append("\n");
        json.append("  },\n");
        json.append("  \"measurements\": {\n");
        for (int i = 0; i < series.size(); i++) {
            Series s = series.get(i);
            json.append("    \"").append(escape(s.name())).append("\": {\n");
            json.append("      \"unit\": \"").append(escape(s.unit())).append("\",\n");
            json.append("      \"warmup\": ").append(s.warmup()).append(",\n");
            json.append("      \"count\": ").append(s.stats().count()).append(",\n");
            json.append("      \"min\": ").append(number(s.stats().min())).append(",\n");
            json.append("      \"median\": ").append(number(s.stats().median())).append(",\n");
            json.append("      \"p95\": ").append(number(s.stats().p95())).append(",\n");
            json.append("      \"max\": ").append(number(s.stats().max())).append(",\n");
            json.append("      \"mean\": ").append(number(s.stats().mean())).append(",\n");
            json.append("      \"stddev\": ").append(number(s.stats().stddev())).append(",\n");
            json.append("      \"samples\": [");
            for (int k = 0; k < s.samples().length; k++) {
                if (k > 0) {
                    json.append(", ");
                }
                json.append(number(s.samples()[k]));
            }
            json.append("]\n");
            json.append("    }").append(i + 1 < series.size() ? "," : "").append("\n");
        }
        json.append("  }\n");
        json.append("}\n");
        return json.toString();
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String jsonArray(List<String> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("\"").append(escape(values.get(i))).append("\"");
        }
        return builder.append("]").toString();
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }

    static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(path)) {
            byte[] buffer = new byte[1 << 20];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", b));
        }
        return builder.toString();
    }
}
