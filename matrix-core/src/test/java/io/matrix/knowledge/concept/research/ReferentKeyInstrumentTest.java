package io.matrix.knowledge.concept.research;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EPISTEMIC-INTEGRITY P1 — the referent key: the instrument.
 *
 * <p><b>What this test is.</b> A measurement of the <b>resolution and alias policy</b>
 * of the referent key — the part the research left open
 * ({@code docs-v2/engineering/EPISTEMIC-CONCEPT-IDENTITY.md} §6.3: <i>"the alias layer
 * is where correctness will live, and it is the part that must be measured on
 * human-verified examples"</i>).</p>
 *
 * <p><b>What it does NOT do.</b> It migrates nothing, renames nothing and changes no
 * id. The concept id is referenced by ten tables and SQLite declares no foreign keys,
 * so a partial migration would be invisible; the identity half is owner-gated. This
 * test reads a <b>temporary</b> database, resolves the claims into referents and
 * reports the numbers.</p>
 *
 * <p><b>The measurements.</b>
 * <ol>
 *   <li>the baseline is reproduced (the seed corpus: 348 claims, 326 concepts), so the
 *       instrument is measuring the same corpus the research measured;</li>
 *   <li>the key's <b>fragmentation</b>: how many referents the resolution yields over
 *       the seed corpus and over the wider nine-document corpus, against the current
 *       splitter's 326 and 813;</li>
 *   <li>the <b>A1 precision</b> of the policy on the frozen hand-labelled set of 150
 *       spans (the instrument of the A1 measure), with the current splitter's number
 *       beside it;</li>
 *   <li>the <b>four properties</b> the directive names — the same word in different
 *       contexts, the synonym, the paraphrase, the homonym — on a hand-labelled set
 *       of the corpus's own cases;</li>
 *   <li>the <b>definition coverage</b> under the key, against the current 27/326.</li>
 * </ol>
 *
 * <p><b>The write-up</b> is {@code docs-v2/engineering/EPISTEMIC-REFERENT-KEY.md}.</p>
 */
@DisplayName("P1 — the referent key: the resolution and alias policy")
class ReferentKeyInstrumentTest {

    /** The workspace the seed corpus is imported into. */
    private static final String SEED_WORKSPACE = "plate-tectonics-referent-key";

    /** The workspace the wider corpus is imported into. */
    private static final String WIDE_WORKSPACE = "plate-tectonics-referent-key-wide";

    /** The seed corpus: the two licensed documents of the measured baseline. */
    private static final String SEED_CORPUS = AcquisitionCycle.SEED_CORPUS;

    /** The research pool: the seven further documents of the wider corpus. */
    private static final String RESEARCH_POOL = AcquisitionCycle.RESEARCH_POOL;

    /** The classpath of the frozen A1 instrument. */
    private static final String HAND_LABELLED_CLASS =
            "io.matrix.knowledge.concept.definitions.HandLabelledSpans";

    /** The field of the frozen A1 instrument that holds the labels. */
    private static final String HAND_LABELLED_FIELD = "LABELS";

    private static Path workDirectory;
    private static Imported seedImported;
    private static Imported wideImported;
    private static KnowledgeSnapshot seedSnapshot;
    private static KnowledgeSnapshot wideSnapshot;
    private static long seedImportMillis;
    private static long wideImportMillis;

    /**
     * What an import produced: the snapshot, the recorded definitions and the time.
     *
     * @param snapshot the workspace's snapshot
     * @param definitions the recorded definitions of the workspace
     * @param millis how long the import took
     */
    private record Imported(KnowledgeSnapshot snapshot,
                            List<io.matrix.knowledge.ConceptDefinition> definitions,
                            long millis) {
    }

    @BeforeAll
    static void measure() throws IOException {
        workDirectory = Files.createTempDirectory("referent-key-");
        seedImported = importCorpus(SEED_WORKSPACE, List.of(SEED_CORPUS));
        wideImported = importCorpus(WIDE_WORKSPACE, List.of(SEED_CORPUS, RESEARCH_POOL));
        seedSnapshot = seedImported.snapshot();
        wideSnapshot = wideImported.snapshot();
    }

    @AfterAll
    static void cleanUp() throws IOException {
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

    // ── 1. the baseline ───────────────────────────────────────────────────────

    @Test
    @DisplayName("the production importer reproduces the baseline the research measured")
    void theBaselineIsReproduced() {
        assertThat(seedSnapshot.claims()).hasSize(348);
        assertThat(seedSnapshot.concepts()).hasSize(326);
        assertThat(seedSnapshot.sources()).hasSize(2);
        assertThat(wideSnapshot.claims()).hasSize(888);
        assertThat(wideSnapshot.concepts()).hasSize(813);
        System.out.printf("[referent-key] the seed import took %d ms, the wider import %d ms%n",
                seedImportMillis, wideImportMillis);
    }

    // ── 2. the fragmentation ──────────────────────────────────────────────────

    @Test
    @DisplayName("the key's fragmentation: the referents against the current concepts")
    void theFragmentationIsMeasured() {
        ReferentResolver.Result seed = ReferentResolver.resolve(seedSnapshot.claims(),
                seedSnapshot.concepts());
        ReferentResolver.Result wide = ReferentResolver.resolve(wideSnapshot.claims(),
                wideSnapshot.concepts());

        System.out.printf("[referent-key] the seed: claims=%d accepted=%d rejected=%d "
                        + "referents=%d (the current splitter: %d concepts)%n",
                seed.claims(), seed.accepted(), seed.rejected(), seed.clusters(),
                seedSnapshot.concepts().size());
        System.out.printf("[referent-key] the wider: claims=%d accepted=%d rejected=%d "
                        + "referents=%d (the current splitter: %d concepts)%n",
                wide.claims(), wide.accepted(), wide.rejected(), wide.clusters(),
                wideSnapshot.concepts().size());
        for (ReferentPolicy.Strictness level : ReferentPolicy.Strictness.values()) {
            ReferentResolver.Result strictSeed = ReferentResolver.resolve(seedSnapshot.claims(),
                    seedSnapshot.concepts(), true, level);
            ReferentResolver.Result strictWide = ReferentResolver.resolve(wideSnapshot.claims(),
                    wideSnapshot.concepts(), true, level);
            System.out.printf("[referent-key] the strictness %s: the seed accepted=%d "
                            + "rejected=%d referents=%d; the wider accepted=%d rejected=%d "
                            + "referents=%d%n",
                    level, strictSeed.accepted(), strictSeed.rejected(), strictSeed.clusters(),
                    strictWide.accepted(), strictWide.rejected(), strictWide.clusters());
            System.out.println("[referent-key] the rejections (the seed, " + level + "): "
                    + strictSeed.rejections());
        }
        // The CONTROL: the fragmentation the source-anchoring alone produces, without
        // the reject-only rule. The two causes are told apart here.
        ReferentResolver.Result seedAll = ReferentResolver.resolve(seedSnapshot.claims(),
                seedSnapshot.concepts(), false);
        ReferentResolver.Result wideAll = ReferentResolver.resolve(wideSnapshot.claims(),
                wideSnapshot.concepts(), false);
        System.out.printf("[referent-key] the control (the source-anchoring alone, no rejection): "
                        + "the seed %d referents, the wider %d referents%n",
                seedAll.clusters(), wideAll.clusters());
        System.out.println("[referent-key] the rejections (the seed): " + seed.rejections());
        System.out.println("[referent-key] the rejections (the wider): " + wide.rejections());

        // The referents never exceed the claims: a referent holds at least one claim.
        assertThat(seedAll.clusters()).as("the control resolves every claim")
                .isGreaterThan(seed.clusters());
        assertThat(seed.clusters()).isLessThanOrEqualTo(seed.accepted());
        assertThat(wide.clusters()).isLessThanOrEqualTo(wide.accepted());
        // The resolution never loses a claim: every accepted claim is in exactly one
        // referent.
        Set<String> clustered = new LinkedHashSet<>();
        for (ReferentResolver.Cluster cluster : seed.clustersDetail()) {
            clustered.addAll(cluster.claimIds());
        }
        assertThat(clustered).hasSize(seed.accepted());
    }

    // ── 3. the A1 precision on the frozen 150 ─────────────────────────────────

    @Test
    @DisplayName("the policy's precision on the frozen hand-labelled 150")
    void thePrecisionOnTheFrozenSetIsMeasured() {
        List<HandLabel> labels = handLabels();
        assertThat(labels).hasSize(150);

        // The current splitter accepts every concept name it produces, so its
        // precision over this set is the share of the labels that are true.
        long labelledTrue = labels.stream().filter(HandLabel::concept).count();
        assertThat(labelledTrue).as("the frozen set's share of genuine referents")
                .isEqualTo(35L);

        double splitterPrecision = (double) labelledTrue / labels.size();
        for (ReferentPolicy.Strictness level : ReferentPolicy.Strictness.values()) {
            int acceptedByPolicy = 0;
            int acceptedAndTrue = 0;
            int acceptedAndFalse = 0;
            int rejectedTrue = 0;
            List<String> falsePositives = new ArrayList<>();
            List<String> wronglyRejected = new ArrayList<>();
            for (HandLabel label : labels) {
                if (!ReferentPolicy.accepted(label.span(), level)) {
                    if (label.concept()) {
                        rejectedTrue++;
                        wronglyRejected.add(label.span() + "  ["
                                + ReferentPolicy.rejection(label.span(), level) + "]");
                    }
                    continue;
                }
                acceptedByPolicy++;
                if (label.concept()) {
                    acceptedAndTrue++;
                } else {
                    acceptedAndFalse++;
                    falsePositives.add(label.span() + "  ["
                            + ReferentPolicy.rejection(label.span(), level) + "]");
                }
            }
            double policyPrecision = acceptedByPolicy == 0 ? 0
                    : (double) acceptedAndTrue / acceptedByPolicy;
            double[] interval = wilson(acceptedAndTrue, acceptedByPolicy);
            System.out.printf("[referent-key] the frozen 150 (%s): the splitter accepts all of "
                            + "them, precision %.4f (%d/%d); the policy accepts %d, precision "
                            + "%.4f (%d/%d) [%.4f, %.4f], %d false positives, %d true referents "
                            + "rejected%n",
                    level, splitterPrecision, labelledTrue, labels.size(), acceptedByPolicy,
                    policyPrecision, acceptedAndTrue, acceptedByPolicy, interval[1], interval[2],
                    acceptedAndFalse, rejectedTrue);
            System.out.println("[referent-key] the false positives (" + level + "): "
                    + falsePositives);
            System.out.println("[referent-key] the wrongly rejected (" + level + "): "
                    + wronglyRejected);
            assertThat(acceptedByPolicy).as("the policy must reject something")
                    .isLessThan(labels.size());
            assertThat(policyPrecision).as("the policy's precision over what it accepts")
                    .isGreaterThan(splitterPrecision);
            assertThat(interval[1]).as("the policy's interval must lie above the splitter's")
                    .isGreaterThan(wilson((int) labelledTrue, labels.size())[2]);
        }
    }

    // ── 4. the four properties ────────────────────────────────────────────────

    @Test
    @DisplayName("the four properties the directive names, on the corpus's own cases")
    void theFourPropertiesAreMeasured() {
        List<PropertyCase> cases = PropertyCase.FROZEN;
        int sameWordDistinct = 0;
        int synonymMerged = 0;
        int paraphraseMerged = 0;
        int homonymKeptDistinct = 0;
        List<String> failures = new ArrayList<>();
        for (PropertyCase testCase : cases) {
            boolean merged = mergedByPolicy(testCase);
            boolean expectedMerged = testCase.expectedSameReferent();
            if (merged == expectedMerged) {
                switch (testCase.property()) {
                    case SAME_WORD_DIFFERENT_CONTEXT -> sameWordDistinct++;
                    case SYNONYM -> synonymMerged++;
                    case PARAPHRASE -> paraphraseMerged++;
                    case HOMONYM -> homonymKeptDistinct++;
                    default -> {
                    }
                }
            } else {
                failures.add(testCase.property() + ": " + testCase.first() + " / "
                        + testCase.second() + " -> merged=" + merged);
            }
        }
        System.out.printf("[referent-key] the properties: the same word in different contexts "
                        + "%d/%d, the synonym %d/%d, the paraphrase %d/%d, the homonym %d/%d%n",
                sameWordDistinct, count(Property.SAME_WORD_DIFFERENT_CONTEXT), synonymMerged, count(Property.SYNONYM),
                paraphraseMerged, count(Property.PARAPHRASE), homonymKeptDistinct, count(Property.HOMONYM));
        System.out.println("[referent-key] the property failures: " + failures);
        assertThat(failures).as("every labelled property case must be decided as labelled")
                .isEmpty();
    }

    // ── 5. the definition coverage ────────────────────────────────────────────

    @Test
    @DisplayName("the definition coverage under the key, against the current 27/326")
    void theDefinitionCoverageIsMeasured() {
        ReferentResolver.Result seed = ReferentResolver.resolve(seedSnapshot.claims(),
                seedSnapshot.concepts());
        Map<String, Set<String>> claimsByConcept = new LinkedHashMap<>();
        for (Claim claim : seedSnapshot.claims()) {
            claimsByConcept.computeIfAbsent(claim.conceptId(), key -> new LinkedHashSet<>())
                    .add(claim.id());
        }
        int referentsWithADefinition = 0;
        // The definitions live in their OWN table (the concept_definition table of the
        // definition half); the concept.definition COLUMN is deliberately empty, because
        // putting the definitions into the retriever's searchable text was measured to
        // degrade the refusal correctness.
        Set<String> conceptsWithADefinition = new LinkedHashSet<>();
        for (io.matrix.knowledge.ConceptDefinition definition : seedImported.definitions()) {
            conceptsWithADefinition.add(definition.conceptId());
        }
        int definitions = conceptsWithADefinition.size();
        for (ReferentResolver.Cluster cluster : seed.clustersDetail()) {
            for (String claimId : cluster.claimIds()) {
                String conceptId = conceptIdOf(claimId);
                if (conceptId != null && conceptsWithADefinition.contains(conceptId)) {
                    referentsWithADefinition++;
                    break;
                }
            }
        }
        System.out.printf("[referent-key] the definitions: %d concepts carry one now; under the "
                        + "key %d of the %d referents carry one%n",
                definitions, referentsWithADefinition, seed.clusters());
        for (ReferentPolicy.Strictness level : ReferentPolicy.Strictness.values()) {
            ReferentResolver.Result strict = ReferentResolver.resolve(seedSnapshot.claims(),
                    seedSnapshot.concepts(), true, level);
            int withOne = 0;
            for (ReferentResolver.Cluster cluster : strict.clustersDetail()) {
                for (String claimId : cluster.claimIds()) {
                    String conceptId = conceptIdOf(claimId);
                    if (conceptId != null && conceptsWithADefinition.contains(conceptId)) {
                        withOne++;
                        break;
                    }
                }
            }
            System.out.printf("[referent-key] the definitions (%s): %d of the %d referents carry "
                            + "one (%.4f)%n",
                    level, withOne, strict.clusters(),
                    strict.clusters() == 0 ? 0 : (double) withOne / strict.clusters());
        }
        // The corrected rule (the name must be the SUBJECT of the sentence, a denial is
        // not a definition, and a name that is not a referent is rejected) finds 27
        // definitions, not the 120 the earlier, weaker rule found. See
        // docs-v2/engineering/EPISTEMIC-CONCEPT-DEFINITIONS.md section 10.
        assertThat(definitions).as("the production import writes the definitions")
                .isEqualTo(27);
    }

    // ── 6. the relations ──────────────────────────────────────────────────────

    @Test
    @DisplayName("the relation candidates that survive the policy")
    void theRelationsAreMeasured() {
        io.matrix.knowledge.concept.SourceGroundedEnrichment.Extraction extraction =
                io.matrix.knowledge.concept.SourceGroundedEnrichment.extract(seedSnapshot,
                        io.matrix.knowledge.concept.SourceGroundedEnrichment.DefinitionRule.TIGHT_V1_1);
        int surviving = 0;
        List<String> rejected = new ArrayList<>();
        for (io.matrix.knowledge.concept.SourceGroundedEnrichment.RelationCandidate candidate
                : extraction.relations()) {
            boolean fromAccepted = ReferentPolicy.accepted(candidate.fromConceptName());
            boolean toAccepted = ReferentPolicy.accepted(candidate.toConceptName());
            if (fromAccepted && toAccepted) {
                surviving++;
            } else {
                rejected.add(candidate.type().name() + ": " + candidate.fromConceptName() + " -> "
                        + candidate.toConceptName());
            }
        }
        System.out.printf("[referent-key] the relations: %d candidates of the frozen rule, %d of "
                        + "them have both endpoints accepted by the policy; the rejected: %s%n",
                extraction.relations().size(), surviving, rejected);
        for (ReferentPolicy.Strictness level : ReferentPolicy.Strictness.values()) {
            int strictSurviving = 0;
            for (io.matrix.knowledge.concept.SourceGroundedEnrichment.RelationCandidate candidate
                    : extraction.relations()) {
                if (ReferentPolicy.accepted(candidate.fromConceptName(), level)
                        && ReferentPolicy.accepted(candidate.toConceptName(), level)) {
                    strictSurviving++;
                }
            }
            System.out.printf("[referent-key] the relations (%s): %d of %d candidates have both "
                            + "endpoints accepted%n",
                    level, strictSurviving, extraction.relations().size());
        }
        assertThat(extraction.relations()).as("the frozen relation rule's candidates").isNotEmpty();
    }

    // ── the intervals ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("the two precisions and their intervals")
    void theIntervalsAreMeasured() {
        List<HandLabel> labels = handLabels();
        long labelledTrue = labels.stream().filter(HandLabel::concept).count();
        int accepted = 0;
        int acceptedTrue = 0;
        for (HandLabel label : labels) {
            if (ReferentPolicy.accepted(label.span())) {
                accepted++;
                if (label.concept()) {
                    acceptedTrue++;
                }
            }
        }
        double[] splitter = wilson(labelledTrue, labels.size());
        double[] policy = wilson(acceptedTrue, accepted);
        System.out.printf("[referent-key] the intervals: the splitter %.4f [%.4f, %.4f] (%d/%d); "
                        + "the policy %.4f [%.4f, %.4f] (%d/%d of the accepted)%n",
                splitter[0], splitter[1], splitter[2], labelledTrue, labels.size(),
                policy[0], policy[1], policy[2], acceptedTrue, accepted);
        assertThat(policy[1]).as("the policy's interval must lie above the splitter's")
                .isGreaterThan(splitter[2]);
    }

    /**
     * The Wilson score interval of a proportion.
     *
     * @param successes the number of successes
     * @param total the number of trials
     * @return the point estimate, the lower bound and the upper bound
     */
    private static double[] wilson(long successes, long total) {
        if (total == 0) {
            return new double[] {0, 0, 0};
        }
        double z = 1.959963984540054;
        double p = (double) successes / total;
        double denominator = 1 + z * z / total;
        double centre = (p + z * z / (2 * total)) / denominator;
        double margin = z / denominator
                * Math.sqrt(p * (1 - p) / total + z * z / (4 * total * total));
        return new double[] {p, Math.max(0, centre - margin), Math.min(1, centre + margin)};
    }

    // ── the helpers ───────────────────────────────────────────────────────────

    /**
     * The frozen hand-labelled set of the A1 measure, read from the A1 instrument
     * itself.
     *
     * <p>The instrument's class is package-private, so it is read reflectively: the
     * frozen set stays the single source of truth and this instrument cannot drift
     * away from it. A failure to read it fails the test loudly.</p>
     */
    private static List<HandLabel> handLabels() {
        try {
            Class<?> type = Class.forName(HAND_LABELLED_CLASS);
            Field field = type.getDeclaredField(HAND_LABELLED_FIELD);
            field.setAccessible(true);
            List<?> rows = (List<?>) field.get(null);
            List<HandLabel> labels = new ArrayList<>();
            for (Object row : rows) {
                Method position = row.getClass().getDeclaredMethod("position");
                Method span = row.getClass().getDeclaredMethod("span");
                Method concept = row.getClass().getDeclaredMethod("concept");
                position.setAccessible(true);
                span.setAccessible(true);
                concept.setAccessible(true);
                labels.add(new HandLabel((Integer) position.invoke(row),
                        (String) span.invoke(row), (Boolean) concept.invoke(row)));
            }
            return labels;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the frozen A1 instrument could not be read", e);
        }
    }

    /** One label of the frozen A1 set. */
    private record HandLabel(int position, String span, boolean concept) {
    }

    /** The property a case tests. */
    enum Property {
        /** The same word in two contexts must stay two referents. */
        SAME_WORD_DIFFERENT_CONTEXT,
        /** A synonym must not fragment a referent. */
        SYNONYM,
        /** A paraphrase must not fragment a referent. */
        PARAPHRASE,
        /** A homonym must not merge. */
        HOMONYM
    }

    /**
     * One labelled case of the four properties.
     *
     * @param property which property the case tests
     * @param first the first surface form
     * @param second the second surface form
     * @param sameReferent whether the two are the same referent
     * @param reason why the case is labelled that way
     */
    record PropertyCase(Property property, String first, String second, boolean sameReferent,
                        String reason) {
        /** Whether the policy is expected to merge the two. */
        boolean expectedSameReferent() {
            return sameReferent;
        }

        /** The frozen cases, hand-labelled against the corpus's own sentences. */
        static final List<PropertyCase> FROZEN = List.of(
                new PropertyCase(Property.SAME_WORD_DIFFERENT_CONTEXT,
                        "Oceanic crust", "Continental crust", false,
                        "the head is shared, the modifier is not: two different parts of the Earth"),
                new PropertyCase(Property.SAME_WORD_DIFFERENT_CONTEXT,
                        "Nazca Plate", "Pacific Plate", false,
                        "two named plates, one head"),
                new PropertyCase(Property.SAME_WORD_DIFFERENT_CONTEXT,
                        "Divergent boundaries", "Convergent boundaries", false,
                        "the head is shared, the modifier names the other kind of boundary"),
                new PropertyCase(Property.SAME_WORD_DIFFERENT_CONTEXT,
                        "Subduction zones", "Rifting zones", false,
                        "the head is shared, the modifier names the other kind of zone"),
                new PropertyCase(Property.SYNONYM,
                        "Slab pull", "The slab pull force", true,
                        "the corpus writes both for the same force"),
                new PropertyCase(Property.SYNONYM,
                        "Seafloor spreading", "Sea-floor spreading", true,
                        "the corpus writes the term with and without the hyphen"),
                new PropertyCase(Property.SYNONYM,
                        "The lithosphere", "Earth's lithosphere", true,
                        "the corpus's two writings of the same layer"),
                new PropertyCase(Property.SYNONYM,
                        "Continental drift", "Wegener's theory of continental drift", true,
                        "the corpus's two writings of the same theory"),
                new PropertyCase(Property.PARAPHRASE,
                        "Plate motion", "The motion of the tectonic plates", true,
                        "the corpus's two writings of the same motion"),
                new PropertyCase(Property.PARAPHRASE,
                        "The tracks of hotspots", "Hotspot tracks", true,
                        "the corpus's two writings of the same feature"),
                new PropertyCase(Property.PARAPHRASE,
                        "Divergent boundary", "Divergent plate boundary", true,
                        "the corpus's two writings of the same boundary"),
                new PropertyCase(Property.HOMONYM,
                        "Crust", "Oceanic crust", false,
                        "the generic crust and one kind of crust are not one referent"),
                new PropertyCase(Property.HOMONYM,
                        "Plate", "Nazca Plate", false,
                        "the generic plate and one named plate are not one referent"),
                new PropertyCase(Property.HOMONYM,
                        "Trench", "Middle America Trench", false,
                        "the generic trench and one named trench are not one referent"));
    }

    /** The count of the cases of one property. */
    private static int count(Property property) {
        int total = 0;
        for (PropertyCase testCase : PropertyCase.FROZEN) {
            if (testCase.property() == property) {
                total++;
            }
        }
        return total;
    }

    /**
     * Whether the policy merges two surface forms into one referent.
     *
     * <p>The decision is taken on the two lemmas and on the declared alias table, the
     * same way the resolver takes it.</p>
     */
    private static boolean mergedByPolicy(PropertyCase testCase) {
        String first = ReferentPolicy.lemma(testCase.first());
        String second = ReferentPolicy.lemma(testCase.second());
        if (first.equals(second)) {
            return !ReferentPolicy.declaredDistinct(first, second);
        }
        ReferentPolicy.AliasKind declared = ReferentPolicy.declaredAs(first, second);
        if (declared == ReferentPolicy.AliasKind.DISTINCT) {
            return false;
        }
        if (declared != null) {
            return true;
        }
        return ReferentPolicy.canonicalLemma(first).equals(ReferentPolicy.canonicalLemma(second));
    }

    private String conceptIdOf(String claimId) {
        for (Claim claim : seedSnapshot.claims()) {
            if (claim.id().equals(claimId)) {
                return claim.conceptId();
            }
        }
        return null;
    }

    private Concept conceptById(String conceptId) {
        for (Concept concept : seedSnapshot.concepts()) {
            if (concept.id().equals(conceptId)) {
                return concept;
            }
        }
        return null;
    }

    /** Imports a corpus into a workspace of a temporary database. */
    private static Imported importCorpus(String workspace, List<String> corpora)
            throws IOException {
        Path database = workDirectory.resolve(workspace + ".sqlite");
        long started = System.nanoTime();
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString())) {
            for (String corpus : corpora) {
                ClasspathCorpus extracted = ClasspathCorpus.extract(corpus);
                for (ClasspathCorpus.Entry entry : extracted.entries()) {
                    workbench.importDocument(workspace, entry.path(), entry.file(),
                            entry.language());
                }
            }
            KnowledgeSnapshot snapshot = workbench.workspace(workspace);
            List<io.matrix.knowledge.ConceptDefinition> definitions =
                    workbench.storeForValidation().conceptDefinitions(workspace);
            long elapsed = (System.nanoTime() - started) / 1_000_000L;
            if (SEED_WORKSPACE.equals(workspace)) {
                seedImportMillis = elapsed;
            } else {
                wideImportMillis = elapsed;
            }
            return new Imported(snapshot, definitions, elapsed);
        }
    }
}
