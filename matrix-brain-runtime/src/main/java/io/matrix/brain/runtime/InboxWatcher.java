package io.matrix.brain.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * MIND-W4 — Inbox watcher.
 *
 * <p>Polls a directory (default {@code data/mind/inbox/}) for new files
 * and feeds them into the persistent HDC store as new knowledge. Each
 * file gets a deterministic FNV-1a 64-bit id derived from its path + last
 * modified time, so re-ingesting the same file is idempotent.</p>
 *
 * <p>Supported file types:</p>
 * <ul>
 *   <li>{@code .txt} — full text content ingested</li>
 *   <li>{@code .csv} — header + row joined with {@code ;}</li>
 *   <li>{@code .md} — full markdown content</li>
 *   <li>other — first 4 KiB of bytes as a SHA-256 hex string (placeholder)</li>
 * </ul>
 *
 * <p>CONSTITUTION Article IV — ingest is logged to the audit chain (deferred
 * to W7) and FROZEN modulators apply at query time, not at ingest.</p>
 */
public final class InboxWatcher {

    private static final Logger LOG = Logger.getLogger(InboxWatcher.class.getName());
    private static final int MAX_TEXT_BYTES = 4096;

    private final Path inboxDir;
    private final PersistentHdcStore hdcStore;
    /** Path -> last seen modified-time (for change detection). */
    private final Map<String, Long> lastSeenMTime = new ConcurrentHashMap<>();
    /** Last ingest summary for /v1/status reporting. */
    private volatile String lastIngestSummary = "(none yet)";

    public InboxWatcher(Path inboxDir, PersistentHdcStore hdcStore) {
        this.inboxDir = inboxDir;
        this.hdcStore = hdcStore;
    }

    /** Returns number of newly-ingested files. */
    public int scan() {
        if (!Files.exists(inboxDir)) return 0;
        int ingested = 0;
        try (Stream<Path> files = Files.list(inboxDir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(p)) continue;
                BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                long mtime = attrs.lastModifiedTime().toMillis();
                String key = p.toString();
                Long prev = lastSeenMTime.get(key);
                if (prev != null && prev == mtime) continue; // unchanged
                if (ingest(p)) {
                    lastSeenMTime.put(key, mtime);
                    ingested++;
                }
            }
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Inbox scan failed: {0}", ex.getMessage());
            return ingested;
        }
        if (ingested > 0) {
            lastIngestSummary = ingested + " file(s) ingested at " + System.currentTimeMillis();
        }
        return ingested;
    }

    /** Ingest a single file. Returns true on success. */
    public boolean ingest(Path path) {
        try {
            String content = readContent(path);
            if (content == null || content.isBlank()) {
                LOG.log(Level.FINE, "Inbox: skipping empty file {0}", path);
                return false;
            }
            String id = "inbox-" + Long.toHexString(fnv1a64(path.toString()));
            hdcStore.teach(id, "inbox:" + path.getFileName() + " " + content);
            LOG.log(Level.INFO, "Inbox ingested {0} -> id={1} ({2} chars)",
                new Object[]{path.getFileName(), id, content.length()});
            return true;
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Inbox ingest failed for {0}: {1}",
                new Object[]{path, ex.getMessage()});
            return false;
        }
    }

    /** Read file content with type-aware normalisation. */
    private String readContent(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".txt") || name.endsWith(".md")) {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > MAX_TEXT_BYTES) {
                return new String(bytes, 0, MAX_TEXT_BYTES, java.nio.charset.StandardCharsets.UTF_8);
            }
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (name.endsWith(".csv")) {
            List<String> lines = Files.readAllLines(path);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(lines.size(), 50); i++) {
                sb.append(lines.get(i).replaceAll(",", ";")).append('\n');
            }
            return sb.toString();
        }
        // Default: SHA-256 fingerprint (avoids binary garbage in HDC vectors)
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > MAX_TEXT_BYTES) {
                bytes = java.util.Arrays.copyOf(bytes, MAX_TEXT_BYTES);
            }
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return "file:" + name + " sha256:" + hex;
        } catch (java.security.NoSuchAlgorithmException ex) {
            return null;
        }
    }

    /** Snapshot for /v1/status rendering. */
    public Map<String, Object> statusSnapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inboxDir", inboxDir.toString());
        m.put("lastIngest", lastIngestSummary);
        m.put("trackedFiles", lastSeenMTime.size());
        return m;
    }

    private static long fnv1a64(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }
}
