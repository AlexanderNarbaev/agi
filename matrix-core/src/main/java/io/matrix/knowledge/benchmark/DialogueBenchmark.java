package io.matrix.knowledge.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.KnowledgeWorkbench;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The benchmark of the dialogue (RECON-W43.3).
 *
 * <p>The slice benchmark measures what the system concludes. This one
 * measures what it does when it must NOT conclude: when the question's
 * referent is unsettled and the honest move is to ask, when the workspace
 * holds nothing that answers the question, and when the question carries a
 * presupposition that is false.
 *
 * <p><b>What it measures.</b>
 *
 * <ul>
 *   <li>the clarification RECALL: of the questions whose referent is
 *       unsettled, how many got a question back instead of a guess;</li>
 *   <li>the clarification PRECISION: of the questions that could be
 *       answered, how many were answered rather than refused with a
 *       question;</li>
 *   <li>the abstention CORRECTNESS: of the questions the workspace cannot
 *       cover, how many were refused;</li>
 *   <li>the false-premise REJECTION: of the questions that carry a false
 *       presupposition, how many were refused instead of answered.</li>
 * </ul>
 *
 * <p><b>What it does not measure.</b> It does not measure the accuracy of
 * the answers it accepts — that is the W42 instrument over its own cases,
 * and the two are not comparable. It measures the refusals.
 *
 * <p><b>The isolation property.</b> Every case runs in its own store, built
 * from the bundled package alone, so the cases cannot see each other.
 */
public final class DialogueBenchmark {

    /** The dataset this runner reads. */
    public static final String DATASET = "benchmark/w43-dialogue-benchmark-v1.json";

    /** The workspace every case runs against. */
    public static final String WORKSPACE = "photosynthesis";

    /**
     * The marker of a clarification in an abstention.
     *
     * <p>The system asks when the conversation has not settled what the
     * question is about, and it says so in these words. The refusal for a
     * question the workspace cannot cover is a DIFFERENT message, and the
     * two are told apart by this marker rather than by the language of the
     * question — the internal messages are in English even for a Russian
     * question.
     */
    public static final String CLARIFICATION_MARKER = "has not settled";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** What the case expects of the dialogue. */
    public record Expect(boolean answered, boolean clarification) {
    }

    /** One case. */
    public record Item(String id, String language, String split, String category,
                       String question, Expect expect) {
    }

    /** What the dialogue actually did. */
    public record Result(String id, String language, String split, String category,
                         boolean answeredCorrect, boolean clarificationCorrect,
                         boolean expectedAnswered, boolean actualAnswered,
                         boolean expectedClarification, boolean actualClarification,
                         String reason) {
    }

    /** The measurement. */
    public record Report(List<Result> results, Map<String, Map<String, Integer>> byLanguage,
                          Map<String, Map<String, Integer>> bySplit,
                          Map<String, Map<String, Integer>> byCategory) {

        /** The number of cases. */
        public int total() {
            return results.size();
        }

        /** The number of cases whose answer decision was right. */
        public int answeredCorrect() {
            return (int) results.stream().filter(Result::answeredCorrect).count();
        }

        /** The number of cases whose clarification decision was right. */
        public int clarificationCorrect() {
            return (int) results.stream().filter(Result::clarificationCorrect).count();
        }

        /** The number of cases where a clarification was required. */
        public int clarificationRequired() {
            return (int) results.stream().filter(Result::expectedClarification).count();
        }

        /** The number of cases where a clarification was required AND was asked. */
        public int clarificationAskedWhenRequired() {
            return (int) results.stream()
                    .filter(r -> r.expectedClarification() && r.actualClarification()).count();
        }

        /** The number of cases where no clarification was required. */
        public int clarificationNotRequired() {
            return (int) results.stream().filter(r -> !r.expectedClarification()).count();
        }

        /** The number of cases where no clarification was required AND none was asked. */
        public int clarificationWithheldWhenNotRequired() {
            return (int) results.stream()
                    .filter(r -> !r.expectedClarification() && !r.actualClarification()).count();
        }

        /** The number of cases where a refusal was the right answer. */
        public int refusalRequired() {
            return (int) results.stream().filter(r -> !r.expectedAnswered()).count();
        }

        /** The number of cases where the refusal was given. */
        public int refusalGiven() {
            return (int) results.stream().filter(r -> !r.expectedAnswered() && !r.actualAnswered())
                    .count();
        }

        /** The number of false-premise cases. */
        public int falsePremiseCases() {
            return (int) results.stream()
                    .filter(r -> "false_premise".equals(r.category())).count();
        }

        /** The number of false-premise cases that were refused. */
        public int falsePremiseRefused() {
            return (int) results.stream()
                    .filter(r -> "false_premise".equals(r.category()) && !r.actualAnswered()).count();
        }
    }

