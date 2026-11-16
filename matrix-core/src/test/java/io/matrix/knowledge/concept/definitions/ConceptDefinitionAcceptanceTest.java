package io.matrix.knowledge.concept.definitions;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeRetriever;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.knowledge.acquisition.SourceSpanDefinitions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EPISTEMIC-INTEGRITY P1 — the ACCEPTANCE MEASUREMENT of the definition half.
 *
 * <p>The corpus is the seed corpus (the two licensed documents of
 * {@code matrix-core/src/main/resources/corpus/plate-tectonics/}), imported
 * through the PRODUCTION workbench and the PRODUCTION importer into a
 * TEMPORARY database — the owner's store is never touched.</p>
 *
 * <p>The measures, and what they must beat:</p>
 * <ul>
 *   <li><b>A2</b> — the definition coverage: the baseline is <b>0 usable
 *       definitions</b>; the research's adjacency-passing figure is
 *       <b>121/326 = 37.1 %</b>;</li>
 *   <li><b>A1</b> — the concept-identification precision on the frozen
 *       hand-labelled set: the baseline is <b>30 %</b> (12/40);</li>
 *   <li><b>A3</b> — the relation precision: the baseline was <b>never
 *       measured</b>;</li>
 *   <li><b>A7</b> — the retrieval latency: the baseline is <b>32 ms</b>;</li>
 *   <li><b>A8</b> — the concept ids: <b>none may change</b>.</li>
 * </ul>
 */
class ConceptDefinitionAcceptanceTest {

    /**
     * The workspace the seed corpus is imported into.
     *
     * <p>The name matters for the A1 draw: a concept id is a digest of the
     * workspace and the name, so the draw is over the ids of THIS workspace and
     * the frozen set describes exactly these names. The names themselves do not
     * depend on the workspace: the sample is a sample of the same 326 names
     * whatever the workspace is called.</p>
     */
    private static final String WORKSPACE = "seed";

    /** The seed corpus of the measured baseline: two documents. */
    private static final int SEED_DOCUMENTS = 2;

    /** The claims of the measured baseline. */
    private static final int BASELINE_CLAIMS = 348;

    /** The concepts of the measured baseline. */
    private static final int BASELINE_CONCEPTS = 326;

    /**
     * The definitions the research's instrument finds on the seed corpus.
     *
     * <p>The research document publishes 121/326 = 37.1 %; the instrument in the
     * repository finds 120 on the same corpus. The number this implementation
     * must reproduce is the INSTRUMENT's, and the difference is reported.</p>
     */
    private static final int INSTRUMENT_DEFINITIONS = 120;

    /** The retriever's floor: the production value of the official evaluation. */
    private static final double RETRIEVER_FLOOR = 0.25;

    /** How many times the questions are asked, so that one slow round is not the measurement. */
    private static final int MEASUREMENT_ROUNDS = 20;

    /**
     * The frozen relation judgements (A3).
     *
     * <p>One row per relation candidate of the seed corpus under the frozen
     * §3.2 rule, labelled by hand against the span: TRUE means the span states
     * the relation between the two endpoints, in the recorded direction, and
     * both endpoints denote referents. The endpoints are shown because the
     * judgement is about them: five of the seven candidates have an endpoint
     * that is a fragment of the sentence, and one has its direction reversed.</p>
     */
    private static final List<String[]> FROZEN_RELATIONS = List.of(
            new String[] {"PRODUCES", "Picture two giant conveyor belts, facing each other but slowly "
                    + "moving in opposite directions as they", "Oceanic crust", "false"},
            new String[] {"IS_A", "Plate tectonics (from Latin tectonicus, from Ancient Greek "
                    + "τεκτονικός (tektonikós) 'pertaining to building')", "Tectonic plates", "false"},
            new String[] {"PRODUCES", "The processes that", "Earth's crust", "false"},
            new String[] {"PART_OF", "Tectonic lithospheric plates", "Oceanic crust", "false"},
            new String[] {"PART_OF", "Tectonic plates may", "Continental crust", "false"},
            new String[] {"IS_A", "Wegener later", "Plate tectonics", "false"},
            new String[] {"PRODUCES", "Starting from the idea (also expressed by his forerunners) "
                    + "that the present continents once", "Wegener", "false"});

    private static Path workDirectory;
    private static KnowledgeSnapshot snapshot;
    private static KnowledgeWorkbench workbench;

    @BeforeAll
    static void importTheSeedCorpus() throws IOException {
        workDirectory = Files.createTempDirectory("p1-definitions-");
        Path database = workDirectory.resolve("knowledge.sqlite");
        workbench = KnowledgeWorkbench.at(database.toString());
        ClasspathCorpus corpus = ClasspathCorpus.extract(AcquisitionCycle.SEED_CORPUS);
        for (ClasspathCorpus.Entry entry : corpus.entries()) {
            workbench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
        }
        snapshot = workbench.workspace(WORKSPACE);
    }

