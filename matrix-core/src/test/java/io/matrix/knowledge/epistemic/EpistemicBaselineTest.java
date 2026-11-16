package io.matrix.knowledge.epistemic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.PremiseAssessment;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.knowledge.acquisition.DocumentImporter;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.3 — the BASELINE measurement of the CURRENT production path on the new held-out set.
 *
 * <p><b>What this is.</b> The measurement of the premise-handling path as it stands today,
 * on the frozen set of {@code docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json}. It is the
 * STARTING POINT, not a target: the numbers say where the production path stands, and a
 * later implementation is measured against this, not against the set's expectations.</p>
 *
 * <p><b>Why it is a test and not a script.</b> The measurement has to be repeatable, and the
 * repeatability has to be checked rather than assumed: the same frozen set, the same
 * corpus, the same questions must give the same verdict every run. The test also re-derives
 * the FREEZE digest from the file itself, so a dataset that was edited after the freeze
 * cannot be measured quietly.</p>
 *
 * <p><b>What it measures, per case.</b> Two production paths, because there are two:
 * {@code KnowledgeWorkbench.ask} — the path a user question takes, which answers or refuses
 * — and {@code PremiseAssessment.assess} — the path an assertion takes, which says what the
 * workspace makes of each premise. The dataset's expected disposition is mapped onto the
 * production vocabulary, and the difference is the metric.</p>
 *
 * <p><b>What it does not do.</b> It does not judge the implementation, and it does not
 * require the implementation to be good: a red number here is a finding, not a failure.
 * The assertions are about the MEASUREMENT — the freeze, the coverage of the set, the
 * determinism of the reading — and never about the production path's behaviour.</p>
 */
class EpistemicBaselineTest {

    /** The frozen set this measurement reads. */
    private static final String DATASET = "docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json";

    /**
     * The adversarial probe files, which may NAME the artefact.
     *
     * <p><b>The exemption is narrow on purpose.</b> A probe that measures the production
     * path against the set has to say which artefact it reads, and it may read it AT RUN
     * TIME. So the probe files are exempt from the SET-REFERENCE check only. They are
     * NOT exempt from the question check or the gold check: a probe that QUOTES a
     * question of the set puts that question into the development code, and the set
     * stops being held out for it. That check applies to every file in the tree,
     * probes included, and a probe that legitimately needs a question must read it
     * from the artefact at run time instead of writing it down.</p>
     */
    private static final String PROBE_FILE_MARKER = "AdversarialProbe";

    /** The corpus the set was built from and the measurement is run against. */
    private static final String CORPUS = AcquisitionCycle.SEED_CORPUS;

    /** The workspace the corpus is imported into. */
    private static final String WORKSPACE = "epistemic-heldout-baseline";

    /** The production files that decide the premise verdict, pinned by digest in the report. */
    private static final List<String> PRODUCTION_PATH = List.of(
            "matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java",
            "matrix-core/src/main/java/io/matrix/knowledge/PremiseChecker.java",
            "matrix-core/src/main/java/io/matrix/knowledge/PremiseAssessment.java",
            "matrix-core/src/main/java/io/matrix/knowledge/PremiseSemantics.java");

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporary;

    // ── one measured case ─────────────────────────────────────────────────────

    /** What the production path did with one case of the set. */
    record Observation(String id, String caseClass, String expected, String observed,
                       boolean answered, boolean refused, int conflicts, int readings,
                       int evidence, long millis) {

        /** The metrics a single case contributes to. */
        boolean falseRefusal() {
            return expected.equals("ANSWERED") && refused;
        }

        boolean falseContradiction() {
            return !expected.equals("REFUTED") && !expected.equals("CONFLICT_SURFACED")
                    && !expected.equals("AMBIGUITY_SURFACED") && observed.equals("REFUTED");
        }

        boolean unsupportedAnswering() {
            return expected.equals("REFUTED") && answered;
        }

        /**
         * Whether the production status is the one the set expects.
         *
         * <p>The set speaks in dispositions and the production path speaks in statuses, so
         * the two vocabularies are mapped here, once, and the mapping is written down
         * instead of being hidden in a comparison.</p>
         */
        boolean statusAsExpected() {
            return switch (expected) {
                case "ANSWERED" -> observed.equals("SUPPORTED") || observed.equals("NO_PREMISE");
                case "REFUTED" -> observed.equals("REFUTED");
                case "UNSUPPORTED" -> observed.equals("UNSUPPORTED");
                case "NOT_REFUTED" -> !observed.equals("REFUTED");
                case "CONFLICT_SURFACED" -> observed.equals("CONTRADICTORY");
                case "AMBIGUITY_SURFACED" -> observed.equals("AMBIGUOUS");
                default -> false;
            };
        }
    }

