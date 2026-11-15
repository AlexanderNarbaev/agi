package io.matrix.knowledge.epistemic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.knowledge.acquisition.DocumentImporter;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0.3 — the NEW INDEPENDENT held-out evaluation set for the EPISTEMIC SEMANTICS.
 *
 * <p><b>What this is.</b> A dataset that tests PREMISE HANDLING and EVIDENCE-CONFLICT
 * SURFACING, with positive controls and adversarial negative controls, and that was
 * written <b>before</b> any implementation of the premise semantics was selected. The two
 * existing sets — the official 29-case evaluation ({@code epistemic-eval/cases.json}) and the
 * C2A 38-case held-out set ({@code C2A-HELDOUT-SET.json}) — are REGRESSION sets from here
 * on: they keep their value, and they are no longer fresh proof.</p>
 *
 * <p><b>What the set is built from.</b> The SEED CORPUS
 * ({@code corpus/plate-tectonics/}: the USGS {@code This Dynamic Earth} and the Wikipedia
 * {@code Plate tectonics} article, both licensed and both in the repository). The C2A
 * held-out set drew its gold from the RESEARCH POOL, so the gold of this set is
 * <b>document-disjoint</b> from the C2A gold, and the builder proves it: no gold proposition
 * of this set equals a gold proposition of the C2A set or a required proposition of the
 * 29-case development set.</p>
 *
 * <p><b>What the builder refuses to write.</b> A gold proposition that is not VERBATIM in
 * its source document. A gold proposition that is not a PRODUCTION claim of that document
 * (no identifier in this dataset is written by hand). An UNSUPPORTED case whose content
 * terms occur somewhere in the corpus. A question that shares a normalised four-token
 * sequence with any development question, any development assertion or any C2A held-out
 * question. A set without all nine classes. A case without a source span.</p>
 *
 * <p><b>Where the artefact goes.</b> {@code docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json},
 * written by this builder and read back and compared byte-for-byte before the test ends.
 * The artefact is byte-reproducible: the maps are {@link LinkedHashMap}s and the order is
 * the order written here, on every run.</p>
 *
 * <p><b>What it does not establish.</b> It does not measure any candidate: nothing is
 * designed, tuned or selected here. It measures the dataset itself — that its gold is the
 * source's text, that its classes are present, and that its questions and its gold are
 * independent of the two existing sets. It is written by one author, over one domain, from
 * two documents of one corpus, and it is not a measure of generalisation beyond them.</p>
 */
class EpistemicHeldOutSetBuilderTest {

    /** The workspace the seed corpus is imported into. */
    private static final String WORKSPACE = "epistemic-heldout-plate-tectonics";

    /** The corpus the set is built from: the licensed seed corpus of the repository. */
    private static final String CORPUS = AcquisitionCycle.SEED_CORPUS;

    /** The official 29-case development set: a REGRESSION set from here on. */
    private static final String DEVELOPMENT_SET = "epistemic-eval/cases.json";

    /** The C2A 38-case held-out set: a REGRESSION set from here on. */
    private static final String C2A_SET = "docs-v2/engineering/C2A-HELDOUT-SET.json";

    /** The shortest token sequence two questions may share. */
    private static final int MINIMUM_SHARED_TOKENS = 4;

    /** The fewest content words two paraphrased premises must have in common. */
    private static final int PARAPHRASE_MINIMUM_SHARED_WORDS = 3;

    /** The abstraction level the held-out questions are asked at. */
    private static final String LEVEL = "INTRODUCTORY";

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporary;

    // ── the shape of a case ────────────────────────────────────────────────────

    /** The nine classes the directive names. */
    enum CaseClass {
        /** The corpus supports the presupposition: the system must ANSWER. */
        SUPPORTED_PREMISE,
        /** The corpus contradicts the presupposition: the system must REFUTE it, with ids. */
        REFUTED_PREMISE,
        /** No evidence either way: the system must NOT call it false. */
        UNSUPPORTED_PREMISE,
        /** A claim that looks like a refutation but is about another subject. */
        IRRELEVANT_REFUTATION,
        /** Two claims that cannot both hold: both must be surfaced, with provenance. */
        CONFLICTING_EVIDENCE,
        /** Several defensible readings: they must be distinguished, not resolved. */
        AMBIGUOUS_READINGS,
        /** A surface wording that negates or inverts the relation. */
        LEXICAL_NEGATION,
        /** The same presupposition phrased differently. */
        PARAPHRASE,
        /** A plain factual question with no premise: it must be answered normally. */
        NO_PREMISE
    }

    /** The expected epistemic disposition of a case. */
    enum Disposition {
        /** Answered from the corpus. */
        ANSWERED,
        /** The premise is refused, and the contradicting claim is cited. */
        REFUTED,
        /** The premise is neither supported nor refuted. */
        UNSUPPORTED,
        /** The premise is not refuted; the look-alike claim does not refute it. */
        NOT_REFUTED,
        /** Both sides of a conflict are surfaced. */
        CONFLICT_SURFACED,
        /** Every reading of an ambiguity is surfaced. */
        AMBIGUITY_SURFACED
    }

    /** One gold claim: a production claim of a corpus document, and the span it rests on. */
    record Gold(String file, String proposition) {
    }

    /** One reading of an ambiguous case: a document, the spans that carry it, and what it says. */
    record Reading(String file, List<String> propositions, String reading) {
    }

    /** One case of the held-out set. */
    record CaseDef(String id, CaseClass caseClass, Disposition disposition, String question,
                   String premise, List<Gold> gold, List<Reading> readings,
                   List<String> trapPropositions, List<String> contentTerms,
                   List<String> traits, String note) {

        static CaseDef of(String id, CaseClass caseClass, Disposition disposition, String question,
                           String premise, List<Gold> gold, List<Reading> readings,
                           List<String> trapPropositions, List<String> contentTerms,
                           List<String> traits, String note) {
            return new CaseDef(id, caseClass, disposition, question, premise, List.copyOf(gold),
                    List.copyOf(readings), List.copyOf(trapPropositions),
                    List.copyOf(contentTerms), List.copyOf(traits), note);
        }
    }

    // ── the cases ─────────────────────────────────────────────────────────────

