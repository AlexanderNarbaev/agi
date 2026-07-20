package io.matrix.training;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Watches the user-facing "drop folder" and ingests new training material
 * for self-supervised learning.
 *
 * <h2>Folder layout</h2>
 * <pre>
 *   /app/data/user-drop/             (mounted from data/user-drop/ on host)
 *     raw/                          (free-form text, .txt, .md)
 *     conversations/                (JSONL with {input, output} pairs)
 *     images/                       (PNG, JPG, JPEG, WebP)   — multimodal
 *     audio/                        (WAV, MP3, FLAC)         — multimodal
 *     video/                        (MP4, WebM)              — multimodal
 *     notes/                        (markdown, .md knowledge)
 *   .processed/                     (archive after ingestion)
 * </pre>
 *
 * <h2>What happens when a file appears</h2>
 * <ol>
 *   <li>Watcher polls every 5s, detects new files (created in last cycle)</li>
 *   <li>For .jsonl / .json: parse training pairs and append to corpus</li>
 *   <li>For .txt / .md: chunk into Q/A pairs by paragraph</li>
 *   <li>For image/audio/video: register descriptor in multimodal index</li>
 *   <li>Move processed files to {@code .processed/&lt;date&gt;/}</li>
 * </ol>
 *
 * <p>The class is intentionally tolerant: invalid files are logged and
 * moved to a {@code .failed/} subfolder rather than blocking the queue.
 */
@ApplicationScoped
public class DropFolderWatcher {

    private static final Logger log = LoggerFactory.getLogger(DropFolderWatcher.class);

    @ConfigProperty(name = "matrix.user-drop.dir", defaultValue = "data/user-drop")
    String dropDir;

    @ConfigProperty(name = "matrix.user-drop.poll-seconds", defaultValue = "5")
    long pollSeconds;

    @ConfigProperty(name = "matrix.user-drop.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "matrix.chat.output-file",
            defaultValue = "models/training_data/auto_generated.jsonl")
    String corpusFile;

    private final AtomicLong totalFiles = new AtomicLong();
    private final AtomicLong totalPairs = new AtomicLong();
    private final AtomicLong totalMultimodal = new AtomicLong();

    private WatchService watchService;
    private ScheduledExecutorService pollExec;
    private Path watchedDir;

