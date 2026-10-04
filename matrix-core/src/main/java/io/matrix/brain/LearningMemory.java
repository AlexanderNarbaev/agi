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
            // RECON-W32.22: a failed load means the mind starts AMNESIAC, and an
            // empty memory is indistinguishable from a fresh start. Counted, not silent.
            loadFailures++;
            System.err.println("[LearningMemory] load FAILED for " + memoryFile
                + ": " + e + " - starting with an EMPTY memory; every fact previously "
                + "learned is unavailable");
        }
    }
    
    /**
     * Persist the learned facts, and report a failure instead of swallowing it.
     *
     * <p>RECON-W32.22. This caught {@code IOException} and did nothing, which is a
     * materially worse failure than a read that skips a line. A read skip loses data the
     * mind already knew it had. A write failure means <em>the mind believes it saved</em>
     * — the in-memory map keeps growing, every subsequent call reports success — and the
     * loss only surfaces at the next restart, when the facts are simply gone. There is no
     * point at which anything looks wrong to the caller.</p>
     *
     * <p>So the count is exposed, the reason is reported once, and the failure is
     * visible to whatever is watching. The write is still ATTEMPTED and still does not
     * throw: a memory subsystem that throws on a full disk takes the mind down with it,
     * and losing the ability to think is worse than losing the file.</p>
     */
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
            saveFailures = 0;
        } catch (IOException e) {
            // The mind keeps working; the FILE is what is lost, and that has to be
            // visible rather than discovered at the next restart.
            saveFailures++;
            if (saveFailures == 1) {
                System.err.println("[LearningMemory] save FAILED for " + memoryFile
                    + ": " + e
                    + " - learned facts exist in memory only and WILL be lost on "
                    + "restart; further failures are counted, not printed");
            }
        }
    }

    /**
     * Failed {@link #save()} calls since the last success.
     *
     * <p>Unit: calls. Non-zero means learned facts are not durable RIGHT NOW, which is a
     * different and more urgent fact than "a file had a bad line".</p>
     */
    private volatile int saveFailures = 0;

    /** Failed loads since construction. Unit: calls. */
    /**
     * Failed loads since construction.
     *
     * <p>RECON-W32.22. Non-zero means the mind started amnesiac — every fact it had
     * learned is unavailable — which without this looks exactly like a fresh start.</p>
     *
     * <p>Unit: calls.</p>
     */
    private volatile int loadFailures = 0;

    public int loadFailures() {
        return loadFailures;
    }

    public int saveFailures() {
        return saveFailures;
    }

    /**
     * True when every learned fact is durably on disk.
     * Unit: a boolean. Callers that must not lose learning should check this.
     */
    public boolean durable() {
        return saveFailures == 0;
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
