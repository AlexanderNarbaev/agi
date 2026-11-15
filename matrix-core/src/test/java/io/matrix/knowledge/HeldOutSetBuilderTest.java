package io.matrix.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.knowledge.acquisition.DocumentImporter;
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
 * B6 — the INDEPENDENT HELD-OUT EVALUATION SET for the retrieval subsystem.
 *
 * <p><b>What this is.</b> A dataset that is built BEFORE any candidate exists, from
 * documents that the development corpus does not contain, and whose gold is the SOURCE's
 * text rather than the system's output. The workspace is assembled through the production
 * acquisition path — {@link AcquisitionCycle#RESEARCH_POOL}, {@link ClasspathCorpus} and
 * {@link io.matrix.knowledge.KnowledgeWorkbench#importDocument} — so that every gold claim
 * carries the id the production importer gave it, and no id is written by hand.</p>
 *
 * <p><b>Why the research pool.</b> The development set (29 cases, the seed corpus) and this
 * set must not measure the same thing. The pool documents are not in the seed corpus, so a
 * candidate that has been fitted to the development set has never seen the sentences this set
 * asks about. The gold claim of a case is a sentence the production importer derived from a
 * pool document, and the builder proves the sentence is VERBATIM in that document before the
 * case is written out.</p>
 *
 * <p><b>What the builder refuses to write.</b> A case whose proposition is not in its source.
 * A negative case whose content terms occur somewhere in the pool. A question that shares a
 * four-word sequence with a development question. A set without all five kinds, or without a
 * case for every pool document. A case with no source span is not a case, and the span is
 * recorded per gold claim.</p>
 *
 * <p><b>Where the artefact goes.</b> The dataset is written into the directory the
 * {@code matrix.c2a.dir} system property names; when that property is not set the
 * {@code MATRIX_C2A_DIR} environment variable is used, and without either the artefact goes to a
 * temporary directory that vanishes with the test. The {@code matrix.c2a.dump} property (or
 * {@code MATRIX_C2A_DUMP}) additionally writes the production claims the importer derived, one per
 * line, for a reader who wants to see what the extractor yields.</p>
 *
 * <p><b>What it does not establish.</b> It does not measure any candidate: no retrieval
 * improvement is designed, tuned or evaluated here. It measures the dataset itself — that its
 * gold is the source's, that its kinds are present, and that its questions are independent of
 * the development questions. It is written by one author and covers seven documents; it is
 * not a measure of generalisation beyond them.</p>
 */
class HeldOutSetBuilderTest {

    /** The workspace the pool is imported into. */
    private static final String WORKSPACE = "c2a-heldout-plate-tectonics";

    /** The research pool: the material the held-out set is built from. */
    private static final String POOL = AcquisitionCycle.RESEARCH_POOL;

    /** The development set the held-out questions must not resemble. */
    private static final String DEVELOPMENT_SET = "epistemic-eval/cases.json";

    /** The abstraction level the held-out questions are asked at. */
    private static final String LEVEL = "INTRODUCTORY";

    /** The shortest token sequence two questions may share. */
    private static final int MINIMUM_SHARED_TOKENS = 4;

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporary;

    // ── the shape of a case ────────────────────────────────────────────────────

    /** The five kinds the directive names. */
    enum Kind {
        /** Answerable from the pool, with gold claims. */
        POSITIVE,
        /** Answerable in principle, with no supporting claim: the expected disposition is an abstention. */
        NEGATIVE,
        /** A question whose premise the corpus contradicts or cannot support. */
        UNSUPPORTED,
        /** A question that admits more than one reading in the corpus. */
        AMBIGUOUS,
        /** Two claims in the corpus that cannot both hold. */
        CONTRADICTORY
    }

    /** The expected epistemic disposition of a case. */
    enum Disposition {
        ANSWERED, ABSTAINED, UNSUPPORTED, AMBIGUOUS, CONTRADICTORY
    }

    /** One gold claim: a production claim of a pool document, and the span it rests on. */
    record Gold(String file, String proposition) {
    }

    /** One reading of an ambiguous question: a document, the sentences that carry it, and what it says. */
    record Reading(String file, List<String> propositions, String reading) {
    }

    /** One case of the held-out set. */
    record CaseDef(String id, Kind kind, Disposition disposition, String question,
                   List<Gold> gold, List<Reading> readings, List<String> contentTerms,
                   String premise, String note) {

        static CaseDef positive(String id, String question, Gold... gold) {
            return new CaseDef(id, Kind.POSITIVE, Disposition.ANSWERED, question,
                    List.of(gold), List.of(), List.of(), null,
                    "answerable from the research pool; the gold is the sentence the source wrote");
        }

        static CaseDef negative(String id, String question, List<String> contentTerms, String note) {
            return new CaseDef(id, Kind.NEGATIVE, Disposition.ABSTAINED, question,
                    List.of(), List.of(), List.copyOf(contentTerms), null, note);
        }

        static CaseDef unsupported(String id, String question, String premise,
                                   List<Gold> counterEvidence, String note) {
            return new CaseDef(id, Kind.UNSUPPORTED, Disposition.UNSUPPORTED, question,
                    List.copyOf(counterEvidence), List.of(), List.of(), premise, note);
        }

        static CaseDef ambiguous(String id, String question, List<Reading> readings, String note) {
            List<Gold> gold = new ArrayList<>();
            for (Reading reading : readings) {
                for (String proposition : reading.propositions()) {
                    gold.add(new Gold(reading.file(), proposition));
                }
            }
            return new CaseDef(id, Kind.AMBIGUOUS, Disposition.AMBIGUOUS, question,
                    gold, List.copyOf(readings), List.of(), null, note);
        }

        static CaseDef contradictory(String id, String question, List<Gold> conflicting,
                                     String note) {
            return new CaseDef(id, Kind.CONTRADICTORY, Disposition.CONTRADICTORY, question,
                    List.copyOf(conflicting), List.of(), List.of(), null, note);
        }
    }

    // ── the cases ─────────────────────────────────────────────────────────────

    private static List<CaseDef> cases() {
        List<CaseDef> cases = new ArrayList<>();
        cases.add(CaseDef.positive("H-P01",
                "Which mechanism did Arthur Holmes advance for the movement of the continents, and what is said to power it?",
                new Gold("wikipedia-continental-drift.md", "In 1931, English geologist Arthur Holmes proposed mantle convection for that mechanism, which is now known to be powered by radioactive decay and primordial heat and a much smaller amount of heat from tidal heating.")));
        cases.add(CaseDef.positive("H-P02",
                "In the research pool, what did Wegener call the single landmass from which the continents later broke apart?",
                new Gold("wikipedia-continental-drift.md", "He proposed that the continents had once formed a single landmass, which he called Pangaea, before breaking apart and drifting to their present locations.")));
        cases.add(CaseDef.positive("H-P03",
                "By how much did a GPS receiver standing in Maui, Hawaii, shift over a fourteen-year period?",
                new Gold("wikipedia-continental-drift.md", "A GPS device placed in Maui, Hawaii, moved about 48 cm latitudinally and about 84 cm longitudinally over 14 years.")));
        cases.add(CaseDef.positive("H-P04",
                "How do the opening rates of fast, intermediate and slow spreading ridges compare in the research pool?",
                new Gold("wikipedia-seafloor-spreading.md", "Spreading rates determine if the ridge is fast, intermediate, or slow."), new Gold("wikipedia-seafloor-spreading.md", "As a general rule, fast ridges have spreading (opening) rates of more than 90 mm/year."), new Gold("wikipedia-seafloor-spreading.md", "Intermediate ridges have a spreading rate of 40–90 mm/year while slow spreading ridges have a rate less than 40 mm/year.")));
        cases.add(CaseDef.positive("H-P05",
                "Where does the research pool say new seafloor is continually formed, and what happens there?",
                new Gold("wikipedia-seafloor-spreading.md", "In locations where two plates move apart, at mid-ocean ridges, new seafloor is continually formed during seafloor spreading."), new Gold("wikipedia-seafloor-spreading.md", "At a spreading center, basaltic magma rises up the fractures and cools on the ocean floor to form new seabed.")));
        cases.add(CaseDef.positive("H-P06",
                "Which rock type crystallises at a ridge axis, and what lies beneath it in the lower oceanic crust?",
                new Gold("wikipedia-mid-ocean-ridge.md", "The crystallized magma forms a new crust of basalt known as MORB for mid-ocean ridge basalt, and gabbro below it in the lower oceanic crust."), new Gold("wikipedia-mid-ocean-ridge.md", "Mid-ocean ridge basalt is a tholeiitic basalt and is low in incompatible elements.")));
        cases.add(CaseDef.positive("H-P07",
                "How does a faster spreading rate change the magnesium and calcium balance of seawater?",
                new Gold("wikipedia-mid-ocean-ridge.md", "Fast spreading rates will expand the mid-ocean ridge causing basalt reactions with seawater to happen more rapidly."), new Gold("wikipedia-mid-ocean-ridge.md", "The magnesium/calcium ratio will be lower because more magnesium ions are being removed from seawater and consumed by the rock, and more calcium ions are being removed from the rock and released into seawater.")));
        cases.add(CaseDef.positive("H-P08",
                "Which survey team concluded that a rift valley runs down the middle of the Atlantic Ocean?",
                new Gold("wikipedia-mid-ocean-ridge.md", "A team led by Marie Tharp and Bruce Heezen concluded that there was an enormous mountain chain with a rift valley at its crest, running up the middle of the Atlantic Ocean."), new Gold("wikipedia-mid-ocean-ridge.md", "Also, crustal heat flow was higher here than elsewhere in the Atlantic Ocean basin.")));
        cases.add(CaseDef.positive("H-P09",
                "What is the Wadati-Benioff zone, and how deep does it reach?",
                new Gold("wikipedia-subduction.md", "Subduction zones are defined by an inclined zone of earthquakes, the Wadati–Benioff zone, that dips away from the trench and extends down below the volcanic arc to the 660-kilometer discontinuity."), new Gold("wikipedia-subduction.md", "Subduction zone earthquakes occur at greater depths (up to 600 km (370 mi)) than elsewhere on Earth (typically less than 20 km (12 mi) depth); such deep earthquakes may be driven by deep phase transformations, thermal runaway, or dehydration embrittlement.")));
        cases.add(CaseDef.positive("H-P10",
                "How does the water carried by a subducting slab generate magma beneath a volcanic arc?",
                new Gold("wikipedia-subduction.md", "Once the oceanic slab reaches about 100 km (62 mi) in depth, hydrous minerals become unstable and release fluids into the asthenosphere."), new Gold("wikipedia-subduction.md", "The fluids act as a flux for the rock within the asthenosphere and cause it to partially melt."), new Gold("wikipedia-subduction.md", "The supercritical water, which is hot and more buoyant than the surrounding rock, rises into the overlying mantle, where it lowers the melting temperature of the mantle rock, generating magma via flux melting.")));
        cases.add(CaseDef.positive("H-P11",
                "Which metamorphic path do subduction zones follow, and which facies zones does it cross?",
                new Gold("wikipedia-subduction.md", "Subduction zone metamorphism is characterized by a low temperature, high-ultrahigh pressure metamorphic path through the zeolite, prehnite-pumpellyite, blueschist, and eclogite facies stability zones of subducted oceanic crust."), new Gold("wikipedia-subduction.md", "The breakdown of hydrous mineral phases typically occurs at depths greater than 10 km (6.2 mi).")));
        cases.add(CaseDef.positive("H-P12",
                "How long does a single shallow mantle convection cycle take, and how does that compare with a deeper one?",
                new Gold("wikipedia-mantle-convection.md", "A single shallow convection cycle takes on the order of 50 million years, though deeper convection can be closer to 200 million years.")));
        cases.add(CaseDef.positive("H-P13",
                "What do the elevated helium-3 to helium-4 ratios of ocean island basalts tell geochemists about their source?",
                new Gold("wikipedia-mantle-convection.md", "The elevated He-3:He-4 ratio of ocean island basalts suggest that they must be sourced from a part of the Earth that has not previously been melted and reproduced in the same way as mid-ocean ridge basalts have been."), new Gold("wikipedia-mantle-convection.md", "This has been interpreted as their originating from a different less well-mixed region, suggested to be the lower mantle.")));
        cases.add(CaseDef.positive("H-P14",
                "At which temperature does magnetite lose its magnetic record, and how does that compare with the crystallisation of basalt?",
                new Gold("wikipedia-paleomagnetism.md", "The Curie temperature of magnetite, a spinel-group iron oxide, is about 580 °C (1,076 °F), whereas most basalt and gabbro are completely crystallized at temperatures below 900 °C (1,650 °F).")));
        cases.add(CaseDef.positive("H-P15",
                "How are the two varieties of detrital remanent magnetization told apart?",
                new Gold("wikipedia-paleomagnetism.md", "If the magnetization is acquired as the grains are deposited, the result is a depositional detrital remanent magnetization; if it is acquired soon after deposition, it is a post-depositional detrital remanent magnetization.")));
        cases.add(CaseDef.positive("H-P16",
                "Which physical process does the research pool name as the origin of slab pull, and what happens to the rest of the plate?",
                new Gold("wikipedia-slab-pull.md", "Slab pull is a geophysical mechanism whereby the cooling and subsequent densifying of a subducting tectonic plate produces a downward force along the rest of the plate.")));
        cases.add(CaseDef.positive("H-P17",
                "Which large plates keep moving although they are subducted nowhere, and what force is credited with their motion?",
                new Gold("wikipedia-slab-pull.md", "The North American Plate is nowhere being subducted, yet it is in motion, and likewise for the African, Eurasian and Antarctic Plates."), new Gold("wikipedia-slab-pull.md", "Ridge push is thought responsible for the motion of these plates.")));
        cases.add(CaseDef.positive("H-P18",
                "What did the 1975 inverse-theory study of Forsyth and Uyeda find about the relative strength of the driving forces?",
                new Gold("wikipedia-slab-pull.md", "In 1975 Forsyth and Uyeda used the inverse theory method to show that, of the many forces likely to be driving plate motion, slab pull was the strongest.")));
        cases.add(CaseDef.positive("H-P19",
                "Which minerals carry water into the deep mantle, and what happens to them during subduction?",
                new Gold("wikipedia-subduction.md", "Sea water seeps into oceanic lithosphere through fractures and pores, and reacts with minerals in the crust and mantle to form hydrous minerals (such as serpentine) that store water in their crystal structures."), new Gold("wikipedia-subduction.md", "Water is transported into the deep mantle via hydrous minerals in subducting slabs."), new Gold("wikipedia-subduction.md", "During subduction, a series of minerals in these slabs such as serpentine can be stable at different pressures within the slab geotherms, and may transport a significant amount of water into the Earth's interior.")));
        cases.add(CaseDef.negative("H-N01",
                "Which company installed the first transatlantic telegraph cable in 1866?",
                List.of("company", "installed", "transatlantic", "telegraph", "cable", "1866"),
                "no content term of this question occurs in any pool document; the expected disposition is an abstention"));
        cases.add(CaseDef.negative("H-N02",
                "Which studio recorded the first orchestral performance of the Sibelius violin concerto?",
                List.of("studio", "orchestral", "Sibelius", "violin", "concerto"),
                "no content term of this question occurs in any pool document; the expected disposition is an abstention"));
        cases.add(CaseDef.negative("H-N03",
                "How many wheat sheaves did Rosario export in its busiest year?",
                List.of("wheat", "sheaves", "Rosario", "export", "busiest"),
                "no content term of this question occurs in any pool document; the expected disposition is an abstention"));
        cases.add(CaseDef.negative("H-N04",
                "Which pharmaceutical company announced the trial of the substance S-1423 in 1997?",
                List.of("pharmaceutical", "company", "announced", "trial", "substance", "1997"),
                "no content term of this question occurs in any pool document; the expected disposition is an abstention"));
        cases.add(CaseDef.negative("H-N05",
                "Which football club recruited the retired midfielder from Zaragoza in 1994?",
                List.of("football", "club", "recruited", "midfielder", "Zaragoza", "1994"),
                "no content term of this question occurs in any pool document; the expected disposition is an abstention"));
        cases.add(CaseDef.negative("H-N06",
                "Which cathedral in Oviedo houses the medieval tapestry commissioned in 1388?",
                List.of("cathedral", "Oviedo", "medieval", "tapestry", "commissioned", "1388"),
                "no content term of this question occurs in any pool document; the expected disposition is an abstention"));
        cases.add(CaseDef.unsupported("H-U01",
                "Did the continents plow through the ocean floor, as Wegener originally proposed?",
                "that the continents plowed through the ocean crust",
                List.of(new Gold("wikipedia-seafloor-spreading.md", "This was impossible: oceanic crust is both more dense and more rigid than continental crust.")),
                "the corpus states the proposal and then denies it; the expected disposition is to flag the premise, not to answer as if it held"));
        cases.add(CaseDef.unsupported("H-U02",
                "Is subduction known to take place on planets other than Earth?",
                "that subduction is known to occur on planets other than Earth",
                List.of(new Gold("wikipedia-subduction.md", "So far, Earth is the only planet where subduction is known to occur, and subduction zones are its most important tectonic feature.")),
                "the corpus states the opposite; the expected disposition is to flag the premise"));
        cases.add(CaseDef.unsupported("H-U03",
                "Do the continents gain area as they drift, in the manner the Expanding Earth theory proposed?",
                "that the continents grow in area as they drift",
                List.of(new Gold("wikipedia-continental-drift.md", "This led Mantovani to propose a now-discredited Expanding Earth theory."), new Gold("wikipedia-continental-drift.md", "Continental drift without expansion was proposed by Frank Bursley Taylor, who suggested in 1908 (published in 1910) that the continents were moved into their present positions by a process of \"continental creep\", later proposing a mechanism of increased tidal forces during the Cretaceous dragging the crust towards the equator.")),
                "the corpus records the theory as discredited and gives a drift-without-expansion account instead"));
        cases.add(CaseDef.unsupported("H-U04",
                "Are the lavas of intraplate volcanoes chemically identical to mid-ocean ridge basalts?",
                "that intraplate lavas are chemically identical to mid-ocean ridge basalts",
                List.of(new Gold("wikipedia-mantle-convection.md", "Many geochemistry studies have argued that the lavas erupted in intraplate areas are different in composition from shallow-derived mid-ocean ridge basalts."), new Gold("wikipedia-mantle-convection.md", "This debate is linked to the controversy regarding whether intraplate volcanism is caused by shallow, upper mantle processes or by plumes from the lower mantle.")),
                "the corpus states that the two differ and records the open controversy; the expected disposition is to flag the premise"));
        cases.add(CaseDef.unsupported("H-U05",
                "Do the continents and the oceans exchange places over geological time, as the fixists of the nineteenth century maintained?",
                "that the continents and the oceans exchanged places over geological time",
                List.of(new Gold("wikipedia-continental-drift.md", "This suggested that the oceans were a permanent feature of the Earth's surface, rather than having \"changed places\" with the continents."), new Gold("wikipedia-continental-drift.md", "Dana was enormously influential in America—his Manual of Mineralogy is still in print in revised form—and the theory became known as the Permanence theory.")),
                "the corpus states the opposite and names the theory that denied it; the expected disposition is to flag the premise"));
        cases.add(CaseDef.unsupported("H-U06",
                "Do the reversals of the geomagnetic field occur at regular intervals through the history of the Earth?",
                "that the geomagnetic reversals occur at regular intervals",
                List.of(new Gold("wikipedia-paleomagnetism.md", "Reversals have occurred at irregular intervals throughout Earth's history."), new Gold("wikipedia-paleomagnetism.md", "The age and pattern of these reversals is known from the study of sea floor spreading zones and the dating of volcanic rocks.")),
                "the corpus states that they are irregular; the expected disposition is to flag the premise"));
        cases.add(CaseDef.ambiguous("H-A01",
                "In the research pool, which plate does the word slab name?",
                List.of(
                        new Reading("wikipedia-subduction.md", List.of("The lower plate itself is the subject.", "It subducts, in the sense of retreat, or removes itself, and while doing so, is the \"subducting plate\"."), "the lower, subducting plate"),
                        new Reading("wikipedia-subduction.md", List.of("Moreover, the word slab is specifically attached to the \"subducting plate\", even though in English the upper plate is just as much of a slab.", "The upper plate is left hanging, so to speak."), "the overriding upper plate, which the corpus says is just as much a slab")
                ),
                "the corpus attaches the word slab to the subducting plate and says the upper plate is just as much a slab; the expected disposition is to report both readings"));
        cases.add(CaseDef.ambiguous("H-A02",
                "In the research pool, which kinds of arc are distinguished, and what tells them apart?",
                List.of(
                        new Reading("wikipedia-subduction.md", List.of("Island arcs (intraoceanic or primitive arcs) are produced by the subduction of oceanic lithosphere beneath another oceanic lithosphere (ocean-ocean subduction) while continental arcs (Andean arcs) form during the subduction of oceanic lithosphere beneath a continental lithosphere (ocean-continent subduction)."), "island arcs and continental arcs, told apart by what subducts beneath what"),
                        new Reading("wikipedia-subduction.md", List.of("Accretionary arcs have a well-developed forearc basin behind the accretionary wedge, while the forearc basin is poorly developed in non-accretionary arcs."), "accretionary and non-accretionary arcs, told apart by the forearc basin behind the accretionary wedge")
                ),
                "the corpus distinguishes island from continental arcs, and accretionary from non-accretionary arcs; the expected disposition is to report both readings"));
        cases.add(CaseDef.ambiguous("H-A03",
                "In the research pool, what does the word ridge stand for?",
                List.of(
                        new Reading("wikipedia-mid-ocean-ridge.md", List.of("A mid-ocean ridge (MOR) is a seafloor mountain system formed by plate tectonics."), "a seafloor mountain system"),
                        new Reading("wikipedia-mid-ocean-ridge.md", List.of("Mid-ocean ridge basalt is a tholeiitic basalt and is low in incompatible elements."), "a rock, mid-ocean ridge basalt"),
                        new Reading("wikipedia-mid-ocean-ridge.md", List.of("Ridge push refers to the gravitational sliding of the ocean plate that is raised above the hotter asthenosphere, thus creating a body force causing sliding of the plate downslope."), "a force, ridge push")
                ),
                "the corpus uses ridge for a mountain system, for a rock and for a force; the expected disposition is to report the readings rather than to pick one"));
        cases.add(CaseDef.ambiguous("H-A04",
                "In the research pool, what does the word plate denote?",
                List.of(
                        new Reading("wikipedia-subduction.md", List.of("Individual plates often include both regions of the oceanic lithosphere and continental lithosphere."), "a real lithospheric plate"),
                        new Reading("wikipedia-seafloor-spreading.md", List.of("The plate has a constant temperature at its base and spreading edge."), "the model plate of the cooling calculation"),
                        new Reading("wikipedia-mid-ocean-ridge.md", List.of("Plate tectonics was a suitable explanation for seafloor spreading, and the acceptance of plate tectonics by the majority of geologists resulted in a major paradigm shift in geological thinking."), "the theory, plate tectonics")
                ),
                "the corpus uses plate for a real lithospheric plate, for the model plate of the cooling calculation and for the theory; the expected disposition is to report the readings rather than to pick one"));
        cases.add(CaseDef.contradictory("H-C01",
                "Does mantle convection cause the movement of the plates?",
                List.of(new Gold("wikipedia-mantle-convection.md", "Mantle convection causes tectonic plates to move around the Earth's surface."), new Gold("wikipedia-mid-ocean-ridge.md", "Computer modeling of the plates and mantle motions suggest that plate motion and mantle convection are not connected, and the main plate driving force is slab pull.")),
                "the two claims cannot both hold: one says mantle convection moves the plates, the other says the two are not connected and that slab pull is the main driving force; the expected disposition is to say so rather than to pick one silently"));
        cases.add(CaseDef.contradictory("H-C02",
                "How long is the global system of mid-ocean ridges, according to the research pool?",
                List.of(new Gold("wikipedia-subduction.md", "Oceanic subduction zones are located along 55,000 km (34,000 mi) of convergent plate margins, almost equal to the cumulative plate formation rate of 60,000 km (37,000 mi) of mid-ocean ridges."), new Gold("wikipedia-mid-ocean-ridge.md", "The continuous mountain range is 65,000 km (40,400 mi) long (several times longer than the Andes, the longest continental mountain range), and the total length of the oceanic ridge system is 80,000 km (49,700 mi) long.")),
                "the two articles state the length of the global ridge system differently (60,000 km against 80,000 km; the second sentence of the same claim also gives 65,000 km for the continuous mountain range) and the corpus does not reconcile them; the expected disposition is to report both figures with their sources"));
        cases.add(CaseDef.contradictory("H-C03",
                "Is subduction the driving force behind plate tectonics, or is it mantle convection?",
                List.of(new Gold("wikipedia-subduction.md", "Subduction is the driving force behind plate tectonics, and without it, plate tectonics could not occur."), new Gold("wikipedia-mantle-convection.md", "Mantle convection causes tectonic plates to move around the Earth's surface.")),
                "the two claims cannot both hold: one says subduction is the driving force without which plate tectonics could not occur, the other says mantle convection moves the plates; the expected disposition is to say so rather than to pick one silently"));
        return List.copyOf(cases);
    }

    // ── the build ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B6 — the independent held-out set, built from the research pool and verified")
    void buildHeldOutSet() throws Exception {
        List<CaseDef> definitions = cases();

        // ── the workspace, assembled through the production acquisition path ──
        Path database = temporary.resolve("heldout.sqlite");
        KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString());
        ClasspathCorpus pool = ClasspathCorpus.extract(POOL);
        Map<String, ClasspathCorpus.Entry> entries = new LinkedHashMap<>();
        List<Map<String, Object>> acquisitions = new ArrayList<>();
        for (ClasspathCorpus.Entry entry : pool.entries()) {
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
            // the production importer derived claims from this document
            assertThat(result.claimsAdded()).as("claims derived from %s", entry.file()).isPositive();
        }
        assertThat(acquisitions).hasSize(7);

        KnowledgeSnapshot snapshot = workbench.workspace(WORKSPACE);
        Map<String, Claim> claimsByProposition = new LinkedHashMap<>();
        for (Claim claim : snapshot.claims()) {
            claimsByProposition.put(claim.proposition(), claim);
        }
        Map<String, Source> sourcesById = new LinkedHashMap<>();
        for (Source source : snapshot.sources()) {
            sourcesById.put(source.id(), source);
        }

        // the raw text of every pool document, for the verbatim check
        Map<String, String> rawText = new LinkedHashMap<>();
        for (ClasspathCorpus.Entry entry : pool.entries()) {
            rawText.put(entry.file(), readResource(POOL + "/" + entry.file()));
        }

        String dump = dumpPath();
        if (dump != null) {
            List<String> lines = new ArrayList<>();
            for (Claim claim : snapshot.claims()) {
                Source source = sourcesById.get(claim.introducedBySourceId());
                lines.add(claim.id() + "\t" + (source == null ? "?" : source.title()) + "\t"
                        + claim.proposition());
            }
            lines.sort(Comparator.naturalOrder());
            Files.writeString(Path.of(dump), String.join("\n", lines), StandardCharsets.UTF_8);
        }

        // ── the cases, each verified before it is written out ────────────────
        assertThat(definitions).isNotEmpty();
        List<Map<String, Object>> written = new ArrayList<>();
        Set<String> questionTexts = new HashSet<>();
        Set<String> caseIds = new HashSet<>();
        Map<String, Integer> perKind = new LinkedHashMap<>();
        Map<String, Integer> perFile = new LinkedHashMap<>();
        Map<String, Integer> casesPerFile = new LinkedHashMap<>();
        for (CaseDef definition : definitions) {
            assertThat(definition.question()).isNotBlank();
            assertThat(questionTexts.add(definition.question().strip()))
                    .as("the question of %s is repeated", definition.id()).isTrue();
            assertThat(caseIds.add(definition.id()))
                    .as("the id %s is repeated", definition.id()).isTrue();
            // A CASE WITHOUT A SPAN IS NOT A CASE.
            if (definition.kind() != Kind.NEGATIVE) {
                assertThat(definition.gold()).as("the case %s names no source span",
                        definition.id()).isNotEmpty();
            }

            List<Map<String, Object>> gold = new ArrayList<>();
            for (Gold entry : definition.gold()) {
                Claim claim = claimsByProposition.get(entry.proposition());
                assertThat(claim).as("the production importer derived no claim for the gold "
                        + "proposition of %s: %s", definition.id(), entry.proposition()).isNotNull();
                ClasspathCorpus.Entry entryOfFile = entries.get(entry.file());
                assertThat(entryOfFile).as("%s names no pool document", definition.id()).isNotNull();
                Source source = sourcesById.get(claim.introducedBySourceId());
                assertThat(source).as("the claim of %s has no source", definition.id()).isNotNull();
                assertThat(source.title()).isEqualTo(entry.file());
                // the claim really was derived from that document
                assertThat(claim.introducedBySourceId())
                        .as("the claim of %s came from another document", definition.id())
                        .isEqualTo(DocumentImporter.sourceId(WORKSPACE, entryOfFile.sha256()));
                // THE PROPOSITION IS VERBATIM IN ITS SOURCE
                String document = rawText.get(entry.file());
                assertThat(document).as("the pool document %s was not read", entry.file()).isNotNull();
                assertThat(document).as("the gold proposition of %s is not verbatim in %s",
                        definition.id(), entry.file()).contains(entry.proposition());

                Map<String, Object> goldEntry = new LinkedHashMap<>();
                goldEntry.put("claimId", claim.id());
                goldEntry.put("proposition", claim.proposition());
                goldEntry.put("sourceSpan", entry.proposition());
                goldEntry.put("claimContext", claim.context());
                goldEntry.put("sourceId", source.id());
                goldEntry.put("sourceTitle", source.title());
                goldEntry.put("sourceLicence", entryOfFile.licence());
                goldEntry.put("sourceLicenceUrl", entryOfFile.licenceUrl());
                goldEntry.put("extractionMethod", claim.extractionMethod());
                goldEntry.put("claimStatus", claim.status().name());
                gold.add(goldEntry);
                perFile.merge(entry.file(), 1, Integer::sum);
            }

            // ── the negative control: the terms of a negative case are absent ──
            if (definition.kind() == Kind.NEGATIVE) {
                assertThat(definition.contentTerms()).as("the negative case %s names no terms",
                        definition.id()).isNotEmpty();
                for (String term : definition.contentTerms()) {
                    for (Map.Entry<String, String> document : rawText.entrySet()) {
                        assertThat(document.getValue().toLowerCase(Locale.ROOT))
                                .as("the content term '%s' of the negative case %s occurs in %s",
                                        term, definition.id(), document.getKey())
                                .doesNotContain(term.toLowerCase(Locale.ROOT));
                    }
                }
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", definition.id());
            entry.put("kind", definition.kind().name());
            entry.put("level", LEVEL);
            entry.put("question", definition.question());
            entry.put("expectedDisposition", definition.disposition().name());
            entry.put("goldClaims", gold);
            Set<String> caseFiles = new LinkedHashSet<>();
            for (Gold g : definition.gold()) {
                caseFiles.add(g.file());
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
                        assertThat(rawText.get(reading.file()))
                                .as("the reading of %s is not verbatim in %s", definition.id(),
                                        reading.file()).contains(proposition);
                        Map<String, Object> span = new LinkedHashMap<>();
                        span.put("proposition", proposition);
                        span.put("sourceSpan", proposition);
                        span.put("claimId", claim.id());
                        span.put("claimContext", claim.context());
                        span.put("sourceId", claim.introducedBySourceId());
                        spans.add(span);
                    }
                    Map<String, Object> written1 = new LinkedHashMap<>();
                    written1.put("reading", reading.reading());
                    written1.put("file", reading.file());
                    written1.put("spans", spans);
                    readings.add(written1);
                }
                entry.put("readings", readings);
            }
            if (definition.premise() != null) {
                entry.put("disputedPremise", definition.premise());
            }
            if (!definition.contentTerms().isEmpty()) {
                entry.put("contentTerms", definition.contentTerms());
            }
            entry.put("note", definition.note());
            written.add(entry);
            perKind.merge(definition.kind().name(), 1, Integer::sum);
        }

        // ── the shape of the set ──────────────────────────────────────────────
        assertThat(written).hasSizeGreaterThanOrEqualTo(24);
        assertThat(written).hasSizeLessThanOrEqualTo(40);
        for (Kind kind : Kind.values()) {
            assertThat(perKind.getOrDefault(kind.name(), 0))
                    .as("the set has no %s case", kind).isPositive();
        }
        for (ClasspathCorpus.Entry entry : pool.entries()) {
            assertThat(casesPerFile.getOrDefault(entry.file(), 0))
                    .as("the set has no case for %s", entry.file()).isPositive();
        }

        // ── the questions are independent of the development questions ────────
        List<String> developmentTexts = developmentTexts();
        assertThat(developmentTexts).isNotEmpty();
        for (Map<String, Object> entry : written) {
            String question = (String) entry.get("question");
            Set<String> questionGrams = ngrams(question);
            for (String development : developmentTexts) {
                Set<String> shared = new LinkedHashSet<>(questionGrams);
                shared.retainAll(ngrams(development));
                assertThat(shared).as("the question of %s shares a %d-word sequence with a "
                        + "development question: %s", entry.get("id"), MINIMUM_SHARED_TOKENS,
                        shared).isEmpty();
            }
        }

        // ── the artefact ──────────────────────────────────────────────────────
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("dataset", "c2a-heldout-plate-tectonics");
        dataset.put("version", "1.0");
        dataset.put("date", "2026-11-15");
        dataset.put("purpose", "an independent, source-verified held-out evaluation set for the "
                + "retrieval subsystem, built before any candidate exists");
        dataset.put("manifest", manifest(acquisitions, perKind, perFile, casesPerFile, written));
        dataset.put("cases", written);

        String json = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(dataset);
        Path out = outputDirectory();
        Files.createDirectories(out);
        Path artefact = out.resolve("C2A-HELDOUT-SET.json");
        Files.writeString(artefact, json, StandardCharsets.UTF_8);
        assertThat(Files.readString(artefact)).isEqualTo(json);

        System.out.println("[B6] " + artefact + " (" + written.size() + " cases)");
        System.out.println("[B6] kinds " + perKind);
        System.out.println("[B6] cases per document " + casesPerFile);
        System.out.println("[B6] gold claims per document " + perFile);
    }

    // ── the manifest inside the dataset ───────────────────────────────────────

    private Map<String, Object> manifest(List<Map<String, Object>> acquisitions,
                                         Map<String, Integer> perKind,
                                         Map<String, Integer> perFile,
                                         Map<String, Integer> casesPerFile,
                                         List<Map<String, Object>> written) throws Exception {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("corpus", POOL);
        manifest.put("corpusManifest", "matrix-core/src/main/resources/" + POOL + "/corpus-manifest.json");
        manifest.put("corpusManifestSha256", sha256(resourceBytes(POOL + "/corpus-manifest.json")));
        manifest.put("workspace", WORKSPACE);
        manifest.put("acquisitionPath", "io.matrix.knowledge.acquisition.AcquisitionCycle.RESEARCH_POOL"
                + " -> io.matrix.knowledge.acquisition.ClasspathCorpus.extract"
                + " -> io.matrix.knowledge.KnowledgeWorkbench.importDocument");
        manifest.put("builder", "io.matrix.knowledge.HeldOutSetBuilderTest");
        manifest.put("method", "the pool documents are imported through the production acquisition "
                + "path; every gold claim is the production claim whose proposition equals a "
                + "sentence of a pool document, and the builder proves the sentence is verbatim in "
                + "that document before the case is written out");
        manifest.put("verifications", List.of(
                "the gold proposition occurs verbatim in its pool document",
                "the gold claim exists in the production workspace and was introduced by that document's source",
                "a negative case's content terms occur in no pool document",
                "a held-out question shares no normalised token sequence of length >= 4 with any development question",
                "the five kinds are present, the set is not empty, and every pool document has a case"));
        // THE ARTEFACT MUST BE BYTE-REPRODUCIBLE.
        //
        // Map.of(...) has an unspecified iteration order that is not stable between JVM runs, so a
        // dataset written with it has a different sha256 on every build - and another document
        // that cites that digest would be citing a digest that no longer exists. The maps here
        // are LinkedHashMaps: the order is the order written below, on every run.
        Map<String, Object> kinds = new LinkedHashMap<>();
        kinds.put("POSITIVE", "answerable from the research pool; the expected disposition is ANSWERED "
                + "and the cited claims must carry the gold propositions");
        kinds.put("NEGATIVE", "answerable in principle but with no supporting claim; the content terms "
                + "occur in no pool document and the expected disposition is ABSTAINED");
        kinds.put("UNSUPPORTED", "a question whose premise the corpus contradicts or cannot support; "
                + "the expected disposition is UNSUPPORTED and the disputed premise is named");
        kinds.put("AMBIGUOUS", "a question that admits more than one reading in the corpus; the "
                + "expected disposition is AMBIGUOUS and both readings are recorded with their spans");
        kinds.put("CONTRADICTORY", "two claims in the corpus that cannot both hold; the expected "
                + "disposition is CONTRADICTORY and both claims are recorded with their spans");
        manifest.put("kinds", kinds);
        manifest.put("documents", acquisitions);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("cases", written.size());
        counts.put("distinctGoldClaims", distinctGoldClaims(written));
        counts.put("distinctSourceDocuments", perFile.size());
        counts.put("casesPerKind", perKind);
        counts.put("casesPerDocument", casesPerFile);
        counts.put("goldClaimsPerDocument", perFile);
        manifest.put("counts", counts);
        Map<String, Object> development = new LinkedHashMap<>();
        development.put("file", "matrix-core/src/main/resources/" + DEVELOPMENT_SET);
        development.put("sha256", sha256(resourceBytes(DEVELOPMENT_SET)));
        development.put("version", developmentVersion());
        development.put("cases", developmentCaseCount());
        development.put("textsCompared", developmentTexts().size());
        development.put("textsComparedNote", "every development question and every development "
                + "assertion; the held-out questions share no normalised token sequence of length "
                + ">= 4 with any of them");
        manifest.put("developmentSet", development);
        return manifest;
    }

    private long distinctGoldClaims(List<Map<String, Object>> written) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> entry : written) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> gold = (List<Map<String, Object>>) entry.get("goldClaims");
            for (Map<String, Object> claim : gold) {
                ids.add((String) claim.get("claimId"));
            }
        }
        return ids.size();
    }

    /** How many cases the development set holds. */
    private int developmentCaseCount() throws IOException {
        JsonNode root = JSON.readTree(resourceBytes(DEVELOPMENT_SET));
        return root.path("cases").size();
    }

    private String developmentVersion() throws IOException {
        JsonNode root = JSON.readTree(resourceBytes(DEVELOPMENT_SET));
        return root.path("version").asText("unknown");
    }

    // ── the development set, for the independence check ───────────────────────

    /** The texts of the development set: its questions and its assertions. */
    private List<String> developmentTexts() throws IOException {
        JsonNode root = JSON.readTree(resourceBytes(DEVELOPMENT_SET));
        List<String> texts = new ArrayList<>();
        for (JsonNode entry : root.path("cases")) {
            String question = entry.path("question").asText("");
            if (!question.isBlank()) {
                texts.add(question);
            }
            JsonNode body = entry.path("call").path("body");
            String assertion = body.path("assertion").asText("");
            if (!assertion.isBlank()) {
                texts.add(assertion);
            }
        }
        return texts;
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
        String directory = System.getProperty("matrix.c2a.dir");
        if (directory == null || directory.isBlank()) {
            directory = System.getenv("MATRIX_C2A_DIR");
        }
        return directory == null || directory.isBlank()
                ? temporary.resolve("out") : Path.of(directory);
    }

    /** Where the claim dump goes, when one is asked for. */
    private static String dumpPath() {
        String dump = System.getProperty("matrix.c2a.dump");
        if (dump == null || dump.isBlank()) {
            dump = System.getenv("MATRIX_C2A_DUMP");
        }
        return dump == null || dump.isBlank() ? null : dump;
    }

    private static byte[] resourceBytes(String resource) throws IOException {
        ClassLoader loader = HeldOutSetBuilderTest.class.getClassLoader();
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