    /**
     * The denial markers this dataset uses to check that a REFUTED case really is refuted.
     *
     * <p>The list is written HERE and not taken from the production rule, on purpose: a
     * dataset that borrowed the implementation's own vocabulary could only confirm that the
     * implementation agrees with itself. The gold has to be verifiable by reading the
     * document.</p>
     */
    private static final List<String> DENIAL_MARKERS = List.of(
            "not", "no", "never", "cannot", "neither", "nor", "without",
            "isn't", "doesn't", "wasn't", "aren't", "weren't", "don't", "didn't");

    /** The words that mark a premise as negated, checked on the question's own wording. */
    private static final List<String> NEGATION_MARKERS = List.of(
            "not", "no", "never", "cannot", "n't", "не", "нет", "ни", "никогда");

    /** The two corpus documents the gold is drawn from. */
    private static final String USGS = "usgs-this-dynamic-earth.md";

    /** The Wikipedia plate-tectonics document. */
    private static final String WIKIPEDIA = "wikipedia-plate-tectonics.md";

    private static List<CaseDef> cases() {
        return List.of(
                // ── SUPPORTED_PREMISE ──────────────────────────────────────────
                CaseDef.of("E-S01", CaseClass.SUPPORTED_PREMISE, Disposition.ANSWERED,
                        "Given that new crust is generated where the plates pull away from each other, "
                                + "what happens where Mid-Atlantic Ridge runs?",
                        "new crust is generated where the plates pull away from each other",
                        List.of(
                                new Gold(USGS, "There are four types of plate boundaries: Divergent boundaries "
                                        + "-- where new crust is generated as the plates pull away from each other."),
                                new Gold(USGS, "Perhaps the best known of the divergent boundaries is the "
                                        + "Mid-Atlantic Ridge.")),
                        List.of(), List.of(), List.of(), List.of("premise-marker"),
                        "the corpus states the presupposition in its own words; the system must answer it, "
                                + "and a refusal here is a false refusal"),
                CaseDef.of("E-S02", CaseClass.SUPPORTED_PREMISE, Disposition.ANSWERED,
                        "Given that a subduction zone is where a sinking plate goes, what becomes of the "
                                + "crust there?",
                        "a subduction zone is where a sinking plate goes",
                        List.of(
                                new Gold(USGS, "The location where sinking of a plate occurs is called a "
                                        + "subduction zone."),
                                new Gold(USGS, "Convergent boundaries -- where crust is destroyed as one plate "
                                        + "dives under another.")),
                        List.of(), List.of(), List.of(), List.of("premise-marker"),
                        "the corpus states the presupposition; the system must answer it"),

                // ── REFUTED_PREMISE ────────────────────────────────────────────
                CaseDef.of("E-R01", CaseClass.REFUTED_PREMISE, Disposition.REFUTED,
                        "Given that there was an apparent mechanism for continental drift when Wegener "
                                + "proposed it, how was his theory received?",
                        "there was an apparent mechanism for continental drift when Wegener proposed it",
                        List.of(
                                new Gold(WIKIPEDIA, "However, his ideas were not taken seriously by many "
                                        + "geologists, who pointed out that there was no apparent mechanism "
                                        + "for continental drift."),
                                new Gold(WIKIPEDIA, "Wegener's work was initially not widely accepted, in part "
                                        + "due to a lack of detailed evidence but mostly because of the lack "
                                        + "of a reasonable physically supported mechanism.")),
                        List.of(), List.of(), List.of(), List.of("premise-marker"),
                        "the corpus denies the presupposition outright; the system must refuse it and name "
                                + "the contradicting claim and its source"),
                CaseDef.of("E-R02", CaseClass.REFUTED_PREMISE, Disposition.REFUTED,
                        "Given that a horizontal pushing force is what moves the plates apart at spreading "
                                + "ridges, what drives that motion?",
                        "a horizontal pushing force is what moves the plates apart at spreading ridges",
                        List.of(
                                new Gold(WIKIPEDIA, "This is a misnomer as there is no force \"pushing\" "
                                        + "horizontally, indeed tensional features are dominant along ridges.")),
                        List.of(), List.of(), List.of(), List.of("premise-marker"),
                        "the corpus denies the presupposition in its own words; the system must refuse it and "
                                + "cite the contradicting claim"),

                // ── UNSUPPORTED_PREMISE ─────────────────────────────────────────
                CaseDef.of("E-U01", CaseClass.UNSUPPORTED_PREMISE, Disposition.UNSUPPORTED,
                        "Given that foraminifera in palaeomagnetic core samples date the Triassic, how is "
                                + "the age of the layer established?",
                        "foraminifera in palaeomagnetic core samples date the Triassic",
                        List.of(), List.of(), List.of(),
                        List.of("foraminifera", "palaeomagnetic", "triassic"),
                        List.of("premise-marker"),
                        "no corpus document mentions any of the content terms; the system must not call the "
                                + "premise false, and must not call it true either"),
                CaseDef.of("E-U02", CaseClass.UNSUPPORTED_PREMISE, Disposition.UNSUPPORTED,
                        "Given that an accretionary prism of eclogite marks a Moho discontinuity, what does "
                                + "the structure indicate?",
                        "an accretionary prism of eclogite marks a Moho discontinuity",
                        List.of(), List.of(), List.of(),
                        List.of("accretionary", "eclogite", "moho"),
                        List.of("premise-marker"),
                        "no corpus document mentions any of the content terms; absence of evidence is not a "
                                + "contradiction and the system must say so rather than refuse"),

                // ── IRRELEVANT_REFUTATION ──────────────────────────────────────
                CaseDef.of("E-I01", CaseClass.IRRELEVANT_REFUTATION, Disposition.NOT_REFUTED,
                        "Given that the horizontal grinding of the Pacific Plate past the North American "
                                + "Plate is caused by a pushing force, what causes that motion?",
                        "the horizontal grinding of the Pacific Plate past the North American Plate is "
                                + "caused by a pushing force",
                        List.of(
                                new Gold(USGS, "Along it, the Pacific Plate has been grinding horizontally "
                                        + "past the North American Plate for 10 million years, at an average "
                                        + "rate of about 5 cm/yr.")),
                        List.of(),
                        List.of("This is a misnomer as there is no force \"pushing\" horizontally, indeed "
                                + "tensional features are dominant along ridges."),
                        List.of(), List.of("premise-marker"),
                        "the corpus holds a denial of a pushing force, but the denial is about spreading "
                                + "ridges and not about the San Andreas; a system that reads the denial as a "
                                + "refutation of this premise has produced a false contradiction"),

                // ── CONFLICTING_EVIDENCE ───────────────────────────────────────
                CaseDef.of("E-C01", CaseClass.CONFLICTING_EVIDENCE, Disposition.CONFLICT_SURFACED,
                        "Given that slab pull along subduction zones is the major driving force of plate "
                                + "motion, what does the corpus say about the Pacific plate?",
                        "slab pull along subduction zones is the major driving force of plate motion",
                        List.of(
                                new Gold(WIKIPEDIA, "It is thus thought that forces associated with the "
                                        + "downgoing plate (slab pull and slab suction) are the driving forces "
                                        + "which determine the motion of plates, except for those plates which "
                                        + "are not being subducted."),
                                new Gold(WIKIPEDIA, "This view however has been contradicted by a recent study "
                                        + "which found that the actual motions of the Pacific plate and other "
                                        + "plates associated with the East Pacific Rise do not correlate mainly "
                                        + "with either slab pull or slab push, but rather with a mantle "
                                        + "convection upwelling whose horizontal spreading along the bases of "
                                        + "the various plates drives them along via viscosity-related traction "
                                        + "forces.")),
                        List.of(), List.of(), List.of(), List.of("premise-marker"),
                        "the corpus holds two accounts of the driving mechanism that cannot both hold; the "
                                + "system must surface both with their provenance instead of choosing one"),

                // ── AMBIGUOUS_READINGS ─────────────────────────────────────────
                CaseDef.of("E-A01", CaseClass.AMBIGUOUS_READINGS, Disposition.AMBIGUITY_SURFACED,
                        "Given that plate boundaries are narrow lines between two plates, how are they "
                                + "described?",
                        "plate boundaries are narrow lines between two plates",
                        List.of(),
                        List.of(
                                new Reading(USGS, List.of("Most movement occurs along narrow zones between "
                                                + "plates where the results of plate-tectonic forces are most "
                                                + "evident."),
                                        "the boundary is a narrow zone between two plates"),
                                new Reading(USGS, List.of(
                                        "Plate boundary zones -- broad belts in which boundaries are not well "
                                                + "defined and the effects of plate interaction are unclear.",
                                        "In some regions, the boundaries are not well defined because the "
                                                + "plate-movement deformation occurring there extends over a "
                                                + "broad belt (called a plate-boundary zone )."),
                                        "the boundary is a broad belt in which the boundary is not well defined")),
                        List.of(), List.of(), List.of("premise-marker"),
                        "the corpus describes the plate boundary both as a narrow zone and as a broad belt; "
                                + "the two readings are defensible and the system must distinguish them rather "
                                + "than pick one"),
                CaseDef.of("E-A02", CaseClass.AMBIGUOUS_READINGS, Disposition.AMBIGUITY_SURFACED,
                        "Which forces does the corpus list as possible drivers of plate motion?",
                        null,
                        List.of(),
                        List.of(
                                new Reading(WIKIPEDIA, List.of("It is thus thought that forces associated with "
                                                + "the downgoing plate (slab pull and slab suction) are the "
                                                + "driving forces which determine the motion of plates, except "
                                                + "for those plates which are not being subducted."),
                                        "slab pull and slab suction"),
                                new Reading(WIKIPEDIA, List.of("Possible tidal effect on plate tectonics Of the "
                                                + "many forces discussed above, tidal force is still highly "
                                                + "debated and defended as a possible principal driving force "
                                                + "of plate tectonics."),
                                        "tidal force"),
                                new Reading(WIKIPEDIA, List.of("Surge tectonics Another theory is that the mantle "
                                                + "flows neither in cells nor large plumes but rather as a "
                                                + "series of channels just below Earth's crust, which then "
                                                + "provide basal friction to the lithosphere."),
                                        "surge tectonics, in which the mantle flows in channels"),
                                new Reading(WIKIPEDIA, List.of("This view however has been contradicted by a "
                                                + "recent study which found that the actual motions of the "
                                                + "Pacific plate and other plates associated with the East "
                                                + "Pacific Rise do not correlate mainly with either slab pull "
                                                + "or slab push, but rather with a mantle convection upwelling "
                                                + "whose horizontal spreading along the bases of the various "
                                                + "plates drives them along via viscosity-related traction "
                                                + "forces."),
                                        "mantle convection upwelling with basal traction")),
                        List.of(), List.of(), List.of("no-premise"),
                        "the corpus names four defensible drivers and does not settle between them; the "
                                + "system must report the readings rather than invent a resolution"),

                // ── LEXICAL_NEGATION ────────────────────────────────────────────
                CaseDef.of("E-L01", CaseClass.LEXICAL_NEGATION, Disposition.REFUTED,
                        "Given that no new crust is created where the plates pull away from each other, what "
                                + "happens there?",
                        "no new crust is created where the plates pull away from each other",
                        List.of(
                                new Gold(USGS, "There are four types of plate boundaries: Divergent boundaries "
                                        + "-- where new crust is generated as the plates pull away from each "
                                        + "other.")),
                        List.of(), List.of(), List.of(), List.of("premise-marker", "negation"),
                        "the question negates the relation the corpus asserts; the verdict must follow the "
                                + "evidence, so the negated premise is the refuted one"),
                CaseDef.of("E-L02", CaseClass.LEXICAL_NEGATION, Disposition.UNSUPPORTED,
                        "Поскольку тектонические плиты никогда не расходились, как образовались "
                                + "срединно-океанические хребты?",
                        "тектонические плиты никогда не расходились",
                        List.of(), List.of(), List.of(),
                        List.of("тектонические", "расходились", "хребты"),
                        List.of("premise-marker", "negation"),
                        "the negated premise is in a language the corpus does not hold; the requirement is the "
                                + "conservative one: a negation must never become a contradiction the evidence "
                                + "does not contain, so the system must neither confirm nor deny it"),

                // ── PARAPHRASE ──────────────────────────────────────────────────
                CaseDef.of("E-P01", CaseClass.PARAPHRASE, Disposition.ANSWERED,
                        "Given that Mid-Atlantic Ridge is a divergent boundary, what takes place along it?",
                        "Mid-Atlantic Ridge is a divergent boundary",
                        List.of(
                                new Gold(USGS, "Perhaps the best known of the divergent boundaries is the "
                                        + "Mid-Atlantic Ridge."),
                                new Gold(USGS, "There are four types of plate boundaries: Divergent boundaries "
                                        + "-- where new crust is generated as the plates pull away from each "
                                        + "other.")),
                        List.of(), List.of(), List.of(), List.of("premise-marker", "paraphrase-pair"),
                        "the first of the paraphrased pair; the presupposition is stated as a classification"),
                CaseDef.of("E-P02", CaseClass.PARAPHRASE, Disposition.ANSWERED,
                        "Assuming that the plates pull apart where Mid-Atlantic Ridge runs, what happens "
                                + "there?",
                        "the plates pull apart where Mid-Atlantic Ridge runs",
                        List.of(
                                new Gold(USGS, "Perhaps the best known of the divergent boundaries is the "
                                        + "Mid-Atlantic Ridge."),
                                new Gold(USGS, "There are four types of plate boundaries: Divergent boundaries "
                                        + "-- where new crust is generated as the plates pull away from each "
                                        + "other.")),
                        List.of(), List.of(), List.of(), List.of("premise-marker", "paraphrase-pair"),
                        "the second of the paraphrased pair; the same presupposition is phrased as a process, "
                                + "so a matcher that looks for the first wording finds nothing"),

                // ── NO_PREMISE ──────────────────────────────────────────────────
                CaseDef.of("E-N01", CaseClass.NO_PREMISE, Disposition.ANSWERED,
                        "What is the place where a sinking plate goes under another one called?",
                        null,
                        List.of(
                                new Gold(USGS, "The location where sinking of a plate occurs is called a "
                                        + "subduction zone.")),
                        List.of(), List.of(), List.of(), List.of("no-premise"),
                        "a plain factual question with no presupposition; the system must answer it normally, "
                                + "and an abstention here is an over-refusal"),
                CaseDef.of("E-N02", CaseClass.NO_PREMISE, Disposition.ANSWERED,
                        "Which ridge spreads fastest and which spreads slowest?",
                        null,
                        List.of(
                                new Gold(USGS, "The Arctic Ridge has the slowest rate (less than 2.5 cm/yr), "
                                        + "and the East Pacific Rise near Easter Island, in the South Pacific "
                                        + "about 3,400 km west of Chile, has the fastest rate (more than 15 "
                                        + "cm/yr).")),
                        List.of(), List.of(), List.of(), List.of("no-premise"),
                        "a plain factual question with no presupposition; the control against over-refusal"));
    }

