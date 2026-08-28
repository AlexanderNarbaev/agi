package io.matrix.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.bir.Bir;
import io.matrix.bir.TtForm;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MATRIX Model Registry (M-A.T.R.I.X.6, this wave): a Java-side store of
 * distilled BIR models that the Quarkus app uses directly.
 *
 * <p>The current entries:
 * <ul>
 *   <li><b>sentiment-classifier</b> — TtForm distilled from
 *       DistilBERT SST-2 (loaded from
 *       {@code distilled-models/sentiment-classifier.json} if present;
 *       otherwise the synthetic parity-rule placeholder).</li>
 *   <li><b>topic-router</b> — a 4-input TtForm for backend routing.</li>
 * </ul>
 */
@Singleton
public final class ModelRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModelRegistry.class);

    /** A registered model entry: name → Bir + metadata. */
    public record Entry(String name, String origin, Bir bir, String description) {
        public Entry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(bir, "bir");
        }
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private final AtomicLong evalCount = new AtomicLong();

    public ModelRegistry() {
        registerDefaults();
    }

    @PostConstruct
    public void init() {
        // load any persisted distilled models from the resource path
        loadDistilledFromResources();
        log.info("ModelRegistry initialised with {} models: {}",
                entries.size(), entries.keySet());
    }

    /** Alias a distilled model under the canonical short name. */
    private void alias(String alias, String canonical) {
        Entry src = entries.get(canonical);
        if (src != null) {
            entries.put(alias, new Entry(alias, src.origin(), src.bir(), src.description()));
        }
    }

    private void registerDefaults() {
        // Topic router: always present (cheap)
        long[] topicTable = new long[16];
        for (int i = 0; i < topicTable.length; i++) {
            topicTable[i] = (i & 0x3);
        }
        TtForm router = new TtForm(4, topicTable, "topic-router/default", 1.0);
        entries.put("topic-router", new Entry(
                "topic-router",
                "Internal (W6.2)",
                router,
                "Routes text → generation backend. Codes: 0=chat, 1=qa, 2=command, 3=none."));
    }

    /** Load a distilled model from the classpath. Used at startup. */
    private void loadDistilledFromResources() {
        String[] candidates = {
                "distilled-models/sentiment-classifier.json",
                "distilled-models/distilbert-sst2.json",
        };
        for (String path : candidates) {
            Optional<Entry> loaded = loadOne(path);
            if (loaded.isPresent()) {
                Entry e = loaded.get();
                entries.put(e.name(), e);
                // alias under the short name so the chat pipeline can
                // call predictSentiment() without knowing the artifact's
                // long identifier
                if (!e.name().equals("sentiment-classifier")) {
                    entries.put("sentiment-classifier", e);
                }
                log.info("loaded distilled model {} from classpath:{}",
                        e.name(), path);
            }
        }
    }

    private Optional<Entry> loadOne(String classpathPath) {
        try (InputStream is = ModelRegistry.class.getResourceAsStream(
                "/" + classpathPath)) {
            if (is == null) {
                return Optional.empty();
            }
            ObjectMapper m = new ObjectMapper();
            BirDef def = m.readValue(is, BirDef.class);
            TtForm tt = def.toTtForm();
            String desc = String.format("Distilled %d→%d BIR (fidelity=%.3f).",
                    def.inputBits, def.outputBits, def.fidelity);
            return Optional.of(new Entry(
                    def.name,
                    def.provenance == null ? "unknown" : String.valueOf(def.provenance),
                    tt, desc));
        } catch (Exception e) {
            log.warn("could not load {}: {}", classpathPath, e.getMessage());
            return Optional.empty();
        }
    }

    /** Register a new model entry. */
    public void register(Entry entry) {
        entries.put(entry.name(), entry);
        log.info("ModelRegistry: registered {} (origin={})", entry.name(), entry.origin());
    }

    public Entry get(String name) {
        return entries.get(name);
    }

    public java.util.Set<String> names() {
        return java.util.Set.copyOf(entries.keySet());
    }

    public long[] eval(String name, long[] input) {
        Entry e = entries.get(name);
        if (e == null) {
            throw new IllegalArgumentException("no model registered: " + name);
        }
        evalCount.incrementAndGet();
        return io.matrix.bir.BooleanRuntime.evaluate(e.bir(), input);
    }

    /** Convenience: predict sentiment (1 = positive, 0 = negative). */
    public int predictSentiment(long[] input20bit) {
        long[] out = eval("sentiment-classifier", input20bit);
        return (out.length > 0 && out[0] != 0) ? 1 : 0;
    }

    public int routeTopic(long[] input4bit) {
        long[] out = eval("topic-router", input4bit);
        return (int) (out[0] & 0x3);
    }

    public long totalEvaluations() {
        return evalCount.get();
    }

    public java.util.Map<String, Object> describe() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("totalEvaluations", totalEvaluations());
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        for (String name : entries.keySet()) {
            Entry e = entries.get(name);
            java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("name", e.name());
            entry.put("origin", e.origin());
            entry.put("description", e.description());
            entry.put("inputBits", e.bir().inputBits());
            list.add(entry);
        }
        out.put("models", list);
        return out;
    }

    /**
     * JSON schema for a distilled BIR saved by
     * {@code scripts/distill_distilbert_sentiment.py}. Used at startup
     * to load the saved artifact.
     */
    public static class BirDef {
        public String name;
        public int inputBits;
        public int outputBits;
        public String tableHex;
        public Object provenance;
        public double fidelity;
        public long ttl_seconds;

        public TtForm toTtForm() {
            int cells = 1 << Math.min(inputBits, 16); // cap at 2^16 for safety
            int longCount = (cells + 63) / 64;
            long[] table = new long[longCount];
            // TtForm packs bit i at table[i >>> 6] bit (i & 63)
            int len = Math.min(tableHex.length(), cells);
            for (int i = 0; i < len; i++) {
                if (tableHex.charAt(i) == '1') {
                    table[i >>> 6] |= (1L << (i & 63));
                }
            }
            return new TtForm(inputBits, table, name, fidelity);
        }
    }
}