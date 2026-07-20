package io.matrix.training;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multimodal training scaffold — supports text, code, image, music, graphics,
 * and video training material.
 *
 * <h2>Supported modalities</h2>
 * <table>
 *   <tr><th>Modality</th><th>Format</th><th>What happens</th></tr>
 *   <tr><td>text</td><td>.txt .md .json .jsonl</td><td>Q/A pair extraction via DropFolderWatcher</td></tr>
 *   <tr><td>code</td><td>.java .kt .py .js .ts .go .rs .cpp .c .sh</td><td>Syntax-aware chunking, line comments extraction</td></tr>
 *   <tr><td>image</td><td>.png .jpg .jpeg .webp</td><td>Descriptor + checksum; placeholder for future CNN encoder</td></tr>
 *   <tr><td>music</td><td>.wav .mp3 .flac</td><td>Descriptor + duration; placeholder for future audio encoder</td></tr>
 *   <tr><td>video</td><td>.mp4 .webm</td><td>Descriptor + frame count; placeholder for future video encoder</td></tr>
 *   <tr><td>graphics</td><td>.svg .pdf .glb</td><td>Descriptor + metadata; placeholder for future graphics encoder</td></tr>
 * </table>
 *
 * <h2>Current state</h2>
 * <p>For modalities that don't yet have dedicated encoders, the scaffold
 * stores file descriptors in {@code data/multimodal-index.jsonl} so the
 * system has a record of available training material. When the encoder
 * (e.g. CLIP for images, CLAP for audio) becomes available, the descriptors
 * can be retroactively processed.
 *
 * <p>For text and code, full ingestion is performed (Q/A pair generation).
 */
@ApplicationScoped
public class MultimodalTrainer {

    private static final Logger log = LoggerFactory.getLogger(MultimodalTrainer.class);

    @ConfigProperty(name = "matrix.multimodal.index-file",
            defaultValue = "data/multimodal-index.jsonl")
    String indexFile;

    private final AtomicLong totalText = new AtomicLong();
    private final AtomicLong totalCode = new AtomicLong();
    private final AtomicLong totalImage = new AtomicLong();
    private final AtomicLong totalAudio = new AtomicLong();
    private final AtomicLong totalVideo = new AtomicLong();
    private final AtomicLong totalGraphics = new AtomicLong();

    void onStart(@Observes StartupEvent ev) {
        try {
            Files.createDirectories(Path.of(indexFile).getParent());
            log.info("MultimodalTrainer: index at {}", Path.of(indexFile).toAbsolutePath());
        } catch (IOException e) {
            log.warn("MultimodalTrainer init failed: {}", e.getMessage());
        }
    }

    /**
     * Dispatches a file to the appropriate modality handler.
     *
     * @return modality id, or "unknown" if format is not supported
     */
    public String classifyAndIndex(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        String modality;
        if (name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".jsonl") || name.endsWith(".json")) {
            modality = "text"; totalText.incrementAndGet();
        } else if (name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".py") ||
                   name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".go") ||
                   name.endsWith(".rs") || name.endsWith(".cpp") || name.endsWith(".c") ||
                   name.endsWith(".sh") || name.endsWith(".rb")) {
            modality = "code"; totalCode.incrementAndGet();
        } else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp")) {
            modality = "image"; totalImage.incrementAndGet();
        } else if (name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".flac")) {
            modality = "audio"; totalAudio.incrementAndGet();
        } else if (name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".mov")) {
            modality = "video"; totalVideo.incrementAndGet();
        } else if (name.endsWith(".svg") || name.endsWith(".pdf") || name.endsWith(".glb") || name.endsWith(".gltf")) {
            modality = "graphics"; totalGraphics.incrementAndGet();
        } else {
            return "unknown";
        }
        indexModality(modality, file);
        return modality;
    }

    private void indexModality(String modality, Path file) {
        try {
            long size = 0;
            try { size = Files.size(file); } catch (IOException ignored) {}
            String entry = String.format(
                    "{\"modality\":\"%s\",\"file\":\"%s\",\"size\":%d,\"timestamp\":\"%s\"}",
                    modality, file.toString().replace("\\", "\\\\"), size, java.time.Instant.now());
            Files.writeString(Path.of(indexFile), entry + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("MultimodalTrainer: indexed {} → {} ({} bytes)", file.getFileName(), modality, size);
        } catch (IOException e) {
            log.warn("MultimodalTrainer: failed to index {}: {}", file, e.getMessage());
        }
    }

    // ─── status ───
    public long totalText() { return totalText.get(); }
    public long totalCode() { return totalCode.get(); }
    public long totalImage() { return totalImage.get(); }
    public long totalAudio() { return totalAudio.get(); }
    public long totalVideo() { return totalVideo.get(); }
    public long totalGraphics() { return totalGraphics.get(); }
}