    // ── the build ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P0.3 — the new epistemic held-out set, built from the seed corpus and verified")
    void buildEpistemicHeldOutSet() throws Exception {
        List<CaseDef> definitions = cases();

        // ── the workspace, assembled through the production acquisition path ──
        Path database = temporary.resolve("epistemic-heldout.sqlite");
        KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString());
        ClasspathCorpus corpus = ClasspathCorpus.extract(CORPUS);
        Map<String, ClasspathCorpus.Entry> entries = new LinkedHashMap<>();
        List<Map<String, Object>> acquisitions = new ArrayList<>();
        for (ClasspathCorpus.Entry entry : corpus.entries()) {
            DocumentImporter.ImportResult result =
                    workbench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
            entries.put(entry.file(), entry);
            Map<String, Object> acquisition = new LinkedHashMap<>();
            acquisition.put("file", entry.file());
            acquisition.put("sourceId", result.source().sourceId());
            acquisition.put("contentHash", result.source().contentHash());
            acquisition.put("claimsAdded", result.claimsAdded());
            acquisition.put("conceptsCreated", result.conceptsCreated());
            acquisition.put("representationsCreated", result.representationsCreated());
            acquisition.put("licence", entry.licence());
            acquisition.put("licenceUrl", entry.licenceUrl());
            acquisition.put("sourceUrl", entry.sourceUrl());
            acquisition.put("attribution", entry.attribution());
            acquisition.put("revision", entry.revision());
            acquisition.put("accessedAt", entry.accessedAt());
            acquisition.put("sha256", entry.sha256());
            acquisitions.add(acquisition);
            assertThat(result.claimsAdded()).as("claims derived from %s", entry.file()).isPositive();
        }
        assertThat(acquisitions).hasSize(2);

