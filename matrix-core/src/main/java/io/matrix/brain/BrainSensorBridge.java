package io.matrix.brain;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * W520 — Brain Sensor Bridge.
 * 
 * Connects the brain to various input sources:
 * - stdin (keyboard) — already in BrainRunner
 * - file watcher — watch for files and process them
 * - periodic polling — check for new data periodically
 * 
 * Usage:
 *   java io.matrix.brain.BrainSensorBridge [model-path]
 * 
 * Sensors:
 *   - stdin: keyboard input
 *   - file: watch data/sensors/ for new files
 *   - timer: periodic self-reflection (every 60s)
 */
public final class BrainSensorBridge {
    
    private final LlmBrainLoopRag brain;
    private final ConfidenceFilter filter;
    private final Path sensorsDir;
    private final List<String> processedFiles = new ArrayList<>();
    
    public BrainSensorBridge(String modelPath) throws IOException {
        this.brain = new LlmBrainLoopRag(modelPath, new io.matrix.knowledge.SimpleKnowledgeBase());
        this.filter = new ConfidenceFilter(0.3);
        this.sensorsDir = Paths.get("data/sensors");
        Files.createDirectories(sensorsDir);
    }
    
    /**
     * Process input from stdin (keyboard).
     */
    public String processStdin(String input) {
        BrainCycle.CycleResult result = brain.cycle(input);
        String filtered = filter.filter(result.reply(), result.confidence());
        return filtered != null ? filtered : filter.rejectionMessage(result.confidence());
    }
    
    /**
     * Process files in sensors directory.
     * Returns number of files processed.
     */
    public int processFiles() throws IOException {
        if (!Files.exists(sensorsDir)) return 0;
        
        int processed = 0;
        try (var files = Files.list(sensorsDir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (p.toString().endsWith(".processed")) continue;
                
                String content = Files.readString(p);
                String reply = processStdin(content);
                
                // Mark as processed
                Path processedFile = p.resolveSibling(p.getFileName() + ".processed");
                Files.writeString(processedFile, "Processed: " + reply);
                processedFiles.add(p.toString());
                processed++;
            }
        }
        return processed;
    }
    
    /**
     * Process a single file.
     */
    public String processFile(Path file) throws IOException {
        String content = Files.readString(file);
        return processStdin(content);
    }
    
    public int getProcessedCount() { return processedFiles.size(); }
    
    public void close() { brain.close(); }
    
    public static void main(String[] args) throws Exception {
        String modelPath = args.length > 0 ? args[0] : "models/onnx/qwen05b";
        
        System.out.println("Brain Sensor Bridge (W520)");
        System.out.println("Model: " + modelPath);
        System.out.println("Sensors dir: data/sensors/");
        System.out.println();
        
        BrainSensorBridge bridge = new BrainSensorBridge(modelPath);
        
        // Process stdin if available
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        if (System.in.available() > 0) {
            String input = reader.readLine();
            if (input != null && !input.isEmpty()) {
                String reply = bridge.processStdin(input);
                System.out.println("[brain] " + reply);
            }
        }
        
        // Process files
        int processed = bridge.processFiles();
        if (processed > 0) {
            System.out.println("[brain] Processed " + processed + " sensor files");
        }
        
        System.out.println("Total processed: " + bridge.getProcessedCount());
        bridge.close();
    }
}
