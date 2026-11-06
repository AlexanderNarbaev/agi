package io.matrix.knowledge.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The W42 benchmark, version 3: the items, their splits, their provenance
 * and the logical delay structure.
 *
 * <p><b>Why the suite exists beside {@link BenchmarkItem}.</b> The item
 * record of version 1 carries the fields the RUNNER needs, and the runner
 * is the frozen instrument: changing how an item is loaded, scored or
 * counted would move the baseline of decision D-001. The suite adds what
 * version 3 needs and version 1 does not have — the <b>split</b> an item
 * belongs to, the note that says what it measures, the <b>lesson</b> item a
 * delayed recall tests, and the <b>provenance</b> of the dataset — and it
 * hands the runner exactly the same {@link BenchmarkItem} list the version
 * 1 file produces.
 *
 * <p><b>The splits are disjoint by construction.</b> An item belongs to
 * exactly one split, and the splits are the three the mission's §14.1
 * names: the train split is the material a change may be developed against,
 * the validation split is the material a threshold may be chosen on, and
 * the test split is the material the reported number is measured from.
 *
 * <p><b>The delay of a delayed-recall item is LOGICAL.</b> The lesson item
 * is asked first and the recall item is asked later in the same run, over
 * an item set that is disjoint from the lesson's — the dialogue the lesson
 * happened in has rolled out of the context by then. There is no wait on
 * the wall clock anywhere in this file, and the mission's §16 forbids a
 * measurement that depends on one.
 */
public final class BenchmarkSuite {

    /** The split a change may be developed against. */
    public static final String SPLIT_TRAIN = "train";

    /** The split a threshold may be chosen on. */
    public static final String SPLIT_VALIDATION = "validation";

    /** The split the reported number is measured from. */
    public static final String SPLIT_TEST = "test";

    /** The three splits, in the order they are run. */
    public static final List<String> SPLITS = List.of(SPLIT_TRAIN, SPLIT_VALIDATION, SPLIT_TEST);

    /** One item of the suite: the item, its split and what it measures. */
    public record Entry(
            BenchmarkItem item,
            String split,
            String notes,
            String lessonItemId) {
    }

    /** Where the dataset came from and how it was made. */
    public record Provenance(
            String id,
            int version,
            long seed,
            String generator,
            Map<String, Object> parameters) {

        public Provenance {
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    private final String id;
    private final int version;
    private final List<String> categories;
    private final List<Entry> entries;
    private final Provenance provenance;

    private BenchmarkSuite(String id, int version, List<String> categories,
                           List<Entry> entries, Provenance provenance) {
        this.id = id;
        this.version = version;
        this.categories = List.copyOf(categories);
        this.entries = List.copyOf(entries);
        this.provenance = provenance;
    }

    /** The whole suite, loaded from a classpath resource. */
    public static BenchmarkSuite load(String resourcePath) {
        try (InputStream in = BenchmarkSuite.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("no benchmark at " + resourcePath);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the benchmark at " + resourcePath, e);
        }
    }

    /** The whole suite, parsed from JSON text. */
    public static BenchmarkSuite parse(String json) {
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            JsonNode header = root.path("benchmark");
            List<Entry> entries = new ArrayList<>();
            for (JsonNode node : root.path("items")) {
                String split = node.path("split").asText("").strip();
                if (split.isEmpty()) {
                    throw new IllegalArgumentException(
                            "the item " + node.path("id").asText() + " declares no split");
                }
                if (!SPLITS.contains(split)) {
                    throw new IllegalArgumentException("the item "
                            + node.path("id").asText() + " declares the unknown split " + split);
                }
                entries.add(new Entry(
                        new BenchmarkItem(
                                node.path("id").asText(),
                                node.path("category").asText(),
                                node.path("language").asText(),
                                node.path("workspace").asText(),
                                node.path("question").asText(),
                                node.path("answerable").asBoolean(false),
                                textList(node.path("keyTerms")),
                                textList(node.path("mustMention")),
                                textList(node.path("mustNotMention"))),
                        split,
                        node.path("notes").asText(""),
                        node.path("lessonItemId").asText("")));
            }
            JsonNode provenanceNode = header.path("provenance");
            Map<String, Object> parameters = new LinkedHashMap<>();
            JsonNode parametersNode = provenanceNode.path("generatorParameters");
            parametersNode.fields().forEachRemaining(field ->
                    parameters.put(field.getKey(), field.getValue().asText()));
            Provenance provenance = new Provenance(
                    header.path("id").asText("w42-benchmark-v3"),
                    header.path("version").asInt(0),
                    provenanceNode.path("seed").asLong(0L),
                    provenanceNode.path("generator").asText(""),
                    parameters);
            List<String> categories = new ArrayList<>();
            header.path("categories").forEach(node -> categories.add(node.asText()));
            return new BenchmarkSuite(
                    header.path("id").asText("w42-benchmark-v3"),
                    header.path("version").asInt(0),
                    categories,
                    entries,
                    provenance);
        } catch (IOException e) {
            throw new IllegalArgumentException("the benchmark is not valid JSON: " + e.getMessage(), e);
        }
    }

    private static List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(child -> values.add(child.asText()));
        return values;
    }