        KnowledgeSnapshot snapshot = workbench.workspace(WORKSPACE);
        Map<String, Claim> claimsByProposition = new LinkedHashMap<>();
        for (Claim claim : snapshot.claims()) {
            claimsByProposition.put(claim.proposition(), claim);
        }
        Map<String, Source> sourcesById = new LinkedHashMap<>();
        for (Source source : snapshot.sources()) {
            sourcesById.put(source.id(), source);
        }

        // the raw text of every corpus document, for the verbatim check
        Map<String, String> rawText = new LinkedHashMap<>();
        for (ClasspathCorpus.Entry entry : corpus.entries()) {
            rawText.put(entry.file(), readResource(CORPUS + "/" + entry.file()));
        }

        String dump = dumpPath();
        if (dump != null) {
            List<String> lines = new ArrayList<>();
            for (Claim claim : snapshot.claims()) {
                Source source = sourcesById.get(claim.introducedBySourceId());
                lines.add(claim.id() + "\t" + (source == null ? "?" : source.title()) + "\t"
                        + claim.status() + "\t" + claim.proposition());
            }
            lines.sort(Comparator.naturalOrder());
            Files.writeString(Path.of(dump), String.join("\n", lines), StandardCharsets.UTF_8);
        }

        // ── the cases, each verified before it is written out ────────────────
        assertThat(definitions).isNotEmpty();
        List<Map<String, Object>> written = new ArrayList<>();
        Set<String> questionTexts = new HashSet<>();
        Set<String> caseIds = new HashSet<>();
        Map<String, Integer> perClass = new LinkedHashMap<>();
        Map<String, Integer> casesPerFile = new LinkedHashMap<>();
        Map<String, Integer> goldPerFile = new LinkedHashMap<>();
        Set<String> allGoldPropositions = new LinkedHashSet<>();
        for (CaseDef definition : definitions) {
            assertThat(definition.question()).isNotBlank();
            assertThat(questionTexts.add(definition.question().strip()))
                    .as("the question of %s is repeated", definition.id()).isTrue();
            assertThat(caseIds.add(definition.id()))
                    .as("the id %s is repeated", definition.id()).isTrue();

            List<Map<String, Object>> gold = new ArrayList<>();
            for (Gold entry : definition.gold()) {
                Claim claim = claimsByProposition.get(entry.proposition());
                assertThat(claim).as("the production importer derived no claim for the gold "
                        + "proposition of %s: %s", definition.id(), entry.proposition()).isNotNull();
                ClasspathCorpus.Entry entryOfFile = entries.get(entry.file());
                assertThat(entryOfFile).as("%s names no corpus document", definition.id()).isNotNull();
                Source source = sourcesById.get(claim.introducedBySourceId());
                assertThat(source).as("the claim of %s has no source", definition.id()).isNotNull();
                assertThat(source.title()).isEqualTo(entry.file());
                assertThat(claim.introducedBySourceId())
                        .as("the claim of %s came from another document", definition.id())
                        .isEqualTo(DocumentImporter.sourceId(WORKSPACE, entryOfFile.sha256()));
                String document = rawText.get(entry.file());
                assertThat(document).as("the corpus document %s was not read", entry.file()).isNotNull();
                assertThat(flatten(document))
                        .as("the gold proposition of %s is not verbatim in %s",
                                definition.id(), entry.file())
                        .contains(flatten(entry.proposition()));
                allGoldPropositions.add(entry.proposition());

                Map<String, Object> goldEntry = new LinkedHashMap<>();
                goldEntry.put("claimId", claim.id());
                goldEntry.put("proposition", claim.proposition());
                goldEntry.put("sourceSpan", entry.proposition());
                goldEntry.put("claimContext", claim.context());
                goldEntry.put("sourceId", source.id());
                goldEntry.put("sourceTitle", source.title());
                goldEntry.put("sourceLicence", entryOfFile.licence());
                goldEntry.put("sourceLicenceUrl", entryOfFile.licenceUrl());
                goldEntry.put("sourceUrl", entryOfFile.sourceUrl());
                goldEntry.put("extractionMethod", claim.extractionMethod());
                goldEntry.put("claimStatus", claim.status().name());
                gold.add(goldEntry);
                goldPerFile.merge(entry.file(), 1, Integer::sum);
            }
            // A case names a source span UNLESS its expected disposition is UNSUPPORTED:
            // there the absence of evidence IS the result, and a span would be a fiction.
            if (definition.disposition() != Disposition.UNSUPPORTED
                    && definition.caseClass() != CaseClass.AMBIGUOUS_READINGS) {
                assertThat(definition.gold()).as("the case %s names no source span",
                        definition.id()).isNotEmpty();
            }
            if (definition.disposition() == Disposition.UNSUPPORTED) {
                assertThat(definition.contentTerms())
                        .as("the unsupported case %s names no content terms", definition.id())
                        .isNotEmpty();
            }
            if (definition.caseClass() == CaseClass.AMBIGUOUS_READINGS) {
                // An ambiguous case carries its spans in the READINGS: the class is about the
                // several defensible readings, and a single gold claim would resolve it.
                assertThat(definition.readings()).as("the ambiguous case %s names no reading",
                        definition.id()).isNotEmpty();
                for (Reading reading : definition.readings()) {
                    assertThat(reading.propositions()).as("a reading of %s names no span",
                            definition.id()).isNotEmpty();
                }
            }

            // ── the class-specific grounding checks ───────────────────────────
            //
            // A dataset that does not check its own classes is a list of questions with
            // expectations attached. Each check below states, in the dataset's own terms,
            // what the class MEANS, and refuses the case when the evidence does not match.

            if (definition.caseClass() == CaseClass.UNSUPPORTED_PREMISE) {
                // "Unsupported" is a claim about the corpus: the content terms of the premise
                // must occur NOWHERE in it. Without that, the class is not distinguishable
                // from "refuted", which is the mistake the directive names.
                assertThat(definition.contentTerms())
                        .as("the unsupported case %s names no content terms", definition.id())
                        .isNotEmpty();
                for (String term : definition.contentTerms()) {
                    for (Map.Entry<String, String> document : rawText.entrySet()) {
                        assertThat(document.getValue().toLowerCase(Locale.ROOT))
                                .as("the content term %s of %s occurs in %s", term,
                                        definition.id(), document.getKey())
                                .doesNotContain(term.toLowerCase(Locale.ROOT));
                    }
                }
            }

            if (definition.caseClass() == CaseClass.REFUTED_PREMISE) {
                // A refuted case must be refuted BY THE TEXT: at least one gold claim has to
                // deny something. The marker list is this file's own, not the production
                // rule's, so the check cannot be satisfied by the implementation agreeing
                // with itself.
                assertThat(definition.gold()).as("the refuted case %s names no gold claim",
                        definition.id()).isNotEmpty();
                boolean denied = false;
                for (Gold goldEntry : definition.gold()) {
                    if (carriesDenial(goldEntry.proposition())) {
                        denied = true;
                        break;
                    }
                }
                assertThat(denied)
                        .as("no gold claim of the refuted case %s denies anything",
                                definition.id()).isTrue();
            }

            if (definition.caseClass() == CaseClass.LEXICAL_NEGATION) {
                // The class is about the WORDING: the premise must carry a negation, in
                // whatever language the case is written in.
                assertThat(definition.premise()).as("the negated case %s names no premise",
                        definition.id()).isNotBlank();
                assertThat(carriesNegation(definition.premise()))
                        .as("the premise of %s carries no negation", definition.id()).isTrue();
            }

            if (definition.caseClass() == CaseClass.IRRELEVANT_REFUTATION) {
                // The trap must be a real claim that denies something, and it must not be the
                // case's own gold: a trap that supports the premise is not a trap.
                assertThat(definition.trapPropositions())
                        .as("the irrelevant-refutation case %s names no trap claim",
                                definition.id()).isNotEmpty();
                for (String trap : definition.trapPropositions()) {
                    assertThat(carriesDenial(trap))
                            .as("the trap claim of %s denies nothing: %s", definition.id(), trap)
                            .isTrue();
                    for (Gold goldEntry : definition.gold()) {
                        assertThat(goldEntry.proposition())
                                .as("the trap claim of %s is also its gold", definition.id())
                                .isNotEqualTo(trap);
                    }
                }
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", definition.id());
            entry.put("class", definition.caseClass().name());
            entry.put("level", LEVEL);
            entry.put("question", definition.question());
            entry.put("expectedDisposition", definition.disposition().name());
            if (definition.premise() != null) {
                entry.put("premise", definition.premise());
            }
            entry.put("goldClaims", gold);
            Set<String> caseFiles = new LinkedHashSet<>();
            for (Gold g : definition.gold()) {
                caseFiles.add(g.file());
            }
            for (Reading r : definition.readings()) {
                caseFiles.add(r.file());
            }
            entry.put("sourceFiles", caseFiles);
            for (String file : caseFiles) {
                casesPerFile.merge(file, 1, Integer::sum);
            }
            if (!definition.readings().isEmpty()) {
                List<Map<String, Object>> readings = new ArrayList<>();
                for (Reading reading : definition.readings()) {
                    List<Map<String, Object>> spans = new ArrayList<>();
                    for (String proposition : reading.propositions()) {
                        Claim claim = claimsByProposition.get(proposition);
                        assertThat(claim).as("the reading of %s has no production claim",
                                definition.id()).isNotNull();
                        assertThat(flatten(rawText.get(reading.file())))
                                .as("the reading of %s is not verbatim in %s", definition.id(),
                                        reading.file()).contains(flatten(proposition));
                        allGoldPropositions.add(proposition);
                        Map<String, Object> span = new LinkedHashMap<>();
                        span.put("proposition", proposition);
                        span.put("sourceSpan", proposition);
                        span.put("claimId", claim.id());
                        span.put("claimContext", claim.context());
                        span.put("sourceId", claim.introducedBySourceId());
                        spans.add(span);
                        goldPerFile.merge(reading.file(), 1, Integer::sum);
                    }
                    Map<String, Object> writtenReading = new LinkedHashMap<>();
                    writtenReading.put("reading", reading.reading());
                    writtenReading.put("file", reading.file());
                    writtenReading.put("spans", spans);
                    readings.add(writtenReading);
                }
                entry.put("readings", readings);
            }
            if (!definition.trapPropositions().isEmpty()) {
                List<Map<String, Object>> traps = new ArrayList<>();
                for (String proposition : definition.trapPropositions()) {
                    Claim claim = claimsByProposition.get(proposition);
                    assertThat(claim).as("the trap claim of %s is not a production claim: %s",
                            definition.id(), proposition).isNotNull();
                    assertThat(rawText.values())
                            .as("the trap claim of %s is not verbatim in any corpus document",
                                    definition.id()).anyMatch(document ->
                                    flatten(document).contains(flatten(proposition)));
                    Map<String, Object> trap = new LinkedHashMap<>();
                    trap.put("proposition", proposition);
                    trap.put("sourceSpan", proposition);
                    trap.put("claimId", claim.id());
                    trap.put("claimContext", claim.context());
                    trap.put("sourceId", claim.introducedBySourceId());
                    traps.add(trap);
                }
                entry.put("trapClaims", traps);
            }
            if (!definition.contentTerms().isEmpty()) {
                entry.put("contentTerms", definition.contentTerms());
            }
            if (!definition.traits().isEmpty()) {
                entry.put("traits", definition.traits());
            }
            entry.put("note", definition.note());
            written.add(entry);
            perClass.merge(definition.caseClass().name(), 1, Integer::sum);
        }

        // ── the shape of the set ──────────────────────────────────────────────
        for (CaseClass caseClass : CaseClass.values()) {
            assertThat(perClass.getOrDefault(caseClass.name(), 0))
                    .as("the set has no %s case", caseClass).isPositive();
        }
        for (ClasspathCorpus.Entry entry : corpus.entries()) {
            assertThat(casesPerFile.getOrDefault(entry.file(), 0))
                    .as("the set has no case for %s", entry.file()).isPositive();
        }

        // ── the paraphrase pair really is a paraphrase ───────────────────────
        //
        // The class exists to defeat string matching, so the two premises must be
        // DIFFERENT words and the SAME presupposition. Equal strings would make the
        // class a duplicate; unrelated strings would make it a different class.
        List<String> paraphrasePremises = new ArrayList<>();
        for (CaseDef definition : definitions) {
            if (definition.caseClass() == CaseClass.PARAPHRASE) {
                paraphrasePremises.add(definition.premise());
            }
        }
        assertThat(paraphrasePremises).as("the set has fewer than two paraphrase cases")
                .hasSizeGreaterThan(1);
        for (int first = 0; first < paraphrasePremises.size(); first++) {
            for (int second = first + 1; second < paraphrasePremises.size(); second++) {
                String left = paraphrasePremises.get(first);
                String right = paraphrasePremises.get(second);
                assertThat(left).as("the paraphrase pair repeats a premise").isNotEqualTo(right);
                Set<String> shared = new LinkedHashSet<>(tokens(left));
                shared.retainAll(tokens(right));
                assertThat(shared.size())
                        .as("the paraphrased premises share only %d content word(s): %s / %s",
                                shared.size(), left, right)
                        .isGreaterThanOrEqualTo(PARAPHRASE_MINIMUM_SHARED_WORDS);
            }
        }

        // ── the gold is independent of the two existing sets ──────────────────
        Set<String> existingPropositions = existingPropositions();
        assertThat(existingPropositions).isNotEmpty();
        for (String proposition : allGoldPropositions) {
            assertThat(existingPropositions)
                    .as("the gold proposition of this set is already gold of an existing set: %s",
                            proposition).doesNotContain(proposition);
        }

        // ── the questions are independent of the existing questions ───────────
        List<String> existingTexts = existingTexts();
        assertThat(existingTexts).isNotEmpty();
        for (Map<String, Object> entry : written) {
            String question = (String) entry.get("question");
            Set<String> questionGrams = ngrams(question);
            for (String existing : existingTexts) {
                Set<String> shared = new LinkedHashSet<>(questionGrams);
                shared.retainAll(ngrams(existing));
                assertThat(shared).as("the question of %s shares a %d-word sequence with an "
                        + "existing question: %s", entry.get("id"), MINIMUM_SHARED_TOKENS,
                        shared).isEmpty();
            }
        }

        // ── the artefact ──────────────────────────────────────────────────────
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("dataset", "epistemic-heldout-premise-semantics");
        dataset.put("version", "1.0");
        dataset.put("date", "2026-11-15");
        dataset.put("purpose", "a new independently held-out evaluation set for the epistemic "
                + "semantics: premise handling and evidence-conflict surfacing, with positive "
                + "controls and adversarial negative controls, frozen before any implementation "
                + "is selected");
        dataset.put("freeze", freeze(written));
        dataset.put("manifest", manifest(acquisitions, perClass, casesPerFile, goldPerFile, written));
        dataset.put("cases", written);

        String json = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(dataset);
        Path out = outputDirectory();
        Files.createDirectories(out);
        Path artefact = out.resolve("EPISTEMIC-HELDOUT-SET.json");
        Files.writeString(artefact, json, StandardCharsets.UTF_8);
        assertThat(Files.readString(artefact)).isEqualTo(json);

        System.out.println("[P0.3] " + artefact + " (" + written.size() + " cases)");
        System.out.println("[P0.3] classes " + perClass);
        System.out.println("[P0.3] cases per document " + casesPerFile);
        System.out.println("[P0.3] gold claims per document " + goldPerFile);
        System.out.println("[P0.3] freeze " + dataset.get("freeze"));
    }