    private DialogueBenchmark() {
    }

    /** The dataset, read from the classpath. */
    public static List<Item> items() throws IOException {
        try (InputStream in = DialogueBenchmark.class.getClassLoader().getResourceAsStream(DATASET)) {
            if (in == null) {
                throw new IOException("the dataset is missing: " + DATASET);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** The dataset, parsed from JSON text. */
    public static List<Item> parse(String json) throws IOException {
        JsonNode root = MAPPER.readTree(json);
        List<Item> items = new ArrayList<>();
        for (JsonNode node : root.path("items")) {
            JsonNode expect = node.path("expect");
            items.add(new Item(
                    node.path("id").asText(),
                    node.path("language").asText(),
                    node.path("split").asText(),
                    node.path("category").asText(),
                    node.path("question").asText(),
                    new Expect(
                            expect.path("answered").asBoolean(false),
                            expect.path("clarification").asBoolean(false))));
        }
        return items;
    }

    /**
     * Runs the benchmark.
     *
     * @param storeDirectory where the per-case stores are built
     * @return the measurement
     * @throws IOException when the dataset cannot be read
     */
    public static Report run(Path storeDirectory) throws IOException {
        List<Result> results = new ArrayList<>();
        List<Item> items = items();
        for (Item item : items) {
            Path store = storeDirectory.resolve(item.id());
            try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(store.toString())) {
                workbench.loadBundledPackage(WORKSPACE);
                Answer answer = workbench.ask(WORKSPACE, item.question(),
                        AbstractionLevel.INTRODUCTORY);
                String reason = answer.abstentionReason();
                boolean clarification = reason != null && reason.contains(CLARIFICATION_MARKER);
                results.add(new Result(
                        item.id(),
                        item.language(),
                        item.split(),
                        item.category(),
                        answer.answered() == item.expect().answered(),
                        clarification == item.expect().clarification(),
                        item.expect().answered(),
                        answer.answered(),
                        item.expect().clarification(),
                        clarification,
                        reason));
            }
        }
        return new Report(results, byLanguage(results), bySplit(results), byCategory(results));
    }

    private static Map<String, Map<String, Integer>> byLanguage(List<Result> results) {
        Map<String, Map<String, Integer>> out = new TreeMap<>();
        for (Result r : results) {
            out.computeIfAbsent(r.language(), key -> new LinkedHashMap<>());
            Map<String, Integer> counts = out.get(r.language());
            counts.merge("cases", 1, Integer::sum);
            counts.merge("answeredCorrect", r.answeredCorrect() ? 1 : 0, Integer::sum);
            counts.merge("clarificationCorrect", r.clarificationCorrect() ? 1 : 0, Integer::sum);
        }
        return out;
    }

    private static Map<String, Map<String, Integer>> bySplit(List<Result> results) {
        Map<String, Map<String, Integer>> out = new TreeMap<>();
        for (Result r : results) {
            out.computeIfAbsent(r.split(), key -> new LinkedHashMap<>());
            Map<String, Integer> counts = out.get(r.split());
            counts.merge("cases", 1, Integer::sum);
            counts.merge("answeredCorrect", r.answeredCorrect() ? 1 : 0, Integer::sum);
            counts.merge("clarificationCorrect", r.clarificationCorrect() ? 1 : 0, Integer::sum);
        }
        return out;
    }

    private static Map<String, Map<String, Integer>> byCategory(List<Result> results) {
        Map<String, Map<String, Integer>> out = new TreeMap<>();
        for (Result r : results) {
            out.computeIfAbsent(r.category(), key -> new LinkedHashMap<>());
            Map<String, Integer> counts = out.get(r.category());
            counts.merge("cases", 1, Integer::sum);
            counts.merge("answeredCorrect", r.answeredCorrect() ? 1 : 0, Integer::sum);
            counts.merge("clarificationCorrect", r.clarificationCorrect() ? 1 : 0, Integer::sum);
        }
        return out;
    }
}
