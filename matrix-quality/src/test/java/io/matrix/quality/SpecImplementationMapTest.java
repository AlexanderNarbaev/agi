package io.matrix.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W39 — the specification-to-implementation registry guard.
 *
 * <p>{@code formal/spec-implementation-map.yml} is the authoritative map from
 * every TLA+ specification to the Java that implements it. This test is what
 * makes the map trustworthy:
 *
 * <ol>
 *   <li>every referenced path must exist;</li>
 *   <li>every specification must identify tests;</li>
 *   <li>every conformance status must come from the closed vocabulary;</li>
 *   <li>a specification may only claim {@code PRODUCTION_PATH_VERIFIED} when
 *       it actually names a production caller — this is the check that stops
 *       the registry from over-claiming;</li>
 *   <li>the CI path filter in {@code .github/workflows/tla.yml} must contain
 *       every path the map declares, so a change to an implementation triggers
 *       the formal gate.</li>
 * </ol>
 *
 * <p>The registry is the single source of truth for the path filter: the
 * expected block is computed from the map and compared with the workflow. Run
 * {@code ./gradlew :matrix-quality:regenerateFormalPaths} to update the
 * workflow after changing the map.
 */
class SpecImplementationMapTest {

    /** Marker pair delimiting the generated block inside the workflow. */
    private static final String BEGIN = "      # BEGIN generated-formal-paths (from formal/spec-implementation-map.yml)";
    private static final String END = "      # END generated-formal-paths";