    // ── the freeze ────────────────────────────────────────────────────────────

    /**
     * The freeze record.
     *
     * <p>The digest is taken over the canonical JSON of the CASES array — the gold-bearing
     * content — so that it can be recomputed by any reader from the file itself, without
     * the file having to contain its own digest. The digest is recorded in this dataset and
     * in every artefact that cites it.</p>
     */
    private Map<String, Object> freeze(List<Map<String, Object>> written) throws Exception {
        String canonical = JSON.writeValueAsString(written) + "\n";
        Map<String, Object> freeze = new LinkedHashMap<>();
        freeze.put("algorithm", "sha-256");
        freeze.put("scope", "the UTF-8 bytes of the compact JSON document that is the 'cases' "
                + "array of this dataset, followed by a single line feed");
        freeze.put("sha256", sha256(canonical.getBytes(StandardCharsets.UTF_8)));
        freeze.put("frozenAt", "2026-11-15");
        freeze.put("frozenBefore", "the measurement of any candidate implementation of the "
                + "premise semantics, and before any algorithm was selected for the repair");
        freeze.put("regressionSets", List.of(DEVELOPMENT_SET, C2A_SET));
        return freeze;
    }

    // ── the manifest inside the dataset ───────────────────────────────────────

    private Map<String, Object> manifest(List<Map<String, Object>> acquisitions,
                                         Map<String, Integer> perClass,
                                         Map<String, Integer> casesPerFile,
                                         Map<String, Integer> goldPerFile,
                                         List<Map<String, Object>> written) throws Exception {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("corpus", CORPUS);
        manifest.put("corpusManifest", "matrix-core/src/main/resources/" + CORPUS
                + "/corpus-manifest.json");
        manifest.put("corpusManifestSha256", sha256(resourceBytes(CORPUS + "/corpus-manifest.json")));
        manifest.put("workspace", WORKSPACE);
        manifest.put("acquisitionPath", "io.matrix.knowledge.acquisition.AcquisitionCycle.SEED_CORPUS"
                + " -> io.matrix.knowledge.acquisition.ClasspathCorpus.extract"
                + " -> io.matrix.knowledge.KnowledgeWorkbench.importDocument");
        manifest.put("builder", "io.matrix.knowledge.epistemic.EpistemicHeldOutSetBuilderTest");
        manifest.put("method", "the corpus documents are imported through the production "
                + "acquisition path; every gold claim is the production claim whose proposition "
                + "equals a sentence of a corpus document, and the builder proves the sentence is "
                + "verbatim in that document before the case is written out");
        manifest.put("verifications", List.of(
                "the gold proposition occurs verbatim in its corpus document",
                "the gold claim exists in the production workspace and was introduced by that document's source",
                "the gold proposition of this set is not a gold proposition of the 29-case development set nor of the C2A 38-case held-out set",
                "a question of this set shares no normalised token sequence of length >= 4 with any development question, any development assertion or any C2A held-out question",
                "an UNSUPPORTED case names content terms that occur in no corpus document",
                "the nine classes are present, and every corpus document carries a case",
                "no case repeats a question or an id"));
        Map<String, Object> classes = new LinkedHashMap<>();
        classes.put("SUPPORTED_PREMISE", "the corpus supports the presupposition; the expected "
                + "disposition is ANSWERED and a refusal is a false refusal");
        classes.put("REFUTED_PREMISE", "the corpus contradicts the presupposition; the expected "
                + "disposition is REFUTED and the contradicting claim and source must be cited");
        classes.put("UNSUPPORTED_PREMISE", "no evidence either way; the expected disposition is "
                + "UNSUPPORTED and calling the premise false is a false contradiction");
        classes.put("IRRELEVANT_REFUTATION", "the corpus holds a claim that looks like a "
                + "refutation but is about another subject; the expected disposition is "
                + "NOT_REFUTED and the look-alike claim must not be used as the contradiction");
        classes.put("CONFLICTING_EVIDENCE", "two corpus claims that cannot both hold; the "
                + "expected disposition is CONFLICT_SURFACED and both sides are cited with their "
                + "provenance");
        classes.put("AMBIGUOUS_READINGS", "several defensible readings; the expected disposition "
                + "is AMBIGUITY_SURFACED and no reading may be silently chosen");
        classes.put("LEXICAL_NEGATION", "the surface wording negates or inverts the relation; the "
                + "expected disposition follows the evidence, not the wording");
        classes.put("PARAPHRASE", "the same presupposition phrased differently; the expected "
                + "disposition is the one of the presupposition, and string matching cannot find it");
        classes.put("NO_PREMISE", "a plain factual question; the expected disposition is ANSWERED "
                + "and an abstention is an over-refusal");
        manifest.put("classes", classes);
        manifest.put("documents", acquisitions);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("cases", written.size());
        counts.put("distinctGoldPropositions", distinctGoldPropositions(written));
        counts.put("casesPerClass", perClass);
        counts.put("casesPerDocument", casesPerFile);
        counts.put("goldClaimsPerDocument", goldPerFile);
        manifest.put("counts", counts);
        Map<String, Object> regression = new LinkedHashMap<>();
        regression.put("developmentSet", "matrix-core/src/main/resources/" + DEVELOPMENT_SET);
        regression.put("developmentSetSha256", sha256(resourceBytes(DEVELOPMENT_SET)));
        regression.put("c2aHeldOutSet", C2A_SET);
        regression.put("c2aHeldOutSetSha256", sha256(Files.readAllBytes(
                RepositoryPaths.root().resolve(C2A_SET))));
        regression.put("status", "both sets are REGRESSION sets from this one on: they keep their "
                + "value and they are no longer fresh proof");
        manifest.put("regressionSets", regression);
        return manifest;
    }