    // ── the measurement ───────────────────────────────────────────────────────

    @Test
    @DisplayName("P0.3 — the baseline of the current production path on the frozen held-out set")
    void measureTheCurrentProductionPath() throws Exception {
        // ── the frozen set, and the freeze it carries ────────────────────────
        Path artefact = RepositoryPaths.root().resolve(DATASET);
        assertThat(artefact).as("the frozen set %s is missing", DATASET).exists();
        JsonNode dataset = JSON.readTree(Files.readAllBytes(artefact));
        JsonNode cases = dataset.path("cases");
        assertThat(cases.isArray()).as("the frozen set carries no cases array").isTrue();
        assertThat(cases).as("the frozen set is empty").isNotEmpty();

        // The freeze digest is taken over the compact JSON of the cases array; the reader
        // re-derives it, so an edit after the freeze cannot pass unnoticed.
        String canonical = JSON.writeValueAsString(cases) + "\n";
        String recomputed = sha256(canonical.getBytes(StandardCharsets.UTF_8));
        assertThat(recomputed)
                .as("the freeze digest of the set does not re-derive: the set was edited "
                        + "after the freeze")
                .isEqualTo(dataset.path("freeze").path("sha256").asText());
        System.out.println("[P0.3-baseline] freeze sha256 " + recomputed);

        // ── the workspace, assembled through the production acquisition path ──
        Path database = temporary.resolve("epistemic-baseline.sqlite");
        KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString());
        ClasspathCorpus corpus = ClasspathCorpus.extract(CORPUS);
        for (ClasspathCorpus.Entry entry : corpus.entries()) {
            DocumentImporter.ImportResult result =
                    workbench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
            assertThat(result.claimsAdded()).as("claims derived from %s", entry.file()).isPositive();
        }

        // ── the measurement, case by case ─────────────────────────────────────
        List<Observation> observations = new ArrayList<>();
        Map<String, int[]> perClass = new LinkedHashMap<>();
        Map<String, Long> latencyTotal = new LinkedHashMap<>();
        Map<String, Long> latencyMax = new LinkedHashMap<>();
        int falseRefusals = 0;
        int falseContradictions = 0;
        int unsupportedAnswering = 0;
        // The three numbers are kept apart ON PURPOSE (adversarial review, round 1):
        // named = what the set names, shown = what the response showed, matched = how
        // many of the named spans the response actually carried.
        int ambiguitySpansNamed = 0;
        int ambiguityReadingsShown = 0;
        int ambiguitySpansMatched = 0;
        int conflictSidesNamed = 0;
        int conflictSidesShown = 0;
        int conflictSidesMatched = 0;

