package io.matrix.crj.corpus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 / H2 — the DEV SPLIT's validator: every rule of the protocol's §5.4, checked on the
 * H2 split.
 *
 * <p><b>Why it exists.</b> The H2 milestone needs a case set whose gold is the corpus's own
 * text and whose questions are independent of every set that came before. A set nobody checks
 * is a list of questions, and the repository's rule is that "the evidence of a claim is the
 * artefact" — here the artefact of "the split is independent" is this test, not the
 * author's assurance.</p>
 *
 * <p><b>What it checks.</b> The generator rules of the protocol's §5.4 that a DEVELOPMENT split
 * can satisfy — rules **1 to 7**; rules 8 and 9 govern the SEALED split and are not enforceable
 * here — plus the structure
 * this milestone's own measurement depends on: the learning episode's OBSERVED cases carry the
 * procedure's support set, the VALIDATION cases are held out from it, the NOVEL cases are in
 * the support and were never validated on, and the OUT_OF_SUPPORT cases carry the control
 * whose evidence lies outside it.</p>
 *
 * <p><b>What it does NOT do.</b> It judges no arm and measures nothing. It checks the
 * DATASET.</p>
 */
class Crj01H2DevSplitValidatorTest {

    /** The H2 split's artefact. */
    private static final String DATASET = "docs-v2/engineering/CRJ-01-H2-DEV-SPLIT.json";

    /** The H2 corpus report, whose digests the split's gold is pinned to. */
    private static final String CORPUS_REPORT = "docs-v2/engineering/CRJ-01-H2-CORPUS-REPORT.json";

    /** The corpus files, and the classpath directory they live in. */
    private static final List<String> CORPUS_FILES = List.of(
            "h2-magnetic-anomalies.md",
            "h2-spreading-rate.md",
            "h2-deep-trenches.md",
            "h2-hydrothermal-vents.md");

    /** The corpus resource directory. */
    private static final String CORPUS_RESOURCE = "/crj01-h2/";

    /**
     * The DEVELOPMENT set: the 29-case evaluation the development code is meant to use.
     *
     * <p>It is named outright, because a development set is a development artefact.</p>
     */
    private static final String DEVELOPMENT_SET =
            "matrix-core/src/main/resources/epistemic-eval/cases.json";

    /** The existing CRJ-01 DEV split — the rule of §5.4 names it as a neighbour. */
    private static final String CRJ01_DEV_SPLIT = "docs-v2/engineering/CRJ-01-DEV-SPLIT.json";

    /** The directory the HELD-OUT sets live in. */
    private static final String HELD_OUT_DIRECTORY = "docs-v2/engineering";

    /** The classes the split must carry. */
    private static final Set<String> REQUIRED_CLASSES = Set.of("POSITIVE", "PARAPHRASE",
            "UNSUPPORTED_PREMISE", "REFUTED_PREMISE");

    /** The normalised token sequence length a question may not share. */
    private static final int MINIMUM_SHARED_TOKENS = 4;

    /** The subsets the split must carry, and how many cases each holds. */
    private static final Map<String, Integer> REQUIRED_SUBSETS = Map.of(
            "OBSERVED", 2,
            "VALIDATION", 5,
            "OUT_OF_SUPPORT", 2);

    /** The subset that carries the transfer cases; its size is a range, not a number. */
    private static final int MINIMUM_NOVEL = 4;

    @Test
    @DisplayName("the H2 dev split holds every rule the protocol names, and the structure the "
            + "transfer measurement depends on")
    void theH2DevSplitHoldsEveryRule(@TempDir Path temporary) throws Exception {
        Path dataset = RepositoryPaths.root().resolve(DATASET);
        assertThat(dataset).as("the H2 dev split %s is missing", DATASET).exists();
        ObjectMapper json = JsonMapper.builder().build();
        Map<?, ?> parsed = json.readValue(Files.readString(dataset, StandardCharsets.UTF_8),
                Map.class);
        List<Map<String, Object>> cases = new ArrayList<>();
        for (Object entry : (List<?>) parsed.get("cases")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) entry;
            cases.add(map);
        }
        assertThat(cases).as("the H2 dev split carries cases").isNotEmpty();
        assertThat(parsed.get("sealed"))
                .as("the split says plainly that it is not a sealed set")
                .isEqualTo(false);

