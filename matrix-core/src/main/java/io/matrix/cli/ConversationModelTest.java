package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * W431 — Conversation Model Test CLI.
 * 
 * Quick sanity check that the Qwen model is working properly.
 * Asks a known question and verifies the response contains expected terms.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationModelTest
 */
public final class ConversationModelTest {
    
    public static void main(String[] args) throws Exception {
        String modelPath = args.length > 0 ? args[0] : "models/onnx/qwen05b";
        
        System.out.println("=".repeat(50));
        System.out.println("MATRIX MODEL TEST");
        System.out.println("Model: " + modelPath);
        System.out.println("=".repeat(50));
        
        io.matrix.api.QwenOnnxBridge bridge = new io.matrix.api.QwenOnnxBridge(Paths.get(modelPath));
        bridge.useGpu(false);
        bridge.setMaxNewTokens(64);
        
        System.out.print("Loading model... ");
        long t0 = System.currentTimeMillis();
        boolean loaded = bridge.load();
        long loadMs = System.currentTimeMillis() - t0;
        System.out.println("OK (" + loadMs + "ms)");
        
        // Test cases
        String[][] tests = {
            {"What is 2+2?", "4"},
            {"What color is the sky?", "blue"},
            {"Say hello", "hello"},
        };
        
        int passed = 0;
        for (String[] test : tests) {
            String question = test[0];
            String expected = test[1].toLowerCase();
            
            String response = bridge.chat(question, 64);
            String lowerResp = response.toLowerCase();
            
            boolean ok = lowerResp.contains(expected);
            String mark = ok ? "PASS" : "FAIL";
            System.out.println("[" + mark + "] Q: \"" + question + "\" → contains '" + expected + "': " + ok);
            if (!ok) {
                System.out.println("       A: \"" + response + "\"");
            }
            if (ok) passed++;
        }
        
        bridge.close();
        
        System.out.println("=".repeat(50));
        System.out.println("Result: " + passed + "/" + tests.length + " tests passed");
        System.out.println("=".repeat(50));
        
        if (passed < tests.length) {
            System.exit(1);
        }
    }
}