        for (JsonNode entry : cases) {
            String id = entry.path("id").asText();
            String caseClass = entry.path("class").asText();
            String expected = entry.path("expectedDisposition").asText();
            String question = entry.path("question").asText();
            String premise = entry.path("premise").isNull() || entry.path("premise").asText().isBlank()
                    ? null : entry.path("premise").asText();

            long started = System.nanoTime();
            Answer answer = workbench.ask(WORKSPACE, question, AbstractionLevel.INTRODUCTORY);
            long millis = (System.nanoTime() - started)
                    / java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(1);

            String observed = answer.premise() == null
                    ? "NO_PREMISE" : answer.premise().status().name();
            int conflicts = answer.premise() == null ? 0 : answer.premise().conflicts().size();
            int readings = answer.premise() == null ? 0 : answer.premise().readings().size();
            int evidence = answer.premise() == null ? 0 : answer.premise().evidence().size();

            // The second production path: the premise on its own.
            if (premise != null) {
                PremiseAssessment.Assessment assessment =
                        PremiseAssessment.assess(workbench, WORKSPACE, premise);
                assertThat(assessment.findings()).as("the assessment of %s is empty", id).isNotEmpty();
                assertThat(assessment.promoted())
                        .as("the assessment of %s promoted the user's assertion", id).isFalse();
            }

            Observation observation = new Observation(id, caseClass, expected, observed,
                    answer.answered(), !answer.answered(), conflicts, readings, evidence, millis);
            observations.add(observation);

            perClass.computeIfAbsent(caseClass, key -> new int[4]);
            int[] counters = perClass.get(caseClass);
            counters[0]++;
            if (observation.answered()) {
                counters[1]++;
            } else {
                counters[2]++;
            }
            if (!observation.statusAsExpected()) {
                counters[3]++;
            }
            if (observation.falseRefusal()) {
                falseRefusals++;
            }
            if (observation.falseContradiction()) {
                falseContradictions++;
            }
            if (observation.unsupportedAnswering()) {
                unsupportedAnswering++;
            }
            latencyTotal.merge(caseClass, millis, Long::sum);
            latencyMax.merge(caseClass, millis, Math::max);

            // ── the coverage of the readings and of the conflicting sides ───────
            //
            // THE MEASUREMENT IS A MATCH, NOT A COUNT (adversarial review, round 1).
            //
            // The first version of this metric added up the readings the response
            // SHOWED and called the result "coverage". A count cannot see a wrong
            // reading: the response can show seven readings of which only five are
            // the corpus's, and the metric then reports 7/7 while the truth is 5/7.
            // The two numbers are now kept apart and both are printed:
            //   named   — what the set names (the gold spans, the gold sides)
            //   shown   — what the response showed (its own count)
            //   matched — how many of the NAMED spans the response actually carried
            // A reader cannot mistake one for the other, and the coverage is
            // matched/named.
            int namedSpans = 0;
            Set<String> reportedReadingKeys = new LinkedHashSet<>();
            if (answer.premise() != null) {
                // EVERY evidence item the response carries, not only the ones that hang
                // under a reading: the question is whether the response carried the
                // corpus's span at all.
                for (io.matrix.knowledge.PremiseAssessment.EvidenceRef carried
                        : answer.premise().evidence()) {
                    reportedReadingKeys.add(carried.proposition());
                    reportedReadingKeys.add(carried.claimId());
                }
                for (io.matrix.knowledge.PremiseSemantics.Reading reading
                        : answer.premise().readings()) {
                    for (io.matrix.knowledge.PremiseAssessment.EvidenceRef ref
                            : reading.evidence()) {
                        reportedReadingKeys.add(ref.proposition());
                        reportedReadingKeys.add(ref.claimId());
                    }
                }
            }
            int matchedSpans = 0;
            List<String> goldSpanKeys = new ArrayList<>();
            for (JsonNode reading : entry.path("readings")) {
                for (JsonNode span : reading.path("spans")) {
                    namedSpans++;
                    goldSpanKeys.add(span.path("claimId").asText() + " :: "
                            + abbreviate(span.path("proposition").asText()));
                    if (reportedReadingKeys.contains(span.path("proposition").asText())
                            || reportedReadingKeys.contains(span.path("claimId").asText())) {
                        matchedSpans++;
                    }
                }
            }

            int namedSides = 0;
            Set<String> reportedSideKeys = new LinkedHashSet<>();
            int reportedSideCount = 0;
            if (answer.premise() != null) {
                for (io.matrix.knowledge.ClaimConflictDetector.Conflict conflict
                        : answer.premise().conflicts()) {
                    for (io.matrix.knowledge.ClaimConflictDetector.Side side : conflict.sides()) {
                        reportedSideCount++;
                        reportedSideKeys.add(side.proposition());
                        reportedSideKeys.add(side.claimId());
                    }
                }
            }
            int matchedSides = 0;
            for (JsonNode claim : entry.path("goldClaims")) {
                namedSides++;
                if (reportedSideKeys.contains(claim.path("proposition").asText())
                        || reportedSideKeys.contains(claim.path("claimId").asText())) {
                    matchedSides++;
                }
            }

            if (caseClass.equals("AMBIGUOUS_READINGS")) {
                ambiguitySpansNamed += namedSpans;
                ambiguityReadingsShown += readings;
                ambiguitySpansMatched += matchedSpans;
                System.out.println("[P0.3-baseline] " + id
                        + " | ambiguity: named " + namedSpans + " spans, shown " + readings
                        + " readings, matched " + matchedSpans + " spans");
                System.out.println("[P0.3-baseline] " + id + " | gold spans: " + goldSpanKeys);
                System.out.println("[P0.3-baseline] " + id + " | reported: "
                        + reportedReadingKeys);
            }
            if (caseClass.equals("CONFLICTING_EVIDENCE")) {
                conflictSidesNamed += namedSides;
                conflictSidesShown += reportedSideCount;
                conflictSidesMatched += matchedSides;
                System.out.println("[P0.3-baseline] " + id
                        + " | conflict: named " + namedSides + " sides, shown "
                        + reportedSideCount + " sides, matched " + matchedSides);
            }

            System.out.println("[P0.3-baseline] " + id + " | " + caseClass
                    + " | expected=" + expected + " observed=" + observed
                    + " answered=" + answer.answered()
                    + " conflicts=" + conflicts + " readings=" + readings
                    + " evidence=" + evidence + " | " + millis + " ms"
                    + (observation.statusAsExpected() ? "" : "   << NOT AS EXPECTED"));
        }

