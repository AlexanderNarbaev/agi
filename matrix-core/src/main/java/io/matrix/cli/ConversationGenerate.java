package io.matrix.cli;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.Random;

/**
 * W467 — Conversation Generate CLI.
 * 
 * Generates synthetic NDJSON conversations for testing.
 * Useful for benchmarking, unit tests, demos.
 * 
 * Usage:
 *   java io.matrix.cli.ConversationGenerate [count] [turns] [seed]
 *   Defaults: 5 sessions, 10 turns each, random seed
 *   Output: data/synthetic/synthetic-{i}.ndjson
 */
public final class ConversationGenerate {
    
    private static final String[][] USER_PROMPTS = {
        {"What is 2+2?", "4"},
        {"What color is the sky?", "blue"},
        {"What is the capital of France?", "paris"},
        {"Who wrote Hamlet?", "shakespeare"},
        {"What is H2O?", "water"},
        {"What is the speed of light?", "299792458"},
        {"What is DNA?", "deoxyribonucleic acid"},
        {"Hello", "hello"},
        {"How are you?", "fine"},
        {"What is 1+1?", "2"}
    };
    
    private static final String[] ASSISTANT_RESPONSES = {
        "That's a great question. Let me think about it.",
        "Based on my knowledge, I can provide the following answer.",
        "The answer is straightforward and well-documented.",
        "I can help with that. Here's what I know.",
        "That's interesting. Let me explain the details.",
        "Sure, here's the explanation you need.",
        "I understand. Let me provide a detailed response.",
        "Of course. Here's a clear and concise answer.",
        "That's a fair point. My analysis follows.",
        "Let me break this down step by step."
    };
    
    public static void main(String[] args) throws IOException {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        int turns = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : System.currentTimeMillis();
        
        Random rng = new Random(seed);
        
        Path outDir = Paths.get("data/synthetic");
        Files.createDirectories(outDir);
        
        System.out.println("=".repeat(60));
        System.out.println("CONVERSATION GENERATE (W467)");
        System.out.println("Sessions: " + count);
        System.out.println("Turns per session: " + turns);
        System.out.println("Seed: " + seed);
        System.out.println("=".repeat(60));
        
        for (int i = 0; i < count; i++) {
            String sessionId = "syn-" + i + "-" + System.currentTimeMillis();
            Path outFile = outDir.resolve(sessionId + ".ndjson");
            
            try (OutputStreamWriter w = new OutputStreamWriter(
                    Files.newOutputStream(outFile), StandardCharsets.UTF_8)) {
                
                // Alternate user/assistant
                Instant baseTime = Instant.now();
                for (int t = 0; t < turns; t++) {
                    boolean isUser = (t % 2 == 0);
                    String role = isUser ? "user" : "assistant";
                    
                    String content;
                    if (isUser) {
                        int idx = rng.nextInt(USER_PROMPTS.length);
                        content = USER_PROMPTS[idx][0];
                    } else {
                        content = ASSISTANT_RESPONSES[rng.nextInt(ASSISTANT_RESPONSES.length)];
                    }
                    
                    // Random time offset (0-60 seconds per turn)
                    baseTime = baseTime.plusSeconds(rng.nextInt(60));
                    
                    String escaped = content
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r");
                    
                    w.write(String.format(
                        "{\"conversationId\":\"%s\",\"role\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}\n",
                        sessionId, role, escaped, baseTime.toString()));
                }
            }
        }
        
        System.out.println("Generated " + count + " sessions in " + outDir);
        System.out.println("Sample: " + outDir.resolve("syn-0-*.ndjson"));
    }
}
