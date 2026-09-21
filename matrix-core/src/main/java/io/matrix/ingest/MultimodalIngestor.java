package io.matrix.ingest;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multi-modal ingestor: text, audio, video, photo, PDF, URLs.
 * Feeds chunks to the RAG system for knowledge accumulation.
 */
@ApplicationScoped
public class MultimodalIngestor {

    private static final Logger log = LoggerFactory.getLogger(MultimodalIngestor.class);

    private static final int CHUNK_SIZE = 512;

    private final AtomicLong totalIngested = new AtomicLong(0);
    private final AtomicLong totalChunks = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);

    public int ingestText(String text, String source, String title, String hash) {
        int chunks = (text.length() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        for (int i = 0; i < chunks; i++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(start + CHUNK_SIZE, text.length());
            String chunk = text.substring(start, end);
            storeChunk(chunk, "text", source, title, hash, i);
        }
        totalIngested.incrementAndGet();
        totalChunks.addAndGet(chunks);
        totalBytes.addAndGet(text.length());
        log.info("Ingested text: {} chunks, hash={}", chunks, hash.substring(0, 12));
        return chunks;
    }

    public int ingestBinary(String type, byte[] data, String source, String title, String hash) {
        // For binary data, extract metadata and create a stub chunk
        String meta = String.format("[%s] %d bytes from %s", type, data.length, source);
        storeChunk(meta, type, source, title, hash, 0);
        totalIngested.incrementAndGet();
        totalChunks.incrementAndGet();
        totalBytes.addAndGet(data.length);
        return 1;
    }

    public int ingestUrl(String url) {
        try {
            URI uri = URI.create(url);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestProperty("User-Agent", "M.A.T.R.I.X./3.58");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            
            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("URL fetch failed: {} -> {}", url, code);
                return 0;
            }
            
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                    if (sb.length() > 100_000) break; // Cap at 100KB
                }
            }
            
            String content = sb.toString();
            int chunks = ingestText(content, url, url, Integer.toHexString(content.hashCode()));
            log.info("Ingested URL {}: {} chars, {} chunks", url, content.length(), chunks);
            return chunks;
        } catch (Exception e) {
            log.warn("URL fetch error for {}: {}", url, e.getMessage());
            return 0;
        }
    }

    private void storeChunk(String content, String type, String source, String title, 
                            String hash, int chunkIndex) {
        // In a real system, this would feed into the RAG BooleanIndex
        // For now, we log the chunk for demonstration
        if (chunkIndex == 0) {
            log.debug("Storing {} chunk from {} (title={})", type, source, title);
        }
    }

    public long getTotalIngested() { return totalIngested.get(); }
    public long getTotalChunks() { return totalChunks.get(); }
    public long getTotalBytes() { return totalBytes.get(); }
}
