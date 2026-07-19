package io.matrix.imports;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Curated registry of HuggingFace models suitable for ingestion into the Matrix FNL pool.
 *
 * <p>Models are organised by size class so {@link AdaptiveSelector} can pick the
 * biggest compatible set given the machine's free disk and memory.
 *
 * <p>Source: <a href="https://huggingface.co">huggingface.co</a>.
 * Only the safetensors weight files are referenced; no full-model (with optimizer
 * state) downloads are performed.
 *
 * <p>Ref: L24_WeightImport.md (proposed).
 */
public final class ModelCatalog {

    /** Tier classification used by adaptive selection. */
    public enum Tier {
        TINY,
        MEDIUM,
        LARGE,
        HEAVY
    }

    /**
     * One entry in the catalog.
     *
     * @param modelId        HF {@code owner/name}
     * @param tier           size bucket
     * @param estimatedBytes best-effort on-disk weight size
     * @param parameterCount number of parameters (informational only)
     * @param architecture   family tag (e.g. {@code "qwen"}, {@code "phi"}, {@code "smollm2"})
     * @param notes          human-readable context
     */
    public record Entry(
            String modelId,
            Tier tier,
            long estimatedBytes,
            long parameterCount,
            String architecture,
            String notes
    ) {
        public String sizeHuman() {
            double mb = estimatedBytes / 1_000_000.0;
            if (mb < 1000) return String.format("%.0f MB", mb);
            return String.format("%.1f GB", mb / 1000.0);
        }

        public long approxNeurons() {
            // ~3 TruthTables per weight cluster; 1 cluster = sqrt(params)
            return Math.max(1, parameterCount / 1000);
        }
    }

    /** Tiny tier: comfortable on CPU laptops with &lt; 8 GB RAM. */
    public static final Set<Entry> TINY = Set.of(
            new Entry("HuggingFaceTB/SmolLM2-360M-Instruct", Tier.TINY, 360L * 1_048_576, 360_000_000L,
                    "smollm2", "Ultra-compact model; fits in 1 GB resident"),
            new Entry("Qwen/Qwen3-0.6B", Tier.TINY, 600L * 1_048_576, 600_000_000L,
                    "qwen3", "Qwen 3 base, smallest variant"),
            new Entry("Qwen/Qwen3-1.7B", Tier.TINY, 1_700L * 1_048_576, 1_700_000_000L,
                    "qwen3", "Qwen 3 base, 1.7B params"),
            new Entry("Qwen/Qwen2.5-1.5B-Instruct", Tier.TINY, 1_500L * 1_048_576, 1_500_000_000L,
                    "qwen2.5", "Qwen 2.5 instruction-tuned 1.5B")
    );

    /** Medium tier: requires 16+ GB RAM. */
    public static final Set<Entry> MEDIUM = Set.of(
            new Entry("google/gemma-3-1b-it", Tier.MEDIUM, 2_000L * 1_048_576, 1_000_000_000L,
                    "gemma3", "Gemma 3 1B instruction-tuned"),
            new Entry("microsoft/Phi-4-mini-instruct", Tier.MEDIUM, 3_600L * 1_048_576, 3_800_000_000L,
                    "phi4", "Phi-4 mini, instruction-tuned"),
            new Entry("deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B", Tier.MEDIUM, 3_000L * 1_048_576, 1_500_000_000L,
                    "deepseek-r1", "DeepSeek R1 reasoning distilled to Qwen 1.5B")
    );

    /** Large tier: 24+ GB RAM or quantised inference. */
    public static final Set<Entry> LARGE = Set.of(
            new Entry("meta-llama/Llama-3.2-3B-Instruct", Tier.LARGE, 6_000L * 1_048_576, 3_200_000_000L,
                    "llama3.2", "Llama 3.2 3B instruction-tuned"),
            new Entry("mistralai/Mistral-7B-Instruct-v0.3", Tier.LARGE, 14_000L * 1_048_576, 7_200_000_000L,
                    "mistral", "Mistral 7B instruction-tuned v0.3")
    );

    /** Heavy tier: requires GPU or extensive quantisation; rarely used for ingestion. */
    public static final Set<Entry> HEAVY = Set.of(
            new Entry("meta-llama/Llama-3.1-70B-Instruct", Tier.HEAVY, 140_000L * 1_048_576, 70_000_000_000L,
                    "llama3.1", "Llama 3.1 70B — GPU/quantised only")
    );

    /** Aggregate: all entries grouped by tier for easy iteration. */
    public static Map<Tier, List<Entry>> all() {
        Map<Tier, List<Entry>> result = new LinkedHashMap<>();
        result.put(Tier.TINY, List.copyOf(TINY));
        result.put(Tier.MEDIUM, List.copyOf(MEDIUM));
        result.put(Tier.LARGE, List.copyOf(LARGE));
        result.put(Tier.HEAVY, List.copyOf(HEAVY));
        return result;
    }

    /** Lookup entry by model id, or {@code null} if not in catalog. */
    public static Entry findById(String modelId) {
        for (var tier : all().values()) {
            for (var entry : tier) {
                if (entry.modelId().equals(modelId)) return entry;
            }
        }
        return null;
    }

    /** All unique entries across tiers in a deterministic order. */
    public static Set<Entry> allEntries() {
        Set<Entry> combined = new TreeSet<>((a, b) -> a.modelId().compareTo(b.modelId()));
        all().values().forEach(combined::addAll);
        return combined;
    }

    /** A short, human-readable summary useful for logging & UI. */
    public static String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Model catalog: ");
        for (var tier : Tier.values()) {
            sb.append(tier).append("=").append(all().get(tier).size()).append(' ');
        }
        return sb.toString().trim();
    }

    private ModelCatalog() { /* static-only */ }

    /** Convenience: aggregate bytes for an iterable of entries. */
    public static long totalBytes(Iterable<Entry> entries) {
        long total = 0;
        for (Entry e : entries) total += e.estimatedBytes();
        return total;
    }

    /** Suppress unused warnings on the small helper lists. */
    @SuppressWarnings("unused")
    private static final List<Entry> ALL_FLAT = Arrays.asList(
            TINY.toArray(new Entry[0]));
}
