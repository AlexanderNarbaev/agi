package io.matrix.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Persists every chat interaction to disk so that real user conversations
 * become training material for the neural brain.
 *
 * <h2>Storage layout</h2>
 * <pre>
 *   data/conversations/
 *     YYYY-MM-DD.ndjson            ← one record per line, append-only
 *     daily_index.json             ← {date: count, lastUpdated}
 * </pre>
 *
 * <h2>Lifecycle</h2>
 * <ul>
 *   <li>{@link #onStart} — bootstraps storage dir, starts flush daemon (5s tick)</li>
 *   <li>{@link #record(ConversationRecord)} — non-blocking enqueue</li>
 *   <li>{@link #flush()} — drains queue to disk (called by scheduler or on shutdown)</li>
 * </ul>
 *
 * <h2>Why NDJSON + queue</h2>
 * <p>NDJSON allows streaming reads (training pipeline never blocks on the whole log).
 * The in-memory queue absorbs request bursts without holding up the HTTP path.
 *
 * <p>Thread-safety: {@link ConcurrentLinkedQueue} for the buffer; the writer
 * is synchronized so a single flush holds the file lock.
 */
@ApplicationScoped
public class ConversationRecorder {

    private static final Logger log = LoggerFactory.getLogger(ConversationRecorder.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ObjectMapper JSON = new ObjectMapper()
            .findAndRegisterModules();

    @ConfigProperty(name = "matrix.chat.storage-dir",
            defaultValue = "data/conversations")
    String storageDir;

    @ConfigProperty(name = "matrix.chat.flush-interval-seconds", defaultValue = "5")
    long flushIntervalSeconds = 5;

    @ConfigProperty(name = "matrix.chat.enabled", defaultValue = "true")
    boolean enabled = true;

    private final ConcurrentLinkedQueue<ConversationRecord> buffer = new ConcurrentLinkedQueue<>();
    private final AtomicLong totalRecorded = new AtomicLong();
    private final AtomicLong totalFlushed = new AtomicLong();

    private Path currentDayDir;
    private String currentDateStr;
    private ScheduledExecutorService flushExecutor;
    private volatile boolean shuttingDown = false;

    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            log.info("ConversationRecorder disabled (matrix.chat.enabled=false)");
            return;
        }
        try {
            Path root = Path.of(storageDir);
            Files.createDirectories(root);
            currentDayDir = root;
            currentDateStr = DATE_FMT.format(LocalDate.now());
            log.info("ConversationRecorder started → dir={}, flush={}s",
                    root.toAbsolutePath(), flushIntervalSeconds);
        } catch (IOException e) {
            log.error("Failed to initialize ConversationRecorder: {}", e.getMessage());
            return;
        }

        flushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "matrix-conversation-flush");
            t.setDaemon(true);
            return t;
        });
        flushExecutor.scheduleAtFixedRate(this::flushSafely,
                flushIntervalSeconds, flushIntervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Enqueues a record for asynchronous persistence. Non-blocking, allocation-free.
     *
     * @param record the conversation record to persist
     */
    public void record(ConversationRecord record) {
        if (!enabled || record == null) {
            return;
        }
        buffer.offer(record);
        totalRecorded.incrementAndGet();
    }

    /**
     * Enqueues a batch of records. Equivalent to calling {@link #record} for each,
     * but slightly cheaper for bulk callers.
     */
    public void recordAll(List<ConversationRecord> records) {
        if (!enabled || records == null || records.isEmpty()) {
            return;
        }
        for (ConversationRecord r : records) {
            buffer.offer(r);
        }
        totalRecorded.addAndGet(records.size());
    }

    /** Number of records enqueued since startup (may be ahead of {@link #totalFlushed}). */
    public long totalRecorded() {
        return totalRecorded.get();
    }

    /** Number of records successfully persisted to disk. */
    public long totalFlushed() {
        return totalFlushed.get();
    }

    /** True when there is at least one record waiting to be flushed. */
    public boolean hasPending() {
        return !buffer.isEmpty();
    }

    /**
     * Synchronously drains the queue and persists everything to the daily NDJSON file.
     * Safe to call from any thread. No-op if the buffer is empty.
     */
    public synchronized int flush() {
        if (buffer.isEmpty()) {
            return 0;
        }
        try {
            rolloverIfNeeded();
            Path file = currentDayDir.resolve(currentDateStr + ".ndjson");
            int written = 0;
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                ConversationRecord rec;
                while ((rec = buffer.poll()) != null) {
                    w.write(JSON.writeValueAsString(rec));
                    w.newLine();
                    written++;
                }
            }
            totalFlushed.addAndGet(written);
            if (written > 0 && log.isDebugEnabled()) {
                log.debug("Flushed {} conversation records → {}", written, file);
            }
            return written;
        } catch (IOException e) {
            log.error("ConversationRecorder flush failed: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Reads all conversation records from disk. Intended for training-pipeline use,
     * not for the hot HTTP path.
     *
     * <p>Returns the records in chronological order across all stored days.
     *
     * @param maxRecords upper bound on returned records; older records are trimmed
     * @return immutable list of records
     */
    public List<ConversationRecord> readAll(int maxRecords) {
        if (!enabled) {
            return List.of();
        }
        Path root = Path.of(storageDir);
        if (!Files.exists(root)) {
            return List.of();
        }
        try {
            List<Path> files = new ArrayList<>();
            try (var stream = Files.list(root)) {
                stream.filter(p -> p.toString().endsWith(".ndjson"))
                        .forEach(files::add);
            }
            Collections.sort(files); // lexical order == chronological for ISO date prefixes
            List<ConversationRecord> all = new ArrayList<>();
            for (Path file : files) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line.isBlank()) continue;
                    try {
                        all.add(JSON.readValue(line, ConversationRecord.class));
                    } catch (IOException parseErr) {
                        log.warn("Skipping malformed conversation line in {}: {}",
                                file, parseErr.getMessage());
                    }
                }
            }
            int from = Math.max(0, all.size() - maxRecords);
            return List.copyOf(all.subList(from, all.size()));
        } catch (IOException e) {
            log.error("readAll failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Filesystem path to today's NDJSON log (may not exist yet). */
    public Path currentLogFile() {
        rolloverIfNeeded();
        return currentDayDir.resolve(currentDateStr + ".ndjson");
    }

    private void flushSafely() {
        try {
            flush();
        } catch (Throwable t) {
            log.error("Scheduled flush crashed: {}", t.getMessage());
        }
    }

    private void rolloverIfNeeded() {
        String today = DATE_FMT.format(LocalDate.now());
        if (!today.equals(currentDateStr)) {
            try {
                Files.createDirectories(Path.of(storageDir));
                currentDayDir = Path.of(storageDir);
                currentDateStr = today;
                log.info("ConversationRecorder day rollover → {}", today);
            } catch (IOException e) {
                log.error("Day rollover failed: {}", e.getMessage());
            }
        }
    }

    void onStop(@Observes io.quarkus.runtime.ShutdownEvent event) {
        shuttingDown = true;
        if (flushExecutor != null) {
            flushExecutor.shutdown();
            try {
                if (!flushExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    flushExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Final synchronous flush so we don't lose anything in flight
        flush();
        log.info("ConversationRecorder stopped. totalRecorded={}, totalFlushed={}",
                totalRecorded.get(), totalFlushed.get());
    }

    // Test seam: force flush of all pending records synchronously
    int flushForTest() {
        return flush();
    }

    // Test seam: peek buffer size without mutating
    int bufferSizeForTest() {
        return buffer.size();
    }
}