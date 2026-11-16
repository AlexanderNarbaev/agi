package io.matrix.knowledge.epistemic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round 4 — the pinned numbers, re-measured, and the two rules probed for false positives.
 *
 * <p>Every question and every gold proposition of the frozen set is READ from the artefact at
 * run time: a probe may name the artefact, but it may not carry the set's content.</p>
 */
class AdversarialProbeRound4Test {

    private static final String EPISTEMIC_DATASET =
            "docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json";

    private static KnowledgeWorkbench workbench;
    private static List<JsonNode> cases;
    private static String seedWorkspace;
    private static String poolWorkspace;

    @TempDir
    Path directory;

    @BeforeAll
    static void prepare() throws Exception {
        Path database = Files.createTempDirectory("adversarial-probe-r4-").resolve("knowledge.sqlite");
        workbench = KnowledgeWorkbench.at(database.toString());
        seedWorkspace = "r4-seed";
        poolWorkspace = "r4-pool";
        ClasspathCorpus seed = ClasspathCorpus.extract(AcquisitionCycle.SEED_CORPUS);
        for (ClasspathCorpus.Entry entry : seed.entries()) {
            workbench.importDocument(seedWorkspace, entry.path(), entry.file(), entry.language());
        }
        ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
        for (ClasspathCorpus.Entry entry : pool.entries()) {
            workbench.importDocument(poolWorkspace, entry.path(), entry.file(), entry.language());
        }
        workbench.loadBundledPackage("photosynthesis");
        workbench.loadBundledPackage("logic");
        JsonNode root = new ObjectMapper().readTree(
                Files.readString(RepositoryPaths.root().resolve(EPISTEMIC_DATASET)));
        cases = new ArrayList<>();
        for (JsonNode node : root.get("cases")) {
            cases.add(node);
        }
    }

    @Test
    @DisplayName("R4-1: the plants premise is unsupported, the two false premises are refuted")
    void thePlantsAndTheFalsePremises() {
        System.out.println("### R4-1 - the three photosynthesis/logic premises");
        Answer plants = workbench.ask("photosynthesis",
                "Since plants release oxygen during the day, what does a plant release during the day?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  the plants premise: answered=" + plants.answered()
                + " status=" + statusOf(plants));
        assertTrue(plants.answered(), "the plants premise was refused");
        assertEquals("UNSUPPORTED", statusOf(plants),
                "the plants premise was not reported as unsupported: " + reasonOf(plants));
        Answer false1 = workbench.ask("photosynthesis",
                "Since photosynthesis produces oxygen from carbon dioxide, what does a plant release during the day?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  the false photosynthesis premise: answered=" + false1.answered()
                + " status=" + statusOf(false1));
        assertEquals("REFUTED", statusOf(false1), "the false premise was not refuted");
        assertFalse(false1.answered(), "the false premise was answered");
        Answer false2 = workbench.ask("logic",
                "Since every valid argument has true premises, what are the premises of modus ponens?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  the false logic premise: answered=" + false2.answered()
                + " status=" + statusOf(false2));
        assertEquals("REFUTED", statusOf(false2), "the false premise was not refuted");
        assertFalse(false2.answered(), "the false premise was answered");
    }

    @Test
    @DisplayName("R4-2: none of the corpus-stated premises is called refuted")
    void noneOfTheCorpusStatedPremisesIsCalledRefuted() {
        System.out.println("### R4-2 - nine premises the corpora STATE");
        String[][] probes = {
                {"photosynthesis",
                        "Since photosynthesis stores energy in chemical bonds, what is the role of RuBisCO?"},
                {"photosynthesis",
                        "Since RuBisCO fixes carbon dioxide, what is the role of RuBisCO?"},
                {"photosynthesis",
                        "Since plants release oxygen during the day, what does a plant release during the day?"},
                {"logic",
                        "Since an argument of the modus ponens form is valid, what are the premises of modus ponens?"},
                {"logic",
                        "Since validity says nothing about whether the premises of an argument are true, what are the premises of modus ponens?"},
                {poolWorkspace,
                        "Since oceanic crust is created at spreading centers, what takes place along the Mid-Atlantic Ridge?"},
                {poolWorkspace,
                        "Since subduction is the driving force behind plate tectonics, what does the corpus say about the Pacific plate?"},
                {poolWorkspace, questionOf("E-P01")},
                {seedWorkspace,
                        "Since divergent boundaries are where new crust is generated, what happens where Mid-Atlantic Ridge runs?"},
        };
        int refuted = 0;
        for (String[] pair : probes) {
            Answer answer = workbench.ask(pair[0], pair[1], AbstractionLevel.INTRODUCTORY);
            String status = statusOf(answer);
            if ("REFUTED".equals(status)) {
                refuted++;
                System.out.println("  REFUTED: " + pair[1]);
                System.out.println("     reason=" + oneLine(reasonOf(answer)));
            } else {
                System.out.println("  " + status + " answered=" + answer.answered()
                        + " | " + oneLine(pair[1]));
            }
            assertNoPath(answer);
        }
        System.out.println("  premises called refuted: " + refuted + " of " + probes.length);
        assertEquals(0, refuted, "a premise the corpus states was called refuted");
    }

