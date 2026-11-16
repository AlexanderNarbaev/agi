package io.matrix.knowledge.epistemic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimConflictDetector;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round 3 — the implementer's claims, re-measured with the reviewer's own probes.
 *
 * <p>R3-1 N1 (a symbol is content: "q is true" vs "p is not true" is no conflict) and
 * N2 (the polarity words are folded: "p is true" vs "p is false" IS a conflict).
 * R3-2 N5 (the research-pool corpus: the Mid-Atlantic Ridge premise is SUPPORTED).
 * R3-3 (a) a claim the workspace marks REFUTED cannot refute another proposition.
 * R3-4 the conflict coverage: E-C01 is a CONFLICT with both sides.
 * R3-5 N3: is the ambiguity the corpus holds surfaced to the caller in ANY field?
 * R3-6 `statesTheSame` at 0.30: does it create false contradictions on other corpora?
 * R3-7 the latency of the served path.
 */
class AdversarialProbeRound3Test {

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
        Path database = Files.createTempDirectory("adversarial-probe-r3-").resolve("knowledge.sqlite");
        workbench = KnowledgeWorkbench.at(database.toString());
        seedWorkspace = "r3-seed";
        poolWorkspace = "r3-pool";
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
    @DisplayName("R3-1: the conflict detector — different symbols are no conflict, the same thesis is")
    void theConflictDetectorOnTheLogicPackage() {
        KnowledgeSnapshot logic = workbench.workspace("logic");
        Map<String, Source> sources = new HashMap<>();
        for (Source source : logic.sources()) {
            sources.put(source.id(), source);
        }
        System.out.println("### R3-1 - the logic package: claims=" + logic.claims().size());
        List<ClaimConflictDetector.Conflict> all =
                ClaimConflictDetector.detect(logic.claims(), sources);
        System.out.println("  conflicts=" + all.size());
        for (ClaimConflictDetector.Conflict conflict : all) {
            System.out.println("  reason: " + conflict.reason());
            for (ClaimConflictDetector.Side side : conflict.sides()) {
                System.out.println("     side " + side.claimId() + ": " + side.proposition());
            }
        }
        // N1: two accounts that use DIFFERENT symbols are not a conflict
        Claim pNot = find(logic, "The propositional variable p is not true in the assignment");
        Claim qTrue = find(logic, "The propositional variable q is true in the assignment");
        Claim rTrue = find(logic, "The propositional variable r is true in the assignment");
        assertNotNull(pNot);
        assertNotNull(qTrue);
        assertNotNull(rTrue);
        System.out.println("  N1 p-vs-q conflicts=" + ClaimConflictDetector.detect(
                List.of(pNot, qTrue), sources).size()
                + " p-vs-r conflicts=" + ClaimConflictDetector.detect(
                List.of(pNot, rTrue), sources).size());
        assertEquals(0, ClaimConflictDetector.detect(List.of(pNot, qTrue), sources).size(),
                "accounts about different symbols were reported as a conflict");
        assertEquals(0, ClaimConflictDetector.detect(List.of(pNot, rTrue), sources).size(),
                "accounts about different symbols were reported as a conflict");
        // N2: the SAME thesis with the opposite polarity IS a conflict
        Claim pTrue = find(logic, "The propositional variable p is true in the assignment");
        Claim pFalse = find(logic, "The propositional variable p is false in the assignment");
        assertNotNull(pTrue);
        assertNotNull(pFalse);
        List<ClaimConflictDetector.Conflict> same = ClaimConflictDetector.detect(
                List.of(pTrue, pFalse), sources);
        System.out.println("  N2 p-true-vs-p-false conflicts=" + same.size());
        assertEquals(1, same.size(),
                "the same thesis with the opposite polarity was not reported as a conflict");
    }

    @Test
    @DisplayName("R3-2: the Mid-Atlantic Ridge premise on the research-pool corpus")
    void theMidAtlanticRidgePremiseOnTheResearchPool() {
        System.out.println("### R3-2 - the research-pool corpus");
        Answer answer = workbench.ask(poolWorkspace,
                "Given that Mid-Atlantic Ridge is a divergent boundary, what takes place along it?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  answered=" + answer.answered()
                + " status=" + statusOf(answer)
                + " reason=" + oneLine(reasonOf(answer)));
        assertTrue(answer.answered(), "the question was refused on the research-pool corpus");
        assertFalse("REFUTED".equals(statusOf(answer)),
                "the premise was called refuted on the research-pool corpus: " + reasonOf(answer));
    }

    @Test
    @DisplayName("R3-3: a claim the workspace marks REFUTED cannot refute another proposition")
    void aRefutedClaimCannotRefuteAnotherProposition() {
        System.out.println("### R3-3 - the photosynthetic and logical premises");
        Answer plants = workbench.ask("photosynthesis",
                "Since plants release oxygen during the day, what does a plant release during the day?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  the plants premise: answered=" + plants.answered()
                + " status=" + statusOf(plants));
        System.out.println("     reason=" + oneLine(reasonOf(plants)));
        assertFalse("REFUTED".equals(statusOf(plants)),
                "the premise the corpus states was called refuted: " + reasonOf(plants));
        Answer false1 = workbench.ask("photosynthesis",
                "Since photosynthesis produces oxygen from carbon dioxide, what does a plant release during the day?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  the false photosynthesis premise: answered=" + false1.answered()
                + " status=" + statusOf(false1));
        assertEquals("REFUTED", statusOf(false1),
                "the false premise was not refuted: " + reasonOf(false1));
        Answer false2 = workbench.ask("logic",
                "Since every valid argument has true premises, what are the premises of modus ponens?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("  the false logic premise: answered=" + false2.answered()
                + " status=" + statusOf(false2));
        assertEquals("REFUTED", statusOf(false2),
                "the false premise was not refuted: " + reasonOf(false2));
    }

