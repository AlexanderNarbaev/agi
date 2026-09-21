package io.matrix.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * W434 — Conversation Model Evaluation CLI.
 * 
 * Evaluates the model on a set of test Q&A pairs.
 * Each pair is tested and the response is checked for expected substring.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationModelEval [test-file]
 * 
 * Test file format (JSONL):
 *   {"input":"...", "expected":"...", "category":"..."}
 * 
 * If no test file, uses default hardcoded test set.
 */
public final class ConversationModelEval {
    
    public static void main(String[] args) throws Exception {
        String modelPath = args.length > 1 ? args[1] : "models/onnx/qwen05b";
        String testFile = args.length > 0 ? args[0] : null;
        
        System.out.println("=".repeat(60));
        System.out.println("MATRIX MODEL EVALUATION");
        System.out.println("Model: " + modelPath);
        System.out.println("=".repeat(60));
        
        io.matrix.api.QwenOnnxBridge bridge = new io.matrix.api.QwenOnnxBridge(Paths.get(modelPath));
        bridge.useGpu(false);
        bridge.setMaxNewTokens(96);
        bridge.load();
        
        // Load test cases
        List<TestCase> tests = new ArrayList<>();
        if (testFile != null) {
            loadTests(testFile, tests);
        } else {
            loadDefaultTests(tests);
        }
        
        System.out.println("\nRunning " + tests.size() + " tests...\n");
        
        int passed = 0;
        long totalMs = 0;
        for (TestCase test : tests) {
            long t0 = System.currentTimeMillis();
            String response = bridge.chat(test.input, 96);
            long elapsed = System.currentTimeMillis() - t0;
            totalMs += elapsed;
            
            boolean ok = response.toLowerCase().contains(test.expected.toLowerCase());
            String mark = ok ? "PASS" : "FAIL";
            
            if (ok) passed++;
            
            String cat = test.category != null ? "[" + test.category + "] " : "";
            System.out.println(String.format("[%s] %s%.2fs: \"%s\"",
                mark, cat, elapsed / 1000.0,
                test.input.length() > 60 ? test.input.substring(0, 60) + "..." : test.input));
            
            if (!ok) {
                System.out.println("       Expected: \"" + test.expected + "\"");
                System.out.println("       Got:      \"" + truncate(response, 100) + "\"");
            }
        }
        
        bridge.close();
        
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("RESULTS: " + passed + "/" + tests.size() + " passed");
        System.out.println("Total time: " + (totalMs / 1000) + "s");
        System.out.println("Avg time/test: " + (tests.isEmpty() ? 0 : totalMs / tests.size()) + "ms");
        System.out.println("=".repeat(60));
        
        if (passed < tests.size()) {
            System.exit(1);
        }
    }
    
    static class TestCase {
        String input;
        String expected;
        String category;
        TestCase(String i, String e, String c) { input = i; expected = e; category = c; }
    }
    
    private static void loadTests(String file, List<TestCase> tests) throws IOException {
        try (var lines = Files.lines(Paths.get(file))) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank()) continue;
                String input = extractField(line, "input");
                String expected = extractField(line, "expected");
                String category = extractField(line, "category");
                if (input != null && expected != null) {
                    tests.add(new TestCase(input, expected, category));
                }
            }
        }
    }
    
    private static void loadDefaultTests(List<TestCase> tests) {
        // Math
        tests.add(new TestCase("What is 2+2?", "4", "math"));
        tests.add(new TestCase("What is 5+3?", "8", "math"));
        tests.add(new TestCase("What is 10-7?", "3", "math"));
        // Knowledge
        tests.add(new TestCase("What color is the sky?", "blue", "knowledge"));
        tests.add(new TestCase("What is water made of?", "hydrogen", "knowledge"));
        // Social
        tests.add(new TestCase("Say hello", "hello", "social"));
        tests.add(new TestCase("Tell me a joke", "joke", "social"));
    }
    
    private static String extractField(String json, String fieldName) {
        String needle = "\"" + fieldName + "\":\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return null;
        int start = idx + needle.length();
        boolean escaped = false;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                if (c == 'n') sb.append(' ');
                else if (c == 'r') sb.append(' ');
                else if (c == 't') sb.append(' ');
                else if (c == '"') sb.append('"');
                else if (c == '\\') sb.append('\\');
                else sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