        // ── the reading of the measurement ───────────────────────────────────
        assertThat(observations).as("not every case of the set was measured")
                .hasSameSizeAs(cases);
        assertThat(perClass).as("the measurement covered fewer classes than the set holds")
                .hasSameSizeAs(countClasses(cases));
        assertThat(perClass.values().stream().mapToInt(counters -> counters[0]).sum())
                .as("the per-class counters do not add up to the number of cases")
                .isEqualTo(observations.size());
        assertThat(falseRefusals).as("more false refusals than cases").isLessThanOrEqualTo(observations.size());
        assertThat(falseContradictions).as("more false contradictions than cases")
                .isLessThanOrEqualTo(observations.size());
        assertThat(unsupportedAnswering).as("more unsupported answers than cases")
                .isLessThanOrEqualTo(observations.size());
        assertThat(ambiguitySpansMatched).as("more matched spans than named spans")
                .isLessThanOrEqualTo(ambiguitySpansNamed);
        assertThat(conflictSidesMatched).as("more matched sides than named sides")
                .isLessThanOrEqualTo(conflictSidesNamed);
        assertThat(ambiguitySpansNamed).as("the ambiguous cases name no span").isPositive();
        assertThat(conflictSidesNamed).as("the conflicting case names no side").isPositive();

        System.out.println("[P0.3-baseline] class | cases | answered | refused | status not as expected");
        for (Map.Entry<String, int[]> entry : perClass.entrySet()) {
            int[] counters = entry.getValue();
            System.out.println("[P0.3-baseline] " + entry.getKey() + " | " + counters[0]
                    + " | " + counters[1] + " | " + counters[2] + " | " + counters[3]
                    + " | mean " + (latencyTotal.get(entry.getKey()) / Math.max(1, counters[0]))
                    + " ms, max " + latencyMax.get(entry.getKey()) + " ms");
        }
        System.out.println("[P0.3-baseline] false refusals " + falseRefusals);
        System.out.println("[P0.3-baseline] false contradictions " + falseContradictions);
        System.out.println("[P0.3-baseline] unsupported answering " + unsupportedAnswering);
        System.out.println("[P0.3-baseline] ambiguity: named " + ambiguitySpansNamed
                + " spans, shown " + ambiguityReadingsShown + " readings, matched "
                + ambiguitySpansMatched + " spans (coverage "
                + coverage(ambiguitySpansMatched, ambiguitySpansNamed) + ")");
        System.out.println("[P0.3-baseline] conflict: named " + conflictSidesNamed
                + " sides, shown " + conflictSidesShown + ", matched " + conflictSidesMatched
                + " (coverage " + coverage(conflictSidesMatched, conflictSidesNamed) + ")");

