package io.matrix.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.quarkus.runtime.Startup;
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
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory retrieval index over training Q&A pairs.
 *
 * <p>Loaded at startup from {@code matrix.qa.corpus} (or the default
 * paths). New entries learned via {@code POST /v1/qa/learn} are
 * appended to the disk file and reloaded into the index.
 *
 * <p>Search algorithm: token-overlap scoring with simple
 * token-frequency weighting — same input text → same result
 * (deterministic, no LLM calls in the decision path).
 */
@ApplicationScoped
@Startup
public class QaCorpusIndex {

    private static final Logger log = LoggerFactory.getLogger(QaCorpusIndex.class);

    /** Tokenization: lowercase + split on non-word + drop 1-char + Russian stoplist. */
    private static final Set<String> STOPWORDS = Set.of(
            "и", "в", "на", "с", "по", "для", "что", "это", "как", "или",
            "the", "a", "an", "is", "are", "of", "to", "in", "for",
            "я", "не", "он", "она", "мы", "вы", "они", "этот", "тот"
    );

    @ConfigProperty(name = "matrix.qa.path", defaultValue = "models/training_data/qa_pairs.json")
    String qaPath;

    @ConfigProperty(name = "matrix.qa.forum-path", defaultValue = "models/training_data/forum_training_pairs.json")
    String forumPath;

    /** One Q&A entry. */
    public record Entry(int id, String question, String answer, String category, String source) {}

    /** Thread-safe mutable state of the index. */
    public static final class State {
        public final List<Entry> entries = new ArrayList<>();
        public final Map<String, List<int[]>> inverted = new HashMap<>();
        public final Map<Integer, Set<String>> docTokens = new HashMap<>();
        public final Map<String, Integer> docFreq = new HashMap<>();
        public final long loadedAt;

        public State(long loadedAt) { this.loadedAt = loadedAt; }

        public int size() { return entries.size(); }
    }

    private final AtomicReference<State> stateRef = new AtomicReference<>(new State(0L));
    private final ObjectMapper mapper = new ObjectMapper();

    /** Load corpus at startup. */
    void onStart(@Observes StartupEvent ev) {
        reload();
    }

    public synchronized void reload() {
        long t0 = System.currentTimeMillis();
        State s = new State(t0);
        try {
            if (Files.isRegularFile(Path.of(qaPath))) {
                loadQaPairs(Path.of(qaPath), s);
            }
        } catch (Exception e) {
            log.warn("Failed to load qa_pairs: {}", e.getMessage());
        }
        try {
            if (Files.isRegularFile(Path.of(forumPath))) {
                loadForumPairs(Path.of(forumPath), s);
            }
        } catch (Exception e) {
            log.warn("Failed to load forum pairs: {}", e.getMessage());
        }
        rebuildIndex(s);
        stateRef.set(s);
        log.info("QaCorpusIndex loaded: {} entries, {} unique tokens ({} ms)",
                s.size(), s.inverted.size(), System.currentTimeMillis() - t0);
    }

    private void loadQaPairs(Path file, State s) throws IOException {
        JsonNode root = mapper.readTree(file.toFile());
        if (!root.isArray()) return;
        int nextId = s.size();
        for (JsonNode n : root) {
            String q = textOrNull(n, "question");
            String a = textOrNull(n, "answer");
            if (q == null || a == null || q.isBlank() || a.isBlank()) continue;
            s.entries.add(new Entry(nextId++, q.trim(), a.trim(),
                    textOrDefault(n, "category", "general"),
                    textOrDefault(n, "source", "")));
        }
    }

    private void loadForumPairs(Path file, State s) throws IOException {
        JsonNode root = mapper.readTree(file.toFile());
        JsonNode pairs = root.has("pairs") ? root.get("pairs") : root;
        if (pairs == null || !pairs.isArray()) return;
        int nextId = s.size();
        for (JsonNode n : pairs) {
            String q = textOrNull(n, "question");
            String a = textOrNull(n, "answer");
            if (q == null || a == null || q.isBlank() || a.isBlank()) continue;
            s.entries.add(new Entry(nextId++, q.trim(), a.trim(),
                    textOrDefault(n, "category", "forum"),
                    textOrDefault(n, "source_id", "")));
        }
    }

    private void rebuildIndex(State s) {
        s.inverted.clear();
        s.docTokens.clear();
        s.docFreq.clear();
        for (Entry e : s.entries) {
            Set<String> tokens = tokenize(e.question());
            s.docTokens.put(e.id(), tokens);
            for (String t : tokens) {
                s.inverted.computeIfAbsent(t, k -> new ArrayList<>()).add(new int[]{e.id(), tokens.size()});
                s.docFreq.merge(t, 1, Integer::sum);
            }
        }
    }

