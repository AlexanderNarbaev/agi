package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * W470 — ConversationPrompt CLI.
 * 
 * Single-shot prompt test with full parameter control.
 * Useful for testing specific prompts against the model.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationPrompt <prompt> [model-path] [max-tokens]
 */
public final class ConversationPrompt {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: ConversationPrompt <prompt> [model-path] [max-tokens]");
            System.exit(1);
        }
        
        String prompt = args[0];
        String modelPath = args.length > 1 ? args[1] : "models/onnx/qwen05b";
        int maxTokens = args.length > 2 ? Integer.parseInt(args[2]) : 128;
        
        System.out.println("=".repeat(60));
        System.out.println("PROMPT TEST (W470)");
        System.out.println("Model: " + modelPath);
        System.out.println("Max tokens: " + maxTokens);
        System.out.println("=".repeat(60));
        System.out.println();
        System.out.println("PROMPT:");
        System.out.println("  " + prompt);
        System.out.println();
        
        io.matrix.api.QwenOnnxBridge bridge = new io.matrix.api.QwenOnnxBridge(Paths.get(modelPath));
        bridge.useGpu(false);
        bridge.setMaxNewTokens(maxTokens);
        bridge.load();
        
        long start = System.currentTimeMillis();
        String response = bridge.chat(prompt, maxTokens);
        long elapsed = System.currentTimeMillis() - start;
        
        System.out.println("RESPONSE (" + elapsed + "ms, " + 
            (response == null ? 0 : response.split("\\s+").length) + " tokens):");
        System.out.println("  " + response);
        System.out.println();
        System.out.println("=".repeat(60));
        
        bridge.close();
    }
}
