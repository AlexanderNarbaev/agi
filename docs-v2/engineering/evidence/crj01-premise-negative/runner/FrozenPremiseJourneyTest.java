package io.matrix.crj.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.cognitive.report.ReportJourneyPlanner;
import io.matrix.knowledge.ArtifactReportResource;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimSpan;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeChanges;
import io.matrix.knowledge.KnowledgeStore;
import io.matrix.knowledge.KnowledgeWorkbench;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

class FrozenPremiseJourneyTest {
    private static final String REFERENCE = "report.journey.reference.v1";
    private static final ComputeBudget BUDGET = new ComputeBudget(30_000, 30_000,
            512 * 1024 * 1024L, 100_000);
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporary;

    private record Provenance(String workspace, String source, String claim,
                              String revision, long start, long end, String span) {}

    private record Observation(Provenance expected, Provenance observed) {}

    @TestFactory
    List<DynamicTest> servedReference() throws Exception {
        Path fixtures = Path.of(System.getenv("MATRIX_FROZEN_EVALUATION"));
        JsonNode oracle = JSON.readTree(fixtures.resolve("oracle.json").toFile());
        var groups = new java.util.ArrayList<DynamicTest>();
        oracle.path("cases").fields().forEachRemaining(group ->
                groups.add(DynamicTest.dynamicTest(group.getKey(),
                        () -> runGroup(fixtures, oracle.path("documents"), group.getKey(), group.getValue()))));
        assertEquals(7, groups.size());
        return groups;
    }

    private void runGroup(Path fixtures, JsonNode documents, String group, JsonNode trials) throws Exception {
        Path directory = Files.createDirectory(temporary.resolve(group));
        Path output = Files.createDirectory(directory.resolve("output"));
        Path database = directory.resolve("workspace.sqlite");
        try (var bench = KnowledgeWorkbench.at(database.toString())) {
            var cognitive = new CognitiveService();
            cognitive.workbench(bench);
            try (var store = cognitive.store()) {
                var route = new ArtifactReportResource();
                route.workbench = bench;
                route.cognitive = cognitive;
                route.actions = new ArtifactActionService(true, output.toString());
                route.planner = new ReportJourneyPlanner();
                route.budget = BUDGET;

                var previousByWorkspace = new HashMap<String, Set<String>>();
                var observations = new java.util.ArrayList<Observation>();
                var failures = new java.util.ArrayList<org.junit.jupiter.api.function.Executable>();
                for (int number = 0; number < trials.size(); number++) {
                    JsonNode trial = trials.get(number);
                    String workspace = trial.path("workspace").asText();
                    int ordinal = number;
                    try {
                        if (trial.path("source_keys").isEmpty()) {
                            try (var emptyStore = new KnowledgeStore(database)) {
                                emptyStore.applyChanges(workspace, KnowledgeChanges.none());
                            }
                            assertTrue(bench.workspaces().contains(workspace));
                            assertTrue(bench.workspace(workspace).claims().isEmpty());
                        }
                        observations.add(runTrial(fixtures, documents, group, trial, ordinal,
                                bench, route, previousByWorkspace));
                    } catch (AssertionError | RuntimeException e) {
                        failures.add(() -> { throw e; });
                    }
                }

                assertAll(group, failures);
                if (group.startsWith("05")) {
                    assertEquals(2, observations.size());
                    for (int index = 0; index < observations.size(); index++) {
                        Provenance expected = observations.get(index).expected();
                        Provenance foreign = observations.get(1 - index).observed();
                        assertNotEquals(expected.workspace(), foreign.workspace());
                        assertThrows(AssertionError.class, () -> match(expected, foreign));
                    }
                }
            }
        }
    }

