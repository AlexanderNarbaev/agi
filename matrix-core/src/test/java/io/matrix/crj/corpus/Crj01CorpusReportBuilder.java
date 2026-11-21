package io.matrix.crj.corpus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 — the CORPUS REPORT: what the research pool can still serve as a case set.
 *
 * <p><b>Why this measurement exists.</b> The programme needs a corpus the **sealed** split
 * can be built from, and the corpus question was settled only <b>partly</b>: the two earlier
 * measurements showed that the bundled photosynthesis package is a <b>paraphrase</b> corpus
 * (0 of 7 cited claims are spans) and that the research pool <b>can</b> serve verbatim spans
 * (483 of 540). Neither measurement asked the question the sealed split actually turns on:
 * <b>how much of the pool is left after the three regression sets have taken their gold?</b>
 *
 * <p><b>Why it matters.</b> The sealed split is 18 domain-A cases, and the three regression
 * sets — the 29-case development set, the C2A 38-case set and the EPISTEMIC 16-case set —
 * have all drawn their gold from this pool or from its seed corpus. A claim that is a
 * regression set's gold is <b>not fresh evidence</b>: a CRJ-01 result that quotes it as
 * held-out proof is invalid by the protocol's own §5.2. So the corpus's <b>usable</b> size is
 * the number that decides whether the sealed split can be built here at all — and that
 * number was unmeasured.</p>
 *
 * <p><b>What it measures, and what it does not.</b> It counts, over the claims the
 * <b>production acquisition path</b> derives: how many are <b>verbatim spans</b> of their
 * document (so the oracle can ground them), how many are <b>fresh</b> (their proposition is
 * not the gold proposition of any of the three regression sets), and how many are <b>both</b>.
 * It also records, per document, the claim count, so the <b>support fraction</b> — the
 * quantity the cost curve proved decides whether the arm C mechanism can pay off at all — is
 * readable from the report.</p>
 *
 * <p><b>What it does not do.</b> It authors no case, judges no arm and changes no product
 * behaviour. The cases are the owner's commission (a case author who implements no arm), and
 * the freshness this report measures is a <b>necessary</b> condition for a fresh case, not a
 * sufficient one: a fresh claim is <em>available</em>, and whether it makes a good case is
 * the author's judgement.</p>
 *
 * <p><b>The artefact.</b> {@code docs-v2/engineering/CRJ-01-CORPUS-REPORT.json}, written
 * byte-reproducibly and read back and compared byte-for-byte before the test ends.</p>
 */
class Crj01CorpusReportBuilder {

    /** The workspace the pool is imported into. */
    private static final String WORKSPACE = "research-pool";

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
     * <p><b>Why the sets are DISCOVERED and not named.</b> The repository's governance holds
     * that a held-out set "is referenced by its own builder and its own reader, and by
     * nothing else in the production or research code" — the check that reads
     * {@code EpistemicBaselineTest} fails any development file that NAMES a held-out set,
     * and the check is enforced on every file. A corpus instrument that needs the held-out
     * sets' gold to say what is FRESH therefore cannot name them: it must FIND them.</p>
     *
     * <p>So it finds them by <b>shape</b>: a JSON file whose cases carry gold claims with
     * propositions is a held-out set. The rule is self-describing, it needs no list to
     * maintain, and the instrument's own source names no set. What the governance forbids
     * outright — a development file CARRYING a gold proposition or a question of a set —
     * this file does neither of: it reads the sets at run time, exactly as the repository's
     * own adversarial probes do, and its source holds no gold string and no question.</p>
     */
    private static final String HELD_OUT_DIRECTORY = "docs-v2/engineering";