        // ── the corpus, as the bytes on disk ────────────────────────────────────
        Path corpus = Files.createDirectories(temporary.resolve("corpus"));
        for (String file : CORPUS_FILES) {
            try (var stream = Crj01H2DevSplitValidatorTest.class
                    .getResourceAsStream(CORPUS_RESOURCE + file)) {
                assertThat(stream).as("the corpus file %s is on the classpath", file).isNotNull();
                Files.write(corpus.resolve(file), stream.readAllBytes());
            }
        }

        // ── the workspace, assembled through the PRODUCTION acquisition path ─────
        Path database = Files.createDirectories(temporary.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        try {
            for (String file : CORPUS_FILES) {
                bench.importDocument("h2-split", corpus.resolve(file), file, "en");
            }
            Map<String, String> productionIdsByProposition = new LinkedHashMap<>();
            Map<String, String> sourcesByProposition = new LinkedHashMap<>();
            for (Claim claim : bench.workspace("h2-split").claims()) {
                productionIdsByProposition.put(claim.proposition(), claim.id());
                sourcesByProposition.put(claim.proposition(), claim.introducedBySourceId());
            }

            // ── the rules, one by one ────────────────────────────────────────────
            Set<String> ids = new LinkedHashSet<>();
            Set<String> questions = new LinkedHashSet<>();
            Set<String> keys = new LinkedHashSet<>();
            Set<String> classes = new LinkedHashSet<>();
            Set<String> goldPropositions = new LinkedHashSet<>();
            Set<String> goldClaimIds = new LinkedHashSet<>();
            Set<String> regressionGold = regressionGold();
            List<String> existingTexts = existingTexts();
            Map<String, Integer> subsets = new LinkedHashMap<>();
            Map<String, String> observedSourceByFile = new LinkedHashMap<>();

            for (Map<String, Object> entry : cases) {
                String id = String.valueOf(entry.get("id"));
                String question = String.valueOf(entry.get("question"));
                String caseClass = String.valueOf(entry.get("class"));
                String subset = String.valueOf(entry.get("subset"));
                String key = String.valueOf(entry.get("idempotencyKey"));
                classes.add(caseClass);
                subsets.merge(subset, 1, Integer::sum);
                assertThat(ids.add(id)).as("the case id %s is unique", id).isTrue();
                assertThat(questions.add(question))
                        .as("the question of %s is unique", id).isTrue();
                assertThat(keys.add(key))
                        .as("the idempotency key of %s is fresh", id).isTrue();

                // rule 4: the question shares no four-token sequence with any question,
                // assertion or disputed premise of the regression sets, nor with any
                // question of the existing CRJ-01 dev split
                Set<String> questionGrams = ngrams(question);
                for (String existing : existingTexts) {
                    Set<String> shared = new LinkedHashSet<>(questionGrams);
                    shared.retainAll(ngrams(existing));
                    assertThat(shared)
                            .as("the question of %s shares a %d-word sequence with an existing "
                                    + "question or assertion: %s", id, MINIMUM_SHARED_TOKENS,
                                    shared)
                            .isEmpty();
                }

                // rules 1, 2 and 3: the gold is verbatim in its document, it IS a claim the
                // production importer derived, and the case carries a source span
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> golds =
                        (List<Map<String, Object>>) entry.get("goldClaims");
                assertThat(golds).as("the case %s carries a gold claim", id).isNotEmpty();
                for (Map<String, Object> gold : golds) {
                    String proposition = String.valueOf(gold.get("proposition"));
                    String file = String.valueOf(gold.get("corpusFile"));
                    String digest = String.valueOf(gold.get("corpusSha256"));
                    assertThat(CORPUS_FILES).as("the gold of %s is in the H2 corpus", id)
                            .contains(file);
                    assertThat(productionIdsByProposition)
                            .as("the workspace holds the gold of %s as a PRODUCTION claim", id)
                            .containsKey(proposition);
                    // the production claim id embeds the WORKSPACE id, so the id of the
                    // workspace under test is not the id the split recorded. The split's id is
                    // the one the CORPUS REPORT froze, and the claim the workspace under test
                    // derives from the same sentence is a production claim of that sentence.
                    assertThat(productionIdsByProposition.get(proposition))
                            .as("the workspace under test derives the gold of %s as a PRODUCTION "
                                    + "claim", id)
                            .isNotBlank();
                    assertThat(String.valueOf(gold.get("claimId")))
                            .as("the id the split recorded for the gold of %s is the id the corpus "
                                    + "report froze", id)
                            .isIn(reportIdsByProposition().getOrDefault(proposition, List.of()));
                    assertThat(sha256(corpus.resolve(file)))
                            .as("the corpus file %s still has the digest the split froze", file)
                            .isEqualTo(digest);
                    assertThat(containsVerbatim(corpus.resolve(file), proposition))
                            .as("the gold proposition of %s occurs VERBATIM in %s", id, file)
                            .isTrue();
                    assertThat(regressionGold)
                            .as("the gold proposition of %s is not the gold of a regression set",
                                    id)
                            .doesNotContain(proposition);
                    assertThat(goldClaimIds.add(String.valueOf(gold.get("claimId"))))
                            .as("the gold claim of %s is used by one case only", id).isTrue();
                    goldPropositions.add(proposition);
                    if ("OBSERVED".equals(subset)) {
                        observedSourceByFile.put(file, sourcesByProposition.get(proposition));
                    }
                }
            }

            // rule 6: the required classes are present
            assertThat(classes).as("the H2 dev split carries the required classes")
                    .containsAll(REQUIRED_CLASSES);

            // ── the structure this milestone's measurement depends on ───────────
            for (Map.Entry<String, Integer> required : REQUIRED_SUBSETS.entrySet()) {
                assertThat(subsets.getOrDefault(required.getKey(), 0))
                        .as("the split carries %s case(s) in the %s subset", required.getValue(),
                                required.getKey())
                        .isEqualTo(required.getValue());
            }
            assertThat(subsets.getOrDefault("NOVEL", 0))
                    .as("the split carries novel cases: the transfer subset")
                    .isGreaterThanOrEqualTo(MINIMUM_NOVEL);

            // the learning episode's support set is the two in-support documents
            assertThat(observedSourceByFile.keySet())
                    .as("the observed episodes draw their gold from the two in-support documents")
                    .containsExactlyInAnyOrder(CORPUS_FILES.get(0), CORPUS_FILES.get(1));

            // the validation and novel cases stay INSIDE the support set, and the control
            // cases stay outside it
            for (Map<String, Object> entry : cases) {
                String subset = String.valueOf(entry.get("subset"));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> golds =
                        (List<Map<String, Object>>) entry.get("goldClaims");
                for (Map<String, Object> gold : golds) {
                    String file = String.valueOf(gold.get("corpusFile"));
                    if ("VALIDATION".equals(subset) || "NOVEL".equals(subset)) {
                        assertThat(file)
                                .as("the %s case %s draws its gold from the support set", subset,
                                        entry.get("id"))
                                .isIn(CORPUS_FILES.get(0), CORPUS_FILES.get(1));
                    } else if ("OUT_OF_SUPPORT".equals(subset)) {
                        assertThat(file)
                                .as("the control case %s draws its gold from OUTSIDE the support "
                                        + "set", entry.get("id"))
                                .isIn(CORPUS_FILES.get(2), CORPUS_FILES.get(3));
                    }
                }
            }

            // the corpus report is the artefact the split's gold was written against
            Path report = RepositoryPaths.root().resolve(CORPUS_REPORT);
            assertThat(report).as("the H2 corpus report %s is present", CORPUS_REPORT).exists();
            Map<?, ?> reportJson = json.readValue(Files.readString(report, StandardCharsets.UTF_8),
                    Map.class);
            assertThat(String.valueOf(parsed.get("corpusReportSha256")))
                    .as("the split pins the corpus report's digest")
                    .isEqualTo(sha256(report));
            List<?> documents = (List<?>) reportJson.get("documents");
            assertThat(documents).as("the corpus report names the corpus's documents")
                    .hasSize(CORPUS_FILES.size());
        } finally {
            bench.close();
        }
    }