    private Observation runTrial(Path fixtures, JsonNode documents, String group, JsonNode trial,
                                 int ordinal, KnowledgeWorkbench bench, ArtifactReportResource route,
                                 Map<String, Set<String>> previousByWorkspace) throws Exception {
        String workspace = trial.path("workspace").asText();
        Map<String, String> sources = new HashMap<>();
        for (JsonNode keyNode : trial.path("source_keys")) {
            String key = keyNode.asText();
            JsonNode document = documents.path(key);
            Path path = fixtures.resolve(document.path("filename").asText());
            byte[] bytes = Files.readAllBytes(path);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            assertEquals(document.path("sha256").asText(), hash);
            assertEquals(document.path("bytes").asLong(), bytes.length);
            String canonicalPath = trial.path("canonical_import_path").asText(document.path("filename").asText());
            var receipt = bench.importDocument(workspace, path, canonicalPath, "en");
            assertEquals(workspace, receipt.workspaceId());
            assertEquals(canonicalPath, receipt.source().filename());
            assertEquals(hash, receipt.source().contentHash());
            sources.put(key, receipt.source().sourceId());
        }

        KnowledgeSnapshot snapshot = bench.workspace(workspace);
        Set<String> oldSources = previousByWorkspace.put(workspace, Set.copyOf(sources.values()));
        boolean oldClaimsApplicable = trial.path("epoch").asInt() == 2 && oldSources != null
                && snapshot.claims().stream().anyMatch(claim -> oldSources.contains(claim.introducedBySourceId())
                        && claim.status() != ClaimStatus.SUPERSEDED && claim.status() != ClaimStatus.REFINED);

        // The reference route recognizes explicit background markers. The original
        // causal question and the exact asserted proposition both remain frozen.
        String assertion = trial.path("gold_premise").asText().strip();
        String question = "Given that " + assertion.substring(0, assertion.length() - 1)
                + ", " + trial.path("question").asText();
        var request = new ArtifactReportResource.ReportRequest(question, null, true,
                group + "-" + ordinal, "INTRODUCTORY");
        long start = System.nanoTime();
        var response = route.journey(workspace, request, REFERENCE);
        long wallNs = System.nanoTime() - start;
        var body = assertInstanceOf(ArtifactReportResource.ReportResponse.class, response.response().getEntity());
        var premise = body.premise();
        System.out.println("FROZEN-PREMISE " + JSON.writeValueAsString(Map.of(
                "group", group, "ordinal", ordinal, "workspace", workspace,
                "wallNs", wallNs, "method", response.outcome().methodId(), "question", question,
                "priorRevisionClaimsStillApplicable", oldClaimsApplicable, "response", body)));
        assertEquals(REFERENCE, response.outcome().methodId());

        if (oldClaimsApplicable) {
            fail("UNVERIFIED: same-path reimport did not supersede old claims; no source revision operation tested");
        }

        assertNotNull(premise, "Expected an explicit premise assessment");
        assertEquals(normalize(trial.path("gold_premise").asText()), normalize(premise.premise()));
        String expectedStatus = trial.path("expected_status").asText();
        assertEquals(expectedStatus, premise.status());

        List<Provenance> expectedRefutations = resolveAll(fixtures, documents, trial.path("refuting"),
                sources, snapshot, workspace);
        List<Provenance> expectedSupport = resolveAll(fixtures, documents, trial.path("supporting"),
                sources, snapshot, workspace);
        if ("AMBIGUOUS".equals(expectedStatus)) {
            var expectedSides = new HashSet<List<String>>();
            StreamSupport.stream(trial.path("refuting").spliterator(), false)
                    .forEach(ref -> assertEquals("CONTRADICTS", ref.path("relation").asText()));
            StreamSupport.stream(trial.path("supporting").spliterator(), false)
                    .forEach(ref -> assertEquals("SUPPORTS", ref.path("relation").asText()));
            for (List<Provenance> side : List.of(expectedRefutations, expectedSupport)) {
                for (Provenance entry : side) {
                    expectedSides.add(List.of(entry.claim(), entry.source()));
                }
            }
            assertTrue(body.conflicts().stream().anyMatch(conflict -> {
                Set<List<String>> actualSides = new HashSet<>();
                for (var side : conflict.sides()) {
                    if (side.claimId() != null && side.sourceId() != null) {
                        actualSides.add(List.of(side.claimId(), side.sourceId()));
                    }
                }
                return expectedSides.equals(actualSides);
            }), "Both competing claims must appear in the typed record");
            return new Observation(null, null);
        }
        assertEquals(expectedRefutations.stream().map(Provenance::claim).collect(java.util.stream.Collectors.toSet()),
                Set.copyOf(premise.refutingClaimIds()), "refuting claims");
        assertEquals(expectedRefutations.stream().map(Provenance::source).collect(java.util.stream.Collectors.toSet()),
                Set.copyOf(premise.refutingSourceIds()), "refuting sources");
        if ("SUPPORTED".equals(expectedStatus)) {
            assertTrue(expectedSupport.stream().allMatch(expected -> body.evidence().stream()
                    .anyMatch(item -> expected.claim().equals(item.claimId())
                            && expected.source().equals(item.sourceId()))));
        }
        if (expectedRefutations.isEmpty()) {
            return new Observation(null, null);
        }
        assertEquals(1, expectedRefutations.size());
        Provenance expected = expectedRefutations.getFirst();
        Provenance actual = persisted(workspace, premise.refutingSourceIds().getFirst(),
                premise.refutingClaimIds().getFirst(), snapshot, expected.revision());
        match(expected, actual);
        if (trial.has("reject_refuting_span")) {
            Provenance wrong = resolve(fixtures, documents, trial.path("reject_refuting_span"),
                    sources, snapshot, workspace);
            assertEquals(expected.source(), wrong.source());
            assertThrows(AssertionError.class, () -> match(expected, wrong));
        }
        return new Observation(expected, actual);
    }

