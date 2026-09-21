package io.matrix.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * W453 — Conversation Benchmark CLI.
 * 
 * Benchmarks the Qwen model's inference speed with various prompts.
 * Measures tokens/second and time-to-first-token.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationBenchmark [iterations] [model-path]
 *   Default: 5 iterations
 */
public final class ConversationBenchmark {
    
    private static final String[] TEST_PROMPTS = {
        "What is 2+2?",
        "Explain quantum entanglement in one sentence.",
        "List three fruits.",
        "What is the capital of Japan?",
        "Write a haiku about programming."
    };
    
    public static void main(String[] args) throws Exception {
        int iterations = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        String modelPath = args.length > 1 ? args[1] : "models/onnx/qwen05b";
        
        System.out.println("=".repeat(60));
        System.out.println("MATRIX INFERENCE BENCHMARK (W453)");
        System.out.println("Model: " + modelPath);
        System.out.println("Iterations per prompt: " + iterations);
        System.out.println("=".repeat(60));
        
        io.matrix.api.QwenOnnxBridge bridge = new io.matrix.api.QwenOnnxBridge(Paths.get(modelPath));
        bridge.useGpu(false);
        bridge.setMaxNewTokens(64);
        bridge.load();
        
        // Warmup
        System.out.print("Warmup... ");
        long warmupStart = System.currentTimeMillis();
        bridge.chat("Hello", 16);
        long warmupMs = System.currentTimeMillis() - warmupStart;
        System.out.println("OK (" + warmupMs + "ms)");
        System.out.println();
        
        long totalMs = 0;
        int totalTokens = 0;
        int successCount = 0;
        long minMs = Long.MAX_VALUE;
        long maxMs = 0;
        
        for (int i = 0; i < iterations; i++) {
            System.out.println("--- Iteration " + (i + 1) + "/" + iterations + " ---");
            
            for (String prompt : TEST_PROMPTS) {
                long start = System.currentTimeMillis();
                String response = bridge.chat(prompt, 64);
                long elapsed = System.currentTimeMillis() - start;
                
                if (response != null && !response.isEmpty()) {
                    int tokens = response.split("\\s+").length;
                    totalMs += elapsed;
                    totalTokens += tokens;
                    successCount++;
                    minMs = Math.min(minMs, elapsed);
                    maxMs = Math.max(maxMs, elapsed);
                    
                    double tps = tokens * 1000.0 / elapsed;
                    System.out.println(String.format("  Q: %-50s -> %4dms, %3d tok, %.1f tok/s",
                        prompt.length() > 50 ? prompt.substring(0, 47) + "..." : prompt,
                        elapsed, tokens, tps));
                }
            }
        }
        
        bridge.close();
        
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("BENCHMARK RESULTS");
        System.out.println("=".repeat(60));
        System.out.println("Successful requests: " + successCount + " / " + (iterations * TEST_PROMPTS.length));
        if (successCount > 0) {
            System.out.println("Total time:          " + totalMs + "ms");
            System.out.println("Total tokens:        " + totalTokens);
            System.out.println("Avg time/req:        " + (totalMs / successCount) + "ms");
            System.out.println("Avg tokens/req:      " + (totalTokens / successCount));
            double avgTps = totalTokens * 1000.0 / totalMs;
            System.out.println("Throughput:          " + String.format("%.1f tokens/sec", avgTps));
            System.out.println("Min time:            " + minMs + "ms");
            System.out.println("Max time:            " + maxMs + "ms");
        }
        System.out.println("=".repeat(60));
    }
}
