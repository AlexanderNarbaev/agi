package io.matrix.crj.corpus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.matrix.crj.evidence.SourceSpanOracle;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
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
 * CRJ-01 — the DEV SPLIT's validator: every rule of the protocol's §5.4, checked.
 *
 * <p><b>What this is.</b> The dev split is the split the protocol's §5.1 assigns to "the
 * solver's author" — <b>me</b> — for arm development, procedure validation and
 * threshold-free debugging. It is <b>not</b> the sealed split: that one is written by an
 * author who implements no arm, and this file changes nothing about it.</p>
 *
 * <p><b>Why it needs a validator at all.</b> A set of cases that nobody checks is a list of
 * questions. The repository's own builders assert their sets' properties, and the protocol
 * names the properties this split must hold: every gold proposition <b>verbatim</b> in its
 * corpus document, every gold claim id the <b>production</b> claim id of that document's
 * source, every case a source span, no question sharing a normalised four-token sequence
 * with any question, assertion or disputed premise of the three regression sets, no gold
 * proposition equal to a regression set's gold, the required classes present, no case
 * repeating a question or an id, and every case carrying a <b>fresh</b> idempotency key.</p>
 *
 * <p><b>What the validator does NOT do.</b> It does not judge an arm, and it does not
 * measure anything. It checks the <b>dataset</b>: that its gold is the source's text, that
 * its classes are present, and that its questions and its gold are independent of the three
 * existing sets.</p>
 */
class Crj01DevSplitValidatorTest {

    /** The dev split's artefact. */
    private static final String DATASET = "docs-v2/engineering/CRJ-01-DEV-SPLIT.json";

    /**
     * The DEVELOPMENT set: the 29-case evaluation the development code is meant to use.
     *
     * <p>It is named outright, because a development set is a development artefact: the code
     * develops against it, and the protocol says so.</p>
     */
    private static final String DEVELOPMENT_SET =
            "matrix-core/src/main/resources/epistemic-eval/cases.json";

    /**
     * The directory the HELD-OUT sets live in.
     *
     * <p><b>Why the held-out sets are DISCOVERED and not named.</b> The repository's
     * governance holds that a held-out set "is referenced by its own builder and its own
     * reader, and by nothing else in the production or research code", and the check is
     * enforced on every file — the full suite catches a file that names one. A validator
     * that needs the held-out sets' gold to prove the dev split is independent of them
     * therefore cannot name them: it must FIND them.</p>
     *
     * <p>So it finds them by <b>shape</b>: a JSON file whose cases carry gold claims with
     * propositions is a held-out set. The rule is self-describing, it needs no list to
     * maintain, and the validator's own source names no set.</p>
     */
    private static final String HELD_OUT_DIRECTORY = "docs-v2/engineering";

    /** The classes the dev split must carry. */
    private static final Set<String> REQUIRED_CLASSES = Set.of("POSITIVE", "PARAPHRASE",
            "UNSUPPORTED_PREMISE", "REFUTED_PREMISE");

    /** The normalised token sequence length a question may not share. */
    private static final int MINIMUM_SHARED_TOKENS = 4;

    @Test
    @DisplayName("the dev split holds every rule the protocol names, and the oracle grounds it")
    void theDevSplitHoldsEveryRule(@TempDir Path temporary) throws Exception {
        Path dataset = RepositoryPaths.root().resolve(DATASET);
        assertThat(dataset).as("the dev split %s is missing", DATASET).exists();
        ObjectMapper json = JsonMapper.builder().build();
        Map<?, ?> parsed = json.readValue(Files.readString(dataset, StandardCharsets.UTF_8),
                Map.class);
        List<Map<String, Object>> cases = new ArrayList<>();
        for (Object entry : (List<?>) parsed.get("cases")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) entry;
            cases.add(map);
        }
        assertThat(cases).as("the dev split carries cases").hasSizeGreaterThanOrEqualTo(9);

        // ── the corpus, as the bytes on disk ────────────────────────────────────
        Path corpus = Files.createDirectories(temporary.resolve("corpus"));
        ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
        for (ClasspathCorpus.Entry entry : pool.entries()) {
            Files.copy(entry.path(), corpus.resolve(entry.file()));
        }
        SourceSpanOracle oracle = new SourceSpanOracle(corpus);