    /** The identifier of the dataset. */
    public String id() {
        return id;
    }

    /** The version of the dataset. */
    public int version() {
        return version;
    }

    /** The categories the dataset covers. */
    public List<String> categories() {
        return categories;
    }

    /** Where the dataset came from. */
    public Provenance provenance() {
        return provenance;
    }

    /** The number of items. */
    public int size() {
        return entries.size();
    }

    /** The entries, in the order the file declares them. */
    public List<Entry> entries() {
        return entries;
    }

    /** The items, in the order the file declares them. */
    public List<BenchmarkItem> items() {
        List<BenchmarkItem> items = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            items.add(entry.item());
        }
        return items;
    }

    /** The items of one split, in the order the file declares them. */
    public List<BenchmarkItem> itemsInSplit(String split) {
        List<BenchmarkItem> items = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.split().equals(split)) {
                items.add(entry.item());
            }
        }
        return items;
    }

    /**
     * The order the items are asked in.
     *
     * <p>The train split is asked FIRST, because the delayed-recall items
     * test a lesson that was taught there, and the recall itself is asked
     * with the test split — after every other item of the run. The delay is
     * therefore logical and the order is reproducible.
     *
     * @return the items, split by split and sorted by their identifier
     */
    public List<BenchmarkItem> runOrder() {
        List<BenchmarkItem> ordered = new ArrayList<>(entries.size());
        for (String split : SPLITS) {
            List<BenchmarkItem> ofSplit = itemsInSplit(split);
            ofSplit.sort(Comparator.comparing(BenchmarkItem::id));
            ordered.addAll(ofSplit);
        }
        return ordered;
    }

    /** The split an item belongs to, or null when the item is not in the suite. */
    public String splitOf(String itemId) {
        for (Entry entry : entries) {
            if (entry.item().id().equals(itemId)) {
                return entry.split();
            }
        }
        return null;
    }

    /** The lesson item a delayed recall tests, or null when it tests none. */
    public String lessonItemIdOf(String itemId) {
        for (Entry entry : entries) {
            if (entry.item().id().equals(itemId)) {
                return entry.lessonItemId();
            }
        }
        return null;
    }

    /** What an item measures. */
    public String notesOf(String itemId) {
        for (Entry entry : entries) {
            if (entry.item().id().equals(itemId)) {
                return entry.notes();
            }
        }
        return null;
    }

    /** The identifiers of the items that are lessons of a delayed recall. */
    public Set<String> lessonItemIds() {
        Set<String> lessons = new LinkedHashSet<>();
        for (Entry entry : entries) {
            if (!entry.lessonItemId().isBlank()) {
                lessons.add(entry.lessonItemId());
            }
        }
        return lessons;
    }

    /** The identifiers of the items that are delayed recalls. */
    public Set<String> recallItemIds() {
        Set<String> recalls = new LinkedHashSet<>();
        for (Entry entry : entries) {
            if (!entry.lessonItemId().isBlank()) {
                recalls.add(entry.item().id());
            }
        }
        return recalls;
    }

    /** How many items each category holds. */
    public Map<String, Integer> countsByCategory() {
        return countsBy(BenchmarkItem::category);
    }

    /** How many items each language holds. */
    public Map<String, Integer> countsByLanguage() {
        return countsBy(BenchmarkItem::language);
    }

    /** How many items each split holds. */
    public Map<String, Integer> countsBySplit() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String split : SPLITS) {
            counts.put(split, 0);
        }
        for (Entry entry : entries) {
            counts.merge(entry.split(), 1, Integer::sum);
        }
        return counts;
    }

    private Map<String, Integer> countsBy(java.util.function.Function<BenchmarkItem, String> key) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Entry entry : entries) {
            counts.merge(key.apply(entry.item()), 1, Integer::sum);
        }
        return counts;
    }
}
