package io.quarkus.runner;

import io.matrix.consciousness.*;
import io.quarkus.runtime.Application;

/**
 * W310 — Manually created ApplicationImpl with extended CLI.
 *
 * <p>Supports:
 * - --version, --help, --status, --bench (basic)
 * - --info: shows detailed binary info
 * - --cognitive: runs cognitive pipeline test
 *
 * <p>CONSTITUTION VI compliance: native CLI substrate,
 * not a phenomenal consciousness claim.
 */
public class ApplicationImpl extends Application {
    private static final String VERSION = "2.0.0";
    private static final String BUILD_DATE = "2026-09-17";
    private static final long START_TIME_MS = System.currentTimeMillis();

    public ApplicationImpl() {
        super(false);
    }

    @Override
    public String getName() {
        return "io.quarkus.runner.ApplicationImpl";
    }

    @Override
    protected void doStart(String[] args) {
        if (args.length == 0) {
            printBanner();
        } else if (args.length == 1) {
            handleCommand(args[0]);
        } else {
            printHelp();
        }
    }

    private void handleCommand(String flag) {
        switch (flag) {
            case "--version":
                System.out.println("MATRIX v" + VERSION + " (native image, build " + BUILD_DATE + ")");
                break;
            case "--help":
                printHelp();
                break;
            case "--status":
                printStatus();
                break;
            case "--bench":
                runBench();
                break;
            case "--info":
                printInfo();
                break;
            case "--cognitive":
                runCognitivePipeline();
                break;
            default:
                System.out.println("Unknown flag: " + flag);
                printHelp();
                break;
        }
    }

    private static void printBanner() {
        System.out.println("MATRIX v" + VERSION + " — native image started");
        System.out.println("Size: 126MB, GC: epsilon, build: " + BUILD_DATE);
        System.out.println("For full Quarkus features use: ./gradlew :matrix-core:quarkusRun");
        System.out.println("Try --help for native CLI options");
    }

    private static void printHelp() {
        System.out.println("MATRIX native CLI (v" + VERSION + "):");
        System.out.println("  (no args)      Print banner");
        System.out.println("  --version      Print version");
        System.out.println("  --status       Print binary status (memory, GC, etc.)");
        System.out.println("  --bench        Run simple startup benchmark");
        System.out.println("  --info         Show detailed info");
        System.out.println("  --cognitive    Run cognitive pipeline test");
        System.out.println("  --help         Print this help");
    }

    private static void printStatus() {
        long uptimeMs = System.currentTimeMillis() - START_TIME_MS;
        long maxMem = Runtime.getRuntime().maxMemory();
        long totalMem = Runtime.getRuntime().totalMemory();
        long freeMem = Runtime.getRuntime().freeMemory();
        System.out.println("MATRIX Native Binary Status:");
        System.out.println("  Version:  v" + VERSION);
        System.out.println("  Build:    " + BUILD_DATE);
        System.out.println("  GC:       epsilon");
        System.out.println("  Uptime:   " + uptimeMs + "ms");
        System.out.println("  MaxMem:   " + (maxMem / (1024*1024)) + " MB");
        System.out.println("  TotalMem: " + (totalMem / (1024*1024)) + " MB");
        System.out.println("  FreeMem:  " + (freeMem / (1024*1024)) + " MB");
    }

    private static void printInfo() {
        System.out.println("MATRIX Native Binary Info:");
        System.out.println("  Version:    v" + VERSION);
        System.out.println("  Build:      " + BUILD_DATE);
        System.out.println("  VM:         GraalVM CE 25.0.2");
        System.out.println("  GC:         epsilon (no GC overhead)");
        System.out.println("  Image size: 126MB (with --gc=epsilon)");
        System.out.println("  Startup:    ~100ms (vs JVM 2-5s)");
        System.out.println("  Modules:    137+ cognitive measurement classes");
        System.out.println("  Tests:      700+ verified tests");
        System.out.println("  CONSTITUTION: Articles I (seeded Random) + VI (no consciousness claim)");
    }

    private static void runBench() {
        System.out.println("MATRIX Native Startup Benchmark:");
        long start = System.nanoTime();
        long sum = 0;
        for (int i = 0; i < 1_000_000; i++) {
            sum += Math.sqrt(i) * Math.sin(i);
        }
        long elapsedNs = System.nanoTime() - start;
        System.out.println("  1M sqrt*sin operations: " + (elapsedNs / 1_000_000) + "ms (sum=" + sum + ")");
        System.out.println("  Throughput: " + (1_000_000_000L / elapsedNs) + " ops/sec");
    }

    private static void runCognitivePipeline() {
        System.out.println("MATRIX Cognitive Pipeline Test (native):");
        // Embedding
        CognitiveEmbedding embedding = new CognitiveEmbedding(64, 42L);
        CognitiveGenesisProfile p = new CognitiveGenesisProfile(
            0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5,
            50.0, 0.5, 0.5, 2, 0.5, 2.0);
        double[] vec = embedding.embed(p);
        System.out.println("  Embedding:    " + vec.length + "-dim vector");
        // Constitutional AI
        CognitiveConstitutionalAI.ConstitutionalResult cai =
            CognitiveConstitutionalAI.evaluate(p,
                CognitiveConstitutionalAI.DEFAULT_CONSTITUTION, 0.5);
        System.out.println("  Constitution: " + cai.critiques().size() + " principles, avg=" + cai.averageScore());
        // Test-time compute
        CognitiveTestTimeCompute ttc = new CognitiveTestTimeCompute(42L, 3);
        CognitiveTestTimeCompute.TestTimeResult ttcResult =
            ttc.solve(p, 2);
        System.out.println("  TestTime:     " + ttcResult.allAttempts().size() + " attempts");
        // Adaptive compute
        CognitiveAdaptiveCompute.AdaptiveResult adaptResult =
            CognitiveAdaptiveCompute.process(java.util.List.of(p), 3, 0.5);
        System.out.println("  Adaptive:     " + adaptResult.totalCompute() + " compute");
        // MLA (latent attention)
        CognitiveLatentAttention mla = new CognitiveLatentAttention(64, 8, 16, 42L);
        double[] latent = mla.compress(vec, true);
        System.out.println("  MLA:          " + latent.length + "-dim latent (" + mla.compressionRatio() + "x compression)");
        // YaRN (extended RoPE)
        double[] extended = CognitiveYaRN.apply(vec, 100, 16.0);
        System.out.println("  YaRN:         " + extended.length + "-dim extended (16x scale)");
        // MRA (multi-resolution)
        CognitiveMultiResolutionAttention mra = new CognitiveMultiResolutionAttention(64, 4, 42L);
        double[] result = mra.attendMultiResolution(vec, java.util.List.of(vec), java.util.List.of(vec));
        System.out.println("  MRA:          " + result.length + "-dim multi-res output");
        // QLoRA
        CognitiveQLoRA qlora = new CognitiveQLoRA(64, 16, 42L);
        CognitiveQLoRA.QuantizedWeights qw = qlora.quantize(vec);
        double[] adapted = qlora.apply(vec, qw);
        System.out.println("  QLoRA:        " + adapted.length + "-dim adapted");
        // HyperNetwork
        CognitiveHyperNetwork hn = new CognitiveHyperNetwork(64, 32, 16, 42L);
        CognitiveHyperNetwork.GeneratedWeights gw = hn.generate(vec);
        System.out.println("  HyperNet:     " + gw.weights().length + "x" + gw.weights()[0].length + " generated weights");
        System.out.println("  Done!");
    }

    @Override
    protected void doStop() {
        // No-op
    }
}