    @Test
    @DisplayName("the research pool's usable remainder is counted, and the artefact is written")
    void theUsableRemainderIsCounted(@TempDir Path temporary) throws Exception {
        // ── the corpus, as the bytes on disk ────────────────────────────────────
        Path corpus = Files.createDirectories(temporary.resolve("corpus"));
        ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
        for (ClasspathCorpus.Entry entry : pool.entries()) {
            Files.copy(entry.path(), corpus.resolve(entry.file()));
        }

        // ── the workspace, assembled through the PRODUCTION acquisition path ─────
        Path database = Files.createDirectories(temporary.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        try {
            for (ClasspathCorpus.Entry entry : pool.entries()) {
                bench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
            }
            KnowledgeSnapshot snapshot = bench.workspace(WORKSPACE);

            // ── what the regression sets have already taken ──────────────────────
            Map<String, Set<String>> goldBySet = new LinkedHashMap<>();
            List<String> sets = regressionSets();
            for (String set : sets) {
                goldBySet.put(set, goldPropositionsOf(set));
            }
            // the instrument must prove it READ something: a silent empty read would make
            // every claim look fresh, and "everything is fresh" is exactly the answer a
            // broken instrument gives
            assertThat(sets)
                    .as("the regression sets were DISCOVERED, not named, and the discovery "
                            + "found the development set and the held-out sets")
                    .hasSizeGreaterThanOrEqualTo(2);
            for (Map.Entry<String, Set<String>> entry : goldBySet.entrySet()) {
                assertThat(entry.getValue())
                        .as("the set %s was read and holds its gold", entry.getKey())
                        .isNotEmpty();
            }
            Set<String> allGold = new LinkedHashSet<>();
            goldBySet.values().forEach(allGold::addAll);

            // ── the claims, classified ───────────────────────────────────────────
            List<Map<String, Object>> candidates = new ArrayList<>();
            Map<String, Integer> claimsPerFile = new LinkedHashMap<>();
            int spans = 0;
            int fresh = 0;
            int freshAndSpan = 0;
            // ── REACHABILITY: the span the ORACLE reads and the span the LEARNER
            //    reads are not the same text. The oracle reads the corpus FILE; the
            //    learner's containment gate reads the source's stored EXCERPT. A
            //    candidate the learner cannot see is a candidate the sealed split
            //    cannot use, so the report counts the ones it CAN see.
            int spansVisibleToTheLearner = 0;
            int freshAndSpanVisibleToTheLearner = 0;
            for (Claim claim : snapshot.claims()) {
                Source source = snapshot.sources().stream()
                        .filter(s -> s.id().equals(claim.introducedBySourceId()))
                        .findFirst().orElse(null);
                if (source == null) {
                    continue;
                }
                String file = fileOf(source, pool);
                if (file == null) {
                    continue;
                }
                claimsPerFile.merge(file, 1, Integer::sum);
                boolean isSpan = containsVerbatim(corpus.resolve(file), claim.proposition());
                boolean isFresh = !allGold.contains(claim.proposition());
                if (isSpan) {
                    spans++;
                }
                if (isFresh) {
                    fresh++;
                }
                boolean visibleToTheLearner = source.excerpt() != null
                        && source.excerpt().contains(claim.proposition());
                if (isSpan && visibleToTheLearner) {
                    spansVisibleToTheLearner++;
                }
                if (isFresh && isSpan) {
                    freshAndSpan++;
                    if (visibleToTheLearner) {
                        freshAndSpanVisibleToTheLearner++;
                    }
                    Map<String, Object> candidate = new LinkedHashMap<>();
                    candidate.put("claimId", claim.id());
                    candidate.put("proposition", claim.proposition());
                    candidate.put("sourceId", source.id());
                    candidate.put("sourceTitle", source.title());
                    candidate.put("corpusFile", file);
                    candidate.put("corpusSha256", sha256(corpus.resolve(file)));
                    candidate.put("conceptIds", List.of());
                    candidates.add(candidate);
                }
            }
            candidates.sort(Comparator.comparing(c -> String.valueOf(c.get("claimId"))));

            // ── the support fractions, per document width ────────────────────────
            int totalClaims = snapshot.claims().size();
            List<Map<String, Object>> supportFractions = new ArrayList<>();
            List<String> files = new ArrayList<>(claimsPerFile.keySet());
            files.sort(Comparator.naturalOrder());
            for (int width = 1; width <= files.size(); width++) {
                int covered = 0;
                for (int i = 0; i < width; i++) {
                    covered += claimsPerFile.getOrDefault(files.get(i), 0);
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("supportDocuments", width);
                row.put("supportFiles", files.subList(0, width));
                row.put("supportClaims", covered);
                row.put("workspaceClaims", totalClaims);
                row.put("supportFraction",
                        String.format(Locale.ROOT, "%.4f", (double) covered / totalClaims));
                supportFractions.add(row);
            }

            // ── the artefact ─────────────────────────────────────────────────────
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("report", "crj-01-corpus-report");
            report.put("version", "1.0");
            report.put("date", "2026-11-21");
            report.put("corpus", "research-pool/plate-tectonics");
            report.put("acquisitionPath", "the PRODUCTION import path: "
                    + "workbench.importDocument(workspace, path, file, language)");
            report.put("purpose", "how much of the research pool is left for a FRESH case after "
                    + "the three regression sets have taken their gold, and whether the arm C "
                    + "mechanism's support fraction is reachable here at all");
            Map<String, Object> counts = new LinkedHashMap<>();
            counts.put("documents", files.size());
            counts.put("claims", totalClaims);
            counts.put("claimsThatAreVerbatimSpans", spans);
            counts.put("claimsThatAreFresh", fresh);
            counts.put("claimsThatAreFreshAndSpans", freshAndSpan);
            counts.put("claimsWhoseSpanTheLearnerCanSee", spansVisibleToTheLearner);
            counts.put("freshAndSpanCandidatesTheLearnerCanSee",
                    freshAndSpanVisibleToTheLearner);
            counts.put("regressionGoldPropositions", allGold.size());
            report.put("counts", counts);
            report.put("claimsPerDocument", claimsPerFile);
            report.put("supportFractions", supportFractions);
            report.put("regressionSets", goldBySet.keySet());
            report.put("regressionGoldPerSet",
                    goldBySet.entrySet().stream().collect(java.util.stream.Collectors
                            .toMap(Map.Entry::getKey, entry -> entry.getValue().size(),
                                    (a, b) -> a, LinkedHashMap::new)));
            report.put("candidates", candidates);

            ObjectMapper json = JsonMapper.builder().build();
            String text = json.writerWithDefaultPrettyPrinter().writeValueAsString(report);
            Path artefact = RepositoryPaths.root().resolve("docs-v2/engineering")
                    .resolve("CRJ-01-CORPUS-REPORT.json");
            Files.createDirectories(artefact.getParent());
            Files.writeString(artefact, text, StandardCharsets.UTF_8);
            assertThat(Files.readString(artefact, StandardCharsets.UTF_8)).isEqualTo(text);

            System.out.println("[CRJ-01] the corpus report " + artefact);
            System.out.println("[CRJ-01] claims " + totalClaims + " spans " + spans
                    + " fresh " + fresh + " fresh-and-span " + freshAndSpan);

            // ── the report must be truthful about what it measured ───────────────
            assertThat(spans).as("the claims that are literal spans of their document")
                    .isGreaterThan(totalClaims / 2);
            assertThat(fresh).as("the claims that no regression set has taken").isPositive();
            assertThat(freshAndSpan)
                    .as("the claims that are BOTH fresh and literal spans - the ones a fresh "
                            + "case could draw its gold from")
                    .isPositive();
            assertThat(candidates).as("and each is reported with its document's digest")
                    .allSatisfy(candidate -> {
                        assertThat(candidate.get("claimId")).isNotNull();
                        assertThat(candidate.get("proposition")).isNotNull();
                        assertThat(String.valueOf(candidate.get("corpusSha256")))
                                .hasSize(64);
                    });
        } finally {
            bench.close();
        }
    }

    // ── the helpers ────────────────────────────────────────────────────────────

    /**
     * The regression sets: the development set, named, and the held-out sets, discovered.
     *
     * @return the sets' paths, relative to the repository root
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
                        sets.add(HELD_OUT_DIRECTORY + "/"
                                + path.getFileName().toString());
                    }
                }
            }
        }
        return sets;
    }

    /**
     * Whether a JSON file is a HELD-OUT SET, judged by its shape.
     *
     * <p>A held-out set is a file whose cases carry <b>gold claims</b> — each with a
     * proposition. An evaluation artefact, a report and a manifest do not, and a CRJ-01
     * artefact of this builder's own making is excluded by its name.</p>
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
            // NO set at all - and then report every claim as fresh.
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
     * The gold propositions a regression set holds.
     *
     * @param path the set's path, relative to the repository root
     * @return the propositions
     */
    private static Set<String> goldPropositionsOf(String path) throws Exception {
        Set<String> propositions = new LinkedHashSet<>();
        for (Map<String, Object> entry : casesOf(path)) {
            Object gold = entry.get("goldClaims");
            if (gold instanceof List<?> claims) {
                for (Object claim : claims) {
                    if (claim instanceof Map<?, ?> map && map.get("proposition") != null) {
                        propositions.add(String.valueOf(map.get("proposition")));
                    }
                }
            }
            Object expected = entry.get("expect");
            if (expected instanceof Map<?, ?> map && map.get("requiredPropositions") != null) {
                for (Object proposition : (List<?>) map.get("requiredPropositions")) {
                    propositions.add(String.valueOf(proposition));
                }
            }
        }
        return propositions;
    }

    /**
     * The cases of a set, whatever it calls them.
     *
     * @param path the set's path, relative to the repository root
     * @return the cases
     */
    private static List<Map<String, Object>> casesOf(String path) throws Exception {
        Path file = RepositoryPaths.root().resolve(path);
        if (!Files.exists(file)) {
            return List.of();
        }
        ObjectMapper json = JsonMapper.builder().build();
        Object parsed = json.readValue(Files.readString(file, StandardCharsets.UTF_8), Map.class);
        if (parsed instanceof Map<?, ?> map && map.get("cases") instanceof List<?> cases) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object entry : cases) {
                if (entry instanceof Map<?, ?> entryMap) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    entryMap.forEach((key, value) -> copy.put(String.valueOf(key), value));
                    out.add(copy);
                }
            }
            return out;
        }
        return List.of();
    }

    /**
     * The corpus file a source came from, found by the title the manifest records.
     *
     * @param source the source
     * @param pool   the corpus
     * @return the file's name, or null when the source has no file
     */
    private static String fileOf(Source source, ClasspathCorpus pool) {
        for (ClasspathCorpus.Entry entry : pool.entries()) {
            if (entry.file().equals(source.title()) || entry.file().contains(source.title())) {
                return entry.file();
            }
        }
        return null;
    }

    /**
     * Whether a text occurs VERBATIM in a file's bytes.
     *
     * <p>Byte-identical and not "contains as a substring of the decoded text": the oracle
     * that will score the sealed split reads the file's bytes, and a case whose gold is
     * only a substring after a newline-normalising decode would fail there.</p>
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
     * The sha256 of a file's bytes.
     *
     * @param file the file
     * @return the digest, in lower-case hex
     */
    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(Files.readAllBytes(file));
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