    private static final String MAP = "formal/spec-implementation-map.yml";
    private static final String WORKFLOW = ".github/workflows/tla.yml";

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("formal/spec-implementation-map.yml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repository root not found from " + Paths.get("").toAbsolutePath());
    }

    private static JsonNode map() throws IOException {
        return YAML.readTree(repoRoot().resolve(MAP).toFile());
    }

    // ── structure ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the registry parses and every referenced path exists")
    void everyReferencedPathExists() throws IOException {
        JsonNode root = map();
        List<String> missing = new ArrayList<>();

        for (JsonNode spec : root.get("specifications")) {
            String id = spec.get("id").asText();
            collectMissingScalar(spec.get("spec"), missing, "spec(" + id + ").spec");
            collectMissingScalar(spec.get("config"), missing, "spec(" + id + ").config");
            JsonNode java = spec.get("java");
            if (java != null) {
                collectMissing(java.get("sources"), missing, "spec(" + id + ").java.sources");
                collectMissing(java.get("production_callers"), missing,
                        "spec(" + id + ").java.production_callers");
            }
            JsonNode tests = spec.get("tests");
            if (tests != null) {
                collectMissing(tests.get("unit"), missing, "spec(" + id + ").tests.unit");
                collectMissing(tests.get("integration"), missing, "spec(" + id + ").tests.integration");
            }
        }
        collectMissing(root.get("ci_paths"), missing, "ci_paths");

        assertThat(missing).as("paths referenced by the registry that do not exist").isEmpty();
    }

    private static void collectMissing(JsonNode array, List<String> missing, String where) {
        if (array == null) {
            return;
        }
        for (JsonNode node : array) {
            String path = node.asText();
            if (!Files.exists(repoRoot().resolve(path))) {
                missing.add(where + " -> " + path);
            }
        }
    }

    private static void collectMissingScalar(JsonNode node, List<String> missing, String where) {
        if (node == null || node.isNull()) {
            missing.add(where + " -> (missing)");
            return;
        }
        String path = node.asText();
        if (!Files.exists(repoRoot().resolve(path))) {
            missing.add(where + " -> " + path);
        }
    }

    @Test
    @DisplayName("every specification identifies at least one test")
    void everySpecificationHasTests() throws IOException {
        JsonNode root = map();
        List<String> withoutTests = new ArrayList<>();
        for (JsonNode spec : root.get("specifications")) {
            JsonNode unit = spec.path("tests").path("unit");
            JsonNode integration = spec.path("tests").path("integration");
            if (!unit.isArray() || unit.isEmpty()) {
                if (!integration.isArray() || integration.isEmpty()) {
                    withoutTests.add(spec.get("id").asText());
                }
            }
        }
        assertThat(withoutTests).as("specifications with no identified test").isEmpty();
    }

    @Test
    @DisplayName("every conformance status comes from the closed vocabulary")
    void statusesComeFromTheVocabulary() throws IOException {
        JsonNode root = map();
        Set<String> vocabulary = new LinkedHashSet<>();
        for (JsonNode status : root.path("vocabulary").path("status")) {
            vocabulary.add(status.asText());
        }
        assertThat(vocabulary).as("the vocabulary must not be empty").isNotEmpty();

        List<String> unknown = new ArrayList<>();
        for (JsonNode spec : root.get("specifications")) {
            for (JsonNode status : spec.path("conformance").path("status")) {
                if (!vocabulary.contains(status.asText())) {
                    unknown.add(spec.get("id").asText() + " -> " + status.asText());
                }
            }
        }
        assertThat(unknown).as("statuses outside the vocabulary").isEmpty();
    }

    @Test
    @DisplayName("PRODUCTION_PATH_VERIFIED requires a named production caller")
    void productionVerifiedRequiresAProductionCaller() throws IOException {
        JsonNode root = map();
        List<String> overClaims = new ArrayList<>();
        for (JsonNode spec : root.get("specifications")) {
            boolean claims = false;
            for (JsonNode status : spec.path("conformance").path("status")) {
                if ("PRODUCTION_PATH_VERIFIED".equals(status.asText())) {
                    claims = true;
                }
            }
            JsonNode callers = spec.path("java").path("production_callers");
            if (claims && (!callers.isArray() || callers.isEmpty())) {
                overClaims.add(spec.get("id").asText());
            }
        }
        assertThat(overClaims)
                .as("specifications claiming PRODUCTION_PATH_VERIFIED without a production caller")
                .isEmpty();
    }

    @Test
    @DisplayName("a specification with no production caller says so explicitly")
    void noProductionCallerIsRecordedHonestly() throws IOException {
        JsonNode root = map();
        List<String> inconsistent = new ArrayList<>();
        for (JsonNode spec : root.get("specifications")) {
            JsonNode callers = spec.path("java").path("production_callers");
            String status = spec.path("java").path("production_caller_status").asText("");
            boolean empty = !callers.isArray() || callers.isEmpty();
            if (empty && !"NO_PRODUCTION_CALLER".equals(status)) {
                inconsistent.add(spec.get("id").asText() + " -> " + status);
            }
            if (!empty && !"PRODUCTION_PATH_VERIFIED".equals(status)) {
                inconsistent.add(spec.get("id").asText() + " -> " + status);
            }
        }
        assertThat(inconsistent)
                .as("production_caller_status inconsistent with the declared production callers")
                .isEmpty();
    }

    @Test
    @DisplayName("every specification in formal/ appears in the registry")
    void everySpecificationIsRegistered() throws IOException {
        Set<String> registered = new LinkedHashSet<>();
        for (JsonNode spec : map().get("specifications")) {
            registered.add(spec.get("id").asText());
        }
        Set<String> onDisk = new TreeSet<>();
        try (Stream<Path> files = Files.list(repoRoot().resolve("formal"))) {
            files.filter(p -> p.getFileName().toString().endsWith(".tla"))
                    .forEach(p -> onDisk.add(p.getFileName().toString().replace(".tla", "")));
        }
        Set<String> missing = new TreeSet<>(onDisk);
        missing.removeAll(registered);
        assertThat(missing).as("specifications present in formal/ but absent from the registry").isEmpty();
    }

    // ── the CI path filter ────────────────────────────────────────────────────

    /**
     * The paths the formal gate must watch: everything the registry maps, plus
     * the registry and its validator. Derived from the map, so the map stays
     * the single source of truth.
     */
    private static Set<String> expectedPaths() throws IOException {
        JsonNode root = map();
        Set<String> paths = new TreeSet<>();
        for (JsonNode node : root.path("ci_paths")) {
            paths.add(node.asText());
        }
        for (JsonNode spec : root.get("specifications")) {
            JsonNode java = spec.path("java");
            if (java != null) {
                for (JsonNode node : java.path("sources")) {
                    paths.add(node.asText());
                }
                for (JsonNode node : java.path("production_callers")) {
                    paths.add(node.asText());
                }
            }
            JsonNode tests = spec.path("tests");
            if (tests != null) {
                for (JsonNode node : tests.path("unit")) {
                    paths.add(node.asText());
                }
                for (JsonNode node : tests.path("integration")) {
                    paths.add(node.asText());
                }
            }
        }
        return paths;
    }

    @Test
    @DisplayName("the CI path filter contains every path the registry maps")
    void ciPathFilterIsComplete() throws IOException {
        String workflow = Files.readString(repoRoot().resolve(WORKFLOW), StandardCharsets.UTF_8);
        List<String> missing = new ArrayList<>();
        for (String path : expectedPaths()) {
            if (!workflow.contains("'" + path + "'")) {
                missing.add(path);
            }
        }
        assertThat(missing)
                .as("paths the registry maps that the TLA+ workflow does not trigger on")
                .isEmpty();
    }

    /** Every generated block in the workflow, as a set of declared paths. */
    private static List<Set<String>> generatedBlocks(String workflow) {
        List<Set<String>> blocks = new ArrayList<>();
        int cursor = 0;
        while (true) {
            int begin = workflow.indexOf(BEGIN, cursor);
            if (begin < 0) {
                return blocks;
            }
            int end = workflow.indexOf(END, begin);
            if (end < 0) {
                throw new IllegalStateException("a generated block has no END marker");
            }
            Set<String> declared = new TreeSet<>();
            for (String line : workflow.substring(begin, end).split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("- '")) {
                    declared.add(trimmed.substring(3, trimmed.length() - 1));
                }
            }
            blocks.add(declared);
            cursor = end + END.length();
        }
    }

    @Test
    @DisplayName("every generated path block in the workflow matches the registry")
    void ciPathFilterMatchesTheRegistry() throws IOException {
        String workflow = Files.readString(repoRoot().resolve(WORKFLOW), StandardCharsets.UTF_8);
        List<Set<String>> blocks = generatedBlocks(workflow);
        assertThat(blocks)
                .as("the workflow must carry the generated path block for both push and pull_request")
                .hasSize(2);
        Set<String> expected = expectedPaths();
        for (Set<String> declared : blocks) {
            assertThat(declared)
                    .as("the workflow's generated path block is out of date — run "
                            + "./gradlew :matrix-quality:regenerateFormalPaths")
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("regenerating the path blocks is idempotent")
    void regenerationIsIdempotent() throws IOException {
        if (!Boolean.getBoolean("matrix.formalPaths.write")) {
            return;
        }
        Path workflow = repoRoot().resolve(WORKFLOW);
        String text = Files.readString(workflow, StandardCharsets.UTF_8);
        StringBuilder replacement = new StringBuilder(BEGIN).append('\n');
        for (String path : expectedPaths()) {
            replacement.append("      - '").append(path).append("'\n");
        }
        replacement.append(END);

        StringBuilder updated = new StringBuilder();
        int cursor = 0;
        while (true) {
            int begin = text.indexOf(BEGIN, cursor);
            if (begin < 0) {
                updated.append(text, cursor, text.length());
                break;
            }
            int end = text.indexOf(END, begin);
            updated.append(text, cursor, begin).append(replacement);
            cursor = end + END.length();
        }
        String result = updated.toString();
        if (!result.equals(text)) {
            Files.writeString(workflow, result, StandardCharsets.UTF_8);
        }
    }
}
