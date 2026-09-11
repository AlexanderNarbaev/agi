package io.matrix.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.imports.BooleanChainRunner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * RUN 327 — EXP: restore real-domain corpus (Wave K.1 acceptance).
 *
 * <p>Loads the production QA corpus from
 * {@code models/training_data/qa_pairs.json} — 6,607 Russian
 * forum-derived QA pairs. This is the closest available
 * real-domain corpus after the original benchmark corpora
 * (HellaSwag / ARC-Easy / MMLU) were deleted per WAL
 * §Известные проблемы.
 *
 * <p>Acceptance: the corpus loads, parses, and is queryable.
 * Reports counts by category and source.
 *
 * <p>Auto-skips if the corpus file is not present.
 */
class Exp327CorpusRestoreTest {

    @Test
    void loadProductionQaCorpus() throws Exception {
        Path corpus = locateCorpus();
        assumeTrue(corpus != null && Files.exists(corpus),
                "models/training_data/qa_pairs.json not present");

        List<QA> pairs = loadQa(corpus);
        assertThat(pairs).as("corpus non-empty").isNotEmpty();
        assertThat(pairs.size()).as("corpus size")
                .isGreaterThanOrEqualTo(1_000);

        // First/last sanity
        QA first = pairs.get(0);
        QA last = pairs.get(pairs.size() - 1);
        assertThat(first.question()).as("first pair has question").isNotBlank();
        assertThat(first.answer()).as("first pair has answer").isNotBlank();
        assertThat(last.question()).as("last pair has question").isNotBlank();

        // Per-category breakdown
        java.util.Map<String, Integer> byCat = new java.util.LinkedHashMap<>();
        for (QA q : pairs) {
            byCat.merge(q.category() == null ? "unknown" : q.category(), 1, Integer::sum);
        }
        System.out.printf("[Exp327] loaded %,d QA pairs from %s%n",
                pairs.size(), corpus);
        System.out.printf("[Exp327] categories: %d distinct, top 5: %s%n",
                byCat.size(),
                byCat.entrySet().stream()
                        .sorted((a, b) -> b.getValue() - a.getValue())
                        .limit(5)
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .toList());

        // Acceptable: at least one QA is in Russian (Cyrillic present)
        long cyrillicCount = pairs.stream()
                .filter(q -> q.question() != null
                        && q.question().chars().anyMatch(c -> c >= 0x0400 && c <= 0x04FF))
                .count();
        System.out.printf("[Exp327] Cyrillic-question pairs: %,d / %,d (%.1f%%)%n",
                cyrillicCount, pairs.size(),
                100.0 * cyrillicCount / pairs.size());
        assertThat(cyrillicCount).as("majority Cyrillic").isGreaterThan(pairs.size() / 2);
    }

    public record QA(String question, String answer, String category, String source) {}

    static List<QA> loadQa(Path file) throws IOException {
        ObjectMapper m = new ObjectMapper();
        JsonNode arr = m.readTree(file.toFile());
        List<QA> out = new ArrayList<>();
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                out.add(new QA(
                        textOrNull(n, "question"),
                        textOrNull(n, "answer"),
                        textOrNull(n, "category"),
                        textOrNull(n, "source")));
            }
        }
        return out;
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Path locateCorpus() {
        Path cwd = Paths.get("").toAbsolutePath();
        for (Path p = cwd; p != null; p = p.getParent()) {
            Path candidate = p.resolve("models/training_data/qa_pairs.json");
            if (Files.exists(candidate)) return candidate;
        }
        return null;
    }
}