        // ── the state of the production path the measurement read ────────────
        for (String file : PRODUCTION_PATH) {
            Path production = RepositoryPaths.root().resolve(file);
            if (Files.exists(production)) {
                System.out.println("[P0.3-baseline] production " + file + " sha256 "
                        + sha256(Files.readAllBytes(production)));
            }
        }
    }

    @Test
    @DisplayName("P0.3 — the measurement is repeatable: the same case read twice gives the same verdict")
    void theMeasurementIsRepeatable() throws Exception {
        Path artefact = RepositoryPaths.root().resolve(DATASET);
        assertThat(artefact).as("the frozen set %s is missing", DATASET).exists();
        JsonNode dataset = JSON.readTree(Files.readAllBytes(artefact));
        JsonNode cases = dataset.path("cases");
        assertThat(cases).isNotEmpty();

        Path database = temporary.resolve("epistemic-baseline-repeat.sqlite");
        KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString());
        ClasspathCorpus corpus = ClasspathCorpus.extract(CORPUS);
        for (ClasspathCorpus.Entry entry : corpus.entries()) {
            workbench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
        }

        Set<String> first = readVerdicts(workbench, cases);
        Set<String> second = readVerdicts(workbench, cases);
        assertThat(second).as("the production path gives a different reading on the second pass")
                .isEqualTo(first);
        assertThat(first).as("the reading of the set is empty").hasSameSizeAs(cases);
        System.out.println("[P0.3-baseline] repeatable verdicts " + first.size());
    }

    /** The verdict of every case, read from the production path, in a stable order. */
    private static Set<String> readVerdicts(KnowledgeWorkbench workbench, JsonNode cases) {
        Set<String> verdicts = new LinkedHashSet<>();
        for (JsonNode entry : cases) {
            Answer answer = workbench.ask(WORKSPACE, entry.path("question").asText(),
                    AbstractionLevel.INTRODUCTORY);
            verdicts.add(entry.path("id").asText() + "="
                    + (answer.premise() == null ? "NO_PREMISE" : answer.premise().status().name())
                    + "/" + answer.answered());
        }
        return verdicts;
    }

    /**
     * A coverage ratio, printed as a fraction of the named gold.
     *
     * @param matched how many of the named spans the response carried
     * @param named   how many spans the set names
     * @return the ratio, or "n/a" when the set names none
     */
    private static String coverage(int matched, int named) {
        if (named <= 0) return "n/a";
        return matched + "/" + named;
    }

    /** A short form of a proposition, for a report line. */
    private static String abbreviate(String text) {
        if (text == null) return "";
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 70 ? flat : flat.substring(0, 67) + "...";
    }

    /** The classes the frozen set holds. */
    private static Set<String> countClasses(JsonNode cases) {
        Set<String> classes = new LinkedHashSet<>();
        for (JsonNode entry : cases) {
            classes.add(entry.path("class").asText());
        }
        return classes;
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    @Test
    @DisplayName("P0.3 — the set carries no gold string that the development code already holds")
    void theSetIsNotLeakedIntoTheDevelopmentCode() throws Exception {
        Path artefact = RepositoryPaths.root().resolve(DATASET);
        assertThat(artefact).as("the frozen set %s is missing", DATASET).exists();
        JsonNode dataset = JSON.readTree(Files.readAllBytes(artefact));
        List<String> gold = new ArrayList<>();
        for (JsonNode entry : dataset.path("cases")) {
            for (JsonNode claim : entry.path("goldClaims")) {
                gold.add(claim.path("proposition").asText());
            }
        }
        assertThat(gold).isNotEmpty();

        // The set is referenced by its own builder and its own reader, and by nothing else
        // in the production or research code.
        Path root = RepositoryPaths.root();
        List<String> offenders = new ArrayList<>();
        List<String> questionOffenders = new ArrayList<>();
        List<String> roots = List.of(
                "matrix-core/src/main/java", "matrix-core/src/test/java",
                "matrix-brain-runtime/src", "matrix-api-gateway/src", "docs-v2/research");
        for (String area : roots) {
            Path start = root.resolve(area);
            if (!Files.exists(start)) {
                continue;
            }
            try (var paths = Files.walk(start)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    String relative = root.relativize(path).toString();
                    // The set's own builder and reader hold the set's questions by
                    // construction: they create it and measure it. They are exempt
                    // from every check here.
                    if (relative.endsWith("EpistemicHeldOutSetBuilderTest.java")
                            || relative.endsWith("EpistemicBaselineTest.java")) {
                        continue;
                    }
                    String text = Files.readString(path);
                    // The independent adversarial probes may NAME the set: they read
                    // it at run time to measure the production path against it.
                    // What no file may do is carry a gold proposition or a question
                    // of the set — that check below applies to every file, including
                    // the probes.
                    boolean isSetInstrument = relative.contains(PROBE_FILE_MARKER);
                    if (!isSetInstrument && text.contains("EPISTEMIC-HELDOUT-SET")) {
                        offenders.add(relative);
                    }
                    for (JsonNode entry : dataset.path("cases")) {
                        String question = entry.path("question").asText();
                        if (!question.isBlank() && text.contains(question)) {
                            questionOffenders.add(relative + " carries the question of "
                                    + entry.path("id").asText());
                        }
                        // The gold propositions are checked in EVERY file, with no
                        // exemption: a gold string embedded in the development code
                        // would make the set a development artefact rather than a
                        // held-out one.
                        for (JsonNode claim : entry.path("goldClaims")) {
                            String proposition = claim.path("proposition").asText();
                            if (!proposition.isBlank() && text.contains(proposition)) {
                                questionOffenders.add(relative + " carries the gold of "
                                        + entry.path("id").asText());
                            }
                        }
                    }
                }
            }
        }
        assertThat(offenders)
                .as("the code refers to the held-out set: %s", offenders)
                .isEmpty();
        assertThat(questionOffenders)
                .as("a question of the set appears in the development code: %s", questionOffenders)
                .isEmpty();
        System.out.println("[P0.3-baseline] gold propositions " + gold.size()
                + ", production references to the set " + offenders.size());
    }

    @Test
    @DisplayName("P0.3 — the licence of every document the set draws on is recorded in the manifest")
    void theManifestCarriesTheLicences() throws Exception {
        Path artefact = RepositoryPaths.root().resolve(DATASET);
        JsonNode dataset = JSON.readTree(Files.readAllBytes(artefact));
        JsonNode documents = dataset.path("manifest").path("documents");
        assertThat(documents.isArray()).as("the manifest carries no document list").isTrue();
        assertThat(documents).as("the manifest lists no document").isNotEmpty();
        for (JsonNode document : documents) {
            assertThat(document.path("licence").asText())
                    .as("the document %s carries no licence", document.path("file").asText())
                    .isNotBlank();
            assertThat(document.path("sha256").asText())
                    .as("the document %s carries no content digest", document.path("file").asText())
                    .isNotBlank();
        }
        // the corpus manifest digest the dataset records must still be the one on disk
        Path manifest = RepositoryPaths.root()
                .resolve("matrix-core/src/main/resources/" + CORPUS + "/corpus-manifest.json");
        assertThat(sha256(Files.readAllBytes(manifest)))
                .as("the corpus manifest has changed since the set was frozen")
                .isEqualTo(dataset.path("manifest").path("corpusManifestSha256").asText());
        System.out.println("[P0.3-baseline] documents " + documents.size());
    }

    @Test
    @DisplayName("P0.3 — the names of the classes the directive names are the names of the set")
    void theClassesAreTheOnesTheDirectiveNames() throws Exception {
        Path artefact = RepositoryPaths.root().resolve(DATASET);
        JsonNode dataset = JSON.readTree(Files.readAllBytes(artefact));
        Set<String> declared = new LinkedHashSet<>();
        dataset.path("manifest").path("classes").fieldNames().forEachRemaining(declared::add);
        assertThat(declared).containsExactlyInAnyOrder(
                "SUPPORTED_PREMISE", "REFUTED_PREMISE", "UNSUPPORTED_PREMISE",
                "IRRELEVANT_REFUTATION", "CONFLICTING_EVIDENCE", "AMBIGUOUS_READINGS",
                "LEXICAL_NEGATION", "PARAPHRASE", "NO_PREMISE");
        assertThat(dataset.path("freeze").path("regressionSets").size())
                .as("the set does not name the two regression sets").isEqualTo(2);
        System.out.println("[P0.3-baseline] classes declared " + declared.size());
    }
}