    @Test
    @DisplayName("R4-3: the ambiguity reaches the caller and the gold readings match")
    void theAmbiguityReachesTheCallerAndTheGoldMatches() {
        for (String id : List.of("E-A01", "E-A02")) {
            JsonNode heldOut = caseOf(id);
            Answer answer = workbench.ask(seedWorkspace, heldOut.get("question").asText(),
                    AbstractionLevel.INTRODUCTORY);
            String everything = answer.statement() + " " + answer.abstentionReason() + " "
                    + answer.limitations() + " " + answer.gaps()
                    + (answer.premise() == null ? "" : " " + answer.premise().reason()
                            + " " + answer.premise().readings());
            int named = 0;
            int matched = 0;
            for (JsonNode reading : heldOut.get("readings")) {
                for (JsonNode span : reading.get("spans")) {
                    named++;
                    if (everything.contains(span.get("proposition").asText())) {
                        matched++;
                    }
                }
            }
            boolean saysAmbiguous = everything.toLowerCase().contains("ambig")
                    || everything.toLowerCase().contains("more than one reading")
                    || everything.toLowerCase().contains("several reading");
            System.out.println("### R4-3 - " + id + ": status=" + statusOf(answer)
                    + " readings=" + (answer.premise() == null ? 0 : answer.premise().readings().size())
                    + " goldMatched=" + matched + "/" + named
                    + " saysAmbiguous=" + saysAmbiguous);
            System.out.println("  reason=" + oneLine(reasonOf(answer)));
            assertEquals("AMBIGUOUS", statusOf(answer), "the ambiguity was not reported");
            assertTrue(saysAmbiguous, "the reason does not name the ambiguity");
            assertNoPath(answer);
        }
    }

    @Test
    @DisplayName("R4-4: the same-assertion rule does not refute a premise the corpus states")
    void theSameAssertionRuleDoesNotRefuteAStatedPremise() {
        System.out.println("### R4-4 - the same-assertion rule, probed for false positives");
        // (a) a premise that is TRUE and shares most of the misconception's words: the
        // oxygen comes from the WATER. The misconception says it comes from the carbon
        // dioxide. The two differ in two words — and the premise is the true one.
        Answer water = workbench.ask("photosynthesis",
                "Since the oxygen a plant releases comes from the water it absorbs, what does a plant release during the day?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  (a) the water premise: answered=" + water.answered()
                + " status=" + statusOf(water));
        System.out.println("     reason=" + oneLine(reasonOf(water)));
        // (b) the synonym of the misconception: the same assertion in other words.
        Answer synonym = workbench.ask("photosynthesis",
                "Since the oxygen a plant releases comes from the carbon dioxide it takes in, what does a plant release during the day?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  (b) the synonym premise: answered=" + synonym.answered()
                + " status=" + statusOf(synonym));
        // (c) the Russian formulation of the misconception, built from the lexicon's own
        // counterparts.
        Answer russian = workbench.ask("photosynthesis",
                "Поскольку кислород растения происходит из углекислого газа, который растение поглощает, "
                        + "что растение выделяет днём?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  (c) the Russian misconception: answered=" + russian.answered()
                + " status=" + statusOf(russian));
        System.out.println("     reason=" + oneLine(reasonOf(russian)));
    }

    @Test
    @DisplayName("R4-4a: the water premise — a TRUE premise the corpus states")
    void theWaterPremiseIsNotRefuted() {
        Answer water = workbench.ask("photosynthesis",
                "Since the oxygen a plant releases comes from the water it absorbs, what does a plant release during the day?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("### R4-4a - the water premise: answered=" + water.answered()
                + " status=" + statusOf(water));
        System.out.println("  reason=" + oneLine(reasonOf(water)));
        assertFalse("REFUTED".equals(statusOf(water)),
                "a premise the corpus states was called refuted: " + reasonOf(water));
    }

    @Test
    @DisplayName("R4-5: the ambiguity rule on the cases the implementer names")
    void theAmbiguityRuleOnTheNamedCases() {
        System.out.println("### R4-5 - E-S02 and E-R02");
        for (String id : List.of("E-S02", "E-R02")) {
            JsonNode heldOut = caseOf(id);
            System.out.println("  " + id + " expects " + heldOut.get("expectedDisposition").asText());
            Answer answer = workbench.ask(seedWorkspace, heldOut.get("question").asText(),
                    AbstractionLevel.INTRODUCTORY);
            System.out.println("     observed=" + statusOf(answer)
                    + " answered=" + answer.answered());
            System.out.println("     reason=" + oneLine(reasonOf(answer)));
            assertNoPath(answer);
        }
    }

    // ── the helpers ──────────────────────────────────────────────────────────

    private static JsonNode caseOf(String id) {
        for (JsonNode heldOut : cases) {
            if (id.equals(heldOut.get("id").asText())) {
                return heldOut;
            }
        }
        throw new IllegalStateException("the frozen set holds no case " + id);
    }

    private static String questionOf(String id) {
        return caseOf(id).get("question").asText();
    }

    private static String statusOf(Answer answer) {
        return answer.premise() == null ? "NONE" : String.valueOf(answer.premise().status());
    }

    private static String reasonOf(Answer answer) {
        return answer.premise() == null ? "" : String.valueOf(answer.premise().reason());
    }

    private static void assertNoPath(Answer answer) {
        String body = answer.statement() + " " + answer.abstentionReason() + " "
                + answer.limitations() + " " + answer.gaps();
        for (String marker : List.of("/home/", "/root/", "/tmp/", "file:/")) {
            assertFalse(body.contains(marker), "the response carries the path marker " + marker);
        }
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