    private long distinctGoldPropositions(List<Map<String, Object>> written) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> entry : written) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> gold = (List<Map<String, Object>>) entry.get("goldClaims");
            for (Map<String, Object> claim : gold) {
                ids.add((String) claim.get("proposition"));
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> readings = (List<Map<String, Object>>) entry.get("readings");
            if (readings != null) {
                for (Map<String, Object> reading : readings) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> spans = (List<Map<String, Object>>) reading.get("spans");
                    for (Map<String, Object> span : spans) {
                        ids.add((String) span.get("proposition"));
                    }
                }
            }
        }
        return ids.size();
    }

    // ── the two existing sets, for the independence checks ────────────────────

    /** Every proposition the two existing sets already use as gold. */
    private Set<String> existingPropositions() throws IOException {
        Set<String> propositions = new LinkedHashSet<>();
        JsonNode development = JSON.readTree(resourceBytes(DEVELOPMENT_SET));
        collectPropositions(development, propositions);
        JsonNode c2a = JSON.readTree(Files.readAllBytes(RepositoryPaths.root().resolve(C2A_SET)));
        collectPropositions(c2a, propositions);
        return propositions;
    }

    private void collectPropositions(JsonNode node, Set<String> into) {
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectPropositions(child, into);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        for (String field : List.of("requiredPropositions", "requiredPropositionsAny", "goldClaims",
                "spans")) {
            JsonNode value = node.get(field);
            if (value == null || !value.isArray()) {
                continue;
            }
            for (JsonNode child : value) {
                JsonNode proposition = child.isObject() ? child.get("proposition") : child;
                if (proposition != null && proposition.isTextual()) {
                    into.add(proposition.asText());
                }
            }
        }
        for (JsonNode child : node) {
            collectPropositions(child, into);
        }
    }

    /** Every question and assertion the two existing sets carry. */
    private List<String> existingTexts() throws IOException {
        List<String> texts = new ArrayList<>();
        JsonNode development = JSON.readTree(resourceBytes(DEVELOPMENT_SET));
        for (JsonNode entry : development.path("cases")) {
            String question = entry.path("question").asText("");
            if (!question.isBlank()) {
                texts.add(question);
            }
            String assertion = entry.path("call").path("body").path("assertion").asText("");
            if (!assertion.isBlank()) {
                texts.add(assertion);
            }
        }
        JsonNode c2a = JSON.readTree(Files.readAllBytes(RepositoryPaths.root().resolve(C2A_SET)));
        for (JsonNode entry : c2a.path("cases")) {
            String question = entry.path("question").asText("");
            if (!question.isBlank()) {
                texts.add(question);
            }
            String premise = entry.path("disputedPremise").asText("");
            if (!premise.isBlank()) {
                texts.add(premise);
            }
        }
        return texts;
    }

    // ── the text ──────────────────────────────────────────────────────────────

    /**
     * Collapses the line wrapping of a document.
     *
     * <p>The corpus documents are hard-wrapped Markdown: a sentence that reads as one line
     * on the screen occupies several lines in the file. The VERBATIM check compares the
     * texts with their wrapping collapsed, so a single word or a single mark still has to
     * match exactly — what the check forgives is the line break and nothing else.</p>
     */
    private static String flatten(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    /**
     * Whether a text denies something.
     *
     * <p>The marker list is this dataset's own. A production rule would be the wrong
     * yardstick here: the dataset has to be checkable by reading the document, not by
     * running the implementation it is meant to measure.</p>
     */
    private static boolean carriesDenial(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z']+")) {
            if (DENIAL_MARKERS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a text carries a negation, in any language the dataset is written in. */
    private static boolean carriesNegation(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String marker : NEGATION_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** The normalised token sequences of a text, of length n and more. */
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

    /** The normalised tokens of a text: lower case, words and numbers only. */
    private static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!word.isBlank()) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    // ── the plumbing ──────────────────────────────────────────────────────────

    /** Where the artefact is written: the directory the runbook names. */
    private Path outputDirectory() {
        String directory = System.getProperty("matrix.epistemic.dir");
        if (directory == null || directory.isBlank()) {
            directory = System.getenv("MATRIX_EPISTEMIC_DIR");
        }
        if (directory == null || directory.isBlank()) {
            return RepositoryPaths.root().resolve("docs-v2/engineering");
        }
        return Path.of(directory);
    }

    /** Where the claim dump goes, when one is asked for. */
    private static String dumpPath() {
        String dump = System.getProperty("matrix.epistemic.dump");
        if (dump == null || dump.isBlank()) {
            dump = System.getenv("MATRIX_EPISTEMIC_DUMP");
        }
        return dump == null || dump.isBlank() ? null : dump;
    }

    private static byte[] resourceBytes(String resource) throws IOException {
        ClassLoader loader = EpistemicHeldOutSetBuilderTest.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("the resource " + resource + " is not on the classpath");
            }
            return stream.readAllBytes();
        }
    }

    private static String readResource(String resource) throws IOException {
        return new String(resourceBytes(resource), StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] content) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
