package io.matrix.knowledge.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimAssessment;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.ClaimWarrant;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.CounterexampleReading;
import io.matrix.knowledge.DerivationStatus;
import io.matrix.knowledge.Evidence;
import io.matrix.knowledge.EvidenceRelation;
import io.matrix.knowledge.KnowledgeChanges;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.SourceKind;
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
 * The benchmark of the counterexample slice (RECON-W43.3).
 *
 * <p>What this measures, and what it does not:
 *
 * <ul>
 *   <li>It measures the slice's DECISIONS: whether a counterexample is read
 *       as a refutation, as a missing condition or as inconclusive; whether
 *       a narrowing is proposed when the evidence supports one and withheld
 *       when it does not; and whether the proposed narrowing quotes the
 *       recorded condition rather than inventing one.</li>
 *   <li>It does NOT measure understanding. The slice's decisions are
 *       language-INDEPENDENT by construction — they depend on the evidence
 *       relation and on whether a condition was recorded, not on the words.
 *       The Russian subset therefore measures that the text is carried
 *       through unchanged and the structure is read, not that the system
 *       understands Russian. The dataset says so, and the report says so
 *       again.</li>
 *   <li>It does NOT measure the retrieval, the answering or the promotion:
 *       those are the W42 instruments, and this one is a different
 *       instrument over different cases. The two accuracies are not
 *       comparable.</li>
 * </ul>
 *
 * <p><b>The isolation property.</b> Every case is run in its own store, and
 * the store is built from the case alone. No case can see another case's
 * claims or evidence, so the held-out split is held out in fact and not
 * only in the file.
 */
public final class SliceBenchmark {

    /** The dataset this runner reads. */
    public static final String DATASET = "benchmark/w43-slice-benchmark-v1.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The timestamp every case is stamped with.
     *
     * <p>Named, because a literal in a benchmark is still a literal: the
     * quality gate counts the occurrences and the constant is what the
     * count is about.
     */
    private static final String CASE_TIMESTAMP = "2026-11-02T00:00:00Z";

    /** One observation attached to a claim. */
    public record Observation(String text, String condition, boolean contradicts,
                              String relation, String sourceTitle) {
    }

    /** What the case expects the slice to decide. */
    public record Expect(String standing, String reading, boolean proposal,
                          String proposalContains) {
    }

    /** One case. */
    public record Item(String id, String language, String split, String category,
                       String proposition, String scope, List<Observation> supporting,
                       List<Observation> observations, Expect expect) {
    }

    /** What the slice actually decided. */
    public record Result(String id, String language, String split, String category,
                         boolean standingCorrect, boolean readingCorrect,
                         boolean proposalCorrect, boolean proposalContainsCorrect,
                         String expectedStanding, String actualStanding,
                         String expectedReading, String actualReading) {
    }

    /** The measurement. */
    public record Report(List<Result> results, Map<String, Map<String, Integer>> byLanguage,
                          Map<String, Map<String, Integer>> bySplit,
                          Map<String, Map<String, Integer>> byCategory) {

        /** The number of cases. */
        public int total() {
            return results.size();
        }

        /** The number of cases whose standing was decided correctly. */
        public int standingCorrect() {
            return (int) results.stream().filter(Result::standingCorrect).count();
        }

        /** The number of cases whose reading was decided correctly. */
        public int readingCorrect() {
            return (int) results.stream().filter(Result::readingCorrect).count();
        }

        /** The number of cases whose proposal decision was correct. */
        public int proposalCorrect() {
            return (int) results.stream().filter(Result::proposalCorrect).count();
        }

        /** The number of cases whose proposal quoted the recorded condition. */
        public int proposalContainsCorrect() {
            return (int) results.stream().filter(Result::proposalContainsCorrect).count();
        }

        /** The number of cases where a reading was expected at all. */
        public int readingExpected() {
            return (int) results.stream().filter(r -> r.expectedReading() != null).count();
        }

        /**
         * The number of cases where a reading was expected AND was decided
         * correctly.
         *
         * <p>This is the metric with a denominator: the other count includes
         * the cases where no reading was expected and none was made, which
         * are correct but say nothing about the reading.
         */
        public int readingCorrectAmongExpected() {
            return (int) results.stream()
                    .filter(r -> r.expectedReading() != null && r.readingCorrect()).count();
        }

        /** The number of cases where a proposal was expected at all. */
        public int proposalExpected() {
            return (int) results.stream().filter(r -> r.expectedReading() != null).count();
        }
    }

    private SliceBenchmark() {
    }

    /** The dataset, read from the classpath. */
    public static List<Item> items() throws IOException {
        try (InputStream in = SliceBenchmark.class.getClassLoader().getResourceAsStream(DATASET)) {
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
            List<Observation> supporting = new ArrayList<>();
            for (JsonNode o : node.path("supporting")) {
                supporting.add(observation(o));
            }
            List<Observation> observations = new ArrayList<>();
            for (JsonNode o : node.path("observations")) {
                observations.add(observation(o));
            }
            JsonNode expect = node.path("expect");
            items.add(new Item(
                    node.path("id").asText(),
                    node.path("language").asText(),
                    node.path("split").asText(),
                    node.path("category").asText(),
                    node.path("proposition").asText(),
                    node.path("scope").asText(null),
                    supporting,
                    observations,
                    new Expect(
                            expect.path("standing").asText(null),
                            expect.path("reading").asText(null),
                            expect.path("proposal").asBoolean(false),
                            expect.path("proposalContains").asText(null))));
        }
        return items;
    }

