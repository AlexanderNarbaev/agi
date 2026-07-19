package io.matrix.imports;

import io.matrix.neuron.TruthTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Universal weight importer — orchestrates download → parse → projection into
 * the Matrix FNL pool. This is the entry-point of {@code L24_WeightImport.md}:
 * one Matrix instance can absorb weights from many different HF models.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>{@link AdaptiveSelector} picks which catalog entries to ingest,
 *       given a configurable disk budget.</li>
 *   <li>{@link WeightSource} (e.g. {@link HuggingFaceHubSource}) downloads only
 *       the weight shards — no tokenizer, no optimizer state.</li>
 *   <li>{@link SafetensorsReader} parses each shard, exposing per-tensor metadata.</li>
 *   <li>{@link TensorProjector} converts each tensor's float[] into a batch of
 *       {@link TruthTable} neurons tagged with provenance.</li>
 *   <li>The aggregated {@link IngestReport} lists every neuron produced plus
 *       the source model and tensor, ready to be merged into NoosphereRegistry
 *       as a single FNL package.</li>
 * </ol>
 *
 * <p>Single-threaded by default; concurrent ingestion can be layered on top via
 * parallel streams without changing this class's contract.
 */
public final class WeightImporter {

    private static final Logger log = LoggerFactory.getLogger(WeightImporter.class);

    private final WeightSource source;
    private final AdaptiveSelector selector;
    private final SafetensorsReader safetensors;
    private final TensorProjector projector;
    private final Path cacheRoot;

    public WeightImporter(Path cacheRoot) {
        this(new HuggingFaceHubSource(), new AdaptiveSelector(),
                new SafetensorsReader(), new TensorProjector(), cacheRoot);
    }

    public WeightImporter(WeightSource source, AdaptiveSelector selector,
                          SafetensorsReader safetensors, TensorProjector projector,
                          Path cacheRoot) {
        this.source = source;
        this.selector = selector;
        this.safetensors = safetensors;
        this.projector = projector;
        this.cacheRoot = cacheRoot;
    }

    /** Pretty-print bytes (delegated). */
    public static String humanBytes(long bytes) {
        return AdaptiveSelector.humanBytes(bytes);
    }

    /**
     * Runs the universal ingestion pipeline.
     *
     * @return aggregated report of downloaded models, tensors processed and
     *         neurons produced (with provenance map)
     */
    public IngestReport ingestAll() {
        AdaptiveSelector.Selection picked = selector.select();
        log.info("{}", picked.summary());
        if (picked.isEmpty()) {
            throw new IllegalStateException(
                    "Adaptive selector could not choose any model within budget (" +
                            AdaptiveSelector.humanBytes(picked.diskBudgetBytes()) + ")");
        }

        Map<String, ModelIngest> byModel = new LinkedHashMap<>();
        AtomicLong totalBytes = new AtomicLong();
        AtomicLong totalTensors = new AtomicLong();
        AtomicLong totalNeurons = new AtomicLong();

        for (ModelCatalog.Entry entry : picked.selected()) {
            try {
                WeightSource.DownloadResult dl = source.download(entry.modelId(), cacheRoot);
                ModelIngest mi = ingestOne(entry, dl);
                byModel.put(entry.modelId(), mi);
                totalBytes.addAndGet(mi.downloadedBytes);
                totalTensors.addAndGet(mi.tensorCount);
                totalNeurons.addAndGet(mi.neuronCount);
                log.info("Ingested {} → {} tensors, {} neurons ({})",
                        entry.modelId(), mi.tensorCount(), mi.neuronCount(),
                        AdaptiveSelector.humanBytes(mi.downloadedBytes));
            } catch (WeightSource.WeightSourceException ws) {
                log.warn("Skipping {}: {}", entry.modelId(), ws.getMessage());
                byModel.put(entry.modelId(), ModelIngest.skipped(entry.modelId(), ws.getMessage()));
            }
        }

        return new IngestReport(byModel, picked,
                totalBytes.get(), totalTensors.get(), totalNeurons.get());
    }