    /** Search top-K entries by token overlap with the query. */
    public List<Entry> search(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        State s = stateRef.get();
        Set<String> qTokens = tokenize(query);
        if (qTokens.isEmpty() || s.entries.isEmpty()) return List.of();

        // Score each document by token overlap weighted by idf (inverse doc frequency)
        Map<Integer, Double> scores = new HashMap<>();
        int totalDocs = s.entries.size();
        for (String qt : qTokens) {
            List<int[]> post = s.inverted.get(qt);
            if (post == null || post.isEmpty()) continue;
            double idf = Math.log(1.0 + totalDocs / (1.0 + s.docFreq.getOrDefault(qt, 0)));
            for (int[] hit : post) {
                int docId = hit[0];
                int docLen = hit[1];
                double tfBoost = 1.0 / (1.0 + docLen);  // shorter docs score higher
                scores.merge(docId, idf * tfBoost, Double::sum);
            }
        }
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> s.entries.get(e.getKey()))
                .toList();
    }

    /** Score of the best match for query (idf-weighted overlap), or 0 if no overlap. */
    public double topScore(String query) {
        if (query == null || query.isBlank()) return 0.0;
        State s = stateRef.get();
        Set<String> qTokens = tokenize(query);
        if (qTokens.isEmpty() || s.entries.isEmpty()) return 0.0;
        Map<Integer, Double> scores = new HashMap<>();
        int totalDocs = s.entries.size();
        for (String qt : qTokens) {
            List<int[]> post = s.inverted.get(qt);
            if (post == null || post.isEmpty()) continue;
            double idf = Math.log(1.0 + totalDocs / (1.0 + s.docFreq.getOrDefault(qt, 0)));
            for (int[] hit : post) {
                scores.merge(hit[0], idf * 1.0 / (1.0 + hit[1]), Double::sum);
            }
        }
        return scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    /** Add a new entry to corpus. Persisted to disk + reindexed. */
    public synchronized Entry add(String question, String answer, String category, String source) {
        State s = stateRef.get();
        int id = s.size();
        Entry e = new Entry(id, question, answer,
                category == null ? "user" : category,
                source == null ? "learned" : source);
        s.entries.add(e);
        try {
            appendToDisk(e);
        } catch (IOException ex) {
            log.warn("disk append failed: {}", ex.getMessage());
        }
        Set<String> tokens = tokenize(e.question());
        s.docTokens.put(e.id(), tokens);
        for (String t : tokens) {
            s.inverted.computeIfAbsent(t, k -> new ArrayList<>()).add(new int[]{e.id(), tokens.size()});
            s.docFreq.merge(t, 1, Integer::sum);
        }
        return e;
    }

    private void appendToDisk(Entry e) throws IOException {
        Path p = Path.of(qaPath);
        Files.createDirectories(p.getParent());
        ObjectNode node = mapper.createObjectNode();
        node.put("question", e.question());
        node.put("answer", e.answer());
        node.put("category", e.category());
        node.put("source", e.source());
        String json = mapper.writeValueAsString(node);

        // Append as a newline-delimited entry before the closing "]"
        if (Files.isRegularFile(p) && Files.size(p) > 0) {
            String existing = Files.readString(p);
            // strip trailing whitespace, find the last "]"
            existing = existing.replaceAll("\\s+$", "");
            if (existing.endsWith("]")) {
                String head = existing.substring(0, existing.length() - 1).replaceAll("\\s+$", "");
                String separator = head.endsWith("}") || head.endsWith("]") ? "," : "";
                String out = head + separator + "\n  " + json + "\n]\n";
                Files.writeString(p, out);
                return;
            }
        }
        // Fresh file: create array
        Files.writeString(p, "[\n  " + json + "\n]\n");
    }

    /** Read-only snapshot. */
    public State state() { return stateRef.get(); }

    /** Size of the index. */
    public int size() { return stateRef.get().size(); }

    private static Set<String> tokenize(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) return out;
        String[] words = text.toLowerCase().split("[^а-яa-z0-9]+");
        for (String w : words) {
            if (w.length() < 2) continue;
            if (STOPWORDS.contains(w)) continue;
            out.add(w);
        }
        return out;
    }

    private static String textOrNull(JsonNode n, String field) {
        return n.has(field) && !n.get(field).isNull() ? n.get(field).asText() : null;
    }

    private static String textOrDefault(JsonNode n, String field, String fallback) {
        String s = textOrNull(n, field);
        return s == null ? fallback : s;
    }
}
