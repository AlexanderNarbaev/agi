package io.matrix.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A versioned assessment dataset (owner directive §26).
 *
 * <p>The dataset is a first-class artefact with a version, not a list of
 * questions buried in a test. That is what makes the learning measurements
 * interpretable: a claim of improvement has to name the dataset version it
 * was measured on.
 *
 * <p>Items marked {@code heldOut} form the evaluation set. They must not be
 * used to build the knowledge model or to tune the learning process — the
 * whole point of holding them out is that the post-learning measurement is
 * independent of the learning that produced it.
 */
public final class AssessmentDataset {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String id;
    private final int version;
    private final String description;
    private final List<AssessmentItem> items;
    private final List<String> skipped;

    /**
     * A dataset that holds the package's items and the generated ones.
     *
     * <p>RECON-W42.7. The generated items are part of the workspace's
     * assessment, so the dataset has to be able to carry them.
     *
     * @param base the dataset the items come from
     * @param items the items to add
     * @return the merged dataset
     */
    public static AssessmentDataset withAdditionalItems(AssessmentDataset base,
                                                        List<AssessmentItem> items) {
        List<AssessmentItem> all = new ArrayList<>(base.items());
        all.addAll(items);
        return new AssessmentDataset(base.id(), base.version(), base.description(), all,
                base.skipped());
    }

    private AssessmentDataset(String id, int version, String description, List<AssessmentItem> items) {
        this(id, version, description, items, List.of());
    }

    private AssessmentDataset(String id, int version, String description, List<AssessmentItem> items,
                              List<String> skipped) {
        this.id = id;
        this.version = version;
        this.description = description;
        this.items = List.copyOf(items);
        this.skipped = List.copyOf(skipped);
    }

    /**
     * The items the dataset holds that could NOT be anchored to a concept
     * of this workspace.
     *
     * <p>RECON-W43.4. The dataset is loaded for every workspace, and a
     * workspace that does not hold the concepts of the packaged dataset used
     * to make the load FAIL — so importing a document into an empty
     * workspace crashed with "the assessment dataset refers to an unknown
     * concept". An item that names a concept this workspace does not have
     * cannot be asked here, and the honest thing is to leave it out AND
     * say so, not to refuse the whole dataset over it.
     */
    public List<String> skipped() {
        return skipped;
    }

    /** The dataset identifier, for example "photosynthesis-v1". */
    public String id() {
        return id;
    }

    /** The dataset version. */
    public int version() {
        return version;
    }

    /** What the dataset covers. */
    public String description() {
        return description;
    }

    /** Every item. */
    public List<AssessmentItem> items() {
        return items;
    }

    /** The items that form the held-out evaluation set. */
    public List<AssessmentItem> heldOutItems() {
        return items.stream().filter(AssessmentItem::heldOut).toList();
    }

    /** The items that may be used during learning. */
    public List<AssessmentItem> trainingItems() {
        return items.stream().filter(item -> !item.heldOut()).toList();
    }

    /** Load a dataset from a file, resolving the concept names against a workspace. */
    public static AssessmentDataset load(Path file, KnowledgeSnapshot snapshot) {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8), snapshot);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the assessment dataset at " + file, e);
        }
    }

    /** Load a dataset from the classpath. */
    public static AssessmentDataset loadResource(String resourcePath, KnowledgeSnapshot snapshot) {
        try (InputStream in = AssessmentDataset.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("No assessment dataset on the classpath at " + resourcePath);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), snapshot);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the assessment dataset at " + resourcePath, e);
        }
    }

    private static AssessmentDataset parse(String json, KnowledgeSnapshot snapshot) {
        DatasetFile file;
        try {
            file = JSON.readValue(json, DatasetFile.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("The assessment dataset is not valid JSON: " + e.getMessage(), e);
        }
        List<AssessmentItem> items = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (ItemEntry entry : file.items) {
            String conceptId = resolveConcept(snapshot, entry.concept);
            if (conceptId == null) {
                skipped.add(entry.id + ": the concept '" + entry.concept + "' is not in this "
                        + "workspace");
                continue;
            }
            List<AssessmentItem.Alternative> alternatives = new ArrayList<>();
            for (AlternativeEntry alternative : entry.alternatives) {
                alternatives.add(new AssessmentItem.Alternative(
                        alternative.id, alternative.text, alternative.misconceptionId));
            }
            items.add(new AssessmentItem(entry.id, conceptId,
                    LearningDimension.valueOf(entry.dimension.trim().toUpperCase(Locale.ROOT)),
                    entry.prompt, entry.keyTerms, entry.difficulty,
                    entry.variant == null ? "standard" : entry.variant, entry.critical, entry.heldOut,
                    alternatives, entry.correctAlternativeId));
        }
        return new AssessmentDataset(file.dataset.id, file.dataset.version, file.dataset.description,
                items, skipped);
    }

    /**
     * The concept of a name in this workspace, or null when the workspace
     * does not hold it.
     *
     * <p>It returns null rather than throwing: the dataset is shared
     * between workspaces, and a workspace that does not hold a concept the
     * dataset names is a normal state, not an error.
     */
    private static String resolveConcept(KnowledgeSnapshot snapshot, String conceptName) {
        for (Concept concept : snapshot.concepts()) {
            if (concept.name().equalsIgnoreCase(conceptName)) {
                return concept.id();
            }
        }
        return null;
    }

    /** The JSON shape of a dataset file. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class DatasetFile {
        @JsonProperty("dataset")
        public DatasetMeta dataset;
        @JsonProperty("items")
        public List<ItemEntry> items = List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class DatasetMeta {
        @JsonProperty("id")
        public String id;
        @JsonProperty("version")
        public int version = 1;
        @JsonProperty("description")
        public String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    /** One answer a learner may choose. */
    static final class AlternativeEntry {
        @JsonProperty("id")
        public String id;
        @JsonProperty("text")
        public String text;
        @JsonProperty("misconceptionId")
        public String misconceptionId;
    }

    static final class ItemEntry {
        @JsonProperty("id")
        public String id;
        @JsonProperty("concept")
        public String concept;
        @JsonProperty("dimension")
        public String dimension;
        @JsonProperty("prompt")
        public String prompt;
        @JsonProperty("keyTerms")
        public List<String> keyTerms = List.of();
        @JsonProperty("difficulty")
        public int difficulty = 1;
        @JsonProperty("variant")
        public String variant;
        @JsonProperty("critical")
        public boolean critical;
        @JsonProperty("heldOut")
        public boolean heldOut;
        /** The answers a learner may choose, with the key and the misconception bindings. */
        @JsonProperty("alternatives")
        public List<AlternativeEntry> alternatives = List.of();
        /** The identifier of the correct alternative. */
        @JsonProperty("correctAlternativeId")
        public String correctAlternativeId;
    }
}