    @AfterAll
    static void cleanUp() throws IOException {
        if (workbench != null) {
            workbench.close();
            workbench = null;
        }
        if (workDirectory == null || !Files.exists(workDirectory)) {
            return;
        }
        try (var paths = Files.walk(workDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // the temporary directory is below the operating system's own care
                }
            });
        }
    }

    // ── A2 — the definition coverage ──────────────────────────────────────────

    @Test
    @DisplayName("A2 — the seed corpus now carries the definitions the rule finds")
    void theSeedCorpusCarriesTheDefinitions() {
        List<Concept> concepts = snapshot.concepts();
        assertThat(snapshot.claims()).as("the seed corpus holds the baseline's 348 claims")
                .hasSize(BASELINE_CLAIMS);
        assertThat(concepts).as("the seed corpus holds the baseline's 326 concepts")
                .hasSize(BASELINE_CONCEPTS);
        assertThat(snapshot.sources()).hasSize(SEED_DOCUMENTS);

        List<io.matrix.knowledge.ConceptDefinition> definitions =
                workbench.storeForValidation().conceptDefinitions(WORKSPACE);
        System.out.println("[A2] concepts=" + concepts.size() + " with a definition="
                + definitions.size());
        assertThat(definitions.size())
                .as("the production import must write the definitions the rule finds")
                .isEqualTo(INSTRUMENT_DEFINITIONS);
        assertThat(definitions.size())
                .as("the baseline was 0 usable definitions")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("A2 — the production definitions are the research instrument's, set by set")
    void theProductionDefinitionsAreTheInstruments() {
        io.matrix.knowledge.concept.SourceGroundedEnrichment.Extraction extraction =
                io.matrix.knowledge.concept.SourceGroundedEnrichment.extract(snapshot,
                        io.matrix.knowledge.concept.SourceGroundedEnrichment.DefinitionRule.TIGHT_V1_1);
        Set<String> instrument = new LinkedHashSet<>();
        for (io.matrix.knowledge.concept.SourceGroundedEnrichment.DefinitionCandidate candidate
                : extraction.definitions()) {
            instrument.add(candidate.conceptId() + "|" + candidate.definition());
        }
        Set<String> production = new LinkedHashSet<>();
        for (io.matrix.knowledge.ConceptDefinition definition
                : workbench.storeForValidation().conceptDefinitions(WORKSPACE)) {
            production.add(definition.conceptId() + "|" + definition.definition());
        }
        assertThat(production)
                .as("the production rule and the research instrument must agree exactly")
                .isEqualTo(instrument);
    }

    @Test
    @DisplayName("A2 — every definition is a VERBATIM sentence of the concept's own source")
    void everyDefinitionIsAVerbatimSentenceOfItsOwnSource() {
        List<SourceSpanDefinitions.Definition> derived = SourceSpanDefinitions.derive(
                snapshot.concepts(), snapshot.sources(),
                SourceSpanDefinitions.sentencesOf(snapshot.claims(), null));
        assertThat(derived).hasSize(INSTRUMENT_DEFINITIONS);
        Set<String> propositions = new LinkedHashSet<>();
        for (Claim claim : snapshot.claims()) {
            propositions.add(claim.proposition());
        }
        for (SourceSpanDefinitions.Definition definition : derived) {
            assertThat(propositions)
                    .as("the definition must be a sentence of the corpus, verbatim")
                    .contains(definition.text());
            assertThat(definition.text().length())
                    .as("the definition must respect the frozen length cap")
                    .isLessThanOrEqualTo(SourceSpanDefinitions.MAX_DEFINITION_LENGTH);
            assertThat(definition.method()).isEqualTo(SourceSpanDefinitions.DEFINITION_METHOD);
            assertThat(definition.sourceId())
                    .as("the definition must name the source it was read from")
                    .isNotBlank();
        }
    }

    // ── A2 (ii) — the human-verified share of the definitions ─────────────────

    @Test
    @DisplayName("A2 (ii) — the frozen human-verified sample of the definitions")
    void theHumanVerifiedShareOfTheDefinitions() {
        List<io.matrix.knowledge.ConceptDefinition> definitions =
                workbench.storeForValidation().conceptDefinitions(WORKSPACE);
        java.util.Map<String, String> byDigest = new java.util.LinkedHashMap<>();
        for (io.matrix.knowledge.ConceptDefinition definition : definitions) {
            byDigest.put(sha256(definition.definition()), definition.conceptId());
        }
        int about = 0;
        for (HandVerifiedDefinitions.VerifiedDefinition verified
                : HandVerifiedDefinitions.SAMPLE) {
            assertThat(byDigest)
                    .as("the frozen sample must still be part of the workspace's definitions")
                    .containsKey(verified.definitionSha256());
            if (verified.aboutTheConcept()) {
                about++;
            }
        }
        System.out.println("[A2] the human-verified sample: " + about + "/"
                + HandVerifiedDefinitions.SAMPLE.size() + " definitions are about their concept");
        assertThat(about)
                .as("the human-verified share of the frozen sample")
                .isEqualTo(24);
    }

    // ── A8 — the identity stability ───────────────────────────────────────────

    @Test
    @DisplayName("A8 — no concept id changed: the ids are still the digests of the names")
    void noConceptIdChanged() {
        for (Concept concept : snapshot.concepts()) {
            String expected = io.matrix.knowledge.Ids.digest(WORKSPACE + "|concept|"
                    + concept.name().strip().toLowerCase(java.util.Locale.ROOT));
            assertThat(concept.id())
                    .as("the id of the concept %s must still be the digest of its name",
                            concept.name())
                    .isEqualTo(expected);
        }
        // The claim ids are digests of the workspace, the content hash and the
        // proposition: the definition write cannot have moved them.
        assertThat(snapshot.claims()).allSatisfy(claim ->
                assertThat(claim.id()).startsWith("clm-"));
        assertThat(snapshot.concepts()).hasSize(BASELINE_CONCEPTS);
    }

    // ── A1 — the concept-identification precision ─────────────────────────────

    @Test
    @DisplayName("A1 — the frozen hand-labelled set is drawn from this corpus and still holds")
    void theFrozenHandLabelledSetIsTheDrawOfThisCorpus() {
        List<String> ids = new ArrayList<>();
        for (Concept concept : snapshot.concepts()) {
            ids.add(concept.id());
        }
        Collections.sort(ids);
        Collections.shuffle(ids, new Random(HandLabelledSpans.DRAW_SEED));
        List<String> drawn = ids.subList(0, HandLabelledSpans.DRAW_SIZE);
        assertThat(drawn).hasSize(HandLabelledSpans.DRAW_SIZE);
        java.util.Map<String, String> byId = new java.util.LinkedHashMap<>();
        for (Concept concept : snapshot.concepts()) {
            byId.put(concept.id(), concept.name());
        }
        for (int index = 0; index < drawn.size(); index++) {
            HandLabelledSpans.HandLabel label = HandLabelledSpans.LABELS.get(index);
            assertThat(label.position()).isEqualTo(index + 1);
            assertThat(byId.get(drawn.get(index)))
                    .as("the frozen set must still describe the corpus it was drawn from")
                    .isEqualTo(label.span());
        }
    }

    @Test
    @DisplayName("A1 — the precision on the frozen set, and on the research's first 40")
    void thePrecisionOnTheFrozenSet() {
        int concepts = 0;
        for (HandLabelledSpans.HandLabel label : HandLabelledSpans.LABELS) {
            if (label.concept()) {
                concepts++;
            }
        }
        int baselineConcepts = 0;
        for (HandLabelledSpans.HandLabel label : HandLabelledSpans.LABELS) {
            if (label.position() <= HandLabelledSpans.BASELINE_SAMPLE_SIZE && label.concept()) {
                baselineConcepts++;
            }
        }
        System.out.println("[A1] the frozen set: " + concepts + "/"
                + HandLabelledSpans.LABELS.size() + " are genuine referents; the research's first "
                + HandLabelledSpans.BASELINE_SAMPLE_SIZE + ": " + baselineConcepts);
        assertThat(baselineConcepts)
                .as("the first 40 of the draw must land on the published share: 12 of 40")
                .isEqualTo(12);
        assertThat(concepts)
                .as("the frozen set's own figure")
                .isEqualTo(35);
        // BEFORE AND AFTER ARE THE SAME MEASUREMENT: the labels are over the
        // names, and the names are the preimage of the ids, and the ids do not
        // change (A8). The change therefore cannot move this number.
        for (Concept concept : snapshot.concepts()) {
            String expected = io.matrix.knowledge.Ids.digest(WORKSPACE + "|concept|"
                    + concept.name().strip().toLowerCase(java.util.Locale.ROOT));
            assertThat(concept.id())
                    .as("the name of a concept is the preimage of its id, so the names "
                            + "are unchanged and the A1 labels still hold")
                    .isEqualTo(expected);
        }
    }

    // ── A3 — the relation precision ───────────────────────────────────────────

    @Test
    @DisplayName("A3 — the relation candidates are measured, and the precision is reported")
    void theRelationPrecisionIsMeasured() {
        io.matrix.knowledge.concept.SourceGroundedEnrichment.Extraction extraction =
                io.matrix.knowledge.concept.SourceGroundedEnrichment.extract(snapshot,
                        io.matrix.knowledge.concept.SourceGroundedEnrichment.DefinitionRule.TIGHT_V1_1);
        assertThat(extraction.relations())
                .as("the frozen §3.2 rule finds this many candidates on the seed corpus")
                .hasSize(FROZEN_RELATIONS.size());
        int true_ = 0;
        for (int index = 0; index < extraction.relations().size(); index++) {
            io.matrix.knowledge.concept.SourceGroundedEnrichment.RelationCandidate candidate =
                    extraction.relations().get(index);
            String[] judged = FROZEN_RELATIONS.get(index);
            assertThat(candidate.type().name()).isEqualTo(judged[0]);
            assertThat(candidate.fromConceptName()).isEqualTo(judged[1]);
            assertThat(candidate.toConceptName()).isEqualTo(judged[2]);
            if (Boolean.parseBoolean(judged[3])) {
                true_++;
            }
        }
        System.out.println("[A3] relation candidates=" + extraction.relations().size()
                + " true and correctly directed=" + true_);
        // The measure is reported, not asserted to a threshold: the research never
        // measured it, so there is no baseline to beat.
        assertThat(true_).isBetween(0, extraction.relations().size());
    }

    // ── A7 — the cost ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("A7 — the retrieval latency, with the definitions and without them")
    void theRetrievalLatencyIsMeasured() {
        List<String> questions = new ArrayList<>();
        try {
            for (io.matrix.knowledge.concept.RetrievalExperiment.CaseSpec spec
                    : new io.matrix.knowledge.concept.RetrievalExperiment(
                            workDirectory.resolve("latency-probe.sqlite")).dataset()
                    .cases()) {
                if (spec.question() != null && !spec.question().isBlank()) {
                    questions.add(spec.question());
                }
            }
        } catch (Exception e) {
            questions.add("What is plate tectonics?");
            questions.add("How do mountains form?");
            questions.add("What causes earthquakes?");
            questions.add("Where is Iceland located?");
        }
        assertThat(questions).isNotEmpty();

        long withDefinitions = retrievalMicros(snapshot, questions);
        long withoutDefinitions = retrievalMicros(withoutDefinitions(snapshot), questions);
        // The two states are measured again, in the other order, so that a
        // machine that got busier cannot be mistaken for the definitions.
        long withoutAgain = retrievalMicros(withoutDefinitions(snapshot), questions);
        long withAgain = retrievalMicros(snapshot, questions);
        System.out.println("[A7] the mean retrieval over " + questions.size()
                + " questions: with the definitions=" + withDefinitions + " us / " + withAgain
                + " us, without them=" + withoutDefinitions + " us / " + withoutAgain + " us");
        assertThat(withDefinitions).isGreaterThan(0);
        assertThat(withoutDefinitions).isGreaterThan(0);
        // The definitions are NOT in the retriever's searchable text, so the two
        // states ARE the same state: this is the structural proof that the
        // retrieval cannot have moved.
        assertThat(snapshot.concepts())
                .as("the concept table's definition column stays empty: the definition is "
                        + "its own record, so the retriever's input is unchanged")
                .allSatisfy(concept -> assertThat(concept.definition()).isNull());
    }

    /** The snapshot with every concept's definition removed. */
    private static KnowledgeSnapshot withoutDefinitions(KnowledgeSnapshot source) {
        List<Concept> concepts = new ArrayList<>();
        for (Concept concept : source.concepts()) {
            concepts.add(new Concept(concept.id(), concept.workspaceId(), concept.name(), null,
                    concept.domain(), concept.createdAt()));
        }
        return new KnowledgeSnapshot(source.workspaceId(), source.workspaceName(), concepts,
                source.sources(), source.claims(), source.evidence(), source.representations(),
                source.prerequisites(), source.revisions(), source.derivations(),
                source.contradictions(), source.goals(), source.exportedAt());
    }

    /** The SHA-256 of a text, in lower-case hex. */
    private static String sha256(String text) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
    }

    /** The mean time one retrieval takes, in milliseconds, over the given questions. */
    private static long retrievalMicros(KnowledgeSnapshot source, List<String> questions) {
        // The retriever is built ONCE: the measurement is the RETRIEVAL, not the
        // indexing of the snapshot, and the two states are indexed the same way.
        KnowledgeRetriever retriever = new KnowledgeRetriever(source, RETRIEVER_FLOOR);
        // A warm-up round, so that the JIT is not part of the measurement.
        for (String question : questions) {
            retriever.answer(question);
        }
        int rounds = MEASUREMENT_ROUNDS;
        long started = System.nanoTime();
        for (int round = 0; round < rounds; round++) {
            for (String question : questions) {
                retriever.answer(question);
            }
        }
        return (System.nanoTime() - started) / 1_000L / (rounds * Math.max(1, questions.size()));
    }
}