    // ── the helpers ────────────────────────────────────────────────────────────

    /**
     * The claim ids the corpus report froze, by proposition.
     *
     * <p>The report was built in its own workspace, and the production claim id embeds the
     * workspace id — so this is the map that says which id the split's gold carries.</p>
     *
     * @return the ids, by proposition
     */
    private static Map<String, List<String>> reportIdsByProposition() {
        Map<String, List<String>> ids = new LinkedHashMap<>();
        Path report = RepositoryPaths.root().resolve(CORPUS_REPORT);
        if (!Files.exists(report)) {
            return ids;
        }
        try {
            ObjectMapper json = JsonMapper.builder().build();
            Map<?, ?> parsed = json.readValue(Files.readString(report, StandardCharsets.UTF_8),
                    Map.class);
            for (Object document : (List<?>) parsed.get("documents")) {
                if (!(document instanceof Map<?, ?> documentMap)) {
                    continue;
                }
                for (Object claim : (List<?>) documentMap.get("claims")) {
                    if (claim instanceof Map<?, ?> claimMap) {
                        ids.put(String.valueOf(claimMap.get("proposition")),
                                List.of(String.valueOf(claimMap.get("claimId"))));
                    }
                }
            }
        } catch (Exception unreadable) {
            return Map.of();
        }
        return ids;
    }