    private static Observation observation(JsonNode node) {
        return new Observation(
                node.path("text").asText(""),
                node.path("condition").asText(null),
                node.path("contradicts").asBoolean(false),
                node.path("relation").asText(null),
                node.path("sourceTitle").asText(null));
    }

    /**
     * Runs the cases, each in its own store.
     *
     * @param items the cases
     * @param directory where the stores are written
     * @return the measurement
     */
    public static Report run(List<Item> items, Path directory) throws IOException {
        List<Result> results = new ArrayList<>();
        int index = 0;
        for (Item item : items) {
            index++;
            ClaimAssessment assessment = assess(item, directory.resolve("case-" + index + ".sqlite"));
            results.add(compare(item, assessment));
        }
        return new Report(results, byLanguage(results), bySplit(results), byCategory(results));
    }

    /** Runs one case in a store of its own. */
    private static ClaimAssessment assess(Item item, Path database) throws IOException {
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString())) {
            String workspace = "slice";
            Concept concept = new Concept("c-" + item.id(), workspace, "the subject of the case",
                    "the concept the case is about", "benchmark", CASE_TIMESTAMP);
            Claim claim = new Claim("cl-" + item.id(), workspace, concept.id(),
                    item.proposition(),
                    item.scope() == null ? "" : item.scope(),
                    "a benchmark case",
                    ClaimStatus.HYPOTHESIS, 0.0, 1, null,
                    "manual", "manual", ClaimWarrant.NONE, DerivationStatus.NOT_DERIVED,
                    CASE_TIMESTAMP, CASE_TIMESTAMP);
            workbench.storeForValidation().applyChanges(workspace,
                    new KnowledgeChanges(List.of(), List.of(claim), List.of(), List.of(), List.of()),
                    List.of(concept));

            for (Observation observation : item.supporting()) {
                write(workbench, workspace, item.id(), claim.id(), observation,
                        EvidenceRelation.SUPPORTS);
            }
            for (Observation observation : item.observations()) {
                EvidenceRelation relation = observation.relation() != null
                        ? EvidenceRelation.valueOf(observation.relation())
                        : (observation.contradicts() ? EvidenceRelation.CONTRADICTS
                                : EvidenceRelation.EXEMPLIFIES);
                write(workbench, workspace, item.id(), claim.id(), observation, relation);
            }
            return workbench.assessClaim(workspace, claim.id());
        }
    }

    /** Writes one observation and its evidence link. */
    private static void write(KnowledgeWorkbench workbench, String workspace, String itemId,
                              String claimId, Observation observation, EvidenceRelation relation) {
        String sourceId = "src-" + itemId + "-" + Math.abs(observation.text().hashCode());
        Source source = new Source(sourceId, workspace, SourceKind.OBSERVATION,
                observation.sourceTitle() == null ? "an observation" : observation.sourceTitle(),
                observation.condition(), 40, 60, observation.text(),
                CASE_TIMESTAMP, "hash-" + Math.abs(observation.text().hashCode()),
                null, null, "observation");
        Evidence evidence = new Evidence("ev-" + sourceId, workspace, claimId, sourceId, relation,
                observation.text(), 50, CASE_TIMESTAMP);
        workbench.storeForValidation().applyChanges(workspace,
                new KnowledgeChanges(List.of(source), List.of(), List.of(evidence), List.of(),
                        List.of()));
    }

    /** Compares what was decided with what was expected. */
    private static Result compare(Item item, ClaimAssessment assessment) {
        String actualStanding = assessment.standing() == null ? null : assessment.standing().name();
        String actualReading = assessment.counterexampleReading() == null
                ? null : assessment.counterexampleReading().name();
        boolean standingCorrect = item.expect().standing() == null
                ? actualStanding == null
                : item.expect().standing().equals(actualStanding);
        boolean readingCorrect = item.expect().reading() == null
                ? actualReading == null
                : item.expect().reading().equals(actualReading);
        boolean proposalCorrect = assessment.hasProposal() == item.expect().proposal();
        boolean containsCorrect = item.expect().proposalContains() == null
                ? true
                : (assessment.proposedRefinement() != null
                        && assessment.proposedRefinement().contains(item.expect().proposalContains()));
        return new Result(item.id(), item.language(), item.split(), item.category(),
                standingCorrect, readingCorrect, proposalCorrect, containsCorrect,
                item.expect().standing(), actualStanding,
                item.expect().reading(), actualReading);
    }

    /** The results per language. */
    private static Map<String, Map<String, Integer>> byLanguage(List<Result> results) {
        return group(results, Result::language);
    }

    /** The results per split. */
    private static Map<String, Map<String, Integer>> bySplit(List<Result> results) {
        return group(results, Result::split);
    }

    /** The results per category. */
    private static Map<String, Map<String, Integer>> byCategory(List<Result> results) {
        return group(results, Result::category);
    }

    private static Map<String, Map<String, Integer>> group(List<Result> results,
                                                            java.util.function.Function<Result, String> key) {
        Map<String, Map<String, Integer>> grouped = new TreeMap<>();
        for (Result result : results) {
            Map<String, Integer> counts = grouped.computeIfAbsent(key.apply(result),
                    k -> new LinkedHashMap<>());
            counts.merge("cases", 1, Integer::sum);
            counts.merge("standingCorrect", result.standingCorrect() ? 1 : 0, Integer::sum);
            counts.merge("readingCorrect", result.readingCorrect() ? 1 : 0, Integer::sum);
            counts.merge("proposalCorrect", result.proposalCorrect() ? 1 : 0, Integer::sum);
        }
        return grouped;
    }
}