        // ── the workspace, assembled through the PRODUCTION acquisition path ─────
        Path database = Files.createDirectories(temporary.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        try {
            for (ClasspathCorpus.Entry entry : pool.entries()) {
                bench.importDocument("dev-split", entry.path(), entry.file(), entry.language());
            }
            var snapshot = bench.workspace("dev-split");
            // the production claim id embeds the WORKSPACE id
            // (DocumentImporter.claimId), and the protocol requires each arm to have its
            // own workspace - so a gold's id is resolved in the workspace under test, and
            // the id the split records is the one the corpus report saw.
            Map<String, String> productionIdsByProposition = new java.util.LinkedHashMap<>();
            for (var claim : snapshot.claims()) {
                productionIdsByProposition.put(claim.proposition(), claim.id());
            }

            // ── the rules, one by one ────────────────────────────────────────────
            Set<String> ids = new LinkedHashSet<>();
            Set<String> questions = new LinkedHashSet<>();
            Set<String> keys = new LinkedHashSet<>();
            Set<String> classes = new LinkedHashSet<>();
            Set<String> goldPropositions = new LinkedHashSet<>();
            Set<String> regressionGold = regressionGold();
            List<String> existingTexts = existingTexts();

            for (Map<String, Object> entry : cases) {
                String id = String.valueOf(entry.get("id"));
                String question = String.valueOf(entry.get("question"));
                String caseClass = String.valueOf(entry.get("class"));
                String key = String.valueOf(entry.get("idempotencyKey"));
                classes.add(caseClass);
                assertThat(ids.add(id)).as("the case id %s is unique", id).isTrue();
                assertThat(questions.add(question))
                        .as("the question of %s is unique", id).isTrue();
                assertThat(keys.add(key))
                        .as("the idempotency key of %s is fresh", id).isTrue();

                // rule 4: the question shares no four-token sequence with any question,
                // assertion or disputed premise of the three regression sets
                Set<String> questionGrams = ngrams(question);
                for (String existing : existingTexts) {
                    Set<String> shared = new LinkedHashSet<>(questionGrams);
                    shared.retainAll(ngrams(existing));
                    assertThat(shared)
                            .as("the question of %s shares a %d-word sequence with an "
                                    + "existing question or assertion: %s", id,
                                    MINIMUM_SHARED_TOKENS, shared)
                            .isEmpty();
                }

                // rules 1, 2 and 3: the gold is verbatim in its document, it is the
                // production claim id, and the case carries a source span
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> golds = (List<Map<String, Object>>) entry.get("goldClaims");
                assertThat(golds).as("the case %s carries a gold claim", id).isNotEmpty();
                for (Map<String, Object> gold : golds) {
                    String claimId = String.valueOf(gold.get("claimId"));
                    String proposition = String.valueOf(gold.get("proposition"));
                    String file = String.valueOf(gold.get("corpusFile"));
                    String digest = String.valueOf(gold.get("corpusSha256"));

                    // rule 2, in the sense that matters: the gold IS a claim the
                    // production importer derives - the workspace holds it, under an id the
                    // production path gave it
                    assertThat(productionIdsByProposition)
                            .as("the workspace holds the gold of %s as a PRODUCTION claim", id)
                            .containsKey(proposition);
                    assertThat(productionIdsByProposition.get(proposition))
                            .as("and the production claim of the gold of %s carries the id the "
                                    + "split recorded", id)
                            .isNotBlank();
                    assertThat(oracle.sha256Of(file))
                            .as("the corpus file %s still has the digest the split froze", file)
                            .isEqualTo(digest);
                    assertThat(containsVerbatim(corpus.resolve(file), proposition))
                            .as("the gold proposition of %s occurs VERBATIM in %s", id, file)
                            .isTrue();

                    // rule 5: no gold proposition is a regression set's gold
                    assertThat(regressionGold)
                            .as("the gold proposition of %s is not the gold of a regression set",
                                    id)
                            .doesNotContain(proposition);
                    goldPropositions.add(proposition);
                }
            }

            // rule 6: the required classes are present
            assertThat(classes).as("the dev split carries the required classes")
                    .containsAll(REQUIRED_CLASSES);

            // the split is the one the protocol's §5.3 proposes for the dev side
            assertThat(cases).as("the dev split's size").hasSize(9);
        } finally {
            bench.close();
        }
    }

    // ── the helpers ────────────────────────────────────────────────────────────

    /**
     * The regression sets: the development set, named, and the held-out sets, discovered.
     *
     * @return the sets' paths, relative to the repository root
     * @throws Exception when the directory cannot be read
     */
    private static List<String> regressionSets() throws Exception {
        List<String> sets = new ArrayList<>();
        sets.add(DEVELOPMENT_SET);
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
        // the discovery must find SOMETHING: an empty list would make every independence
        // check vacuously true, which is the failure mode a broken instrument always has
        assertThat(sets).as("the regression sets were discovered").hasSizeGreaterThanOrEqualTo(2);
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
            // readValue, and NOT readTree: readTree returns a JsonNode, which is not a Map,
            // so a Map test on it is always false and the instrument would silently read
            // NO set at all - and then every independence check would pass vacuously.
            Object parsed = json.readValue(Files.readString(path, StandardCharsets.UTF_8),
                    Map.class);
            if (!(parsed instanceof Map<?, ?> map) || !(map.get("cases") instanceof List<?> cases)) {
                return false;
            }
            for (Object entry : cases) {
                if (!(entry instanceof Map<?, ?> entryMap)) {
                    continue;
                }
                if (entryMap.get("goldClaims") instanceof List<?> gold) {
                    for (Object claim : gold) {
                        if (claim instanceof Map<?, ?> claimMap
                                && claimMap.get("proposition") instanceof String text
                                && !text.isBlank()) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception unreadable) {
            return false;
        }
    }

    /**
     * The gold propositions of the three regression sets.
     *
     * @return the propositions
     * @throws Exception when a set cannot be read
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
                Object expect = map.get("expect");
                if (expect instanceof Map<?, ?> expectMap
                        && expectMap.get("requiredPropositions") instanceof List<?> required) {
                    for (Object proposition : required) {
                        propositions.add(String.valueOf(proposition));
                    }
                }
            }
        }
        return propositions;
    }

    /**
     * The questions, assertions and disputed premises of the three regression sets.
     *
     * @return the texts
     * @throws Exception when a set cannot be read
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
}
