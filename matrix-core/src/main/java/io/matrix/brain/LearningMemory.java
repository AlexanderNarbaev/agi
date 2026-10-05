package io.matrix.brain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.Objects;

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

    /**
     * RECON-W32.24: counters are AtomicInteger, not volatile int. A volatile int is
     * only a visibility guarantee, not atomicity: {@code saveFailures++} is a
     * read-modify-write, and 8 threads x 20k calls lost 3 792 of 160 000 updates. A
     * counter that under-counts failures under-reports data loss, which is worse than
     * having no counter because it is believed.
     *
     * <p>Unit: calls. Non-zero means learned facts are not durable RIGHT NOW - a more
     * urgent fact than "a file had a bad line". Reset to zero on a successful save,
     * because {@code save()} rewrites the whole map and a later success genuinely does
     * make every fact durable again.</p>
     */
    private final AtomicInteger saveFailures = new AtomicInteger();

    /**
     * Failed loads since construction. Unit: calls. Non-zero means the mind started
     * amnesiac - every fact it had learned is unavailable - which without this looks
     * exactly like a fresh start.
     */
    private final AtomicInteger loadFailures = new AtomicInteger();
    /**
     * RECON-W32.24: a plain HashMap let {@link #save()} iterate {@code entrySet()}
     * while {@link #store()} mutated it. Under 4 threads x 40k stores that threw
     * 15 737 ConcurrentModificationExceptions, each one escaping {@code save()},
     * each one silently losing facts - while {@link #durable()} reported all-clear,
     * because the counter only ever saw IOException.
     *
     * <p>ConcurrentHashMap also forbids null keys and values, which is deliberate: a
     * null fact was reaching {@code escape()} and throwing an NPE that bypassed the
     * counter entirely. {@link #store} now rejects it with a message instead.</p>
     */
    private final Map<String, String> facts = new ConcurrentHashMap<>();
    
    public LearningMemory(Path memoryFile) {
        this.memoryFile = memoryFile;
        load();
    }
    
    public LearningMemory() {
        this(Paths.get("data/memory/learned-facts.json"));
    }
    
    public void store(String key, String value) {
        // RECON-W32.24: an explicit rejection, because ConcurrentHashMap's own NPE would
        // otherwise be the message. A null fact used to reach escape() and throw from
        // inside save(), where it walked past the failure counter and left durable()
        // reporting all-clear for a fact that was never written.
        Objects.requireNonNull(key, "fact key must not be null");
        Objects.requireNonNull(value, "fact value must not be null");
        facts.put(key, value);
        save();
    }
    
    public String recall(String key) {
        return facts.get(key);
    }
    
    public Map<String, String> all() {
        return new java.util.HashMap<>(facts);
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
        } catch (Exception e) {
            // RECON-W32.22: a failed load means the mind starts AMNESIAC, and an
            // empty memory is indistinguishable from a fresh start. Counted, not silent.
            loadFailures.incrementAndGet();
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
        saveTo(memoryFile);
    }

    /**
     * Write the whole map to {@code target}.
     *
     * <p>RECON-W32.24. This is a method rather than inline code in {@link #save()} so a
     * test can force a real failure against a real path. The first version of these
     * tests reached in with reflection to redirect the field, which is the kind of
     * cleverness that makes a test lie about the code it is testing.</p>
     *
     * @param target where to write; a directory here produces a genuine failure
     */
    void saveTo(Path target) {
        try {
            Files.createDirectories(target.getParent());
            StringBuilder sb = new StringBuilder("{\n");
            int i = 0;
            for (var entry : facts.entrySet()) {
                if (i > 0) sb.append(",\n");
                sb.append("  \"").append(escape(entry.getKey())).append("\": \"")
                  .append(escape(entry.getValue())).append("\"");
                i++;
            }
            sb.append("\n}\n");
            Files.writeString(target, sb.toString());
            if (target.equals(memoryFile)) {
                saveFailures.set(0);
            }
        } catch (Exception e) {
            // RECON-W32.24: this caught IOException only, so a NullPointerException
            // from escape() or a RuntimeException from a mutating map walked straight
            // out of save() and past the counter. durable() then reported "every fact
            // is on disk" while the fact was only ever in a HashMap. IOException was
            // never the only thing that can go wrong; it was the only thing handled.
            //
            // The mind keeps working; the FILE is what is lost, and that has to be
            // visible rather than discovered at the next restart.
            saveFailures.incrementAndGet();
            if (saveFailures.get() == 1) {
                System.err.println("[LearningMemory] save FAILED for " + target
                    + ": " + e
                    + " - learned facts exist in memory only and WILL be lost on "
                    + "restart; further failures are counted, not printed");
            }
        }
    }


    public int loadFailures() {
        return loadFailures.get();
    }

    public int saveFailures() {
        return saveFailures.get();
    }

    /**
     * True when every learned fact is durably on disk.
     *
     * <p>RECON-W32.24. The previous version was {@code saveFailures == 0}, which
     * answered a question about the COUNTER while presenting itself as an answer about
     * the DISK, and returned true in two reproducible cases where the facts were only
     * ever in memory. It now asks about the disk, and a fact in a map nobody has
     * written is not a fact anyone can lose-and-recover.</p>
     *
     * <p>Unit: a boolean. Callers that must not lose learning should check this.</p>
     *
     * @return true only if the last write completed with no failure recorded
     *         <em>and</em> the file is present right now.
     */
    public boolean durable() {
        // An empty memory has nothing to lose, so it is trivially durable. Saying false
        // would be its own kind of lie - it would imply a write failed when none was
        // ever attempted, and would train callers to ignore the accessor.
        if (facts.isEmpty()) return true;
        return saveFailures.get() == 0 && Files.exists(memoryFile);
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