    /**
     * The gold propositions of the regression sets.
     *
     * @return the propositions
     */
    private static Set<String> regressionGold() throws Exception {
        Set<String> propositions = new LinkedHashSet<>();
        for (String set : regressionSets()) {
            Path file = RepositoryPaths.root().resolve(set);
            if (!Files.exists(file)) {
                continue;
            }
            ObjectMapper json = JsonMapper.builder().build();
            Map<?, ?> parsed = json.readValue(Files.readString(file, StandardCharsets.UTF_8),
                    Map.class);
            Object raw = parsed.get("cases");
            if (!(raw instanceof List<?> entries)) {
                continue;
            }
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                if (map.get("goldClaims") instanceof List<?> golds) {
                    for (Object gold : golds) {
                        if (gold instanceof Map<?, ?> goldMap
                                && goldMap.get("proposition") != null) {
                            propositions.add(String.valueOf(goldMap.get("proposition")));
                        }
                    }
                }
            }
        }
        return propositions;
    }

    /**
     * The questions, assertions and disputed premises of the regression sets and of the
     * existing CRJ-01 dev split.
     *
     * @return the texts
     */
    private static List<String> existingTexts() throws Exception {
        List<String> texts = new ArrayList<>();
        for (String set : regressionSets()) {
            Path file = RepositoryPaths.root().resolve(set);
            if (!Files.exists(file)) {
                continue;
            }
            ObjectMapper json = JsonMapper.builder().build();
            Map<?, ?> parsed = json.readValue(Files.readString(file, StandardCharsets.UTF_8),
                    Map.class);
            Object raw = parsed.get("cases");
            if (!(raw instanceof List<?> entries)) {
                continue;
            }
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                for (String key : List.of("question", "premise", "assertion",
                        "disputedPremise")) {
                    Object value = map.get(key);
                    if (value instanceof String text && !text.isBlank()) {
                        texts.add(text);
                    }
                }
                Object call = map.get("call");
                if (call instanceof Map<?, ?> callMap && callMap.get("body") instanceof Map<?, ?> body
                        && body.get("question") instanceof String text) {
                    texts.add(text);
                }
            }
        }
        return texts;
    }

    /**
     * The regression sets: the development set, named, and the held-out sets, discovered.
     *
     * @return the sets' paths, relative to the repository root
     */
    private static List<String> regressionSets() throws Exception {
        List<String> sets = new ArrayList<>();
        sets.add(DEVELOPMENT_SET);
        sets.add(CRJ01_DEV_SPLIT);
        Path directory = RepositoryPaths.root().resolve(HELD_OUT_DIRECTORY);
        if (Files.exists(directory)) {
            try (var paths = Files.list(directory)) {
                for (Path path : paths.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".json"))
                        .sorted()
                        .toList()) {
                    if (isHeldOutSet(path)) {
                        sets.add(HELD_OUT_DIRECTORY + "/" + path.getFileName().toString());
                    }
                }
            }
        }
        return sets;
    }

    /**
     * Whether a JSON file is a HELD-OUT SET, judged by its shape.
     *
     * @param path the file
     * @return true when the file is a held-out set
     */
    private static boolean isHeldOutSet(Path path) {
        String name = path.getFileName().toString();
        if (name.startsWith("CRJ-01-")) {
            return false;
        }
        try {
            ObjectMapper json = JsonMapper.builder().build();
            Object parsed = json.readValue(Files.readString(path, StandardCharsets.UTF_8),
                    Map.class);
            if (!(parsed instanceof Map<?, ?> map) || !(map.get("cases") instanceof List<?>)) {
                return false;
            }
            for (Object entry : (List<?>) map.get("cases")) {
                if (entry instanceof Map<?, ?> caseMap
                        && caseMap.get("goldClaims") instanceof List<?> golds) {
                    for (Object gold : golds) {
                        if (gold instanceof Map<?, ?> goldMap
                                && goldMap.get("proposition") != null) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception unreadable) {
            return false;
        }
        return false;
    }

    /**
     * The normalised token sequences of a text, of length n and more.
     *
     * @param text the text
     * @return the sequences
     */
    private static Set<String> ngrams(String text) {
        List<String> tokens = tokens(text);
        Set<String> grams = new LinkedHashSet<>();
        for (int size = MINIMUM_SHARED_TOKENS; size <= tokens.size(); size++) {
            for (int start = 0; start + size <= tokens.size(); start++) {
                grams.add(String.join(" ", tokens.subList(start, start + size)));
            }
        }
        return grams;
    }

    /**
     * The normalised tokens of a text: lower case, words and numbers only.
     *
     * @param text the text
     * @return the tokens
     */
    private static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!word.isBlank()) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    /**
     * Whether a text occurs VERBATIM in a file's bytes.
     *
     * @param file        the file
     * @param proposition the text
     * @return true when the text occurs verbatim
     */
    private static boolean containsVerbatim(Path file, String proposition) {
        if (proposition == null || proposition.isBlank()) {
            return false;
        }
        try {
            byte[] haystack = Files.readAllBytes(file);
            byte[] needle = proposition.getBytes(StandardCharsets.UTF_8);
            if (needle.length == 0 || needle.length > haystack.length) {
                return false;
            }
            outer:
            for (int i = 0; i + needle.length <= haystack.length; i++) {
                for (int j = 0; j < needle.length; j++) {
                    if (haystack[i + j] != needle[j]) {
                        continue outer;
                    }
                }
                return true;
            }
            return false;
        } catch (Exception unreadable) {
            return false;
        }
    }

    /**
     * The digest of a file's bytes.
     *
     * @param file the file
     * @return the digest, in lowercase hex
     */
    private static String sha256(Path file) throws Exception {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(Files.readAllBytes(file)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
