package io.matrix.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.bir.TtForm;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Disk-backed wrapper around {@link HierarchicalMemory} (Wave
 * improvement). Periodically flushes the in-memory hierarchy to a
 * JSONL file so conversations survive JVM restarts.
 *
 * <p>Format: one JSON object per line, each representing a
 * {@link HierarchicalMemory.MemoryEntry}. The flush is debounced
 * (only flushes if dirty) and runs every {@code matrix.memory.flush-seconds}
 * seconds.
 */
@ApplicationScoped
public class PersistentHierarchicalMemory {

    private static final Logger log =
            Logger.getLogger(PersistentHierarchicalMemory.class);

    @Inject
    HierarchicalMemory memory;

    @ConfigProperty(name = "matrix.memory.persistence.path",
                    defaultValue = "data/hierarchical_memory.jsonl")
    String persistencePath;

    @ConfigProperty(name = "matrix.memory.flush-seconds", defaultValue = "30")
    int flushSeconds;

    @ConfigProperty(name = "matrix.memory.persistence-enabled", defaultValue = "true")
    boolean enabled;

    private final ObjectMapper mapper = new ObjectMapper();
    private ScheduledExecutorService flushExecutor;
    private volatile boolean dirty = false;

    /** Quarkus startup hook: ensures persistence is active even if nothing
     *  @Inject's this bean directly. */
    void onStart(@Observes StartupEvent ev) {
        start();
    }

    void start() {
        if (!enabled) {
            log.info("PersistentHierarchicalMemory: persistence disabled by config");
            return;
        }
        // 0. register a listener so every store() marks the memory dirty
        memory.addStoreListener(entry -> {
            dirty = true;
            log.debugf("LTM marked dirty by store of '%s'", entry.content());
        });
        // 1. restore from disk if file exists
        try {
            restore();
        } catch (Exception e) {
            log.warnf("PersistentHierarchicalMemory: restore failed: %s", e.getMessage());
        }
        // 2. schedule periodic flushes
        flushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ltm-flush");
            t.setDaemon(true);
            return t;
        });
        flushExecutor.scheduleAtFixedRate(this::flushIfDirty,
                flushSeconds, flushSeconds, TimeUnit.SECONDS);
        log.infof("PersistentHierarchicalMemory: enabled, path=%s, flush every %ds",
                persistencePath, flushSeconds);
    }

    @PreDestroy
    void stop() {
        // final flush on shutdown
        if (enabled) {
            try {
                flush();
            } catch (Exception e) {
                log.warnf("final flush failed: %s", e.getMessage());
            }
        }
        if (flushExecutor != null) flushExecutor.shutdownNow();
    }

    /** Mark the memory as dirty — call this after every {@link HierarchicalMemory#store}
     *  so the periodic flusher picks up the new entries. The flush executor
     *  runs at the configured interval. */
    public void markDirty() {
        dirty = true;
    }

    /** Test-only: read the dirty flag. */
    public boolean dirtyForTest() {
        return dirty;
    }

    /** Force-flush synchronously. Use for testing or shutdown paths. */
    public void flushIfDirtyNow() {
        flushIfDirty();
    }

    /** Force a flush now (e.g. for tests). */
    public void flushNow() {
        try {
            flush();
        } catch (Exception e) {
            log.warnf("manual flush failed: %s", e.getMessage());
        }
    }

    private void flushIfDirty() {
        if (dirty) {
            try {
                flush();
                dirty = false;
            } catch (Exception e) {
                log.warnf("periodic flush failed: %s", e.getMessage());
            }
        }
    }

    private void flush() throws IOException {
        Path path = Path.of(persistencePath);
        Files.createDirectories(path.getParent());
        // snapshot all entries from each level
        try (var writer = Files.newBufferedWriter(path)) {
            for (HierarchicalMemory.Level level : HierarchicalMemory.Level.values()) {
                List<HierarchicalMemory.MemoryEntry> entries =
                        memory.entriesAtLevel(level);
                for (var entry : entries) {
                    Map<String, Object> rec = new LinkedHashMap<>();
                    rec.put("id", entry.id());
                    rec.put("level", level.name());
                    rec.put("content", entry.content());
                    rec.put("domain", entry.domain());
                    rec.put("tags", entry.tags());
                    rec.put("importance", entry.importance());
                    rec.put("createdAt", entry.createdAt());
                    rec.put("lastAccessedAt", entry.lastAccessedAt());
                    rec.put("accessCount", entry.accessCount());
                    rec.put("parentId", entry.parentId());
                    rec.put("childIds", entry.childIds());
                    writer.write(mapper.writeValueAsString(rec));
                    writer.newLine();
                }
            }
        }
        log.debugf("flushed LTM to %s", path);
    }

    private void restore() throws IOException {
        Path path = Path.of(persistencePath);
        if (!Files.exists(path)) {
            log.infof("no persistence file at %s, starting fresh", path);
            return;
        }
        int restored = 0;
        try (var reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> rec = mapper.readValue(line, Map.class);
                    Object levelObj = rec.get("level");
                    if (levelObj == null) {
                        log.warnf("skipping entry without level: %s", line);
                        continue;
                    }
                    HierarchicalMemory.Level level =
                            HierarchicalMemory.Level.valueOf(levelObj.toString());
                    Object contentObj = rec.get("content");
                    String content = contentObj == null ? "" : contentObj.toString();
                    @SuppressWarnings("unchecked")
                    java.util.Collection<String> rawTags =
                            (java.util.Collection<String>) rec.getOrDefault("tags", Set.of());
                    Set<String> tags = new java.util.HashSet<>(rawTags);
                    String domain = String.valueOf(rec.getOrDefault("domain", ""));
                    memory.store(level, content, domain, tags);
                    restored++;
                } catch (Exception e) {
                    log.warnf("skipping malformed line: %s — %s",
                            line.substring(0, Math.min(80, line.length())),
                            e.getMessage());
                }
            }
        }
        log.infof("restored %d memory entries from %s", restored, path);
    }

    /** Convenience factory for tests. */
    public static PersistentHierarchicalMemory forTest(HierarchicalMemory memory, String path) {
        PersistentHierarchicalMemory p = new PersistentHierarchicalMemory();
        p.memory = memory;
        p.persistencePath = path;
        p.flushSeconds = 1;
        p.enabled = true;
        return p;
    }
}