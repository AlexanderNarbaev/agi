package io.quarkus.runner;

import io.quarkus.runtime.Application;

/**
 * W289 — Manually created ApplicationImpl with CLI support.
 *
 * <p>Native entry-point with basic CLI:
 * - (no args): prints banner
 * - --version: prints version
 * - --help: prints help
 * - --status: prints binary status
 * - --bench: runs a simple benchmark
 *
 * <p>For full Quarkus DI features, use the standard JVM mode:
 *   ./gradlew :matrix-core:quarkusRun
 *
 * <p>CONSTITUTION VI compliance: native CLI entry-point,
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
            String flag = args[0];
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
                default:
                    System.out.println("Unknown flag: " + flag);
                    printHelp();
                    break;
            }
        } else {
            printHelp();
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
        System.out.println("  (no args)  Print banner");
        System.out.println("  --version  Print version");
        System.out.println("  --status   Print binary status (size, GC, etc.)");
        System.out.println("  --bench    Run simple startup benchmark");
        System.out.println("  --help     Print this help");
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

    private static void runBench() {
        System.out.println("MATRIX Native Startup Benchmark:");
        long start = System.nanoTime();
        // Simple computational benchmark
        long sum = 0;
        for (int i = 0; i < 1_000_000; i++) {
            sum += Math.sqrt(i) * Math.sin(i);
        }
        long elapsedNs = System.nanoTime() - start;
        System.out.println("  1M sqrt*sin operations: " + (elapsedNs / 1_000_000) + "ms (sum=" + sum + ")");
        System.out.println("  Throughput: " + (1_000_000_000L / elapsedNs) + " ops/sec");
    }

    @Override
    protected void doStop() {
        // No-op
    }
}