    private static List<Provenance> resolveAll(Path fixtures, JsonNode documents, JsonNode expected,
                                               Map<String, String> sources, KnowledgeSnapshot snapshot,
                                               String workspace) throws Exception {
        var results = new java.util.ArrayList<Provenance>();
        for (JsonNode gold : expected) {
            results.add(resolve(fixtures, documents, gold, sources, snapshot, workspace));
        }
        return results;
    }

    private static Provenance resolve(Path fixtures, JsonNode documents, JsonNode gold,
                                      Map<String, String> sources, KnowledgeSnapshot snapshot,
                                      String workspace) throws Exception {
        String key = gold.path("source_key").asText();
        String revision = gold.path("sha256").asText();
        JsonNode document = documents.path(key);
        assertEquals(document.path("sha256").asText(), revision);
        String contents = Files.readString(fixtures.resolve(document.path("filename").asText()),
                StandardCharsets.US_ASCII);
        int start = gold.path("span_start").asInt();
        int end = gold.path("span_end_exclusive").asInt();
        String wanted = contents.substring(start, end);
        assertEquals(gold.path("span").asText(), wanted);
        String source = assertNotNullValue(sources.get(key), "canonical import receipt");
        List<Claim> matches = snapshot.claims().stream()
                .filter(claim -> workspace.equals(claim.workspaceId())
                        && source.equals(claim.introducedBySourceId())
                        && normalize(wanted).equals(normalize(claim.proposition())))
                .toList();
        assertEquals(1, matches.size(), "UNVERIFIED: expected a unique imported gold claim for " + key);
        Provenance expected = new Provenance(workspace, source, matches.getFirst().id(), revision,
                start, end, wanted);
        match(expected, persisted(workspace, source, expected.claim(), snapshot, revision));
        return expected;
    }

    private static Provenance persisted(String workspace, String source, String claimId,
                                        KnowledgeSnapshot snapshot, String revision) {
        Claim claim = snapshot.claims().stream()
                .filter(c -> claimId.equals(c.id()) && workspace.equals(c.workspaceId()))
                .findFirst().orElseThrow();
        assertEquals(source, claim.introducedBySourceId());
        String sourceText = snapshot.sources().stream()
                .filter(s -> source.equals(s.id()) && workspace.equals(s.workspaceId())
                        && revision.equals(s.contentHash()))
                .map(io.matrix.knowledge.Source::excerpt)
                .findFirst().orElseThrow();
        var offsets = assertNotNullValue(ClaimSpan.parse(claim.context()), "Persisted source coordinates required");
        String excerpt = snapshot.evidence().stream()
                .filter(e -> claimId.equals(e.claimId()) && source.equals(e.sourceId())
                        && workspace.equals(e.workspaceId()))
                .map(io.matrix.knowledge.Evidence::excerpt)
                .filter(text -> normalize(claim.proposition()).equals(normalize(text)))
                .findFirst().orElseThrow();
        // The importer can retain paragraph coordinates. Reconstruct a unique literal
        // sentence offset within that retained paragraph without using the gold offsets.
        int segmentStart = Math.toIntExact(offsets.start());
        int segmentEnd = Math.min(sourceText.length(), Math.toIntExact(offsets.end()));
        String segment = sourceText.substring(segmentStart, segmentEnd);
        int relative = segment.indexOf(excerpt);
        assertTrue(relative >= 0, "Literal evidence is absent from the persisted segment");
        assertEquals(relative, segment.lastIndexOf(excerpt), "Literal evidence span is ambiguous");
        long start = segmentStart + relative;
        return new Provenance(workspace, source, claimId, revision, start, start + excerpt.length(), excerpt);
    }

    private static <T> T assertNotNullValue(T value, String message) {
        assertNotNull(value, message);
        return value;
    }

    private static void match(Provenance expected, Provenance actual) {
        assertEquals(expected.workspace(), actual.workspace());
        assertEquals(expected.source(), actual.source());
        assertEquals(expected.claim(), actual.claim());
        assertEquals(expected.revision(), actual.revision());
        assertEquals(expected.start(), actual.start());
        assertEquals(expected.end(), actual.end());
        assertEquals(normalize(expected.span()), normalize(actual.span()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("[.?!]$", "")
                .replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }
}
