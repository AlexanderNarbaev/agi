package io.matrix.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.api.ChainTrainerEndpoint;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-trainer (refinement of Phase 3): runs sign-descent training
 * on the loaded {@link BooleanChainRunner} at startup, using the
 * project's pre-existing training corpus
 * ({@code models/training_data/combined_training.json}).
 *
 * <p>After training, the chain's neurons specialize toward the corpus
 * patterns and {@code /v1/sandbox/chat} produces non-zero-density
 * decisions for inputs similar to the training data.
 *
 * <p>The trainer is intentionally agnostic to the source model — the
 * combined BooleanChainRunner is treated as MATRIX's single internal
 * pool regardless of which distilled weights contributed which
 * neurons (Qwen + TinyLlama + GPT-2 → all merged into one chain).
 */
@ApplicationScoped
public class AutoTrainer {

    private static final Logger log = LoggerFactory.getLogger(AutoTrainer.class);

    @Inject
    ChainTrainerEndpoint trainer;

    @ConfigProperty(name = "matrix.auto-train.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "matrix.auto-train.path",
                    defaultValue = "models/training_data/combined_training.json")
    String corpusPath;

    @ConfigProperty(name = "matrix.auto-train.epochs", defaultValue = "1")
    int epochs;

    private final ObjectMapper mapper = new ObjectMapper();

    void onStart(@Observes StartupEvent ev) {
        if (!enabled) return;
        if (trainer == null || trainer.isEmpty()) {
            log.info("AutoTrainer: chain not loaded; skipping");
            return;
        }
        Path p = Path.of(corpusPath);
        if (!Files.exists(p)) {
            log.info("AutoTrainer: corpus not found at {}; skipping", corpusPath);
            return;
        }
        train(p);
    }

    /** Train from a JSONL corpus path. Public for tests. */
    public void train(Path p) {
        try {
            List<Map<String, Object>> pairs = readPairs(p);
            if (pairs.isEmpty()) {
                log.info("AutoTrainer: empty corpus");
                return;
            }
            log.info("AutoTrainer: training on {} pairs from {}", pairs.size(), p);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("pairs", pairs);
            body.put("epochs", epochs);
            trainer.train(body);
            log.info("AutoTrainer: complete");
        } catch (Exception e) {
            log.warn("AutoTrainer: failed: {}", e.getMessage());
        }
    }

    private List<Map<String, Object>> readPairs(Path p) throws IOException {
        // Try JSON array first (combined_training.json)
        String text = Files.readString(p);
        try {
            List<Object> arr = mapper.readValue(text, List.class);
            return arr.stream().map(o -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                return Map.of(
                        "input", (Object) String.valueOf(m.getOrDefault("question",
                                m.getOrDefault("input", ""))),
                        "expected", (Object) String.valueOf(m.getOrDefault("answer",
                                m.getOrDefault("expected", ""))));
            }).limit(500).toList();  // cap at 500 to bound startup time
        } catch (Exception e) {
            // try JSONL (one JSON object per line)
            return Files.readAllLines(p).stream()
                    .filter(l -> !l.isBlank())
                    .limit(500)
                    .map(line -> {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = mapper.readValue(line, Map.class);
                            return Map.of(
                                    "input", String.valueOf(m.getOrDefault("question",
                                            m.getOrDefault("input", ""))),
                                    "expected", String.valueOf(m.getOrDefault("answer",
                                            m.getOrDefault("expected", ""))));
                        } catch (Exception ex) {
                            return null;
                        }
                    })
                    .filter(m -> m != null)
                    .map(m -> {
                        Map<String, Object> out = new LinkedHashMap<>();
                        out.put("input", m.get("input"));
                        out.put("expected", m.get("expected"));
                        return out;
                    })
                    .toList();
    }
    }
}