    @Test
    @DisplayName("R3-4: the frozen conflict case is a conflict with both sides")
    void theFrozenConflictCaseIsAConflictWithBothSides() {
        JsonNode heldOut = caseOf("E-C01");
        Answer answer = workbench.ask(seedWorkspace, heldOut.get("question").asText(),
                AbstractionLevel.INTRODUCTORY);
        System.out.println("### R3-4 - E-C01: answered=" + answer.answered()
                + " status=" + statusOf(answer));
        if (answer.premise() != null) {
            System.out.println("  conflicts=" + answer.premise().conflicts().size()
                    + " readings=" + answer.premise().readings().size());
            for (ClaimConflictDetector.Conflict conflict : answer.premise().conflicts()) {
                System.out.println("  conflict: " + conflict.reason());
                for (ClaimConflictDetector.Side side : conflict.sides()) {
                    System.out.println("     side " + side.claimId() + " source=" + side.sourceId()
                            + " | " + side.proposition());
                }
            }
        }
        int named = heldOut.get("goldClaims").size();
        int surfaced = answer.premise() == null ? 0 : answer.premise().conflicts().size();
        System.out.println("  conflicts surfaced=" + surfaced + " (the set names " + named + " sides)");
        assertTrue(surfaced > 0, "the conflict the corpus holds was not reported");
    }

    @Test
    @DisplayName("R3-5: is the ambiguity the corpus holds surfaced to the caller in any field")
    void isTheAmbiguitySurfacedToTheCaller() {
        for (String id : List.of("E-A01", "E-A02")) {
            JsonNode heldOut = caseOf(id);
            Answer answer = workbench.ask(seedWorkspace, heldOut.get("question").asText(),
                    AbstractionLevel.INTRODUCTORY);
            String everything = answer.statement() + " " + answer.abstentionReason() + " "
                    + answer.limitations() + " " + answer.gaps()
                    + (answer.premise() == null ? "" : " " + answer.premise().reason()
                            + " " + answer.premise().readings());
            int matched = 0;
            int named = 0;
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
            System.out.println("### R3-5 - " + id + ": status=" + statusOf(answer)
                    + " readings=" + (answer.premise() == null ? 0 : answer.premise().readings().size())
                    + " goldMatched=" + matched + "/" + named
                    + " saysAmbiguous=" + saysAmbiguous);
            System.out.println("  reason=" + oneLine(reasonOf(answer)));
        }
    }

    @Test
    @DisplayName("R3-6: does statesTheSame create false contradictions on other corpora")
    void doesStatesTheSameCreateFalseContradictions() {
        System.out.println("### R3-6 - premises the corpora STATE, asked as premises");
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
                {poolWorkspace,
                        "Since Mid-Atlantic Ridge is a divergent boundary, what takes place along it?"},
                {seedWorkspace,
                        "Since divergent boundaries are where new crust is generated, what happens where Mid-Atlantic Ridge runs?"},
        };
        int refuted = 0;
        for (String[] pair : probes) {
            Answer answer = workbench.ask(pair[0], pair[1], AbstractionLevel.INTRODUCTORY);
            String status = statusOf(answer);
            if ("REFUTED".equals(status)) {
                refuted++;
            }
            System.out.println("  status=" + status + " answered=" + answer.answered()
                    + " | " + pair[1]);
            if ("REFUTED".equals(status)) {
                System.out.println("     reason=" + oneLine(reasonOf(answer)));
            }
        }
        System.out.println("  premises called refuted: " + refuted + " of " + probes.length);
        assertEquals(0, refuted,
                "a premise the corpus states was called refuted — a false contradiction");
    }

    @Test
    @DisplayName("R3-7: the latency of the served path")
    void theLatencyOfTheServedPath() {
        System.out.println("### R3-7 - the latency of one ask");
        for (String[] pair : new String[][]{{seedWorkspace, "seed"}, {poolWorkspace, "pool"}}) {
            String question = "What drives the motion of the tectonic plates?";
            workbench.ask(pair[0], question, AbstractionLevel.INTRODUCTORY);
            long total = 0;
            int rounds = 10;
            for (int i = 0; i < rounds; i++) {
                long started = System.nanoTime();
                workbench.ask(pair[0], question, AbstractionLevel.INTRODUCTORY);
                total += (System.nanoTime() - started) / 1_000_000L;
            }
            System.out.println("  " + pair[1] + ": mean " + (total / (double) rounds)
                    + " ms over " + rounds + " asks");
        }
    }

    // ── the helpers ──────────────────────────────────────────────────────────

    private static Claim find(KnowledgeSnapshot snapshot, String prefix) {
        for (Claim claim : snapshot.claims()) {
            if (claim.proposition().startsWith(prefix)) {
                return claim;
            }
        }
        return null;
    }

    private static JsonNode caseOf(String id) {
        for (JsonNode heldOut : cases) {
            if (id.equals(heldOut.get("id").asText())) {
                return heldOut;
            }
        }
        throw new IllegalStateException("the frozen set holds no case " + id);
    }

    private static String statusOf(Answer answer) {
        return answer.premise() == null ? "NONE" : String.valueOf(answer.premise().status());
    }

    private static String reasonOf(Answer answer) {
        return answer.premise() == null ? "" : String.valueOf(answer.premise().reason());
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