    /** Ingest a single downloaded model from disk into the neuron pool. */
    private ModelIngest ingestOne(ModelCatalog.Entry entry,
                                  WeightSource.DownloadResult dl) {
        long tensors = 0;
        long neurons = 0;
        Map<String, TensorProjector.Projection> perTensor = new LinkedHashMap<>();
        try (FileChannel ignored = null) {
            for (Path shard : dl.files()) {
                String fileName = shard.getFileName().toString();
                if (!fileName.endsWith(".safetensors")) continue;
                SafetensorsReader.Header header = safetensors.readHeader(shard);
                try (FileChannel ch = FileChannel.open(shard)) {
                    for (String tensorName : header.tensorNames()) {
                        try {
                            SafetensorsReader.Tensor t = safetensors.loadTensor(ch, header, tensorName);
                            TensorProjector.Projection p = projector.project(t);
                            if (p.neuronCount() > 0) {
                                perTensor.put(t.name(), p);
                                neurons += p.neuronCount();
                            }
                            tensors++;
                        } catch (IOException ioe) {
                            log.warn("Tensor load failed: {} / {} — {}",
                                    shard.getFileName(), tensorName, ioe.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new WeightSource.WeightSourceException("IO failure ingesting " + entry.modelId(), e);
        }
        return new ModelIngest(entry.modelId(), entry.tier(), entry.architecture(),
                dl.totalBytes(), dl.sha256(), tensors, neurons, perTensor, null);
    }

    /**
     * Result of one model's ingestion.
     *
     * @param modelId         identifier of the source model
     * @param tier            size class
     * @param architecture    family tag (e.g. {@code "qwen"})
     * @param downloadedBytes sum of file sizes written to disk
     * @param sha256          aggregate content fingerprint
     * @param tensorCount     count of tensors successfully parsed
     * @param neuronCount     count of {@link TruthTable} instances produced
     * @param projections     per-tensor projection (with provenance)
     * @param error           null on success; otherwise the failure reason
     */
    public record ModelIngest(
            String modelId,
            ModelCatalog.Tier tier,
            String architecture,
            long downloadedBytes,
            String sha256,
            long tensorCount,
            long neuronCount,
            Map<String, TensorProjector.Projection> projections,
            String error) {

        public boolean isOk() { return error == null; }

        public static ModelIngest skipped(String modelId, String reason) {
            return new ModelIngest(modelId, null, null, 0L, "", 0L, 0L,
                    Map.of(), reason);
        }

        /** List of {@link TruthTable} flattened from every projection, preserving order. */
        public List<TruthTable> allNeurons() {
            List<TruthTable> out = new ArrayList<>((int) neuronCount);
            for (TensorProjector.Projection p : projections.values()) out.addAll(p.truthTables());
            return out;
        }
    }

    /**
     * Top-level report from {@link #ingestAll()}.
     *
     * @param byModel       per-model detail
     * @param selection     adaptive-selector decision that drove the run
     * @param totalBytes    sum of disk downloads across all models
     * @param totalTensors  number of individual tensors processed
     * @param totalNeurons  number of TruthTables produced
     */
    public record IngestReport(
            Map<String, ModelIngest> byModel,
            AdaptiveSelector.Selection selection,
            long totalBytes,
            long totalTensors,
            long totalNeurons) {

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("Universal ingest report\n");
            sb.append("  selection: ").append(selection.summary()).append('\n');
            sb.append("  totals: ")
                    .append(WeightImporter.humanBytes(totalBytes))
                    .append(" over ").append(byModel.size()).append(" models, ")
                    .append(totalTensors).append(" tensors → ")
                    .append(totalNeurons).append(" neurons\n");
            for (Map.Entry<String, ModelIngest> e : byModel.entrySet()) {
                sb.append("  • ").append(e.getKey());
                ModelIngest m = e.getValue();
                if (m.isOk()) {
                    sb.append(" [").append(m.architecture()).append('/')
                            .append(WeightImporter.humanBytes(m.downloadedBytes)).append("] ")
                            .append(m.tensorCount()).append(" tensors, ")
                            .append(m.neuronCount()).append(" neurons");
                } else {
                    sb.append("  ⚠ SKIPPED: ").append(m.error());
                }
                sb.append('\n');
            }
            return sb.toString();
        }
    }
}