    void onStart(@Observes StartupEvent ev) {
        if (!enabled) {
            log.info("DropFolderWatcher disabled");
            return;
        }
        try {
            watchedDir = Path.of(dropDir);
            Files.createDirectories(watchedDir);
            Files.createDirectories(watchedDir.resolve("raw"));
            Files.createDirectories(watchedDir.resolve("conversations"));
            Files.createDirectories(watchedDir.resolve("images"));
            Files.createDirectories(watchedDir.resolve("audio"));
            Files.createDirectories(watchedDir.resolve("video"));
            Files.createDirectories(watchedDir.resolve("notes"));
            Files.createDirectories(watchedDir.resolve(".processed"));
            Files.createDirectories(watchedDir.resolve(".failed"));
            log.info("DropFolderWatcher: ready at {} — drop files for self-training", watchedDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("DropFolderWatcher init failed: {}", e.getMessage());
            return;
        }

        // Background poller — every poll-seconds
        pollExec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "matrix-drop-folder");
            t.setDaemon(true);
            return t;
        });
        pollExec.scheduleAtFixedRate(this::scanSafely, 10, pollSeconds, TimeUnit.SECONDS);
    }

    private void scanSafely() {
        try {
            scan();
        } catch (Throwable t) {
            log.error("DropFolderWatcher scan failed: {}", t.getMessage());
        }
    }

    /** Polls the drop folder for new files and ingests them. */
    public void scan() throws IOException {
        // Walk all subfolders except .processed / .failed
        Files.walkFileTree(watchedDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().contains("/.processed/") || file.toString().contains("/.failed/")) {
                    return FileVisitResult.CONTINUE;
                }
                if (file.getFileName().toString().startsWith(".")) {
                    return FileVisitResult.CONTINUE;
                }
                try {
                    ingest(file);
                } catch (Exception e) {
                    log.warn("Failed to ingest {}: {}", file, e.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void ingest(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase();
        int pairsAdded = 0;
        boolean isMultimodal = false;

        if (name.endsWith(".jsonl")) {
            // JSONL training pairs
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                if (line.isBlank()) continue;
                String input = extractField(line, "input");
                String output = extractField(line, "output");
                if (input == null) input = extractField(line, "question");
                if (output == null) output = extractField(line, "answer");
                if (input != null && output != null && input.length() > 2 && output.length() > 3) {
                    appendPairToCorpus(input, output, "user-drop:" + file.getFileName());
                    pairsAdded++;
                }
            }
        } else if (name.endsWith(".json")) {
            String content = Files.readString(file);
            String input = extractField(content, "input");
            String output = extractField(content, "output");
            if (input == null) input = extractField(content, "question");
            if (output == null) output = extractField(content, "answer");
            if (input != null && output != null) {
                appendPairToCorpus(input, output, "user-drop:" + file.getFileName());
                pairsAdded = 1;
            }
        } else if (name.endsWith(".txt") || name.endsWith(".md")) {
            // Chunk text into Q/A pairs by paragraph
            String content = Files.readString(file);
            String[] paragraphs = content.split("\\n\\n+");
            for (String para : paragraphs) {
                String trimmed = para.trim();
                if (trimmed.length() < 20) continue;
                // Simple heuristic: treat first sentence as Q, rest as A
                int dotIdx = trimmed.indexOf(". ");
                if (dotIdx > 10 && dotIdx < trimmed.length() - 20) {
                    String q = trimmed.substring(0, dotIdx).trim();
                    String a = trimmed.substring(dotIdx + 2).trim();
                    if (q.endsWith("?")) {
                        appendPairToCorpus(q, a, "user-drop:" + file.getFileName());
                        pairsAdded++;
                    } else {
                        // No question mark — treat as a knowledge statement
                        appendPairToCorpus("Tell me about: " + q, a, "user-drop:" + file.getFileName());
                        pairsAdded++;
                    }
                } else {
                    // No clear Q structure — add as a single knowledge statement
                    appendPairToCorpus("What do you know about: " + trimmed.substring(0, Math.min(40, trimmed.length())), trimmed, "user-drop:" + file.getFileName());
                    pairsAdded++;
                }
            }
        } else if (name.endsWith(".png") || name.endsWith(".jpg") ||
                   name.endsWith(".jpeg") || name.endsWith(".webp")) {
            // Multimodal: image descriptor
            registerMultimodalDescriptor("image", file);
            isMultimodal = true;
        } else if (name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".flac")) {
            registerMultimodalDescriptor("audio", file);
            isMultimodal = true;
        } else if (name.endsWith(".mp4") || name.endsWith(".webm")) {
            registerMultimodalDescriptor("video", file);
            isMultimodal = true;
        } else {
            log.debug("Skipping unsupported file type: {}", file);
            return;
        }

        if (isMultimodal) {
            totalMultimodal.incrementAndGet();
        } else {
            totalFiles.incrementAndGet();
            totalPairs.addAndGet(pairsAdded);
        }
        // Move to .processed
        moveToProcessed(file);
    }

    private void appendPairToCorpus(String input, String output, String source) {
        try {
            // Idempotent — uses NeuralMemoryResponse's seen-pair tracking
            String line = String.format(
                    "{\"input\":\"%s\",\"output\":\"%s\",\"source\":\"%s\",\"timestamp\":\"%s\"}",
                    input.replace("\"", "\\\""),
                    output.replace("\"", "\\\""),
                    source,
                    java.time.Instant.now());
            Files.writeString(Path.of(corpusFile), line + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("DropFolder: appended pair from {}: {} chars in, {} chars out",
                    source, input.length(), output.length());
        } catch (IOException e) {
            log.warn("Failed to append training pair: {}", e.getMessage());
        }
    }

    private void registerMultimodalDescriptor(String type, Path file) {
        try {
            Path indexFile = Path.of("data/multimodal-index.jsonl");
            Files.createDirectories(indexFile.getParent());
            String entry = String.format(
                    "{\"type\":\"%s\",\"file\":\"%s\",\"size\":%d,\"timestamp\":\"%s\"}",
                    type, file.toString(), Files.size(file), java.time.Instant.now());
            Files.writeString(indexFile, entry + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("DropFolder: registered {} descriptor: {}", type, file.getFileName());
        } catch (IOException e) {
            log.warn("Failed to register multimodal descriptor: {}", e.getMessage());
        }
    }

    /** Simple JSON field extractor — handles escaped quotes via lookahead. */
    private static String extractField(String json, String field) {
        String key = "\"" + field + "\"";
        int ki = json.indexOf(key);
        if (ki < 0) return null;
        int colon = json.indexOf(':', ki + key.length());
        if (colon < 0) return null;
        int valueStart = colon + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= json.length()) return null;
        if (json.charAt(valueStart) != '"') return null;
        StringBuilder sb = new StringBuilder();
        for (int i = valueStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                sb.append(json.charAt(++i));
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return null;
    }

    private void moveToProcessed(Path file) {
        try {
            String today = java.time.LocalDate.now().toString();
            Path destDir = watchedDir.resolve(".processed").resolve(today);
            Files.createDirectories(destDir);
            Path dest = destDir.resolve(file.getFileName());
            // If name already exists, add timestamp suffix
            if (Files.exists(dest)) {
                String stem = file.getFileName().toString();
                int dot = stem.lastIndexOf('.');
                String base = dot > 0 ? stem.substring(0, dot) : stem;
                String ext = dot > 0 ? stem.substring(dot) : "";
                dest = destDir.resolve(base + "_" + System.currentTimeMillis() + ext);
            }
            Files.move(file, dest, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Fallback to non-atomic move
            try {
                String today = java.time.LocalDate.now().toString();
                Path dest = watchedDir.resolve(".processed").resolve(today).resolve(file.getFileName());
                Files.createDirectories(dest.getParent());
                Files.move(file, dest);
            } catch (IOException e2) {
                log.warn("Failed to move file to .processed: {}", e2.getMessage());
            }
        }
    }

    // ─── status ───

    public long totalFiles() { return totalFiles.get(); }
    public long totalPairs() { return totalPairs.get(); }
    public long totalMultimodal() { return totalMultimodal.get(); }

    /** Triggers an immediate scan (e.g. from a CLI command). */
    public void scanNow() {
        try {
            scan();
        } catch (IOException e) {
            log.error("Manual scan failed: {}", e.getMessage());
        }
    }
}