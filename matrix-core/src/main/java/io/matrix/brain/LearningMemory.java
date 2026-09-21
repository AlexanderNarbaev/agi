package io.matrix.brain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * W488 — Learning Memory (stores learned facts from conversations).
 * 
 * Key-value store backed by JSON file. Survives restarts.
 * Used to store conversation-derived facts that improve brain responses.
 * 
 * Separate from PersistentMemory (which stores ConsolidationCycle entries).
 */
public final class LearningMemory {
    
    private final Path memoryFile;
    private final Map<String, String> facts = new HashMap<>();
    
    public LearningMemory(Path memoryFile) {
        this.memoryFile = memoryFile;
        load();
    }
    
    public LearningMemory() {
        this(Paths.get("data/memory/learned-facts.json"));
    }
    
    public void store(String key, String value) {
        facts.put(key, value);
        save();
    }
    
    public String recall(String key) {
        return facts.get(key);
    }
    
    public Map<String, String> all() {
        return new HashMap<>(facts);
    }
    
    public int size() { return facts.size(); }
    
    private void load() {
        if (!Files.exists(memoryFile)) return;
        try {
            String json = Files.readString(memoryFile);
            json = json.trim();
            if (!json.startsWith("{") || !json.endsWith("}")) return;
            json = json.substring(1, json.length() - 1);
            
            // Simple parse: split by comma outside strings
            String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String pair : pairs) {
                pair = pair.trim();
                int colon = pair.indexOf(':');
                if (colon < 0) continue;
                String key = pair.substring(0, colon).trim().replace("\"", "");
                String val = pair.substring(colon + 1).trim().replace("\"", "");
                facts.put(key, val);
            }
        } catch (IOException e) {
            // ignore
        }
    }
    
    private void save() {
        try {
            Files.createDirectories(memoryFile.getParent());
            StringBuilder sb = new StringBuilder("{\n");
            int i = 0;
            for (var entry : facts.entrySet()) {
                if (i > 0) sb.append(",\n");
                sb.append("  \"").append(escape(entry.getKey())).append("\": \"")
                  .append(escape(entry.getValue())).append("\"");
                i++;
            }
            sb.append("\n}\n");
            Files.writeString(memoryFile, sb.toString());
        } catch (IOException e) {
            // ignore
        }
    }
    
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
               .replace("\n", "\\n").replace("\r", "\\r");
    }
    
    public static void main(String[] args) {
        LearningMemory mem = new LearningMemory();
        if (args.length == 0) {
            System.out.println("LearningMemory: " + mem.size() + " facts");
            for (var e : mem.all().entrySet()) {
                System.out.println("  " + e.getKey() + " = " + e.getValue());
            }
        } else if (args[0].equals("store") && args.length >= 3) {
            mem.store(args[1], args[2]);
            System.out.println("Stored: " + args[1]);
        } else if (args[0].equals("recall") && args.length >= 2) {
            String val = mem.recall(args[1]);
            System.out.println(val != null ? val : "(not found)");
        }
    }
}
