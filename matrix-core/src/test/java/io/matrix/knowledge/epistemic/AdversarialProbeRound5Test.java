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
 * Round 5 — the distinguishing-terms rule: its false positives and its false negatives.
 *
 * <p>Every question of the frozen set is READ from the artefact at run time; a probe may name
 * the artefact, but it may not carry the set's content.</p>
 */
class AdversarialProbeRound5Test {

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
        Path database = Files.createTempDirectory("adversarial-probe-r5-").resolve("knowledge.sqlite");
        workbench = KnowledgeWorkbench.at(database.toString());
        seedWorkspace = "r5-seed";
        poolWorkspace = "r5-pool";
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
    @DisplayName("R5-1: the pinned numbers")
    void thePinnedNumbers() {
        System.out.println("### R5-1 - the pinned numbers");
        // (a) the water premise: the corpus STATES it — the oxygen comes from the water.
        Answer water = workbench.ask("photosynthesis",
                "Since the oxygen a plant releases comes from the water it absorbs, what does a plant release during the day?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  (a) the water premise: answered=" + water.answered()
                + " status=" + statusOf(water));
        assertTrue(water.answered(), "the water premise was refused");
        assertEquals("UNSUPPORTED", statusOf(water),
                "the water premise was not reported as unsupported: " + reasonOf(water));
        // (b) the synonym control: the misconception in other words — it must still be refuted.
        Answer synonym = workbench.ask("photosynthesis",
                "Since the oxygen a plant releases comes from the carbon dioxide it takes in, what does a plant release during the day?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  (b) the synonym of the misconception: answered=" + synonym.answered()
                + " status=" + statusOf(synonym));
        assertEquals("REFUTED", statusOf(synonym),
                "the synonym of the misconception was not refuted: " + reasonOf(synonym));
        // (c) the Russian misconception, in the reviewer's own wording and in the
        // lexicon's case forms.
        Answer russian = workbench.ask("photosynthesis",
                "Поскольку кислород растения поступает из углекислого газа который растение "
                        + "поглощает что растение выделяет днём?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  (c) the Russian misconception: answered=" + russian.answered()
                + " status=" + statusOf(russian));
        System.out.println("     reason=" + oneLine(reasonOf(russian)));
        // (d) the plants premise: the corpus neither states nor denies it.
        Answer plants = workbench.ask("photosynthesis",
                "Since plants release oxygen during the day, what does a plant release during the day?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  (d) the plants premise: answered=" + plants.answered()
                + " status=" + statusOf(plants));
        assertTrue(plants.answered(), "the plants premise was refused");
        assertEquals("UNSUPPORTED", statusOf(plants), "the plants premise was not unsupported");
        // (e) the two false premises: both must still be refuted and refused.
        Answer false1 = workbench.ask("photosynthesis",
                "Since photosynthesis produces oxygen from carbon dioxide, what does a plant release during the day?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  (e) the false photosynthesis premise: answered=" + false1.answered()
                + " status=" + statusOf(false1));
        assertEquals("REFUTED", statusOf(false1), "the false premise was not refuted");
        assertFalse(false1.answered(), "the false premise was answered");
        Answer false2 = workbench.ask("logic",
                "Since every valid argument has true premises, what are the premises of modus ponens?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  (e) the false logic premise: answered=" + false2.answered()
                + " status=" + statusOf(false2));
        assertEquals("REFUTED", statusOf(false2), "the false premise was not refuted");
    }

    @Test
    @DisplayName("R5-2: none of the nine corpus-stated premises is called refuted")
    void noneOfTheNineIsCalledRefuted() {
        System.out.println("### R5-2 - nine premises the corpora STATE");
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
            if ("REFUTED".equals(statusOf(answer))) {
                refuted++;
                System.out.println("  REFUTED: " + pair[1]);
                System.out.println("     reason=" + oneLine(reasonOf(answer)));
            }
            assertNoPath(answer);
        }
        System.out.println("  premises called refuted: " + refuted + " of " + probes.length);
        assertEquals(0, refuted, "a premise the corpus states was called refuted");
    }

    @Test
    @DisplayName("R5-3: a Russian premise the corpus SUPPORTS is answered")
    void aSupportedRussianPremiseIsAnswered() {
        System.out.println("### R5-3 - a Russian premise the corpus supports");
        Answer answer = workbench.ask("photosynthesis",
                "Поскольку фотосинтез выделяет кислород, что растение выделяет днём?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  answered=" + answer.answered() + " status=" + statusOf(answer));
        System.out.println("  reason=" + oneLine(reasonOf(answer)));
        assertTrue(answer.answered(), "a supported Russian premise was refused");
        assertNoPath(answer);
    }

    @Test
    @DisplayName("R5-4: the rule still recognises the refutations it must recognise")
    void theRuleStillRecognisesTheRefutations() {
        System.out.println("### R5-4 - the false-negative check: paraphrases of the false premises");
        String[][] probes = {
                {"photosynthesis",
                        "Since the oxygen a plant releases comes from the carbon dioxide it absorbs, what does a plant release during the day?"},
                {"photosynthesis",
                        "Since photosynthesis produces the oxygen of a plant from the carbon dioxide it absorbs, what does a plant release during the day?"},
                {"logic",
                        "Since valid arguments always have true premises, what are the premises of modus ponens?"},
                {"logic",
                        "Since the premises of a valid argument are always true, what are the premises of modus ponens?"},
        };
        int refuted = 0;
        for (String[] pair : probes) {
            Answer answer = workbench.ask(pair[0], pair[1], AbstractionLevel.INTRODUCTORY);
            String status = statusOf(answer);
            if ("REFUTED".equals(status)) {
                refuted++;
            }
            System.out.println("  " + status + " answered=" + answer.answered()
                    + " | " + oneLine(pair[1]));
            assertNoPath(answer);
        }
        System.out.println("  refutations recognised: " + refuted + " of " + probes.length);
        assertEquals(probes.length, refuted,
                "a refutation the corpus states was no longer recognised");
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
